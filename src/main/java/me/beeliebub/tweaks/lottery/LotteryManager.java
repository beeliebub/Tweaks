package me.beeliebub.tweaks.lottery;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.economy.HousePayOutcome;
import me.beeliebub.tweaks.economy.HousePaymentService;
import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Owns the persisted entrant pool, baseline, and crash-recoverable lottery award. */
public final class LotteryManager {

    private static final String STORE_KEY = "lottery";
    private static final String BASELINE_KEY = "baseline";
    private static final String ENTRANTS_KEY = "entrants";
    private static final String PENDING_KEY = "pending_award";
    private static final long DEFAULT_RESEED_AMOUNT = 10_000L;
    private static final int MAX_REPLAY_ATTEMPTS = 3;

    private final Tweaks plugin;
    private final HouseAccount houseAccount;
    private final HousePaymentService housePaymentService;
    private final EconomyManager economyManager;
    private final YamlStore store;
    private final SecureRandom random = new SecureRandom();
    private final Object stateLock = new Object();
    private final AtomicBoolean loaded = new AtomicBoolean();
    private final AtomicBoolean loadFailed = new AtomicBoolean();
    private final AtomicBoolean drawInFlight = new AtomicBoolean();
    private final CompletableFuture<Void> loadFuture;

    private final Set<UUID> entrants = new HashSet<>();
    private long baseline;
    private PendingAward pendingAward;

    public LotteryManager(Tweaks plugin, HouseAccount houseAccount,
                          HousePaymentService housePaymentService, EconomyManager economyManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.houseAccount = Objects.requireNonNull(houseAccount, "houseAccount");
        this.housePaymentService = Objects.requireNonNull(housePaymentService, "housePaymentService");
        this.economyManager = Objects.requireNonNull(economyManager, "economyManager");
        this.store = new YamlStore(plugin, new File(plugin.getDataFolder(), "lottery"), "lottery state");
        boolean existingFile = store.exists(STORE_KEY);
        this.loadFuture = store.readAsync(STORE_KEY)
                .thenCompose(config -> initialize(config, existingFile))
                .exceptionally(error -> {
                    failLoad("Lottery state failed to load safely; draws and entries remain disabled", error);
                    return null;
                });
    }

    private CompletableFuture<Void> initialize(YamlConfiguration config, boolean existingFile) {
        ParsedState parsed;
        try {
            parsed = parse(config, existingFile);
        } catch (IllegalArgumentException error) {
            failLoad("Lottery state is malformed; refusing to overwrite it", error);
            return CompletableFuture.completedFuture(null);
        }

        return houseAccount.whenLoaded().thenCompose(ignored -> {
            if (!houseAccount.isLoaded()) {
                return failedFuture(new IllegalStateException("House account did not load safely"));
            }
            synchronized (stateLock) {
                entrants.addAll(parsed.entrants());
                baseline = parsed.baseline() != null ? parsed.baseline() : houseAccount.balance();
                pendingAward = parsed.pendingAward();
            }
            CompletableFuture<Void> baselineWrite = parsed.baseline() == null
                    ? persistSnapshot()
                    : CompletableFuture.completedFuture(null);
            if (pendingAward == null) return baselineWrite;
            return baselineWrite.thenCompose(ignoredWrite -> housePaymentService.whenReady())
                    .thenCompose(ignoredReady -> replayPendingAward());
        }).thenRun(() -> {
            if (!loadFailed.get()) loaded.set(true);
        });
    }

