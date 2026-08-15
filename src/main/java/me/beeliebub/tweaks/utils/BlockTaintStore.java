package me.beeliebub.tweaks.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.Chunk;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;

/** Chunk-PDC store for persistent placed-block taint markers. */
public final class BlockTaintStore {

    private final String namespace;
    private final String prefix;

    public BlockTaintStore(JavaPlugin plugin, String prefix) {
        if (plugin == null) throw new IllegalArgumentException("plugin");
        if (prefix == null || prefix.isBlank() || !prefix.matches("[a-z0-9._/-]+")) {
            throw new IllegalArgumentException("Invalid taint prefix: " + prefix);
        }
        this.namespace = plugin.getName().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        this.prefix = prefix.endsWith("_") ? prefix : prefix + "_";
    }

    public void mark(Block block) {
        if (block == null) return;
        block.getChunk().getPersistentDataContainer().set(key(block), PersistentDataType.LONG,
                System.currentTimeMillis());
    }

    public boolean isTainted(Block block) {
        if (block == null) return false;
        try {
            return block.getChunk().getPersistentDataContainer().has(key(block), PersistentDataType.LONG);
        } catch (IllegalArgumentException malformed) {
            block.getChunk().getPersistentDataContainer().remove(key(block));
            return false;
        }
    }

    public boolean consume(Block block) {
        if (block == null) return false;
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        NamespacedKey key = key(block);
        try {
            if (!pdc.has(key, PersistentDataType.LONG)) return false;
        } catch (IllegalArgumentException malformed) {
            pdc.remove(key);
            return false;
        }
        pdc.remove(key);
        return true;
    }

    /** Removes this store's expired or malformed markers and preserves unrelated chunk PDC keys. */
    public int pruneExpired(Chunk chunk, long ttlMillis) {
        if (chunk == null) return 0;
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (pdc.isEmpty()) return 0;
        long cutoff = System.currentTimeMillis() - Math.max(0L, ttlMillis);
        int removed = 0;
        for (NamespacedKey stored : new ArrayList<>(pdc.getKeys())) {
            if (!stored.getNamespace().equals(namespace) || !stored.getKey().startsWith(prefix)) continue;
            Long timestamp;
            try {
                timestamp = pdc.get(stored, PersistentDataType.LONG);
            } catch (IllegalArgumentException malformed) {
                timestamp = null;
            }
            if (timestamp == null || timestamp < cutoff) {
                pdc.remove(stored);
                removed++;
            }
        }
        return removed;
    }

    public String prefix() {
        return prefix;
    }

    private NamespacedKey key(Block block) {
        int localX = block.getX() & 0xF;
        int localZ = block.getZ() & 0xF;
        long y = block.getY();
        String encodedY = y < 0 ? "n" + -y : "p" + y;
        return new NamespacedKey(namespace, prefix + localX + "_" + encodedY + "_" + localZ);
    }
}
