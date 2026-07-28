package me.beeliebub.tweaks.minigames.roulette;

import java.util.Arrays;

/**
 * Pure geometry math for the board-scan diagnostic — no Bukkit or JOML types anywhere in this
 * class, so it is unit-testable without MockBukkit. {@link RouletteScanCommand} is responsible for
 * copying the live, mutable Bukkit/JOML values into {@link Vec3}/{@link Quat} once at capture time
 * before calling into this class (see the read-only warning on {@code ScannedSegment}).
 *
 * <p>This is diagnostic-only code (see the package {@code CLAUDE.md}) — production board
 * scanning is not built on top of this class, though it does call {@link #correctCentroid}
 * directly, since that specific formula was already verified against the live board.
 */
final class RouletteGeometry {

    private RouletteGeometry() {}

    // ---------------------------------------------------------------- Vectors & quaternions

    record Vec3(double x, double y, double z) {}

    /** A unit quaternion in (x, y, z, w) order, matching JOML's {@code Quaternionf} field order. */
    record Quat(double x, double y, double z, double w) {}

    static Vec3 add(Vec3 a, Vec3 b) {
        return new Vec3(a.x() + b.x(), a.y() + b.y(), a.z() + b.z());
    }

    static Vec3 scale(Vec3 v, double s) {
        return new Vec3(v.x() * s, v.y() * s, v.z() * s);
    }

    /** Componentwise multiply (Hadamard product), used to apply a display's {@code scale} vector. */
    static Vec3 multiply(Vec3 a, Vec3 b) {
        return new Vec3(a.x() * b.x(), a.y() * b.y(), a.z() * b.z());
    }

    /** Rotates {@code v} by unit quaternion {@code q}. */
    static Vec3 rotate(Vec3 v, Quat q) {
        double qx = q.x(), qy = q.y(), qz = q.z(), qw = q.w();
        // t = 2 * cross(q.xyz, v)
        double tx = 2 * (qy * v.z() - qz * v.y());
        double ty = 2 * (qz * v.x() - qx * v.z());
        double tz = 2 * (qx * v.y() - qy * v.x());
        // v' = v + qw * t + cross(q.xyz, t)
        double cx = qy * tz - qz * ty;
        double cy = qz * tx - qx * tz;
        double cz = qx * ty - qy * tx;
        return new Vec3(v.x() + qw * tx + cx, v.y() + qw * ty + cy, v.z() + qw * tz + cz);
    }

    // ---------------------------------------------------------------- Centroid formulas

    /**
     * The originally proposed centroid formula, kept only so the scan can report whether it agrees
     * with {@link #correctCentroid} for this specific build — see the package CLAUDE.md for why the
     * two can disagree (translation applied inside the rotation, rightRotation dropped).
     */
    static Vec3 planCentroid(Vec3 entityPos, Vec3 translation, Vec3 scale, Quat leftRotation) {
        Vec3 inner = add(translation, scale(scale, 0.5));
        return add(entityPos, rotate(inner, leftRotation));
    }

    /**
     * The correct world-space centroid of a display's local model centre {@code (0.5,0.5,0.5)},
     * following Bukkit/Paper's documented transform order:
     * {@code translate(translation) . rotate(leftRotation) . scale(scale) . rotate(rightRotation)}.
     */
    static Vec3 correctCentroid(Vec3 entityPos, Vec3 translation, Vec3 scale, Quat leftRotation, Quat rightRotation) {
        Vec3 localCentre = new Vec3(0.5, 0.5, 0.5);
        Vec3 rotatedByRight = rotate(localCentre, rightRotation);
        Vec3 scaled = multiply(scale, rotatedByRight);
        Vec3 rotatedByLeft = rotate(scaled, leftRotation);
        return add(entityPos, add(translation, rotatedByLeft));
    }

    // ---------------------------------------------------------------- Circle fitting

    record CircleFit(double centerX, double centerZ, double radius, double rmsResidual) {}

