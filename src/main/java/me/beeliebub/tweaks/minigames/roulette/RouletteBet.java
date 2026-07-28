package me.beeliebub.tweaks.minigames.roulette;

import java.util.Objects;
import java.util.UUID;

/**
 * One wager. {@code selector} meaning depends on {@code type}:
 * <ul>
 *   <li>{@link BetType#STRAIGHT} — pocket number, 0-36.</li>
 *   <li>{@link BetType#DOZEN} — 1, 2, or 3.</li>
 *   <li>{@link BetType#COLOR} — {@link #COLOR_RED} or {@link #COLOR_BLACK}.</li>
 * </ul>
 * There is no zero-stake path (no free/practice board exists) and no bet-removal method anywhere
 * in this feature — a placed bet is final.
 */
public record RouletteBet(UUID player, BetType type, int selector, int amount) {

    public static final int COLOR_RED = 0;
    public static final int COLOR_BLACK = 1;

    /** Payout multiplier for a {@link BetType#STRAIGHT} bet on pocket 0 (Green) — 50:1, overriding
     *  {@link BetType#STRAIGHT}'s standard 36:1 rate for every other pocket. */
    public static final int GREEN_PAYOUT_MULTIPLIER = 50;

    public RouletteBet {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(type, "type");
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be >= 1, was " + amount);
        }
        validateSelector(type, selector);
    }

    private static void validateSelector(BetType type, int selector) {
        switch (type) {
            case STRAIGHT -> {
                if (selector < 0 || selector > 36) {
                    throw new IllegalArgumentException("STRAIGHT selector must be 0-36, was " + selector);
                }
            }
            case DOZEN -> {
                if (selector < 1 || selector > 3) {
                    throw new IllegalArgumentException("DOZEN selector must be 1-3, was " + selector);
                }
            }
            case COLOR -> {
                if (selector != COLOR_RED && selector != COLOR_BLACK) {
                    throw new IllegalArgumentException(
                            "COLOR selector must be COLOR_RED or COLOR_BLACK, was " + selector);
                }
            }
        }
    }

    /** Winnings multiplier for this bet, accounting for the pocket-0 Green override. */
    public int payoutMultiplier() {
        return payoutMultiplierFor(type, selector);
    }

    /**
     * Winnings multiplier for a bet of {@code type} on {@code selector}, excluding the returned
     * stake. Pulled out of the instance method so callers that only have a type/selector pair
     * (not a constructed {@link RouletteBet}) — e.g. a segment label — compute the exact same
     * number the settlement math will use, rather than reading {@link BetType#payoutMultiplier()}
     * directly and silently missing the Green override.
     */
    public static int payoutMultiplierFor(BetType type, int selector) {
        if (type == BetType.STRAIGHT && selector == 0) {
            return GREEN_PAYOUT_MULTIPLIER;
        }
        return type.payoutMultiplier();
    }

    /**
     * True if this bet wins on the drawn {@code pocket}. Pocket 0 loses every {@code DOZEN}/
     * {@code COLOR} bet; a {@code STRAIGHT} bet placed on 0 itself still wins (at the Green rate).
     */
    public boolean wins(int pocket) {
        return switch (type) {
            case STRAIGHT -> selector == pocket;
            case DOZEN -> pocket != 0 && RouletteWheel.dozenOf(pocket) == selector;
            case COLOR -> pocket != 0 && matchesColor(RouletteWheel.colorOf(pocket));
        };
    }

    private boolean matchesColor(RouletteWheel.PocketColor color) {
        return (selector == COLOR_RED && color == RouletteWheel.PocketColor.RED)
                || (selector == COLOR_BLACK && color == RouletteWheel.PocketColor.BLACK);
    }
}
