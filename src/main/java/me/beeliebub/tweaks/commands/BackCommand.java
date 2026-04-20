package me.beeliebub.tweaks.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

// Saves a player's location before teleporting so they can return with /back.
// Stores the location in the player's PersistentDataContainer so it survives restarts.
public class BackCommand implements CommandExecutor, Listener {

    private final NamespacedKey backKey;

    public BackCommand(JavaPlugin plugin) {
        this.backKey = new NamespacedKey(plugin, "back_location");
    }

    // Record the player's location before each teleport (ignoring beds, dismounts, and unknown causes)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) return;
        if (cause == PlayerTeleportEvent.TeleportCause.DISMOUNT) return;
        if (cause == PlayerTeleportEvent.TeleportCause.EXIT_BED) return;
        Location from = event.getFrom();
        String serialized = from.getWorld().getName()
                + "," + from.getX()
                + "," + from.getY()
                + "," + from.getZ()
                + "," + from.getYaw()
                + "," + from.getPitch();

        event.getPlayer().getPersistentDataContainer()
                .set(backKey, PersistentDataType.STRING, serialized);
    }

    // Teleport the player back to their last saved location
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /back.").color(NamedTextColor.RED));
            return true;
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String raw = pdc.get(backKey, PersistentDataType.STRING);

        if (raw == null || raw.isEmpty()) {
            player.sendMessage(Component.text("No previous location found.").color(NamedTextColor.RED));
            return true;
        }

        String[] parts = raw.split(",");
        if (parts.length != 6) {
            player.sendMessage(Component.text("Stored location data is corrupt.").color(NamedTextColor.RED));
            pdc.remove(backKey);
            return true;
        }

        try {
            var world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                player.sendMessage(Component.text("The world for your previous location is not loaded.")
                        .color(NamedTextColor.RED));
                return true;
            }

            Location loc = new Location(world,
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]),
                    Float.parseFloat(parts[5]));

            player.teleportAsync(loc).thenAccept(success -> {
                if (success) {
                    player.sendMessage(Component.text("Teleported to your previous location!")
                            .color(NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Teleportation failed.").color(NamedTextColor.RED));
                }
            });

        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Stored location data is corrupt.").color(NamedTextColor.RED));
            pdc.remove(backKey);
        }

        return true;
    }
}