package me.beeliebub.tweaks.minigames.roulette;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Placed in the production package (no {@code tests.} prefix) so package-private access to
 * {@link RouletteGeometry}'s statics and records is legal without widening production visibility —
 * mirrors the exception already documented in the root {@code CLAUDE.md}'s Test Layout section for
 * {@code BlackjackTableStoreTest}. Pure math, no MockBukkit needed.
 */
class RouletteGeometryTest {

    private static final double TOLERANCE = 1e-6;

    @Test
    void kasaFitRecoversKnownCircleWithUnevenSpacing() {
        double centerX = 12.5;
        double centerZ = -7.25;
        double radius = 3.4;
        int n = 37;

        double[] xs = new double[n];
        double[] zs = new double[n];
        for (int i = 0; i < n; i++) {
            // Deliberately uneven angular spacing (not a uniform circle) to prove the Kåsa fit
            // isn't just recovering the arithmetic mean of evenly-spaced points.
            double angle = Math.toRadians((i * 360.0 / n) + (i % 3) * 0.7);
            xs[i] = centerX + radius * Math.cos(angle);
            zs[i] = centerZ + radius * Math.sin(angle);
        }

        RouletteGeometry.CircleFit fit = RouletteGeometry.kasaFit(xs, zs);

        assertEquals(centerX, fit.centerX(), 1e-4);
        assertEquals(centerZ, fit.centerZ(), 1e-4);
        assertEquals(radius, fit.radius(), 1e-4);
        assertTrue(fit.rmsResidual() < 1e-4);
    }

    @Test
    void meanEstimatorAgreesWithKasaOnEvenlySpacedCircle() {
        double centerX = 0.0;
        double centerZ = 0.0;
        double radius = 5.0;
        int n = 37;

        double[] xs = new double[n];
        double[] zs = new double[n];
        for (int i = 0; i < n; i++) {
            double angle = Math.toRadians(i * 360.0 / n);
            xs[i] = centerX + radius * Math.cos(angle);
            zs[i] = centerZ + radius * Math.sin(angle);
        }

        RouletteGeometry.CircleFit mean = RouletteGeometry.meanEstimatorFit(xs, zs);
        RouletteGeometry.CircleFit kasa = RouletteGeometry.kasaFit(xs, zs);

        assertEquals(kasa.centerX(), mean.centerX(), 1e-3);
        assertEquals(kasa.centerZ(), mean.centerZ(), 1e-3);
        assertEquals(kasa.radius(), mean.radius(), 1e-3);
    }

    @Test
    void angleDegreesNormalizesToZeroThreeSixty() {
        assertEquals(0.0, RouletteGeometry.angleDegrees(1, 0, 0, 0), TOLERANCE);
        assertEquals(90.0, RouletteGeometry.angleDegrees(0, 1, 0, 0), TOLERANCE);
        assertEquals(180.0, RouletteGeometry.angleDegrees(-1, 0, 0, 0), TOLERANCE);
        assertEquals(270.0, RouletteGeometry.angleDegrees(0, -1, 0, 0), TOLERANCE);
    }

    @Test
    void gapStatsWrapsAroundAndSumsToFullCircle() {
        double[] angles = {0, 90, 180, 270};
        RouletteGeometry.GapStats stats = RouletteGeometry.gapStats(angles);

        double sum = 0;
        for (double gap : stats.gapsDegrees()) sum += gap;
        assertEquals(360.0, sum, TOLERANCE);
        assertEquals(90.0, stats.min(), TOLERANCE);
        assertEquals(90.0, stats.max(), TOLERANCE);
        assertEquals(90.0, stats.median(), TOLERANCE);
        assertEquals(0.0, stats.stddev(), TOLERANCE);
    }

    @Test
    void oppositePairCheckCoversFloorOfHalfTheSegments() {
        int n = 37;
        double[] xs = new double[n];
        double[] zs = new double[n];
        for (int i = 0; i < n; i++) {
            double angle = Math.toRadians(i * 360.0 / n);
            xs[i] = Math.cos(angle);
            zs[i] = Math.sin(angle);
        }

        RouletteGeometry.OppositePairCheck check = RouletteGeometry.oppositePairCheck(xs, zs);

        // 37 is odd, so index i and i+18 are ~175.1deg apart, not exactly 180 - the midpoints
        // cluster near the origin but aren't exactly on it. Bound is well under the unit radius.
        assertEquals(18, check.pairCount());
        assertTrue(check.spreadStddev() < 0.1, "expected small spread, got " + check.spreadStddev());
    }

