package me.beeliebub.tweaks.xpbottle;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.potion.PotionMix;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

// XP-storage bottle pipeline:
//   - Registers two PotionMix brewing recipes (emerald and emerald block + glass bottle).
//   - Tracks the player who placed the ingredient on the BrewingStand's tile-state PDC.
//   - At BrewEvent: charges the tracked player as much as they can afford, replaces unaffordable
//     bottles with plain glass, and on a full shortfall (or no tracked brewer at all — e.g.
//     hopper-fed) cancels the brew and drops the ingredient back at the stand so the player
//     keeps their emerald.
//   - On PlayerItemConsumeEvent: awards the bottle's stored orb count via ExperienceManager.
//     Vanilla potion drinking handles the animation, stack decrement, and glass-bottle remainder.
//
// Why the brewer tag is cleared after every brew: the tag is set only by *player* clicks/drags
// on the brewing stand's ingredient slot. Clearing it after each brew means a hopper can't
// silently auto-feed emeralds and ride the previously-tracked player's XP forever — the next
// brew with no fresh placement has no brewer and is rejected.
public class XpBottleListener implements Listener {

    public static final int ORBS_PER_EMERALD = 1395;
    public static final int ORBS_PER_EMERALD_BLOCK = ORBS_PER_EMERALD * 9;

    private final JavaPlugin plugin;
    private final XpBottle xpBottle;
    private final NamespacedKey brewerKey;
    private final NamespacedKey emeraldRecipeKey;
    private final NamespacedKey emeraldBlockRecipeKey;

