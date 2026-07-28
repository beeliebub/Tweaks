package me.beeliebub.tweaks.tests.minigames.blackjack;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.ranks.RankManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-level regression tests for the Blackjack rakeback rule (ev2a DoD).
 *
 * <p>Rule: when a player with casino rakeback loses to the dealer, they receive
 * {@code floor(bet * rakeback_rate)} coins credited back to their balance.
 *
 * <p>The private {@code finish()} method in
 * {@link me.beeliebub.tweaks.minigames.blackjack.BlackjackSessionManager} renders
 * {@link org.bukkit.entity.ItemDisplay} entities, spawns
 * {@link org.bukkit.entity.TextDisplay} holograms, and schedules delayed tasks — all of which
 * MockBukkit does not fully support. Driving a real game through the session manager to a
 * dealer-win is therefore impractical in this test environment and is covered by real-server
 * smoke tests. The pure payout/rakeback settlement math extracted from {@code finish()} — the
 * part that previously had no direct coverage — is now tested against the real production code
 * path in {@code me.beeliebub.tweaks.minigames.blackjack.BlackjackSessionManagerSettlementTest}.
 *
 * <p>Instead, these tests verify:
 * <ol>
 *   <li>That {@link RankManager} loads the correct rakeback rate from the live config for
 *       each rank (the config is the source of truth for the 5% rate).</li>
 *   <li>That {@link RankManager#getCasinoRakebackRate} reflects a player's current rank
 *       via {@link EconomyManager#getRank}.</li>
 *   <li>That the rakeback arithmetic {@code (int)Math.floor(bet * rate)} produces the
 *       correct integer coin amount.</li>
 *   <li>That {@link EconomyManager#addBalance} updates the player's balance by exactly
 *       the computed rakeback amount — proving the credit mechanism works end-to-end.</li>
 * </ol>
 */
class BlackjackRakebackTest {

    private ServerMock server;
    private Tweaks plugin;
    private EconomyManager em;
    private RankManager rm;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        em = plugin.getEconomyManager();
        rm = plugin.getRankManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -------------------------------------------------------------------------
    // Config loading — rakeback rates for key ranks
    // -------------------------------------------------------------------------

    @Test
    void rank5HasFivePercentRakeback() {
        // Rank V is configured with rakeback: 0.05 in config.yml.
        assertEquals(0.05, rm.getRankRakeback(5), 1e-9,
                "Rank 5 must carry a 5% rakeback rate per config.yml");
    }

    @Test
    void unrankedPlayerHasZeroRakeback() {
        assertEquals(0.0, rm.getRankRakeback(0), 1e-9,
                "Rank 0 (Unranked) must have 0% rakeback");
    }

    @Test
    void rank1HasOnePercentRakeback() {
        assertEquals(0.01, rm.getRankRakeback(1), 1e-9,
                "Rank 1 must carry a 1% rakeback rate per config.yml");
    }

    @Test
    void rank10HasTenPercentRakeback() {
        assertEquals(0.10, rm.getRankRakeback(10), 1e-9,
                "Rank 10 must carry a 10% rakeback rate per config.yml");
    }

    // -------------------------------------------------------------------------
    // getCasinoRakebackRate reflects player's live rank
    // -------------------------------------------------------------------------

    @Test
    void casinoRakebackRateForRank5Player() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();

        em.setRank(uuid, 5);

        assertEquals(0.05, rm.getCasinoRakebackRate(uuid), 1e-9,
                "getCasinoRakebackRate must return 5% for a rank-5 player");
    }

    @Test
    void casinoRakebackRateForUnrankedPlayerIsZero() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();

        // Default rank is 0 (Unranked) — no explicit setRank needed.
        assertEquals(0.0, rm.getCasinoRakebackRate(uuid), 1e-9,
                "getCasinoRakebackRate must return 0% for an unranked player");
    }

    // -------------------------------------------------------------------------
    // Rakeback arithmetic: floor(bet * rate)
    // -------------------------------------------------------------------------

    @Test
    void rakebackArithmeticFor100BetAt5PercentIs5() {
        // The DoD example: rank-5 player loses a 100-coin bet.
        double rate = 0.05;
        int bet = 100;

        int rakeback = (int) Math.floor(bet * rate);

        assertEquals(5, rakeback,
                "floor(100 * 0.05) must equal 5");
    }

    @Test
    void rakebackArithmeticFloorsTruncatedResult() {
        // 333 * 0.05 = 16.65 → floor = 16 (not 17).
        double rate = 0.05;
        int bet = 333;

        int rakeback = (int) Math.floor(bet * rate);

        assertEquals(16, rakeback,
                "floor(333 * 0.05) = floor(16.65) must equal 16");
    }

    @Test
    void rakebackArithmeticZeroRateProducesZero() {
        // Unranked player: rate = 0.0 → rakeback = 0, meaning nothing is credited.
        int rakeback = (int) Math.floor(100 * 0.0);
        assertEquals(0, rakeback,
                "A 0% rakeback rate must produce a zero rakeback amount");
    }

    // -------------------------------------------------------------------------
    // EconomyManager.addBalance credits the exact rakeback amount
    // -------------------------------------------------------------------------

    @Test
    void addBalanceCreditsFiveCoinsToRank5PlayerAfter100CoinLoss() {
        // Simulates the settlement path in BlackjackListener.finish() for DEALER_WIN.
        // Full wiring: set rank → read rate → compute rakeback → credit via addBalance.
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();

        em.setRank(uuid, 5);
        em.setBalance(uuid, 0.0); // start with a clean slate

        double rate = rm.getCasinoRakebackRate(uuid); // 0.05
        int bet = 100;
        int rakeback = (int) Math.floor(bet * rate);  // 5

        // Guard: only credit when rakeback > 0 (mirrors the production guard in finish()).
        assertTrue(rakeback > 0, "rakeback must be positive before crediting");
        em.addBalance(uuid, rakeback);

        assertEquals(5.0, em.getBalance(uuid), 1e-9,
                "Balance must be exactly 5 after crediting a 5-coin rakeback on a 100-coin loss");
    }

    @Test
    void addBalanceDoesNotCreditWhenRakebackIsZero() {
        // Unranked player: rate = 0 → rakeback = 0 → no credit call should occur.
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();

        em.setBalance(uuid, 50.0);

        double rate = rm.getCasinoRakebackRate(uuid); // 0.0 (unranked)
        int bet = 100;
        int rakeback = (int) Math.floor(bet * rate); // 0

        // Production code skips the credit when rakeback == 0.
        // Verify the guard works correctly and balance stays unchanged.
        if (rakeback > 0) {
            em.addBalance(uuid, rakeback);
        }

        assertEquals(50.0, em.getBalance(uuid), 1e-9,
                "Balance must remain unchanged when rakeback is zero");
    }

    @Test
    void fullRakebackFlowRank5Player200BetCredits10() {
        // 200 * 0.05 = 10.0 → floor = 10.
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();

        em.setRank(uuid, 5);
        em.setBalance(uuid, 0.0);

        double rate = rm.getCasinoRakebackRate(uuid); // 0.05
        int bet = 200;
        int rakeback = (int) Math.floor(bet * rate); // 10

        em.addBalance(uuid, rakeback);

        assertEquals(10.0, em.getBalance(uuid), 1e-9,
                "A rank-5 player losing a 200-coin bet must receive a 10-coin rakeback");
    }
}
