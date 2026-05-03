package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class ResourceCommand implements CommandExecutor {

    private final ResourceHunt resourceHunt;
    private final ResourceHuntItems resourceHuntItems;

    public ResourceCommand(ResourceHunt resourceHunt, ResourceHuntItems resourceHuntItems) {
        this.resourceHunt = resourceHunt;
        this.resourceHuntItems = resourceHuntItems;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 1. Check if the command sender is actually a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        String activeWorldKey = resourceHunt.getActiveWorldKey();

        // Only scan inventory when entering from outside the target resource world.
        // A player already in the specific resource world legally has whatever they're
        // carrying, and re-checking would block a /resource that is just a
        // "return to spawn within the world" convenience.
        boolean alreadyInTargetResource =
                activeWorldKey.equals(player.getWorld().getKey().asString());
        if (!alreadyInTargetResource) {
            List<Material> disallowed = resourceHuntItems.getDisallowedItems(player);
            if (!disallowed.isEmpty()) {
                String itemNames = disallowed.stream()
                        .map(m -> m.name().toLowerCase().replace('_', ' '))
                        .distinct()
                        .collect(Collectors.joining(", "));
                player.sendMessage(Component.text("You cannot enter the resource world with these items: ", NamedTextColor.RED)
                        .append(Component.text(itemNames, NamedTextColor.YELLOW)));
                return true;
            }
        }

        // 2. Locate the specific world
        String[] parts = activeWorldKey.split(":");
        World resourceWorld = Bukkit.getWorld(new NamespacedKey(parts[0], parts[1]));

        if (resourceWorld == null) {
            player.sendMessage(Component.text("The resource world (" + activeWorldKey + ") is currently unavailable.", NamedTextColor.RED));
            return true;
        }

        // 3. Calculate a safe spawn location
        Location defaultSpawn = resourceWorld.getSpawnLocation();
        Location safeLocation;

        if (resourceWorld.getEnvironment() == World.Environment.NETHER) {
            // In the Nether, we can't use getHighestBlockYAt (it gives the ceiling).
            // Attempt to find a safe 2-block air gap nearby, or fallback to a platform.
            Location found = findSafeNetherLocation(defaultSpawn);
            if (found != null) {
                safeLocation = found;
            } else {
                safeLocation = createBedrockPlatform(defaultSpawn);
                player.sendMessage(Component.text("No safe spot found; generated a temporary platform.", NamedTextColor.GOLD));
            }
        } else {
            // Get the highest solid block Y coordinate to prevent suffocation or falling
            int safeY = resourceWorld.getHighestBlockYAt(defaultSpawn.getBlockX(), defaultSpawn.getBlockZ());
            safeLocation = new Location(
                    resourceWorld,
                    defaultSpawn.getBlockX() + 0.5,
                    safeY + 1.0,
                    defaultSpawn.getBlockZ() + 0.5,
                    defaultSpawn.getYaw(),
                    defaultSpawn.getPitch()
            );
        }

        // 4. Teleport the player asynchronously (Recommended in PaperMC)
        player.sendMessage(Component.text("Teleporting to the resource world...", NamedTextColor.YELLOW));
        player.teleportAsync(safeLocation).thenAccept(success -> {
            if (success) {
                player.sendMessage(Component.text("Successfully teleported!", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Teleportation was cancelled or failed.", NamedTextColor.RED));
            }
        });

        return true;
    }

    private Location findSafeNetherLocation(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        // Search in a small radius for a 2-block air gap with a solid floor
        for (int x = cx - 5; x <= cx + 5; x++) {
            for (int z = cz - 5; z <= cz + 5; z++) {
                for (int y = 31; y < 120; y++) {
                    if (isSafe(world, x, y, z)) {
                        return new Location(world, x + 0.5, y, z + 0.5, center.getYaw(), center.getPitch());
                    }
                }
            }
        }
        return null;
    }

    private boolean isSafe(World world, int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType().isAir() &&
               world.getBlockAt(x, y + 1, z).getType().isAir() &&
               world.getBlockAt(x, y - 1, z).getType().isSolid();
    }

    private Location createBedrockPlatform(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int y = 64; // Middle-ish of the Nether

        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                world.getBlockAt(x, y, z).setType(Material.BEDROCK);
                world.getBlockAt(x, y + 1, z).setType(Material.AIR);
                world.getBlockAt(x, y + 2, z).setType(Material.AIR);
            }
        }
        return new Location(world, cx + 0.5, y + 1, cz + 0.5, center.getYaw(), center.getPitch());
    }
}
