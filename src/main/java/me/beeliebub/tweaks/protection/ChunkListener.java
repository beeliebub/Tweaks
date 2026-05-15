package me.beeliebub.tweaks.protection;

import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Single ChunkLoadEvent listener that performs the two lazy-resolution
// passes required by the hybrid architecture:
//
//   1. Pending-stamp drain (Sprint 2.3): if the loading chunk's key is in
//      the pending stamps map, merge the queued RegionIDs into its PDC and
//      atomically remove the pending entry. Synchronous on purpose — the
//      ChunkLoadEvent fires on the main thread exactly when the chunk is
//      instantiated, so any deferred scheduling re-introduces the unload
//      race (Edge Case 5: stamping an orphaned chunk reference leaks
//      memory and silently loses the protection flag).
//
//   2. Orphan cleanup (Sprint 2.4): cross-reference the chunk's PDC with
//      the orphaned-regions set. Any dead RegionID is stripped from the
//      pointer list. This amortizes the physical cost of huge unclaims
//      over organic exploration instead of flooding the chunk I/O queue
//      with a thousand getChunkAtAsync calls at unclaim time
//      (Edge Case 4).
public final class ChunkListener implements Listener {

    private final ProtectionManager protection;

    public ChunkListener(ProtectionManager protection) {
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        long key = chunk.getChunkKey();

        drainPendingStamps(chunk, key);
        cleanOrphans(chunk);
    }

    private void drainPendingStamps(Chunk chunk, long key) {
        Set<String> pending = protection.pendingStamps().remove(key);
        if (pending == null || pending.isEmpty()) return;
        PDCUtil.append(chunk, pending);
    }

    private void cleanOrphans(Chunk chunk) {
        Set<String> orphaned = protection.orphanedRegions();
        if (orphaned.isEmpty()) return;

        List<String> current = PDCUtil.read(chunk);
        if (current.isEmpty()) return;

        Set<String> deadOnThisChunk = null;
        for (String id : current) {
            if (orphaned.contains(id)) {
                if (deadOnThisChunk == null) deadOnThisChunk = new HashSet<>();
                deadOnThisChunk.add(id);
            }
        }
        if (deadOnThisChunk != null) PDCUtil.remove(chunk, deadOnThisChunk);
    }
}
