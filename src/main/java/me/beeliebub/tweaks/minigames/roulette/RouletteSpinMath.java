package me.beeliebub.tweaks.minigames.roulette;

/**
 * Pure math for the spin animation — easing, wheel/ball angle-at-frame, position rotation about
 * a centre, and travelling-glow pocket resolution. No Bukkit types anywhere in this class, so it is
 * unit-testable without MockBukkit. See {@code roulette/CLAUDE.md}'s rotation contract section for
 * why this rotates entity position <em>and</em> yaw rather than the builder's {@code Transformation}.
 */
final class RouletteSpinMath {

    private RouletteSpinMath() {}

    /** Cubic ease-out: {@code ease(0) == 0} and {@code ease(1) == 1} exactly. */
    static double easeOutCubic(double u) {
        double k = 1.0 - u;
        return 1.0 - k * k * k;
    }

    /**
     * The wheel's absolute rotation angle (degrees, about the fit centre) at {@code frame} of
     * {@code totalFrames}, sweeping exactly {@code turns} full rotations. At {@code frame ==
     * totalFrames} this is exactly {@code 360.0 * turns} — a whole number of full rotations by
     * construction of {@link #easeOutCubic}'s exact endpoint, not by convention.
     */
    static double wheelAngleDeg(int frame, int totalFrames, int turns) {
        double u = Math.max(0.0, Math.min(1.0, (double) frame / totalFrames));
        return 360.0 * turns * easeOutCubic(u);
    }

    /**
     * Rotates the point {@code (x, z)} about centre {@code (cx, cz)} by {@code thetaDeg} degrees
     * counter-clockwise (standard math convention). Returns {@code [x', z']}.
     */
    static double[] rotateAboutY(double x, double z, double cx, double cz, double thetaDeg) {
        double r = Math.toRadians(thetaDeg);
        double dx = x - cx;
        double dz = z - cz;
        double c = Math.cos(r);
        double s = Math.sin(r);
        return new double[] { cx + dx * c - dz * s, cz + dx * s + dz * c };
    }

    /**
     * Minecraft yaw is clockwise-from-south; a {@code +thetaDeg} counter-clockwise world rotation
     * corresponds to a {@code -thetaDeg} yaw delta.
     */
    static float yawAfter(float restYaw, double thetaDeg) {
        return (float) (restYaw - thetaDeg);
    }

    /**
     * Total sweep (degrees, always positive) so a ball travelling clockwise from
     * {@code startAngleDeg} completes exactly {@code turns} full orbits and lands precisely on
     * {@code endAngleDeg} (mod 360). {@code turns} must be {@code >= 1} so the result is never a
     * bare {@code 0} when {@code startAngleDeg == endAngleDeg}.
     */
    static double ballSweepDeg(double startAngleDeg, double endAngleDeg, int turns) {
        double delta = normalize360(startAngleDeg - endAngleDeg);
        return 360.0 * turns + delta;
    }

    /** Index into {@code anglesDegAscending} nearest {@code angleDeg}, wrapping across the 0/360 seam. */
    static int nearestIndexByAngle(double angleDeg, double[] anglesDegAscending) {
        double target = normalize360(angleDeg);
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < anglesDegAscending.length; i++) {
            double dist = angularDistance(target, anglesDegAscending[i]);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private static double angularDistance(double a, double b) {
        double diff = Math.abs(normalize360(a) - normalize360(b)) % 360.0;
        return Math.min(diff, 360.0 - diff);
    }

    static double normalize360(double degrees) {
        double d = degrees % 360.0;
        if (d < 0) d += 360.0;
        return d;
    }
}
