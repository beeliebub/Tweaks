package me.beeliebub.tweaks.lottery;

import java.math.BigInteger;

/** Pure pot calculation for the server-wide lottery. */
public final class LotteryMath {

    private LotteryMath() {
    }

    public enum RefusalReason {
        NO_ENTRANTS,
        NO_GROWTH,
        EMPTY_HOUSE,
        INVALID_BASELINE,
        POT_ROUNDS_TO_ZERO
    }

    public sealed interface PotOutcome permits PotOutcome.Payable, PotOutcome.Refused {
        record Payable(long pot, long newHouseBalance, long newBaseline, boolean capBound)
                implements PotOutcome {
        }

        record Refused(RefusalReason reason) implements PotOutcome {
        }
    }

    public static PotOutcome calculate(int entrantCount, long balance, long baseline, long reseedAmount) {
        if (entrantCount < 1) return new PotOutcome.Refused(RefusalReason.NO_ENTRANTS);
        if (balance <= 0) return new PotOutcome.Refused(RefusalReason.EMPTY_HOUSE);
        if (baseline < 0) return new PotOutcome.Refused(RefusalReason.INVALID_BASELINE);
        if (reseedAmount < 1) throw new IllegalArgumentException("reseedAmount must be positive");

        long pot;
        boolean capBound;
        if (baseline == 0) {
            pot = balance;
            capBound = true;
        } else {
            long growth = balance - baseline;
            if (growth <= 0) return new PotOutcome.Refused(RefusalReason.NO_GROWTH);
            long ratioNumerator = Math.min(growth, baseline);
            capBound = growth >= baseline;
            pot = BigInteger.valueOf(ratioNumerator)
                    .multiply(BigInteger.valueOf(balance))
                    .divide(BigInteger.valueOf(baseline))
                    .longValueExact();
        }

        if (pot < 1) return new PotOutcome.Refused(RefusalReason.POT_ROUNDS_TO_ZERO);
        if (pot > balance) throw new ArithmeticException("Lottery pot exceeded the House balance");
        if (capBound) return new PotOutcome.Payable(pot, reseedAmount, reseedAmount, true);
        long newBalance = balance - pot;
        return new PotOutcome.Payable(pot, newBalance, newBalance, false);
    }
}
