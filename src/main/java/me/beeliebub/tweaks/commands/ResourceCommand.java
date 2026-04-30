package me.beeliebub.tweaks.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ResourceCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 1. Check if the command sender is actually a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

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
