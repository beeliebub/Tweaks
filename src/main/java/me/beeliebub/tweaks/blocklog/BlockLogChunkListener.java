package me.beeliebub.tweaks.blocklog;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.concurrent.TimeUnit;

// On chunk load, prunes any chest log entries older than the 30-day retention window.
// Operates on the chunk's PDC, which means it must run on the main thread (handled by
// Bukkit's event dispatch). Pruning is cheap when there are no expired entries because
// the codec only re-encodes chests that actually shrank.
public final class BlockLogChunkListener implements Listener {

    static final long RETENTION_MILLIS = TimeUnit.DAYS.toMillis(30);

    private final ChestLogManager manager;

    public BlockLogChunkListener(ChestLogManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        long cutoff = System.currentTimeMillis() - RETENTION_MILLIS;
        manager.pruneChunk(event.getChunk(), cutoff);
    }
}