package me.beeliebub.tweaks.xpbottle;

import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.HotPathEventBuffer;
import me.beeliebub.tweaks.logging.LoggingPaths;
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

import java.util.UUID;
import java.util.logging.Level;

// XP-storage bottle pipeline:
//   - Registers two PotionMix brewing recipes (emerald and emerald block + glass bottle).
//   - Tracks the player who placed the ingredient on the BrewingStand's tile-state PDC.
//   - At BrewEvent: ALWAYS cancels the event and reapplies inventory mutations from a one-tick
//     scheduled task — Paper 26.1.1's BrewEvent.getResults() set() does not reliably propagate
//     to the applied bottle slots, and the result item produced by PotionMix loses its orbs PDC
//     somewhere in the brewing pipeline. Cancelling lets us bypass both pitfalls and gives full
//     control over the bottle slots and the ingredient slot (consumption + partial-brew refund).
//   - The deferred task: charges the tracked player, writes our XP-bottle template directly into
//     each affordable slot, refunds unaffordable brewed slots as glass bottles, and decides what
//     to do with the ingredient slot. On a full brew, decrement by 1 (vanilla parity); on a
//     partial brew, drop the entire remaining ingredient stack on top of the stand and clear the
//     slot so the leftover glass bottles can't auto-cycle into another 400-tick brew.
//   - On PlayerItemConsumeEvent: awards the bottle's stored orb count via ExperienceManager.
//     Vanilla potion drinking handles the animation, stack decrement, and glass-bottle remainder.
//
// CRITICAL: do NOT touch the live inventory via a snapshot BlockState. In Paper, `block.getState()`
// returns a snapshot whose `update()` loads its captured NBT back into the live tile entity —
// including the inventory items. If we call `bs.update()` after mutating `bs.getInventory()` (which
// is the LIVE BrewerInventory on a placed state), the update reverts our setItem/setIngredient
// writes to whatever the snapshot recorded at getState() time. Operate on the BrewerInventory
// captured from BrewEvent#getContents() (which is live and stable across ticks) and never call
// BlockState.update() in the deferred task.
//
// Why the brewer tag is cleared after every brew: the tag is set only by *player* clicks/drags
// on the brewing stand's ingredient slot. Clearing it after each brew means a hopper can't
// silently auto-feed emeralds and ride the previously-tracked player's XP forever — the next
// brew with no fresh placement has no brewer and is rejected.
public class XpBottleListener implements Listener {

    private final JavaPlugin plugin;
    private final XpBottle xpBottle;
    private final NamespacedKey brewerKey;
    private final NamespacedKey emeraldRecipeKey;
    private final NamespacedKey emeraldBlockRecipeKey;
    // Read once at construction, not live: this value is baked into a registered PotionMix
    // recipe at boot (see registerRecipes below), so changing it at runtime would desync the
    // recipe's ratio from the charged cost. /tconfig writes config.yml and replies "applies on
    // restart" — see xpbottle/CLAUDE.md and this plugin's ConfigRegistry entry.
    private final int orbsPerEmerald;
    private final int orbsPerEmeraldBlock;

    public XpBottleListener(JavaPlugin plugin) {
        this.plugin = plugin;
        int configuredOrbs = plugin.getConfig().getInt("xpbottle.orbs-per-emerald", 1395);
        if (configuredOrbs < 1) {
            plugin.getLogger().warning("xpbottle.orbs-per-emerald configured as " + configuredOrbs
                    + "; clamped to 1. This setting is baked into a boot-time brewing recipe, so"
                    + " fixing it requires correcting config.yml and restarting the server.");
        }
        this.orbsPerEmerald = Math.max(1, configuredOrbs);
        this.orbsPerEmeraldBlock = orbsPerEmerald * 9;
        this.xpBottle = new XpBottle(new NamespacedKey(plugin, "xp_bottle_orbs"));
        this.brewerKey = new NamespacedKey(plugin, "xp_bottle_brewer");
        this.emeraldRecipeKey = new NamespacedKey(plugin, "xp_bottle_emerald");
        this.emeraldBlockRecipeKey = new NamespacedKey(plugin, "xp_bottle_emerald_block");
        registerRecipes();
    }

