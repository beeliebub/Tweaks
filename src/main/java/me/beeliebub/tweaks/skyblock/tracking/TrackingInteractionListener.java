package me.beeliebub.tweaks.skyblock.tracking;

import io.papermc.paper.event.player.PlayerTradeEvent;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.Objects;

/** Records interaction counters that are not item collection or block placement. */
public final class TrackingInteractionListener implements Listener {
    private static final Set<TrackCategory> RECORDED_CATEGORIES = Set.of(
            TrackCategory.SHEAR, TrackCategory.BREED, TrackCategory.TAME,
            TrackCategory.BARTER, TrackCategory.BREW, TrackCategory.TRADE);

    private final IslandManager islands;
    private final IslandTracker tracker;

    public TrackingInteractionListener(IslandManager islands, IslandTracker tracker) {
        this.islands = Objects.requireNonNull(islands, "islands");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    public static Set<TrackCategory> recordedCategories() {
        return RECORDED_CATEGORIES;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        EntityType entityType = event.getEntity().getType();
        Island island = islands.islandAt(event.getPlayer().getLocation()).orElse(null);
        if (island == null) return;
        record(island, TrackCategory.SHEAR, entityType.name(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        EntityType entityType = event.getEntity().getType();
        Island island = islands.islandAt(player.getLocation()).orElse(null);
        if (island == null) return;
        record(island, TrackCategory.BREED, entityType.name(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        EntityType entityType = event.getEntity().getType();
        Island island = islands.islandAt(player.getLocation()).orElse(null);
        if (island == null) return;
        record(island, TrackCategory.TAME, entityType.name(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBarter(PiglinBarterEvent event) {
        Island island = islands.islandAt(event.getEntity().getLocation()).orElse(null);
        if (island == null) return;
        for (ItemStack result : event.getOutcome()) {
            recordMaterial(island, TrackCategory.BARTER, result);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Island island = islands.islandAt(event.getBlock().getLocation()).orElse(null);
        if (island == null) return;
        for (ItemStack result : event.getResults()) {
            recordMaterial(island, TrackCategory.BREW, result);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        ItemStack result = event.getTrade() == null ? null : event.getTrade().getResult();
        if (result == null || result.getType().isAir() || result.getAmount() <= 0) return;
        Island island = islands.islandAt(event.getPlayer().getLocation()).orElse(null);
        if (island == null) return;
        recordMaterial(island, TrackCategory.TRADE, result);
    }

    private void recordMaterial(Island island, TrackCategory category, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) return;
        record(island, category, stack.getType().name(), stack.getAmount());
    }

    private void record(Island island, TrackCategory category, String identifier, long amount) {
        tracker.record(island, new TrackKey(category, identifier), amount);
    }
}
