package me.beeliebub.tweaks.combos;

import me.beeliebub.tweaks.Tweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// /afk toggles a player's AFK status. While AFK, the player is marked sleeping-ignored so they
// drop out of the sleep percentage calculation, and TabManager appends a red [AFK] suffix to
// their tab list name. Moving at least one block from the AFK origin clears the status; clicks
// or rotation-only PlayerMoveEvents do not (rotation does not change position, so the distance
// check stays at zero).
//
// Players are also auto-marked AFK after AUTO_AFK_MILLIS of no positional movement. The idle
// timer is refreshed only on actual position changes (not rotation, not clicks, not chat),
// matching the rule that clicks do not exit AFK.
public class AfkCommand implements CommandExecutor, Listener {

    // Squared distance threshold (1 block) to bail out of AFK on movement
    private static final double EXIT_DISTANCE_SQ = 1.0;

    // Auto-AFK after 10 minutes of no positional movement.
    private static final long AUTO_AFK_MILLIS = 10L * 60L * 1000L;

    // Run the idle check every 30 seconds (in ticks).
    private static final long IDLE_CHECK_PERIOD_TICKS = 20L * 30L;

    private final Tweaks plugin;
    private final Map<UUID, Location> afkLocations = new HashMap<>();
    private final Map<UUID, Long> lastMovementMs = new HashMap<>();
    private TabManager tabManager;
    private BukkitTask idleCheckTask;

    public AfkCommand(Tweaks plugin) {
        this.plugin = plugin;
    }

    public void setTabManager(TabManager tabManager) {
        this.tabManager = tabManager;
    }

    public void start() {
        if (idleCheckTask != null) return;
        long now = System.currentTimeMillis();
        for (Player online : Bukkit.getOnlinePlayers()) {
            lastMovementMs.put(online.getUniqueId(), now);
        }
        idleCheckTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::checkIdle, IDLE_CHECK_PERIOD_TICKS, IDLE_CHECK_PERIOD_TICKS);
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
        // Reset the idle timer so a player who manually un-AFK's isn't auto-re-AFK'd next tick.
        lastMovementMs.put(player.getUniqueId(), System.currentTimeMillis());
        if (announce) {
            player.sendMessage(Component.text("You are no longer AFK.", NamedTextColor.GRAY));
        }
    }

    private void checkIdle() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isAfk(player)) continue;
            Long last = lastMovementMs.get(player.getUniqueId());
            if (last == null) continue;
            if (now - last >= AUTO_AFK_MILLIS) {
                enterAfk(player);
            }
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        lastMovementMs.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        // Refresh the idle timer only on actual position changes (rotation-only events
        // share identical x/y/z and must not count as activity, matching the click rule).
        boolean moved = to.getX() != from.getX()
                || to.getY() != from.getY()
                || to.getZ() != from.getZ();
        if (moved) {
            lastMovementMs.put(player.getUniqueId(), System.currentTimeMillis());
        }

        Location origin = afkLocations.get(player.getUniqueId());
        if (origin == null) return;

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
        lastMovementMs.put(player.getUniqueId(), System.currentTimeMillis());

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
        lastMovementMs.remove(player.getUniqueId());
        if (afkLocations.remove(player.getUniqueId()) != null) {
            player.setSleepingIgnored(false);
        }
    }
}