    public XpBottle xpBottle() {
        return xpBottle;
    }

    public int orbsPerEmerald() {
        return orbsPerEmerald;
    }

    public int orbsPerEmeraldBlock() {
        return orbsPerEmeraldBlock;
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
                    stackableTemplate(orbsPerEmerald),
                    new RecipeChoice.MaterialChoice(Material.GLASS_BOTTLE),
                    new RecipeChoice.MaterialChoice(Material.EMERALD)
            );
            PotionMix emeraldBlock = new PotionMix(
                    emeraldBlockRecipeKey,
                    stackableTemplate(orbsPerEmeraldBlock),
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

        // Identify our recipe by ingredient + glass-bottle inputs. Emerald / emerald_block +
        // glass bottle is exclusive to our PotionMix (no vanilla recipe matches), so the
        // ingredient type is a sound discriminator. We deliberately do NOT inspect the result
        // item's PDC — Paper 26.1.1's brewing pipeline does not reliably surface the orbs PDC
        // back into BrewEvent.getResults().
        BrewerInventory contents = event.getContents();
        ItemStack ingredient = contents.getIngredient();
        if (ingredient == null || ingredient.isEmpty()) return;
        int costPerBottle;
        if (ingredient.getType() == Material.EMERALD) {
            costPerBottle = orbsPerEmerald;
        } else if (ingredient.getType() == Material.EMERALD_BLOCK) {
            costPerBottle = orbsPerEmeraldBlock;
        } else {
            return;
        }

        boolean[] brewedSlots = new boolean[3];
        int totalBottles = 0;
        for (int i = 0; i < 3; i++) {
            ItemStack input = contents.getItem(i);
            if (input != null && input.getType() == Material.GLASS_BOTTLE) {
                brewedSlots[i] = true;
                totalBottles++;
            }
        }
        if (totalBottles == 0) return;

        String uuidStr = stand.getPersistentDataContainer().get(brewerKey, PersistentDataType.STRING);
        Player brewer = uuidStr != null ? Bukkit.getPlayer(UUID.fromString(uuidStr)) : null;
        String brewerNameSnapshot = brewer == null ? null : brewer.getName();
        UUID brewerIdSnapshot = brewer == null ? null : brewer.getUniqueId();

        // Always clear the tag — the next brew must come from a fresh player click (prevents
        // hopper-fed automation from re-using a stale tracked player).
        stand.getPersistentDataContainer().remove(brewerKey);
        stand.update();

        int affordable;
        if (brewer == null) {
            affordable = 0;
        } else {
            int currentXp = new ExperienceManager(brewer).getCurrentExp();
            affordable = Math.min(totalBottles, currentXp / costPerBottle);
        }

        // Always cancel — we apply our own bottle-slot writes and ingredient consumption from a
        // one-tick scheduled task. Mutating event.getResults() does not reliably propagate to
        // the inventory on Paper 26.1.1, and cancelling also lets us cleanly drop the leftover
        // ingredient stack on partial brews so the brewing stand can't auto-cycle into another
        // 400-tick attempt with the unaffordable glass bottles still in slots.
        event.setCancelled(true);

        Location standCenter = block.getLocation().toCenterLocation().add(0, 0.5, 0);
        World world = block.getWorld();

        // Live BrewerInventory reference from the event — survives across the 1-tick deferral
        // because Inventory wrappers point to the underlying tile entity. Using this avoids the
        // snapshot/update revert bug entirely (see class-level comment).
        final BrewerInventory liveInv = contents;

        if (affordable == 0) {
            ItemStack ingredientClone = ingredient.clone();
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Verify the brewing stand still exists. Use getState() instanceof check so this
                // works under both real Paper and MockBukkit; the inventory write goes through the
                // captured live `liveInv`, NOT a snapshot's getInventory().
                if (!(block.getState() instanceof BrewingStand)) return;
                liveInv.setIngredient(null);
                if (world != null) {
                    world.dropItemNaturally(standCenter, ingredientClone);
                }
            });
            if (brewer != null) {
                brewer.sendMessage(Component.text("Not enough XP to brew XP bottles. Ingredients returned.",
                        NamedTextColor.RED));
            }
            return;
        }

        // Capture state for the deferred task. XP charging is deferred too so a stand that's
        // broken or replaced between the event and the task doesn't bill the player.
        final Player finalBrewer = brewer;
        final int finalAffordable = affordable;
        final int finalTotalBottles = totalBottles;
        final int finalCost = costPerBottle;
        final boolean[] finalBrewedSlots = brewedSlots;
        final Material expectedIngredientType = ingredient.getType();
        final String finalBrewerName = brewerNameSnapshot;
        final UUID finalBrewerId = brewerIdSnapshot;

        Bukkit.getScheduler().runTask(plugin, () -> {
            // Verify the brewing stand still exists. Use getState() instanceof check so this
            // works under both real Paper and MockBukkit; the inventory writes go through the
            // captured live `liveInv`, NOT a snapshot's getInventory(), and we never call
            // BlockState.update() (which would revert our setItem/setIngredient writes).
            if (!(block.getState() instanceof BrewingStand)) return;

            new ExperienceManager(finalBrewer).changeExp(-(finalAffordable * finalCost));
            if (finalAffordable < finalTotalBottles) {
                finalBrewer.sendMessage(Component.text("Not enough XP for all bottles — brewed "
                        + finalAffordable + " of " + finalTotalBottles + ".", NamedTextColor.YELLOW));
            }

            // Write XP bottles to affordable brewed slots, refund the rest as glass bottles.
            int kept = 0;
            for (int i = 0; i < 3; i++) {
                if (!finalBrewedSlots[i]) continue;
                if (kept < finalAffordable) {
                    liveInv.setItem(i, stackableTemplate(finalCost));
                    kept++;
                } else {
                    liveInv.setItem(i, new ItemStack(Material.GLASS_BOTTLE));
                }
            }

            ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
            if (eventLog != null && finalBrewerId != null) {
                eventLog.logHot(LoggingPaths.XPBOTTLE_BOTTLED,
                        new HotPathEventBuffer.HotKey(finalBrewerId, "bottled", null), finalBrewerName);
            }

            // Ingredient handling. Cancelling left the original stack untouched, so we replicate
            // vanilla's "consume one per cycle" by decrementing here on a full brew. On a partial
            // brew, drop the entire remaining stack on top of the stand and clear the slot — that
            // breaks the auto-cycle that would otherwise attempt to brew the leftover glass-bottle
            // refunds with the still-present ingredient on the next tick.
            ItemStack currentIng = liveInv.getIngredient();
            if (currentIng == null || currentIng.isEmpty() || currentIng.getType() != expectedIngredientType) {
                // Player swapped the ingredient between event and task — leave whatever is there.
                return;
            }
            if (finalAffordable >= finalTotalBottles) {
                int newAmount = currentIng.getAmount() - 1;
                if (newAmount <= 0) {
                    liveInv.setIngredient(null);
                } else {
                    ItemStack newIng = currentIng.clone();
                    newIng.setAmount(newAmount);
                    liveInv.setIngredient(newIng);
                }
            } else {
                // Partial brew: refund the entire ingredient stack and clear the slot. The
                // previous (amount - 1) math vanished one emerald per partial cycle even though
                // no full recipe completed. Refunding everything also breaks the auto-cycle that
                // would otherwise pair the leftover glass-bottle refunds with another emerald
                // for a fresh 400-tick attempt.
                ItemStack drop = currentIng.clone();
                liveInv.setIngredient(null);
                if (world != null) {
                    world.dropItemNaturally(standCenter, drop);
                }
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!xpBottle.isXpBottle(item)) return;
        int orbs = xpBottle.getStoredOrbs(item);
        if (orbs <= 0) return;
        Player player = event.getPlayer();
        String actorName = player.getName();
        UUID actorId = player.getUniqueId();
        new ExperienceManager(player).changeExp(orbs);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) {
            eventLog.logHot(LoggingPaths.XPBOTTLE_RELEASED,
                    new HotPathEventBuffer.HotKey(actorId, "released", null), actorName);
        }
    }
}