    /** Baseline estimator: arithmetic mean of the points, radius = mean distance to that point. */
    static CircleFit meanEstimatorFit(double[] xs, double[] zs) {
        int n = xs.length;
        double meanX = mean(xs);
        double meanZ = mean(zs);
        double[] dists = new double[n];
        for (int i = 0; i < n; i++) {
            dists[i] = Math.hypot(xs[i] - meanX, zs[i] - meanZ);
        }
        double radius = mean(dists);
        double rms = rmsResidual(dists, radius);
        return new CircleFit(meanX, meanZ, radius, rms);
    }

    /**
     * Algebraic (Kåsa) circle fit, closed-form, no iteration. Points are mean-centered first for
     * numerical conditioning, then the center is shifted back — this is the same 3x3 normal-equation
     * system reduced to 2x2 by that centering, not a different algorithm.
     */
    static CircleFit kasaFit(double[] xs, double[] zs) {
        int n = xs.length;
        double meanX = mean(xs);
        double meanZ = mean(zs);

        double suu = 0, svv = 0, suv = 0, suuu = 0, svvv = 0, suvv = 0, svuu = 0;
        for (int i = 0; i < n; i++) {
            double u = xs[i] - meanX;
            double v = zs[i] - meanZ;
            suu += u * u;
            svv += v * v;
            suv += u * v;
            suuu += u * u * u;
            svvv += v * v * v;
            suvv += u * v * v;
            svuu += v * u * u;
        }

        double rhsU = (suuu + suvv) / 2.0;
        double rhsV = (svvv + svuu) / 2.0;
        double det = suu * svv - suv * suv;

        double uc;
        double vc;
        if (Math.abs(det) < 1e-12) {
            // Degenerate (collinear points, or a single repeated point) — fall back to the mean.
            uc = 0;
            vc = 0;
        } else {
            uc = (rhsU * svv - rhsV * suv) / det;
            vc = (rhsV * suu - rhsU * suv) / det;
        }

        double centerX = meanX + uc;
        double centerZ = meanZ + vc;
        double radius = Math.sqrt(uc * uc + vc * vc + (suu + svv) / n);

        double[] dists = new double[n];
        for (int i = 0; i < n; i++) {
            dists[i] = Math.hypot(xs[i] - centerX, zs[i] - centerZ);
        }
        double rms = rmsResidual(dists, radius);
        return new CircleFit(centerX, centerZ, radius, rms);
    }

    private static double rmsResidual(double[] dists, double radius) {
        double sumSq = 0;
        for (double d : dists) {
            double residual = d - radius;
            sumSq += residual * residual;
        }
        return Math.sqrt(sumSq / dists.length);
    }

    // ---------------------------------------------------------------- Angles & gaps

    /** Angle of (x, z) around (centerX, centerZ), normalized to [0, 360). */
    static double angleDegrees(double x, double z, double centerX, double centerZ) {
        double deg = Math.toDegrees(Math.atan2(z - centerZ, x - centerX));
        return normalize360(deg);
    }

    /** Minecraft yaw for a direction (dx, dz) from the wheel centre, normalized to [-180, 180). */
    static double yawDegrees(double dx, double dz) {
        double deg = Math.toDegrees(Math.atan2(-dx, dz));
        double wrapped = deg % 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        return wrapped;
    }

    record GapStats(double min, double median, double max, double stddev, double[] gapsDegrees) {}

    /** Consecutive angular gaps with wrap-around: n sorted angles produce n gaps summing to 360. */
    static GapStats gapStats(double[] anglesSortedAscendingDegrees) {
        int n = anglesSortedAscendingDegrees.length;
        double[] gaps = new double[n];
        for (int i = 0; i < n - 1; i++) {
            gaps[i] = anglesSortedAscendingDegrees[i + 1] - anglesSortedAscendingDegrees[i];
        }
        gaps[n - 1] = 360.0 - anglesSortedAscendingDegrees[n - 1] + anglesSortedAscendingDegrees[0];

        double[] sorted = gaps.clone();
        Arrays.sort(sorted);
        double min = sorted[0];
        double max = sorted[sorted.length - 1];
        double median = (sorted.length % 2 == 0)
                ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0
                : sorted[sorted.length / 2];
        double stddev = stddev(gaps);
        return new GapStats(min, median, max, stddev, gaps);
    }

    // ---------------------------------------------------------------- Opposite-pair corroboration

