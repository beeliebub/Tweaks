package me.beeliebub.tweaks.protection.region;

import org.bukkit.World;

import java.util.ArrayList;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Owns the in-memory indexes that back protection lookup and lazy PDC work.
 *
 * <p>The map accessors deliberately return the original concurrent instances:
 * callers on the event path and chunk-load path rely on their lock-free reads
 * and atomic mutations. This class is a responsibility boundary, not a cache
 * wrapper.</p>
 */
final class RegionRegistry {

    private static final int MAX_ORPHANED_REGIONS = 5_000;

    private final ConcurrentHashMap<String, Region> regions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> pendingStamps = new ConcurrentHashMap<>();
    private final Set<String> orphanedRegions = new BoundedOrphanSet(MAX_ORPHANED_REGIONS);
    private final Logger logger;

    RegionRegistry(Logger logger) {
        this.logger = logger;
    }

    ConcurrentHashMap<String, Region> regions() {
        return regions;
    }

    ConcurrentHashMap<String, Set<String>> pendingStamps() {
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

    static String stampKey(String worldName, long chunkKey) {
        return worldName + ":" + chunkKey;
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
            if (region.worldName() != null) continue;

            if (ProtectionManager.GLOBAL_REGION_ID.equals(region.id())) {
                Region defaultGlobal = regions.get(keyOf(defaultWorldName, region.id()));
                if (defaultGlobal == null) {
                    Region updated = region.withWorld(defaultWorldName);
                    regions.remove(key, region);
                    regions.put(keyOf(updated), updated);
                    if (migrated != null) migrated.accept(updated);
                } else {
                    Region merged = mergeGlobal(defaultGlobal, region);
                    regions.remove(key, region);
                    regions.put(keyOf(merged), merged);
                    if (migrated != null) migrated.accept(merged);
                }
                migratedCount++;
                if (logger != null) {
                    logger.info("Reconciled legacy world-less global region into world '"
                            + defaultWorldName + "'");
                }
                continue;
            }

            Region updated = region.withWorld(defaultWorldName);
            String targetKey = keyOf(updated);
            if (regions.containsKey(targetKey)) {
                if (logger != null) {
                    logger.warning("Region id '" + region.id()
                            + "' appears in multiple files during legacy migration; keeping the world-tagged region");
                }
                continue;
            }
            regions.remove(key);
            regions.put(targetKey, updated);
            if (migrated != null) migrated.accept(updated);
            migratedCount++;
        }
        return migratedCount;
    }

    private static Region mergeGlobal(Region current, Region legacy) {
        Region merged = current;
        for (Map.Entry<RegionFlag, Map<FlagTarget, Boolean>> flags : legacy.flagRules().entrySet()) {
            for (Map.Entry<FlagTarget, Boolean> rule : flags.getValue().entrySet()) {
                if (!merged.rulesFor(flags.getKey()).containsKey(rule.getKey())) {
                    merged = merged.withFlagRule(flags.getKey(), rule.getKey(), rule.getValue());
                }
            }
        }
        for (Map.Entry<RegionFlag, Set<org.bukkit.Material>> flags : legacy.materialFlags().entrySet()) {
            if (!merged.materialFlags().containsKey(flags.getKey())) {
                merged = merged.withMaterials(flags.getKey(), flags.getValue());
            }
        }
        for (Map.Entry<RegionFlag, Set<org.bukkit.entity.EntityType>> flags : legacy.entityFlags().entrySet()) {
            if (!merged.entityFlags().containsKey(flags.getKey())) {
                merged = merged.withEntities(flags.getKey(), flags.getValue());
            }
        }
        return merged;
    }

    private static final class BoundedOrphanSet extends AbstractSet<String> {
        private final int maxSize;
        private final Set<String> delegate = java.util.Collections.synchronizedSet(new LinkedHashSet<>());

        private BoundedOrphanSet(int maxSize) {
            this.maxSize = maxSize;
        }

        @Override
        public boolean add(String value) {
            synchronized (delegate) {
                boolean changed = delegate.add(value);
                while (delegate.size() > maxSize) {
                    Iterator<String> iterator = delegate.iterator();
                    iterator.next();
                    iterator.remove();
                }
                return changed;
            }
        }

        @Override
        public boolean remove(Object value) {
            return delegate.remove(value);
        }

        @Override
        public boolean contains(Object value) {
            return delegate.contains(value);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public Iterator<String> iterator() {
            synchronized (delegate) {
                return new ArrayList<>(delegate).iterator();
            }
        }
    }
}
