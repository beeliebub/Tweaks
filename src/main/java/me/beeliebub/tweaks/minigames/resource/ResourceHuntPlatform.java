package me.beeliebub.tweaks.minigames.resource;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/** Resource-world identification and the Nether safety-platform fallback. */
final class ResourceHuntPlatform {
    private ResourceHuntPlatform() {
    }

    static boolean isResourceWorld(String worldKey) {
        return worldKey.contains(ResourceHunt.TARGET_WORLD_KEY)
                || worldKey.contains(ResourceHunt.TARGET_WORLD_NETHER_KEY);
    }

    static Location createBedrockPlatform(World world, int centerX, int centerZ) {
        int y = 64;
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                world.getBlockAt(x, y, z).setType(Material.BEDROCK);
                world.getBlockAt(x, y + 1, z).setType(Material.AIR);
                world.getBlockAt(x, y + 2, z).setType(Material.AIR);
            }
        }
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                if (x == centerX - 2 || x == centerX + 2 || z == centerZ - 2 || z == centerZ + 2) {
                    world.getBlockAt(x, y + 1, z).setType(Material.NETHER_BRICK_FENCE);
                }
            }
        }
        return new Location(world, centerX + 0.5, y + 1, centerZ + 0.5);
    }
}
