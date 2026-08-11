package me.beeliebub.tweaks.lottery;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure pot calculation for the server-wide lottery. */
public final class LotteryMath {

    private static final BigDecimal ENTRY_BONUS_RATE = new BigDecimal("0.01");

    private LotteryMath() {
    }

    public enum RefusalReason {
        NOT_ENOUGH_ENTRANTS,
        NO_GROWTH,
        HOUSE_AT_FLOOR,
        INVALID_BASELINE,
        POT_ROUNDS_TO_ZERO
    }

    public sealed interface PotOutcome permits PotOutcome.Payable, PotOutcome.Refused {
        record Payable(long pot, long newHouseBalance, long newBaseline)
                implements PotOutcome {
        }

        record Refused(RefusalReason reason) implements PotOutcome {
        }
    }

    public static PotOutcome calculate(int entrantCount, long balance, long baseline,
                                       long fallback, double potMultiplier) {
        if (entrantCount < 2) return new PotOutcome.Refused(RefusalReason.NOT_ENOUGH_ENTRANTS);
        if (baseline < 0) return new PotOutcome.Refused(RefusalReason.INVALID_BASELINE);
        if (fallback < 0) throw new IllegalArgumentException("fallback must not be negative");
        if (!Double.isFinite(potMultiplier) || potMultiplier < 0) {
            throw new IllegalArgumentException("potMultiplier must be finite and non-negative");
        }
        if (balance <= fallback) return new PotOutcome.Refused(RefusalReason.HOUSE_AT_FLOOR);

        long growth = balance - baseline;
        if (growth <= 0) return new PotOutcome.Refused(RefusalReason.NO_GROWTH);

        BigDecimal rawPot = BigDecimal.valueOf(potMultiplier)
                .multiply(BigDecimal.valueOf(growth))
                .multiply(BigDecimal.ONE.add(ENTRY_BONUS_RATE.multiply(BigDecimal.valueOf(entrantCount))));
        BigDecimal flooredPot = rawPot.setScale(0, RoundingMode.FLOOR);
        long potRaw = flooredPot.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
                ? Long.MAX_VALUE : flooredPot.longValueExact();

        long headroom = balance - fallback;
        long pot = Math.min(potRaw, headroom);
        if (pot < 1) return new PotOutcome.Refused(RefusalReason.POT_ROUNDS_TO_ZERO);
        if (pot > balance) throw new ArithmeticException("Lottery pot exceeded the House balance");
        long newBalance = balance - pot;
        return new PotOutcome.Payable(pot, newBalance, newBalance);
    }
}
