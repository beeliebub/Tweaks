package me.beeliebub.tweaks.skyblock.island;

import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.skyblock.SkyblockConfig;
import me.beeliebub.tweaks.skyblock.economy.SkyblockEconomy;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Validates and runs the wallet-backed, chunk-budgeted island biome sink. */
public final class IslandBiomeService {
    private final JavaPlugin plugin;
    private final IslandManager islands;
    private final SkyblockEconomy economy;
    private final SkyblockConfig config;
    private final World world;
    private final int chunksPerTick;
    private final Set<Job> active = ConcurrentHashMap.newKeySet();

    public IslandBiomeService(JavaPlugin plugin, IslandManager islands, SkyblockEconomy economy,
                              SkyblockConfig config, World world, int chunksPerTick) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.islands = Objects.requireNonNull(islands, "islands");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.config = Objects.requireNonNull(config, "config");
        this.world = Objects.requireNonNull(world, "world");
        this.chunksPerTick = Math.max(1, chunksPerTick);
    }

    public Result begin(Player player, Biome biome, Consumer<Result> completion) {
        if (player == null || player.getWorld() != world || !player.hasPermission(Permissions.SKYBLOCK_USE)) {
            return Result.rejected("Skyblock biome changes are unavailable");
        }
        Island island = islands.forPlayer(player.getUniqueId()).orElse(null);
        if (island == null) return Result.rejected("You do not have a Skyblock island");
        if (biome == null) return Result.rejected("unknown biome");
        double price = config.biomeChangePrice();
        if (!Double.isFinite(price) || price < 0) return Result.rejected("biome price is invalid");
        if (economy.debit(island, price) != SkyblockEconomy.Mutation.APPLIED) {
            return Result.rejected("Insufficient island funds");
        }
        BiomePaster paster = new BiomePaster(world,
                islands.grid().chunkBoundsFor(island.slotIndex(), island.size()), biome, config.islandY());
        Job job = new Job(island, price, paster, completion);
        active.add(job);
        job.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(job), 1L, 1L);
        return Result.started("biome change started");
    }

    private void tick(Job job) {
        try {
            job.paster.tick(chunksPerTick);
            if (!job.paster.complete()) return;
            if (job.task != null) job.task.cancel();
            job.done = true;
            active.remove(job);
            complete(job, Result.applied("biome changed"));
        } catch (RuntimeException error) {
            if (job.task != null) job.task.cancel();
            if (!job.done) {
                job.done = true;
                active.remove(job);
                economy.credit(job.island, job.price);
                complete(job, Result.rejected("biome change failed; the wallet was refunded"));
            }
        }
    }

    /** Cancels unfinished jobs and refunds their reserved wallet amount during plugin shutdown. */
    public void shutdown() {
        for (Job job : active.toArray(Job[]::new)) {
            if (job.task != null) job.task.cancel();
            if (job.done || !active.remove(job)) continue;
            job.done = true;
            economy.credit(job.island, job.price);
        }
    }

    private static void complete(Job job, Result result) {
        if (job.completion != null) job.completion.accept(result);
    }

    private static final class Job {
        private final Island island;
        private final double price;
        private final BiomePaster paster;
        private final Consumer<Result> completion;
        private BukkitTask task;
        private boolean done;

        private Job(Island island, double price, BiomePaster paster, Consumer<Result> completion) {
            this.island = island;
            this.price = price;
            this.paster = paster;
            this.completion = completion;
        }
    }

    public record Result(Status status, String reason) {
        static Result started(String reason) { return new Result(Status.STARTED, reason); }
        static Result applied(String reason) { return new Result(Status.COMPLETED, reason); }
        static Result rejected(String reason) { return new Result(Status.FAILED, reason); }
    }

    public enum Status { STARTED, COMPLETED, FAILED }
}
