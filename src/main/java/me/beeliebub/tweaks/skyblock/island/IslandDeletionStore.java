package me.beeliebub.tweaks.skyblock.island;

import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;

/** Durable cursor store for in-progress island terrain deletion. */
public final class IslandDeletionStore implements IslandDeletionService.ProgressStore,
        IslandDeletionService.FlushableProgressStore, IslandDeletionService.AsyncProgressStore {

    private final JavaPlugin plugin;
    private final YamlStore store;
    private final File directory;
    private final Set<CompletableFuture<Void>> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean invalidRecord;

    public IslandDeletionStore(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.directory = new File(plugin.getDataFolder(), "skyblock/deletions");
        this.store = new YamlStore(plugin, directory, "skyblock deletion data");
    }

    @Override
    public Collection<IslandDeletionService.DeletionProgress> load() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return List.of();
        List<IslandDeletionService.DeletionProgress> result = new ArrayList<>();
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            try {
                YamlConfiguration yaml = strictLoad(file);
                if (!yaml.isSet("slot") || !yaml.isSet("next-chunk") || !yaml.isSet("stage")
                        || !yaml.isSet("bounds.min-x") || !yaml.isSet("bounds.min-z")
                        || !yaml.isSet("bounds.max-x") || !yaml.isSet("bounds.max-z")) {
                    throw new IllegalArgumentException("required deletion fields are missing");
                }
                IslandGrid.ChunkBounds bounds = new IslandGrid.ChunkBounds(
                        yaml.getInt("bounds.min-x"), yaml.getInt("bounds.min-z"),
                        yaml.getInt("bounds.max-x"), yaml.getInt("bounds.max-z"));
                result.add(new IslandDeletionService.DeletionProgress(id, yaml.getInt("slot"), bounds,
                        yaml.getInt("next-chunk"), IslandDeletionService.Stage.valueOf(
                                yaml.getString("stage", IslandDeletionService.Stage.EJECTING.name()))));
            } catch (RuntimeException error) {
                invalidRecord = true;
                plugin.getLogger().warning("Skipping malformed Skyblock deletion file " + file.getName()
                        + ": " + error.getMessage());
            }
        }
        return List.copyOf(result);
    }

    @Override
    public boolean save(IslandDeletionService.DeletionProgress progress) {
        Objects.requireNonNull(progress, "progress");
        try {
            saveAsync(progress).join();
            return true;
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Skyblock deletion progress was not persisted: " + error.getMessage());
            return false;
        }
    }

    public CompletableFuture<Void> saveAsync(IslandDeletionService.DeletionProgress progress) {
        Objects.requireNonNull(progress, "progress");
        CompletableFuture<Void> future = store.writeAsync(progress.islandId(), yaml -> write(yaml, progress));
        track(future);
        return future;
    }

    @Override
    public boolean delete(String islandId) {
        deleteAsync(islandId);
        return true;
    }

    @Override
    public CompletableFuture<Void> deleteAsync(String islandId) {
        CompletableFuture<Void> future = store.deleteAsync(islandId);
        track(future);
        return future;
    }

    @Override
    public CompletableFuture<Void> flushAsync() {
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
    }

    public boolean hasInvalidRecord() {
        return invalidRecord;
    }

    private static YamlConfiguration strictLoad(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException error) {
            throw new IllegalArgumentException("could not parse YAML", error);
        }
    }

    private void track(CompletableFuture<Void> future) {
        pending.add(future);
        future.whenComplete((ignored, error) -> pending.remove(future));
    }

    private static void write(YamlConfiguration yaml, IslandDeletionService.DeletionProgress progress) {
        IslandGrid.ChunkBounds bounds = progress.bounds();
        yaml.set("slot", progress.slotIndex());
        yaml.set("next-chunk", progress.nextChunkIndex());
        yaml.set("stage", progress.stage().name());
        yaml.set("bounds.min-x", bounds.minChunkX());
        yaml.set("bounds.min-z", bounds.minChunkZ());
        yaml.set("bounds.max-x", bounds.maxChunkX());
        yaml.set("bounds.max-z", bounds.maxChunkZ());
    }
}
