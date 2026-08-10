package me.beeliebub.tweaks.skyblock.tracking;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Main-thread island counter mutator with active-key and purchased-item guards. */
public final class IslandTracker {
    private final TrackingScope scope;
    private final SkyblockPdc pdc;
    private final IslandManager islands;
    private final ReadinessCallback callback;
    private final Map<TrackKey, Long> thresholds = new HashMap<>();
    private final Map<String, Map<TrackKey, Long>> fired = new HashMap<>();

    public IslandTracker(TrackingScope scope, SkyblockPdc pdc) {
        this(scope, pdc, null, (island, key, value) -> { });
    }

    public IslandTracker(TrackingScope scope, SkyblockPdc pdc, IslandManager islands,
                         ReadinessCallback callback) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.pdc = Objects.requireNonNull(pdc, "pdc");
        this.islands = islands;
        this.callback = callback == null ? (island, key, value) -> { } : callback;
    }

    public void setThreshold(TrackKey key, long threshold) {
        if (threshold <= 0) throw new IllegalArgumentException("threshold must be positive");
        thresholds.put(key, threshold);
    }

    public Island record(Island island, TrackKey key, long amount) {
        return record(island, key, amount, null);
    }

    public Island record(Island island, TrackKey key, long amount, ItemStack source) {
        Objects.requireNonNull(island, "island");
        Objects.requireNonNull(key, "key");
        if (amount <= 0 || !scope.activeTrackKeys(island).contains(key) || pdc.isPurchased(source)) return island;
        Map<String, Long> counters = new HashMap<>(island.counters());
        long previous = counters.getOrDefault(key.format(), 0L);
        long updated = Math.addExact(previous, amount);
        counters.put(key.format(), updated);
        Island changed = island.withCounters(counters);
        if (islands != null) islands.update(changed);
        Long threshold = thresholds.get(key);
        if (threshold != null && previous < threshold && updated >= threshold
                && fired.computeIfAbsent(island.id(), ignored -> new HashMap<>())
                .putIfAbsent(key, threshold) == null) {
            callback.onReady(changed, key, updated);
        }
        return changed;
    }

    public Island record(Island island, TrackKey key, int amount, ItemStack source) {
        return record(island, key, (long) amount, source);
    }

    public long count(Island island, TrackKey key) {
        return island == null || key == null ? 0L : island.counters().getOrDefault(key.format(), 0L);
    }

    public boolean isActive(TrackKey key) {
        return key != null && scope.activeTrackKeys().contains(key);
    }

    public boolean isActive(Island island, TrackKey key) {
        return island != null && key != null && scope.activeTrackKeys(island).contains(key);
    }

    public interface ReadinessCallback {
        void onReady(Island island, TrackKey key, long value);
    }
}
