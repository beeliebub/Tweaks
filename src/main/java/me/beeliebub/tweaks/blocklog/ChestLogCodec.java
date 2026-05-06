package me.beeliebub.tweaks.blocklog;

import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
final class ChestLogCodec {

    private static final byte FORMAT_VERSION = 1;

    private ChestLogCodec() {}

    static byte[] encode(List<ChestLogEntry> entries) {
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

    static List<ChestLogEntry> decode(byte[] data) {
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