    @Test
    void chordWidthMatchesStraightChordLength() {
        double radius = 10.0;
        double gapRadians = Math.toRadians(90.0);
        double expected = radius * Math.sqrt(2);

        assertEquals(expected, RouletteGeometry.chordWidth(radius, gapRadians), 1e-6);
    }

    @Test
    void rotateByIdentityQuaternionIsNoOp() {
        RouletteGeometry.Vec3 v = new RouletteGeometry.Vec3(1, 2, 3);
        RouletteGeometry.Quat identity = new RouletteGeometry.Quat(0, 0, 0, 1);

        RouletteGeometry.Vec3 rotated = RouletteGeometry.rotate(v, identity);

        assertEquals(v.x(), rotated.x(), TOLERANCE);
        assertEquals(v.y(), rotated.y(), TOLERANCE);
        assertEquals(v.z(), rotated.z(), TOLERANCE);
    }

    @Test
    void rotateNinetyDegreesAroundYMapsXOntoNegativeZ() {
        RouletteGeometry.Vec3 v = new RouletteGeometry.Vec3(1, 0, 0);
        double half = Math.toRadians(90.0) / 2.0;
        RouletteGeometry.Quat yaw90 = new RouletteGeometry.Quat(0, Math.sin(half), 0, Math.cos(half));

        RouletteGeometry.Vec3 rotated = RouletteGeometry.rotate(v, yaw90);

        assertEquals(0.0, rotated.x(), TOLERANCE);
        assertEquals(0.0, rotated.y(), TOLERANCE);
        assertEquals(-1.0, rotated.z(), TOLERANCE);
    }

    @Test
    void stepAxisAngleRecoversKnownYAxisRotation() {
        double stepDegrees = 9.7297;
        double half = Math.toRadians(stepDegrees) / 2.0;
        RouletteGeometry.Quat from = new RouletteGeometry.Quat(0, 0, 0, 1);
        RouletteGeometry.Quat to = new RouletteGeometry.Quat(0, Math.sin(half), 0, Math.cos(half));

        RouletteGeometry.AxisAngleDeg step = RouletteGeometry.stepAxisAngle(from, to);

        assertEquals(stepDegrees, step.angleDegrees(), 1e-3);
        assertEquals(0.0, step.axisX(), TOLERANCE);
        assertEquals(1.0, step.axisY(), TOLERANCE);
        assertEquals(0.0, step.axisZ(), TOLERANCE);
    }

    @Test
    void correctCentroidAccountsForRightRotationUnlikePlanCentroid() {
        RouletteGeometry.Vec3 entityPos = new RouletteGeometry.Vec3(0, 0, 0);
        RouletteGeometry.Vec3 translation = new RouletteGeometry.Vec3(0, 0, 0);
        RouletteGeometry.Vec3 scale = new RouletteGeometry.Vec3(1, 1, 1);
        RouletteGeometry.Quat identity = new RouletteGeometry.Quat(0, 0, 0, 1);
        // A 180-degree rightRotation about Y flips the local centre from (0.5,0.5,0.5) to (-0.5,0.5,-0.5).
        RouletteGeometry.Quat yaw180 = new RouletteGeometry.Quat(0, 1, 0, 0);

        RouletteGeometry.Vec3 plan = RouletteGeometry.planCentroid(entityPos, translation, scale, identity);
        RouletteGeometry.Vec3 correct = RouletteGeometry.correctCentroid(entityPos, translation, scale, identity, yaw180);

        assertEquals(new RouletteGeometry.Vec3(0.5, 0.5, 0.5), plan);
        assertEquals(-0.5, correct.x(), TOLERANCE);
        assertEquals(0.5, correct.y(), TOLERANCE);
        assertEquals(-0.5, correct.z(), TOLERANCE);
    }
}
