package me.beeliebub.tweaks.tools.xp;

import me.beeliebub.tweaks.utils.BlockTaintStore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/** Prunes XP taint only when a previously-generated chunk is loaded. */
public final class TaintPruneListener implements Listener {

    private final BlockTaintStore store;
    private final XpSettings settings;

    public TaintPruneListener(BlockTaintStore store, XpSettings settings) {
        this.store = store;
        this.settings = settings;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) return;
        if (event.getChunk().getPersistentDataContainer().isEmpty()) return;
        store.pruneExpired(event.getChunk(), settings.taintTtlMillis());
    }
}
