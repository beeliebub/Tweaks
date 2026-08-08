package me.beeliebub.tweaks.minigames.blackjack;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.economy.BalanceMutationResult;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.lottery.LotteryManager;
import me.beeliebub.tweaks.ranks.RankManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns {@link BlackjackGame} session lifecycle (start/hit/stand/settle), the economy flow
 * (bet deduct/refund/payout/rakeback), and the inactivity sweeper. Every exit path — early
 * clear, auto-clear, quit, chunk unload, shutdown, and the inactivity sweep — funnels through
 * the single {@link #endSession} teardown method, which is the sole caller of
 * {@link BlackjackRenderer#removeDisplays}. This makes "no orphaned display/mannequin entity
 * survives a session" a structural guarantee rather than a call-site convention.
 */
public final class BlackjackSessionManager {

    /** How often the inactivity sweeper runs, in ticks (1200 = 60 seconds). */
    private static final long SWEEP_PERIOD_TICKS = 1200L;

    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final HouseAccount houseAccount;
    private final RankManager rankManager;
    private final BlackjackRenderer renderer;
    private final LotteryManager lotteryManager;

    /** Per-player active game session. At most one game per player at a time. */
    private final Map<UUID, Session> sessions = new HashMap<>();

    /**
     * Task id of the repeating inactivity-sweep scheduler, or {@code -1} when not yet
     * scheduled (e.g. during unit tests that skip the constructor scheduling path).
     */
    private int sweepTaskId = -1;

    public BlackjackSessionManager(JavaPlugin plugin, EconomyManager economyManager, HouseAccount houseAccount,
                            RankManager rankManager, BlackjackRenderer renderer) {
        this(plugin, economyManager, houseAccount, rankManager, renderer, null);
    }

    public BlackjackSessionManager(JavaPlugin plugin, EconomyManager economyManager, HouseAccount houseAccount,
                            RankManager rankManager, BlackjackRenderer renderer, LotteryManager lotteryManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.houseAccount = houseAccount;
        this.rankManager = rankManager;
        this.renderer = renderer;
        this.lotteryManager = lotteryManager;

        // Schedule the inactivity sweeper to run every 60 seconds (1200 ticks).
        sweepTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin,
                        () -> sweepInactiveSessions(System.currentTimeMillis()),
                        SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS)
                .getTaskId();
    }

    // ---- Public accessors -----------------------------------------------------

    /** True if the player already has a game running. */
    public boolean hasActiveGame(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /**
     * Returns the {@link BlackjackGame} for the given player if a session is active,
     * or {@code null} when no session exists. Exposed for test clock-injection via
     * {@link BlackjackGame#touchInteraction(long)} so the inactivity sweeper can be
     * exercised deterministically.
     */
    public BlackjackGame getActiveGame(UUID playerId) {
        Session s = sessions.get(playerId);
        return s != null ? s.game : null;
    }

    /**
     * Register a minimal session containing {@code game} for {@code playerId} without
     * spawning any card-display entities. Intended exclusively for unit tests so the
     * inactivity sweeper can be exercised without a running game server.
     *
     * <p>The session's {@code world} is derived from {@code center.getWorld()} and its display-ID
     * list is left empty (no entities are spawned), which is safe for the sweeper path (it only
     * calls {@link BlackjackRenderer#removeDisplays} and cancels the auto-clear task, neither of
     * which requires a non-empty display list).
     *
     * <p>Do NOT call this from production code paths.
     *
     * @param playerId the player UUID whose session to register
     * @param game     a pre-constructed game whose {@code lastInteractionTime} can be
     *                 manipulated via {@link BlackjackGame#touchInteraction(long)}
     * @param center   table centre location used to anchor the session's chunk key
     */
    public void registerGameForTesting(UUID playerId, BlackjackGame game, Location center) {
        Chunk anchorChunk = center.getChunk();
        // Testing: use SOUTH as the default facing; inactivity sweeper doesn't need geometry.
        Session session = new Session(game, center.getWorld(), anchorChunk.getChunkKey(), center,
                BlockFace.SOUTH, null);
        sessions.put(playerId, session);
    }

    // ---- Gameplay entry points --------------------------------------------------

    /**
     * Handle a MIDDLE-button press for {@code player} at {@code table}: clears a
     * finished board immediately, starts a new game (deducting the bet first, refunding
     * it if the game fails to start), or informs the player their game is still in progress.
     */
    void handleMiddleClick(Player player, BlackjackTableStore.TableEntry table) {
        UUID playerId = player.getUniqueId();
        Session session = sessions.get(playerId);
        Location tableCenter = table.center();

        if (session != null && session.waitingToClear) {
            endSession(playerId);
            return;
        }
        if (session != null) {
            player.sendMessage(Messages.MINIGAMES.blackjackGameInProgress());
            return;
        }

        int bet = table.bet();
        if (bet > 0) {
            double balance = economyManager.getBalance(playerId);
            if (balance < bet) {
                player.sendMessage(Messages.MINIGAMES.blackjackCannotAfford(bet, (long) balance));
                return;
            }
            BalanceMutationResult debit;
            try {
                debit = economyManager.removeBalance(playerId, bet);
            } catch (RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Blackjack bet debit for " + player.getName() + " ($" + bet
                                + ") raised after an uncertain mutation; the game was not started."
                                + " Verify the player's balance before manual reconciliation.", error);
                player.sendMessage(Messages.MINIGAMES.blackjackStartFailed(false));
                return;
            }
            if (debit == BalanceMutationResult.REJECTED_UNREPRESENTABLE) {
                player.sendMessage(Messages.MINIGAMES.blackjackBalanceUnrepresentable());
                return;
            }
        }
        boolean started = startGame(player, bet, tableCenter, table.facing(), table.backColor());
        if (!started) {
            if (bet > 0) {
                creditOrRouteToHouse(playerId, player.getName(), bet, "failed-game refund");
            }
            player.sendMessage(Messages.MINIGAMES.blackjackStartFailed(bet > 0));
        }
    }

    /**
     * Start a new table game for {@code player} with {@code bet}, rendered on the surface
     * centred at {@code tableCenter}. The bet must already have been deducted by the caller.
     * Returns {@code true} on success; on failure returns {@code false} and the caller MUST
     * refund the bet.
     *
     * @param facing    the direction the player-side buttons face (used for card orientation)
     * @param backColor optional RGB integer for the card-back tint; {@code null} for pack default
     */
    private boolean startGame(Player player, int bet, Location tableCenter,
                              BlockFace facing, Integer backColor) {
        World world = player.getWorld();
        if (world == null) {
            return false;
        }

        // Everything from game construction through the initial render is guarded as one unit:
        // a failure anywhere in here must leave no session registered and no entity spawned, so
        // the caller's "false ⇒ refund the bet" contract holds regardless of which step fails.
        Session session = null;
        try {
            BlackjackGame game = new BlackjackGame(player.getUniqueId(), bet);
            game.dealInitial();

            Chunk anchorChunk = tableCenter.getChunk();
            session = new Session(game, world, anchorChunk.getChunkKey(), tableCenter,
                    facing, backColor);

            // Spawn the dealer mannequin at game start so it is present for the whole round.
            Location dealerLoc = BlackjackRenderer.dealerLocation(tableCenter, facing);
            session.dealerMannequinId = renderer.spawnDealerMannequin(world, dealerLoc);

            renderer.renderOnTable(session, tableCenter);
        } catch (RuntimeException ex) {
            if (session != null) {
                renderer.removeDisplays(session);
            }
            plugin.getLogger().warning(
                    "Blackjack: failed to start a game for " + player.getName() + ": " + ex.getMessage());
            return false;
        }

        sessions.put(player.getUniqueId(), session);

        if (session.game.isFinished()) {
            finish(player, session);
        } else {
            player.sendMessage(Messages.MINIGAMES.blackjackHandStarted(
                    session.game.playerValue(), session.game.dealerHand().getFirst().rank().key()));
            player.sendMessage(Messages.MINIGAMES.blackjackControls());
        }
        return true;
    }

    /** Handle a LEFT-button (hit) press for {@code player}, if a game is active and not finished. */
    void hit(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.game.isFinished()) {
            return;
        }
        session.game.playerHit();
        afterAction(player, session, Messages.MINIGAMES.blackjackHit(session.game.playerValue()));
    }

    /** Handle a RIGHT-button (stand) press for {@code player}, if a game is active and not finished. */
    void stand(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.game.isFinished()) {
            return;
        }
        session.game.playerStand();
        afterAction(player, session, Messages.MINIGAMES.blackjackStand());
    }

    private void afterAction(Player player, Session session, Component message) {
        player.sendMessage(message);
        if (session.game.isFinished()) {
            finish(player, session);
        } else {
            renderer.redraw(session);
        }
    }

    // ---- Settlement -------------------------------------------------------------

    /**
     * Settle the game, credit the payout/rakeback, display the outcome message, and schedule
     * an auto-clear of card displays after {@link #autoClearTicks()} ticks.
     * The player can cancel the timer early by pressing the MIDDLE button.
     */
    private void finish(Player player, Session session) {
        BlackjackGame game = session.game;

        // Rakeback rate is only meaningful on a non-free dealer win, so avoid the rank
        // lookup entirely otherwise.
        double rakebackRate = (game.bet() > 0 && game.result() == BlackjackGame.Result.DEALER_WIN)
                ? rankManager.getCasinoRakebackRate(player.getUniqueId())
                : 0.0;
        Settlement settlement = computeSettlement(game.result(), game.bet(), game.payout(), rakebackRate);

        if (settlement.payoutAmount() > 0) {
            creditOrRouteToHouse(player.getUniqueId(), player.getName(), settlement.payoutAmount(), "payout");
        }
        if (settlement.rakebackAmount() > 0) {
            creditOrRouteToHouse(player.getUniqueId(), player.getName(), settlement.rakebackAmount(), "rakeback");
        }
        if (settlement.houseWinnings() > 0) {
            houseAccount.credit(settlement.houseWinnings());
            enterLottery(player.getUniqueId(), settlement.houseWinnings(), "blackjack settlement");
        }

        player.sendMessage(settlement.summaryMessage());
        player.sendMessage(Messages.MINIGAMES.blackjackFinalValues(game.playerValue(), game.dealerValue()));
        player.sendMessage(Messages.MINIGAMES.blackjackClearBoard());

        try {
            // Re-render so dealer's hole card is revealed.
            renderer.redraw(session);
            // Resolve the dealer mannequin reaction against the already-spawned mannequin.
            renderer.resolveDealerMannequin(session, game.result());
        } catch (RuntimeException ex) {
            // Economy is already settled above; a render/mannequin failure here must not
            // prevent the auto-clear below from being scheduled, or the table would be stuck
            // forever (waitingToClear would never be set, exempting the session from both the
            // inactivity sweep and the MIDDLE-click early-clear path).
            plugin.getLogger().warning(
                    "Blackjack: post-settlement render/mannequin update failed for "
                            + player.getName() + ": " + ex.getMessage());
        }

        // Schedule auto-clear; store the task id so it can be cancelled early.
        session.waitingToClear = true;
        UUID playerId = player.getUniqueId();
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Guard: session may have been replaced or removed by the time this fires.
            Session current = sessions.get(playerId);
            if (current == session && session.waitingToClear) {
                endSession(playerId);
            }
        }, autoClearTicks()).getTaskId();
        session.autoClearTaskId = taskId;
    }

    // Live read (no constructor-time caching) so a /tconfig edit to
    // minigames.blackjack.auto-clear-seconds takes effect on the next hand that settles.
    private long autoClearTicks() {
        int seconds = plugin.getConfig().getInt("minigames.blackjack.auto-clear-seconds", 30);
        if (seconds < 1) seconds = 30;
        return seconds * 20L;
    }

    /** Pure result of settling a hand: amounts to credit and the player-facing summary. */
    record Settlement(int payoutAmount, int rakebackAmount, int houseWinnings, Component summaryMessage) {}

    /**
     * Pure settlement computation — no Bukkit types, no side effects — so the payout/rakeback
     * math and message wording can be unit-tested directly without MockBukkit's entity/scheduler
     * limitations (previously an untested gap; see {@code BlackjackRakebackTest}).
     *
     * @param result       the settled game's outcome
     * @param bet          the bet amount ({@code 0} for a free/practice table)
     * @param payout       {@link BlackjackGame#payout()} for the settled game
     * @param rakebackRate the player's rakeback rate; ignored unless {@code bet > 0} and
     *                     {@code result == DEALER_WIN}
     */
    static Settlement computeSettlement(BlackjackGame.Result result, int bet, int payout, double rakebackRate) {
        boolean freeTurn = bet == 0;
        int payoutAmount = freeTurn ? 0 : payout;

        int rakebackAmount = 0;
        if (!freeTurn && result == BlackjackGame.Result.DEALER_WIN && rakebackRate > 0.0) {
            rakebackAmount = (int) Math.floor(bet * rakebackRate);
        }

        int houseWinnings = Math.max(0, bet - payoutAmount);
        return new Settlement(payoutAmount, rakebackAmount, houseWinnings,
                Messages.MINIGAMES.blackjackSettlementSummary(result.name(), bet, payoutAmount, rakebackAmount));
    }

    // ---- Teardown ----------------------------------------------------------------

    /**
     * The single teardown funnel for every session exit path. Cancels any scheduled
     * auto-clear task, removes every tracked display/mannequin entity via
     * {@link BlackjackRenderer#removeDisplays}, and removes the session from the map.
     * Idempotent — a no-op if no session is registered for {@code playerId}.
     */
    void endSession(UUID playerId) {
        Session session = sessions.remove(playerId);
        if (session == null) {
            return;
        }
        cancelAutoClear(session);
        renderer.removeDisplays(session);
    }

    /** End every session anchored in the given chunk (identified by chunk key + world). */
    void evictSessionsInChunk(long chunkKey, World world) {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Session session = sessions.get(id);
            if (session != null && session.chunkKey == chunkKey && session.world.equals(world)) {
                endSession(id);
            }
        }
    }

    /** End {@code playerId}'s session, if any, on disconnect. */
    void onPlayerQuit(UUID playerId) {
        endSession(playerId);
    }

    /**
     * Forcibly end every mid-hand session that has been idle for longer than
     * {@link #inactivityTimeoutMillis()}. The player loses their already-deducted bet
     * — no payout is credited.
     *
     * <p>Only in-progress (not yet finished / not {@code waitingToClear}) sessions are
     * swept. Sessions that are already in the 30-second auto-clear window are left alone
     * because their economy outcome is already settled.
     *
     * <p>Iterates over a snapshot of the session keys to avoid
     * {@link java.util.ConcurrentModificationException} while removing entries.
     *
     * @param now the current time in milliseconds (injected so tests can use a synthetic clock)
     */
    public void sweepInactiveSessions(long now) {
        long timeoutMillis = inactivityTimeoutMillis();
        for (UUID playerId : new ArrayList<>(sessions.keySet())) {
            try {
                Session session = sessions.get(playerId);
                if (session == null) continue;
                if (session.game.isFinished() || session.waitingToClear) continue;
                if (now - session.game.lastInteractionTime() > timeoutMillis) {
                    int bet = session.game.bet();
                    endSession(playerId);
                    if (bet > 0) {
                        houseAccount.credit(bet);
                        enterLottery(playerId, bet, "blackjack inactivity forfeiture");
                    }

                    var player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        player.sendMessage(Messages.MINIGAMES.blackjackInactiveGameEnded(bet));
                    }
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Blackjack inactivity sweep failed for " + playerId, e);
            }
        }
    }

    private void enterLottery(UUID playerId, long amount, String source) {
        if (lotteryManager == null || !lotteryManager.isLoaded()) return;
        try {
            lotteryManager.enter(playerId);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Blackjack lottery entry failed after " + source + " for " + playerId, e);
        }
    }

    void creditOrRouteToHouse(UUID playerId, String playerIdentity, long amount, String purpose) {
        try {
            if (economyManager.addBalance(playerId, amount) == BalanceMutationResult.APPLIED) return;
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Blackjack " + purpose + " credit of $" + amount + " for " + playerIdentity
                            + " (" + playerId + ") raised after an uncertain mutation; verify the"
                            + " player's balance before manual reconciliation.", error);
            return;
        }
        try {
            if (houseAccount.credit(amount)) {
                plugin.getLogger().severe("Blackjack " + purpose + " of $" + amount + " for "
                        + playerIdentity + " (" + playerId + ") could not be represented; "
                        + "the amount was routed to the House for manual reconciliation.");
            } else {
                plugin.getLogger().severe("Blackjack " + purpose + " of $" + amount + " for "
                        + playerIdentity + " (" + playerId + ") could not be represented, and the "
                        + "House fallback overflowed; the amount is unrecoverable by this code path.");
            }
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Blackjack " + purpose + " of $" + amount + " for " + playerIdentity + " ("
                            + playerId + ") could not be represented, and the House fallback failed; "
                            + "manual reconciliation is required.", error);
        }
    }

    /**
     * Live read (no constructor-time caching) so a /tconfig edit to
     * minigames.blackjack.inactivity-timeout-minutes takes effect on the next sweep. Floored at
     * 1 minute — core/config/ConfigRegistry already enforces this at the /tconfig edit boundary,
     * but a hand-edited config.yml value could still bypass it. Public (like the field it replaces)
     * so {@code tests.minigames.blackjack.BlackjackInactivityTimeoutTest} can snapshot the same
     * value the sweeper uses instead of duplicating the read.
     *
     * <p>Lowering this while sessions are in progress evicts any session already idle past the
     * new threshold on the very next sweep, forfeiting that player's already-deducted bet.
     */
    public long inactivityTimeoutMillis() {
        int minutes = plugin.getConfig().getInt("minigames.blackjack.inactivity-timeout-minutes", 10);
        if (minutes < 1) minutes = 10;
        return minutes * 60L * 1000L;
    }

    /** Called from {@link BlackjackListener#shutdown()} to guarantee no session outlives a stop. */
    public void shutdown() {
        if (sweepTaskId != -1) {
            Bukkit.getScheduler().cancelTask(sweepTaskId);
            sweepTaskId = -1;
        }
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            endSession(id);
        }
    }

    /** Cancel the session's auto-clear task if one is scheduled. */
    private void cancelAutoClear(Session session) {
        if (session.autoClearTaskId != -1) {
            Bukkit.getScheduler().cancelTask(session.autoClearTaskId);
            session.autoClearTaskId = -1;
        }
    }
}
