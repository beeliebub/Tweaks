package me.beeliebub.tweaks.protection.ui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClaimPricing} is package-private, so this test lives beside production code rather than
 * under {@code tests/} — see the root {@code CLAUDE.md}'s Test Layout note. No MockBukkit needed:
 * {@link YamlConfiguration} is a plain data object.
 */
class ClaimPricingTest {

    @Test
    void defaultsReproduceCanonical5x5Equals89() {
        ClaimPricing pricing = ClaimPricing.from(new YamlConfiguration());
        assertEquals(89, ClaimSubcommand.computeClaimCost(25, pricing),
                "An empty config falls back to ClaimPricing.DEFAULT, which must reproduce the "
                        + "canonical 5x5 = 89 claim cost");
    }

    @Test
    void decayRateOfOneGivesLinearPricing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("protection.claim-cost.base", 10.0);
        config.set("protection.claim-cost.decay-rate", 1.0);
        config.set("protection.claim-cost.minimum-per-chunk", 1);
        ClaimPricing pricing = ClaimPricing.from(config);

        // Linear: every chunk costs exactly base (10), so 5 chunks = 50.
        assertEquals(50, ClaimSubcommand.computeClaimCost(5, pricing));
    }

    @Test
    void decayRateBelowOneFallsBackToDefault() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("protection.claim-cost.decay-rate", 0.5);
        ClaimPricing pricing = ClaimPricing.from(config);

        assertEquals(ClaimPricing.DEFAULT.decayRate(), pricing.decayRate(),
                "decay-rate < 1.0 would invert the pricing formula's intent and must fall back");
    }

    @Test
    void nonPositiveBaseFallsBackToDefault() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("protection.claim-cost.base", 0.0);
        ClaimPricing pricing = ClaimPricing.from(config);

        assertEquals(ClaimPricing.DEFAULT.base(), pricing.base());
    }

    @Test
    void minimumPerChunkBelowOneFallsBackToDefault() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("protection.claim-cost.minimum-per-chunk", 0);
        ClaimPricing pricing = ClaimPricing.from(config);

        assertEquals(ClaimPricing.DEFAULT.minimumPerChunk(), pricing.minimumPerChunk());
    }

    // core/config/ConfigRegistry bounds minimum-per-chunk to [1, Integer.MAX_VALUE] with no upper
    // cap tied to max_chunks, so an admin-set extreme value (still fully valid under /tconfig's own
    // validation) could overflow a plain `int` accumulator and wrap a multi-chunk claim's cost to
    // zero or negative - i.e. free land. computeClaimCost accumulates in `long` and clamps the
    // result at Integer.MAX_VALUE instead of wrapping; this test pins that guarantee.
    @Test
    void extremeMinimumPerChunkNeverWrapsToAFreeOrNegativeCost() {
        ClaimPricing extreme = new ClaimPricing(10.0, 1.1, 1_073_741_824); // 2^30
        int cost = ClaimSubcommand.computeClaimCost(4, extreme);

        assertTrue(cost > 0, "a claim cost must never wrap to zero or negative regardless of "
                + "how extreme an admin-configured minimum-per-chunk is");
        assertEquals(Integer.MAX_VALUE, cost,
                "an overflowing total must clamp to Integer.MAX_VALUE, not wrap");
    }

    @Test
    void extremeBaseNeverWrapsToAFreeOrNegativeCost() {
        ClaimPricing extreme = new ClaimPricing(Double.MAX_VALUE, 1.0, 1);
        int cost = ClaimSubcommand.computeClaimCost(2, extreme);

        assertTrue(cost > 0);
        assertEquals(Integer.MAX_VALUE, cost);
    }
}
