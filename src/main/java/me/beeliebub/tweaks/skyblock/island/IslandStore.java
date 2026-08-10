package me.beeliebub.tweaks.skyblock.island;

import me.beeliebub.tweaks.skyblock.SkyblockConfig;
import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;

/**
 * Asynchronous persistence coordinator for one YAML file per island.
 *
 * <p>The island model is intentionally supplied through {@link YamlCodec}. That keeps file
 * layout and scheduling here while leaving model evolution and validation with the island model.
 * Codec output is serialized before it enters the dirty set, so a later async write never reads a
 * mutable Bukkit object or a record that another main-thread mutation has already replaced.
 */
public final class IslandStore {

    private static final String ISLAND_DIRECTORY = "skyblock/islands";
    private static final String LABEL = "skyblock island data";

    private final JavaPlugin plugin;
    private final SkyblockConfig config;
    private final File directory;
    private final YamlStore store;
    private final YamlCodec<Object> defaultCodec;
    private final ConcurrentMap<String, PendingSnapshot> dirty = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<Void>> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean acceptingWrites = new AtomicBoolean(true);
    private volatile BukkitTask flushTask;

    /** Converts an island value to and from its persisted YAML document. */
    public interface YamlCodec<T> {
        T decode(String islandId, YamlConfiguration yaml);

        void encode(T island, YamlConfiguration yaml);
    }

    private record PendingSnapshot(String yaml) {
    }

    public IslandStore(JavaPlugin plugin, SkyblockConfig config) {
        this(plugin, config, ISLAND_CODEC);
    }

