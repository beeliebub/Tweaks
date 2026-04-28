package me.beeliebub.tweaks.managers;

import me.beeliebub.tweaks.Point;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// Handles async YAML persistence for homes, warps, and per-world inventory data.
// All data is cached in memory with ConcurrentHashMaps and saved to disk asynchronously.
public class StorageManager {

    private final JavaPlugin plugin;
    private final File homesDir;
    private final File warpsFile;
    private final File invDir;

    // In-memory caches: homes per player, global warps, and Base64 inventory strings per player/profile
    private final Map<UUID, Map<String, Point>> homes = new ConcurrentHashMap<>();
    private final Map<String, Point> warps = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> inventoryCache = new ConcurrentHashMap<>();

    public StorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.warpsFile = new File(plugin.getDataFolder(), "warps.yml");

        this.homesDir = new File(plugin.getDataFolder(), "homes");
        if (!homesDir.exists()) {
            homesDir.mkdirs();
        }

        this.invDir = new File(plugin.getDataFolder(), "inventories");
        if (!invDir.exists()) {
            invDir.mkdirs();
        }

        loadData();
    }

    public void setWarp(String name, Point point) {
        warps.put(name.toLowerCase(), point);
        saveWarpsAsync();
    }

    public void delWarp(String name) {
        warps.remove(name.toLowerCase());
        saveWarpsAsync();
    }

    public Optional<Point> getWarp(String name) {
        return Optional.ofNullable(warps.get(name.toLowerCase()));
    }

    public Set<String> getWarps() {
        return warps.keySet();
    }

    public void setHome(UUID player, String name, Point point) {
        homes.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(name.toLowerCase(), point);
        savePlayerHomesAsync(player);
    }

    public void delHome(UUID player, String name) {
        if (homes.containsKey(player)) {
            Map<String, Point> playerHomes = homes.get(player);
            playerHomes.remove(name.toLowerCase());

            if (playerHomes.isEmpty()) {
                homes.remove(player);
            }

            savePlayerHomesAsync(player);
        }
    }

    public Optional<Point> getHome(UUID player, String name) {
        Map<String, Point> playerHomes = homes.get(player);
        return playerHomes != null ? Optional.ofNullable(playerHomes.get(name.toLowerCase())) : Optional.empty();
    }

    public Set<String> getHomes(UUID player) {
        return homes.containsKey(player) ? homes.get(player).keySet() : Collections.emptySet();
    }

    public int getHomeCount(UUID player) {
        return homes.containsKey(player) ? homes.get(player).size() : 0;
    }

    private void saveWarpsAsync() {
        CompletableFuture.runAsync(() -> {
            YamlConfiguration config = new YamlConfiguration();
            warps.forEach((name, point) -> {
                String path = name.toLowerCase();
                config.set(path + ".world", point.worldName());
                config.set(path + ".x", point.x());
                config.set(path + ".y", point.y());
                config.set(path + ".z", point.z());
                config.set(path + ".yaw", (double) point.yaw());
                config.set(path + ".pitch", (double) point.pitch());
            });
            try { config.save(warpsFile); } catch (IOException e) { e.printStackTrace(); }
        });
    }

    private void savePlayerHomesAsync(UUID uuid) {
        CompletableFuture.runAsync(() -> {
            File playerFile = new File(homesDir, uuid.toString() + ".yml");
            Map<String, Point> playerHomes = homes.get(uuid);

            if (playerHomes == null || playerHomes.isEmpty()) {
                if (playerFile.exists()) {
                    playerFile.delete();
                }
                return;
            }

            YamlConfiguration config = new YamlConfiguration();
            playerHomes.forEach((name, point) -> {
                String path = name.toLowerCase();
                config.set(path + ".world", point.worldName());
                config.set(path + ".x", point.x());
                config.set(path + ".y", point.y());
                config.set(path + ".z", point.z());
                config.set(path + ".yaw", (double) point.yaw());
                config.set(path + ".pitch", (double) point.pitch());
            });

            try { config.save(playerFile); } catch (IOException e) { e.printStackTrace(); }
        });
    }

    private void loadData() {
        if (warpsFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(warpsFile);
            for (String name : config.getKeys(false)) {
                org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection(name);
                if (section != null) {
                    String worldName = section.getString("world");
                    double x = section.getDouble("x");
                    double y = section.getDouble("y");
                    double z = section.getDouble("z");
                    float yaw = (float) section.getDouble("yaw");
                    float pitch = (float) section.getDouble("pitch");

                    warps.put(name.toLowerCase(), new Point(worldName, x, y, z, yaw, pitch));
                }
            }
        }

        if (homesDir.exists() && homesDir.isDirectory()) {
            File[] files = homesDir.listFiles((dir, name) -> name.endsWith(".yml"));

            if (files != null) {
                for (File file : files) {
                    try {
                        String uuidString = file.getName().replace(".yml", "");
                        UUID player = UUID.fromString(uuidString);

                        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                        Map<String, Point> playerHomes = new ConcurrentHashMap<>();

                        for (String homeName : config.getKeys(false)) {
                            org.bukkit.configuration.ConfigurationSection homeSection = config.getConfigurationSection(homeName);
                            if (homeSection != null) {
                                String worldName = homeSection.getString("world");
                                double x = homeSection.getDouble("x");
                                double y = homeSection.getDouble("y");
                                double z = homeSection.getDouble("z");
                                float yaw = (float) homeSection.getDouble("yaw");
                                float pitch = (float) homeSection.getDouble("pitch");

                                playerHomes.put(homeName.toLowerCase(), new Point(worldName, x, y, z, yaw, pitch));
                            }
                        }

                        if (!playerHomes.isEmpty()) {
                            homes.put(player, playerHomes);
                        }

                    } catch (IllegalArgumentException ignored) {
                        plugin.getLogger().warning("Invalid UUID filename found in homes folder: " + file.getName());
                    }
                }
            }
        }
    }

    // Load a player's per-world inventory data from disk into cache (called on join)
    public CompletableFuture<Void> loadPlayerInventoriesAsync(UUID player) {
        // Pre-create the cache entry on the calling thread so any sync cacheInventory(...) writes
        // that race the async disk read land in the same map. The async path then uses
        // putIfAbsent, treating live in-memory writes as fresher than disk.
        Map<String, String> cache = inventoryCache.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        return CompletableFuture.runAsync(() -> {
            File file = new File(invDir, player.toString() + ".yml");
            if (!file.exists()) return;

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (String profileKey : config.getKeys(false)) {
                String value = config.getString(profileKey);
                if (value != null) {
                    cache.putIfAbsent(profileKey, value);
                }
            }
        });
    }

    // Snapshot a player's inventory cache to disk without removing from memory.
    // Called after world changes so a crash between hops cannot lose state.
    public void savePlayerInventoriesAsync(UUID player) {
        Map<String, String> data = inventoryCache.get(player);
        if (data == null || data.isEmpty()) return;
        Map<String, String> snapshot = new HashMap<>(data);

        CompletableFuture.runAsync(() -> {
            File file = new File(invDir, player.toString() + ".yml");
            YamlConfiguration config = new YamlConfiguration();
            snapshot.forEach(config::set);
            try { config.save(file); } catch (IOException e) {
                plugin.getLogger().warning("Failed to save inventory data for " + player + ": " + e.getMessage());
            }
        });
    }

    // Save a player's inventory cache to disk and remove from memory (called on quit)
    public void unloadAndSavePlayerInventoriesAsync(UUID player) {
        Map<String, String> data = inventoryCache.remove(player);
        if (data == null || data.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            File file = new File(invDir, player.toString() + ".yml");
            YamlConfiguration config = new YamlConfiguration();

            data.forEach(config::set);

            try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
        });
    }

    public void cacheInventory(UUID player, String profile, String base64) {
        inventoryCache.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(profile, base64);
    }

    public String getCachedInventory(UUID player, String profile) {
        Map<String, String> data = inventoryCache.get(player);
        return data != null ? data.get(profile) : null;
    }

}