package me.beeliebub.tweaks.blocklog;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Data layer for the block-log subsystem: enum + record + binary codec + chunk-PDC store.
public final class BlockLogData {

    private BlockLogData() {}

    // ============================================================
    // LogAction
    // ============================================================
    // Persisted as a single byte (ordinal()).
    public enum LogAction {
        ADD,
        REMOVE;

        public static LogAction fromByte(byte b) {
            return b == 1 ? REMOVE : ADD;
        }

        public byte toByte() {
            return (byte) ordinal();
        }
    }

    // ============================================================
    // ChestLogEntry
    // ============================================================
    // One ADD/REMOVE event against a single chest at a specific moment. 'item' is the full
    // ItemStack so the viewer can display its meta (enchants, lore, name) on hover.
    public record ChestLogEntry(
            long timestamp,
            LogAction action,
            UUID playerUuid,
            String playerName,
            ItemStack item
    ) {
        public ChestLogEntry {
            if (action == null) throw new IllegalArgumentException("action");
            if (playerUuid == null) throw new IllegalArgumentException("playerUuid");
            if (playerName == null) playerName = "";
            if (item == null) throw new IllegalArgumentException("item");
        }
    }

    // ============================================================
    // Codec
    // ============================================================
    // Compact binary codec for a single chest's log list, stored in chunk PDC as byte[].
    //
    // Layout:
    //   [1 byte ] format version
    //   [4 bytes] entry count (int)
    //   foreach entry:
    //     [8 bytes] timestamp (millis)
    //     [1 byte ] action (ADD=0, REMOVE=1)
    //     [16 bytes] player UUID (msb then lsb)
    //     [2 bytes] player name length (short, UTF-8 byte length)
    //     [N bytes] player name UTF-8 bytes
    //     [4 bytes] item bytes length
    //     [M bytes] ItemStack.serializeAsBytes() output
    public static final class Codec {

        private static final byte FORMAT_VERSION = 1;

        private Codec() {}

