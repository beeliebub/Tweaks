package me.beeliebub.tweaks.economy;

import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Server-wide casino house balance. Gameplay may only add to this account; administration owns
 * the debit and set paths. The account is represented as whole dollars because casino wagers are
 * integral amounts. Player balances are also whole dollars, so a transfer stays integral across
 * both account boundaries.
 *
 * <p>Also owns the durable payment journal for {@code /house pay}: {@link #beginPayment} records a
 * {@code DEBIT_DURABLE} intent in the <em>same</em> {@code house.yml} snapshot as the debit, so the
 * debit and its recovery record can never land on disk independently. {@link HousePaymentService}
 * sequences the rest of the transfer (crediting the recipient via
 * {@link EconomyManager#applyHousePayment}) and calls back into {@link #completePayment}/
 * {@link #refundPayment}/{@link #markNeedsReconciliation} to resolve the entry. This class never
 * reads or writes player state itself.
 */
public final class HouseAccount {

    private static final String STORE_KEY = "house";
    private static final String BALANCE_KEY = "balance";
    private static final String JOURNAL_KEY = "payment_journal";

    private final JavaPlugin plugin;
    private final YamlStore store;
    private final AtomicLong balance = new AtomicLong();
    private final AtomicBoolean loaded = new AtomicBoolean();
    private final AtomicBoolean loadFailed = new AtomicBoolean();
    private final Object stateLock = new Object();
    private final CompletableFuture<Void> loadFuture;

    // Guarded entirely by stateLock; every mutator persists {balance, journal} together in one
    // snapshot so an unrelated write (e.g. a blackjack credit()) can never erase an in-flight entry.
    private final Map<String, HouseJournalEntry> journal = new HashMap<>();

    public HouseAccount(JavaPlugin plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, new File(plugin.getDataFolder(), "house"), "house account");
        boolean existingAccountFile = store.exists(STORE_KEY);
        this.loadFuture = store.readAsync(STORE_KEY).thenAccept(config -> {
            // SnakeYAML round-trips a small integer literal as Integer, not Long — isLong() alone
            // would reject every normal reload of a balance under Integer.MAX_VALUE. getLong()
            // already coerces either representation, so accept whichever numeric type is present.
            if (existingAccountFile && !config.isInt(BALANCE_KEY) && !config.isLong(BALANCE_KEY)) {
                failLoad("Existing house-account file has no integral balance; refusing to overwrite it", null);
                return;
            }
            Map<String, HouseJournalEntry> parsedJournal;
            try {
                parsedJournal = parseJournal(config);
            } catch (IllegalArgumentException exception) {
                failLoad("Existing house-account file has a malformed payment journal; refusing to overwrite it", exception);
                return;
            }
            mergeLoaded(config.getLong(BALANCE_KEY, 0L), parsedJournal);
        }).exceptionally(error -> {
            failLoad("Failed to load house account; refusing to overwrite it", error);
            return null;
        });
    }

    private void mergeLoaded(long storedBalance, Map<String, HouseJournalEntry> parsedJournal) {
        synchronized (stateLock) {
            try {
                // Credits earned before the asynchronous load completes are retained. Administrative
                // commands and payments are unavailable until loaded, so there is no competing
                // set/debit/beginPayment operation, and the in-memory journal is still empty here —
                // the persisted journal is authoritative.
                balance.set(Math.addExact(storedBalance, balance.get()));
                journal.putAll(parsedJournal);
                // Submit the merged startup snapshot before publishing readiness: subsequent
                // mutations queue after it for the same YamlStore key and cannot be overwritten.
                persistSnapshot(balance.get());
                loaded.set(true);
            } catch (ArithmeticException exception) {
                failLoad("House account overflow while applying persisted balance", exception);
            }
        }
    }

    private void failLoad(String message, Throwable error) {
        loadFailed.set(true);
        if (error == null) {
            plugin.getLogger().severe(message);
        } else {
            plugin.getLogger().log(Level.SEVERE, message, error);
        }
    }

    private CompletableFuture<Void> failedLoadFuture() {
        return failedFuture(new IllegalStateException("House account did not load safely"));
    }

    private <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> failed = new CompletableFuture<>();
        failed.completeExceptionally(throwable);
        return failed;
    }

    /** Must only be called while holding {@link #stateLock}. */
    private CompletableFuture<Void> persistSnapshot(long snapshot) {
        Map<String, HouseJournalEntry> journalSnapshot = Map.copyOf(journal);
        return store.writeAsync(STORE_KEY, config -> {
            config.set(BALANCE_KEY, snapshot);
            writeJournalSection(config, journalSnapshot);
        });
    }

    private static void writeJournalSection(YamlConfiguration config, Map<String, HouseJournalEntry> journal) {
        if (journal.isEmpty()) return;
        ConfigurationSection section = config.createSection(JOURNAL_KEY);
        journal.forEach((id, entry) -> {
            ConfigurationSection entrySection = section.createSection(id);
            entrySection.set("recipient", entry.recipient().toString());
            entrySection.set("amount", entry.amount());
            entrySection.set("state", entry.state().name());
            entrySection.set("created_at", entry.createdAt());
        });
    }

    private static Map<String, HouseJournalEntry> parseJournal(YamlConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection(JOURNAL_KEY);
        if (section == null) return Map.of();
        Map<String, HouseJournalEntry> parsed = new HashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection entrySection = section.getConfigurationSection(id);
            if (entrySection == null || !entrySection.isString("recipient") || !entrySection.isString("state")
                    || !(entrySection.isLong("amount") || entrySection.isInt("amount"))) {
                throw new IllegalArgumentException("Journal entry " + id + " is missing a required field");
            }
            UUID recipient;
            HousePaymentState state;
            try {
                recipient = UUID.fromString(Objects.requireNonNull(entrySection.getString("recipient")));
                state = HousePaymentState.valueOf(Objects.requireNonNull(entrySection.getString("state")));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Journal entry " + id + " has an unparseable recipient/state", exception);
            }
            long amount = entrySection.getLong("amount");
            if (amount < 0) {
                // A negative amount would otherwise reach EconomyManager.applyHousePayment during
                // replay, which throws synchronously (outside the future chain) rather than
                // completing exceptionally — that would starve HousePaymentService.isReady()
                // instead of failing this one entry closed. Reject it here instead.
                throw new IllegalArgumentException("Journal entry " + id + " has a negative amount");
            }
            parsed.put(id, new HouseJournalEntry(recipient, amount, state, entrySection.getLong("created_at", 0L)));
        }
        return Map.copyOf(parsed);
    }

    /** True after the persisted starting balance has been safely merged into memory. */
    public boolean isLoaded() {
        return loaded.get() && !loadFailed.get();
    }

    /**
     * Completes once the startup load has finished, successfully or not — check {@link #isLoaded()}
     * after it completes to tell which. Used by {@code EconomyBootstrap} to chain {@code
     * HousePaymentService}'s startup replay after load without going through {@link #flush()}'s
     * extra snapshot write.
     */
    public CompletableFuture<Void> whenLoaded() {
        return loadFuture;
    }

    /** Returns the current whole-dollar balance. */
    public long balance() {
        return balance.get();
    }

    /**
     * Credits casino proceeds. This is the only mutator minigames may call.
     *
     * <p>This compatibility method reports whether the in-memory admission succeeded. Callers
     * that must gate a downstream action on durable persistence should use
     * {@link #creditDurably(long)}.
     *
     * @return {@code true} when credited in memory; {@code false} if the amount would overflow
     *         the account
     */
    public boolean credit(long amount) {
        CreditAttempt attempt = beginCredit(amount);
        if (!attempt.accepted()) return false;
        attempt.durable().whenComplete((success, error) -> {
            if (error != null || !Boolean.TRUE.equals(success)) {
                plugin.getLogger().warning("House account credit of " + amount
                        + " was not persisted durably");
            }
        });
        return true;
    }

    /**
     * Credits casino proceeds and completes with {@code true} only after the updated snapshot is
     * durably written. A failed write is logged, the in-memory mutation is rolled back when no
     * newer mutation superseded it, and the future completes with {@code false}.
     */
    public CompletableFuture<Boolean> creditDurably(long amount) {
        CreditAttempt attempt = beginCredit(amount);
        return attempt.accepted() ? attempt.durable() : CompletableFuture.completedFuture(false);
    }

    private CreditAttempt beginCredit(long amount) {
        requireNonNegative(amount);
        synchronized (stateLock) {
            long previous = balance.get();
            long updated;
            try {
                updated = Math.addExact(previous, amount);
            } catch (ArithmeticException exception) {
                plugin.getLogger().log(Level.SEVERE, "House account credit would overflow: " + amount, exception);
                return new CreditAttempt(false, CompletableFuture.completedFuture(false));
            }

            balance.set(updated);
            CompletableFuture<Void> persistence;
            try {
                persistence = isLoaded()
                        ? persistSnapshot(updated)
                        : loadFuture.thenCompose(ignored -> {
                            synchronized (stateLock) {
                                return isLoaded() ? persistSnapshot(balance.get()) : failedLoadFuture();
                            }
                        });
            } catch (RuntimeException error) {
                balance.set(previous);
                plugin.getLogger().log(Level.WARNING,
                        "House account credit snapshot could not be built", error);
                return new CreditAttempt(false, CompletableFuture.completedFuture(false));
            }

            CompletableFuture<Boolean> durable = persistence.handle((ignored, error) -> {
                if (error == null) return CompletableFuture.completedFuture(true);

                CompletableFuture<Void> recovery;
                synchronized (stateLock) {
                    // A later mutation's snapshot includes this credit, so do not erase newer
                    // state while unwinding this failed write. Re-emit the current state either
                    // way so a transient failure gets another ordered attempt.
                    if (balance.get() == updated) balance.set(previous);
                    try {
                        recovery = isLoaded() ? persistSnapshot(balance.get()) : failedLoadFuture();
                    } catch (RuntimeException recoveryBuildError) {
                        plugin.getLogger().log(Level.SEVERE,
                                "House account credit recovery snapshot could not be built", recoveryBuildError);
                        return CompletableFuture.completedFuture(false);
                    }
                }
                return recovery.handle((ignoredRecovery, recoveryError) -> {
                    if (recoveryError != null) {
                        plugin.getLogger().log(Level.SEVERE,
                                "House account credit persistence failed and recovery could not be written",
                                recoveryError);
                    }
                    return false;
                });
            }).thenCompose(future -> future);
            return new CreditAttempt(true, durable);
        }
    }

    private record CreditAttempt(boolean accepted, CompletableFuture<Boolean> durable) {}

    /** Administrative-only debit. Refuses to take the balance below zero. */
    public boolean debit(long amount) {
        requireNonNegative(amount);
        synchronized (stateLock) {
            long current = balance.get();
            if (amount > current) {
                return false;
            }
            balance.set(current - amount);
            if (isLoaded()) {
                persistSnapshot(balance.get());
            }
            return true;
        }
    }

    /** Administrative-only absolute balance setter; negative balances require this explicit path. */
    public void set(long amount) {
        synchronized (stateLock) {
            balance.set(amount);
            if (isLoaded()) {
                persistSnapshot(balance.get());
            }
        }
    }

    /**
     * Debits the house and persists a {@code DEBIT_DURABLE} journal entry for {@code paymentId} in
     * the same snapshot, refusing to overdraw. The returned future completes once that snapshot is
     * durable. Only {@link HousePaymentService} should call this — it owns generating {@code
     * paymentId} and sequencing the rest of the transfer.
     */
    public CompletableFuture<HouseBeginPaymentOutcome> beginPayment(String paymentId, UUID recipient, long amount) {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(recipient, "recipient");
        requireNonNegative(amount);
        synchronized (stateLock) {
            if (!isLoaded()) return failedFuture(notLoaded());
            long current = balance.get();
            if (amount > current) {
                return CompletableFuture.completedFuture(HouseBeginPaymentOutcome.INSUFFICIENT_FUNDS);
            }
            balance.set(current - amount);
            journal.put(paymentId, new HouseJournalEntry(recipient, amount, HousePaymentState.DEBIT_DURABLE, System.currentTimeMillis()));
            return persistSnapshot(balance.get()).thenApply(v -> HouseBeginPaymentOutcome.ACCEPTED);
        }
    }

    /** Compacts a resolved payment's journal entry. A missing entry (already compacted) is a no-op. */
    public CompletableFuture<Void> completePayment(String paymentId) {
        Objects.requireNonNull(paymentId, "paymentId");
        synchronized (stateLock) {
            if (!isLoaded()) return failedFuture(notLoaded());
            if (journal.remove(paymentId) == null) {
                return CompletableFuture.completedFuture(null);
            }
            return persistSnapshot(balance.get());
        }
    }

    /**
     * Credits the entry's amount back to the house and compacts it, for the case where the
     * recipient could not be credited (e.g. an unrepresentable balance). A missing entry is a
     * no-op — there is nothing left to compensate.
     */
    public CompletableFuture<Void> refundPayment(String paymentId) {
        Objects.requireNonNull(paymentId, "paymentId");
        synchronized (stateLock) {
            if (!isLoaded()) return failedFuture(notLoaded());
            HouseJournalEntry entry = journal.get(paymentId);
            if (entry == null) {
                return CompletableFuture.completedFuture(null);
            }
            try {
                balance.set(Math.addExact(balance.get(), entry.amount()));
            } catch (ArithmeticException exception) {
                // Leave the DEBIT_DURABLE entry in place rather than losing the record of an
                // un-refunded debit; an admin must reconcile this manually.
                plugin.getLogger().log(Level.SEVERE,
                        "House account refund would overflow; leaving journal entry " + paymentId + " for manual reconciliation", exception);
                return failedFuture(exception);
            }
            journal.remove(paymentId);
            return persistSnapshot(balance.get());
        }
    }

    /**
     * Transitions an entry to {@link HousePaymentState#NEEDS_RECONCILIATION}: terminal, retained
     * for audit, never replayed again. Used when a receipt exists under this payment id with a
     * different amount than the journal records — crediting or refunding would guess, so this
     * leaves the money debited and the discrepancy visible instead.
     */
    public CompletableFuture<Void> markNeedsReconciliation(String paymentId) {
        Objects.requireNonNull(paymentId, "paymentId");
        synchronized (stateLock) {
            if (!isLoaded()) return failedFuture(notLoaded());
            HouseJournalEntry entry = journal.get(paymentId);
            if (entry == null) {
                return CompletableFuture.completedFuture(null);
            }
            journal.put(paymentId, new HouseJournalEntry(entry.recipient(), entry.amount(),
                    HousePaymentState.NEEDS_RECONCILIATION, entry.createdAt()));
            return persistSnapshot(balance.get());
        }
    }

    /** Snapshot of every {@code DEBIT_DURABLE} entry, for startup replay. */
    public Map<String, HouseJournalEntry> pendingPayments() {
        synchronized (stateLock) {
            Map<String, HouseJournalEntry> pending = new HashMap<>();
            journal.forEach((id, entry) -> {
                if (entry.state() == HousePaymentState.DEBIT_DURABLE) pending.put(id, entry);
            });
            return Map.copyOf(pending);
        }
    }

    /** Snapshot of every journal entry, including terminal entries retained for reconciliation. */
    public Map<String, HouseJournalEntry> journalEntries() {
        synchronized (stateLock) {
            return Map.copyOf(journal);
        }
    }

    private static IllegalStateException notLoaded() {
        return new IllegalStateException("House account did not load safely");
    }

    /** Queues a final ordered snapshot write for {@code Tweaks#onDisable()}. */
    public CompletableFuture<Void> flush() {
        return loadFuture.thenCompose(ignored -> {
            synchronized (stateLock) {
                return isLoaded() ? persistSnapshot(balance.get()) : failedLoadFuture();
            }
        });
    }

    private static void requireNonNegative(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("House account amounts must not be negative");
        }
    }
}
