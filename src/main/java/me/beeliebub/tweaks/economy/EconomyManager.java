package me.beeliebub.tweaks.economy;

import me.beeliebub.tweaks.tab.TabManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// Handles async YAML persistence for per-player economy/ranks data.
// Each player is cached in memory and saved to disk asynchronously, mirroring StorageManager.
public class EconomyManager {

    private final JavaPlugin plugin;
    private final File playersDir;

    // In-memory cache: one mutable holder per player. Reads are lock-free O(1) map lookups.
    private final ConcurrentMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    // Set after construction once TabManager is wired in Tweaks#onEnable.
    private TabManager tabManager;

    public void setTabManager(TabManager tabManager) {
        this.tabManager = tabManager;
    }

    public EconomyManager(JavaPlugin plugin) {
        this.plugin = plugin;

        this.playersDir = new File(plugin.getDataFolder(), "players");
        if (!playersDir.exists()) {
            playersDir.mkdirs();
        }
    }

    // Mutable holder for a single player's economy fields. Fields default to sane zero-values.
    private static final class PlayerData {
        volatile double balance;
        volatile long lastLogin;
        volatile int loginStreak;
        volatile boolean balanceHidden;
        volatile int rank;
    }

    // ---- Lifecycle ----------------------------------------------------------

    // Load a player's file from disk into cache (call from a join handler). Reads on the
    // calling thread are acceptable here since each file is tiny.
    public void loadPlayer(UUID player) {
        if (player == null) return;
        cache.computeIfAbsent(player, this::readFromDisk);
    }

    // Save a player's cache to disk asynchronously and remove from memory (call on quit).
    public void unloadAndSavePlayer(UUID player) {
        if (player == null) return;
        PlayerData data = cache.remove(player);
        if (data != null) {
            writeAsync(player, data);
        }
    }

    // Flush every cached player to disk asynchronously (called on disable).
    public void saveAll() {
        cache.forEach(this::writeAsync);
    }

    // ---- Balance ------------------------------------------------------------

    public double getBalance(UUID player) {
        PlayerData data = cache.get(player);
        return data != null ? data.balance : 0.0D;
    }

    public void setBalance(UUID player, double balance) {
        PlayerData data = get(player);
        data.balance = balance;
        writeAsync(player, data);
        refreshTabFor(player);
    }

    public void addBalance(UUID player, double amount) {
        PlayerData data = get(player);
        data.balance += amount;
        writeAsync(player, data);
        refreshTabFor(player);
    }

    public void removeBalance(UUID player, double amount) {
        PlayerData data = get(player);
        data.balance -= amount;
        writeAsync(player, data);
        refreshTabFor(player);
    }

    // ---- Last login ---------------------------------------------------------

    public long getLastLogin(UUID player) {
        PlayerData data = cache.get(player);
        return data != null ? data.lastLogin : 0L;
    }

    public void setLastLogin(UUID player, long lastLogin) {
        PlayerData data = get(player);
        data.lastLogin = lastLogin;
        writeAsync(player, data);
    }

    // ---- Login streak -------------------------------------------------------

    public int getLoginStreak(UUID player) {
        PlayerData data = cache.get(player);
        return data != null ? data.loginStreak : 0;
    }

    public void setLoginStreak(UUID player, int loginStreak) {
        PlayerData data = get(player);
        data.loginStreak = loginStreak;
        writeAsync(player, data);
    }

    // ---- Balance hidden flag ------------------------------------------------

    public boolean isBalanceHidden(UUID player) {
        PlayerData data = cache.get(player);
        return data != null && data.balanceHidden;
    }

    public void setBalanceHidden(UUID player, boolean hidden) {
        PlayerData data = get(player);
        data.balanceHidden = hidden;
        writeAsync(player, data);
        refreshTabFor(player);
    }

    // ---- Rank ---------------------------------------------------------------

    public int getRank(UUID player) {
        PlayerData data = cache.get(player);
        return data != null ? data.rank : 0;
    }

    public void setRank(UUID player, int rank) {
        PlayerData data = get(player);
        data.rank = rank;
        writeAsync(player, data);
    }

    // ---- Internals ----------------------------------------------------------

    // Return the cached holder, lazily loading from disk on first touch.
    private PlayerData get(UUID player) {
        return cache.computeIfAbsent(player, this::readFromDisk);
    }

    // Read a player's YAML into a fresh holder. Missing/unknown fields fall back to defaults.
    private PlayerData readFromDisk(UUID player) {
        PlayerData data = new PlayerData();
        File file = new File(playersDir, player.toString() + ".yml");
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            data.balance = config.getDouble("balance", 0.0D);
            data.lastLogin = config.getLong("last_login", 0L);
            data.loginStreak = config.getInt("login_streak", 0);
            data.balanceHidden = config.getBoolean("balance_hidden", false);
            data.rank = config.getInt("rank", 0);
        }
        return data;
    }

    // If a TabManager is wired and the player is online, ask it to re-render the tab entry.
    // Must be called on the main thread (playerListName writes must stay synchronous).
    private void refreshTabFor(UUID uuid) {
        if (tabManager == null) return;
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) tabManager.refreshTab(p);
    }

    // Snapshot the holder's current values and write them to disk off the main thread.
    private void writeAsync(UUID player, PlayerData data) {
        double balance = data.balance;
        long lastLogin = data.lastLogin;
        int loginStreak = data.loginStreak;
        boolean balanceHidden = data.balanceHidden;
        int rank = data.rank;

        CompletableFuture.runAsync(() -> {
            File file = new File(playersDir, player.toString() + ".yml");
            YamlConfiguration config = new YamlConfiguration();
            config.set("balance", balance);
            config.set("last_login", lastLogin);
            config.set("login_streak", loginStreak);
            config.set("balance_hidden", balanceHidden);
            config.set("rank", rank);
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save economy data for " + player + ": " + e.getMessage());
            }
        });
    }
}
