package me.beeliebub.tweaks.utils;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Read/append/remove of RegionID strings on a chunk's PersistentDataContainer.
// All operations route through PersistentDataType.LIST.strings() so the data
// survives chunk unloads + restarts using Mojang's native NBT format.
//
// Append-and-clean discipline (NEVER overwrite): blind pdc.set() on top of
// an existing pointer list would erase prior overlapping claims. Every write
// path reads the current list, merges, and writes back the deduplicated
// result. Removal collapses an empty list to a key delete so unprotected
// chunks carry no residual NBT.
//
// The pointer key is passed in by the caller (ProtectionManager owns it) rather
// than resolved from a static, so this class has no dependency on the protection
// package or on plugin-instance state.
public final class PDCUtil {

    private PDCUtil() {}

    // Snapshot of every RegionID currently stamped onto the chunk. Returns
    // an empty list when the chunk has never been stamped.
    public static List<String> read(Chunk chunk, NamespacedKey regionPointersKey) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        return pdc.getOrDefault(
                regionPointersKey,
                PersistentDataType.LIST.strings(),
                List.of()
        );
    }

    public static void append(Chunk chunk, String regionId, NamespacedKey regionPointersKey) {
        append(chunk, List.of(regionId), regionPointersKey);
    }

    // Merge regionIds into the chunk's pointer list, preserving existing
    // entries and naturally deduplicating. No-op if every supplied id is
    // already present (avoids needlessly dirtying the chunk).
    public static void append(Chunk chunk, Collection<String> regionIds, NamespacedKey regionPointersKey) {
        if (regionIds.isEmpty()) return;

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> existing = pdc.getOrDefault(
                regionPointersKey,
                PersistentDataType.LIST.strings(),
                List.of()
        );

        LinkedHashSet<String> merged = new LinkedHashSet<>(existing);
        if (!merged.addAll(regionIds)) return;

        pdc.set(
                regionPointersKey,
                PersistentDataType.LIST.strings(),
                new ArrayList<>(merged)
        );
    }

    // Strip dead RegionIDs (orphan cleanup) from the chunk. Collapses the
    // key entirely when the result is empty so unprotected wilderness
    // chunks don't carry empty NBT lists forever.
    public static void remove(Chunk chunk, Set<String> deadIds, NamespacedKey regionPointersKey) {
        if (deadIds.isEmpty()) return;

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> existing = pdc.getOrDefault(
                regionPointersKey,
                PersistentDataType.LIST.strings(),
                List.of()
        );
        if (existing.isEmpty()) return;

        List<String> kept = new ArrayList<>(existing.size());
        boolean removed = false;
        for (String id : existing) {
            if (deadIds.contains(id)) {
                removed = true;
            } else {
                kept.add(id);
            }
        }
        if (!removed) return;

        if (kept.isEmpty()) {
            pdc.remove(regionPointersKey);
        } else {
            pdc.set(
                    regionPointersKey,
                    PersistentDataType.LIST.strings(),
                    kept
            );
        }
    }
}
