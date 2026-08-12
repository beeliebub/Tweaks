package me.beeliebub.tweaks.economy;

import me.beeliebub.tweaks.tab.TabManager;
import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

// Handles async YAML persistence for per-player economy/ranks data.
// Each player is cached in memory and saved to disk asynchronously via utils/YamlStore.
public class EconomyManager {

    /** Largest whole-dollar balance supported by the economy. */
    public static final long MAX_BALANCE = 1L << 53;

    private final JavaPlugin plugin;
    private final YamlStore playerStore;

    // In-memory cache: one mutable holder per player. Reads are lock-free O(1) map lookups.
    private final ConcurrentMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    // Set after construction once TabManager is wired in Tweaks#onEnable.
    private TabManager tabManager;

    public void setTabManager(TabManager tabManager) {
        this.tabManager = tabManager;
    }

    public EconomyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playerStore = new YamlStore(plugin, new File(plugin.getDataFolder(), "players"), "economy data");
    }

    JavaPlugin plugin() {
        return plugin;
    }

    // Mutable holder for a single player's economy fields. Fields default to sane zero-values.
    private static final class PlayerData {
        volatile long balance;
        volatile boolean normalizedOnLoad;
        volatile boolean loadFailed;
        volatile long lastLogin;
        volatile int loginStreak;
        volatile boolean balanceHidden;
        volatile int rank;
        // paymentId -> whole-dollar amount already applied. Copy-on-write: replaced, never mutated
        // in place, so writeAsync's calling-thread snapshot filler always sees a coherent map.
        volatile Map<String, Long> receipts = Map.of();
    }

    // ---- Lifecycle ----------------------------------------------------------

    // Load a player's file from disk into cache (call from a join handler). Reads on the
    // calling thread are acceptable here since each file is tiny.
    public void loadPlayer(UUID player) {
        if (player == null) return;
        PlayerData data = cache.computeIfAbsent(player, this::readFromDisk);
        writeNormalizedValueIfNeeded(player, data);
    }

    // Save a player's cache to disk asynchronously and remove from memory (call on quit).
    public void unloadAndSavePlayer(UUID player) {
        if (player == null) return;
        PlayerData data = cache.remove(player);
        if (data != null && !data.loadFailed) {
            CompletableFuture<Void> write = writeAsync(player, data);
            write.whenComplete((ignored, error) -> {
                if (error != null) {
                    // Keep the snapshot available for saveAll() or a later retry. Do not replace
                    // a newer cache entry created by a rejoining player.
                    cache.putIfAbsent(player, data);
                    plugin.getLogger().log(Level.WARNING,
                            "Player economy save failed on unload; retaining the cache for retry: " + player,
                            error);
                }
            });
        }
    }

    // Flush every cached player to disk. The returned future completes once every write has
    // landed (exceptionally if any write failed — YamlStore logs each failure regardless), so
    // callers on a shutdown path can block on it with a bounded timeout.
    public CompletableFuture<Void> saveAll() {
        CompletableFuture<?>[] writes = cache.entrySet().stream()
                .map(entry -> writeAsync(entry.getKey(), entry.getValue()))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(writes);
    }

    // ---- Balance ------------------------------------------------------------

    public long getBalance(UUID player) {
        PlayerData data = cache.get(player);
        return data != null ? data.balance : 0L;
    }

    public BalanceMutationResult setBalance(UUID player, long balance) {
        return mutateBalance(player, balance, (current, ignored) -> balance, "set");
    }

    public BalanceMutationResult addBalance(UUID player, long amount) {
        return mutateBalance(player, amount, Math::addExact, "add");
    }

    public BalanceMutationResult removeBalance(UUID player, long amount) {
        return mutateBalance(player, amount, (current, delta) -> Math.subtractExact(current, delta), "remove");
    }

    /**
     * Idempotently credits a whole-dollar house payment: {@code paymentId} and the recipient's new
     * balance are written together in one snapshot, so "credited but no receipt" is unrepresentable
     * on disk. Safe to call again with the same {@code paymentId} after a crash — a matching receipt
     * short-circuits to {@link HousePaymentResult#ALREADY_APPLIED} without touching the balance
     * again; a mismatched receipt (same id, different amount) fails closed rather than overwriting.
     *
     * <p>Reads the recipient's on-disk state directly rather than through {@link #getBalance}, which
     * returns a hardcoded {@code 0} for an unloaded player without touching disk (see this class's
     * CLAUDE.md) — that trap would silently zero an offline recipient's real balance here. The read
     * itself uses {@code YamlStore.readOrderedAsyncStrict}, not the tolerant {@code readOrderedAsync}
     * — a present-but-unreadable file must fail this payment rather than silently parsing as an
     * empty (zero-balance) document, which this method would then durably overwrite. On such a
     * failure the returned future completes exceptionally; the debit already recorded by {@code
     * HouseAccount.beginPayment} is left {@code DEBIT_DURABLE} for a later retry, exactly like any
     * other unexpected failure in {@link HousePaymentService}'s sequencing.
     *
     * <p>Never touches Bukkit player/entity API on the calling thread or in a continuation chained
     * onto the returned future — {@code YamlStore}'s filler/continuation contract forbids it. The tab
     * refresh on success is hopped onto the main thread via the scheduler.
     */
    public CompletableFuture<HousePaymentResult> applyHousePayment(UUID recipient, String paymentId, long amount) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(paymentId, "paymentId");
        if (amount < 0) throw new IllegalArgumentException("amount must not be negative");

        return playerStore.readOrderedAsyncStrict(recipient.toString()).thenCompose(config -> {
            HousePaymentResult[] outcome = new HousePaymentResult[1];
            PlayerData data = cache.compute(recipient, (uuid, existing) -> {
                PlayerData holder = existing != null ? existing : parse(uuid, config);
                outcome[0] = applyReceipt(holder, paymentId, amount);
                return holder;
            });

            if (outcome[0] != HousePaymentResult.APPLIED) {
                if (data.loadFailed) cache.remove(recipient, data);
                return writeNormalizedValueIfNeeded(recipient, data)
                        .thenApply(ignored -> outcome[0]);
            }
            clearNormalizedFlag(data);
            return writeAsync(recipient, data).handle((v, err) -> {
                if (err != null) {
                    // The in-memory receipt is not evidence of a durable credit. Evict this
                    // holder so the next retry is forced through the ordered strict disk read;
                    // otherwise a same-process retry could see ALREADY_APPLIED and compact the
                    // House journal even though this write never landed.
                    cache.remove(recipient, data);
                    plugin.getLogger().log(Level.WARNING, "House payment credit write failed for paymentId="
                            + paymentId + " recipient=" + recipient, err);
                    return HousePaymentResult.REJECTED_WRITE_FAILED;
                }
                scheduleTabRefresh(recipient);
                return HousePaymentResult.APPLIED;
            });
        });
    }

    /** Reads the authoritative on-disk receipt for a house payment, including offline recipients. */
    public CompletableFuture<Boolean> hasHousePaymentReceipt(UUID recipient, String paymentId) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(paymentId, "paymentId");
        return playerStore.readOrderedAsyncStrict(recipient.toString())
                .thenApply(config -> parseReceipts(config).containsKey(paymentId));
    }

    /** Removes the named house-payment receipts after their journal entries have been resolved. */
    public CompletableFuture<Void> clearHousePaymentReceipts(UUID recipient, java.util.Set<String> paymentIds) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(paymentIds, "paymentIds");
        if (paymentIds.isEmpty()) return CompletableFuture.completedFuture(null);

        Set<String> ids = Set.copyOf(paymentIds);
        boolean[] changed = {false};
        PlayerData data = cache.compute(recipient, (uuid, existing) -> {
            PlayerData holder = existing != null ? existing : readFromDisk(uuid);
            if (holder.receipts.isEmpty()) return holder;
            Map<String, Long> receipts = new HashMap<>(holder.receipts);
            for (String paymentId : ids) {
                changed[0] |= receipts.remove(paymentId) != null;
            }
            if (changed[0]) holder.receipts = Map.copyOf(receipts);
            return holder;
        });
        if (!changed[0]) return writeNormalizedValueIfNeeded(recipient, data);
        return writeAsync(recipient, data);
    }

    /**
     * Removes legacy receipts that no longer have any journal entry, including terminal journal
     * entries retained for reconciliation in the supplied keep-set.
     */
    CompletableFuture<Void> pruneStaleHousePaymentReceipts(UUID recipient, Set<String> keepPaymentIds) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(keepPaymentIds, "keepPaymentIds");
        Set<String> keep = Set.copyOf(keepPaymentIds);
        boolean[] changed = {false};
        PlayerData data = cache.compute(recipient, (uuid, existing) -> {
            PlayerData holder = existing != null ? existing : readFromDisk(uuid);
            if (holder.receipts.isEmpty()) return holder;
            Map<String, Long> retained = new HashMap<>();
            for (Map.Entry<String, Long> entry : holder.receipts.entrySet()) {
                if (keep.contains(entry.getKey())) retained.put(entry.getKey(), entry.getValue());
                else changed[0] = true;
            }
            if (changed[0]) holder.receipts = Map.copyOf(retained);
            return holder;
        });
        if (!changed[0]) return writeNormalizedValueIfNeeded(recipient, data);
        return writeAsync(recipient, data);
    }

    private static HousePaymentResult applyReceipt(PlayerData data, String paymentId, long amount) {
        if (data.loadFailed) return HousePaymentResult.REJECTED_WRITE_FAILED;
        Long existingReceipt = data.receipts.get(paymentId);
        if (existingReceipt != null) {
            return existingReceipt == amount ? HousePaymentResult.ALREADY_APPLIED : HousePaymentResult.REJECTED_MISMATCH;
        }
        long next;
        try {
            next = Math.addExact(data.balance, amount);
        } catch (ArithmeticException exception) {
            return HousePaymentResult.REJECTED_UNREPRESENTABLE;
        }
        if (!isValidBalance(next)) {
            return HousePaymentResult.REJECTED_UNREPRESENTABLE;
        }
        Map<String, Long> receipts = new HashMap<>(data.receipts);
        receipts.put(paymentId, amount);
        data.balance = next;
        data.receipts = Map.copyOf(receipts);
        return HousePaymentResult.APPLIED;
    }

    // ---- Last login ---------------------------------------------------------

    public long getLastLogin(UUID player) {
        PlayerData data = cache.get(player);
        return data != null ? data.lastLogin : 0L;
    }

    public void setLastLogin(UUID player, long lastLogin) {
        PlayerData data = get(player);
        if (rejectLoadedFailure(player, data, "set last login")) return;
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
        if (rejectLoadedFailure(player, data, "set login streak")) return;
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
        if (rejectLoadedFailure(player, data, "set balance visibility")) return;
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
        if (rejectLoadedFailure(player, data, "set rank")) return;
        data.rank = rank;
        writeAsync(player, data);
        refreshTabFor(player);
    }

    // ---- Internals ----------------------------------------------------------

    // Return the cached holder, lazily loading from disk on first touch.
    private PlayerData get(UUID player) {
        return cache.computeIfAbsent(player, this::readFromDisk);
    }

    // Atomically read-modify-write a balance field. Routing balance mutation through
    // ConcurrentHashMap#compute makes it mutually exclusive, per key, with applyHousePayment's
    // own compute, so a same-tick mutation cannot overwrite a durable payment credit.
    private BalanceMutationResult mutateBalance(UUID player, long requestedDelta,
                                                BalanceMutation mutation, String operation) {
        Objects.requireNonNull(player, "player");
        BalanceMutationResult[] result = {BalanceMutationResult.REJECTED_UNREPRESENTABLE};
        PlayerData data = cache.compute(player, (uuid, existing) -> {
            PlayerData holder = existing != null ? existing : readFromDisk(uuid);
            if (holder.loadFailed) return holder;
            long next;
            try {
                next = mutation.next(holder.balance, requestedDelta);
            } catch (ArithmeticException exception) {
                return holder;
            }
            if (!isValidBalance(next)) {
                return holder;
            }
            holder.balance = next;
            result[0] = BalanceMutationResult.APPLIED;
            return holder;
        });
        if (data.loadFailed) {
            cache.remove(player, data);
            plugin.getLogger().severe("Refusing balance " + operation + " for " + player
                    + " because the persisted economy file could not be loaded safely");
            return result[0];
        }
        if (result[0] == BalanceMutationResult.REJECTED_UNREPRESENTABLE) {
            plugin.getLogger().warning("Rejected balance " + operation + " for " + player
                    + " amount " + requestedDelta + ": the resulting balance is outside the supported range");
            writeNormalizedValueIfNeeded(player, data);
            return result[0];
        }
        clearNormalizedFlag(data);
        writeAsync(player, data);
        refreshTabFor(player);
        return result[0];
    }

    private static boolean isValidBalance(long value) {
        return value <= MAX_BALANCE && value >= -MAX_BALANCE;
    }

    @FunctionalInterface
    private interface BalanceMutation {
        long next(long current, long requestedDelta);
    }

    // Read a player's YAML into a fresh holder. Missing fields fall back to defaults (an absent
    // file loads as an empty YamlConfiguration, which already yields every default below).
    private PlayerData readFromDisk(UUID player) {
        File file = playerStore.fileFor(player.toString());
        if (!file.exists()) return parse(player, new YamlConfiguration());
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            PlayerData failed = failedPlayerData();
            plugin.getLogger().log(Level.SEVERE,
                    "Refusing to load player economy file for " + player + ": " + file, exception);
            return failed;
        }
        return parse(player, config);
    }

    private PlayerData parse(UUID player, YamlConfiguration config) {
        PlayerData data = new PlayerData();
        Object storedBalance = config.get("balance");
        if (storedBalance != null && !(storedBalance instanceof Number)) {
            data.loadFailed = true;
            plugin.getLogger().severe("Refusing to load non-numeric balance for player " + player
                    + " from raw value " + storedBalance);
            return data;
        }
        double rawBalance = storedBalance instanceof Number number ? number.doubleValue() : 0.0D;
        if (Double.isNaN(rawBalance)) {
            data.balance = 0L;
            logInvalidStoredBalance(player, rawBalance, data.balance);
        } else if (rawBalance == Double.POSITIVE_INFINITY || rawBalance > MAX_BALANCE) {
            data.balance = MAX_BALANCE;
            logInvalidStoredBalance(player, rawBalance, data.balance);
        } else if (rawBalance == Double.NEGATIVE_INFINITY || rawBalance < -MAX_BALANCE) {
            data.balance = -MAX_BALANCE;
            logInvalidStoredBalance(player, rawBalance, data.balance);
        } else {
            data.balance = (long) Math.floor(rawBalance);
        }
        data.normalizedOnLoad = data.balance != rawBalance;
        data.lastLogin = config.getLong("last_login", 0L);
        data.loginStreak = config.getInt("login_streak", 0);
        data.balanceHidden = config.getBoolean("balance_hidden", false);
        data.rank = config.getInt("rank", 0);
        data.receipts = parseReceipts(config);
        return data;
    }

    private static PlayerData failedPlayerData() {
        PlayerData data = new PlayerData();
        data.loadFailed = true;
        return data;
    }

    private void logInvalidStoredBalance(UUID player, double rawBalance, long normalizedBalance) {
        plugin.getLogger().log(Level.SEVERE, "Normalized invalid balance for player " + player
                + " from raw value " + rawBalance + " to " + normalizedBalance);
    }

    private void clearNormalizedFlag(PlayerData data) {
        synchronized (data) {
            data.normalizedOnLoad = false;
        }
    }

    private CompletableFuture<Void> writeNormalizedValueIfNeeded(UUID player, PlayerData data) {
        synchronized (data) {
            if (data.loadFailed || !data.normalizedOnLoad) return CompletableFuture.completedFuture(null);
            data.normalizedOnLoad = false;
        }
        return writeAsync(player, data);
    }

    private boolean rejectLoadedFailure(UUID player, PlayerData data, String operation) {
        if (!data.loadFailed) return false;
        plugin.getLogger().severe("Refusing to " + operation + " for " + player
                + " because the persisted economy file could not be loaded safely");
        cache.remove(player, data);
        return true;
    }

    private static Map<String, Long> parseReceipts(YamlConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("house_payment_receipts");
        if (section == null) return Map.of();
        Map<String, Long> receipts = new HashMap<>();
        for (String key : section.getKeys(false)) {
            receipts.put(key, section.getLong(key));
        }
        return Map.copyOf(receipts);
    }

    // If a TabManager is wired and the player is online, ask it to re-render the tab entry.
    // Must be called on the main thread (playerListName writes must stay synchronous) — never
    // move this into a YamlStore filler or a continuation chained onto a returned future; see
    // YamlStore's class Javadoc for why that isn't enforced by the type system.
    private void refreshTabFor(UUID uuid) {
        if (tabManager == null) return;
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) tabManager.refreshTab(p);
    }

    // applyHousePayment resolves off the main thread (chained onto an async read), so the tab
    // refresh it triggers on success must hop back rather than calling refreshTabFor directly.
    private void scheduleTabRefresh(UUID uuid) {
        if (tabManager == null || !plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> refreshTabFor(uuid));
    }

    // Snapshot the holder's current volatile values (the filler runs on the calling thread — see
    // YamlStore's class Javadoc) and write them to disk off the main thread.
    private CompletableFuture<Void> writeAsync(UUID player, PlayerData data) {
        if (data.loadFailed) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException(
                    "Player economy data is not safely loaded: " + player));
            return failed;
        }
        clearNormalizedFlag(data);
        return playerStore.writeAsync(player.toString(), config -> {
            config.set("balance", data.balance);
            config.set("last_login", data.lastLogin);
            config.set("login_streak", data.loginStreak);
            config.set("balance_hidden", data.balanceHidden);
            config.set("rank", data.rank);
            // Every writer of this file must re-emit receipts, or an unrelated mutation (e.g. a
            // login-streak update) would silently erase a durable house-payment receipt.
            Map<String, Long> receipts = data.receipts;
            if (!receipts.isEmpty()) {
                ConfigurationSection section = config.createSection("house_payment_receipts");
                receipts.forEach(section::set);
            }
        });
    }
}
