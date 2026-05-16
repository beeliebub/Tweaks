package me.beeliebub.tweaks.protection;

import org.bukkit.Chunk;
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
public final class PDCUtil {

    private PDCUtil() {}

    // Snapshot of every RegionID currently stamped onto the chunk. Returns
    // an empty list when the chunk has never been stamped.
    public static List<String> read(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        return pdc.getOrDefault(
                ProtectionKeys.regionPointers(),
                PersistentDataType.LIST.strings(),
                List.of()
        );
    }

    public static void append(Chunk chunk, String regionId) {
        append(chunk, List.of(regionId));
    }

    // Merge regionIds into the chunk's pointer list, preserving existing
    // entries and naturally deduplicating. No-op if every supplied id is
    // already present (avoids needlessly dirtying the chunk).
    public static void append(Chunk chunk, Collection<String> regionIds) {
        if (regionIds.isEmpty()) return;

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> existing = pdc.getOrDefault(
                ProtectionKeys.regionPointers(),
                PersistentDataType.LIST.strings(),
                List.of()
        );

        LinkedHashSet<String> merged = new LinkedHashSet<>(existing);
        if (!merged.addAll(regionIds)) return;

        pdc.set(
                ProtectionKeys.regionPointers(),
                PersistentDataType.LIST.strings(),
                new ArrayList<>(merged)
        );
    }

    // Strip dead RegionIDs (orphan cleanup) from the chunk. Collapses the
    // key entirely when the result is empty so unprotected wilderness
    // chunks don't carry empty NBT lists forever.
    public static void remove(Chunk chunk, Set<String> deadIds) {
        if (deadIds.isEmpty()) return;

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> existing = pdc.getOrDefault(
                ProtectionKeys.regionPointers(),
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
            pdc.remove(ProtectionKeys.regionPointers());
        } else {
            pdc.set(
                    ProtectionKeys.regionPointers(),
                    PersistentDataType.LIST.strings(),
                    kept
            );
        }
    }
}
