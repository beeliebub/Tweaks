package me.beeliebub.tweaks.minigames.roulette;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Placed in the production package (no {@code tests.} prefix) so package-private access to
 * {@link RouletteSpinMath}'s statics is legal without widening production visibility — mirrors the
 * exception already documented for {@code RouletteGeometryTest}. Pure math, no MockBukkit needed.
 */
class RouletteSpinMathTest {

    private static final double TOLERANCE = 1e-9;

    // ---- easeOutCubic: exact endpoints ----

    @Test
    void easeOutCubicIsExactlyZeroAtZero() {
        assertEquals(0.0, RouletteSpinMath.easeOutCubic(0.0));
    }

    @Test
    void easeOutCubicIsExactlyOneAtOne() {
        assertEquals(1.0, RouletteSpinMath.easeOutCubic(1.0));
    }

    // ---- wheelAngleDeg: the whole-number-of-rotations hard rule, as an assertion ----

    @Test
    void wheelAngleAtFinalFrameIsExactlyWholeTurns() {
        int totalFrames = 80;
        int turns = 3;
        double angle = RouletteSpinMath.wheelAngleDeg(totalFrames, totalFrames, turns);
        assertEquals(360.0 * turns, angle);
        assertEquals(0.0, angle % 360.0);
    }

    @Test
    void wheelAngleIsMonotonicNonDecreasingAndNeverOvershoots() {
        int totalFrames = 80;
        int turns = 3;
        double previous = -1;
        for (int frame = 0; frame <= totalFrames; frame++) {
            double angle = RouletteSpinMath.wheelAngleDeg(frame, totalFrames, turns);
            assertTrue(angle >= previous, "wheel angle must never decrease frame-to-frame");
            assertTrue(angle <= 360.0 * turns + TOLERANCE, "wheel angle must never overshoot the total sweep");
            previous = angle;
        }
    }

    // ---- rotateAboutY ----

    @Test
    void rotateAboutYByZeroDegreesIsIdentity() {
        double[] result = RouletteSpinMath.rotateAboutY(5.0, -3.0, 1.0, 1.0, 0.0);
        assertEquals(5.0, result[0], TOLERANCE);
        assertEquals(-3.0, result[1], TOLERANCE);
    }

    @Test
    void rotateAboutYByFullCircleReturnsToStart() {
        double[] result = RouletteSpinMath.rotateAboutY(5.0, -3.0, 1.0, 1.0, 360.0);
        assertEquals(5.0, result[0], 1e-6);
        assertEquals(-3.0, result[1], 1e-6);
    }

    @Test
    void rotateAboutYPreservesRadiusForASpreadOfPoints() {
        double cx = 7.5, cz = 9.5, radius = 6.09;
        for (double angle = 0; angle < 360; angle += 17.0) {
            double x = cx + radius * Math.cos(Math.toRadians(angle));
            double z = cz + radius * Math.sin(Math.toRadians(angle));
            double[] rotated = RouletteSpinMath.rotateAboutY(x, z, cx, cz, 41.0);
            double newRadius = Math.hypot(rotated[0] - cx, rotated[1] - cz);
            // 1e-6, not 1e-9: HotSpot's optimized trig intrinsics trade some precision for speed
            // once JIT-compiled, and this round-trips through cos/sin/atan2-shaped math several
            // times — matches rotateAboutYByFullCircleReturnsToStart's tolerance for the same reason.
            assertEquals(radius, newRadius, 1e-6);
        }
    }

    @Test
    void rotatingThenUnrotatingRoundTrips() {
        double cx = 2.0, cz = -4.0;
        double x = 9.3, z = 1.2;
        double[] forward = RouletteSpinMath.rotateAboutY(x, z, cx, cz, 73.0);
        double[] back = RouletteSpinMath.rotateAboutY(forward[0], forward[1], cx, cz, -73.0);
        // 1e-6, matching rotateAboutYByFullCircleReturnsToStart's tolerance — see that test for why.
        assertEquals(x, back[0], 1e-6);
        assertEquals(z, back[1], 1e-6);
    }

    // ---- ballSweepDeg ----

    @Test
    void ballSweepLandsExactlyOnEndAngleForASpreadOfPairs() {
        int turns = 7;
        for (double start = 0; start < 360; start += 23.0) {
            for (double end = 0; end < 360; end += 41.0) {
                double sweep = RouletteSpinMath.ballSweepDeg(start, end, turns);
                double landed = RouletteSpinMath.normalize360(start - sweep);
                assertEquals(RouletteSpinMath.normalize360(end), landed, 1e-6);
            }
        }
    }

    @Test
    void ballSweepIsNeverZeroWhenStartEqualsEnd() {
        double sweep = RouletteSpinMath.ballSweepDeg(90.0, 90.0, 7);
        assertEquals(360.0 * 7, sweep, 1e-9);
    }

    // ---- nearestIndexByAngle ----

    @Test
    void nearestIndexByAngleFindsTheClosestEntry() {
        double[] angles = { 0.0, 90.0, 180.0, 270.0 };
        assertEquals(0, RouletteSpinMath.nearestIndexByAngle(10.0, angles));
        assertEquals(1, RouletteSpinMath.nearestIndexByAngle(95.0, angles));
        assertEquals(2, RouletteSpinMath.nearestIndexByAngle(179.0, angles));
    }

    @Test
    void nearestIndexByAngleWrapsAcrossTheSeam() {
        double[] angles = { 0.0, 90.0, 180.0, 270.0 };
        assertEquals(0, RouletteSpinMath.nearestIndexByAngle(355.0, angles),
                "355 degrees is closer to 0 (distance 5, via wraparound) than to 270 (distance 85)");
        assertEquals(3, RouletteSpinMath.nearestIndexByAngle(285.0, angles),
                "285 degrees is closer to 270 (distance 15) than to 0 (distance 75)");
    }

    // ---- yawAfter ----

    @Test
    void yawAfterSubtractsTheta() {
        assertEquals(10.0f, RouletteSpinMath.yawAfter(50.0f, 40.0));
        assertEquals(-30.0f, RouletteSpinMath.yawAfter(0.0f, 30.0));
    }

    // ---- normalize360 ----

    @Test
    void normalize360WrapsNegativeAndOverflowValues() {
        assertEquals(350.0, RouletteSpinMath.normalize360(-10.0), TOLERANCE);
        assertEquals(10.0, RouletteSpinMath.normalize360(370.0), TOLERANCE);
        assertEquals(0.0, RouletteSpinMath.normalize360(360.0), TOLERANCE);
    }
}
