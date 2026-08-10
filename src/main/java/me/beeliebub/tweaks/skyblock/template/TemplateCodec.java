package me.beeliebub.tweaks.skyblock.template;

import me.beeliebub.tweaks.skyblock.island.Island;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic binary/Base64 codec for the Bukkit-independent template payload. */
public final class TemplateCodec {

    private static final String MAGIC = "TWEAKS-SKYBLOCK-TEMPLATE-2";

    public TemplateCodec() {
    }

    public static String encode(IslandTemplate template) {
        return Base64.getEncoder().encodeToString(encodeBytes(template));
    }

    public static byte[] encodeBytes(IslandTemplate template) {
        if (template == null) throw new NullPointerException("template");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeUTF(MAGIC);
                output.writeUTF(template.id());
                output.writeInt(template.width());
                output.writeInt(template.height());
                output.writeInt(template.depth());
                output.writeInt(template.palette().size());
                for (String value : template.palette()) output.writeUTF(value);
                int[] indices = template.blockIndices();
                output.writeInt(indices.length);
                for (int index : indices) output.writeInt(index);
                Island.SpawnOffset offset = template.spawnOffset();
                output.writeInt(offset.x());
                output.writeInt(offset.y());
                output.writeInt(offset.z());
                List<Map.Entry<IslandTemplate.BlockEntity, String>> entities = new ArrayList<>(
                        template.blockEntities().entrySet());
                entities.sort(Comparator.comparingInt((Map.Entry<IslandTemplate.BlockEntity, String> entry) -> entry.getKey().y())
                        .thenComparingInt(entry -> entry.getKey().z())
                        .thenComparingInt(entry -> entry.getKey().x())
                        .thenComparing(entry -> entry.getKey().type()));
                output.writeInt(entities.size());
                for (Map.Entry<IslandTemplate.BlockEntity, String> entry : entities) {
                    IslandTemplate.BlockEntity key = entry.getKey();
                    output.writeInt(key.x());
                    output.writeInt(key.y());
                    output.writeInt(key.z());
                    output.writeUTF(key.type());
                    output.writeUTF(entry.getValue());
                }
            }
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Could not encode Skyblock template", error);
        }
    }

    public static IslandTemplate decode(String encoded) {
        if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("Template payload is blank");
        try {
            return decodeBytes(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Template payload is not valid Base64", error);
        }
    }

    public static IslandTemplate decodeBytes(byte[] encoded) {
        if (encoded == null || encoded.length == 0) throw new IllegalArgumentException("Template payload is empty");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (!MAGIC.equals(input.readUTF())) throw new IllegalArgumentException("Unsupported template version");
            String id = input.readUTF();
            int width = input.readInt();
            int height = input.readInt();
            int depth = input.readInt();
            int paletteSize = boundedCount(input.readInt(), "palette");
            List<String> palette = new ArrayList<>(paletteSize);
            for (int i = 0; i < paletteSize; i++) palette.add(input.readUTF());
            int indexCount = boundedCount(input.readInt(), "indices");
            int[] indices = new int[indexCount];
            for (int i = 0; i < indexCount; i++) indices[i] = input.readInt();
            Island.SpawnOffset offset = new Island.SpawnOffset(input.readInt(), input.readInt(), input.readInt());
            int entityCount = boundedCount(input.readInt(), "block entities");
            Map<IslandTemplate.BlockEntity, String> entities = new LinkedHashMap<>();
            for (int i = 0; i < entityCount; i++) {
                IslandTemplate.BlockEntity key = new IslandTemplate.BlockEntity(input.readInt(), input.readInt(),
                        input.readInt(), input.readUTF());
                entities.put(key, input.readUTF());
            }
            if (input.available() != 0) throw new IllegalArgumentException("Trailing template bytes");
            return new IslandTemplate(id, width, height, depth, palette, indices, entities, offset);
        } catch (EOFException error) {
            throw new IllegalArgumentException("Truncated template payload", error);
        } catch (IOException error) {
            throw new IllegalArgumentException("Could not decode Skyblock template", error);
        }
    }

    private static int boundedCount(int value, String label) {
        if (value < 0 || value > 10_000_000) throw new IllegalArgumentException("Invalid " + label + " count");
        return value;
    }
}
