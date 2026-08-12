package me.beeliebub.tweaks.skyblock.island;

import me.beeliebub.tweaks.utils.Point;
import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Admin-recorded Skyblock spawn point with live-region bounds and vanilla fallback. */
public final class SkyblockSpawn {

    private final Store store;
    private final Function<World, IslandGrid.ChunkBounds> boundsSource;
    private volatile SpawnData data;
    private volatile CompletableFuture<Void> lastPersistence = CompletableFuture.completedFuture(null);

    public SkyblockSpawn() {
        this(new MemoryStore(), ignored -> null);
    }

    public SkyblockSpawn(JavaPlugin plugin) {
        this(new YamlStoreStore(Objects.requireNonNull(plugin, "plugin")), ignored -> null);
    }

    public SkyblockSpawn(Store store) {
        this(store, ignored -> null);
    }

    public SkyblockSpawn(JavaPlugin plugin, Function<World, IslandGrid.ChunkBounds> boundsSource) {
        this(new YamlStoreStore(Objects.requireNonNull(plugin, "plugin")), boundsSource);
    }

    public SkyblockSpawn(Store store, Function<World, IslandGrid.ChunkBounds> boundsSource) {
        this.store = Objects.requireNonNull(store, "store");
        this.boundsSource = Objects.requireNonNull(boundsSource, "boundsSource");
        this.data = store.load().orElse(null);
    }

    public Optional<SpawnData> data() {
        return Optional.ofNullable(data);
    }

    public Optional<SpawnData> snapshot() {
        return data();
    }

    public boolean isRecorded() {
        return data != null;
    }

    public boolean contains(Chunk chunk) {
        if (chunk == null) {
            return false;
        }
        SpawnData current = data;
        if (current != null && !matchesWorld(current.worldKey(), chunk.getWorld())) return false;
        return permittedBounds(chunk.getWorld()).contains(chunk.getX(), chunk.getZ());
    }

    public Location location(World world) {
        return destination(world);
    }

    public Location destination(World world) {
        Objects.requireNonNull(world, "world");
        SpawnData current = data;
        IslandGrid.ChunkBounds live = boundsSource.apply(world);
        if (live == null) {
            return world.getSpawnLocation().clone();
        }
        if (current != null && current.owner() != null && matchesWorld(current.worldKey(), world)
                && live.contains(chunkOf(current.x()), chunkOf(current.z()))) {
            return current.location(world);
        }
        return world.getSpawnLocation().clone();
    }

    public IslandGrid.ChunkBounds permittedBounds(World world) {
        Objects.requireNonNull(world, "world");
        IslandGrid.ChunkBounds live = boundsSource.apply(world);
        if (live != null) return live;
        Location spawn = world.getSpawnLocation();
        int chunkX = spawn.getBlockX() >> 4;
        int chunkZ = spawn.getBlockZ() >> 4;
        return new IslandGrid.ChunkBounds(chunkX, chunkZ, chunkX, chunkZ);
    }

