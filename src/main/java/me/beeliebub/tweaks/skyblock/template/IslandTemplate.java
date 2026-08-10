package me.beeliebub.tweaks.skyblock.template;

import me.beeliebub.tweaks.skyblock.island.Island;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bukkit-independent, lossless template payload used by capture, storage, and paste. */
public record IslandTemplate(
        String id,
        int width,
        int height,
        int depth,
        List<String> palette,
        int[] blockIndices,
        Map<BlockEntity, String> blockEntities,
        Island.SpawnOffset spawnOffset
) {

    public IslandTemplate {
        id = id == null ? "" : id;
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Template dimensions must be positive");
        }
        long volume = (long) width * height * depth;
        if (volume > Integer.MAX_VALUE || blockIndices == null || blockIndices.length != volume) {
            throw new IllegalArgumentException("Template index array does not match its dimensions");
        }
        List<String> paletteCopy = new ArrayList<>();
        if (palette != null) {
            for (String value : palette) {
                if (value == null || value.isBlank()) throw new IllegalArgumentException("Palette entries cannot be blank");
                paletteCopy.add(value);
            }
        }
        if (paletteCopy.isEmpty()) throw new IllegalArgumentException("Template palette cannot be empty");
        palette = List.copyOf(paletteCopy);
        blockIndices = blockIndices.clone();
        for (int index : blockIndices) {
            if (index < 0 || index >= palette.size()) {
                throw new IllegalArgumentException("Template palette index is out of range: " + index);
            }
        }
        Map<BlockEntity, String> entityCopy = new LinkedHashMap<>();
        if (blockEntities != null) {
            for (Map.Entry<BlockEntity, String> entry : blockEntities.entrySet()) {
                entityCopy.put(Objects.requireNonNull(entry.getKey(), "block entity key"),
                        Objects.requireNonNull(entry.getValue(), "block entity payload"));
            }
        }
        blockEntities = Collections.unmodifiableMap(entityCopy);
        spawnOffset = spawnOffset == null ? new Island.SpawnOffset(0, 0, 0) : spawnOffset;
    }

    @Override
    public int[] blockIndices() {
        return blockIndices.clone();
    }

    public int indexAt(int x, int y, int z) {
        return blockIndices[index(x, y, z)];
    }

    public String blockDataAt(int x, int y, int z) {
        return palette.get(indexAt(x, y, z));
    }

    public int index(int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth) {
            throw new IndexOutOfBoundsException(x + "," + y + "," + z);
        }
        return (y * depth + z) * width + x;
    }

    public long volume() {
        return (long) width * height * depth;
    }

    public record BlockEntity(int x, int y, int z, String type) {
        public BlockEntity {
            if (x < 0 || y < 0 || z < 0) throw new IllegalArgumentException("Block entity coordinates cannot be negative");
            type = Objects.requireNonNull(type, "type");
        }
    }
}
