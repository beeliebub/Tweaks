package me.beeliebub.tweaks.economy;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Owns the crash-durable sequencing of a {@code /house pay} transfer between {@link HouseAccount}'s
 * journal and {@link EconomyManager}'s receipts. Neither of those two classes knows about the
 * other; this service is the only thing that calls both.
 *
 * <p>{@link #pay} is strictly sequenced ({@code thenCompose}, never {@code allOf}) through
 * {@code HouseAccount.beginPayment} (debit + journal, one snapshot) → {@code
 * EconomyManager.applyHousePayment} (credit + receipt, one snapshot) → a resolving call back into
 * {@code HouseAccount} ({@code completePayment} on success, {@code refundPayment} on an
 * unrepresentable balance, {@code markNeedsReconciliation} on a mismatched receipt). Any step that
 * fails unexpectedly simply propagates — the journal entry is left exactly as it was, which is
 * always safe to replay, since only a definite outcome ever mutates or compacts it.
 *
 * <p>{@link #replayPendingPayments()} resumes every surviving {@code DEBIT_DURABLE} entry from the
 * point after the debit (never re-debits) and must complete — successfully or not, per entry —
 * before {@link #isReady()} becomes {@code true}. {@link #beginShutdown()} then {@link
 * #awaitInFlight()} give {@code Tweaks#onDisable()} a bounded point to wait for in-flight transfers
 * before the generic economy/house flushes run.
 */
public final class HousePaymentService {

    private final JavaPlugin plugin;
    private final HouseAccount houseAccount;
    private final EconomyManager economyManager;

    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean replayStarted = new AtomicBoolean();
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private final CompletableFuture<Void> readyFuture = new CompletableFuture<>();
    private final Map<String, CompletableFuture<HousePayOutcome>> inFlight = new ConcurrentHashMap<>();

    public HousePaymentService(JavaPlugin plugin, HouseAccount houseAccount, EconomyManager economyManager) {
        this.plugin = plugin;
        this.houseAccount = houseAccount;
        this.economyManager = economyManager;
    }

    /** True once startup replay of any surviving journal entry has finished. */
    public boolean isReady() {
        return ready.get();
    }

    /** Initiates a new admin-driven payment. Generates and owns the payment id. */
    public CompletableFuture<HousePayOutcome> pay(UUID recipient, long amount) {
        return pay(UUID.randomUUID().toString(), recipient, amount);
    }

    /** Initiates a payment using a durable caller-supplied id, preserving replay idempotency. */
    public CompletableFuture<HousePayOutcome> pay(String paymentId, UUID recipient, long amount) {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(recipient, "recipient");
        if (paymentId.isBlank()) throw new IllegalArgumentException("paymentId must not be blank");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        if (shuttingDown.get()) {
            return CompletableFuture.completedFuture(HousePayOutcome.SHUTTING_DOWN);
        }
        if (!ready.get()) {
            return CompletableFuture.completedFuture(HousePayOutcome.NOT_READY);
        }

        CompletableFuture<HousePayOutcome> future = inFlight.computeIfAbsent(paymentId,
                ignored -> startPayment(paymentId, recipient, amount));
        future.whenComplete((value, error) -> inFlight.remove(paymentId, future));
        return future;
    }

    /** Resumes a retained debit without entering the debit path a second time. */
    public CompletableFuture<HousePayOutcome> resumePendingPayment(String paymentId, UUID recipient, long amount) {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(recipient, "recipient");
        if (paymentId.isBlank()) throw new IllegalArgumentException("paymentId must not be blank");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        if (!ready.get()) return CompletableFuture.completedFuture(HousePayOutcome.NOT_READY);

        HouseJournalEntry entry = houseAccount.pendingPayments().get(paymentId);
        if (entry == null) return CompletableFuture.completedFuture(HousePayOutcome.FAILED);
        CompletableFuture<HousePayOutcome> future = inFlight.computeIfAbsent(paymentId,
                ignored -> startRetainedPayment(paymentId, recipient, amount, entry));
        future.whenComplete((value, error) -> inFlight.remove(paymentId, future));
        return future;
    }

    private CompletableFuture<HousePayOutcome> startPayment(String paymentId, UUID recipient, long amount) {
        HouseJournalEntry existing = houseAccount.pendingPayments().get(paymentId);
        CompletableFuture<HousePayOutcome> payment;
        if (existing != null) {
            if (!existing.recipient().equals(recipient) || existing.amount() != amount) {
                payment = houseAccount.markNeedsReconciliation(paymentId).thenApply(ignored -> {
                    plugin.getLogger().severe("House payment " + paymentId
                            + " was requested with details that differ from its retained journal entry");
                    return HousePayOutcome.NEEDS_RECONCILIATION;
                });
            } else {
                payment = resolvePayment(paymentId, recipient, amount);
            }
        } else {
            payment = houseAccount.beginPayment(paymentId, recipient, amount)
                    .thenCompose(begin -> begin == HouseBeginPaymentOutcome.INSUFFICIENT_FUNDS
                            ? CompletableFuture.completedFuture(HousePayOutcome.INSUFFICIENT_FUNDS)
                            : resolvePayment(paymentId, recipient, amount));
        }
        return payment.exceptionally(error -> {
            plugin.getLogger().log(Level.SEVERE, "House payment " + paymentId + " failed unexpectedly; "
                    + "any debit already recorded is retained for replay on next startup", error);
            return HousePayOutcome.FAILED;
        });
    }

    private CompletableFuture<HousePayOutcome> startRetainedPayment(String paymentId, UUID recipient,
                                                                      long amount, HouseJournalEntry entry) {
        CompletableFuture<HousePayOutcome> payment;
        if (!entry.recipient().equals(recipient) || entry.amount() != amount) {
            payment = houseAccount.markNeedsReconciliation(paymentId).thenApply(ignored -> {
                plugin.getLogger().severe("House payment " + paymentId
                        + " was requested with details that differ from its retained journal entry");
                return HousePayOutcome.NEEDS_RECONCILIATION;
            });
        } else {
            payment = resolvePayment(paymentId, recipient, amount);
        }
        return payment.exceptionally(error -> {
            plugin.getLogger().log(Level.SEVERE, "Replay of house payment " + paymentId
                    + " failed; the retained debit remains for a later retry", error);
            return HousePayOutcome.FAILED;
        });
    }

    /**
     * Resumes every surviving {@code DEBIT_DURABLE} journal entry from the point after the debit.
     * Idempotent by construction — {@code EconomyManager.applyHousePayment} short-circuits on a
     * matching receipt — so running this twice (e.g. two crashes before recovery completes) never
     * double-credits. Sets {@link #isReady()} once every entry has been resolved, whether or not
     * there were any.
     */
    public CompletableFuture<Void> replayPendingPayments() {
        if (!replayStarted.compareAndSet(false, true)) return readyFuture;
        Map<String, HouseJournalEntry> pending = houseAccount.pendingPayments();
        if (pending.isEmpty()) {
            ready.set(true);
            readyFuture.complete(null);
            return readyFuture;
        }
        plugin.getLogger().info("Replaying " + pending.size() + " pending house payment(s) from a prior crash/shutdown");
        CompletableFuture<?>[] replays = pending.entrySet().stream()
                .map(entry -> replayOne(entry.getKey(), entry.getValue()))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(replays).whenComplete((v, err) -> {
            ready.set(true);
            if (err == null) readyFuture.complete(null);
            else readyFuture.completeExceptionally(err);
        });
        return readyFuture;
    }

    /** Completes after the one-time startup replay has finished. */
    public CompletableFuture<Void> whenReady() {
        return readyFuture;
    }

    private CompletableFuture<HousePayOutcome> replayOne(String paymentId, HouseJournalEntry entry) {
        plugin.getLogger().warning("Replaying house payment " + paymentId + " -> " + entry.recipient()
                + " (" + entry.amount() + ")");
        CompletableFuture<HousePayOutcome> future = inFlight.computeIfAbsent(paymentId, ignored ->
                resolvePayment(paymentId, entry.recipient(), entry.amount())
                        .exceptionally(error -> {
                            plugin.getLogger().log(Level.SEVERE, "Replay of house payment " + paymentId + " failed; "
                                    + "will be retried on next startup", error);
                            return HousePayOutcome.FAILED;
                        }));
        future.whenComplete((value, error) -> inFlight.remove(paymentId, future));
        return future;
    }

    // Resumes a payment from immediately after the debit: credit the recipient, then resolve the
    // journal entry according to the credit's outcome. Shared by both the fresh pay() path and replay.
    private CompletableFuture<HousePayOutcome> resolvePayment(String paymentId, UUID recipient, long amount) {
        return economyManager.applyHousePayment(recipient, paymentId, amount)
                .thenCompose(result -> settle(paymentId, result));
    }

    private CompletableFuture<HousePayOutcome> settle(String paymentId, HousePaymentResult result) {
        return switch (result) {
            case APPLIED, ALREADY_APPLIED -> houseAccount.completePayment(paymentId)
                    .thenApply(v -> HousePayOutcome.SUCCESS);
            case REJECTED_UNREPRESENTABLE -> houseAccount.refundPayment(paymentId)
                    .thenApply(v -> HousePayOutcome.UNREPRESENTABLE_REFUNDED);
            case REJECTED_MISMATCH -> houseAccount.markNeedsReconciliation(paymentId).thenApply(v -> {
                plugin.getLogger().severe("House payment " + paymentId
                        + " has a receipt mismatch; needs manual reconciliation");
                return HousePayOutcome.NEEDS_RECONCILIATION;
            });
            case REJECTED_WRITE_FAILED -> {
                // Cannot prove the credit didn't land, so refunding here would risk minting.
                // Leave DEBIT_DURABLE in place; the next startup's replay will resolve it.
                plugin.getLogger().severe("House payment " + paymentId
                        + " credit write failed; retained for replay on next startup");
                yield CompletableFuture.completedFuture(HousePayOutcome.WRITE_FAILED_RETAINED);
            }
        };
    }

    /** Rejects any new payment submitted after this call. Does not affect payments already in flight. */
    public void beginShutdown() {
        shuttingDown.set(true);
    }

    /** A bounded-wait point for {@code Tweaks#onDisable()}: completes once every in-flight payment has resolved. */
    public CompletableFuture<Void> awaitInFlight() {
        return CompletableFuture.allOf(inFlight.values().toArray(CompletableFuture[]::new));
    }
}
