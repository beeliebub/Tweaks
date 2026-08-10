package me.beeliebub.tweaks.skyblock.command.admin;

import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeService;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import me.beeliebub.tweaks.skyblock.island.SkyblockSpawn;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.utils.OfflinePlayerResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

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
        runtime.spawn().record(data);
        return Result.ok("spawn recorded");
    }

    public Result clearSpawn() {
        runtime.spawn().clear();
        return Result.ok("spawn cleared");
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

    public record Result(boolean success, String message) {
        public static Result ok(String message) { return new Result(true, message); }
        public static Result failed(String message) { return new Result(false, message); }
    }
}