    record OppositePairCheck(double centroidX, double centroidZ, double spreadStddev, int pairCount) {}

    /**
     * Corroborating check only — NOT the wheel-centre estimator. 37 is odd, so there is no true
     * diametric opposite; this pairs index i with i + (n/2) in angular order (18 pairs for n=37,
     * index 36 intentionally left unpaired) and reports how tightly the pair midpoints cluster.
     */
    static OppositePairCheck oppositePairCheck(double[] xsSortedByAngle, double[] zsSortedByAngle) {
        int n = xsSortedByAngle.length;
        int offset = n / 2;
        int pairCount = offset;
        if (pairCount == 0) {
            // Fewer than 2 segments found — mean(empty array) would be 0.0/0 = NaN with no signal
            // that it's a "not enough data" case rather than a real geometry problem. Report it as
            // 0 pairs explicitly instead; RouletteScanReport adds a caveat when this happens.
            return new OppositePairCheck(Double.NaN, Double.NaN, Double.NaN, 0);
        }
        double[] midX = new double[pairCount];
        double[] midZ = new double[pairCount];
        for (int i = 0; i < pairCount; i++) {
            midX[i] = (xsSortedByAngle[i] + xsSortedByAngle[i + offset]) / 2.0;
            midZ[i] = (zsSortedByAngle[i] + zsSortedByAngle[i + offset]) / 2.0;
        }
        double centroidX = mean(midX);
        double centroidZ = mean(midZ);
        double[] dists = new double[pairCount];
        for (int i = 0; i < pairCount; i++) {
            dists[i] = Math.hypot(midX[i] - centroidX, midZ[i] - centroidZ);
        }
        return new OppositePairCheck(centroidX, centroidZ, stddev(dists), pairCount);
    }

    // ---------------------------------------------------------------- Interaction sizing

    static double chordWidth(double radius, double gapRadians) {
        return 2 * radius * Math.sin(gapRadians / 2.0);
    }

    // ---------------------------------------------------------------- Rigid-body rotation check

    static Quat invert(Quat q) {
        // Unit quaternion: inverse == conjugate.
        return new Quat(-q.x(), -q.y(), -q.z(), q.w());
    }

    static Quat multiply(Quat a, Quat b) {
        double w = a.w() * b.w() - a.x() * b.x() - a.y() * b.y() - a.z() * b.z();
        double x = a.w() * b.x() + a.x() * b.w() + a.y() * b.z() - a.z() * b.y();
        double y = a.w() * b.y() - a.x() * b.z() + a.y() * b.w() + a.z() * b.x();
        double z = a.w() * b.z() + a.x() * b.y() - a.y() * b.x() + a.z() * b.w();
        return new Quat(x, y, z, w);
    }

    record AxisAngleDeg(double axisX, double axisY, double axisZ, double angleDegrees) {}

    /** Decomposes {@code qTo * inverse(qFrom)} into an axis and angle — the rotation step from qFrom to qTo. */
    static AxisAngleDeg stepAxisAngle(Quat qFrom, Quat qTo) {
        Quat step = multiply(qTo, invert(qFrom));
        // Prefer the shortest-path representation (angle in [0, 180]) by flipping to w >= 0.
        if (step.w() < 0) {
            step = new Quat(-step.x(), -step.y(), -step.z(), -step.w());
        }
        double w = Math.max(-1.0, Math.min(1.0, step.w()));
        double angle = Math.toDegrees(2 * Math.acos(w));
        double sinHalf = Math.sqrt(Math.max(0.0, 1 - w * w));
        if (sinHalf < 1e-9) {
            // No meaningful rotation between the two poses; axis is undefined, report +Y.
            return new AxisAngleDeg(0, 1, 0, angle);
        }
        return new AxisAngleDeg(step.x() / sinHalf, step.y() / sinHalf, step.z() / sinHalf, angle);
    }

    // ---------------------------------------------------------------- Stats helpers

    static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    static double stddev(double[] values) {
        double m = mean(values);
        double sumSq = 0;
        for (double v : values) {
            double d = v - m;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / values.length);
    }

    private static double normalize360(double degrees) {
        double d = degrees % 360.0;
        if (d < 0) d += 360.0;
        return d;
    }
}
