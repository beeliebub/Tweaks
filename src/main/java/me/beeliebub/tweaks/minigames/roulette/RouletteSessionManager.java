package me.beeliebub.tweaks.minigames.roulette;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.economy.BalanceMutationResult;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.discord.DiscordAnnouncer;
import me.beeliebub.tweaks.discord.SettlementLine;
import me.beeliebub.tweaks.lottery.LotteryManager;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingPaths;
import me.beeliebub.tweaks.ranks.RankManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

/**
 * Owns sticky per-player stakes, per-board rounds, the debit path, the 30-second betting window,
 * the spin animation lifecycle, and settlement. Mirrors {@code blackjack.BlackjackSessionManager}'s
 * shape (economy flow + a repeating scheduler task), but a Roulette round spans one shared board
 * rather than one player.
 *
 * <p><b>Money-path discipline:</b> {@link #handleSegmentClick} runs every rejection check — closed
 * betting, no stake, out-of-range stake, malformed bet, exposure guard — <em>before</em> touching
 * {@link EconomyManager}, and calls {@link EconomyManager#getBalance} exactly once, for the
 * clicking (therefore online) player only. {@link #settleRound} never calls {@code getBalance} or
 * {@code RankManager#getCasinoRakebackRate} for a bettor — a settled round may include offline
 * bettors, so it only ever <em>credits</em> via {@link EconomyManager#addBalance}, and rakeback is
 * snapshotted per player at bet-placement time into {@link RouletteRoundContext#rakebackRates}
 * rather than looked up again at settlement (that lookup delegates to
 * {@code EconomyManager#getRank}, which returns {@code 0} for an unloaded player).
 */
final class RouletteSessionManager {

    private static final int ROUND_BROADCAST_RADIUS = 32;
    private static final int STAKE_INDICATOR_RADIUS = 12;
    private static final long STAKE_INDICATOR_PERIOD = 20L;

    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final HouseAccount houseAccount;
    private final RankManager rankManager;
    private final RouletteBoardStore boardStore;
    private final RouletteRenderer renderer;
    private final RouletteRestPoseStore restPoseStore;
    private final LotteryManager lotteryManager;
    private final DiscordAnnouncer discordAnnouncer;
    private final RouletteDiscordGateway discordGateway;
    private final RandomGenerator rng = new SecureRandom();

    private final Map<UUID, Integer> stickyStakes = new HashMap<>();
    private final Map<RouletteBoardStore.BoardEntry, RouletteRoundContext> contexts = new HashMap<>();
    /** Same-tick double-fire guard: {@code PlayerInteractAtEntityEvent} extends
     *  {@code PlayerInteractEntityEvent} and can deliver twice for one physical click. */
    private final Map<UUID, Integer> lastBetTick = new HashMap<>();
    private long settlementGroupSequence;

    private int indicatorTaskId = -1;

    RouletteSessionManager(JavaPlugin plugin, EconomyManager economyManager, HouseAccount houseAccount,
                            RankManager rankManager, RouletteBoardStore boardStore,
                            RouletteRenderer renderer, RouletteRestPoseStore restPoseStore) {
        this(plugin, economyManager, houseAccount, rankManager, boardStore, renderer, restPoseStore,
                null, DiscordAnnouncer.NOOP);
    }

    RouletteSessionManager(JavaPlugin plugin, EconomyManager economyManager, HouseAccount houseAccount,
                            RankManager rankManager, RouletteBoardStore boardStore,
                            RouletteRenderer renderer, RouletteRestPoseStore restPoseStore,
                            LotteryManager lotteryManager) {
        this(plugin, economyManager, houseAccount, rankManager, boardStore, renderer, restPoseStore,
                lotteryManager, DiscordAnnouncer.NOOP);
    }

    RouletteSessionManager(JavaPlugin plugin, EconomyManager economyManager, HouseAccount houseAccount,
                            RankManager rankManager, RouletteBoardStore boardStore,
                            RouletteRenderer renderer, RouletteRestPoseStore restPoseStore,
                            LotteryManager lotteryManager, DiscordAnnouncer discordAnnouncer) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.houseAccount = houseAccount;
        this.rankManager = rankManager;
        this.boardStore = boardStore;
        this.renderer = renderer;
        this.restPoseStore = restPoseStore;
        this.lotteryManager = lotteryManager;
        this.discordAnnouncer = discordAnnouncer == null ? DiscordAnnouncer.NOOP : discordAnnouncer;
        this.discordGateway = new RouletteDiscordGateway(this);

