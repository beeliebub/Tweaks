package me.beeliebub.tweaks.economy;

import me.beeliebub.tweaks.tab.TabManager;
import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

// Handles async YAML persistence for per-player economy/ranks data.
// Each player is cached in memory and saved to disk asynchronously via utils/YamlStore.
public class EconomyManager {

    /** Largest whole-dollar value that can be safely combined with another whole-dollar amount. */
    public static final long MAX_EXACT_DOUBLE_INTEGER = 1L << 53;

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
        volatile double balance;
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

    public double getBalance(UUID player) {
        PlayerData data = cache.get(player);
        return data != null ? data.balance : 0.0D;
    }

    public BalanceMutationResult setBalance(UUID player, double balance) {
        return mutateBalance(player, balance, (current, ignored) -> balance, "set");
    }

    public BalanceMutationResult addBalance(UUID player, double amount) {
        return mutateBalance(player, amount, (current, delta) -> current + delta, "add");
    }

    public BalanceMutationResult removeBalance(UUID player, double amount) {
        return mutateBalance(player, -amount, (current, delta) -> current + delta, "remove");
    }

    /**
     * Idempotently credits a whole-dollar house payment: {@code paymentId} and the recipient's new
     * balance are written together in one snapshot, so "credited but no receipt" is unrepresentable
     * on disk. Safe to call again with the same {@code paymentId} after a crash — a matching receipt
     * short-circuits to {@link HousePaymentResult#ALREADY_APPLIED} without touching the balance
     * again; a mismatched receipt (same id, different amount) fails closed rather than overwriting.
     *
     * <p>Reads the recipient's on-disk state directly rather than through {@link #getBalance}, which
     * returns a hardcoded {@code 0.0} for an unloaded player without touching disk (see this class's
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
                PlayerData holder = existing != null ? existing : parse(config);
                outcome[0] = applyReceipt(holder, paymentId, amount);
                return holder;
            });

            if (outcome[0] != HousePaymentResult.APPLIED) {
                return CompletableFuture.completedFuture(outcome[0]);
            }
            return writeAsync(recipient, data).handle((v, err) -> {
                if (err != null) {
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

    private static HousePaymentResult applyReceipt(PlayerData data, String paymentId, long amount) {
        Long existingReceipt = data.receipts.get(paymentId);
        if (existingReceipt != null) {
            return existingReceipt == amount ? HousePaymentResult.ALREADY_APPLIED : HousePaymentResult.REJECTED_MISMATCH;
        }
        double delta = amount;
        double next = data.balance + delta;
        if (!isRepresentableMutation(data.balance, delta, next)) {
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
    private BalanceMutationResult mutateBalance(UUID player, double requestedDelta,
                                                BalanceMutation mutation, String operation) {
        Objects.requireNonNull(player, "player");
        BalanceMutationResult[] result = {BalanceMutationResult.REJECTED_UNREPRESENTABLE};
        PlayerData data = cache.compute(player, (uuid, existing) -> {
            PlayerData holder = existing != null ? existing : readFromDisk(uuid);
            double next = mutation.next(holder.balance, requestedDelta);
            double requestedChange = "set".equals(operation) ? next - holder.balance : requestedDelta;
            if (!isRepresentableMutation(holder.balance, requestedChange, next)) {
                return holder;
            }
            holder.balance = next;
            result[0] = BalanceMutationResult.APPLIED;
            return holder;
        });
        if (result[0] == BalanceMutationResult.REJECTED_UNREPRESENTABLE) {
            plugin.getLogger().warning("Rejected balance " + operation + " for " + player
                    + " amount " + requestedDelta + ": the balance cannot represent the mutation exactly");
            return result[0];
        }
        writeAsync(player, data);
        refreshTabFor(player);
        return result[0];
    }

    private static boolean isRepresentableMutation(double current, double delta, double next) {
        return Double.isFinite(current)
                && Double.isFinite(delta)
                && Double.isFinite(next)
                && Math.abs(next) <= MAX_EXACT_DOUBLE_INTEGER
                && next - current == delta;
    }

    @FunctionalInterface
    private interface BalanceMutation {
        double next(double current, double requestedDelta);
    }

    // Non-balance setters use the simpler get-then-mutate shape because they do not participate in
    // the payment credit's balance read-modify-write operation.
    private PlayerData mutate(UUID player, java.util.function.Consumer<PlayerData> mutator) {
        return cache.compute(player, (uuid, existing) -> {
            PlayerData holder = existing != null ? existing : readFromDisk(uuid);
            mutator.accept(holder);
            return holder;
        });
    }

    // Read a player's YAML into a fresh holder. Missing fields fall back to defaults (an absent
    // file loads as an empty YamlConfiguration, which already yields every default below).
    private PlayerData readFromDisk(UUID player) {
        return parse(playerStore.read(player.toString()));
    }

    private static PlayerData parse(YamlConfiguration config) {
        PlayerData data = new PlayerData();
        data.balance = config.getDouble("balance", 0.0D);
        data.lastLogin = config.getLong("last_login", 0L);
        data.loginStreak = config.getInt("login_streak", 0);
        data.balanceHidden = config.getBoolean("balance_hidden", false);
        data.rank = config.getInt("rank", 0);
        data.receipts = parseReceipts(config);
        return data;
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
