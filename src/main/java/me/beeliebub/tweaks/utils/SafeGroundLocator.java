package me.beeliebub.tweaks.utils;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.concurrent.CompletableFuture;

/**
 * Finds a non-mutating destination near the centre of a claim.
 *
 * <p>The chunk is requested asynchronously and all world/chunk reads happen only after that
 * future completes. The helper deliberately does not promise collision or lava safety beyond the
 * two-block-air check; those edge cases remain the caller's responsibility.</p>
 */
public final class SafeGroundLocator {

    private SafeGroundLocator() {}

    /** Result of a centre lookup; {@code groundFound=false} identifies the void fallback. */
    public record Result(Location location, boolean groundFound) {}

    /**
     * Loads the centre chunk asynchronously and searches its centre column for safe ground.
     * Empty columns fall back without placing or changing any blocks.
     */
    public static CompletableFuture<Result> findSafeCenter(
            World world, double centerX, double centerZ, float yaw, float pitch) {
        int blockX = (int) Math.floor(centerX);
        int blockZ = (int) Math.floor(centerZ);
        int chunkX = Math.floorDiv(blockX, 16);
        int chunkZ = Math.floorDiv(blockZ, 16);
        return world.getChunkAtAsync(chunkX, chunkZ, true)
                .thenApply(chunk -> findInLoadedChunk(
                        world, chunk, blockX, blockZ, centerX, centerZ, yaw, pitch));
    }

    private static Result findInLoadedChunk(World world, Chunk chunk, int blockX, int blockZ,
                                            double centerX, double centerZ,
                                            float yaw, float pitch) {
        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();
        int localX = Math.floorMod(blockX, 16);
        int localZ = Math.floorMod(blockZ, 16);
        int highest = world.getHighestBlockYAt(blockX, blockZ);
        int start = Math.min(maxHeight - 2, highest);

        for (int groundY = start; groundY >= minHeight; groundY--) {
            if (groundY + 2 >= maxHeight) continue;
            Block ground = chunk.getBlock(localX, groundY, localZ);
            Block feet = chunk.getBlock(localX, groundY + 1, localZ);
            Block head = chunk.getBlock(localX, groundY + 2, localZ);
            if (isGround(ground.getType()) && feet.isPassable() && head.isPassable()) {
                return new Result(new Location(world, centerX, groundY + 1, centerZ, yaw, pitch), true);
            }
        }

        int fallbackY = Math.max(minHeight + 1, 64);
        return new Result(new Location(world, centerX, fallbackY, centerZ, yaw, pitch), false);
    }

    private static boolean isGround(Material material) {
        return material.isSolid();
    }
}
