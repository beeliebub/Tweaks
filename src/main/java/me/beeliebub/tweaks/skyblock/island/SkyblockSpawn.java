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

/** Admin-recorded Skyblock spawn region and point, with vanilla-world fallback. */
public final class SkyblockSpawn {

    private final Store store;
    private volatile SpawnData data;

    public SkyblockSpawn() {
        this(new MemoryStore());
    }

    public SkyblockSpawn(JavaPlugin plugin) {
        this(new YamlStoreStore(Objects.requireNonNull(plugin, "plugin")));
    }

    public SkyblockSpawn(Store store) {
        this.store = Objects.requireNonNull(store, "store");
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
        if (current != null) {
            return matchesWorld(current.worldKey(), chunk.getWorld())
                    && current.bounds().contains(chunk.getX(), chunk.getZ());
        }
        Location vanillaSpawn = chunk.getWorld().getSpawnLocation();
        return (vanillaSpawn.getBlockX() >> 4) == chunk.getX()
                && (vanillaSpawn.getBlockZ() >> 4) == chunk.getZ();
    }

    public Location location(World world) {
        return destination(world);
    }

    public Location destination(World world) {
        Objects.requireNonNull(world, "world");
        SpawnData current = data;
        if (current == null || !matchesWorld(current.worldKey(), world)) {
            return world.getSpawnLocation().clone();
        }
        return current.location(world);
    }

    public IslandGrid.ChunkBounds permittedBounds(World world) {
        Objects.requireNonNull(world, "world");
        SpawnData current = data;
        if (current != null && matchesWorld(current.worldKey(), world)) {
            return current.bounds();
        }
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

    public void record(SpawnData next) {
        Objects.requireNonNull(next, "next");
        data = next;
        store.save(next);
    }

    public void record(String worldKey, IslandGrid.ChunkBounds bounds, Point point) {
        Objects.requireNonNull(point, "point");
        record(new SpawnData(worldKey, bounds, point.x(), point.y(), point.z(), point.yaw(), point.pitch()));
    }

    public void record(World world, IslandGrid.ChunkBounds bounds, Location point) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(point, "point");
        if (point.getWorld() != world) {
            throw new IllegalArgumentException("Skyblock spawn point must be in the recorded world");
        }
        record(new SpawnData(world.getKey().asString(), bounds, point.getX(), point.getY(), point.getZ(),
                point.getYaw(), point.getPitch()));
    }

    public void clear() {
        data = null;
        store.clear();
    }

    private static boolean matchesWorld(String configuredKey, World world) {
        return configuredKey.equalsIgnoreCase(world.getKey().asString())
                || configuredKey.equalsIgnoreCase(world.getName());
    }

    public record SpawnData(String worldKey, IslandGrid.ChunkBounds bounds,
                            double x, double y, double z, float yaw, float pitch) {
        public SpawnData {
            if (worldKey == null || worldKey.isBlank() || bounds == null) {
                throw new IllegalArgumentException("Spawn world and bounds are required");
            }
            int chunkX = (int) Math.floor(Math.floor(x) / 16.0D);
            int chunkZ = (int) Math.floor(Math.floor(z) / 16.0D);
            if (!bounds.contains(chunkX, chunkZ)) {
                throw new IllegalArgumentException("Spawn point must be inside its recorded bounds");
            }
        }

        public Location location(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    public interface Store {
        Optional<SpawnData> load();

        void save(SpawnData data);

        void clear();
    }

    private static final class MemoryStore implements Store {
        private SpawnData value;

        @Override
        public Optional<SpawnData> load() {
            return Optional.ofNullable(value);
        }

        @Override
        public void save(SpawnData data) {
            value = data;
        }

        @Override
        public void clear() {
            value = null;
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
        public void save(SpawnData data) {
            store.writeAsync("spawn", yaml -> write(yaml, data));
        }

        @Override
        public void clear() {
            store.deleteAsync("spawn");
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
                return Optional.of(new SpawnData(world, bounds, yaml.getDouble("x"), yaml.getDouble("y"),
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
