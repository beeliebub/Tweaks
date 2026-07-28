package me.beeliebub.tweaks.minigames.roulette;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Placed in the production package (no {@code tests.} prefix) so package-private access to
 * {@link RouletteSessionManager}'s {@code parseStake}/{@code withinBoardBounds} statics is legal
 * without widening production visibility — mirrors the exception already documented for
 * {@code RouletteGeometryTest}. Pure logic, no MockBukkit needed.
 */
class RouletteStakeValidationTest {

    // ---- parseStake: accepted ----

    @Test
    void parseStakeAcceptsAPlainPositiveInteger() {
        var parsed = RouletteSessionManager.parseStake("10");
        assertTrue(parsed.ok());
        assertEquals(10, parsed.amount());
        assertNull(parsed.error());
    }

    @Test
    void parseStakeAcceptsALeadingPlusSign() {
        var parsed = RouletteSessionManager.parseStake("+10");
        assertTrue(parsed.ok());
        assertEquals(10, parsed.amount());
    }

    @Test
    void parseStakeAcceptsExactlyTheCeiling() {
        var parsed = RouletteSessionManager.parseStake(String.valueOf(RouletteRound.MAX_CUMULATIVE_WAGER));
        assertTrue(parsed.ok());
    }

    // ---- parseStake: NOT_A_WHOLE_NUMBER ----

    @Test
    void parseStakeRejectsNullAndEmpty() {
        assertEquals(RouletteSessionManager.StakeParseError.NOT_A_WHOLE_NUMBER, RouletteSessionManager.parseStake(null).error());
        assertEquals(RouletteSessionManager.StakeParseError.NOT_A_WHOLE_NUMBER, RouletteSessionManager.parseStake("").error());
    }

    @Test
    void parseStakeRejectsNonNumericAndDecimalAndSeparatedInput() {
        String[] bad = { "abc", "1.5", "1,5", "10x", "1e3", "1_000", " 10", "10 " };
        for (String raw : bad) {
            var parsed = RouletteSessionManager.parseStake(raw);
            assertEquals(RouletteSessionManager.StakeParseError.NOT_A_WHOLE_NUMBER, parsed.error(),
                    "expected NOT_A_WHOLE_NUMBER for input: '" + raw + "'");
        }
    }

    @Test
    void parseStakeRejectsAnOverflowingLiteralAsNotAWholeNumber() {
        // Larger than Integer.MAX_VALUE — Integer.parseInt itself throws, which parseStake maps to
        // NOT_A_WHOLE_NUMBER rather than letting the exception propagate.
        var parsed = RouletteSessionManager.parseStake("99999999999999");
        assertEquals(RouletteSessionManager.StakeParseError.NOT_A_WHOLE_NUMBER, parsed.error());
    }

    // ---- parseStake: BELOW_MINIMUM ----

    @Test
    void parseStakeRejectsZeroAndNegative() {
        assertEquals(RouletteSessionManager.StakeParseError.BELOW_MINIMUM, RouletteSessionManager.parseStake("0").error());
        assertEquals(RouletteSessionManager.StakeParseError.BELOW_MINIMUM, RouletteSessionManager.parseStake("-5").error());
    }

    // ---- parseStake: ABOVE_CEILING ----

    @Test
    void parseStakeRejectsIntegerMaxValue() {
        var parsed = RouletteSessionManager.parseStake(String.valueOf(Integer.MAX_VALUE));
        assertEquals(RouletteSessionManager.StakeParseError.ABOVE_CEILING, parsed.error());
    }

    @Test
    void parseStakeRejectsOneOverTheCeiling() {
        var parsed = RouletteSessionManager.parseStake(String.valueOf(RouletteRound.MAX_CUMULATIVE_WAGER + 1));
        assertEquals(RouletteSessionManager.StakeParseError.ABOVE_CEILING, parsed.error());
    }

    // ---- withinBoardBounds ----

    @Test
    void withinBoardBoundsAcceptsExactMinAndMax() {
        assertTrue(RouletteSessionManager.withinBoardBounds(5, 5, 100));
        assertTrue(RouletteSessionManager.withinBoardBounds(100, 5, 100));
    }

    @Test
    void withinBoardBoundsRejectsBelowMinAndAboveMax() {
        assertFalse(RouletteSessionManager.withinBoardBounds(4, 5, 100));
        assertFalse(RouletteSessionManager.withinBoardBounds(101, 5, 100));
    }

    // ---- isBigWin: compares payout (winnings only — the stake is never returned) to 8x wagered ----

    @Test
    void isBigWinAcceptsExactlyEightTimesWagered() {
        assertTrue(RouletteSessionManager.isBigWin(10, 80), "exactly 8x winnings must count as a big win");
    }

    @Test
    void isBigWinRejectsJustUnderEightTimesWagered() {
        assertFalse(RouletteSessionManager.isBigWin(10, 79));
    }

    @Test
    void isBigWinAcceptsWellOverTheThreshold() {
        // a Green (50:1) win on a 10 stake pays 10 * 50 = 500, well past the 80 threshold.
        assertTrue(RouletteSessionManager.isBigWin(10, 500));
    }

    @Test
    void isBigWinRejectsATypicalDozenOrColorWin() {
        // Dozen (3:1) pays 10 * 3 = 30, colour (2:1) pays 10 * 2 = 20 — neither reaches the 8x threshold.
        assertFalse(RouletteSessionManager.isBigWin(10, 30));
        assertFalse(RouletteSessionManager.isBigWin(10, 20));
    }

    @Test
    void isBigWinRejectsZeroOrNegativeWagered() {
        assertFalse(RouletteSessionManager.isBigWin(0, 100), "nothing was staked, so nothing can be a big win");
        assertFalse(RouletteSessionManager.isBigWin(-10, 100));
    }

    @Test
    void isBigWinRejectsALoss() {
        assertFalse(RouletteSessionManager.isBigWin(10, 0));
    }
}
