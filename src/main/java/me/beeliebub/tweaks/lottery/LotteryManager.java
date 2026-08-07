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
            parsedPending = new PendingAward(section.getString("payment_id"), winner, amount,
                    reseedTo, section.getLong("created_at"));
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
            baseline = value;
            return persistSnapshot().thenApply(ignored -> true);
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
                        payable.capBound() ? payable.newHouseBalance() : null, System.currentTimeMillis());
                entrants.clear();
                baseline = payable.newBaseline();
                pendingAward = award;
                context = new DrawContext(award, payable);
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
        if (payment != HousePayOutcome.SUCCESS) {
            return CompletableFuture.completedFuture(DrawResult.paymentFailed(context.award().winner(),
                    context.award().amount(), payment));
        }
        CompletableFuture<Void> reseed = CompletableFuture.completedFuture(null);
        if (context.payable().capBound()) {
            long reseedTo = context.payable().newHouseBalance();
            houseAccount.set(reseedTo);
            plugin.getLogger().info("Lottery paid a 100% draw; reseeding the House to $" + reseedTo);
            reseed = houseAccount.flush();
        }
        return reseed.thenCompose(ignored -> clearPendingAward())
                .thenApply(ignored -> DrawResult.awarded(context.award().winner(), context.award().amount(),
                        context.payable().capBound()));
    }

    private CompletableFuture<Void> replayPendingAward() {
        PendingAward award;
        synchronized (stateLock) {
            award = pendingAward;
        }
        if (award == null) return CompletableFuture.completedFuture(null);
        return economyManager.hasHousePaymentReceipt(award.winner(), award.paymentId())
                .thenCompose(receipt -> receipt
                        ? finishReplayedAward(award)
                        : housePaymentService.pay(award.paymentId(), award.winner(), award.amount())
                                .thenCompose(payment -> {
                                    if (payment != HousePayOutcome.SUCCESS) {
                                        return failedFuture(new IllegalStateException(
                                                "Lottery award replay did not settle: " + payment));
                                    }
                                    return finishReplayedAward(award);
                                }));
    }

    private CompletableFuture<Void> finishReplayedAward(PendingAward award) {
        if (award.reseedTo() != null && houseAccount.balance() < award.reseedTo()) {
            plugin.getLogger().warning("Lottery replay is reasserting House reseed to $" + award.reseedTo());
            houseAccount.set(award.reseedTo());
            return houseAccount.flush().thenCompose(ignored -> clearPendingAward());
        }
        return clearPendingAward();
    }

    private CompletableFuture<Void> clearPendingAward() {
        synchronized (stateLock) {
            pendingAward = null;
            return persistSnapshot();
        }
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

    private record DrawContext(PendingAward award, LotteryMath.PotOutcome.Payable payable) {
    }

    private record PendingAward(String paymentId, UUID winner, long amount, Long reseedTo, long createdAt) {
    }

    public sealed interface DrawResult permits DrawResult.NotReady, DrawResult.InFlight,
            DrawResult.Refused, DrawResult.Awarded, DrawResult.PaymentFailed {
        record NotReady() implements DrawResult {
        }

        record InFlight() implements DrawResult {
        }

        record Refused(LotteryMath.RefusalReason reason) implements DrawResult {
        }

        record Awarded(UUID winner, long amount, boolean capBound) implements DrawResult {
        }

        record PaymentFailed(UUID winner, long amount, HousePayOutcome outcome) implements DrawResult {
        }

        static NotReady notReady() { return new NotReady(); }

        static InFlight inFlight() { return new InFlight(); }

        static Refused refused(LotteryMath.RefusalReason reason) { return new Refused(reason); }

        static Awarded awarded(UUID winner, long amount, boolean capBound) {
            return new Awarded(winner, amount, capBound);
        }

        static PaymentFailed paymentFailed(UUID winner, long amount, HousePayOutcome outcome) {
            return new PaymentFailed(winner, amount, outcome);
        }
    }
}
