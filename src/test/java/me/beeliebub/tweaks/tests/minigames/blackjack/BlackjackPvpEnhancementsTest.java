package me.beeliebub.tweaks.tests.minigames.blackjack;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener.MoneyButtonRef;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener.PvpSession;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener.PvpTableEntry;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackPvpGame;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for three PvP Blackjack behavioral enhancements:
 *   1. Dual-confirm gate — both players must confirm (CONFIRMING_BETS phase) before cards are dealt.
 *   2. LEFT/RIGHT during CONFIRMING_BETS cancels the session and refunds escrowed money.
 *   3. Per-player card display bookkeeping — faceUpIds, faceDownIds, displayIds counts.
 *
 * <p>The pipeline tested here (bead i0m7) is the three-round MIDDLE-button flow:
 *   Round 1 (REGISTERING): both press MIDDLE to claim a seat -> BETTING.
 *   Round 2 (BETTING): both press MIDDLE to lock stakes -> CONFIRMING_BETS.
 *   Round 3 (CONFIRMING_BETS): both press MIDDLE to confirm -> ACTIVE (cards dealt).
 */
public class BlackjackPvpEnhancementsTest {

    private ServerMock server;
    private Tweaks plugin;
    private BlackjackListener listener;
    private EconomyManager economy;

    // Table geometry — mirrors BlackjackPvpIntegrationTest.
    private World world;
    private Location center;
    private Location middleA;   // side A middle button, facing NORTH
    private Location middleB;   // side B middle button, facing SOUTH

    // Derived button locations (computed from BlackjackListener geometry).
    // For NORTH: left = +X, right = -X.
    // For SOUTH: left = -X, right = +X.
    private Location leftA;     // (11, 64, 8)
    private Location rightA;    // (9,  64, 8)
    private Location leftB;     // (9,  64, 12)
    private Location rightB;    // (11, 64, 12)

    private PvpTableEntry entry;
    private PlayerMock playerA;
    private PlayerMock playerB;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        listener = plugin.getBlackjackListener();
        economy = plugin.getEconomyManager();

        world = server.addSimpleWorld("test");
        center  = new Location(world, 10.5, 64.0, 10.5);
        middleA = new Location(world, 10, 64, 8);
        middleB = new Location(world, 10, 64, 12);

        // Derive LEFT/RIGHT from the same offsets as BlackjackListener.
        // NORTH: leftButtonOffset = {+1,0,0}, rightButtonOffset = {-1,0,0}
        leftA  = new Location(world, 11, 64, 8);
        rightA = new Location(world, 9,  64, 8);
        // SOUTH: leftButtonOffset = {-1,0,0}, rightButtonOffset = {+1,0,0}
        leftB  = new Location(world, 9,  64, 12);
        rightB = new Location(world, 11, 64, 12);

        // Set all six control-button blocks as oak buttons with the correct facing.
        setupButton(middleA, BlockFace.NORTH);
        setupButton(leftA,   BlockFace.NORTH);
        setupButton(rightA,  BlockFace.NORTH);
        setupButton(middleB, BlockFace.SOUTH);
        setupButton(leftB,   BlockFace.SOUTH);
        setupButton(rightB,  BlockFace.SOUTH);

        entry = new PvpTableEntry(center, null,
                middleA, BlockFace.NORTH,
                middleB, BlockFace.SOUTH);

        listener.registerPvpButtonsForTable(entry);

        playerA = server.addPlayer("Alice");
        playerB = server.addPlayer("Bob");
        economy.setBalance(playerA.getUniqueId(), 1000);
        economy.setBalance(playerB.getUniqueId(), 1000);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private void setupButton(Location loc, BlockFace facing) {
        loc.getBlock().setType(Material.OAK_BUTTON);
        if (loc.getBlock().getBlockData() instanceof Switch sw) {
            sw.setFacing(facing);
            loc.getBlock().setBlockData(sw);
        }
    }

