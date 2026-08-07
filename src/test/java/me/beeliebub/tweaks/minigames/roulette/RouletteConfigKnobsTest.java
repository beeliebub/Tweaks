package me.beeliebub.tweaks.minigames.roulette;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Placed in the production package so package-private access to {@link RouletteSessionManager}'s
 * {@code isBigWin} 3-arg overload is legal without widening production visibility — mirrors
 * {@code RouletteStakeValidationTest}. Pure logic, no MockBukkit needed.
 *
 * <p>{@code minigames.roulette.big-win-multiplier}'s live-config-driven call site
 * ({@code RouletteSessionManager#bigWinMultiplier}) reads {@code plugin.getConfig()} directly and
 * isn't independently exercised here — {@code core/config/ConfigRegistry} already bounds the
 * setting at {@code >= 1} at the {@code /tconfig} edit boundary, and the 3-arg {@code isBigWin}
 * below is the pure math it ultimately calls into.
 */
class RouletteConfigKnobsTest {

    @Test
    void isBigWinWithConfiguredMultiplierAcceptsExactlyNTimesWagered() {
        assertTrue(RouletteSessionManager.isBigWin(10, 40, 4),
                "exactly 4x winnings must count as a big win when the multiplier is configured to 4");
    }

    @Test
    void isBigWinWithConfiguredMultiplierRejectsJustUnder() {
        assertFalse(RouletteSessionManager.isBigWin(10, 39, 4));
    }

    @Test
    void isBigWinWithConfiguredMultiplierRejectsZeroWagered() {
        assertFalse(RouletteSessionManager.isBigWin(0, 100, 4),
                "nothing was staked, so nothing can be a big win regardless of the multiplier");
    }

    @Test
    void twoArgOverloadStillDefaultsToEightXForThePinnedTest() {
        // core/config/ConfigRegistry.bounded rejects big-win-multiplier <= 0 at the /tconfig
        // boundary; the 2-arg isBigWin overload (used by RouletteStakeValidationTest) must keep
        // its historical 8x default regardless of any live config read.
        assertTrue(RouletteSessionManager.isBigWin(10, 80));
        assertFalse(RouletteSessionManager.isBigWin(10, 79));
    }
}