    public boolean permits(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        SpawnData current = data;
        if (current != null && !matchesWorld(current.worldKey(), location.getWorld())) {
            return false;
        }
        return permittedBounds(location.getWorld()).contains(
                location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public CompletableFuture<Void> record(SpawnData next) {
        Objects.requireNonNull(next, "next");
        data = next;
        lastPersistence = persistence(store.save(next));
        return lastPersistence;
    }

    public CompletableFuture<Void> record(String worldKey, IslandGrid.ChunkBounds bounds, Point point) {
        Objects.requireNonNull(point, "point");
        return record(new SpawnData(worldKey, bounds, point.x(), point.y(), point.z(), point.yaw(), point.pitch()));
    }

    public CompletableFuture<Void> record(World world, IslandGrid.ChunkBounds bounds, Location point) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(point, "point");
        if (point.getWorld() != world) {
            throw new IllegalArgumentException("Skyblock spawn point must be in the recorded world");
        }
        return record(new SpawnData(world.getKey().asString(), bounds, point.getX(), point.getY(), point.getZ(),
                point.getYaw(), point.getPitch()));
    }

    public CompletableFuture<Void> clear() {
        data = null;
        lastPersistence = persistence(store.clear());
        return lastPersistence;
    }

    /** Returns the latest spawn-file operation for bounded lifecycle waits and command reporting. */
    public CompletableFuture<Void> flush() {
        return lastPersistence;
    }

    private static CompletableFuture<Void> persistence(CompletableFuture<Void> future) {
        return future == null ? CompletableFuture.completedFuture(null) : future;
    }

    private static boolean matchesWorld(String configuredKey, World world) {
        return configuredKey.equalsIgnoreCase(world.getKey().asString())
                || configuredKey.equalsIgnoreCase(world.getName());
    }

    private static int chunkOf(double coordinate) {
        return (int) Math.floor(Math.floor(coordinate) / 16.0D);
    }

    /**
     * Recovery record for a spawn claim. The bounds are retained so bootstrap can rebuild a lost
     * protection region; live enforcement always reads the protection region instead.
     */
    public record SpawnData(String worldKey, IslandGrid.ChunkBounds bounds, UUID owner,
                            double x, double y, double z, float yaw, float pitch) {
        public SpawnData(String worldKey, IslandGrid.ChunkBounds bounds,
                         double x, double y, double z, float yaw, float pitch) {
            this(worldKey, bounds, null, x, y, z, yaw, pitch);
        }

        public SpawnData {
            if (worldKey == null || worldKey.isBlank() || bounds == null) {
                throw new IllegalArgumentException("Spawn world and bounds are required");
            }
        }

        public Location location(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    public interface Store {
        Optional<SpawnData> load();

        CompletableFuture<Void> save(SpawnData data);

        CompletableFuture<Void> clear();
    }

    private static final class MemoryStore implements Store {
        private SpawnData value;

        @Override
        public Optional<SpawnData> load() {
            return Optional.ofNullable(value);
        }

        @Override
        public CompletableFuture<Void> save(SpawnData data) {
            value = data;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> clear() {
            value = null;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class YamlStoreStore implements Store {
        private final JavaPlugin plugin;
        private final YamlStore store;

        private YamlStoreStore(JavaPlugin plugin) {
            this.plugin = plugin;
            this.store = new YamlStore(plugin, new File(plugin.getDataFolder(), "skyblock"),
                    "skyblock spawn");
        }

        @Override
        public Optional<SpawnData> load() {
            return read(store.read("spawn"));
        }

        @Override
        public CompletableFuture<Void> save(SpawnData data) {
            return store.writeAsync("spawn", yaml -> write(yaml, data));
        }

        @Override
        public CompletableFuture<Void> clear() {
            return store.deleteAsync("spawn");
        }

        private Optional<SpawnData> read(YamlConfiguration yaml) {
            String world = yaml.getString("world");
            if (world == null || world.isBlank()) {
                return Optional.empty();
            }
            try {
                IslandGrid.ChunkBounds bounds = new IslandGrid.ChunkBounds(
                        yaml.getInt("bounds.min-x"), yaml.getInt("bounds.min-z"),
                        yaml.getInt("bounds.max-x"), yaml.getInt("bounds.max-z"));
                UUID owner = null;
                String ownerText = yaml.getString("owner");
                if (ownerText != null && !ownerText.isBlank()) {
                    try {
                        owner = UUID.fromString(ownerText);
                    } catch (IllegalArgumentException error) {
                        plugin.getLogger().warning("Malformed Skyblock spawn owner '" + ownerText
                                + "'; keeping the legacy ownerless spawn record");
                    }
                }
                return Optional.of(new SpawnData(world, bounds, owner, yaml.getDouble("x"), yaml.getDouble("y"),
                        yaml.getDouble("z"), (float) yaml.getDouble("yaw"),
                        (float) yaml.getDouble("pitch")));
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Ignoring malformed Skyblock spawn data: "
                        + error.getMessage());
                return Optional.empty();
            }
        }

        private static void write(YamlConfiguration yaml, SpawnData value) {
            yaml.set("world", value.worldKey());
            yaml.set("owner", value.owner() == null ? null : value.owner().toString());
            yaml.set("bounds.min-x", value.bounds().minChunkX());
            yaml.set("bounds.min-z", value.bounds().minChunkZ());
            yaml.set("bounds.max-x", value.bounds().maxChunkX());
            yaml.set("bounds.max-z", value.bounds().maxChunkZ());
            yaml.set("x", value.x());
            yaml.set("y", value.y());
            yaml.set("z", value.z());
            yaml.set("yaw", (double) value.yaw());
            yaml.set("pitch", (double) value.pitch());
        }
    }
}