    private ParsedState parse(YamlConfiguration config, boolean existingFile) {
        Long parsedBaseline = null;
        if (config.contains(BASELINE_KEY)) {
            if (!config.isInt(BASELINE_KEY) && !config.isLong(BASELINE_KEY)) {
                throw new IllegalArgumentException("baseline must be an integral value");
            }
            parsedBaseline = config.getLong(BASELINE_KEY);
            if (parsedBaseline < 0) throw new IllegalArgumentException("baseline must not be negative");
        } else if (existingFile) {
            throw new IllegalArgumentException("existing lottery state has no baseline");
        }

        Set<UUID> parsedEntrants = new HashSet<>();
        if (config.contains(ENTRANTS_KEY)) {
            if (!config.isList(ENTRANTS_KEY)) throw new IllegalArgumentException("entrants must be a list");
            for (Object raw : config.getList(ENTRANTS_KEY)) {
                if (!(raw instanceof String value)) throw new IllegalArgumentException("entrant is not a UUID string");
                try {
                    parsedEntrants.add(UUID.fromString(value));
                } catch (IllegalArgumentException error) {
                    throw new IllegalArgumentException("entrant is not a valid UUID", error);
                }
            }
        }

        PendingAward parsedPending = null;
        ConfigurationSection section = config.getConfigurationSection(PENDING_KEY);
        if (section != null) {
            if (!section.isString("payment_id") || !section.isString("winner")
                    || (!section.isLong("amount") && !section.isInt("amount"))
                    || (!section.isLong("created_at") && !section.isInt("created_at"))) {
                throw new IllegalArgumentException("pending_award is missing a required field");
            }
            UUID winner;
            try {
                winner = UUID.fromString(section.getString("winner"));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("pending_award winner is not a UUID", error);
            }
            long amount = section.getLong("amount");
            if (amount < 1) throw new IllegalArgumentException("pending_award amount must be positive");
            Long reseedTo = null;
            if (section.contains("reseed_to")) {
                if (!section.isLong("reseed_to") && !section.isInt("reseed_to")) {
                    throw new IllegalArgumentException("pending_award reseed_to must be integral");
                }
                reseedTo = section.getLong("reseed_to");
                if (reseedTo < 1) throw new IllegalArgumentException("pending_award reseed_to must be positive");
            }
            List<UUID> awardEntrants = new ArrayList<>();
            if (section.contains("entrants")) {
                if (!section.isList("entrants")) {
                    throw new IllegalArgumentException("pending_award entrants must be a list");
                }
                for (Object raw : section.getList("entrants")) {
                    if (!(raw instanceof String value)) {
                        throw new IllegalArgumentException("pending_award entrant is not a UUID string");
                    }
                    try {
                        awardEntrants.add(UUID.fromString(value));
                    } catch (IllegalArgumentException error) {
                        throw new IllegalArgumentException("pending_award entrant is not a valid UUID", error);
                    }
                }
            }
            Long newBaseline = null;
            if (section.contains("new_baseline")) {
                if (!section.isLong("new_baseline") && !section.isInt("new_baseline")) {
                    throw new IllegalArgumentException("pending_award new_baseline must be integral");
                }
                newBaseline = section.getLong("new_baseline");
                if (newBaseline < 0) {
                    throw new IllegalArgumentException("pending_award new_baseline must not be negative");
                }
            }
            int attempts = 0;
            if (section.contains("attempts")) {
                if (!section.isInt("attempts") && !section.isLong("attempts")) {
                    throw new IllegalArgumentException("pending_award attempts must be integral");
                }
                long parsedAttempts = section.getLong("attempts");
                if (parsedAttempts < 0 || parsedAttempts > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("pending_award attempts is out of range");
                }
                attempts = (int) parsedAttempts;
            }
            parsedPending = new PendingAward(section.getString("payment_id"), winner, amount,
                    reseedTo, section.getLong("created_at"), List.copyOf(awardEntrants), newBaseline, attempts);
        }
        return new ParsedState(parsedBaseline, parsedEntrants, parsedPending);
    }

    private void failLoad(String message, Throwable error) {
        loadFailed.set(true);
        if (error == null) plugin.getLogger().severe(message);
        else plugin.getLogger().log(Level.SEVERE, message, error);
    }

    public boolean isLoaded() {
        return loaded.get() && !loadFailed.get();
    }

    public CompletableFuture<Void> whenLoaded() {
        return loadFuture;
    }

    public int entrantCount() {
        synchronized (stateLock) {
            return entrants.size();
        }
    }

    public List<UUID> entrantSnapshot() {
        synchronized (stateLock) {
            return List.copyOf(entrants);
        }
    }

    public long baseline() {
        synchronized (stateLock) {
            return baseline;
        }
    }

    public boolean enter(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (stateLock) {
            if (!isLoaded() || !entrants.add(playerId)) return false;
            persistSnapshot();
            return true;
        }
    }

    public CompletableFuture<Boolean> setBaseline(long value) {
        if (value < 0) throw new IllegalArgumentException("baseline must not be negative");
        synchronized (stateLock) {
            if (!isLoaded()) return CompletableFuture.completedFuture(false);
            long previous = baseline;
            baseline = value;
            return persistSnapshot().handle((ignored, error) -> {
                if (error == null) return true;
                synchronized (stateLock) {
                    if (baseline == value) baseline = previous;
                }
                throw new java.util.concurrent.CompletionException(error);
            });
        }
    }

