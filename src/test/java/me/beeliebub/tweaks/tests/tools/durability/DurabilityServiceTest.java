package me.beeliebub.tweaks.tests.tools.durability;

import me.beeliebub.tweaks.tools.durability.DurabilityService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurabilityServiceTest {

    @Test
    void defaultMultiplierAndTierStepMatchThePlannedCurve() {
        assertEquals(300, DurabilityService.maxDamageFor(100, 3.0, 0));
        assertEquals(270, DurabilityService.maxDamageFor(100, 3.0, 1));
        assertEquals(30, DurabilityService.maxDamageFor(100, 3.0, 9));
    }

    @Test
    void customTierStepIsAppliedWithoutChangingTheAnchoredMultiplier() {
        assertEquals(240, DurabilityService.maxDamageFor(100, 3.0, 2, 10.0));
        assertEquals(225, DurabilityService.maxDamageFor(100, 3.0, 2, 12.5));
    }
}
