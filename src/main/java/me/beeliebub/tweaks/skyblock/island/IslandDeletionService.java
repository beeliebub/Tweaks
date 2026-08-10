package me.beeliebub.tweaks.skyblock.island;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates safe island deletion. Ejection and region unclaim happen before the first terrain
 * chunk is cleared, while the durable cursor keeps a restart from reusing a half-cleared slot.
 */
public final class IslandDeletionService {

    private final World world;
    private final IslandGrid grid;
    private final int chunkBudget;
    private final ProgressStore progressStore;
    private final PlayerEjector ejector;
    private final SpawnProvider spawnProvider;
    private final RegionUnclaimer regionUnclaimer;
    private final ChunkSweeper chunkSweeper;
    private final SlotRegistry slots;
    private final CompletionHandler completionHandler;
    private final Logger logger;
    private final Map<String, DeletionProgress> active = new LinkedHashMap<>();
    private final java.util.Set<String> completionNotified = new java.util.HashSet<>();
    private final Map<String, CompletableFuture<Void>> completionFutures = new LinkedHashMap<>();
    private final Map<String, CompletableFuture<Void>> deletionFutures = new LinkedHashMap<>();

    public IslandDeletionService(World world, IslandGrid grid, int chunkBudget,
                                 ProgressStore progressStore, PlayerEjector ejector,
                                 SpawnProvider spawnProvider, RegionUnclaimer regionUnclaimer,
                                 ChunkSweeper chunkSweeper, SlotRegistry slots,
                                 CompletionHandler completionHandler) {
        this(world, grid, chunkBudget, progressStore, ejector, spawnProvider, regionUnclaimer,
                chunkSweeper, slots, completionHandler,
                Logger.getLogger(IslandDeletionService.class.getName()));
    }

