package me.beeliebub.tweaks.tests.minigames.roulette;

import me.beeliebub.tweaks.minigames.roulette.BetType;
import me.beeliebub.tweaks.minigames.roulette.RouletteBet;
import me.beeliebub.tweaks.minigames.roulette.RouletteRound;
import me.beeliebub.tweaks.minigames.roulette.RouletteRound.State;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pins the round state machine, the ledger, and the per-player exposure guard. */
class RouletteRoundTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private static RouletteBet bet(int amount) {
        return new RouletteBet(PLAYER, BetType.STRAIGHT, 17, amount);
    }

    @Test
    void startsIdleWithNoBets() {
        RouletteRound round = new RouletteRound();
        assertEquals(State.IDLE, round.state());
        assertTrue(round.bets().isEmpty());
    }

    @Test
    void firstBetOpensBettingWindow() {
        RouletteRound round = new RouletteRound();
        assertTrue(round.placeBet(bet(10)));
        assertEquals(State.BETTING, round.state());
        assertEquals(1, round.bets().size());
    }

    @Test
    void multipleBetsStackInTheLedger() {
        RouletteRound round = new RouletteRound();
        round.placeBet(bet(10));
        round.placeBet(new RouletteBet(PLAYER, BetType.DOZEN, 2, 5));
        round.placeBet(new RouletteBet(PLAYER, BetType.COLOR, RouletteBet.COLOR_RED, 3));
        assertEquals(3, round.bets().size());
    }

    @Test
    void betsAreRejectedOnceSpinning() {
        RouletteRound round = new RouletteRound();
        round.placeBet(bet(10));
        round.closeBetting(17);
        assertEquals(State.SPINNING, round.state());
        assertFalse(round.placeBet(bet(10)), "a click during SPINNING must not debit or join the ledger");
        assertEquals(1, round.bets().size());
    }

    @Test
    void betsAreRejectedOnceSettled() {
        RouletteRound round = new RouletteRound();
        round.placeBet(bet(10));
        round.closeBetting(17);
        round.settle();
        assertEquals(State.SETTLED, round.state());
        assertFalse(round.placeBet(bet(10)));
    }

    @Test
    void closeBettingRequiresBettingState() {
        RouletteRound round = new RouletteRound();
        assertThrows(IllegalStateException.class, () -> round.closeBetting(17), "cannot close betting on an IDLE round");
    }

    @Test
    void settleRequiresSpinningState() {
        RouletteRound round = new RouletteRound();
        round.placeBet(bet(10));
        assertThrows(IllegalStateException.class, round::settle, "cannot settle before closeBetting");
    }

    // ---- closeBetting(int) / drawnPocket() ----

    @Test
    void closeBettingRejectsAnOutOfRangePocketAndMutatesNothing() {
        RouletteRound round = new RouletteRound();
        round.placeBet(bet(10));
        assertThrows(IllegalArgumentException.class, () -> round.closeBetting(-1));
        assertThrows(IllegalArgumentException.class, () -> round.closeBetting(37));
        assertEquals(State.BETTING, round.state(), "a rejected pocket must not close betting");
        assertEquals(-1, round.drawnPocket(), "a rejected pocket must not be recorded");
    }

    @Test
    void drawnPocketIsUnsetUntilBettingCloses() {
        RouletteRound round = new RouletteRound();
        round.placeBet(bet(10));
        assertEquals(-1, round.drawnPocket());
        round.closeBetting(0);
        assertEquals(0, round.drawnPocket());
    }

    @Test
    void drawnPocketSurvivesSettlement() {
        RouletteRound round = new RouletteRound();
        round.placeBet(bet(10));
        round.closeBetting(22);
        round.settle();
        assertEquals(22, round.drawnPocket());
    }

    @Test
    void settleTwiceThrowsOnTheSecondCall() {
        RouletteRound round = new RouletteRound();
        round.placeBet(bet(10));
        round.closeBetting(17);
        round.settle();
        assertThrows(IllegalStateException.class, round::settle,
                "double-settlement must be structurally impossible");
    }

    // ---- canPlace(UUID, int) — non-mutating ----

    @Test
    void canPlaceDoesNotMutateStateOnRejection() {
        RouletteRound round = new RouletteRound();
        round.placeBet(bet(10));
        round.closeBetting(17);
        assertFalse(round.canPlace(PLAYER, 10), "SPINNING must reject canPlace too");
        assertEquals(State.SPINNING, round.state());
        assertEquals(1, round.bets().size());
    }

    @Test
    void canPlaceAgreesWithPlaceBetOnAcceptance() {
        RouletteRound round = new RouletteRound();
        assertTrue(round.canPlace(PLAYER, 10));
        assertTrue(round.placeBet(bet(10)));
    }

    @Test
    void canPlaceIsFalseAtTheExactBoundaryPlaceBetWouldReject() {
        RouletteRound round = new RouletteRound();
        int nearCeiling = RouletteRound.MAX_CUMULATIVE_WAGER;
        round.placeBet(new RouletteBet(PLAYER, BetType.STRAIGHT, 17, nearCeiling));
        assertFalse(round.canPlace(PLAYER, Integer.MAX_VALUE));
    }

    // ---- MAX_CUMULATIVE_WAGER boundary ----

    @Test
    void maxCumulativeWagerIsAcceptedButOneMoreIsRejected() {
        RouletteRound round = new RouletteRound();
        assertTrue(round.placeBet(new RouletteBet(PLAYER, BetType.STRAIGHT, 17, RouletteRound.MAX_CUMULATIVE_WAGER)),
                "MAX_CUMULATIVE_WAGER itself must be acceptable as a lone first bet");
        RouletteRound freshRound = new RouletteRound();
        assertFalse(freshRound.placeBet(new RouletteBet(PLAYER, BetType.STRAIGHT, 17, RouletteRound.MAX_CUMULATIVE_WAGER + 1)),
                "one dollar over the ceiling must be rejected");
    }

    @Test
    void bettersListHasNoRemovalMethodStructurally() {
        RouletteRound round = new RouletteRound();
        round.placeBet(bet(10));
        assertThrows(UnsupportedOperationException.class, () -> round.bets().remove(0),
                "the returned ledger snapshot must be immutable — bets are final, no un-bet, enforced structurally");
    }

    // ---- exposure guard: totalWagered * MAX_CUMULATIVE_WAGER's multiplier must never exceed Integer.MAX_VALUE ----

    @Test
    void exposureGuardRejectsAWagerThatWouldOverflow() {
        RouletteRound round = new RouletteRound();
        // MAX_CUMULATIVE_WAGER wagered in straight-up bets is already at the ceiling for this player.
        int nearCeiling = RouletteRound.MAX_CUMULATIVE_WAGER;
        assertTrue(round.placeBet(new RouletteBet(PLAYER, BetType.STRAIGHT, 17, nearCeiling)));
        assertFalse(round.placeBet(new RouletteBet(PLAYER, BetType.STRAIGHT, 17, Integer.MAX_VALUE)),
                "cumulative exposure must be rejected before it can overflow int");
        assertEquals(1, round.bets().size(), "the rejected bet must not join the ledger");
    }

    @Test
    void exposureGuardIsPerPlayerNotGlobal() {
        RouletteRound round = new RouletteRound();
        UUID otherPlayer = UUID.randomUUID();
        int nearCeiling = RouletteRound.MAX_CUMULATIVE_WAGER;
        assertTrue(round.placeBet(new RouletteBet(PLAYER, BetType.STRAIGHT, 17, nearCeiling)));
        assertTrue(round.placeBet(new RouletteBet(otherPlayer, BetType.STRAIGHT, 17, nearCeiling)),
                "another player's exposure must be tracked independently");
    }

    @Test
    void rejectedFirstBetLeavesRoundIdleWithNoStateMutation() {
        RouletteRound round = new RouletteRound();
        // A single bet whose amount alone fails the exposure guard (amount * multiplier > Integer.MAX_VALUE).
        RouletteBet tooLarge = new RouletteBet(PLAYER, BetType.STRAIGHT, 17, Integer.MAX_VALUE);
        assertFalse(round.placeBet(tooLarge), "an oversized first bet must be rejected");
        assertEquals(State.IDLE, round.state(),
                "a rejected first bet must not open the betting window (no state mutated on rejection)");
        assertTrue(round.bets().isEmpty());
    }

    @Test
    void exposureGuardAccumulatesAcrossManySmallBets() {
        RouletteRound round = new RouletteRound();
        int perClick = 1_000_000;
        int clicks = 0;
        boolean rejectedEventually = false;
        for (int i = 0; i < 300; i++) {
            if (!round.placeBet(new RouletteBet(PLAYER, BetType.STRAIGHT, 17, perClick))) {
                rejectedEventually = true;
                break;
            }
            clicks++;
        }
        assertTrue(rejectedEventually, "200 clicks at a large stake must eventually hit the ceiling, not wrap past it");
        assertEquals(clicks, round.bets().size());
    }
}
