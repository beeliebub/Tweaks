package me.beeliebub.tweaks.protection.region;

import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Owns the in-memory indexes that back protection lookup and lazy PDC work.
 *
 * <p>The map accessors deliberately return the original concurrent instances:
 * callers on the event path and chunk-load path rely on their lock-free reads
 * and atomic mutations. This class is a responsibility boundary, not a cache
 * wrapper.</p>
 */
final class RegionRegistry {

    private final ConcurrentHashMap<String, Region> regions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> pendingStamps = new ConcurrentHashMap<>();
    private final Set<String> orphanedRegions = ConcurrentHashMap.newKeySet();

    ConcurrentHashMap<String, Region> regions() {
        return regions;
    }

    ConcurrentHashMap<Long, Set<String>> pendingStamps() {
        return pendingStamps;
    }

    Set<String> orphanedRegions() {
        return orphanedRegions;
    }

    Region globalRegion(World world) {
        if (world == null) return null;
        String key = keyOf(world.getName(), ProtectionManager.GLOBAL_REGION_ID);
        return regions.computeIfAbsent(key, ignored -> new Region(
                ProtectionManager.GLOBAL_REGION_ID,
                ProtectionManager.SERVER_OWNER,
                List.of(), Map.of(), Map.of(), null, null, world.getName(), List.of(), Map.of()));
    }

    List<Region> globalAsFallback(World world) {
        Region global = globalRegion(world);
        return global == null ? List.of() : List.of(global);
    }

    static String keyOf(String worldName, String id) {
        return (worldName == null || worldName.isEmpty()) ? id : worldName + ":" + id;
    }

    static String keyOf(Region region) {
        return region == null ? null : keyOf(region.worldName(), region.id());
    }

    Region byName(World world, String id) {
        if (id == null) return null;
        if (world != null) {
            Region hit = regions.get(keyOf(world.getName(), id));
            if (hit != null) return hit;
        }
        return regions.get(id);
    }

    Region byNameAnyWorld(String id) {
        if (id == null || ProtectionManager.GLOBAL_REGION_ID.equals(id)) return null;
        Region exact = regions.get(id);
        if (exact != null) return exact;
        String suffix = ":" + id;
        for (var entry : regions.entrySet()) {
            if (entry.getKey().endsWith(suffix)) return entry.getValue();
        }
        return null;
    }

    int migrateLegacyRegions(String defaultWorldName, Consumer<Region> migrated) {
        if (defaultWorldName == null || defaultWorldName.isEmpty()) return 0;
        int migratedCount = 0;
        for (var entry : new ArrayList<>(regions.entrySet())) {
            String key = entry.getKey();
            Region region = entry.getValue();
            if (ProtectionManager.GLOBAL_REGION_ID.equals(region.id()) || region.worldName() != null) continue;
            Region updated = region.withWorld(defaultWorldName);
            regions.remove(key);
            regions.put(keyOf(updated), updated);
            if (migrated != null) migrated.accept(updated);
            migratedCount++;
        }
        return migratedCount;
    }
}
