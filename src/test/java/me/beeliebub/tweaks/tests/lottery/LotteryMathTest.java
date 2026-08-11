package me.beeliebub.tweaks.tests.lottery;

import me.beeliebub.tweaks.lottery.LotteryMath;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LotteryMathTest {

    private static final long FALLBACK = 10_000L;
    private static final double MULTIPLIER = 0.6D;

    @Test
    void calculatesTheConfiguredFormulaAndPostDrawBaseline() {
        LotteryMath.PotOutcome.Payable outcome = payable(5, 100_000, 80_000, FALLBACK, MULTIPLIER);

        assertEquals(12_600, outcome.pot());
        assertEquals(87_400, outcome.newHouseBalance());
        assertEquals(87_400, outcome.newBaseline());
    }

    @Test
    void entrantCountBonusScalesThePot() {
        LotteryMath.PotOutcome.Payable two = payable(2, 20_000, 10_000, 1_000, MULTIPLIER);
        LotteryMath.PotOutcome.Payable fifty = payable(50, 20_000, 10_000, 1_000, MULTIPLIER);

        assertEquals(6_120, two.pot());
        assertEquals(9_000, fifty.pot());
    }

    @Test
    void clampsThePotAtTheLiveFallbackFloor() {
        LotteryMath.PotOutcome.Payable outcome = payable(5, 100_000, 80_000, 95_000, MULTIPLIER);

        assertEquals(5_000, outcome.pot());
        assertEquals(95_000, outcome.newHouseBalance());
        assertEquals(95_000, outcome.newBaseline());
    }

    @Test
    void refusesFewerThanTwoEntrants() {
        assertReason(0, 100_000, 80_000, FALLBACK, MULTIPLIER,
                LotteryMath.RefusalReason.NOT_ENOUGH_ENTRANTS);
        assertReason(1, 100_000, 80_000, FALLBACK, MULTIPLIER,
                LotteryMath.RefusalReason.NOT_ENOUGH_ENTRANTS);
    }

    @Test
    void evaluatesRefusalsInTheSpecifiedOrder() {
        assertReason(1, 100_000, -1, FALLBACK, MULTIPLIER,
                LotteryMath.RefusalReason.NOT_ENOUGH_ENTRANTS);
        assertReason(2, 100_000, -1, FALLBACK, MULTIPLIER,
                LotteryMath.RefusalReason.INVALID_BASELINE);
        assertReason(2, 10_000, 10_000, 10_000, MULTIPLIER,
                LotteryMath.RefusalReason.HOUSE_AT_FLOOR);
        assertReason(2, 9_000, 10_000, 10_000, MULTIPLIER,
                LotteryMath.RefusalReason.HOUSE_AT_FLOOR);
        assertReason(2, 100_000, 100_000, FALLBACK, MULTIPLIER,
                LotteryMath.RefusalReason.NO_GROWTH);
    }

    @Test
    void rejectsNegativeFallbackAsAnInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> LotteryMath.calculate(2, 100_000, 80_000, -1, MULTIPLIER));
    }

    @Test
    void refusesWhenTheFlooredPotIsZero() {
        assertReason(2, 10_001, 10_000, FALLBACK, 0.0D,
                LotteryMath.RefusalReason.POT_ROUNDS_TO_ZERO);
    }

    @Test
    void usesExactBigDecimalArithmeticNearLongMaximum() {
        long balance = Long.MAX_VALUE - 1;
        long baseline = Long.MAX_VALUE / 2;
        int entrants = 2;

        LotteryMath.PotOutcome.Payable outcome = payable(entrants, balance, baseline, 0, MULTIPLIER);
        long expected = BigDecimal.valueOf(MULTIPLIER)
                .multiply(BigDecimal.valueOf(balance - baseline))
                .multiply(BigDecimal.ONE.add(new BigDecimal("0.01").multiply(BigDecimal.valueOf(entrants))))
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();

        assertEquals(expected, outcome.pot());
        assertEquals(balance - expected, outcome.newHouseBalance());
        assertEquals(balance - expected, outcome.newBaseline());
    }

    @Test
    void clampsAFormulaThatExceedsLongMaximumBeforeConverting() {
        LotteryMath.PotOutcome.Payable outcome = payable(Integer.MAX_VALUE, Long.MAX_VALUE, 0, 0, 10.0D);

        assertEquals(Long.MAX_VALUE, outcome.pot());
        assertEquals(0, outcome.newHouseBalance());
        assertEquals(0, outcome.newBaseline());
    }

    @Test
    void acceptsFiniteMultipliersAcrossTheConfiguredRange() {
        assertReason(2, 20_000, 10_000, 1_000, 0.0D,
                LotteryMath.RefusalReason.POT_ROUNDS_TO_ZERO);
        assertEquals(10_200, payable(2, 20_000, 10_000, 1_000, 1.0D).pot());
        assertEquals(12_240, payable(2, 20_000, 10_000, 1_000, 1.2D).pot());
    }

    @Test
    void rejectsNonFiniteOrNegativeMultipliers() {
        assertThrows(IllegalArgumentException.class,
                () -> LotteryMath.calculate(2, 20_000, 10_000, 1_000, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> LotteryMath.calculate(2, 20_000, 10_000, 1_000, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> LotteryMath.calculate(2, 20_000, 10_000, 1_000, -0.1D));
    }

    private static LotteryMath.PotOutcome.Payable payable(int entrants, long balance, long baseline,
                                                           long fallback, double multiplier) {
        return assertInstanceOf(LotteryMath.PotOutcome.Payable.class,
                LotteryMath.calculate(entrants, balance, baseline, fallback, multiplier));
    }

    private static void assertReason(int entrants, long balance, long baseline, long fallback,
                                     double multiplier, LotteryMath.RefusalReason reason) {
        LotteryMath.PotOutcome.Refused refused = assertInstanceOf(LotteryMath.PotOutcome.Refused.class,
                LotteryMath.calculate(entrants, balance, baseline, fallback, multiplier));
        assertEquals(reason, refused.reason());
    }
}
