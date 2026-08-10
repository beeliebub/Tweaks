package me.beeliebub.tweaks.skyblock.listener;

import me.beeliebub.tweaks.skyblock.island.IslandVisits;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/** World-scoped rules that do not require command or bootstrap ownership. */
public final class SkyblockWorldListener implements Listener {

    private static final MessageFacade NO_MESSAGES = player -> { };

    private final Predicate<World> skyblockWorld;
    private final Predicate<Player> regionAdmin;
    private final SpawnRules spawnRules;
    private final MessageFacade messages;
    private final IslandVisits visits;

    public SkyblockWorldListener(Predicate<World> skyblockWorld, String regionAdminPermission,
                                 SpawnRules spawnRules, MessageFacade messages,
                                 IslandVisits visits) {
        this(skyblockWorld, permissionCheck(regionAdminPermission), spawnRules, messages, visits);
    }

    public SkyblockWorldListener(Predicate<World> skyblockWorld, String regionAdminPermission,
                                 SpawnRules spawnRules, MessageFacade messages) {
        this(skyblockWorld, regionAdminPermission, spawnRules, messages, null);
    }

    public SkyblockWorldListener(Predicate<World> skyblockWorld, Predicate<Player> regionAdmin,
                                 SpawnRules spawnRules, MessageFacade messages,
                                 IslandVisits visits) {
        this.skyblockWorld = Objects.requireNonNull(skyblockWorld, "skyblockWorld");
        this.regionAdmin = Objects.requireNonNull(regionAdmin, "regionAdmin");
        this.spawnRules = Objects.requireNonNull(spawnRules, "spawnRules");
        this.messages = messages == null ? NO_MESSAGES : messages;
        this.visits = visits;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!skyblockWorld.test(player.getWorld()) || regionAdmin.test(player)) {
            return;
        }
        if (!isRegionCommand(event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        messages.regionDenied(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Location location = event.getLocation();
        if (!skyblockWorld.test(location.getWorld())
                || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        if (spawnRules.isSpawnRegion(location) || !spawnRules.isIslandTerrain(location)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (visits != null) {
            visits.onWorldChange(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (visits != null) {
            visits.onQuit(event.getPlayer().getUniqueId());
        }
    }

    private static boolean isRegionCommand(String message) {
        if (message == null || message.length() < 2 || message.charAt(0) != '/') {
            return false;
        }
        String label = message.substring(1).trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int namespaceSeparator = label.lastIndexOf(':');
        if (namespaceSeparator >= 0) {
            label = label.substring(namespaceSeparator + 1);
        }
        return label.equals("region") || label.equals("rg");
    }

    private static Predicate<Player> permissionCheck(String permission) {
        if (permission == null || permission.isBlank()) {
            throw new IllegalArgumentException("regionAdminPermission cannot be blank");
        }
        return player -> player.hasPermission(permission);
    }

    public interface SpawnRules {
        boolean isSpawnRegion(Location location);

        boolean isIslandTerrain(Location location);
    }

    public interface MessageFacade {
        void regionDenied(Player player);
    }
}
