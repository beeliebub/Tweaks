package me.beeliebub.tweaks.minigames.roulette;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouletteHitboxManagerTest {

    @Test
    void hitboxUsesMarkerScaleAndCentersOnMarkerSpawnHeight() {
        double centroidY = 12.75;

        assertEquals(RouletteRenderer.MARKER_SCALE, RouletteHitboxManager.hitboxSize());
        assertEquals(centroidY + RouletteRenderer.SEGMENT_MARKER_LIFT,
                RouletteHitboxManager.hitboxAnchorY(centroidY) + RouletteHitboxManager.hitboxSize() / 2.0,
                1.0e-9);
    }
}
