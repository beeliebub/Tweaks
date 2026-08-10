package me.beeliebub.tweaks.skyblock.tracking;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Set;

/** Gathers item, kill, smelt, fish, and related event-family counters. */
public final class TrackingGatherListener implements Listener {
    private static final Set<TrackCategory> RECORDED_CATEGORIES = Set.of(
            TrackCategory.COLLECT, TrackCategory.KILL, TrackCategory.SMELT, TrackCategory.FISH);
    private final IslandManager islands;
    private final IslandTracker tracker;
    private final SkyblockPdc pdc;
    private final TrackingScope scope;

    public TrackingGatherListener(IslandManager islands, IslandTracker tracker, SkyblockPdc pdc) {
        this(islands, tracker, pdc, null);
    }

    public TrackingGatherListener(IslandManager islands, IslandTracker tracker, SkyblockPdc pdc,
                                  TrackingScope scope) {
        this.islands = Objects.requireNonNull(islands, "islands");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.pdc = Objects.requireNonNull(pdc, "pdc");
        this.scope = scope;
    }

    public static Set<TrackCategory> recordedCategories() {
        return RECORDED_CATEGORIES;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        Island island = islands.islandAt(event.getBlock().getLocation()).orElse(null);
        if (island == null) return;
        if (pdc.isPurchased(event.getBlock())) {
            pdc.consumePurchased(event.getBlock());
            for (Item item : event.getItems()) {
                ItemStack stack = item.getItemStack();
                stripRetired(island, stack);
                if (pdc.markPurchased(stack)) item.setItemStack(stack);
            }
            return;
        }
        boolean tainted = pdc.consume(event.getBlock(), event.getBlock().getType());
        if (tainted) {
            for (Item item : event.getItems()) {
                ItemStack stack = item.getItemStack();
                stripRetired(island, stack);
                if (pdc.markCounted(stack)) item.setItemStack(stack);
            }
            return;
        }
        Player player = event.getPlayer();
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            stripRetired(island, stack);
            TrackKey key = new TrackKey(TrackCategory.COLLECT, stack.getType().name());
            if (!tracker.isActive(island, key) || pdc.isCounted(stack) || pdc.isPurchased(stack)) continue;
            tracker.record(island, key, stack.getAmount(), stack);
            if (pdc.markCounted(stack)) item.setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        Island island = islands.islandAt(killer.getLocation()).orElse(null);
        if (island == null) return;
        tracker.record(island, new TrackKey(TrackCategory.KILL, event.getEntityType().name()), 1);
        for (ItemStack drop : event.getDrops()) {
            stripRetired(island, drop);
            TrackKey key = new TrackKey(TrackCategory.COLLECT, drop.getType().name());
            if (!tracker.isActive(island, key) || pdc.isCounted(drop) || pdc.isPurchased(drop)) continue;
            tracker.record(island, key, drop.getAmount(), drop);
            pdc.markCounted(drop);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Island island = islands.islandAt(event.getBlock().getLocation()).orElse(null);
        if (island == null) return;
        ItemStack output = null;
        if (event.getBlock().getState() instanceof org.bukkit.block.Furnace furnace) {
            output = furnace.getInventory().getResult();
        }
        if (output != null && pdc.isPurchased(output)) return;
        tracker.record(island, new TrackKey(TrackCategory.SMELT, event.getItemType().name()),
                event.getItemAmount(), output);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Island island = islands.islandAt(event.getPlayer().getLocation()).orElse(null);
        if (island == null || !(event.getCaught() instanceof Item item)) return;
        ItemStack stack = item.getItemStack();
        stripRetired(island, stack);
        tracker.record(island, new TrackKey(TrackCategory.FISH, stack.getType().name()), stack.getAmount(), stack);
    }

    private void stripRetired(Island island, ItemStack stack) {
        if (scope != null && stack != null && !scope.taintMaterials(island).contains(stack.getType())) {
            pdc.stripCounted(stack);
        }
    }
}
