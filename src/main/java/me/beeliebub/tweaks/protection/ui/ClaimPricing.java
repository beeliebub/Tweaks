package me.beeliebub.tweaks.protection.ui;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * The chunk-claim pricing formula's tunable knobs, read live from {@code protection.claim-cost} on
 * every claim rather than cached, so a {@code /tconfig} edit takes effect on the very next
 * {@code /region claim}. {@link #DEFAULT} reproduces today's hardcoded formula exactly (base 10.0,
 * decay-rate 1.1, minimum-per-chunk 1) — the canonical 5x5 = 89 claim cost pinned by
 * {@code ClaimCostTest}.
 *
 * <p>{@link #from(FileConfiguration)} defensively falls back to {@link #DEFAULT} field-by-field for
 * a hand-edited config.yml value that would invert the pricing formula's intent
 * ({@code decayRate < 1.0} makes cost grow unbounded instead of tapering, and can overflow on a
 * large claim) or make it non-positive — {@code core/config/ConfigValueEditor}'s bounded-number
 * validation already rejects these at the {@code /tconfig} edit boundary, so this only fires for a
 * value that predates that validation or was hand-edited directly.
 */
record ClaimPricing(double base, double decayRate, int minimumPerChunk) {

    static final ClaimPricing DEFAULT = new ClaimPricing(10.0, 1.1, 1);

    static ClaimPricing from(FileConfiguration config) {
        double base = config.getDouble("protection.claim-cost.base", DEFAULT.base());
        double decayRate = config.getDouble("protection.claim-cost.decay-rate", DEFAULT.decayRate());
        int minimumPerChunk = config.getInt("protection.claim-cost.minimum-per-chunk", DEFAULT.minimumPerChunk());

        if (Double.isNaN(base) || Double.isInfinite(base) || base <= 0.0) {
            base = DEFAULT.base();
        }
        if (Double.isNaN(decayRate) || Double.isInfinite(decayRate) || decayRate < 1.0) {
            decayRate = DEFAULT.decayRate();
        }
        if (minimumPerChunk < 1) {
            minimumPerChunk = DEFAULT.minimumPerChunk();
        }
        return new ClaimPricing(base, decayRate, minimumPerChunk);
    }
}