        public static byte[] encode(List<ChestLogEntry> entries) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 DataOutputStream out = new DataOutputStream(baos)) {

                out.writeByte(FORMAT_VERSION);
                out.writeInt(entries.size());
                for (ChestLogEntry entry : entries) {
                    out.writeLong(entry.timestamp());
                    out.writeByte(entry.action().toByte());
                    out.writeLong(entry.playerUuid().getMostSignificantBits());
                    out.writeLong(entry.playerUuid().getLeastSignificantBits());

                    byte[] nameBytes = entry.playerName().getBytes(StandardCharsets.UTF_8);
                    if (nameBytes.length > Short.MAX_VALUE) {
                        nameBytes = truncate(nameBytes, Short.MAX_VALUE);
                    }
                    out.writeShort(nameBytes.length);
                    out.write(nameBytes);

                    byte[] itemBytes = entry.item().serializeAsBytes();
                    out.writeInt(itemBytes.length);
                    out.write(itemBytes);
                }
                return baos.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to encode chest log", e);
            }
        }

        public static List<ChestLogEntry> decode(byte[] data) {
            if (data == null || data.length == 0) return new ArrayList<>();

            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
                byte version = in.readByte();
                if (version != FORMAT_VERSION) return new ArrayList<>();

                int count = in.readInt();
                if (count < 0 || count > 1_000_000) return new ArrayList<>();

                List<ChestLogEntry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    long timestamp = in.readLong();
                    LogAction action = LogAction.fromByte(in.readByte());
                    long msb = in.readLong();
                    long lsb = in.readLong();
                    UUID uuid = new UUID(msb, lsb);

                    int nameLen = in.readShort() & 0xFFFF;
                    byte[] nameBytes = in.readNBytes(nameLen);
                    String name = new String(nameBytes, StandardCharsets.UTF_8);

                    int itemLen = in.readInt();
                    if (itemLen < 0) return entries;
                    byte[] itemBytes = in.readNBytes(itemLen);
                    ItemStack item = ItemStack.deserializeBytes(itemBytes);

                    entries.add(new ChestLogEntry(timestamp, action, uuid, name, item));
                }
                return entries;
            } catch (IOException e) {
                return new ArrayList<>();
            }
        }

        private static byte[] truncate(byte[] src, int max) {
            byte[] dst = new byte[max];
            System.arraycopy(src, 0, dst, 0, max);
            return dst;
        }
    }

    // ============================================================
    // ChunkLogStore
    // ============================================================
    // Reads and writes chest logs into chunk PDC. One byte[] per chest, keyed by a NamespacedKey
    // that encodes the chest's chunk-local x/z and absolute y. All reads/writes must run on the
    // main thread (Bukkit PDC is not thread-safe).
    //
    // Key format: "blocklog_<localX>_<y>_<localZ>" under the plugin namespace.
    //   localX, localZ in [0, 15]; y is the absolute world y (signed int).
    // Per-chest entry cap (NEWEST kept) prevents a single hot chest from ballooning the chunk blob.
    public static final class ChunkLogStore {

        private static final String KEY_PREFIX = "blocklog_";
        public static final int MAX_ENTRIES_PER_CHEST = 500;

        private final Plugin plugin;

        public ChunkLogStore(Plugin plugin) {
            this.plugin = plugin;
        }

        public NamespacedKey keyFor(Block block) {
            int localX = block.getX() & 15;
            int localZ = block.getZ() & 15;
            return new NamespacedKey(plugin, KEY_PREFIX + localX + "_" + block.getY() + "_" + localZ);
        }

        public List<ChestLogEntry> read(Block block) {
            PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
            byte[] data = pdc.get(keyFor(block), PersistentDataType.BYTE_ARRAY);
            if (data == null) return new ArrayList<>();
            return Codec.decode(data);
        }

        public void append(Block block, ChestLogEntry entry) {
            PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
            NamespacedKey key = keyFor(block);
            byte[] existing = pdc.get(key, PersistentDataType.BYTE_ARRAY);
            List<ChestLogEntry> entries = existing == null ? new ArrayList<>() : Codec.decode(existing);
            entries.add(entry);
            if (entries.size() > MAX_ENTRIES_PER_CHEST) {
                entries = entries.subList(entries.size() - MAX_ENTRIES_PER_CHEST, entries.size());
            }
            pdc.set(key, PersistentDataType.BYTE_ARRAY, Codec.encode(entries));
        }

        public void appendAll(Block block, List<ChestLogEntry> newEntries) {
            if (newEntries.isEmpty()) return;
            PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
            NamespacedKey key = keyFor(block);
            byte[] existing = pdc.get(key, PersistentDataType.BYTE_ARRAY);
            List<ChestLogEntry> entries = existing == null ? new ArrayList<>() : Codec.decode(existing);
            entries.addAll(newEntries);
            if (entries.size() > MAX_ENTRIES_PER_CHEST) {
                entries = new ArrayList<>(entries.subList(entries.size() - MAX_ENTRIES_PER_CHEST, entries.size()));
            }
            pdc.set(key, PersistentDataType.BYTE_ARRAY, Codec.encode(entries));
        }

        public int pruneChunk(Chunk chunk, long cutoffMillis) {
            PersistentDataContainer pdc = chunk.getPersistentDataContainer();
            Set<NamespacedKey> keys = new HashSet<>(pdc.getKeys());
            int pruned = 0;
            for (NamespacedKey key : keys) {
                if (!key.getNamespace().equals(plugin.getName().toLowerCase())) continue;
                if (!key.getKey().startsWith(KEY_PREFIX)) continue;

                byte[] data = pdc.get(key, PersistentDataType.BYTE_ARRAY);
                if (data == null) continue;

                List<ChestLogEntry> entries = Codec.decode(data);
                int before = entries.size();
                entries.removeIf(e -> e.timestamp() < cutoffMillis);
                int after = entries.size();
                if (after == before) continue;

                pruned += (before - after);
                if (entries.isEmpty()) {
                    pdc.remove(key);
                } else {
                    pdc.set(key, PersistentDataType.BYTE_ARRAY, Codec.encode(entries));
                }
            }
            return pruned;
        }

        public static List<ChestLogEntry> safeRead(byte[] data) {
            if (data == null) return Collections.emptyList();
            return Codec.decode(data);
        }

        public static Location asLocation(Block block) {
            return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
        }
    }
}
