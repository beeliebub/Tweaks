package me.beeliebub.tweaks.tests.minigames.blackjack;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackGame;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener;
import me.beeliebub.tweaks.ranks.RankManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Inactivity-timeout tests for the Blackjack mid-hand idle eviction (Tweaks-edvw).
 *
 * <p>Rule: a mid-hand game that has been idle for longer than
 * {@link BlackjackListener#INACTIVITY_TIMEOUT_MS} (10 minutes) is forcibly ended by
 * {@link BlackjackListener#sweepInactiveSessions(long)}. The already-deducted bet is
 * forfeited — no payout is credited.
 *
 * <p>The tests exercise three scenarios:
 * <ol>
 *   <li><b>Positive</b> — sweep at 11 min after last interaction removes the session
 *       and leaves the balance unchanged (no payout credited).</li>
 *   <li><b>Negative</b> — sweep at 1 min after last interaction leaves the session
 *       active ({@link BlackjackListener#hasActiveGame} returns {@code true}).</li>
 *   <li><b>Edge</b> — a session whose game is already finished is skipped by the
 *       sweeper even when its timestamp is stale.</li>
 * </ol>
 *
 * <p>Because {@code renderOnTable} spawns {@link org.bukkit.entity.ItemDisplay} entities
 * which MockBukkit does not support, {@code startGame} cannot be called directly. Instead
 * the tests use {@link BlackjackListener#registerGameForTesting} to inject a minimal
 * session and verify the sweeper against that session's public interface. The simulated
 * bet deduction is performed via {@link EconomyManager#removeBalance} to accurately
 * reflect the production flow (bet deducted before any game logic runs).
 *
 * <p>Clock injection is performed via {@link BlackjackGame#touchInteraction(long)} so
 * no real wall-clock delay is needed.
 *
 * <p>A fresh {@link BlackjackListener} is constructed per test (same pattern used in
 * {@code BlackjackTableGeometryTest}) so each test runs against an isolated session map.
 */
class BlackjackInactivityTimeoutTest {

    /** Fixed bet amount used across all tests, pre-deducted from the player's balance. */
    private static final int BET = 100;

    private ServerMock server;
    private Tweaks plugin;
    private EconomyManager economyManager;

    /** Table centre in a real MockBukkit world (needed for chunk operations). */
    private Location tableCenter;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        economyManager = plugin.getEconomyManager();

        World world = server.addSimpleWorld("casino");
        tableCenter = new Location(world, 0.5, 65.0, 0.5);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ---- helpers ------------------------------------------------------------

    /** Build a fresh, isolated {@link BlackjackListener} (no cross-test state). */
    private BlackjackListener freshListener() {
        RankManager rm = plugin.getRankManager();
        return new BlackjackListener(plugin, economyManager, rm);
    }

    /**
     * Create a mid-hand (not yet settled) {@link BlackjackGame} for {@code playerId},
     * deduct the bet from the player's balance, and register the session via
     * {@link BlackjackListener#registerGameForTesting}.
     *
     * <p>Because the deck is shuffled randomly, the initial deal may produce a natural
     * Blackjack and settle the game immediately. When that happens this helper retries
     * with a new {@link BlackjackGame} instance (same player, same bet). The probability
     * of needing more than a handful of retries is negligible (natural rate ≈ 4.8% per
     * deal), so a hard cap of 20 attempts is more than sufficient.
     *
     * @return the in-progress game, never {@code null} (the cap is large enough)
     * @throws AssertionError if 20 consecutive deals all produce naturals (pathological)
     */
    private BlackjackGame registerMidHandGame(BlackjackListener listener, UUID playerId) {
        // Deduct the bet once (simulating the production flow).
        economyManager.setBalance(playerId, (double) BET);
        economyManager.removeBalance(playerId, BET);

        for (int i = 0; i < 20; i++) {
            BlackjackGame game = new BlackjackGame(playerId, BET);
            game.dealInitial();
            if (!game.isFinished()) {
                listener.registerGameForTesting(playerId, game, tableCenter);
                return game;
            }
            // Natural: try another deal.
        }
        throw new AssertionError("20 consecutive natural deals — RNG seeding anomaly?");
    }

    // ---- positive: session swept after 10 min idle --------------------------

    /**
     * A game idle for longer than {@link BlackjackListener#INACTIVITY_TIMEOUT_MS}
     * must be removed by {@link BlackjackListener#sweepInactiveSessions(long)}, and
     * the player's balance must remain at its post-bet value (no payout credited).
     */
    @Test
    void sweepRemovesGameIdleOverThreshold() {
        BlackjackListener listener = freshListener();
        PlayerMock player = server.addPlayer();
        UUID playerId = player.getUniqueId();

        BlackjackGame game = registerMidHandGame(listener, playerId);

        assertTrue(listener.hasActiveGame(playerId),
                "Session must be active after registration");

        // Record the balance with the bet already deducted.
        double balanceAfterBet = economyManager.getBalance(playerId);

        // Back-date: game has been idle for TIMEOUT + 1 second.
        long simulatedNow = System.currentTimeMillis();
        long idleStart = simulatedNow - BlackjackListener.INACTIVITY_TIMEOUT_MS - 1_000L;
        game.touchInteraction(idleStart);

        // sweepInactiveSessions: now - idleStart = TIMEOUT + 1000 ms > TIMEOUT → evict.
        listener.sweepInactiveSessions(simulatedNow);

        assertFalse(listener.hasActiveGame(playerId),
                "Session must be removed after sweeping an over-threshold idle game");

        // No payout must be credited (bet was already deducted; forfeited on eviction).
        assertEquals(balanceAfterBet, economyManager.getBalance(playerId), 1e-9,
                "Balance must be unchanged (no payout) when a game is ended for inactivity");
    }

    // ---- negative: session survives when idle for less than threshold --------

    /**
     * A game idle for only 1 minute (well under the 10-minute threshold) must NOT be
     * evicted by {@link BlackjackListener#sweepInactiveSessions(long)}.
     */
    @Test
    void sweepDoesNotRemoveGameBelowThreshold() {
        BlackjackListener listener = freshListener();
        PlayerMock player = server.addPlayer();
        UUID playerId = player.getUniqueId();

        BlackjackGame game = registerMidHandGame(listener, playerId);

        assertTrue(listener.hasActiveGame(playerId),
                "Session must be active after registration");

        // Touch the timestamp to 1 minute ago — well under the 10-minute threshold.
        long simulatedNow = System.currentTimeMillis();
        game.touchInteraction(simulatedNow - 60_000L);

        // Sweep: 60 000 ms idle < INACTIVITY_TIMEOUT_MS → must NOT evict.
        listener.sweepInactiveSessions(simulatedNow);

        assertTrue(listener.hasActiveGame(playerId),
                "Session must survive the sweep when idle time is below the 10-minute threshold");
    }

    // ---- edge: finished game is not touched by the sweeper ------------------

    /**
     * A session whose {@link BlackjackGame#isFinished()} is {@code true} must be skipped
     * by the sweeper regardless of idle time. This proves the sweeper does not interfere
     * with the existing 30-second auto-clear path.
     */
    @Test
    void sweepIgnoresAlreadyFinishedGame() {
        BlackjackListener listener = freshListener();
        PlayerMock player = server.addPlayer();
        UUID playerId = player.getUniqueId();

        // Get an in-progress game and register it.
        BlackjackGame game = registerMidHandGame(listener, playerId);
        assertTrue(listener.hasActiveGame(playerId),
                "Session must be active after registration");

        // Settle the game manually (mirrors what finish() would do after playerStand).
        game.playerStand();
        assertTrue(game.isFinished(),
                "Game must be finished after playerStand");

        // Back-date to simulate > 10 minutes of idle.
        long now = System.currentTimeMillis();
        game.touchInteraction(now - BlackjackListener.INACTIVITY_TIMEOUT_MS - 1_000L);

        // Sweep: game.isFinished() == true → sweeper must skip this session.
        listener.sweepInactiveSessions(now);

        assertTrue(listener.hasActiveGame(playerId),
                "Sweeper must not remove a session whose game.isFinished() is true");
    }
}
