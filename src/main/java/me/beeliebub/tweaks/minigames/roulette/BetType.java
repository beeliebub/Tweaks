package me.beeliebub.tweaks.minigames.roulette;

/**
 * The three bet families that exist on the physical board — no odd/even, splits, or columns.
 * {@link #STRAIGHT}'s constant here is the standard 1-36 payout; pocket 0 (Green) pays a
 * different rate — see {@link RouletteBet#GREEN_PAYOUT_MULTIPLIER} and
 * {@link RouletteBet#payoutMultiplierFor(BetType, int)} for the selector-aware lookup.
 */
public enum BetType {
    STRAIGHT(36),
    DOZEN(3),
    COLOR(2);

    private final int payoutMultiplier;

    BetType(int payoutMultiplier) {
        this.payoutMultiplier = payoutMultiplier;
    }

    /**
     * Winnings multiplier (e.g. 36 for a 36:1 straight-up on 1-36) — the wagered stake itself is
     * never returned, even on a win, so a winning bet's total credit is {@code amount *
     * payoutMultiplier()} and nothing more. Does not account for the pocket-0 Green override on
     * {@link #STRAIGHT} — use {@link RouletteBet#payoutMultiplierFor(BetType, int)} when a
     * selector is available.
     */
    public int payoutMultiplier() {
        return payoutMultiplier;
    }
}