    private void click(Player player, Location loc) {
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK,
                new ItemStack(Material.AIR),
                loc.getBlock(), BlockFace.UP);
        listener.onButtonClick(event);
    }

    /**
     * Drive the session to CONFIRMING_BETS state using the new three-round pipeline.
     *
     * Round 1 (REGISTERING phase):
     *   click(A, middleA) — registers A; still waiting for B.
     *   click(B, middleB) — registers B; both registered → state becomes BETTING.
     *
     * Round 2 (BETTING phase):
     *   click(A, middleA) — locks A's stake (readyA=true).
     *   click(B, middleB) — locks B's stake; both ready → state becomes CONFIRMING_BETS.
     */
    private PvpSession reachConfirmingBets() {
        // Round 1: registration
        click(playerA, middleA);
        click(playerB, middleB);
        // Round 2: stake locking
        click(playerA, middleA);
        click(playerB, middleB);

        String key = BlackjackListener.blockKey(middleA);
        PvpSession session = listener.pvpSessions().get(key);
        assertNotNull(session, "Session must exist after registration and stake-lock rounds");
        assertEquals(BlackjackPvpGame.State.CONFIRMING_BETS, session.game.state(),
                "Session must be in CONFIRMING_BETS state after both players lock stakes");
        return session;
    }

    /**
     * Drive the session to ACTIVE state (cards dealt) from a fresh table.
     * Reaches CONFIRMING_BETS then has both players confirm.
     *
     * Round 3 (CONFIRMING_BETS phase):
     *   click(B, middleB) — B confirms.
     *   click(A, middleA) — A confirms; both confirmed → dealPvp → ACTIVE.
     */
    private PvpSession reachActive() {
        PvpSession session = reachConfirmingBets();
        // Round 3: confirmation
        click(playerB, middleB);  // first confirm — not enough on its own
        click(playerA, middleA);  // second confirm — triggers dealPvp
        assertEquals(BlackjackPvpGame.State.ACTIVE, session.game.state(),
                "Session must be ACTIVE after both players confirm");
        return session;
    }

    // =========================================================================
    // Test 1 — Dual-confirm gate (CONFIRMING_BETS phase)
    // =========================================================================

    /**
     * One confirm is not enough to deal: state stays CONFIRMING_BETS until BOTH players confirm.
     * After both confirm the state advances to ACTIVE and each hand has exactly 2 cards.
     */
    @Test
    public void dualConfirmGate_bothConfirmRequired() {
        PvpSession session = reachConfirmingBets();

        // Precondition: neither side confirmed.
        assertEquals(BlackjackPvpGame.State.CONFIRMING_BETS, session.game.state());
        assertFalse(session.confirmedA, "confirmedA must be false before any confirm");
        assertFalse(session.confirmedB, "confirmedB must be false before any confirm");

        // Only B confirms — should NOT deal.
        click(playerB, middleB);
        assertEquals(BlackjackPvpGame.State.CONFIRMING_BETS, session.game.state(),
                "State must stay CONFIRMING_BETS after only one confirm");
        assertTrue(session.confirmedB,  "confirmedB must be true after B confirms");
        assertFalse(session.confirmedA, "confirmedA must still be false");

        // Now A also confirms — both confirmed, should deal.
        click(playerA, middleA);
        assertEquals(BlackjackPvpGame.State.ACTIVE, session.game.state(),
                "State must be ACTIVE once both players confirm");
        assertEquals(2, session.game.handA().size(),
                "Hand A must have exactly 2 cards after the deal");
        assertEquals(2, session.game.handB().size(),
                "Hand B must have exactly 2 cards after the deal");
    }

    /**
     * Re-confirming the already-confirmed side is idempotent: clicking that side again
     * does not advance the state past CONFIRMING_BETS while the other player is still unconfirmed.
     */
    @Test
    public void dualConfirmGate_reconfirmIsSameIdempotent() {
        PvpSession session = reachConfirmingBets();

        // A confirms once.
        click(playerA, middleA);
        assertEquals(BlackjackPvpGame.State.CONFIRMING_BETS, session.game.state());
        assertTrue(session.confirmedA, "confirmedA must be true after first click");

        // A clicks again (already confirmed) — state must not advance and confirmedA stays true.
        click(playerA, middleA);
        assertEquals(BlackjackPvpGame.State.CONFIRMING_BETS, session.game.state(),
                "State must remain CONFIRMING_BETS — B has not confirmed yet");
        assertTrue(session.confirmedA,
                "confirmedA must remain true after duplicate confirm click");
    }

    // =========================================================================
    // Test 2 — LEFT/RIGHT during CONFIRMING_BETS cancels & refunds
    // =========================================================================

    /**
     * Pressing a Hit (LEFT) button while the session is in CONFIRMING_BETS state aborts
     * the session entirely and refunds any escrowed money wager to the presser.
     */
    @Test
    public void leftDuringConfirmingBets_abortsAndRefunds() {
        // Give A a wager during the BETTING phase (before we reach CONFIRMING_BETS).
        // Round 1: registration
        click(playerA, middleA);
        click(playerB, middleB);

        // Apply wager for A now that we're in BETTING state.
        String key = BlackjackListener.blockKey(middleA);
        PvpSession session = listener.pvpSessions().get(key);
        assertNotNull(session, "Session must exist after registration");
        assertEquals(BlackjackPvpGame.State.BETTING, session.game.state(),
                "Must be in BETTING after both registered");

        listener.promptPvpWager(playerA, new MoneyButtonRef(entry, 0));
        listener.applyWager(playerA, "100");
        assertEquals(100, session.moneyStakeA, "Stake A must be 100 after wager");

        // Balance should be 900 (100 escrowed).
        double balanceAfterEscrow = economy.getBalance(playerA.getUniqueId());
        assertEquals(900.0, balanceAfterEscrow, 0.01,
                "Balance must drop to 900 after 100 is escrowed");

        // Round 2: lock stakes -> CONFIRMING_BETS
        click(playerA, middleA);  // A locks stake
        click(playerB, middleB);  // B locks stake -> CONFIRMING_BETS
        assertEquals(BlackjackPvpGame.State.CONFIRMING_BETS, session.game.state(),
                "Must be in CONFIRMING_BETS before the cancel click");

        // Press the LEFT (Hit) button on side A — this is a pre-ACTIVE action-button press.
        click(playerA, leftA);

        // Session must be removed.
        assertNull(listener.pvpSessions().get(key),
                "Session must be removed after LEFT press during CONFIRMING_BETS");

        // Wager must be refunded — balance back to 1000.
        double balanceAfterRefund = economy.getBalance(playerA.getUniqueId());
        assertEquals(1000.0, balanceAfterRefund, 0.01,
                "Alice's wager must be fully refunded after session abort");
    }

    /**
     * Pressing a Stand (RIGHT) button while the session is in CONFIRMING_BETS state also
     * aborts the session and refunds the escrowed wager (symmetrical to the LEFT case).
     */
    @Test
    public void rightDuringConfirmingBets_abortsAndRefunds() {
        // Round 1: registration
        click(playerA, middleA);
        click(playerB, middleB);

        // Apply wager for B during BETTING state.
        String key = BlackjackListener.blockKey(middleA);
        PvpSession session = listener.pvpSessions().get(key);
        assertNotNull(session);
        assertEquals(BlackjackPvpGame.State.BETTING, session.game.state());

        listener.promptPvpWager(playerB, new MoneyButtonRef(entry, 1));
        listener.applyWager(playerB, "200");
        assertEquals(200, session.moneyStakeB);
        double balanceAfterEscrow = economy.getBalance(playerB.getUniqueId());
        assertEquals(800.0, balanceAfterEscrow, 0.01,
                "Bob's balance must be 800 after 200 is escrowed");

        // Round 2: lock stakes -> CONFIRMING_BETS
        click(playerA, middleA);  // A locks stake
        click(playerB, middleB);  // B locks stake -> CONFIRMING_BETS
        assertEquals(BlackjackPvpGame.State.CONFIRMING_BETS, session.game.state(),
                "Must be in CONFIRMING_BETS before the cancel click");

        // B confirms once (not enough to deal) — still in CONFIRMING_BETS.
        click(playerB, middleB);
        assertEquals(BlackjackPvpGame.State.CONFIRMING_BETS, session.game.state(),
                "Must still be in CONFIRMING_BETS after only one confirm");

        // Press RIGHT (Stand) on side B during CONFIRMING_BETS.
        click(playerB, rightB);

        assertNull(listener.pvpSessions().get(key),
                "Session must be removed after RIGHT press during CONFIRMING_BETS");

        double balanceAfterRefund = economy.getBalance(playerB.getUniqueId());
        assertEquals(1000.0, balanceAfterRefund, 0.01,
                "Bob's wager must be fully refunded after session abort");
    }

    /**
     * LEFT press during BETTING phase (before stake lock) also cancels the session.
     */
    @Test
    public void leftDuringBetting_abortsSession() {
        // Round 1: registration -> BETTING
        click(playerA, middleA);
        click(playerB, middleB);

        String key = BlackjackListener.blockKey(middleA);
        PvpSession session = listener.pvpSessions().get(key);
        assertNotNull(session);
        assertEquals(BlackjackPvpGame.State.BETTING, session.game.state());

        // Press LEFT during BETTING — should abort.
        click(playerA, leftA);
        assertNull(listener.pvpSessions().get(key),
                "Session must be removed after LEFT press during BETTING");
    }

    // =========================================================================
    // Test 3 — Per-player card display bookkeeping
    // =========================================================================

    /**
     * After the initial deal (2 cards per hand, 4 cards total), spawnPvpCard is called
     * 4 times, each spawning one face-up and one face-down ItemDisplay. We assert the
     * three tracking lists have the expected sizes (4 + 4 + 8).
     */
    @Test
    public void cardDisplayBookkeeping_dealTimeCountsAreCorrect() {
        PvpSession session = reachActive();

        // 4 cards dealt (2 per hand), each spawns 1 face-up + 1 face-down entity.
        assertEquals(4, session.faceUpIds.size(),
                "faceUpIds must have 4 entries after deal (one per card)");
        assertEquals(4, session.faceDownIds.size(),
                "faceDownIds must have 4 entries after deal (one per card)");
        assertEquals(8, session.displayIds.size(),
                "displayIds must have 8 entries (both face-up and face-down per card)");
    }

    /**
     * Drive to ACTIVE then have both players Stand. Assert no exception is thrown,
     * the game reaches a finished state, and the session is cleaned up (set to
     * waitingToClear=true). Display counts may be 0 after finish because redrawPvp
     * clears and re-renders, and revealAllPvpCards runs on the same thread.
     */
    @Test
    public void cardDisplayBookkeeping_finishPathRunsCleanly() {
        PvpSession session = reachActive();

        // Both players stand — the simplest path to finish.
        // RIGHT button = Stand.
        assertDoesNotThrow(() -> {
            click(playerA, rightA);
            click(playerB, rightB);
        }, "Stand sequence must not throw");

        // The game should be finished after both stand.
        assertTrue(session.game.isFinished(),
                "Game must be finished after both players stand");

        // Session must be in the waiting-to-clear state.
        assertTrue(session.waitingToClear,
                "Session must enter waitingToClear=true after finish");

        // displayIds may be non-zero (redrawPvp re-renders) or zero depending on render path;
        // we assert it is consistent — both face lists must match in size.
        assertEquals(session.faceUpIds.size(), session.faceDownIds.size(),
                "faceUpIds and faceDownIds must remain balanced after finish");
    }

    /**
     * Assert that faceUpIds and faceDownIds are disjoint sets — no UUID should appear
     * in both lists, confirming that face-up and face-down entities are tracked separately.
     */
    @Test
    public void cardDisplayBookkeeping_faceUpAndFaceDownAreDisjoint() {
        PvpSession session = reachActive();

        for (java.util.UUID id : session.faceUpIds) {
            assertFalse(session.faceDownIds.contains(id),
                    "A UUID must not appear in both faceUpIds and faceDownIds: " + id);
        }
        // Also confirm every tracked ID appears in displayIds.
        for (java.util.UUID id : session.faceUpIds) {
            assertTrue(session.displayIds.contains(id),
                    "Every faceUpId must also be in displayIds: " + id);
        }
        for (java.util.UUID id : session.faceDownIds) {
            assertTrue(session.displayIds.contains(id),
                    "Every faceDownId must also be in displayIds: " + id);
        }
    }
}