    public LotteryMath.PotOutcome currentPot() {
        synchronized (stateLock) {
            return LotteryMath.calculate(entrants.size(), houseAccount.balance(), baseline, reseedAmount());
        }
    }

    public CompletableFuture<DrawResult> draw() {
        if (!isLoaded()) return CompletableFuture.completedFuture(DrawResult.notReady());
        if (!drawInFlight.compareAndSet(false, true)) return CompletableFuture.completedFuture(DrawResult.inFlight());

        try {
            DrawContext context;
            synchronized (stateLock) {
                if (pendingAward != null) {
                    drawInFlight.set(false);
                    return CompletableFuture.completedFuture(DrawResult.paymentPending(
                            pendingAward.paymentId(), pendingAward.winner(), pendingAward.amount(),
                            HousePayOutcome.FAILED));
                }
                List<UUID> snapshot = List.copyOf(entrants);
                LotteryMath.PotOutcome outcome = LotteryMath.calculate(snapshot.size(), houseAccount.balance(),
                        baseline, reseedAmount());
                if (outcome instanceof LotteryMath.PotOutcome.Refused refused) {
                    drawInFlight.set(false);
                    return CompletableFuture.completedFuture(DrawResult.refused(refused.reason()));
                }
                LotteryMath.PotOutcome.Payable payable = (LotteryMath.PotOutcome.Payable) outcome;
                UUID winner = snapshot.get(random.nextInt(snapshot.size()));
                PendingAward award = new PendingAward(UUID.randomUUID().toString(), winner, payable.pot(),
                        payable.capBound() ? payable.newHouseBalance() : null, System.currentTimeMillis(),
                        snapshot, payable.newBaseline(), 0);
                pendingAward = award;
                context = new DrawContext(award);
            }

            return persistSnapshot()
                    .thenCompose(ignored -> housePaymentService.pay(context.award().paymentId(),
                            context.award().winner(), context.award().amount()))
                    .thenCompose(payment -> finishAward(context, payment))
                    .whenComplete((result, error) -> drawInFlight.set(false));
        } catch (RuntimeException error) {
            drawInFlight.set(false);
            return failedFuture(error);
        }
    }

    private CompletableFuture<DrawResult> finishAward(DrawContext context, HousePayOutcome payment) {
        return switch (classify(payment)) {
            case SETTLED -> commitAward(context.award(), false)
                    .thenApply(ignored -> DrawResult.awarded(context.award().winner(), context.award().amount(),
                            context.award().reseedTo() != null));
            case ABANDONED -> abandonAward(context.award())
                    .thenApply(ignored -> DrawResult.paymentAbandoned(context.award().winner(),
                            context.award().amount(), payment, context.award().entrants().size()));
            case PENDING -> CompletableFuture.completedFuture(DrawResult.paymentPending(
                    context.award().paymentId(), context.award().winner(), context.award().amount(), payment));
            case STUCK -> {
                plugin.getLogger().severe("Lottery award " + context.award().paymentId() + " for "
                        + context.award().winner() + " ($" + context.award().amount()
                        + ") needs manual reconciliation; retaining the committed draw state.");
                yield commitAward(context.award(), false)
                        .thenApply(ignored -> DrawResult.paymentStuck(context.award().paymentId(),
                                context.award().winner(), context.award().amount()));
            }
        };
    }

    private CompletableFuture<Void> replayPendingAward() {
        PendingAward award;
        synchronized (stateLock) {
            award = pendingAward;
        }
        if (award == null) return CompletableFuture.completedFuture(null);
        return replayAwardOnce(award).handle((ignored, error) -> {
            if (error == null) return CompletableFuture.<Void>completedFuture(null);
            return handleReplayFailure(award, error);
        }).thenCompose(future -> future);
    }

    private CompletableFuture<Void> replayAwardOnce(PendingAward award) {
        return economyManager.hasHousePaymentReceipt(award.winner(), award.paymentId())
                .thenCompose(receipt -> {
                    boolean retainedDebit = houseAccount.pendingPayments().containsKey(award.paymentId());
                    if (!retainedDebit && receipt) return finishReplayedAward(award, HousePayOutcome.SUCCESS);
                    if (!retainedDebit) {
                        plugin.getLogger().warning("Lottery award " + award.paymentId() + " for "
                                + award.winner() + " ($" + award.amount()
                                + ") has no receipt or retained House debit; abandoning it without re-paying.");
                        return abandonAward(award);
                    }
                    return housePaymentService.resumePendingPayment(award.paymentId(), award.winner(), award.amount())
                            .thenCompose(payment -> finishReplayedAward(award, payment));
                });
    }