        this.indicatorTaskId = Bukkit.getScheduler()
                .runTaskTimer(plugin, this::tickStakeIndicators, STAKE_INDICATOR_PERIOD, STAKE_INDICATOR_PERIOD)
                .getTaskId();
    }

    RouletteDiscordGateway discordGateway() {
        return discordGateway;
    }

    // ---- Sticky stake -----------------------------------------------------------------

    void setStake(UUID playerId, int amount) {
        stickyStakes.put(playerId, amount);
    }

    Integer stakeOf(UUID playerId) {
        return stickyStakes.get(playerId);
    }

    /** Clears the sticky stake on quit. Deliberately does NOT touch any placed bet —
     *  a round settles regardless of who is still online, and disconnecting never refunds a wager. */
    void onPlayerQuit(UUID playerId) {
        stickyStakes.remove(playerId);
        lastBetTick.remove(playerId);
    }

    // ---- Stake parsing (pure) ----------------------------------------------------------

    enum StakeParseError { NOT_A_WHOLE_NUMBER, BELOW_MINIMUM, ABOVE_CEILING }

    record StakeParse(int amount, StakeParseError error) {
        boolean ok() {
            return error == null;
        }
    }

    /** Never throws. Rejects non-numeric input, decimals, zero, negatives, and anything above
     *  {@link RouletteRound#MAX_CUMULATIVE_WAGER} (which also rejects {@code Integer.MAX_VALUE}). */
    static StakeParse parseStake(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new StakeParse(0, StakeParseError.NOT_A_WHOLE_NUMBER);
        }
        int amount;
        try {
            amount = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return new StakeParse(0, StakeParseError.NOT_A_WHOLE_NUMBER);
        }
        if (amount < 1) {
            return new StakeParse(amount, StakeParseError.BELOW_MINIMUM);
        }
        if (amount > RouletteRound.MAX_CUMULATIVE_WAGER) {
            return new StakeParse(amount, StakeParseError.ABOVE_CEILING);
        }
        return new StakeParse(amount, null);
    }

    static boolean withinBoardBounds(int stake, int minBet, int maxBet) {
        return stake >= minBet && stake <= maxBet;
    }

    /** Main-thread Discord bet operation. The UUID is deliberately accepted offline. */
    DiscordBetResult placeDiscordBet(UUID playerId, BetType type, int selector, int amount) {
        // Loading is intentionally the first operation: getBalance returns zero for an uncached
        // player, while Discord betting is allowed for linked offline accounts.
        economyManager.loadPlayer(playerId);
        Optional<RouletteBoardStore.BoardEntry> designated = boardStore.designatedActiveBoard();
        if (designated.isEmpty()) {
            return new DiscordBetResult(boardStore.designatedBoard().isPresent()
                    ? DiscordBetOutcome.BOARD_UNAVAILABLE : DiscordBetOutcome.NO_DESIGNATED_BOARD, amount, 0);
        }
        RouletteBoardStore.BoardEntry board = designated.get();
        if (!boardStore.isActive(board)) {
            return new DiscordBetResult(DiscordBetOutcome.BOARD_UNAVAILABLE, amount, 0);
        }
        RouletteRoundContext ctx = contexts.get(board);
        if (ctx != null && ctx.round.state() == RouletteRound.State.SPINNING) {
            return new DiscordBetResult(DiscordBetOutcome.BETTING_CLOSED, amount, 0);
        }
        if (ctx != null && ctx.round.state() == RouletteRound.State.SETTLED) {
            recycleSettledContext(ctx);
            ctx = null;
        }
        if (amount < 1 || amount > RouletteRound.MAX_CUMULATIVE_WAGER
                || !withinBoardBounds(amount, board.minBet(), board.maxBet())) {
            return new DiscordBetResult(DiscordBetOutcome.INVALID_BET, amount, 0);
        }

        RouletteBet bet;
        try {
            bet = new RouletteBet(playerId, type, selector, amount);
        } catch (IllegalArgumentException e) {
            return new DiscordBetResult(DiscordBetOutcome.INVALID_BET, amount, 0);
        }
        RouletteRoundContext target = ctx != null ? ctx : new RouletteRoundContext(board);
        if (!target.round.canPlace(playerId, amount)) {
            return new DiscordBetResult(DiscordBetOutcome.EXPOSURE_LIMIT, amount, bet.payoutMultiplier());
        }

        long balance = economyManager.getBalance(playerId);
        if (balance < amount) {
            return new DiscordBetResult(DiscordBetOutcome.INSUFFICIENT_FUNDS, amount, bet.payoutMultiplier());
        }
        BalanceMutationResult debit;
        try {
            debit = economyManager.removeBalance(playerId, amount);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Roulette Discord debit of $" + amount + " for "
                    + playerId + " raised after an uncertain mutation; no bet was recorded.", e);
            return new DiscordBetResult(DiscordBetOutcome.REJECTED, amount, bet.payoutMultiplier());
        }
        if (debit == BalanceMutationResult.REJECTED_UNREPRESENTABLE) {
            return new DiscordBetResult(DiscordBetOutcome.BALANCE_UNREPRESENTABLE, amount,
                    bet.payoutMultiplier());
        }
        if (!target.round.placeBet(bet)) {
            creditOrRouteToHouse(playerId, amount, "Discord place-bet refund", board);
            plugin.getLogger().severe("Roulette Discord placeBet rejected a bet canPlace had just accepted "
                    + "for " + playerId + " on board at " + board.center());
            return new DiscordBetResult(DiscordBetOutcome.REJECTED, amount, bet.payoutMultiplier());
        }
        target.rakebackRates.computeIfAbsent(playerId, rankManager::getCasinoRakebackRate);
        if (ctx == null) {
            contexts.put(board, target);
            openWindow(board, target);
        }
        return new DiscordBetResult(DiscordBetOutcome.PLACED, amount, bet.payoutMultiplier());
    }

    DiscordBoardStatus discordBoardStatus(Map<RouletteBoardStore.BoardEntry, LastResult> lastResults) {
        Optional<RouletteBoardStore.BoardEntry> designated = boardStore.designatedBoard();
        if (designated.isEmpty()) {
            return new DiscordBoardStatus(false, false, RouletteRound.State.IDLE, 0, 0, 0, null);
        }
        RouletteBoardStore.BoardEntry board = designated.get();
        boolean active = boardStore.isActive(board);
        RouletteRoundContext ctx = contexts.get(board);
        RouletteRound.State state = ctx == null ? RouletteRound.State.IDLE : ctx.round.state();
        int secondsRemaining = 0;
        if (ctx != null && state == RouletteRound.State.BETTING) {
            long remainingTicks = Math.max(0L, ctx.bettingWindowTicks
                    - (Bukkit.getCurrentTick() - ctx.windowOpenedTick));
            secondsRemaining = (int) (remainingTicks / 20L);
        }
        return new DiscordBoardStatus(true, active, state, secondsRemaining,
                board.minBet(), board.maxBet(), lastResults.get(board));
    }

    List<RouletteBet> discordBetsFor(UUID playerId) {
        Optional<RouletteBoardStore.BoardEntry> designated = boardStore.designatedActiveBoard();
        if (designated.isEmpty()) {
            return List.of();
        }
        RouletteRoundContext ctx = contexts.get(designated.get());
        if (ctx == null || ctx.round.state() == RouletteRound.State.SETTLED) {
            return List.of();
        }
        return ctx.round.bets().stream().filter(bet -> bet.player().equals(playerId)).toList();
    }

    void recordDiscordLastResult(RouletteBoardStore.BoardEntry board, int pocket, String colorName) {
        discordGateway.recordLastResult(board, pocket, colorName);
    }

    void replaceDiscordBoardIdentity(RouletteBoardStore.BoardEntry oldEntry,
                                     RouletteBoardStore.BoardEntry newEntry) {
        discordGateway.replaceBoardIdentity(oldEntry, newEntry);
    }

    /** The hardcoded default for {@link #isBigWin(long, long)}'s 2-arg form and the fallback used
     *  by {@link #bigWinMultiplier()} when {@code minigames.roulette.big-win-multiplier} is missing
     *  or non-positive. */
    private static final long DEFAULT_BIG_WIN_MULTIPLIER = 8L;

    /**
     * True if {@code payout} (already winnings only — see {@code RouletteRound.computeSettlement})
     * reaches {@link #DEFAULT_BIG_WIN_MULTIPLIER}x {@code wagered}. Pulled out as a pure predicate
     * so the threshold itself is unit-testable without MockBukkit, mirroring
     * {@link #withinBoardBounds}. {@code wagered <= 0} is never a big win (nothing was staked).
     * Production settlement uses the 3-arg overload with the live-config-driven multiplier instead
     * — see {@link #bigWinMultiplier()}.
     */
    static boolean isBigWin(long wagered, long payout) {
        return isBigWin(wagered, payout, DEFAULT_BIG_WIN_MULTIPLIER);
    }

    static boolean isBigWin(long wagered, long payout, long multiplier) {
        return wagered > 0 && payout >= wagered * multiplier;
    }

    // Live read (no constructor-time caching) so a /tconfig edit to
    // minigames.roulette.big-win-multiplier takes effect on the next settlement. A non-positive
    // multiplier would fire the celebration broadcast on every settlement, so it's rejected here
    // defensively - core/config/ConfigRegistry already enforces a minimum of 1 at the /tconfig
    // edit boundary, but a hand-edited config.yml value could still bypass it.
    private long bigWinMultiplier() {
        long multiplier = plugin.getConfig().getLong("minigames.roulette.big-win-multiplier", DEFAULT_BIG_WIN_MULTIPLIER);
        return multiplier > 0 ? multiplier : DEFAULT_BIG_WIN_MULTIPLIER;
    }

    // ---- Bet placement -------------------------------------------------------------------

    boolean hasRoundInFlight(RouletteBoardStore.BoardEntry board) {
        RouletteRoundContext ctx = contexts.get(board);
        return ctx != null && ctx.round.state() != RouletteRound.State.SETTLED;
    }

    /** True if ANY board anywhere has a round mid-{@code SPINNING}. Used to refuse finalizing a
     *  brand-new board registration while a nearby wheel is physically rotating — a scan taken
     *  mid-spin would persist a wrong wheel centre forever. */
    boolean anySpinInProgress() {
        for (RouletteRoundContext ctx : contexts.values()) {
            if (ctx.round.state() == RouletteRound.State.SPINNING) {
                return true;
            }
        }
        return false;
    }

    /**
     * Full click-to-bet flow, in order: same-tick dedupe, closed-betting check (before any economy
     * call), sticky-stake presence/bounds, bet construction, the exposure guard, the click-time
     * balance re-check, debit, ledger append, window-open, confirmation.
     */
    void handleSegmentClick(Player player, RouletteBoardStore.SegmentRef ref) {
        UUID playerId = player.getUniqueId();
        int tick = Bukkit.getCurrentTick();
        Integer lastTick = lastBetTick.get(playerId);
        if (lastTick != null && lastTick == tick) {
            return; // second delivery of the same physical click — silent, not a user error
        }

        RouletteBoardStore.BoardEntry board = ref.board();
        RouletteRoundContext ctx = contexts.get(board);
        if (ctx != null && ctx.round.state() == RouletteRound.State.SPINNING) {
            player.sendMessage(Messages.MINIGAMES.rouletteBettingClosed());
            return;
        }
        if (ctx != null && ctx.round.state() == RouletteRound.State.SETTLED) {
            // A bet during the result linger opens a fresh round instead of being rejected on a
            // board that looks idle.
            recycleSettledContext(ctx);
            ctx = null;
        }

        Integer stake = stickyStakes.get(playerId);
        if (stake == null) {
            player.sendMessage(Messages.MINIGAMES.rouletteNoStakeSet(board.minBet(), board.maxBet()));
            return;
        }
        // Bounds re-derived from ref.board() fresh on every click — never snapshotted when a round
        // opened — so a board's min/max changing between two clicks is handled for free.
        if (!withinBoardBounds(stake, board.minBet(), board.maxBet())) {
            player.sendMessage(Messages.MINIGAMES.rouletteStakeOutsideBoardRange(stake, board.minBet(), board.maxBet()));
            return;
        }

        RouletteBet bet;
        try {
            bet = new RouletteBet(playerId, ref.type(), ref.selector(), stake);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Roulette: malformed bet from a hitbox click ("
                    + ref.type() + " " + ref.selector() + ") for " + player.getName(), e);
            player.sendMessage(Messages.MINIGAMES.rouletteBetRejected());
            return;
        }

        RouletteRoundContext target = ctx != null ? ctx : new RouletteRoundContext(board);
        if (!target.round.canPlace(playerId, stake)) {
            player.sendMessage(Messages.MINIGAMES.rouletteExposureLimitReached());
            return;
        }

        // The only getBalance call in this whole class, for the clicking (therefore online) player.
        long balance = economyManager.getBalance(playerId);
        if (balance < stake) {
            player.sendMessage(Messages.MINIGAMES.rouletteInsufficientFunds(stake, balance));
            return;
        }

        boolean opening = target.round.state() == RouletteRound.State.IDLE;
        BalanceMutationResult debit;
        try {
            debit = economyManager.removeBalance(playerId, stake);
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.SEVERE, "Roulette debit of $" + stake + " for "
                    + player.getName() + " raised after an uncertain mutation; no bet was recorded."
                    + " Verify the player's balance before manual reconciliation.", error);
            player.sendMessage(Messages.MINIGAMES.rouletteBetRejected());
            return;
        }
        if (debit == BalanceMutationResult.REJECTED_UNREPRESENTABLE) {
            player.sendMessage(Messages.MINIGAMES.rouletteBalanceUnrepresentable());
            return;
        }
        if (!target.round.placeBet(bet)) {
            // Unreachable in practice — canPlace just passed with no yield point in between — but
            // never silently keep a debit that has no matching ledger entry.
            creditOrRouteToHouse(playerId, stake, "place-bet refund", board);
            plugin.getLogger().severe("Roulette: placeBet rejected a bet canPlace had just accepted "
                    + "for " + player.getName() + " on board at " + board.center());
            player.sendMessage(Messages.MINIGAMES.rouletteBetRejected());
            return;
        }

        // Snapshotted here (the clicking player is online by construction) rather than re-read at
        // settlement, where RankManager#getCasinoRakebackRate would silently return 0 for anyone
        // who has since logged off.
        target.rakebackRates.computeIfAbsent(playerId, rankManager::getCasinoRakebackRate);
        lastBetTick.put(playerId, tick);

        if (opening) {
            contexts.put(board, target);
            openWindow(board, target);
        }

        player.sendMessage(Messages.MINIGAMES.rouletteBetPlaced(
                ref.type().name(), betTargetToken(ref), stake, bet.payoutMultiplier()));
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) eventLog.log(LoggingPaths.ROULETTE_BET, () ->
                "[Roulette] " + ConsoleEventLog.actorLabel(player.getName(), playerId)
                        + " placed a $" + stake + " " + ref.type().name().toLowerCase(java.util.Locale.ROOT)
                        + " bet on " + betTargetToken(ref));
    }

    private static String betTargetToken(RouletteBoardStore.SegmentRef ref) {
        return switch (ref.type()) {
            case STRAIGHT, DOZEN -> String.valueOf(ref.selector());
            case COLOR -> ref.selector() == RouletteBet.COLOR_RED ? "RED" : "BLACK";
        };
    }

    private void recycleSettledContext(RouletteRoundContext ctx) {
        endRound(ctx.board, RouletteTeardownPolicy.EndReason.RECYCLED);
    }

    // ---- Betting window -------------------------------------------------------------------

    private void openWindow(RouletteBoardStore.BoardEntry board, RouletteRoundContext ctx) {
        // Snapshotted into the round context (not re-read at expiry/countdown) so a /tconfig edit
        // to minigames.roulette.betting-window-seconds mid-round can't desync the already-scheduled
        // expiry task from the countdown text shown to players.
        ctx.bettingWindowTicks = bettingWindowTicks();
        ctx.windowOpenedTick = Bukkit.getCurrentTick();
        ctx.windowTaskId = Bukkit.getScheduler()
                .runTaskLater(plugin, () -> onWindowExpired(board, ctx), ctx.bettingWindowTicks)
                .getTaskId();
        broadcastNear(board, Messages.MINIGAMES.rouletteWindowOpened((int) (ctx.bettingWindowTicks / 20)));
    }

    // Live read (no constructor-time caching) so a /tconfig edit takes effect on the next window
    // that opens; already-open windows keep their snapshotted length (see openWindow above).
    private long bettingWindowTicks() {
        int seconds = plugin.getConfig().getInt("minigames.roulette.betting-window-seconds", 30);
        if (seconds < 1) seconds = 30;
        return seconds * 20L;
    }

    private void onWindowExpired(RouletteBoardStore.BoardEntry board, RouletteRoundContext ctx) {
        ctx.windowTaskId = -1;
        if (contexts.get(board) != ctx || ctx.round.state() != RouletteRound.State.BETTING) {
            return; // stale — a different context now owns this board
        }
        beginSpin(ctx);
    }

    // ---- Spin ------------------------------------------------------------------------------

    /**
     * The single entry point for closing betting and spinning — reached identically by window
     * expiry, the idle sweeper ({@link #sweepIdleBoards()}), and admin force-spin
     * ({@link #forceSpin}). Draws the pocket and stores it on the round before anything else, so a
     * crash from this point on settles deterministically.
     */
    private void beginSpin(RouletteRoundContext ctx) {
        RouletteBoardStore.BoardEntry board = ctx.board;
        if (ctx.windowTaskId != -1) {
            Bukkit.getScheduler().cancelTask(ctx.windowTaskId);
            ctx.windowTaskId = -1;
        }

        int pocket = RouletteWheel.spin(rng);
        try {
            ctx.round.closeBetting(pocket);
        } catch (IllegalStateException e) {
            // Nothing has been drawn/committed at this point, so this is the one place a refund is
            // still safe and necessary — every bet already debited into this round's ledger has no
            // other path back to its owner (the round never reaches SPINNING, so settleRound is
            // never called for it). Deliberately bypasses the endRound funnel rather than forcing
            // this into RouletteTeardownPolicy's table: every caller of beginSpin only invokes it
            // when state == BETTING with no yield point in between, so this branch is unreachable
            // today — it exists purely as a structural safety net should that invariant ever be
            // relaxed. Refund-and-remove here
            // is correct either way; it just isn't routed through renderer.endRoundVisuals, so a
            // stale status hologram (if any) is left as-is rather than reset to idle.
            plugin.getLogger().log(Level.SEVERE, "Roulette: closeBetting called on a non-BETTING "
                    + "round for board at " + board.center() + " — refunding every wager in this "
                    + "round's ledger.", e);
            refundRound(ctx);
            contexts.remove(board);
            return;
        }

        // Betting is closed and the pocket is drawn/committed at this point — everything below
        // runs inside one guard so ANY failure (a broken player's broadcast, a scheduler hiccup,
        // a render failure) still settles the round rather than leaving it stuck SPINNING
        // forever. Nothing else in this codebase can rescue a round stuck in SPINNING short of a
        // full restart, since the idle sweeper only force-closes BETTING rounds.
        try {
            safeRender(() -> renderer.refreshStatus(board, Messages.MINIGAMES.rouletteSpinningStatus()),
                    board, "spin-start status refresh");
            broadcastNear(board, Messages.MINIGAMES.rouletteSpinStarted());

            // When nobody is watching, skip the animation entirely and settle immediately.
            if (!renderer.hasWatcher(board)) {
                safeSettleRound(ctx, true);
                scheduleLinger(ctx);
                return;
            }

            try {
                ctx.spinPlan = renderer.planSpin(board, boardStore.restPose(board), pocket);
                if (ctx.spinPlan.rotateWheel()) {
                    ctx.restPoseSnapshot = renderer.snapshotRestPose(ctx.spinPlan);
                    restPoseStore.save(board, ctx.restPoseSnapshot);
                }
                renderer.spinStart(board, ctx);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Roulette: spin setup failed for board at "
                        + board.center(), e);
                renderer.abortSpinVisuals(board, ctx);
                safeSettleRound(ctx, true);
                scheduleLinger(ctx);
                return;
            }

            ctx.frame = 0;
            ctx.spinTaskId = Bukkit.getScheduler()
                    .runTaskTimer(plugin, () -> tickSpin(ctx), 0L, RouletteRenderer.FRAME_PERIOD_TICKS)
                    .getTaskId();
        } catch (RuntimeException e) {
            // Idempotent even if the round was already settled/lingered by the time this fires
            // (e.g. the headless branch above settled successfully but scheduleLinger then threw)
            // — settleRound refuses and logs rather than double-crediting, and scheduleLinger just
            // (re)schedules the linger task.
            plugin.getLogger().log(Level.SEVERE, "Roulette: beginSpin failed after betting closed "
                    + "for board at " + board.center() + " (pocket " + pocket + ") — settling "
                    + "immediately rather than leaving the round stuck SPINNING.", e);
            safeSettleRound(ctx, true);
            scheduleLinger(ctx);
        }
    }

    private void tickSpin(RouletteRoundContext ctx) {
        RouletteBoardStore.BoardEntry board = ctx.board;
        if (contexts.get(board) != ctx || ctx.round.state() != RouletteRound.State.SPINNING) {
            cancelSpinTask(ctx);
            return;
        }
        int frame = ctx.frame++;
        if (frame < RouletteRenderer.TOTAL_FRAMES) {
            try {
                renderer.spinFrame(ctx, frame);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Roulette: spin frame " + frame + " failed for "
                        + "board at " + board.center() + " — ending the spin now.", e);
                finishSpin(ctx, false);
            }
            return;
        }
        finishSpin(ctx, true);
    }

    /**
     * Cancels the frame task and finalizes the animation, then <b>always</b> settles in the
     * {@code finally} block via {@link #safeSettleRound} (never throws) — the money-safety
     * guarantee of this whole animation: a bad keyframe or a render failure can never strand a
     * round holding real debited stakes, and settlement itself can never suppress this cleanup.
     */
    private void finishSpin(RouletteRoundContext ctx, boolean runFinalFrame) {
        RouletteBoardStore.BoardEntry board = ctx.board;
        cancelSpinTask(ctx);
        try {
            if (runFinalFrame) {
                boolean restored = renderer.finishRotation(ctx);
                renderer.showResult(ctx);
                if (!restored) {
                    deactivateMisalignedBoard(ctx);
                }
            } else {
                renderer.abortSpinVisuals(board, ctx);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Roulette: spin finalization failed for board at "
                    + board.center() + " — the persisted rest-pose snapshot (if any) is left in "
                    + "place for recovery at the next plugin enable.", e);
        } finally {
            safeSettleRound(ctx, true);
            scheduleLinger(ctx);
        }
    }

    private void cancelSpinTask(RouletteRoundContext ctx) {
        if (ctx.spinTaskId != -1) {
            Bukkit.getScheduler().cancelTask(ctx.spinTaskId);
            ctx.spinTaskId = -1;
        }
    }

    /**
     * The hitbox↔segment mapping is silently wrong after an incomplete restore, and that mapping
     * decides where real money goes — so this board refuses bets rather than misrouting them. It
     * stays persisted and chunk-ticketed; {@code RouletteListener} re-asserts the rest pose from
     * the (still-present) PDC snapshot and reactivates it on the next plugin enable.
     *
     * <p>Clears glow itself, from {@code ctx.spinPlan}'s own segment list, BEFORE calling
     * {@link RouletteBoardStore#clearBoardRuntime} — that call empties the board's cached
     * rest-pose list, so the linger-elapsed {@link #endRound} call that follows would otherwise
     * clear glow over zero segments and leave the win-highlight {@link RouletteRenderer#showResult}
     * just applied durably lit on a persistent {@code BlockDisplay} until the next reactivation.
     * {@code ctx.spinPlan} is guaranteed non-null here: {@link RouletteRenderer#finishRotation}
     * only returns {@code false} (the trigger for this method) when it had a plan to restore.
     */
    private void deactivateMisalignedBoard(RouletteRoundContext ctx) {
        RouletteBoardStore.BoardEntry board = ctx.board;
        plugin.getLogger().severe("Roulette: board at " + board.center() + " could not be fully "
                + "restored to its rest pose after a spin. Deactivating its hitboxes so clicks are "
                + "refused rather than misrouted — restart the server (or reload the plugin) to "
                + "recover it from the persisted rest-pose snapshot.");
        if (ctx.spinPlan != null) {
            safeRender(() -> renderer.clearAllGlow(ctx.spinPlan.allSegments()), board,
                    "misaligned-board glow clear");
        }
        RouletteHitboxManager.despawn(boardStore.clearBoardRuntime(board));
    }

    // ---- Settlement --------------------------------------------------------------------------

    /**
     * Never throws — every risky call inside is individually guarded so one failure (a bad credit,
     * a disconnected player's message) can never abort settlement for the rest of the round's
     * bettors, and can never propagate out of {@link #finishSpin}'s {@code finally} block and
     * suppress {@link #scheduleLinger}. Call this, never {@link #settleRound} directly.
     *
     * @param presentation {@code false} suppresses the trailing status/broadcast/hologram refresh —
     *                     used for {@code CHUNK_UNLOAD}/{@code SHUTDOWN}, where the players and/or
     *                     entities those calls would touch are already gone or going away.
     */
    private void safeSettleRound(RouletteRoundContext ctx, boolean presentation) {
        try {
            settleRound(ctx, presentation);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Roulette: settleRound itself threw for board at "
                    + ctx.board.center() + " — some credits in this round may not have been "
                    + "applied. Manual reconciliation against the server log above may be required.", e);
        }
    }

    /**
     * {@code round.settle()} runs first, before any money moves — it requires {@link
     * RouletteRound.State#SPINNING}, which makes double-settlement structurally impossible rather
     * than merely unlikely. Every credit is a mutator ({@link EconomyManager#addBalance}); nothing
     * here ever calls {@code getBalance} or {@code getRank} for a bettor, since a settled round may
     * include players who are no longer online. Each player's credit and message is individually
     * try/caught so one player's failure can never stop the rest of the round's bettors from being
     * paid or notified — see {@link #safeSettleRound}, the only caller.
     */
    private void settleRound(RouletteRoundContext ctx, boolean presentation) {
        RouletteBoardStore.BoardEntry board = ctx.board;
        int pocket = ctx.round.drawnPocket();
        List<RouletteBet> bets = ctx.round.bets();
        try {
            ctx.round.settle();
        } catch (IllegalStateException e) {
            plugin.getLogger().log(Level.SEVERE, "Roulette: settle() called on a non-SPINNING round "
                    + "for board at " + board.center() + " — refusing to settle twice.", e);
            return;
        }

        RouletteRound.Settlement settlement =
                RouletteRound.computeSettlement(bets, pocket, Map.copyOf(ctx.rakebackRates));

        for (Map.Entry<UUID, RouletteRound.PlayerCredit> entry : settlement.credits().entrySet()) {
                UUID playerId = entry.getKey();
                RouletteRound.PlayerCredit credit = entry.getValue();
                try {
                    if (credit.payout() > 0) {
                        creditOrRouteToHouse(playerId, credit.payout(), "payout", board);
                    }
                    if (credit.rakeback() > 0) {
                        creditOrRouteToHouse(playerId, credit.rakeback(), "rakeback", board);
                    }
            } catch (RuntimeException e) {
                // Do not assert the player was NOT paid: EconomyManager#addBalance mutates the
                // in-memory balance and schedules the disk write BEFORE its trailing tab-refresh
                // call, so an exception surfacing from addBalance (e.g. a tab-refresh hiccup) can
                // still mean the credit itself already applied — and if the payout call above
                // succeeded and only the rakeback call threw, the payout portion is unaffected
                // either way. Manually re-crediting on the assumption this failed outright risks a
                // real double-credit; verify the player's actual balance first.
                plugin.getLogger().log(Level.SEVERE, "Roulette: crediting " + playerId
                        + " (payout=" + credit.payout() + ", rakeback=" + credit.rakeback()
                        + ") for board at " + board.center() + " raised an exception — the credit "
                        + "may have partially or fully applied despite the error. Verify this "
                        + "player's balance before manually re-crediting.", e);
            }
        }

        // The house is credited exactly once per round, from this pure result — never incrementally
        // per bet as clicks arrived.
        try {
            if (settlement.houseCredit() > 0 && !houseAccount.credit(settlement.houseCredit())) {
                plugin.getLogger().warning("Roulette: house credit of " + settlement.houseCredit()
                        + " was refused (overflow) for board at " + board.center());
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Roulette: house credit of " + settlement.houseCredit()
                    + " threw for board at " + board.center(), e);
        }

        if (lotteryManager != null && lotteryManager.isLoaded()) {
            for (UUID playerId : settlement.netLosers()) {
                try {
                    lotteryManager.enter(playerId);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Roulette lottery entry failed after settlement for " + playerId, e);
                }
            }
        }

        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) eventLog.log(LoggingPaths.ROULETTE_SETTLED, () ->
                "[Roulette] (console) settled pocket " + pocket + " (" + RouletteWheel.colorOf(pocket)
                        + ") for " + bets.size() + " bet(s), house credit $" + settlement.houseCredit());

        String colorName = RouletteWheel.colorOf(pocket).name();
        int dozen = RouletteWheel.dozenOf(pocket);
        discordGateway.recordLastResult(board, pocket, colorName);
        Set<UUID> messaged = new HashSet<>();
        for (Map.Entry<UUID, RouletteRound.PlayerCredit> entry : settlement.credits().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue; // offline bettors get nothing here — no message, no balance read
            }
            try {
                long wagered = entry.getValue().wagered();
                player.sendMessage(Messages.MINIGAMES.rouletteRoundOutcome(
                        pocket, colorName, wagered, entry.getValue().payout(), entry.getValue().rakeback()));
                messaged.add(entry.getKey());
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Roulette: failed to message " + entry.getKey()
                        + " their settlement outcome for board at " + board.center()
                        + " — their credit was still applied.", e);
            }
        }

        // Discord records money that actually settled, so this deliberately ignores the
        // presentation flag used for in-world cosmetics during chunk unload and shutdown.
        if (!settlement.credits().isEmpty()) {
            long groupId = nextSettlementGroupId();
            try {
                discordAnnouncer.announceSettlement(SettlementLine.header(
                        Messages.CASINO_DISCORD.rouletteResultHeader(pocket, colorName), groupId));
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING,
                        "Roulette Discord settlement header announcement failed for board at "
                                + board.center(), throwable);
            }
            for (Map.Entry<UUID, RouletteRound.PlayerCredit> entry : settlement.credits().entrySet()) {
                try {
                    String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    RouletteRound.PlayerCredit credit = entry.getValue();
                    long net = credit.payout() + credit.rakeback() - credit.wagered();
                    String displayName = name == null ? entry.getKey().toString() : name;
                    discordAnnouncer.announceSettlement(SettlementLine.forNet(
                            Messages.CASINO_DISCORD.rouletteRound(displayName, net), net, groupId));
                    discordAnnouncer.announceRouletteOutcome(entry.getKey(),
                            Messages.CASINO_DISCORD.rouletteBettorOutcome(displayName, pocket, colorName, net));
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.WARNING,
                            "Roulette Discord settlement announcement failed for " + entry.getKey(), throwable);
                }
            }
        }
        if (presentation) {
            safeRender(() -> renderer.refreshStatus(board, Messages.MINIGAMES.rouletteResultStatus(pocket, colorName)),
                    board, "post-settlement status refresh");
            safeRender(() -> broadcastNearExcept(board, Messages.MINIGAMES.rouletteSpinResult(pocket, colorName, dozen), messaged),
                    board, "post-settlement broadcast");
            safeRender(() -> renderer.refreshHouseBalance(board), board, "post-settlement house balance refresh");
            for (Map.Entry<UUID, RouletteRound.PlayerCredit> entry : settlement.credits().entrySet()) {
                long wagered = entry.getValue().wagered();
                long payout = entry.getValue().payout();
                if (isBigWin(wagered, payout, bigWinMultiplier())) {
                    announceBigWin(entry.getKey(), payout, pocket, colorName);
                }
            }
        }
    }

    private long nextSettlementGroupId() {
        settlementGroupSequence++;
        if (settlementGroupSequence <= 0L) {
            settlementGroupSequence = 1L;
        }
        return settlementGroupSequence;
    }

    /**
     * Server-wide celebration for a round settlement reaching {@link #bigWinMultiplier()}x the
     * bettor's own wager — gated behind {@code presentation} (see {@link #settleRound}'s only
     * caller) alongside every other cosmetic post-settlement broadcast, unlike the per-bettor
     * outcome message which always sends. {@code winnings} is the same settlement payout {@code
     * rouletteRoundOutcome}'s "Won" figure shows — winnings only, the stake is never returned —
     * so the two messages always agree on the amount. Resolves the winner's name via {@link
     * Bukkit#getOfflinePlayer(UUID)} rather than requiring them online, since a round settles
     * regardless of who is still connected.
     */
    private void announceBigWin(UUID playerId, long winnings, int pocket, String colorName) {
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        Component announcement = Messages.MINIGAMES.rouletteBigWinAnnouncement(
                name != null ? name : "A player", winnings, pocket, colorName);
        try {
            Bukkit.broadcast(announcement);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Roulette: failed to broadcast a big win for "
                    + playerId, e);
        }
    }

    private void scheduleLinger(RouletteRoundContext ctx) {
        ctx.lingerTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (contexts.get(ctx.board) != ctx) {
                return; // stale — already recycled or abandoned
            }
            endRound(ctx.board, RouletteTeardownPolicy.EndReason.LINGER_ELAPSED);
        }, RouletteRenderer.RESULT_LINGER_TICKS).getTaskId();
    }

    // ---- Teardown funnel --------------------------------------------------------------------

    /**
     * The sole caller of round-level visual teardown ({@link RouletteRenderer#endRoundVisuals}) —
     * mirrors {@code BlackjackSessionManager.endSession}'s remove-and-idempotent-guard shape.
     * Reached directly by {@link RouletteListener}'s {@code deactivateBoard} (chunk unload,
     * shutdown, board removal) and by this class's own {@link #shutdown()}; reached transitively,
     * via {@link #beginSpin}'s normal completion, by window expiry, the idle sweeper
     * ({@link #sweepIdleBoards()}), and admin force-spin ({@link #forceSpin}) — force-spin is
     * deliberately routed through the same path as the window expiring, and the same reasoning
     * applies to the sweeper: both are normal round outcomes, not teardowns.
     *
     * <p>Order matters: the context is removed from {@link #contexts} FIRST — the idempotency
     * guard, making a second call for the same board a silent no-op — then every outstanding task
     * is cancelled, then exactly one money action runs, then visuals are torn down last. Removing
     * the context before cancelling {@code spinTaskId} is what makes {@link #tickSpin}'s own
     * staleness check ({@code contexts.get(board) != ctx}) safe against a frame task already
     * queued behind this call.
     */
    void endRound(RouletteBoardStore.BoardEntry board, RouletteTeardownPolicy.EndReason reason) {
        RouletteRoundContext ctx = contexts.remove(board);
        if (ctx == null) {
            return;
        }
        cancelTaskIfSet(ctx.windowTaskId);
        ctx.windowTaskId = -1;
        cancelTaskIfSet(ctx.spinTaskId);
        ctx.spinTaskId = -1;
        cancelTaskIfSet(ctx.lingerTaskId);
        ctx.lingerTaskId = -1;

        try {
            applyMoneyAction(ctx, reason);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Roulette: endRound(" + reason + ") money action "
                    + "threw for board at " + board.center() + " — visuals will still be cleaned up.", e);
        }

        safeRender(() -> renderer.endRoundVisuals(board, ctx, boardStore.restPose(board), reason),
                board, "end-round visual teardown (" + reason + ")");
        try {
            discordAnnouncer.clearRouletteBetHooks();
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING,
                    "Roulette Discord bet-hook cleanup failed for board at " + board.center(), throwable);
        }
    }

    private void applyMoneyAction(RouletteRoundContext ctx, RouletteTeardownPolicy.EndReason reason) {
        RouletteTeardownPolicy.MoneyAction action =
                RouletteTeardownPolicy.moneyActionFor(ctx.round.state(), reason);
        switch (action) {
            case NONE -> {
                if (ctx.round.state() == RouletteRound.State.BETTING) {
                    // Unreachable per RouletteTeardownPolicy's own contract (hasRoundInFlight
                    // blocks board removal while BETTING; linger/recycle only ever fire on a
                    // SETTLED context) — but never silently drop already-debited stakes if it
                    // somehow happens anyway.
                    plugin.getLogger().severe("Roulette: endRound(" + reason + ") reached a BETTING "
                            + "round for board at " + ctx.board.center() + " with no refund/settle "
                            + "action selected — this should be unreachable. Refunding defensively.");
                    refundRound(ctx);
                }
            }
            case REFUND -> refundRound(ctx);
            case SETTLE -> safeSettleRound(ctx, presentationAllowed(reason));
            case DRAW_THEN_SETTLE -> drawThenSettle(ctx, reason);
        }
    }

    /**
     * A round still {@code BETTING} when its board's chunk unloads has
     * no drawn pocket to settle from yet (refund is reserved for shutdown alone, and the round can
     * never simply be cancelled) — so one is drawn right now, exactly as {@link #beginSpin} would,
     * and the round settles headlessly (no animation, no glow, no presentation).
     */
    private void drawThenSettle(RouletteRoundContext ctx, RouletteTeardownPolicy.EndReason reason) {
        RouletteBoardStore.BoardEntry board = ctx.board;
        int pocket = RouletteWheel.spin(rng);
        try {
            ctx.round.closeBetting(pocket);
        } catch (IllegalStateException e) {
            plugin.getLogger().log(Level.SEVERE, "Roulette: drawThenSettle could not close betting "
                    + "for board at " + board.center() + " (" + reason + ") — refunding every wager "
                    + "in this round's ledger instead.", e);
            refundRound(ctx);
            return;
        }
        plugin.getLogger().warning("Roulette: board at " + board.center() + " ended (" + reason
                + ") while a round was still BETTING — drew pocket " + pocket + " and settling "
                + "headlessly.");
        safeSettleRound(ctx, false);
    }

    /** The funnel's only refund path (bets are final's sole exception) — every failed credit is
     *  individually logged so one player's failure can never abort the rest of the round's refund. */
    private void refundRound(RouletteRoundContext ctx) {
        for (RouletteBet bet : ctx.round.bets()) {
            try {
                creditOrRouteToHouse(bet.player(), bet.amount(), "shutdown refund", ctx.board);
            } catch (RuntimeException e) {
                // Same caveat as the settlement credit loop above: addBalance mutates the balance
                // before its trailing tab-refresh call can throw, so this exception does not prove
                // the refund never applied. Verify the player's balance before re-crediting by hand.
                plugin.getLogger().log(Level.SEVERE, "Roulette: refunding " + bet.amount()
                        + " to " + bet.player() + " for board at " + ctx.board.center() + " raised "
                        + "an exception — the refund may have partially or fully applied despite "
                        + "the error. Verify this player's balance before manually re-crediting.", e);
            }
        }
    }

    void creditOrRouteToHouse(UUID playerId, long amount, String purpose,
                              RouletteBoardStore.BoardEntry board) {
        try {
            if (economyManager.addBalance(playerId, amount) == BalanceMutationResult.APPLIED) return;
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.SEVERE, "Roulette " + purpose + " credit of $" + amount
                    + " for " + playerId + " on board at " + board.center()
                    + " raised after an uncertain mutation; verify the player's balance before"
                    + " manual reconciliation.", error);
            return;
        }
        try {
            if (houseAccount.credit(amount)) {
                plugin.getLogger().severe("Roulette " + purpose + " of $" + amount + " for "
                        + playerId + " on board at " + board.center()
                        + " could not be represented; the amount was routed to the House for "
                        + "manual reconciliation.");
            } else {
                plugin.getLogger().severe("Roulette " + purpose + " of $" + amount + " for "
                        + playerId + " on board at " + board.center()
                        + " could not be represented, and the House fallback overflowed; the "
                        + "amount is unrecoverable by this code path.");
            }
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.SEVERE, "Roulette " + purpose + " of $" + amount + " for "
                    + playerId + " on board at " + board.center()
                    + " could not be represented, and the House fallback failed; manual "
                    + "reconciliation is required.", error);
        }
    }

    private static boolean presentationAllowed(RouletteTeardownPolicy.EndReason reason) {
        return reason != RouletteTeardownPolicy.EndReason.CHUNK_UNLOAD
                && reason != RouletteTeardownPolicy.EndReason.SHUTDOWN;
    }

    private static void cancelTaskIfSet(int taskId) {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    /** Routes a stuck board through the exact same {@link #beginSpin} path
     *  as a window naturally expiring — never a parallel one. */
    enum ForceSpinResult { SPUN, NOTHING_TO_SPIN, ALREADY_SPINNING }

    ForceSpinResult forceSpin(RouletteBoardStore.BoardEntry board) {
        RouletteRoundContext ctx = contexts.get(board);
        if (ctx == null || ctx.round.state() == RouletteRound.State.IDLE
                || ctx.round.state() == RouletteRound.State.SETTLED) {
            return ForceSpinResult.NOTHING_TO_SPIN; // nothing to spin, or a result is still lingering
        }
        if (ctx.round.state() == RouletteRound.State.SPINNING) {
            return ForceSpinResult.ALREADY_SPINNING;
        }
        beginSpin(ctx);
        return ForceSpinResult.SPUN;
    }

    /**
     * Cancels every outstanding task and refunds any round still in {@code BETTING} — the only
     * refund path in this feature (the bets-are-final rule's sole exception, reserved specifically for a
     * server shutdown during betting). Wired into {@code Tweaks#onDisable()} via
     * {@code RouletteListener#shutdown()}.
     */
    void shutdown() {
        // Guarded on its own: this refund-any-BETTING-round guarantee is money-critical, and a
        // task-cancel failure must never skip the per-board refund loop below it.
        try {
            if (indicatorTaskId != -1) {
                Bukkit.getScheduler().cancelTask(indicatorTaskId);
                indicatorTaskId = -1;
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Roulette: failed to cancel the stake-indicator "
                    + "task during shutdown — continuing with the per-board refund sweep.", e);
        }
        for (RouletteBoardStore.BoardEntry board : new ArrayList<>(contexts.keySet())) {
            try {
                endRound(board, RouletteTeardownPolicy.EndReason.SHUTDOWN);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Roulette: endRound(SHUTDOWN) threw for board "
                        + "at " + board.center() + " — continuing with the remaining boards.", e);
            }
        }
    }

    // ---- Self-healing per-board refresh ---------------------------------------------------

    /** Extends the stake-indicator task: house-balance self-heal, betting countdown, and the
     *  action-bar sticky-stake indicator — all skipped for a board with no watcher (the
     *  animate-only-when-watched invariant applied to presentation, not just the spin animation). */
    private void tickStakeIndicators() {
        sweepIdleBoards();
        Set<RouletteBoardStore.BoardEntry> active = boardStore.activeBoards();
        if (active.isEmpty()) {
            return;
        }
        for (RouletteBoardStore.BoardEntry board : active) {
            try {
                tickStakeIndicatorsForBoard(board);
            } catch (RuntimeException e) {
                // Every other per-board step in this class is individually isolated (safeRender,
                // sweepIdleBoards' own beginSpin guard); this loop body wasn't. Without this, one
                // board throwing here (e.g. its world unloaded out from under a still-registered
                // board) would abort the whole tick, silently skipping every remaining board's
                // house-balance self-heal, countdown refresh, and stake indicator for that second.
                plugin.getLogger().log(Level.WARNING, "Roulette: stake-indicator refresh failed for "
                        + "board at " + board.center(), e);
            }
        }
    }

    private void tickStakeIndicatorsForBoard(RouletteBoardStore.BoardEntry board) {
        if (!renderer.hasWatcher(board)) {
            return;
        }
        safeRender(() -> renderer.refreshHouseBalance(board), board, "house balance self-heal");

        RouletteRoundContext ctx = contexts.get(board);
        if (ctx != null && ctx.round.state() == RouletteRound.State.BETTING) {
            long remainingTicks = Math.max(0L, ctx.bettingWindowTicks - (Bukkit.getCurrentTick() - ctx.windowOpenedTick));
            int secondsRemaining = (int) (remainingTicks / 20);
            safeRender(() -> renderer.refreshStatus(board, Messages.MINIGAMES.rouletteSpinCountdown(secondsRemaining)),
                    board, "countdown refresh");
        }

        World world = board.center().getWorld();
        for (Player player : world.getNearbyPlayers(board.center(), STAKE_INDICATOR_RADIUS)) {
            Integer stake = stickyStakes.get(player.getUniqueId());
            Component indicator = stake != null
                    ? Messages.MINIGAMES.rouletteStakeIndicator(stake, board.minBet(), board.maxBet())
                    : Messages.MINIGAMES.rouletteStakeIndicatorUnset(board.minBet(), board.maxBet());
            try {
                player.sendActionBar(indicator);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Roulette: failed to send the stake "
                        + "indicator to " + player.getName() + " near board at " + board.center(), e);
            }
        }
    }

    /**
     * A board stuck in {@code BETTING} past {@link RouletteTeardownPolicy
     * #IDLE_CEILING_TICKS} force-closes and spins — through the exact same {@link #beginSpin} path
     * a naturally-expiring window uses, never a parallel one. Iterates a defensive copy of
     * {@link #contexts}' values: {@code beginSpin}'s own {@code closeBetting} failure branch can
     * mutate {@link #contexts} mid-call, so iterating the live view here would risk a
     * {@code ConcurrentModificationException}. Deliberately NOT gated behind {@code hasWatcher}/
     * {@code activeBoards} the way the rest of this task is — the animate-only-when-watched invariant covers
     * presentation, never round scheduling, and a board with a lost window task and nobody nearby
     * is exactly the case this sweep exists to catch.
     */
    private void sweepIdleBoards() {
        long now = Bukkit.getCurrentTick();
        for (RouletteRoundContext ctx : new ArrayList<>(contexts.values())) {
            if (RouletteTeardownPolicy.shouldForceClose(ctx.round.state(), ctx.windowOpenedTick, now,
                    RouletteTeardownPolicy.IDLE_CEILING_TICKS)) {
                plugin.getLogger().warning("Roulette: board at " + ctx.board.center() + " has been "
                        + "BETTING for over " + (RouletteTeardownPolicy.IDLE_CEILING_TICKS / 20)
                        + " seconds — its window task appears lost. Force-closing and spinning now.");
                beginSpin(ctx);
            }
        }
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private void safeRender(Runnable action, RouletteBoardStore.BoardEntry board, String what) {
        try {
            action.run();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Roulette: " + what + " failed for board at "
                    + board.center(), e);
        }
    }

    private void broadcastNear(RouletteBoardStore.BoardEntry board, Component message) {
        World world = board.center().getWorld();
        for (Player player : world.getNearbyPlayers(board.center(), ROUND_BROADCAST_RADIUS)) {
            sendBroadcastMessage(board, player, message);
        }
    }

    private void broadcastNearExcept(RouletteBoardStore.BoardEntry board, Component message, Set<UUID> exclude) {
        World world = board.center().getWorld();
        for (Player player : world.getNearbyPlayers(board.center(), ROUND_BROADCAST_RADIUS)) {
            if (!exclude.contains(player.getUniqueId())) {
                sendBroadcastMessage(board, player, message);
            }
        }
    }

    /** One broken player's {@code sendMessage} must never abort the broadcast for the rest of the
     *  nearby players, nor unwind the caller (e.g. {@code beginSpin} mid-way through closing a
     *  round that already debited real stakes). */
    private void sendBroadcastMessage(RouletteBoardStore.BoardEntry board, Player player, Component message) {
        try {
            player.sendMessage(message);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Roulette: failed to broadcast to "
                    + player.getName() + " near board at " + board.center(), e);
        }
    }
}
