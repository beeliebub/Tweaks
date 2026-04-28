package me.beeliebub.tweaks.combos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// /afk toggles a player's AFK status. While AFK, the player is marked sleeping-ignored so they
// drop out of the sleep percentage calculation, and TabManager appends a red [AFK] suffix to
// their tab list name. Moving at least one block from the AFK origin clears the status; clicks
// or rotation-only PlayerMoveEvents do not (rotation does not change position, so the distance
// check stays at zero).
public class AfkCommand implements CommandExecutor, Listener {

    // Squared distance threshold (1 block) to bail out of AFK on movement
    private static final double EXIT_DISTANCE_SQ = 1.0;

    private final Map<UUID, Location> afkLocations = new HashMap<>();
    private TabManager tabManager;

    public void setTabManager(TabManager tabManager) {
        this.tabManager = tabManager;
    }

    public boolean isAfk(Player player) {
        return afkLocations.containsKey(player.getUniqueId());
    }

    private void enterAfk(Player player) {
        afkLocations.put(player.getUniqueId(), player.getLocation().clone());
        player.setSleepingIgnored(true);
        if (tabManager != null) tabManager.refreshTabName(player);
        player.sendMessage(Component.text("You are now AFK.", NamedTextColor.GRAY));
    }

    private void exitAfk(Player player, boolean announce) {
        if (afkLocations.remove(player.getUniqueId()) == null) return;
        player.setSleepingIgnored(false);
        if (tabManager != null) tabManager.refreshTabName(player);
        if (announce) {
            player.sendMessage(Component.text("You are no longer AFK.", NamedTextColor.GRAY));
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can go AFK.", NamedTextColor.RED));
            return true;
        }
        if (isAfk(player)) {
            exitAfk(player, true);
        } else {
            enterAfk(player);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location origin = afkLocations.get(player.getUniqueId());
        if (origin == null) return;

        Location to = event.getTo();
        if (to.getWorld() == null || !to.getWorld().equals(origin.getWorld())) {
            exitAfk(player, true);
            return;
        }
        if (to.distanceSquared(origin) >= EXIT_DISTANCE_SQ) {
            exitAfk(player, true);
        }
    }

    // Teleports do not raise PlayerMoveEvent — clear AFK explicitly when the destination is at
    // least a block away from where the player went AFK.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location origin = afkLocations.get(player.getUniqueId());
        if (origin == null) return;

        Location to = event.getTo();
        if (to == null) return;
        if (to.getWorld() == null || !to.getWorld().equals(origin.getWorld())
                || to.distanceSquared(origin) >= EXIT_DISTANCE_SQ) {
            exitAfk(player, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (afkLocations.remove(player.getUniqueId()) != null) {
            player.setSleepingIgnored(false);
        }
    }
}
