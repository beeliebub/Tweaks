package me.beeliebub.tweaks.minigames.andrew;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Defines the bounding box for a Whack-an-Andrew arena and tracks spawn block locations within it
public class WhackArena {

    private final World world;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    // Locations on top of designated spawn blocks where mannequins can appear
    private final List<Location> spawnBlocks = new ArrayList<>();

    public WhackArena(Location corner1, Location corner2) {
        this.world = corner1.getWorld();
        this.minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        this.minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        this.minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        this.maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        this.maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        this.maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());
    }

    public boolean contains(Location loc) {
        if (!loc.getWorld().equals(world)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public void addSpawnBlock(Location loc) {
        spawnBlocks.add(loc.toBlockLocation());
    }

    public void clearSpawnBlocks() {
        spawnBlocks.clear();
    }

    public List<Location> getSpawnBlocks() {
        return Collections.unmodifiableList(spawnBlocks);
    }

    /**
     * Scans the arena for all blocks matching the given types and registers them as spawn blocks.
     */
    public int scanForBlocks(org.bukkit.Material... types) {
        spawnBlocks.clear();
        java.util.Set<org.bukkit.Material> typeSet = java.util.EnumSet.noneOf(org.bukkit.Material.class);
        Collections.addAll(typeSet, types);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (typeSet.contains(block.getType())) {
                        // Mannequins spawn on top of the block
                        spawnBlocks.add(block.getLocation().add(0.5, 1.0, 0.5));
                    }
                }
            }
        }
        return spawnBlocks.size();
    }

    public World getWorld() { return world; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }
}