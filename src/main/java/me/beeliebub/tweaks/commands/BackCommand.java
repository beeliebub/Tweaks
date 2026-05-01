package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

// Saves a player's location before teleporting so they can return with /back.
// Stores the location in the player's PersistentDataContainer so it survives restarts.
public class BackCommand implements CommandExecutor, Listener {

    private final NamespacedKey backKey;
    private final ResourceHuntItems resourceHuntItems;

    public BackCommand(JavaPlugin plugin, ResourceHuntItems resourceHuntItems) {
        this.backKey = new NamespacedKey(plugin, "back_location");
        this.resourceHuntItems = resourceHuntItems;
    }

    // Record the player's location before each teleport (ignoring beds, dismounts, and unknown causes)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) return;
        if (cause == PlayerTeleportEvent.TeleportCause.DISMOUNT) return;
        if (cause == PlayerTeleportEvent.TeleportCause.EXIT_BED) return;
        saveLocation(event.getPlayer(), event.getFrom());
    }

    // Capture the death location so /back reliably brings players back to their items.
    // Vanilla respawn does not fire PlayerTeleportEvent, so we record explicitly here. If the
    // player teleports somewhere after respawning, the teleport listener will overwrite this
    // with the post-respawn location — matching the "unless they teleport somewhere else
    // first after dying" rule.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        saveLocation(player, player.getLocation());
    }

    private void saveLocation(Player player, Location loc) {
        if (loc.getWorld() == null) return;
        String serialized = loc.getWorld().getName()
                + "," + loc.getX()
                + "," + loc.getY()
                + "," + loc.getZ()
                + "," + loc.getYaw()
                + "," + loc.getPitch();
        player.getPersistentDataContainer()
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

            // Only scan inventory when the player is actually entering the resource world from
            // outside. Travel that stays within jass:resource (e.g. /back to a death location
            // inside the world) skips the check — the player already had those items legally.
            boolean enteringResourceFromOutside =
                    ResourceHunt.TARGET_WORLD_KEY.equals(world.getKey().asString())
                    && !ResourceHunt.TARGET_WORLD_KEY.equals(player.getWorld().getKey().asString());
            if (enteringResourceFromOutside) {
                List<Material> disallowed = resourceHuntItems.getDisallowedItems(player);
                if (!disallowed.isEmpty()) {
                    String itemNames = disallowed.stream()
                            .map(m -> m.name().toLowerCase().replace('_', ' '))
                            .distinct()
                            .collect(Collectors.joining(", "));
                    player.sendMessage(Component.text("You cannot return to the resource world with these items: ", NamedTextColor.RED)
                            .append(Component.text(itemNames, NamedTextColor.YELLOW)));
                    return true;
                }
            }

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