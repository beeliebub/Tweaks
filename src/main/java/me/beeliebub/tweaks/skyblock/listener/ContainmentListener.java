package me.beeliebub.tweaks.skyblock.listener;

import me.beeliebub.tweaks.skyblock.island.IslandGrid;
import me.beeliebub.tweaks.skyblock.island.IslandVisits;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Enforces the currently permitted Skyblock island or spawn area. */
public final class ContainmentListener implements Listener {

    private static final long DEFAULT_MESSAGE_COOLDOWN_MILLIS = 3_000L;
    private static final MessageFacade NO_MESSAGES = player -> { };

    private final AreaProvider areas;
    private final IslandVisits visits;
    private final Predicate<Player> bypass;
    private final MessageFacade messages;
    private final long messageCooldownMillis;
    private final LongSupplier clock;
    private final Map<UUID, Long> lastMessages = new HashMap<>();

    public ContainmentListener(AreaProvider areas, IslandVisits visits, String bypassPermission,
                               MessageFacade messages, long messageCooldownMillis,
                               LongSupplier clock) {
        this(areas, visits, permissionCheck(bypassPermission), messages, messageCooldownMillis, clock);
    }

    public ContainmentListener(AreaProvider areas, IslandVisits visits, String bypassPermission,
                               MessageFacade messages) {
        this(areas, visits, bypassPermission, messages, DEFAULT_MESSAGE_COOLDOWN_MILLIS,
                System::currentTimeMillis);
    }

    public ContainmentListener(AreaProvider areas, IslandVisits visits, Predicate<Player> bypass,
                               MessageFacade messages, long messageCooldownMillis,
                               LongSupplier clock) {
        this.areas = Objects.requireNonNull(areas, "areas");
        this.visits = Objects.requireNonNull(visits, "visits");
        this.bypass = Objects.requireNonNull(bypass, "bypass");
        this.messages = messages == null ? NO_MESSAGES : messages;
        if (messageCooldownMillis < 0) {
            throw new IllegalArgumentException("messageCooldownMillis cannot be negative");
        }
        this.messageCooldownMillis = messageCooldownMillis;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || !crossesChunkBoundary(from, to)) {
            return;
        }
        Player player = event.getPlayer();
        if (bypass.test(player) || !isSkyblockWorld(to.getWorld())) {
            return;
        }

        Optional<ContainmentArea> permitted = permittedArea(player);
        if (permitted.isEmpty() || permitted.get().contains(to)) {
            return;
        }
        event.setTo(permitted.get().spawnLocation());
        notifyViolation(player);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (bypass.test(player) || !isSkyblockWorld(to.getWorld())) {
            return;
        }

        Optional<ContainmentArea> permitted = permittedArea(player);
        if (permitted.isEmpty() || permitted.get().contains(to)) {
            return;
        }
        event.setTo(permitted.get().spawnLocation());
        notifyViolation(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Location current = event.getRespawnLocation();
        if (current == null || current.getWorld() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (bypass.test(player) || !isSkyblockWorld(current.getWorld())) {
            return;
        }

        Optional<ContainmentArea> permitted = permittedArea(player);
        if (permitted.isPresent() && !permitted.get().contains(current)) {
            event.setRespawnLocation(permitted.get().spawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        visits.onQuit(playerId);
        lastMessages.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        visits.onWorldChange(playerId);
        lastMessages.remove(playerId);
    }

    private Optional<ContainmentArea> permittedArea(Player player) {
        UUID playerId = player.getUniqueId();
        Optional<String> visited = visits.visitedIsland(playerId);
        if (visited.isPresent()) {
            String islandId = visited.get();
            if (!areas.isDeletionInProgress(islandId)) {
                Optional<ContainmentArea> visitedArea = areas.island(islandId);
                if (visitedArea.isPresent()) {
                    return visitedArea;
                }
            }
            visits.clear(playerId);
        }

        Optional<ContainmentArea> own = areas.ownIsland(playerId);
        if (own.isPresent() && !areas.isDeletionInProgress(own.get().id())) {
            return own;
        }

        Optional<ContainmentArea> spawn = areas.spawnRegion(player.getWorld());
        return spawn.isPresent() ? spawn : Optional.of(fallbackSpawn(player.getWorld()));
    }

    private boolean isSkyblockWorld(World world) {
        return world != null && areas.isSkyblockWorld(world);
    }

    private void notifyViolation(Player player) {
        long now = clock.getAsLong();
        Long previous = lastMessages.get(player.getUniqueId());
        if (previous != null && now >= previous && now - previous < messageCooldownMillis) {
            return;
        }
        lastMessages.put(player.getUniqueId(), now);
        messages.containmentDenied(player);
    }

    private static boolean crossesChunkBoundary(Location from, Location to) {
        if (from == null || from.getWorld() != to.getWorld()) {
            return true;
        }
        return (from.getBlockX() >> 4) != (to.getBlockX() >> 4)
                || (from.getBlockZ() >> 4) != (to.getBlockZ() >> 4);
    }

    private static ContainmentArea fallbackSpawn(World world) {
        Location spawn = world.getSpawnLocation();
        int chunkX = spawn.getBlockX() >> 4;
        int chunkZ = spawn.getBlockZ() >> 4;
        return new ContainmentArea(null, world,
                new IslandGrid.ChunkBounds(chunkX, chunkZ, chunkX, chunkZ), spawn);
    }

    private static Predicate<Player> permissionCheck(String permission) {
        if (permission == null || permission.isBlank()) {
            throw new IllegalArgumentException("bypassPermission cannot be blank");
        }
        return player -> player.hasPermission(permission);
    }

    public interface AreaProvider {
        Optional<ContainmentArea> ownIsland(UUID playerId);

        Optional<ContainmentArea> island(String islandId);

        Optional<ContainmentArea> spawnRegion(World world);

        boolean isSkyblockWorld(World world);

        default boolean isDeletionInProgress(String islandId) {
            return false;
        }
    }

    @FunctionalInterface
    public interface MessageFacade {
        void containmentDenied(Player player);
    }

    public record ContainmentArea(String id, World world, IslandGrid.ChunkBounds bounds,
                                  Location spawn) {
        public ContainmentArea {
            world = Objects.requireNonNull(world, "world");
            bounds = Objects.requireNonNull(bounds, "bounds");
            spawn = Objects.requireNonNull(spawn, "spawn").clone();
        }

        @Override
        public Location spawn() {
            return spawn.clone();
        }

        public Location spawnLocation() {
            return spawn.clone();
        }

        public boolean contains(Location location) {
            return location != null && location.getWorld() == world
                    && bounds.contains(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        }
    }
}
