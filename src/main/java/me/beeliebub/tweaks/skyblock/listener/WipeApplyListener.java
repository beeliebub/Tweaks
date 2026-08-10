package me.beeliebub.tweaks.skyblock.listener;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Applies durable Skyblock wipe markers before a joining player can use the world. */
public final class WipeApplyListener implements Listener {

    private final PendingWipeStore pendingWipes;
    private final ProfileWiper profileWiper;
    private final String frozenProfile;
    private final MissingIslandDetector missingIsland;
    private final SpawnProvider spawnProvider;
    private final JoinPreparation preparation;
    private final MainThreadExecutor mainThread;
    private final Logger logger;

    public WipeApplyListener(PendingWipeStore pendingWipes, ProfileWiper profileWiper,
                             String frozenProfile, MissingIslandDetector missingIsland,
                             SpawnProvider spawnProvider) {
        this(pendingWipes, profileWiper, frozenProfile, missingIsland, spawnProvider,
                ignored -> CompletableFuture.completedFuture(null), Runnable::run,
                Logger.getLogger(WipeApplyListener.class.getName()));
    }

    public WipeApplyListener(PendingWipeStore pendingWipes, ProfileWiper profileWiper,
                             String frozenProfile, MissingIslandDetector missingIsland,
                             SpawnProvider spawnProvider, Logger logger) {
        this(pendingWipes, profileWiper, frozenProfile, missingIsland, spawnProvider,
                ignored -> CompletableFuture.completedFuture(null), Runnable::run, logger);
    }

    public WipeApplyListener(PendingWipeStore pendingWipes, ProfileWiper profileWiper,
                             String frozenProfile, MissingIslandDetector missingIsland,
                             SpawnProvider spawnProvider, JoinPreparation preparation,
                             MainThreadExecutor mainThread, Logger logger) {
        this.pendingWipes = Objects.requireNonNull(pendingWipes, "pendingWipes");
        this.profileWiper = Objects.requireNonNull(profileWiper, "profileWiper");
        if (frozenProfile == null || frozenProfile.isBlank()) {
            throw new IllegalArgumentException("frozenProfile cannot be blank");
        }
        this.frozenProfile = frozenProfile;
        this.missingIsland = Objects.requireNonNull(missingIsland, "missingIsland");
        this.spawnProvider = Objects.requireNonNull(spawnProvider, "spawnProvider");
        this.preparation = Objects.requireNonNull(preparation, "preparation");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (pendingWipes.isPending(playerId)) {
            player.getInventory().clear();
            player.getEnderChest().clear();
            player.getOpenInventory().setCursor(new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
        }

        preparation.prepare(playerId).whenComplete((ignored, error) -> mainThread.execute(() -> {
            if (error != null) {
                logger.log(Level.WARNING, "Could not prepare Skyblock wipe for " + playerId, error);
                return;
            }
            if (!player.isOnline()) return;
            if (pendingWipes.isPending(playerId)) applyWipe(playerId);
            relocateIfNeeded(player, playerId);
        }));
    }

    private void relocateIfNeeded(Player player, UUID playerId) {
        Location current = player.getLocation();
        if (!missingIsland.isInsideMissingIsland(current)) return;
        World world = current.getWorld();
        if (world == null) return;
        Location destination = spawnProvider.spawn(world);
        if (destination == null) {
            logger.warning("Skyblock spawn provider returned no destination for " + playerId);
            return;
        }
        if (!player.teleport(destination)) {
            logger.warning("Could not relocate " + playerId + " from a missing Skyblock island");
        }
    }

    private void applyWipe(UUID playerId) {
        final CompletableFuture<Boolean> applied;
        try {
            applied = profileWiper.clearProfileDataAsync(playerId, frozenProfile);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Failed to apply pending Skyblock wipe for " + playerId,
                    exception);
            return;
        }
        applied.whenComplete((success, error) -> mainThread.execute(() -> {
            if (error != null) {
                logger.log(Level.WARNING, "Failed to apply pending Skyblock wipe for " + playerId,
                        error);
            } else if (Boolean.TRUE.equals(success)) {
                pendingWipes.remove(playerId);
            }
        }));
    }

    public interface PendingWipeStore {
        boolean isPending(UUID playerId);

        boolean remove(UUID playerId);
    }

    /** The adapter for the profile package's narrow wipe API. */
    @FunctionalInterface
    public interface ProfileWiper {
        boolean clearProfileData(UUID playerId, String frozenProfile);

        default CompletableFuture<Boolean> clearProfileDataAsync(UUID playerId, String frozenProfile) {
            return CompletableFuture.completedFuture(clearProfileData(playerId, frozenProfile));
        }
    }

    @FunctionalInterface
    public interface JoinPreparation {
        CompletableFuture<Void> prepare(UUID playerId);
    }

    @FunctionalInterface
    public interface MainThreadExecutor {
        void execute(Runnable action);
    }

    @FunctionalInterface
    public interface MissingIslandDetector {
        boolean isInsideMissingIsland(Location location);
    }

    @FunctionalInterface
    public interface SpawnProvider {
        Location spawn(World world);
    }
}