    private CompletableFuture<Void> finishReplayedAward(PendingAward award, HousePayOutcome payment) {
        return switch (classify(payment)) {
            case SETTLED -> commitAward(award, false);
            case ABANDONED -> {
                plugin.getLogger().warning("Lottery award replay for " + award.winner() + " ($"
                        + award.amount() + ") resolved as " + payment + "; entries and baseline were kept.");
                yield abandonAward(award);
            }
            case PENDING -> CompletableFuture.completedFuture(null);
            case STUCK -> {
                plugin.getLogger().severe("Lottery award " + award.paymentId() + " for "
                        + award.winner() + " ($" + award.amount()
                        + ") needs manual reconciliation; retaining the committed draw state.");
                yield commitAward(award, false);
            }
        };
    }

    private CompletableFuture<Void> handleReplayFailure(PendingAward award, Throwable error) {
        PendingAward current;
        synchronized (stateLock) {
            current = pendingAward;
        }
        if (current == null || !current.paymentId().equals(award.paymentId())) {
            return CompletableFuture.completedFuture(null);
        }
        int attempts = current.attempts() + 1;
        if (attempts >= MAX_REPLAY_ATTEMPTS) {
            plugin.getLogger().log(Level.SEVERE, "Lottery award " + current.paymentId() + " for "
                    + current.winner() + " ($" + current.amount()
                    + ") could not be resolved after " + attempts
                    + " indeterminate recovery attempts; abandoning the lottery record while leaving "
                    + "any House journal entry untouched.", error);
            return abandonAward(current);
        }
        PendingAward retried = current.withAttempts(attempts);
        synchronized (stateLock) {
            pendingAward = retried;
        }
        return persistSnapshot().handle((ignored, persistError) -> {
            if (persistError != null) {
                plugin.getLogger().log(Level.WARNING, "Lottery recovery attempt counter could not be persisted for "
                        + retried.paymentId(), persistError);
            }
            return null;
        });
    }

    private CompletableFuture<Void> abandonAward(PendingAward award) {
        synchronized (stateLock) {
            if (pendingAward == null || !pendingAward.paymentId().equals(award.paymentId())) {
                return CompletableFuture.completedFuture(null);
            }
            pendingAward = null;
        }
        return persistSnapshot().handle((ignored, error) -> {
            if (error == null) return null;
            synchronized (stateLock) {
                if (pendingAward == null) pendingAward = award;
            }
            throw new java.util.concurrent.CompletionException(error);
        });
    }

    private CompletableFuture<Void> commitAward(PendingAward award, boolean retainPending) {
        CompletableFuture<Void> reseed = CompletableFuture.completedFuture(null);
        if (award.reseedTo() != null && houseAccount.balance() < award.reseedTo()) {
            long reseedTo = award.reseedTo();
            houseAccount.set(reseedTo);
            plugin.getLogger().info("Lottery paid a 100% draw; reseeding the House to $" + reseedTo);
            reseed = houseAccount.flush();
        }
        return reseed.thenCompose(ignored -> {
            Snapshot previous;
            synchronized (stateLock) {
                previous = new Snapshot(baseline, new ArrayList<>(entrants), pendingAward);
                entrants.removeAll(award.entrants());
                if (award.newBaseline() != null) baseline = award.newBaseline();
                if (!retainPending) pendingAward = null;
            }
            return persistSnapshot().handle((ignoredSnapshot, error) -> {
                if (error == null) return null;
                synchronized (stateLock) {
                    // Preserve entries added while the award was in flight. Only restore the
                    // recorded entrants and the fields this award owns; an admin baseline edit
                    // or a new entrant must not be lost while the failed write is unwinding.
                    entrants.addAll(previous.entrants());
                    if (award.newBaseline() == null || baseline == award.newBaseline()) {
                        baseline = previous.baseline();
                    }
                    if (pendingAward == null
                            || pendingAward.paymentId().equals(award.paymentId())) {
                        pendingAward = previous.pendingAward();
                    }
                }
                throw new java.util.concurrent.CompletionException(error);
            });
        });
    }

