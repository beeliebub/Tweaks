package me.beeliebub.tweaks.skyblock.island;

import org.bukkit.block.Biome;
import org.bukkit.World;

import java.util.Objects;

/** Main-thread, chunk-budgeted biome application for a newly created island. */
public final class BiomePaster {
    private final World world;
    private final IslandGrid.ChunkBounds bounds;
    private final Biome biome;
    private final int y;
    private int nextChunk;

    public BiomePaster(World world, IslandGrid.ChunkBounds bounds, Biome biome, int y) {
        this.world = Objects.requireNonNull(world, "world");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.biome = Objects.requireNonNull(biome, "biome");
        this.y = y;
    }

    public int tick(int chunkBudget) {
        if (chunkBudget <= 0) return 0;
        int processed = 0;
        while (processed < chunkBudget && nextChunk < bounds.chunkCount()) {
            int width = bounds.width();
            int chunkX = bounds.minChunkX() + nextChunk % width;
            int chunkZ = bounds.minChunkZ() + nextChunk / width;
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    world.setBiome(chunkX * 16 + localX, y, chunkZ * 16 + localZ, biome);
                }
            }
            nextChunk++;
            processed++;
        }
        return processed;
    }

    public boolean complete() {
        return nextChunk >= bounds.chunkCount();
    }
}
