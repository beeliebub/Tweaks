package me.beeliebub.tweaks.tests.lottery;

import me.beeliebub.tweaks.lottery.LotteryMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LotteryMathTest {

    private static final long RESEED = 10_000L;

    @Test
    void calculatesHalfGrowthAgainstTheNewHouseTotal() {
        LotteryMath.PotOutcome.Payable outcome = payable(1, 30_000, 20_000);
        assertEquals(15_000, outcome.pot());
        assertEquals(15_000, outcome.newHouseBalance());
        assertEquals(15_000, outcome.newBaseline());
        assertEquals(false, outcome.capBound());
    }

    @Test
    void capsOvergrowthAtTheWholeHouseBalance() {
        LotteryMath.PotOutcome.Payable outcome = payable(1, 30_000, 10_000);
        assertEquals(30_000, outcome.pot());
        assertEquals(RESEED, outcome.newHouseBalance());
        assertEquals(RESEED, outcome.newBaseline());
        assertEquals(true, outcome.capBound());
    }

    @Test
    void zeroBaselinePaysTheWholeHouseAndReseeds() {
        LotteryMath.PotOutcome.Payable outcome = payable(1, 500, 0);
        assertEquals(500, outcome.pot());
        assertEquals(RESEED, outcome.newHouseBalance());
        assertEquals(RESEED, outcome.newBaseline());
    }

    @Test
    void refusesInvalidAndDegenerateInputs() {
        assertReason(0, 1_000, 500, LotteryMath.RefusalReason.NO_ENTRANTS);
        assertReason(1, 0, 500, LotteryMath.RefusalReason.EMPTY_HOUSE);
        assertReason(1, 1_000, -1, LotteryMath.RefusalReason.INVALID_BASELINE);
        assertReason(1, 1_000, 1_000, LotteryMath.RefusalReason.NO_GROWTH);
        assertReason(1, 900, 1_000, LotteryMath.RefusalReason.NO_GROWTH);
    }

    @Test
    void usesExactIntegerArithmeticNearLongMaximum() {
        long balance = Long.MAX_VALUE - 1;
        long baseline = Long.MAX_VALUE / 2;
        LotteryMath.PotOutcome.Payable outcome = payable(1, balance, baseline);
        long expected = java.math.BigInteger.valueOf(balance - baseline)
                .multiply(java.math.BigInteger.valueOf(balance))
                .divide(java.math.BigInteger.valueOf(baseline))
                .longValueExact();
        assertEquals(expected, outcome.pot());
        assertEquals(RESEED, outcome.newHouseBalance());
    }

    @Test
    void reseedingBreaksTheRepeatedFullDrawLoop() {
        LotteryMath.PotOutcome.Payable first = payable(1, 20_000, 10_000);
        assertEquals(RESEED, first.newBaseline());
        assertReason(1, first.newHouseBalance(), first.newBaseline(), LotteryMath.RefusalReason.NO_GROWTH);
    }

    @Test
    void reproducesTheFailedDrawIncidentAndItsArmedFollowUp() {
        LotteryMath.PotOutcome.Payable failedDraw = payable(2, 114_130, 69_420);
        assertEquals(73_505, failedDraw.pot());
        assertEquals(40_625, failedDraw.newBaseline());
        assertEquals(false, failedDraw.capBound());

        LotteryMath.PotOutcome.Payable followUp = payable(1, 114_130, 40_625);
        assertEquals(114_130, followUp.pot());
        assertEquals(true, followUp.capBound());
    }

    @Test
    void ASubFullDrawBelowTheReseedAmountDoesNotReseedUntilTheNextCap() {
        LotteryMath.PotOutcome.Payable first = payable(1, 1_500, 1_000);
        assertEquals(750, first.pot());
        assertEquals(750, first.newHouseBalance());
        assertEquals(750, first.newBaseline());
        LotteryMath.PotOutcome.Payable second = payable(1, 1_500, 750);
        assertEquals(1_500, second.pot());
        assertEquals(RESEED, second.newBaseline());
        assertEquals(true, second.capBound());
    }

    private static LotteryMath.PotOutcome.Payable payable(int entrants, long balance, long baseline) {
        return assertInstanceOf(LotteryMath.PotOutcome.Payable.class,
                LotteryMath.calculate(entrants, balance, baseline, RESEED));
    }

    private static void assertReason(int entrants, long balance, long baseline,
                                     LotteryMath.RefusalReason reason) {
        LotteryMath.PotOutcome.Refused refused = assertInstanceOf(LotteryMath.PotOutcome.Refused.class,
                LotteryMath.calculate(entrants, balance, baseline, RESEED));
        assertEquals(reason, refused.reason());
    }
}
