package me.beeliebub.tweaks.skyblock.command.admin;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeService;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandGrid;
import me.beeliebub.tweaks.skyblock.island.IslandRegionBridge;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import me.beeliebub.tweaks.skyblock.island.SkyblockSpawn;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.utils.OfflinePlayerResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Console-safe validation and mutation services behind the admin command and Dialog surfaces. */
public final class SkyblockAdminService {
    private final JavaPlugin plugin;
    private final SkyblockBootstrap.Runtime runtime;

    public SkyblockAdminService(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    public List<Island> listIslands() { return runtime.islandManager().all(); }

    public void resolveIsland(String input, org.bukkit.command.CommandSender sender, Consumer<Optional<Island>> callback) {
        Optional<Island> direct = directIsland(input);
        if (direct.isPresent()) { callback.accept(direct); return; }
        OfflinePlayerResolver.resolve(plugin, sender, input).whenComplete((offline, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null || offline == null || sender instanceof Player player
                            && (!plugin.isEnabled() || !player.isOnline()
                            || !player.hasPermission(Permissions.ADMIN_SKYBLOCK)
                            || player.getWorld() != runtime.world())) callback.accept(Optional.empty());
                    else callback.accept(runtime.islandManager().forPlayer(offline.getUniqueId()));
                }));
    }

    public Result forceDelete(Island island) {
        if (island == null) return Result.failed("island not found");
        var result = runtime.beginDeletion(island);
        return result == me.beeliebub.tweaks.skyblock.island.IslandDeletionService.BeginResult.STARTED
                ? Result.ok("deletion started") : Result.failed(result.name().toLowerCase(Locale.ROOT));
    }

    public Result setSize(Island island, IslandSize size) {
        if (island == null || size == null) return Result.failed("island or size not found");
        if (size.chunks() <= island.size().chunks()) return Result.failed("size can only increase");
        if (!runtime.regionBridge().resize(island, runtime.world(), size)) return Result.failed("protection resize failed");
        runtime.islandManager().update(island.withSize(size));
        return Result.ok("size updated");
    }

    /** Compatibility overload for player-facing callers that already performed permission checks. */
    public Result setSize(Island island, IslandSize size, Player ignoredActor) {
        return setSize(island, size);
    }

    public Result forceComplete(Island island, String challengeId, Player player) {
        if (runtime.challengeService() == null) return Result.failed("challenge service unavailable");
        ChallengeService.ClaimResult result = runtime.challengeService().forceComplete(island, challengeId, runtime.world());
        return result.claimed() ? Result.ok("challenge completed") : Result.failed(result.reason());
    }

    public Result forceComplete(Island island, String challengeId) {
        if (runtime.challengeService() == null) return Result.failed("challenge service unavailable");
        ChallengeService.ClaimResult result = runtime.challengeService().forceComplete(island, challengeId, runtime.world());
        return result.claimed() ? Result.ok("challenge completed") : Result.failed(result.reason());
    }

    public Result recordSpawn(SkyblockSpawn.SpawnData data) {
        if (data == null) return Result.failed("spawn data is required");
        if (runtime.world() == null || !matchesWorld(data.worldKey(), runtime.world())) {
            return Result.failed(Messages.SKYBLOCK.spawnWorldMismatchError());
        }
        if (!data.bounds().contains(0, 0)) {
            return Result.failed(Messages.SKYBLOCK.spawnBoundsOriginError());
        }
        if (data.owner() == null) {
            return Result.failed(Messages.SKYBLOCK.spawnOwnerRequiredError());
        }
        if (!data.bounds().contains(chunkOf(data.x()), chunkOf(data.z()))) {
            return Result.failed(Messages.SKYBLOCK.spawnPointBoundsError());
        }

        SkyblockSpawn spawn = runtime.spawn();
        IslandRegionBridge bridge = runtime.regionBridge();
        if (spawn == null || bridge == null) return Result.failed("spawn services are unavailable");

        Region previous = bridge.spawnRegion(runtime.world());
        if (previous != null) {
            ProtectionManager.UnclaimResult unclaim = bridge.unclaimSpawn(runtime.world()).result();
            if (unclaim != ProtectionManager.UnclaimResult.OK
                    && unclaim != ProtectionManager.UnclaimResult.UNKNOWN_REGION) {
                return Result.failed(Messages.SKYBLOCK.spawnRegionUnclaimError(unclaim.name()));
            }
        }

        AtomicReference<CompletableFuture<Void>> claimPersistence = new AtomicReference<>();
        ProtectionManager.ClaimResult claim = bridge.claimSpawn(runtime.world(), data, data.bounds(), previous,
                claimPersistence);
        if (claim != ProtectionManager.ClaimResult.OK) {
            if (previous != null && !restorePreviousSpawn(previous, data)) {
                plugin.getLogger().log(Level.SEVERE, "Skyblock spawn claim failed and the previous claim could not be restored");
            }
            return Result.failed(switch (claim) {
                case ID_TAKEN -> Messages.SKYBLOCK.spawnRegionIdTakenError();
                case OVERLAPS_FOREIGN_REGION -> Messages.SKYBLOCK.spawnRegionOverlapError();
                case OK -> "spawn claim succeeded";
            });
        }

        CompletableFuture<Void> claimDone = claimPersistence.get() == null
                ? CompletableFuture.completedFuture(null) : claimPersistence.get();
        CompletableFuture<Void> spawnWrite = spawn.record(data);
        return Result.ok("spawn recorded", CompletableFuture.allOf(claimDone, spawnWrite));
    }

    public Result clearSpawn() {
        SkyblockSpawn spawn = runtime.spawn();
        IslandRegionBridge bridge = runtime.regionBridge();
        if (spawn == null || bridge == null) return Result.failed("spawn services are unavailable");
        ProtectionManager.UnclaimResult unclaim = bridge.unclaimSpawn(runtime.world()).result();
        if (unclaim != ProtectionManager.UnclaimResult.OK
                && unclaim != ProtectionManager.UnclaimResult.UNKNOWN_REGION) {
            return Result.failed(Messages.SKYBLOCK.spawnRegionUnclaimError(unclaim.name()));
        }
        return Result.ok("spawn cleared", spawn.clear());
    }

    public Result reload() {
        new me.beeliebub.tweaks.skyblock.SkyblockReloadService(runtime.challengeRegistry(), runtime.typeRegistry(),
                runtime.generatorRegistry(), runtime.shopCatalog()).reloadAll();
        return Result.ok("Skyblock registries reloaded");
    }

    private Optional<Island> directIsland(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        Optional<Island> byId = runtime.islandManager().byId(input.toLowerCase(Locale.ROOT));
        if (byId.isPresent()) return byId;
        try { return runtime.islandManager().forPlayer(UUID.fromString(input)); }
        catch (IllegalArgumentException ignored) { }
        Player online = Bukkit.getPlayerExact(input);
        return online == null ? Optional.empty() : runtime.islandManager().forPlayer(online.getUniqueId());
    }

    private boolean restorePreviousSpawn(Region previous, SkyblockSpawn.SpawnData attempted) {
        if (previous.bounds() == null) return false;
        IslandGrid.ChunkBounds oldBounds = new IslandGrid.ChunkBounds(
                previous.bounds().minChunkX(), previous.bounds().minChunkZ(),
                previous.bounds().maxChunkX(), previous.bounds().maxChunkZ());
        SkyblockSpawn.SpawnData restored = new SkyblockSpawn.SpawnData(
                attempted.worldKey(), oldBounds, previous.owner(), attempted.x(), attempted.y(), attempted.z(),
                attempted.yaw(), attempted.pitch());
        AtomicReference<CompletableFuture<Void>> ignored = new AtomicReference<>();
        ProtectionManager.ClaimResult result = runtime.regionBridge().claimSpawn(
                runtime.world(), restored, oldBounds, previous, ignored);
        if (result != ProtectionManager.ClaimResult.OK) {
            plugin.getLogger().warning(Messages.SKYBLOCK.spawnRegionRollbackError(result.name()));
            return false;
        }
        return true;
    }

    private static int chunkOf(double coordinate) {
        return (int) Math.floor(Math.floor(coordinate) / 16.0D);
    }

    private static boolean matchesWorld(String configuredKey, org.bukkit.World world) {
        return configuredKey.equalsIgnoreCase(world.getKey().asString())
                || configuredKey.equalsIgnoreCase(world.getName());
    }

    public record Result(boolean success, String message, CompletableFuture<Void> persistence) {
        public Result(boolean success, String message) {
            this(success, message, CompletableFuture.completedFuture(null));
        }

        public Result {
            persistence = persistence == null ? CompletableFuture.completedFuture(null) : persistence;
        }

        public static Result ok(String message) { return new Result(true, message); }

        public static Result ok(String message, CompletableFuture<Void> persistence) {
            return new Result(true, message, persistence);
        }

        public static Result failed(String message) { return new Result(false, message); }
    }
}
