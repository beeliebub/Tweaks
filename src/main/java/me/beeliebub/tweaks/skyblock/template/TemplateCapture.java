package me.beeliebub.tweaks.skyblock.template;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandGrid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Off-hot-path template capture with automatic vertical bounds and all-air rejection. */
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
        int scanMax = Math.min(maxY, minY + maxHeight);
        int minNonAir = Integer.MAX_VALUE;
        int maxNonAir = Integer.MIN_VALUE;
        int minX = horizontal.minChunkX() * 16;
        int minZ = horizontal.minChunkZ() * 16;
        int maxX = horizontal.maxChunkX() * 16 + 15;
        int maxZ = horizontal.maxChunkZ() * 16 + 15;
        for (int y = minY; y < scanMax; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!source.isAir(source.blockData(x, y, z))) {
                        minNonAir = Math.min(minNonAir, y);
                        maxNonAir = Math.max(maxNonAir, y);
                    }
                }
            }
        }
        if (minNonAir == Integer.MAX_VALUE) throw new AllAirException("The selected template contains only air");
        int bottom = Math.max(minY, minNonAir - 1);
        int top = Math.min(scanMax, maxNonAir + 2);
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

        default boolean isAir(String blockData) {
            return blockData == null || blockData.equalsIgnoreCase("minecraft:air")
                    || blockData.startsWith("minecraft:air[");
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
