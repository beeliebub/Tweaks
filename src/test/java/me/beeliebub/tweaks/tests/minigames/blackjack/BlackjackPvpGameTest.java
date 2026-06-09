package me.beeliebub.tweaks.tests.minigames.blackjack;

import me.beeliebub.tweaks.minigames.blackjack.BlackjackGame;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackPvpGame;
import me.beeliebub.tweaks.minigames.cards.Card;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static me.beeliebub.tweaks.minigames.blackjack.BlackjackPvpGame.SIDE_A;
import static me.beeliebub.tweaks.minigames.blackjack.BlackjackPvpGame.SIDE_B;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure head-to-head game-logic tests for {@link BlackjackPvpGame}. No running server.
 *
 * <p>The deck is randomly shuffled, so deterministic outcomes are forced by action
 * (hitting a side until it busts) rather than by stacking specific cards. The repeated
 * tests run many shuffles to cover the comparison branches statistically while still
 * asserting an exact invariant on every iteration.
 */
class BlackjackPvpGameTest {

    @Test
    void dealInitialGivesTwoCardsEachAndDoesNotFinish() {
        BlackjackPvpGame game = new BlackjackPvpGame();
        game.dealInitial();
        assertEquals(2, game.handA().size(), "side A should hold two opening cards");
        assertEquals(2, game.handB().size(), "side B should hold two opening cards");
        assertFalse(game.isFinished(), "the game is not settled immediately after the deal");
        assertNull(game.result(), "no result before settlement");
    }

    @Test
    void bothStandSettlesTheGame() {
        BlackjackPvpGame game = new BlackjackPvpGame();
        game.dealInitial();
        game.stand(SIDE_A);
        assertFalse(game.isFinished(), "one side standing does not settle the game");
        game.stand(SIDE_B);
        assertTrue(game.isFinished(), "both sides standing settles the game");
        assertNotNull(game.result());
    }

    @RepeatedTest(50)
    void twoCardStandoffResultMatchesComparison() {
        // Two opening cards can never bust (max is A+K = 21), so an immediate double-stand
        // always compares totals directly.
        BlackjackPvpGame game = new BlackjackPvpGame();
        game.dealInitial();
        int a = game.valueA();
        int b = game.valueB();
        game.stand(SIDE_A);
        game.stand(SIDE_B);

        BlackjackPvpGame.Result expected;
        if (a > b) {
            expected = BlackjackPvpGame.Result.A_WINS;
        } else if (b > a) {
            expected = BlackjackPvpGame.Result.B_WINS;
        } else {
            expected = BlackjackPvpGame.Result.PUSH;
        }
        assertEquals(expected, game.result(),
                "result must map to the higher two-card total (a=" + a + ", b=" + b + ")");
    }

    @RepeatedTest(50)
    void bustingSideLosesToANonBustingOpponent() {
        BlackjackPvpGame game = new BlackjackPvpGame();
        game.dealInitial();
        // Force side A to bust by hitting it repeatedly. A bust auto-stands A.
        while (!BlackjackGame.isBust(game.handA())) {
            game.hit(SIDE_A);
        }
        assertTrue(BlackjackGame.isBust(game.handA()), "side A should be busted");
        assertFalse(game.isFinished(), "side B still needs to act");
        // Side B stands on its (non-busted) two-card hand.
        game.stand(SIDE_B);
        assertTrue(game.isFinished());
        assertEquals(BlackjackPvpGame.Result.B_WINS, game.result(),
                "a busted side A loses to a standing side B");
    }

    @RepeatedTest(50)
    void bothBustingIsAPush() {
        BlackjackPvpGame game = new BlackjackPvpGame();
        game.dealInitial();
        while (!BlackjackGame.isBust(game.handA())) {
            game.hit(SIDE_A);
        }
        // A is auto-stood from its bust; B is still live, so hitting B until it busts settles.
        while (!game.isFinished()) {
            game.hit(SIDE_B);
        }
        assertTrue(BlackjackGame.isBust(game.handA()));
        assertTrue(BlackjackGame.isBust(game.handB()));
        assertEquals(BlackjackPvpGame.Result.PUSH, game.result(),
                "two busted hands push (no winner)");
    }

    @Test
    void actionsAfterSettlementAreNoOps() {
        BlackjackPvpGame game = new BlackjackPvpGame();
        game.dealInitial();
        game.stand(SIDE_A);
        game.stand(SIDE_B);
        assertTrue(game.isFinished());

        BlackjackPvpGame.Result settled = game.result();
        List<Card> handBefore = List.copyOf(game.handA());
        game.hit(SIDE_A);
        game.stand(SIDE_A);
        assertEquals(settled, game.result(), "result is frozen once settled");
        assertEquals(handBefore, game.handA(), "no further cards are dealt after settlement");
    }

    @Test
    void hittingAStoodSideIsIgnored() {
        BlackjackPvpGame game = new BlackjackPvpGame();
        game.dealInitial();
        game.stand(SIDE_A);
        int sizeAfterStand = game.handA().size();
        game.hit(SIDE_A);
        assertEquals(sizeAfterStand, game.handA().size(),
                "a side that has stood cannot draw more cards");
    }

    @Test
    void touchInteractionOverridesTimestamp() {
        BlackjackPvpGame game = new BlackjackPvpGame();
        game.touchInteraction(123_456L);
        assertEquals(123_456L, game.lastInteractionTime());
    }

    @Test
    void hittingAdvancesInteractionTimestamp() {
        BlackjackPvpGame game = new BlackjackPvpGame();
        game.dealInitial();
        game.touchInteraction(0L);
        game.hit(SIDE_A);
        assertTrue(game.lastInteractionTime() > 0L,
                "a live hit refreshes the inactivity timestamp");
    }
}