    public IslandDeletionService(World world, IslandGrid grid, int chunkBudget,
                                 ProgressStore progressStore, PlayerEjector ejector,
                                 SpawnProvider spawnProvider, RegionUnclaimer regionUnclaimer,
                                 ChunkSweeper chunkSweeper, SlotRegistry slots,
                                 CompletionHandler completionHandler, Logger logger) {
        this.world = Objects.requireNonNull(world, "world");
        this.grid = Objects.requireNonNull(grid, "grid");
        if (chunkBudget <= 0) {
            throw new IllegalArgumentException("chunkBudget must be positive");
        }
        this.chunkBudget = chunkBudget;
        this.progressStore = Objects.requireNonNull(progressStore, "progressStore");
        this.ejector = Objects.requireNonNull(ejector, "ejector");
        this.spawnProvider = Objects.requireNonNull(spawnProvider, "spawnProvider");
        this.regionUnclaimer = Objects.requireNonNull(regionUnclaimer, "regionUnclaimer");
        this.chunkSweeper = Objects.requireNonNull(chunkSweeper, "chunkSweeper");
        this.slots = Objects.requireNonNull(slots, "slots");
        this.completionHandler = Objects.requireNonNull(completionHandler, "completionHandler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Loads durable deletion records and reserves their slots before listeners are registered. */
    public synchronized void resumePending() {
        Collection<DeletionProgress> persisted = progressStore.load();
        if (progressStore instanceof IslandDeletionStore store && store.hasInvalidRecord()) {
            throw new IllegalStateException("Malformed Skyblock deletion state is present; refusing to resume");
        }
        if (persisted == null) {
            return;
        }
        for (DeletionProgress progress : persisted) {
            if (progress == null) {
                continue;
            }
            if (active.containsKey(progress.islandId()) || hasActiveSlot(progress.slotIndex())) {
                throw new IllegalStateException("Duplicate durable Skyblock deletion record");
            }
            grid.slotForIndex(progress.slotIndex());
            if (!grid.chunkBoundsFor(progress.slotIndex(), IslandSize.LARGE).equals(progress.bounds())) {
                throw new IllegalStateException("Skyblock deletion bounds do not match the persisted slot");
            }
            active.put(progress.islandId(), progress);
            slots.reserve(progress.slotIndex());
        }
    }

    /** Starts a deletion and performs the ejection/unclaim phase synchronously. */
    public synchronized BeginResult begin(Island island) {
        Objects.requireNonNull(island, "island");
        if (active.containsKey(island.id())) {
            return BeginResult.ALREADY_IN_PROGRESS;
        }
        if (hasActiveSlot(island.slotIndex())) {
            return BeginResult.SLOT_BUSY;
        }

        IslandGrid.ChunkBounds bounds = grid.chunkBoundsFor(island.slotIndex(), IslandSize.LARGE);
        DeletionProgress progress = new DeletionProgress(
                island.id(), island.slotIndex(), bounds, 0, Stage.EJECTING);
        if (!persist(progress)) {
            return BeginResult.PERSISTENCE_FAILED;
        }
        slots.reserve(island.slotIndex());
        active.put(island.id(), progress);
        advancePreparation(island.id());
        return BeginResult.STARTED;
    }

    public synchronized boolean delete(Island island) {
        return begin(island) == BeginResult.STARTED;
    }

    /** Advances all active deletions, using one shared chunk budget for this tick. */
    public synchronized int tick() {
        return tick(chunkBudget);
    }

    public synchronized int tick(int budget) {
        if (budget <= 0) {
            return 0;
        }
        int cleared = 0;
        List<String> ids = new ArrayList<>(active.keySet());
        for (String islandId : ids) {
            if (budget <= 0) {
                break;
            }
            DeletionProgress progress = active.get(islandId);
            if (progress == null) {
                continue;
            }
            if (progress.stage() != Stage.SWEEPING && progress.stage() != Stage.COMPLETE) {
                advancePreparation(islandId);
                progress = active.get(islandId);
            }
            if (progress == null) {
                continue;
            }
            if (progress.stage() == Stage.SWEEPING) {
                while (budget > 0 && progress.nextChunkIndex() < progress.totalChunks()) {
                    if (!clearNextChunk(progress)) {
                        break;
                    }
                    budget--;
                    cleared++;
                    progress = active.get(islandId);
                    if (progress == null) {
                        break;
                    }
                }
                progress = active.get(islandId);
                if (progress != null && progress.stage() == Stage.SWEEPING
                        && progress.nextChunkIndex() == progress.totalChunks()) {
                    markComplete(progress);
                }
            }
            progress = active.get(islandId);
            if (progress != null && progress.stage() == Stage.COMPLETE) {
                finalizeDeletion(progress);
            }
        }
        return cleared;
    }

    public synchronized boolean isDeleting(String islandId) {
        return islandId != null && active.containsKey(islandId);
    }

    public synchronized boolean isSlotDeleting(int slotIndex) {
        return hasActiveSlot(slotIndex);
    }

    public synchronized List<DeletionProgress> pending() {
        return List.copyOf(active.values());
    }

    public synchronized java.util.Optional<DeletionProgress> progressFor(String islandId) {
        return java.util.Optional.ofNullable(active.get(islandId));
    }

    /** Waits for queued cursor writes when the backing store supports a bounded flush. */
    public CompletableFuture<Void> flush() {
        if (progressStore instanceof FlushableProgressStore flushable) return flushable.flushAsync();
        return CompletableFuture.completedFuture(null);
    }

    private void advancePreparation(String islandId) {
        DeletionProgress progress = active.get(islandId);
        if (progress == null) {
            return;
        }
        if (progress.stage() == Stage.EJECTING) {
            if (!ejectPlayers(progress.bounds())) {
                return;
            }
            DeletionProgress next = progress.withStage(Stage.UNCLAIMING);
            if (!persist(next)) {
                return;
            }
            active.put(islandId, next);
            progress = next;
        }
        if (progress.stage() != Stage.UNCLAIMING) {
            return;
        }
        if (!regionUnclaimer.unclaim(progress.islandId())) {
            return;
        }
        DeletionProgress next = progress.withStage(Stage.SWEEPING);
        if (persist(next)) {
            active.put(islandId, next);
        }
    }

    private boolean ejectPlayers(IslandGrid.ChunkBounds bounds) {
        Location destination = spawnProvider.spawn(world);
        if (destination == null) {
            logger.warning("Cannot eject players: Skyblock spawn is unavailable");
            return false;
        }
        Collection<Player> players = ejector.playersInside(world, bounds);
        if (players == null || players.isEmpty()) {
            return true;
        }
        boolean allEjected = true;
        for (Player player : new ArrayList<>(players)) {
            if (player == null || !ejector.eject(player, destination.clone())) {
                allEjected = false;
            }
        }
        return allEjected;
    }

    private boolean clearNextChunk(DeletionProgress progress) {
        int width = progress.bounds().width();
        int offset = progress.nextChunkIndex();
        int chunkX = progress.bounds().minChunkX() + offset % width;
        int chunkZ = progress.bounds().minChunkZ() + offset / width;
        final boolean cleared;
        try {
            cleared = chunkSweeper.clear(world, chunkX, chunkZ);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Failed to sweep Skyblock chunk " + chunkX + "," + chunkZ,
                    exception);
            return false;
        }
        if (!cleared) {
            return false;
        }
        DeletionProgress next = progress.advanceChunk();
        if (!persist(next)) {
            return false;
        }
        active.put(progress.islandId(), next);
        return true;
    }

    private void markComplete(DeletionProgress progress) {
        DeletionProgress complete = progress.withStage(Stage.COMPLETE);
        if (persist(complete)) {
            active.put(progress.islandId(), complete);
        }
    }

    private void finalizeDeletion(DeletionProgress progress) {
        CompletableFuture<Void> completion = completionFutures.get(progress.islandId());
        if (completion == null) {
            try {
                completion = completionHandler.completeAsync(progress);
                if (completion == null) completion = CompletableFuture.completedFuture(null);
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Failed to finish Skyblock deletion "
                        + progress.islandId(), exception);
                return;
            }
            completionNotified.add(progress.islandId());
            completionFutures.put(progress.islandId(), completion);
        }
        if (!completion.isDone()) return;
        if (completion.isCompletedExceptionally()) {
            logger.warning("Skyblock deletion completion is not durable yet for " + progress.islandId());
            completionFutures.remove(progress.islandId());
            completionNotified.remove(progress.islandId());
            return;
        }
        CompletableFuture<Void> delete = deletionFutures.get(progress.islandId());
        if (delete == null) {
            try {
                delete = progressStore instanceof AsyncProgressStore async
                        ? async.deleteAsync(progress.islandId())
                        : progressStore.delete(progress.islandId())
                        ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.failedFuture(new IllegalStateException("delete refused"));
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Failed to remove Skyblock deletion state "
                        + progress.islandId(), exception);
                return;
            }
            deletionFutures.put(progress.islandId(), delete);
        }
        if (!delete.isDone()) return;
        if (delete.isCompletedExceptionally()) {
            logger.warning("Skyblock deletion state is not removable yet for " + progress.islandId());
            deletionFutures.remove(progress.islandId());
            return;
        }
        try {
            slots.release(progress.slotIndex());
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Failed to release Skyblock slot " + progress.slotIndex(),
                    exception);
            return;
        }
        active.remove(progress.islandId());
        completionNotified.remove(progress.islandId());
        completionFutures.remove(progress.islandId());
        deletionFutures.remove(progress.islandId());
    }

    private boolean persist(DeletionProgress progress) {
        try {
            return progressStore.save(progress);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Failed to persist Skyblock deletion "
                    + progress.islandId(), exception);
            return false;
        }
    }

    private boolean hasActiveSlot(int slotIndex) {
        for (DeletionProgress progress : active.values()) {
            if (progress.slotIndex() == slotIndex) {
                return true;
            }
        }
        return false;
    }

    public enum BeginResult {
        STARTED,
        ALREADY_IN_PROGRESS,
        SLOT_BUSY,
        PERSISTENCE_FAILED
    }

    public enum Stage {
        EJECTING,
        UNCLAIMING,
        SWEEPING,
        COMPLETE
    }

    public record DeletionProgress(String islandId, int slotIndex, IslandGrid.ChunkBounds bounds,
                                   int nextChunkIndex, Stage stage) {
        public DeletionProgress {
            if (!Island.isValidId(islandId)) {
                throw new IllegalArgumentException("Invalid island id in deletion progress");
            }
            if (slotIndex < 0) {
                throw new IllegalArgumentException("slotIndex cannot be negative");
            }
            bounds = Objects.requireNonNull(bounds, "bounds");
            if (nextChunkIndex < 0 || nextChunkIndex > bounds.chunkCount()) {
                throw new IllegalArgumentException("Invalid deletion cursor: " + nextChunkIndex);
            }
            stage = Objects.requireNonNull(stage, "stage");
            if (stage == Stage.COMPLETE && nextChunkIndex != bounds.chunkCount()) {
                throw new IllegalArgumentException("A complete deletion must have a finished cursor");
            }
        }

        public int totalChunks() {
            return bounds.chunkCount();
        }

        public DeletionProgress advanceChunk() {
            if (nextChunkIndex >= totalChunks()) {
                return this;
            }
            return new DeletionProgress(islandId, slotIndex, bounds, nextChunkIndex + 1, stage);
        }

        public DeletionProgress withStage(Stage value) {
            return new DeletionProgress(islandId, slotIndex, bounds, nextChunkIndex, value);
        }
    }

    public interface ProgressStore {
        Collection<DeletionProgress> load();

        boolean save(DeletionProgress progress);

        boolean delete(String islandId);
    }

    public interface FlushableProgressStore {
        CompletableFuture<Void> flushAsync();
    }

    public interface AsyncProgressStore {
        CompletableFuture<Void> deleteAsync(String islandId);
    }

    public interface PlayerEjector {
        Collection<Player> playersInside(World world, IslandGrid.ChunkBounds bounds);

        boolean eject(Player player, Location destination);
    }

    @FunctionalInterface
    public interface SpawnProvider {
        Location spawn(World world);
    }

    @FunctionalInterface
    public interface RegionUnclaimer {
        boolean unclaim(String islandId);
    }

    @FunctionalInterface
    public interface ChunkSweeper {
        boolean clear(World world, int chunkX, int chunkZ);
    }

    public interface SlotRegistry {
        void reserve(int slotIndex);

        void release(int slotIndex);
    }

    @FunctionalInterface
    public interface CompletionHandler {
        void onCompleted(DeletionProgress progress);

        default CompletableFuture<Void> completeAsync(DeletionProgress progress) {
            onCompleted(progress);
            return CompletableFuture.completedFuture(null);
        }
    }
}
