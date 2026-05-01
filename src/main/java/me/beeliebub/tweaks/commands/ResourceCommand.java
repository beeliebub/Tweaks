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

    private final ResourceHuntItems resourceHuntItems;

    public ResourceCommand(ResourceHuntItems resourceHuntItems) {
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

        // Only scan inventory when entering from outside. A player already in jass:resource
        // legally has whatever they're carrying, and re-checking would block a /resource that
        // is just a "return to spawn within the world" convenience.
        boolean alreadyInResource =
                ResourceHunt.TARGET_WORLD_KEY.equals(player.getWorld().getKey().asString());
        if (!alreadyInResource) {
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
        // Bukkit.getWorld(String) matches by world *name* (folder name); we need the namespaced key.
        World resourceWorld = Bukkit.getWorld(new NamespacedKey("jass", "resource"));

        if (resourceWorld == null) {
            player.sendMessage(Component.text("The resource world (jass:resource) is currently unavailable.", NamedTextColor.RED));
            return true;
        }

        // 3. Calculate a safe spawn location
        Location defaultSpawn = resourceWorld.getSpawnLocation();

        // Get the highest solid block Y coordinate to prevent suffocation or falling
        int safeY = resourceWorld.getHighestBlockYAt(defaultSpawn.getBlockX(), defaultSpawn.getBlockZ());

        // Center the player on the block (X/Z + 0.5) and place them right above the highest block (Y + 1.0)
        Location safeLocation = new Location(
                resourceWorld,
                defaultSpawn.getBlockX() + 0.5,
                safeY + 1.0,
                defaultSpawn.getBlockZ() + 0.5,
                defaultSpawn.getYaw(),
                defaultSpawn.getPitch()
        );

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
}
