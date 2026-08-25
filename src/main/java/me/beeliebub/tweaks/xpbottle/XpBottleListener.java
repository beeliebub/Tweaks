package me.beeliebub.tweaks.xpbottle;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.HotPathEventBuffer;
import me.beeliebub.tweaks.logging.LoggingPaths;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.potion.PotionMix;
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

    /** Nine emerald orbs are stored in an int, so this is the largest safe per-emerald value. */
    public static final int MAX_ORBS_PER_EMERALD = Integer.MAX_VALUE / 9;
    private final JavaPlugin plugin;
    private final XpBottle xpBottle;
    private final NamespacedKey brewerKey;
    private final NamespacedKey deferredBrewIdentityKey;
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
        int safeOrbs = configuredOrbs;
        if (configuredOrbs < 1) {
            plugin.getLogger().warning("xpbottle.orbs-per-emerald configured as " + configuredOrbs
                    + "; clamped to 1. This setting is baked into a boot-time brewing recipe, so"
                    + " fixing it requires correcting config.yml and restarting the server.");
            safeOrbs = 1;
        } else if (configuredOrbs > MAX_ORBS_PER_EMERALD) {
            plugin.getLogger().warning("xpbottle.orbs-per-emerald configured as " + configuredOrbs
                    + "; clamped to " + MAX_ORBS_PER_EMERALD
                    + " so the derived emerald-block value remains representable.");
            safeOrbs = MAX_ORBS_PER_EMERALD;
        }
        this.orbsPerEmerald = safeOrbs;
        this.orbsPerEmeraldBlock = Math.multiplyExact(orbsPerEmerald, 9);
        this.xpBottle = new XpBottle(new NamespacedKey(plugin, "xp_bottle_orbs"));
        this.brewerKey = new NamespacedKey(plugin, "xp_bottle_brewer");
        this.deferredBrewIdentityKey = new NamespacedKey(plugin, "xp_bottle_deferred_brew");
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

        final ItemStack ingredientSnapshot = ingredient.clone();
        final ItemStack[] inputSnapshots = snapshotInputs(contents);
        final Location standCenter = block.getLocation().toCenterLocation().add(0, 0.5, 0);
        final World world = block.getWorld();
        final BrewerInventory liveInv = contents;
        final BrewingStand capturedStand = stand;
        final String deferredBrewIdentity = UUID.randomUUID().toString();

        // Cancel before writing the short-lived identity marker. The marker distinguishes this
        // tile entity from a replacement brewing stand at the same coordinates.
        event.setCancelled(true);
        markDeferredBrewIdentity(stand, deferredBrewIdentity);

        String uuidStr = stand.getPersistentDataContainer().get(brewerKey, PersistentDataType.STRING);
        UUID trackedBrewerId = null;
        if (uuidStr != null) {
            try {
                trackedBrewerId = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException failure) {
                // Contain corrupted persisted identity before the platform can continue the brew.
                plugin.getLogger().log(Level.WARNING,
                        "Malformed XP-bottle brewer identity on brewing stand; brew refused", failure);
                clearBrewerTag(stand);
                scheduleIngredientReturn(block, capturedStand, deferredBrewIdentity, liveInv,
                        ingredientSnapshot, inputSnapshots, standCenter, world);
                return;
            }
        }
        Player brewer = trackedBrewerId == null ? null : Bukkit.getPlayer(trackedBrewerId);
        String brewerNameSnapshot = brewer == null ? null : brewer.getName();
        UUID brewerIdSnapshot = brewer == null ? null : brewer.getUniqueId();

        // Always clear the tag — the next brew must come from a fresh player click (prevents
        // hopper-fed automation from re-using a stale tracked player).
        clearBrewerTag(stand);

        int affordable;
        boolean xpUnavailable = false;
        if (brewer == null) {
            affordable = 0;
        } else {
            try {
                int currentXp = new ExperienceManager(brewer).getCurrentExp();
                affordable = Math.min(totalBottles, currentXp / costPerBottle);
            } catch (IllegalArgumentException | IllegalStateException failure) {
                plugin.getLogger().log(Level.WARNING,
                        "Cannot represent XP for XP-bottle brewer " + brewer.getUniqueId(), failure);
                affordable = 0;
                xpUnavailable = true;
            }
        }

        // Live BrewerInventory reference from the event — survives across the 1-tick deferral
        // because Inventory wrappers point to the underlying tile entity. Using this avoids the
        // snapshot/update revert bug entirely (see class-level comment).
        if (affordable == 0) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!prepareDeferredInputs(block, capturedStand, deferredBrewIdentity, liveInv,
                        ingredientSnapshot, inputSnapshots)) return;
                liveInv.setIngredient(null);
                if (world != null) {
                    world.dropItemNaturally(standCenter, ingredientSnapshot.clone());
                }
            });
            if (brewer != null) {
                brewer.sendMessage(xpUnavailable
                        ? Messages.TOOLS.xpBottleXpTotalTooLargeIngredientsReturned()
                        : Messages.TOOLS.xpBottleNotEnoughXpIngredientsReturned());
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
        final String finalBrewerName = brewerNameSnapshot;
        final UUID finalBrewerId = brewerIdSnapshot;

        Bukkit.getScheduler().runTask(plugin, () -> {
            // Revalidate the stand and every captured input before charging XP or mutating the
            // live inventory. A hopper or another plugin may have changed the tile during the
            // one-tick deferral; stale work must leave the replacement state untouched.
            if (!prepareDeferredInputs(block, capturedStand, deferredBrewIdentity, liveInv,
                    ingredientSnapshot, inputSnapshots)) return;

            long totalCost = (long) finalAffordable * finalCost;
            ExperienceManager experience = new ExperienceManager(finalBrewer);
            boolean canPay;
            try {
                canPay = experience.getCurrentExp() >= totalCost
                        && experience.canChangeExp(-totalCost);
            } catch (IllegalArgumentException | IllegalStateException failure) {
                canPay = false;
            }
            if (!canPay) {
                finalBrewer.sendMessage(Messages.TOOLS.xpBottleXpChangedReturned());
                return;
            }
            try {
                experience.changeExp(-totalCost);
            } catch (IllegalArgumentException | IllegalStateException failure) {
                plugin.getLogger().log(Level.WARNING,
                        "Cannot charge XP-bottle brewer " + finalBrewer.getUniqueId(), failure);
                finalBrewer.sendMessage(Messages.TOOLS.xpBottleChargeFailed());
                return;
            }
            if (finalAffordable < finalTotalBottles) {
                finalBrewer.sendMessage(Messages.TOOLS.xpBottlePartialBrew(
                        finalAffordable, finalTotalBottles));
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
            if (finalAffordable >= finalTotalBottles) {
                int newAmount = ingredientSnapshot.getAmount() - 1;
                if (newAmount <= 0) {
                    liveInv.setIngredient(null);
                } else {
                    ItemStack newIng = ingredientSnapshot.clone();
                    newIng.setAmount(newAmount);
                    liveInv.setIngredient(newIng);
                }
            } else {
                // Partial brew: refund the entire ingredient stack and clear the slot. The
                // previous (amount - 1) math vanished one emerald per partial cycle even though
                // no full recipe completed. Refunding everything also breaks the auto-cycle that
                // would otherwise pair the leftover glass-bottle refunds with another emerald
                // for a fresh 400-tick attempt.
                liveInv.setIngredient(null);
                if (world != null) {
                    world.dropItemNaturally(standCenter, ingredientSnapshot.clone());
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
        ExperienceManager experience = new ExperienceManager(player);
        if (!experience.canChangeExp(orbs)) {
            event.setCancelled(true);
            player.sendMessage(Messages.TOOLS.xpBottleConsumeRejected());
            return;
        }
        try {
            experience.changeExp(orbs);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.WARNING,
                    "Cannot award XP bottle to player " + actorId, failure);
            player.sendMessage(Messages.TOOLS.xpBottleAwardFailed());
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) {
            eventLog.logHot(LoggingPaths.XPBOTTLE_RELEASED,
                    new HotPathEventBuffer.HotKey(actorId, "released", null), actorName);
        }
    }

    private void clearBrewerTag(BrewingStand stand) {
        stand.getPersistentDataContainer().remove(brewerKey);
        stand.update();
    }

    private void scheduleIngredientReturn(Block block, BrewingStand capturedStand,
                                          String deferredBrewIdentity, BrewerInventory liveInv,
                                          ItemStack ingredientSnapshot, ItemStack[] inputSnapshots,
                                          Location standCenter, World world) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!prepareDeferredInputs(block, capturedStand, deferredBrewIdentity, liveInv,
                    ingredientSnapshot, inputSnapshots)) return;
            liveInv.setIngredient(null);
            if (world != null) {
                world.dropItemNaturally(standCenter, ingredientSnapshot.clone());
            }
        });
    }

    private static ItemStack[] snapshotInputs(BrewerInventory inventory) {
        ItemStack[] snapshots = new ItemStack[3];
        for (int i = 0; i < snapshots.length; i++) {
            snapshots[i] = copy(inventory.getItem(i));
        }
        return snapshots;
    }

    private void markDeferredBrewIdentity(BrewingStand stand, String deferredBrewIdentity) {
        stand.getPersistentDataContainer().set(deferredBrewIdentityKey,
                PersistentDataType.STRING, deferredBrewIdentity);
        stand.update();
    }

    private boolean prepareDeferredInputs(Block block, BrewingStand capturedStand,
                                          String deferredBrewIdentity, BrewerInventory liveInv,
                                          ItemStack ingredientSnapshot, ItemStack[] inputSnapshots) {
        if (!(block.getState() instanceof BrewingStand currentStand)) return false;
        if (currentStand != capturedStand) {
            String currentIdentity = currentStand.getPersistentDataContainer().get(
                    deferredBrewIdentityKey, PersistentDataType.STRING);
            if (!deferredBrewIdentity.equals(currentIdentity)) return false;
        }
        if (!sameStack(ingredientSnapshot, liveInv.getIngredient())) return false;
        for (int i = 0; i < inputSnapshots.length; i++) {
            if (!sameStack(inputSnapshots[i], liveInv.getItem(i))) return false;
        }

        // Remove the marker before any live inventory mutation. Updating this untouched snapshot
        // cannot revert the writes that follow, and a replacement stand never passes the marker
        // check above.
        currentStand.getPersistentDataContainer().remove(deferredBrewIdentityKey);
        currentStand.update();
        return true;
    }

    private static ItemStack copy(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static boolean sameStack(ItemStack expected, ItemStack actual) {
        boolean expectedEmpty = expected == null || expected.isEmpty();
        boolean actualEmpty = actual == null || actual.isEmpty();
        if (expectedEmpty || actualEmpty) return expectedEmpty && actualEmpty;
        return expected.getAmount() == actual.getAmount() && expected.isSimilar(actual);
    }
}