    /**
     * Constructor seam for the island model. Callers that supply a codec may use the shorter
     * operation overloads; the two-argument constructor remains useful while the model is wired by
     * its owning manager.
     */
    @SuppressWarnings("unchecked")
    public <T> IslandStore(JavaPlugin plugin, SkyblockConfig config, YamlCodec<T> codec) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.directory = new File(plugin.getDataFolder(), ISLAND_DIRECTORY);
        this.store = new YamlStore(plugin, directory, LABEL);
        this.defaultCodec = (YamlCodec<Object>) codec;
    }

    private static final YamlCodec<Island> ISLAND_CODEC = new YamlCodec<>() {
        @Override
        public Island decode(String islandId, YamlConfiguration yaml) {
            try {
                String ownerText = yaml.getString("owner");
                if (ownerText == null) return null;
                java.util.List<java.util.UUID> members = new java.util.ArrayList<>();
                for (String value : yaml.getStringList("members")) members.add(java.util.UUID.fromString(value));
                java.util.Map<String, Long> counters = new java.util.LinkedHashMap<>();
                if (yaml.isConfigurationSection("counters")) {
                    for (String key : yaml.getConfigurationSection("counters").getKeys(false)) {
                        counters.put(key, yaml.getLong("counters." + key));
                    }
                }
                java.util.Map<java.util.UUID, java.util.Map<String, Island.Home>> homes = new java.util.LinkedHashMap<>();
                if (yaml.isConfigurationSection("homes")) {
                    for (String playerText : yaml.getConfigurationSection("homes").getKeys(false)) {
                        java.util.UUID player = java.util.UUID.fromString(playerText);
                        java.util.Map<String, Island.Home> playerHomes = new java.util.LinkedHashMap<>();
                        String playerPath = "homes." + playerText;
                        if (yaml.isConfigurationSection(playerPath)) {
                            for (String name : yaml.getConfigurationSection(playerPath).getKeys(false)) {
                                String homePath = playerPath + "." + name;
                                String homeWorld = yaml.getString(homePath + ".world");
                                if (homeWorld == null || homeWorld.isBlank()) continue;
                                playerHomes.put(name, new Island.Home(homeWorld,
                                        yaml.getDouble(homePath + ".x"), yaml.getDouble(homePath + ".y"),
                                        yaml.getDouble(homePath + ".z"),
                                        (float) yaml.getDouble(homePath + ".yaw"),
                                        (float) yaml.getDouble(homePath + ".pitch")));
                            }
                        }
                        if (!playerHomes.isEmpty()) homes.put(player, playerHomes);
                    }
                }
                return new Island(islandId, java.util.UUID.fromString(ownerText), members,
                        yaml.getInt("slot"), IslandSize.valueOf(yaml.getString("size", "SMALL")),
                        yaml.getString("type", "default"), yaml.getString("difficulty", "normal"),
                        yaml.getBoolean("public", false), yaml.getString("display-name", "Island"),
                        new Island.SpawnOffset(yaml.getInt("spawn.x"), yaml.getInt("spawn.y"), yaml.getInt("spawn.z")),
                        yaml.getString("generator", "default"), homes,
                        yaml.getInt("purchased-home-slots", 0), parseInstant(yaml.getString("created-at")),
                        parseInstant(yaml.getString("last-active")), counters,
                        new java.util.LinkedHashSet<>(yaml.getStringList("completed")),
                        new java.util.LinkedHashSet<>(yaml.getStringList("notified")));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Malformed island " + islandId, error);
            }
        }

        @Override
        public void encode(Island island, YamlConfiguration yaml) {
            yaml.set("owner", island.owner().toString());
            yaml.set("members", island.members().stream().map(java.util.UUID::toString).sorted().toList());
            yaml.set("slot", island.slotIndex());
            yaml.set("size", island.size().name());
            yaml.set("type", island.typeId());
            yaml.set("difficulty", island.difficultyId());
            yaml.set("public", island.isPublic());
            yaml.set("display-name", island.displayName());
            yaml.set("spawn.x", island.spawnOffset().x());
            yaml.set("spawn.y", island.spawnOffset().y());
            yaml.set("spawn.z", island.spawnOffset().z());
            yaml.set("generator", island.generatorTierId());
            island.homes().forEach((player, homes) -> homes.forEach((name, home) -> {
                String path = "homes." + player + "." + name;
                yaml.set(path + ".world", home.world());
                yaml.set(path + ".x", home.x());
                yaml.set(path + ".y", home.y());
                yaml.set(path + ".z", home.z());
                yaml.set(path + ".yaw", (double) home.yaw());
                yaml.set(path + ".pitch", (double) home.pitch());
            }));
            yaml.set("purchased-home-slots", island.purchasedHomeSlots());
            yaml.set("created-at", island.createdAt().toString());
            yaml.set("last-active", island.lastActive().toString());
            island.counters().forEach((key, value) -> yaml.set("counters." + key, value));
            yaml.set("completed", island.completedChallenges().stream().sorted().toList());
            yaml.set("notified", island.notifiedChallenges().stream().sorted().toList());
        }

        private Instant parseInstant(String raw) {
            return raw == null ? Instant.now() : Instant.parse(raw);
        }
    };

    /** Synchronous startup snapshot used by the main-thread island index. */
    public List<Island> loadAll() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return List.of();
        List<Island> islands = new ArrayList<>();
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            try {
                YamlConfiguration yaml = strictLoad(file);
                if (!yaml.isSet("owner")) throw new IllegalArgumentException("owner is missing");
                Island island = ISLAND_CODEC.decode(id, yaml);
                if (island != null) islands.add(island);
            } catch (RuntimeException error) {
                throw new IllegalStateException("Cannot safely load Skyblock island " + file, error);
            }
        }
        return List.copyOf(islands);
    }

    public CompletableFuture<Void> saveAsync(Island island) {
        return saveAsync(island.id(), island, ISLAND_CODEC);
    }

    public CompletableFuture<Void> deleteAsync(String islandId) {
        String key = validateKey(islandId);
        dirty.remove(key);
        CompletableFuture<Void> delete = store.deleteAsync(key);
        inFlight.add(delete);
        delete.whenComplete((ignored, error) -> inFlight.remove(delete));
        return delete;
    }

    public void flush(List<Island> islands) {
        for (Island island : islands) markDirty(island.id(), island, ISLAND_CODEC);
        flush();
    }

    /**
     * Starts the main-thread dirty flush task. The task only builds YAML snapshots and delegates
     * the actual file writes to {@link YamlStore}; it never mutates Bukkit state.
     */
    public void start() {
        if (flushTask != null || !acceptingWrites.get()) return;
        if (plugin.getServer() == null) return;

        long intervalTicks = Math.max(1L, Math.multiplyExact(
                Math.max(1L, config.saveIntervalSeconds()), 20L));
        flushTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::flushSafely, intervalTicks, intervalTicks);
    }

    private void flushSafely() {
        try {
            flush().exceptionally(error -> {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Skyblock island flush failed", error);
                return null;
            });
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Skyblock island flush could not be started", error);
        }
    }

    /** Loads one island document off the main thread and decodes it through the supplied seam. */
    public <T> CompletableFuture<Optional<T>> loadAsync(String islandId, YamlCodec<T> codec) {
        String key = validateKey(islandId);
        Objects.requireNonNull(codec, "codec");
        return store.readOrderedAsync(key).thenApply(yaml -> Optional.ofNullable(codec.decode(key, yaml)));
    }

    public <T> CompletableFuture<Optional<T>> loadAsync(String islandId) {
        return loadAsync(islandId, defaultCodec());
    }

    /** Loads every island file off the main thread, keyed by its filesystem-safe island id. */
    public <T> CompletableFuture<Map<String, T>> loadAllAsync(YamlCodec<T> codec) {
        Objects.requireNonNull(codec, "codec");
        return CompletableFuture.supplyAsync(() -> {
            Map<String, T> loaded = new LinkedHashMap<>();
            File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) return Map.of();

            for (File file : files) {
                String name = file.getName();
                String islandId = name.substring(0, name.length() - ".yml".length());
                try {
                    T island = codec.decode(islandId, strictLoad(file));
                    if (island != null) loaded.put(islandId, island);
                } catch (RuntimeException error) {
                    throw new IllegalStateException("Cannot safely load Skyblock island file " + file, error);
                }
            }
            return Map.copyOf(loaded);
        });
    }

    /** Encodes and submits one island snapshot immediately; disk I/O remains asynchronous. */
    public <T> CompletableFuture<Void> saveAsync(String islandId, T island, YamlCodec<T> codec) {
        ensureAcceptingWrites();
        String key = validateKey(islandId);
        String snapshot = encode(key, island, codec);
        return writeSnapshot(key, snapshot);
    }

    public <T> CompletableFuture<Void> saveAsync(String islandId, T island) {
        return saveAsync(islandId, island, defaultCodec());
    }

    /**
     * Records an encoded immutable snapshot for the next periodic or shutdown flush. The codec
     * runs on the calling thread, which is where the owning manager's main-thread state is safe
     * to read.
     */
    public <T> void markDirty(String islandId, T island, YamlCodec<T> codec) {
        ensureAcceptingWrites();
        String key = validateKey(islandId);
        dirty.put(key, new PendingSnapshot(encode(key, island, codec)));
    }

    public void discardDirty(String islandId) {
        dirty.remove(validateKey(islandId));
    }

    public <T> void markDirty(String islandId, T island) {
        markDirty(islandId, island, defaultCodec());
    }

    /** Writes and removes the current dirty snapshot set. */
    public CompletableFuture<Void> flush() {
        List<CompletableFuture<Void>> writes = new ArrayList<>(inFlight);
        if (dirty.isEmpty()) {
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        }

        Map<String, PendingSnapshot> snapshots = new LinkedHashMap<>();
        for (Map.Entry<String, PendingSnapshot> entry : dirty.entrySet()) {
            if (dirty.remove(entry.getKey(), entry.getValue())) {
                snapshots.put(entry.getKey(), entry.getValue());
            }
        }
        if (snapshots.isEmpty()) {
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        }

        snapshots.forEach((key, snapshot) -> writes.add(writeSnapshot(key, snapshot.yaml())));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        snapshots.forEach((key, snapshot) -> dirty.putIfAbsent(key, snapshot));
                    }
                });
    }

    /** Stops periodic work and returns a future for the final dirty-set flush. */
    public CompletableFuture<Void> shutdown() {
        acceptingWrites.set(false);
        BukkitTask task = flushTask;
        if (task != null) task.cancel();
        return flush();
    }

    public boolean hasPendingWrites() {
        return !dirty.isEmpty();
    }

    public File directory() {
        return directory;
    }

    private <T> String encode(String islandId, T island, YamlCodec<T> codec) {
        Objects.requireNonNull(island, "island");
        Objects.requireNonNull(codec, "codec");
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            codec.encode(island, yaml);
            return yaml.saveToString();
        } catch (RuntimeException error) {
            throw new IllegalStateException("Could not encode Skyblock island " + islandId, error);
        }
    }

    private CompletableFuture<Void> writeSnapshot(String key, String snapshot) {
        CompletableFuture<Void> write = store.writeAsync(key, target -> {
            try {
                target.loadFromString(snapshot);
            } catch (InvalidConfigurationException error) {
                throw new IllegalStateException("Could not restore Skyblock island YAML snapshot " + key,
                        error);
            }
        });
        inFlight.add(write);
        write.whenComplete((ignored, error) -> inFlight.remove(write));
        return write;
    }

    private void ensureAcceptingWrites() {
        if (!acceptingWrites.get()) throw new IllegalStateException("IslandStore is shutting down");
    }

    @SuppressWarnings("unchecked")
    private <T> YamlCodec<T> defaultCodec() {
        if (defaultCodec == null) {
            throw new IllegalStateException("IslandStore requires a YAML codec for this operation");
        }
        return (YamlCodec<T>) defaultCodec;
    }

    private static String validateKey(String islandId) {
        Objects.requireNonNull(islandId, "islandId");
        if (islandId.isBlank()) throw new IllegalArgumentException("islandId must not be blank");
        if (islandId.contains("/") || islandId.contains("\\") || islandId.contains("..")) {
            throw new IllegalArgumentException("islandId must be a filesystem-safe key: " + islandId);
        }
        return islandId;
    }

    private static YamlConfiguration strictLoad(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException error) {
            throw new IllegalArgumentException("Could not parse island YAML " + file, error);
        }
    }
}
