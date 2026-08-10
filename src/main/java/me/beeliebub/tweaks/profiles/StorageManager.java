package me.beeliebub.tweaks.profiles;

import me.beeliebub.tweaks.utils.Point;
import me.beeliebub.tweaks.utils.InventoryUtil;
import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// Handles async YAML persistence for homes, warps, and per-world inventory data.
// All data is cached in memory with ConcurrentHashMaps; persistence is delegated to
// utils/YamlStore (see its class Javadoc for the calling-thread-filler contract).
public class StorageManager {

    private final JavaPlugin plugin;
    private final File homesDir;

    // warpStore is rooted at the plugin data folder itself and only ever touches the single
    // "warps" key (-> warps.yml): a whole-file overwrite on every mutation, unlike homeStore and
    // inventoryStore below, which are one file per player.
    private final YamlStore warpStore;
    private final YamlStore homeStore;
    private final YamlStore inventoryStore;

    // In-memory caches: homes per player, global warps, and Base64 inventory strings per player/profile
    private final Map<UUID, Map<String, Point>> homes = new ConcurrentHashMap<>();
    private final Map<String, Point> warps = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> inventoryCache = new ConcurrentHashMap<>();

    public StorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.warpStore = new YamlStore(plugin, plugin.getDataFolder(), "warps");

        this.homesDir = new File(plugin.getDataFolder(), "homes");
        this.homeStore = new YamlStore(plugin, homesDir, "home data");

        this.inventoryStore = new YamlStore(plugin, new File(plugin.getDataFolder(), "inventories"),
                "inventory data");

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
        // The filler runs on the calling thread (YamlStore's contract), so this iterates a
        // consistent view of `warps` at the moment of the call rather than racing a later mutation
        // the way the old off-thread iteration could.
        warpStore.writeAsync("warps", config ->
                warps.forEach((name, point) -> writePoint(config, name, point)));
    }

    private void savePlayerHomesAsync(UUID uuid) {
        Map<String, Point> playerHomes = homes.get(uuid);
        if (playerHomes == null || playerHomes.isEmpty()) {
            // Deleting a player's last home must delete the stale file — writing an empty
            // document (or skipping the write) would leave the old homes on disk to reappear
            // after the next restart.
            homeStore.deleteAsync(uuid.toString());
            return;
        }
        homeStore.writeAsync(uuid.toString(),
                config -> playerHomes.forEach((name, point) -> writePoint(config, name, point)));
    }

    private static void writePoint(YamlConfiguration config, String name, Point point) {
        String path = name.toLowerCase();
        config.set(path + ".world", point.worldName());
        config.set(path + ".x", point.x());
        config.set(path + ".y", point.y());
        config.set(path + ".z", point.z());
        config.set(path + ".yaw", (double) point.yaw());
        config.set(path + ".pitch", (double) point.pitch());
    }

    private static void readPointInto(YamlConfiguration config, String name, Map<String, Point> target) {
        ConfigurationSection section = config.getConfigurationSection(name);
        if (section == null) {
            return;
        }
        String worldName = section.getString("world");
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");
        target.put(name.toLowerCase(), new Point(worldName, x, y, z, yaw, pitch));
    }

    private void loadData() {
        YamlConfiguration warpConfig = warpStore.read("warps");
        for (String name : warpConfig.getKeys(false)) {
            readPointInto(warpConfig, name, warps);
        }

        if (homesDir.exists() && homesDir.isDirectory()) {
            File[] files = homesDir.listFiles((dir, name) -> name.endsWith(".yml"));

            if (files != null) {
                for (File file : files) {
                    try {
                        String uuidString = file.getName().replace(".yml", "");
                        UUID player = UUID.fromString(uuidString);

                        YamlConfiguration config = YamlStore.load(file);
                        Map<String, Point> playerHomes = new ConcurrentHashMap<>();

                        for (String homeName : config.getKeys(false)) {
                            readPointInto(config, homeName, playerHomes);
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
        return inventoryStore.readAsync(player.toString()).thenAccept(config -> {
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
        inventoryStore.writeAsync(player.toString(), config -> data.forEach(config::set));
    }

    // Save a player's inventory cache to disk and remove from memory (called on quit)
    public void unloadAndSavePlayerInventoriesAsync(UUID player) {
        Map<String, String> data = inventoryCache.remove(player);
        if (data == null || data.isEmpty()) return;
        // `data` is the detached local removed from the cache above — the filler must read this,
        // never inventoryCache.get(player) again, or an unload racing a later cacheInventory(...)
        // call could write back state that was never meant to survive the unload.
        inventoryStore.writeAsync(player.toString(), config -> data.forEach(config::set));
    }

    public void cacheInventory(UUID player, String profile, String base64) {
        inventoryCache.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(profile, base64);
    }

    /**
     * Clears a player's inventory and ender-chest payloads for one frozen world profile.
     * Experience is deliberately left untouched: it is stored under a separate key and must
     * survive leaving or losing access to an island.
     *
     * <p>The read-modify-write preserves profile data that is not currently cached, including the
     * XP payload. A serialized empty inventory is written rather than removing keys because an
     * absent inventory value is allowed to fall through to a different migration/default path on
     * a later login.
     */
    public CompletableFuture<Void> clearProfileData(UUID player, String profile) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(profile, "profile");
        if (profile.isBlank() || profile.contains(".")) {
            throw new IllegalArgumentException("profile must be a non-blank YAML key without dots");
        }

        String enderChestKey = "ec_" + profile;
        String emptyInventory = InventoryUtil.toBase64(new ItemStack[41]);
        String emptyEnderChest = InventoryUtil.toBase64(new ItemStack[27]);
        Map<String, String> cache = inventoryCache.computeIfAbsent(player,
                ignored -> new ConcurrentHashMap<>());
        cache.put(profile, emptyInventory);
        cache.put(enderChestKey, emptyEnderChest);

        return inventoryStore.readOrderedAsyncStrict(player.toString()).thenCompose(config -> {
            Map<String, String> snapshot = new HashMap<>();
            for (String key : config.getKeys(false)) {
                String value = config.getString(key);
                if (value != null) snapshot.put(key, value);
            }
            snapshot.putAll(cache);
            snapshot.put(profile, emptyInventory);
            snapshot.put(enderChestKey, emptyEnderChest);
            return inventoryStore.writeAsync(player.toString(), output -> snapshot.forEach(output::set));
        });
    }

    public String getCachedInventory(UUID player, String profile) {
        Map<String, String> data = inventoryCache.get(player);
        return data != null ? data.get(profile) : null;
    }

    // Used by SeparatorListener's one-time-migration gate: checking against the *currently
    // configured* profile set (WorldProfileTable#knownProfiles) would regress if an admin later
    // removes a world-profiles entry that was the last one referencing a profile a player already
    // has real ec_/xp_ data under - this key scan is independent of what's currently configured, so
    // a profile's data, once cached, is never "forgotten" by this check.
    public boolean hasAnyCachedKeyWithPrefix(UUID player, String prefix) {
        Map<String, String> data = inventoryCache.get(player);
        if (data == null) return false;
        for (String key : data.keySet()) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

}
