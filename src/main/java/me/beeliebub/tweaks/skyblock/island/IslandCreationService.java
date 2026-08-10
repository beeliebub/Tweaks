package me.beeliebub.tweaks.skyblock.island;

import me.beeliebub.tweaks.skyblock.SkyblockConfig;
import me.beeliebub.tweaks.skyblock.template.TemplatePaster;
import me.beeliebub.tweaks.skyblock.template.TemplateStore;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.block.Biome;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Transactional island creation coordinator. The island is exposed only after its paste job ends. */
public final class IslandCreationService {
    private final JavaPlugin plugin;
    private final SkyblockConfig config;
    private final IslandManager islands;
    private final IslandRegionBridge regions;
    private final TypeRegistry types;
    private final TemplateStore templates;
    private final World world;
    private final int chunksPerTick;

    public IslandCreationService(JavaPlugin plugin, SkyblockConfig config, IslandManager islands,
                                 IslandRegionBridge regions, TypeRegistry types, TemplateStore templates,
                                 World world) {
        this(plugin, config, islands, regions, types, templates, world, 1);
    }

    public IslandCreationService(JavaPlugin plugin, SkyblockConfig config, IslandManager islands,
                                 IslandRegionBridge regions, TypeRegistry types, TemplateStore templates,
                                 World world, int chunksPerTick) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.islands = Objects.requireNonNull(islands, "islands");
        this.regions = Objects.requireNonNull(regions, "regions");
        this.types = Objects.requireNonNull(types, "types");
        this.templates = Objects.requireNonNull(templates, "templates");
        this.world = Objects.requireNonNull(world, "world");
        this.chunksPerTick = Math.max(1, chunksPerTick);
    }

    public CreationResult begin(Player player, String typeId, String difficultyId,
                                Consumer<CreationResult> completion) {
        if (player == null || player.getWorld() != world) return CreationResult.failed("wrong world");
        IslandType type = types.type(typeId).orElse(null);
        if (type == null || !type.allowsDifficulty(difficultyId)) return CreationResult.failed("unknown type or difficulty");
        final Biome biome;
        try {
            biome = Biome.valueOf(type.biome());
        } catch (IllegalArgumentException error) {
            return CreationResult.failed("unknown island biome");
        }
        Optional<Island> created = islands.createPendingIsland(player.getUniqueId());
        if (created.isEmpty()) return CreationResult.failed("player already belongs to an island");
        Island island = created.get().withTypeId(type.id()).withDifficultyId(difficultyId);
        IslandCreationService.Job job = new Job(player, island, type, biome, completion);
        if (regions.create(island, world, new AtomicReference<>())
                != me.beeliebub.tweaks.protection.region.ProtectionManager.ClaimResult.OK) {
            islands.discardPending(island);
            return CreationResult.failed("island region is occupied");
        }
        if (type.templateId().isBlank()) {
            startBiome(job);
        } else {
            templates.loadAsync(type.templateId()).whenComplete((template, error) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (error != null || template == null) {
                            rollback(job);
                            return;
                        }
                        try {
                            IslandGrid.Slot slot = islands.grid().slotForIndex(island.slotIndex());
                            job.paster = new TemplatePaster(world, template,
                                    slot.centerChunkX() * 16 + 8 - template.width() / 2,
                                    config.islandY(), slot.centerChunkZ() * 16 + 8 - template.depth() / 2);
                            startJob(job);
                        } catch (RuntimeException pasteSetupFailure) {
                            rollback(job);
                        }
                    }));
        }
        return CreationResult.started(island);
    }

    public boolean canUse(Player player) {
        return plugin.isEnabled() && player != null && player.isOnline()
                && player.hasPermission(Permissions.SKYBLOCK_USE) && player.getWorld() == world;
    }

    private void tick(Job job) {
        try {
            if (job.paster != null && !job.paster.complete()) job.paster.tick(chunksPerTick);
            if (job.paster != null && !job.paster.complete()) return;
            if (job.biomePaster == null) {
                IslandGrid.ChunkBounds bounds = islands.grid().chunkBoundsFor(job.island.slotIndex(), job.island.size());
                job.biomePaster = new BiomePaster(world, bounds, job.biome, config.islandY());
            }
            job.biomePaster.tick(chunksPerTick);
            if (!job.biomePaster.complete()) return;
            if (job.task != null) job.task.cancel();
            finish(job);
        } catch (RuntimeException failure) {
            rollback(job);
        }
    }

    private void startBiome(Job job) {
        job.biomePaster = new BiomePaster(world,
                islands.grid().chunkBoundsFor(job.island.slotIndex(), job.island.size()),
                job.biome, config.islandY());
        startJob(job);
    }

    private void startJob(Job job) {
        job.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(job), 1L, 1L);
    }

    private void finish(Job job) {
        if (job.finished) return;
        try {
            islands.publishCreated(job.island);
            job.published = true;
            if (job.player.isOnline()) {
                grantKit(job);
                if (job.player.getWorld() == world) {
                    job.player.teleport(islands.spawnLocation(job.island, world));
                }
            }
            job.finished = true;
            if (job.completion != null) job.completion.accept(CreationResult.completed(job.island));
        } catch (RuntimeException failure) {
            rollback(job);
        }
    }

    private void grantKit(Job job) {
        for (int index = 0; index < job.type.kit().size(); index++) {
            try {
                IslandType.KitItem kit = job.type.kit().get(index);
                ItemStack granted = kit.itemStack().clone();
                var overflow = job.player.getInventory().addItem(granted);
                overflow.values().forEach(item ->
                        job.player.getWorld().dropItemNaturally(job.player.getLocation(), item));
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not grant kit entry " + index + " for island " + job.island.id()
                                + " (type " + job.type.id() + ")",
                        failure);
            }
        }
    }

    private void rollback(Job job) {
        if (job.finished) return;
        job.finished = true;
        if (job.task != null) job.task.cancel();
        regions.delete(job.island, world);
        if (job.published) islands.remove(job.island.id());
        else islands.discardPending(job.island);
        if (job.completion != null) job.completion.accept(CreationResult.failed("template paste failed"));
    }

    private static final class Job {
        private final Player player;
        private final Island island;
        private final IslandType type;
        private final Biome biome;
        private final Consumer<CreationResult> completion;
        private TemplatePaster paster;
        private BiomePaster biomePaster;
        private BukkitTask task;
        private boolean published;
        private boolean finished;

        private Job(Player player, Island island, IslandType type, Biome biome, Consumer<CreationResult> completion) {
            this.player = player; this.island = island; this.type = type; this.biome = biome;
            this.completion = completion;
        }
    }

    public record CreationResult(Status status, String reason, Island island) {
        static CreationResult started(Island island) { return new CreationResult(Status.STARTED, "started", island); }
        static CreationResult completed(Island island) { return new CreationResult(Status.COMPLETED, "completed", island); }
        public static CreationResult failed(String reason) { return new CreationResult(Status.FAILED, reason, null); }
    }

    public enum Status { STARTED, COMPLETED, FAILED }
}