    private CompletableFuture<Void> persistSnapshot() {
        Snapshot snapshot;
        synchronized (stateLock) {
            snapshot = new Snapshot(baseline, new ArrayList<>(entrants), pendingAward);
        }
        return store.writeAsync(STORE_KEY, config -> {
            config.set(BASELINE_KEY, snapshot.baseline());
            config.set(ENTRANTS_KEY, snapshot.entrants().stream().map(UUID::toString).toList());
            if (snapshot.pendingAward() != null) {
                PendingAward award = snapshot.pendingAward();
                ConfigurationSection section = config.createSection(PENDING_KEY);
                section.set("payment_id", award.paymentId());
                section.set("winner", award.winner().toString());
                section.set("amount", award.amount());
                if (award.reseedTo() != null) section.set("reseed_to", award.reseedTo());
                section.set("created_at", award.createdAt());
                if (!award.entrants().isEmpty()) {
                    section.set("entrants", award.entrants().stream().map(UUID::toString).toList());
                }
                if (award.newBaseline() != null) section.set("new_baseline", award.newBaseline());
                section.set("attempts", award.attempts());
            }
        });
    }

    public CompletableFuture<Void> flush() {
        return loadFuture.thenCompose(ignored -> isLoaded() ? persistSnapshot()
                : failedFuture(new IllegalStateException("Lottery state did not load safely")));
    }

    private long reseedAmount() {
        long value = plugin.getConfig().getLong("lottery.reseed-amount", DEFAULT_RESEED_AMOUNT);
        return value > 0 ? value : DEFAULT_RESEED_AMOUNT;
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> failed = new CompletableFuture<>();
        failed.completeExceptionally(error);
        return failed;
    }

    private record ParsedState(Long baseline, Set<UUID> entrants, PendingAward pendingAward) {
    }

    private record Snapshot(long baseline, List<UUID> entrants, PendingAward pendingAward) {
    }

    private record DrawContext(PendingAward award) {
    }

    private record PendingAward(String paymentId, UUID winner, long amount, Long reseedTo, long createdAt,
                                List<UUID> entrants, Long newBaseline, int attempts) {
        PendingAward {
            entrants = List.copyOf(entrants);
        }

        PendingAward withAttempts(int nextAttempts) {
            return new PendingAward(paymentId, winner, amount, reseedTo, createdAt,
                    entrants, newBaseline, nextAttempts);
        }
    }

    private enum PaymentDisposition { SETTLED, ABANDONED, PENDING, STUCK }

    private static PaymentDisposition classify(HousePayOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> PaymentDisposition.SETTLED;
            case INSUFFICIENT_FUNDS, NOT_READY, SHUTTING_DOWN, UNREPRESENTABLE_REFUNDED ->
                    PaymentDisposition.ABANDONED;
            case WRITE_FAILED_RETAINED, FAILED -> PaymentDisposition.PENDING;
            case NEEDS_RECONCILIATION -> PaymentDisposition.STUCK;
        };
    }

    public sealed interface DrawResult permits DrawResult.NotReady, DrawResult.InFlight,
            DrawResult.Refused, DrawResult.Awarded, DrawResult.PaymentAbandoned,
            DrawResult.PaymentPending, DrawResult.PaymentStuck {
        record NotReady() implements DrawResult {
        }

        record InFlight() implements DrawResult {
        }

        record Refused(LotteryMath.RefusalReason reason) implements DrawResult {
        }

        record Awarded(UUID winner, long amount, boolean capBound) implements DrawResult {
        }

        record PaymentAbandoned(UUID winner, long amount, HousePayOutcome outcome, int entrantCount)
                implements DrawResult {
        }

        record PaymentPending(String paymentId, UUID winner, long amount, HousePayOutcome outcome)
                implements DrawResult {
        }

        record PaymentStuck(String paymentId, UUID winner, long amount) implements DrawResult {
        }

        static NotReady notReady() { return new NotReady(); }

        static InFlight inFlight() { return new InFlight(); }

        static Refused refused(LotteryMath.RefusalReason reason) { return new Refused(reason); }

        static Awarded awarded(UUID winner, long amount, boolean capBound) {
            return new Awarded(winner, amount, capBound);
        }

        static PaymentAbandoned paymentAbandoned(UUID winner, long amount, HousePayOutcome outcome, int entrantCount) {
            return new PaymentAbandoned(winner, amount, outcome, entrantCount);
        }

        static PaymentPending paymentPending(String paymentId, UUID winner, long amount, HousePayOutcome outcome) {
            return new PaymentPending(paymentId, winner, amount, outcome);
        }

        static PaymentStuck paymentStuck(String paymentId, UUID winner, long amount) {
            return new PaymentStuck(paymentId, winner, amount);
        }
    }
}
