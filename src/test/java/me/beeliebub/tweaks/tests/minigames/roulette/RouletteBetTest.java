package me.beeliebub.tweaks.tests.minigames.roulette;

import me.beeliebub.tweaks.minigames.roulette.BetType;
import me.beeliebub.tweaks.minigames.roulette.RouletteBet;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pins bet validation, payout multipliers, and the win/lose rule per bet family. */
class RouletteBetTest {

    private static final UUID PLAYER = UUID.randomUUID();

    // ---- construction validation ----

    @Test
    void rejectsZeroOrNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> new RouletteBet(PLAYER, BetType.STRAIGHT, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> new RouletteBet(PLAYER, BetType.STRAIGHT, 5, -1));
    }

    @Test
    void straightSelectorMustBeAPocketNumber() {
        assertThrows(IllegalArgumentException.class, () -> new RouletteBet(PLAYER, BetType.STRAIGHT, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> new RouletteBet(PLAYER, BetType.STRAIGHT, 37, 10));
        assertDoesNotThrow(() -> new RouletteBet(PLAYER, BetType.STRAIGHT, 0, 10));
        assertDoesNotThrow(() -> new RouletteBet(PLAYER, BetType.STRAIGHT, 36, 10));
    }

    @Test
    void dozenSelectorMustBeOneTwoOrThree() {
        assertThrows(IllegalArgumentException.class, () -> new RouletteBet(PLAYER, BetType.DOZEN, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new RouletteBet(PLAYER, BetType.DOZEN, 4, 10));
        assertDoesNotThrow(() -> new RouletteBet(PLAYER, BetType.DOZEN, 1, 10));
        assertDoesNotThrow(() -> new RouletteBet(PLAYER, BetType.DOZEN, 3, 10));
    }

    @Test
    void colorSelectorMustBeRedOrBlackConstant() {
        assertThrows(IllegalArgumentException.class, () -> new RouletteBet(PLAYER, BetType.COLOR, 2, 10));
        assertDoesNotThrow(() -> new RouletteBet(PLAYER, BetType.COLOR, RouletteBet.COLOR_RED, 10));
        assertDoesNotThrow(() -> new RouletteBet(PLAYER, BetType.COLOR, RouletteBet.COLOR_BLACK, 10));
    }

    // ---- payout multipliers ----

    @Test
    void payoutMultipliersMatchTheThreeBetFamilies() {
        assertEquals(36, new RouletteBet(PLAYER, BetType.STRAIGHT, 17, 10).payoutMultiplier());
        assertEquals(3, new RouletteBet(PLAYER, BetType.DOZEN, 1, 10).payoutMultiplier());
        assertEquals(2, new RouletteBet(PLAYER, BetType.COLOR, RouletteBet.COLOR_RED, 10).payoutMultiplier());
    }

    @Test
    void straightOnPocketZeroPaysTheGreenOverrideInsteadOfTheStandardRate() {
        assertEquals(RouletteBet.GREEN_PAYOUT_MULTIPLIER,
                new RouletteBet(PLAYER, BetType.STRAIGHT, 0, 10).payoutMultiplier());
        assertEquals(RouletteBet.GREEN_PAYOUT_MULTIPLIER, RouletteBet.payoutMultiplierFor(BetType.STRAIGHT, 0));
        assertEquals(36, RouletteBet.payoutMultiplierFor(BetType.STRAIGHT, 17),
                "every other pocket still pays the standard straight-up rate");
    }

    // ---- wins() per bet family ----

    @Test
    void straightWinsOnlyOnItsExactPocket() {
        RouletteBet bet = new RouletteBet(PLAYER, BetType.STRAIGHT, 17, 10);
        assertTrue(bet.wins(17));
        assertFalse(bet.wins(16));
        assertFalse(bet.wins(0));
    }

    @Test
    void dozenWinsAcrossItsWholeRangeAndLosesOnZero() {
        RouletteBet firstDozen = new RouletteBet(PLAYER, BetType.DOZEN, 1, 10);
        assertTrue(firstDozen.wins(1));
        assertTrue(firstDozen.wins(12));
        assertFalse(firstDozen.wins(13));
        assertFalse(firstDozen.wins(0), "pocket 0 is in no dozen");
    }

    @Test
    void colorWinsOnlyItsColorAndLosesOnZero() {
        RouletteBet red = new RouletteBet(PLAYER, BetType.COLOR, RouletteBet.COLOR_RED, 10);
        assertTrue(red.wins(1), "pocket 1 is red");
        assertFalse(red.wins(2), "pocket 2 is black");
        assertFalse(red.wins(0), "pocket 0 is green, neither color wins");

        RouletteBet black = new RouletteBet(PLAYER, BetType.COLOR, RouletteBet.COLOR_BLACK, 10);
        assertTrue(black.wins(2));
        assertFalse(black.wins(1));
        assertFalse(black.wins(0));
    }
}