    public XpBottleListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.xpBottle = new XpBottle(new NamespacedKey(plugin, "xp_bottle_orbs"));
        this.brewerKey = new NamespacedKey(plugin, "xp_bottle_brewer");
        this.emeraldRecipeKey = new NamespacedKey(plugin, "xp_bottle_emerald");
        this.emeraldBlockRecipeKey = new NamespacedKey(plugin, "xp_bottle_emerald_block");
        registerRecipes();
    }

    public XpBottle xpBottle() {
        return xpBottle;
    }

    private ItemStack stackableTemplate(int orbs) {
        ItemStack stack = xpBottle.create(orbs);
        // Allow stacking up to 64 — Material.POTION is unstackable by default, but the data
        // component override applies to our custom bottles only. The orb count is part of the
        // PDC, so bottles of different orb counts still won't stack with each other.
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 64);
        return stack;
    }

    private void registerRecipes() {
        try {
            PotionMix emerald = new PotionMix(
                    emeraldRecipeKey,
                    stackableTemplate(ORBS_PER_EMERALD),
                    new RecipeChoice.MaterialChoice(Material.GLASS_BOTTLE),
                    new RecipeChoice.MaterialChoice(Material.EMERALD)
            );
            PotionMix emeraldBlock = new PotionMix(
                    emeraldBlockRecipeKey,
                    stackableTemplate(ORBS_PER_EMERALD_BLOCK),
                    new RecipeChoice.MaterialChoice(Material.GLASS_BOTTLE),
                    new RecipeChoice.MaterialChoice(Material.EMERALD_BLOCK)
            );
            Bukkit.getPotionBrewer().addPotionMix(emerald);
            Bukkit.getPotionBrewer().addPotionMix(emeraldBlock);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register XP bottle brewing recipes", t);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof BrewerInventory)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> trackBrewer(event.getInventory(), uuid));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory() instanceof BrewerInventory)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> trackBrewer(event.getInventory(), uuid));
    }

    // Records the player's UUID on the brewing stand's tile-state PDC iff the post-click slot
    // state is one we care about: ingredient is emerald or emerald_block AND at least one bottle
    // slot holds a glass bottle. Runs one tick after the click so the slots reflect the result
    // of the click rather than its pre-state.
    private void trackBrewer(Inventory inv, UUID uuid) {
        if (!(inv instanceof BrewerInventory brewer)) return;
        BrewingStand holder = brewer.getHolder();
        if (holder == null) return;

        ItemStack ingredient = brewer.getIngredient();
        if (ingredient == null) return;
        Material ingType = ingredient.getType();
        if (ingType != Material.EMERALD && ingType != Material.EMERALD_BLOCK) return;

        boolean hasGlass = false;
        for (int i = 0; i < 3; i++) {
            ItemStack slot = brewer.getItem(i);
            if (slot != null && slot.getType() == Material.GLASS_BOTTLE) {
                hasGlass = true;
                break;
            }
        }
        if (!hasGlass) return;

        BlockState state = holder.getBlock().getState();
        if (!(state instanceof BrewingStand standState)) return;
        standState.getPersistentDataContainer().set(brewerKey, PersistentDataType.STRING, uuid.toString());
        standState.update();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof BrewingStand stand)) return;

        List<ItemStack> results = event.getResults();
        int totalBottles = 0;
        int costPerBottle = 0;
        for (ItemStack result : results) {
            if (xpBottle.isXpBottle(result)) {
                totalBottles++;
                if (costPerBottle == 0) costPerBottle = xpBottle.getStoredOrbs(result);
            }
        }
        if (totalBottles == 0) return;

        String uuidStr = stand.getPersistentDataContainer().get(brewerKey, PersistentDataType.STRING);
        Player brewer = uuidStr != null ? Bukkit.getPlayer(UUID.fromString(uuidStr)) : null;

        // Always clear the tag — the next brew must come from a fresh player click (prevents
        // hopper-fed automation from re-using a stale tracked player).
        stand.getPersistentDataContainer().remove(brewerKey);
        stand.update();

        int affordable;
        if (brewer == null || costPerBottle <= 0) {
            affordable = 0;
        } else {
            int currentXp = new ExperienceManager(brewer).getCurrentExp();
            affordable = Math.min(totalBottles, currentXp / costPerBottle);
        }

        if (affordable == 0) {
            // No one can pay → cancel and return the ingredient. Cancelling preserves the
            // ingredient and bottles, but the brewing stand will immediately re-attempt on the
            // next tick because the recipe still matches. Clearing the ingredient slot — and
            // dropping its contents on top of the stand so the player can pick it back up —
            // breaks the loop without confiscating items.
            event.setCancelled(true);
            ItemStack ingredient = event.getContents().getIngredient();
            ItemStack ingredientClone = (ingredient != null && !ingredient.isEmpty()) ? ingredient.clone() : null;
            Location standCenter = block.getLocation().toCenterLocation().add(0, 0.5, 0);
            World world = block.getWorld();
            Bukkit.getScheduler().runTask(plugin, () -> {
                BlockState st = block.getState();
                if (st instanceof BrewingStand bs) {
                    bs.getInventory().setIngredient(null);
                }
                if (ingredientClone != null && world != null) {
                    world.dropItemNaturally(standCenter, ingredientClone);
                }
            });
            if (brewer != null) {
                brewer.sendMessage(Component.text("Not enough XP to brew XP bottles. Ingredients returned.",
                        NamedTextColor.RED));
            }
            return;
        }

        new ExperienceManager(brewer).changeExp(-(affordable * costPerBottle));
        if (affordable < totalBottles) {
            brewer.sendMessage(Component.text("Not enough XP for all bottles — brewed "
                    + affordable + " of " + totalBottles + ".", NamedTextColor.YELLOW));
        }

        int kept = 0;
        for (int i = 0; i < results.size(); i++) {
            ItemStack r = results.get(i);
            if (!xpBottle.isXpBottle(r)) continue;
            if (kept < affordable) {
                kept++;
            } else {
                results.set(i, new ItemStack(Material.GLASS_BOTTLE));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!xpBottle.isXpBottle(item)) return;
        int orbs = xpBottle.getStoredOrbs(item);
        if (orbs <= 0) return;
        Player player = event.getPlayer();
        new ExperienceManager(player).changeExp(orbs);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
    }
}