package me.beeliebub.tweaks.blocklog;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Reads and writes chest logs into chunk PDC. One byte[] per chest, keyed by a NamespacedKey
// that encodes the chest's chunk-local x/z and absolute y. All reads/writes must run on the
// main thread (Bukkit PDC is not thread-safe).
//
// Key format: "blocklog_<localX>_<y>_<localZ>" under the plugin namespace.
//   localX, localZ in [0, 15]; y is the absolute world y (signed int).
// Per-chest entry cap (NEWEST kept) prevents a single hot chest from ballooning the chunk blob.
final class ChunkLogStore {

    private static final String KEY_PREFIX = "blocklog_";
    static final int MAX_ENTRIES_PER_CHEST = 500;

    private final Plugin plugin;

    ChunkLogStore(Plugin plugin) {
        this.plugin = plugin;
    }

    NamespacedKey keyFor(Block block) {
        int localX = block.getX() & 15;
        int localZ = block.getZ() & 15;
        return new NamespacedKey(plugin, KEY_PREFIX + localX + "_" + block.getY() + "_" + localZ);
    }

    List<ChestLogEntry> read(Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        byte[] data = pdc.get(keyFor(block), PersistentDataType.BYTE_ARRAY);
        if (data == null) return new ArrayList<>();
        return ChestLogCodec.decode(data);
    }

    void append(Block block, ChestLogEntry entry) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        NamespacedKey key = keyFor(block);
        byte[] existing = pdc.get(key, PersistentDataType.BYTE_ARRAY);
        List<ChestLogEntry> entries = existing == null ? new ArrayList<>() : ChestLogCodec.decode(existing);
        entries.add(entry);
        if (entries.size() > MAX_ENTRIES_PER_CHEST) {
            entries = entries.subList(entries.size() - MAX_ENTRIES_PER_CHEST, entries.size());
        }
        pdc.set(key, PersistentDataType.BYTE_ARRAY, ChestLogCodec.encode(entries));
    }

    void appendAll(Block block, List<ChestLogEntry> newEntries) {
        if (newEntries.isEmpty()) return;
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        NamespacedKey key = keyFor(block);
        byte[] existing = pdc.get(key, PersistentDataType.BYTE_ARRAY);
        List<ChestLogEntry> entries = existing == null ? new ArrayList<>() : ChestLogCodec.decode(existing);
        entries.addAll(newEntries);
        if (entries.size() > MAX_ENTRIES_PER_CHEST) {
            entries = new ArrayList<>(entries.subList(entries.size() - MAX_ENTRIES_PER_CHEST, entries.size()));
        }
        pdc.set(key, PersistentDataType.BYTE_ARRAY, ChestLogCodec.encode(entries));
    }

    // Walk every blocklog_* key in the chunk; drop entries whose timestamp is below the cutoff.
    // Removes the key entirely if a chest's list ends up empty. Returns number of entries pruned.
    int pruneChunk(Chunk chunk, long cutoffMillis) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        Set<NamespacedKey> keys = new HashSet<>(pdc.getKeys());
        int pruned = 0;
        for (NamespacedKey key : keys) {
            if (!key.getNamespace().equals(plugin.getName().toLowerCase())) continue;
            if (!key.getKey().startsWith(KEY_PREFIX)) continue;

            byte[] data = pdc.get(key, PersistentDataType.BYTE_ARRAY);
            if (data == null) continue;

            List<ChestLogEntry> entries = ChestLogCodec.decode(data);
            int before = entries.size();
            entries.removeIf(e -> e.timestamp() < cutoffMillis);
            int after = entries.size();
            if (after == before) continue;

            pruned += (before - after);
            if (entries.isEmpty()) {
                pdc.remove(key);
            } else {
                pdc.set(key, PersistentDataType.BYTE_ARRAY, ChestLogCodec.encode(entries));
            }
        }
        return pruned;
    }

    static List<ChestLogEntry> safeRead(byte[] data) {
        if (data == null) return Collections.emptyList();
        return ChestLogCodec.decode(data);
    }

    static Location asLocation(Block block) {
        return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }
}