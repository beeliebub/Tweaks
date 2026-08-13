package me.beeliebub.tweaks.skyblock.template;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandGrid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Captures a horizontal selection by searching the full caller-supplied column range and limiting the resulting height. */
public final class TemplateCapture {

    public IslandTemplate capture(BlockSource source, IslandGrid.ChunkBounds horizontal,
                                  int minY, int maxY, int maxHeight, String id,
                                  Island.SpawnOffset spawnOffset) {
        return capture(source, horizontal, minY, maxY, maxHeight, Long.MAX_VALUE, id, spawnOffset);
    }

    public IslandTemplate capture(BlockSource source, IslandGrid.ChunkBounds horizontal,
                                  int minY, int maxY, int maxHeight, long maxBlocks, String id,
                                  Island.SpawnOffset spawnOffset) {
        if (source == null || horizontal == null) throw new NullPointerException("source/horizontal");
        if (minY >= maxY || maxHeight <= 0) throw new IllegalArgumentException("Invalid capture range");
        CaptureEstimate estimate = estimate(horizontal, minY, maxY, maxHeight);
        if (maxBlocks <= 0 || estimate.estimatedBlocks() > maxBlocks) {
            throw new VolumeLimitException(estimate, maxBlocks);
        }
        int minNonAir = Integer.MAX_VALUE;
        int maxNonAir = Integer.MIN_VALUE;
        int minX = horizontal.minChunkX() * 16;
        int minZ = horizontal.minChunkZ() * 16;
        int maxX = horizontal.maxChunkX() * 16 + 15;
        int maxZ = horizontal.maxChunkZ() * 16 + 15;
        for (int y = minY; y < maxY; y++) {
            if (layerContainsNonAir(source, y, minX, minZ, maxX, maxZ)) {
                minNonAir = y;
                break;
            }
        }
        if (minNonAir == Integer.MAX_VALUE) {
            throw new AllAirException("The selected chunks contain no non-air blocks between y="
                    + minY + " and y=" + maxY);
        }
        for (int y = maxY - 1; y >= minY; y--) {
            if (layerContainsNonAir(source, y, minX, minZ, maxX, maxZ)) {
                maxNonAir = y;
                break;
            }
        }
        int bottom = Math.max(minY, minNonAir - 1);
        int top = Math.min(maxY, maxNonAir + 2);
        if (top - bottom > maxHeight) {
            bottom = minNonAir;
            top = maxNonAir + 1;
            if (top - bottom > maxHeight) {
                throw new HeightLimitException(top - bottom, maxHeight);
            }
        }
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        List<String> palette = new ArrayList<>();
        Map<String, Integer> paletteIndex = new LinkedHashMap<>();
        int[] indices = new int[Math.multiplyExact(Math.multiplyExact(width, top - bottom), depth)];
        int cursor = 0;
        for (int y = bottom; y < top; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    String data = source.blockData(x, y, z);
                    Integer index = paletteIndex.get(data);
                    if (index == null) {
                        index = palette.size();
                        paletteIndex.put(data, index);
                        palette.add(data);
                    }
                    indices[cursor++] = index;
                }
            }
        }
        return new IslandTemplate(id, width, top - bottom, depth, palette, indices,
                source.blockEntities(minX, bottom, minZ, maxX, top - 1, maxZ), spawnOffset);
    }

    private static boolean layerContainsNonAir(BlockSource source, int y,
                                               int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!source.isAirAt(x, y, z)) return true;
            }
        }
        return false;
    }

    public CaptureEstimate estimate(IslandGrid.ChunkBounds horizontal, int minY, int maxY, int maxHeight) {
        if (horizontal == null || minY >= maxY || maxHeight <= 0) throw new IllegalArgumentException("Invalid capture range");
        int scanMax = Math.min(maxY, minY + maxHeight);
        long width = (long) horizontal.maxChunkX() * 16 + 15 - ((long) horizontal.minChunkX() * 16) + 1;
        long depth = (long) horizontal.maxChunkZ() * 16 + 15 - ((long) horizontal.minChunkZ() * 16) + 1;
        long height = Math.max(0L, (long) scanMax - minY);
        long blocks;
        try {
            blocks = Math.multiplyExact(Math.multiplyExact(width, depth), height);
        } catch (ArithmeticException error) {
            blocks = Long.MAX_VALUE;
        }
        return new CaptureEstimate(Math.toIntExact(Math.min(Integer.MAX_VALUE, width)),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, height)),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, depth)), blocks);
    }

    public interface BlockSource {
        String blockData(int x, int y, int z);

        default boolean isAirAt(int x, int y, int z) {
            return isAir(blockData(x, y, z));
        }

        default boolean isAir(String blockData) {
            if (blockData == null) return true;
            int stateStart = blockData.indexOf('[');
            String material = stateStart < 0 ? blockData : blockData.substring(0, stateStart);
            return switch (material.toLowerCase(Locale.ROOT)) {
                case "minecraft:air", "minecraft:cave_air", "minecraft:void_air" -> true;
                default -> false;
            };
        }

        default Map<IslandTemplate.BlockEntity, String> blockEntities(int minX, int minY, int minZ,
                                                                        int maxX, int maxY, int maxZ) {
            return Map.of();
        }
    }

    public static final class AllAirException extends IllegalArgumentException {
        public AllAirException(String message) {
            super(message);
        }
    }

    public static final class HeightLimitException extends IllegalArgumentException {
        private final int height;
        private final int maximum;

        public HeightLimitException(int height, int maximum) {
            super("Template build is " + height + " blocks tall, maximum is " + maximum
                    + " (skyblock.template-max-height)");
            this.height = height;
            this.maximum = maximum;
        }

        public int height() { return height; }

        public int maximum() { return maximum; }
    }

    public record CaptureEstimate(int width, int height, int depth, long estimatedBlocks) { }

    public static final class VolumeLimitException extends IllegalArgumentException {
        private final CaptureEstimate estimate;
        private final long maximum;

        public VolumeLimitException(CaptureEstimate estimate, long maximum) {
            super("Template capture is " + estimate.width() + "x" + estimate.height() + "x"
                    + estimate.depth() + " (" + estimate.estimatedBlocks() + " blocks), maximum is " + maximum);
            this.estimate = estimate;
            this.maximum = maximum;
        }

        public CaptureEstimate estimate() { return estimate; }

        public long maximum() { return maximum; }
    }
}
