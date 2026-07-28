package me.beeliebub.tweaks.minigames.roulette;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Pure aggregation over a completed scan: ring/tag completeness, both wheel-centre estimators,
 * angular order, gaps, recommended {@code Interaction} sizing, and the rigid-body-rotation verdict.
 * No Bukkit types — everything here operates on {@link ScannedSegment} and
 * {@link RouletteGeometry}'s plain records, so it stays testable without MockBukkit if that's ever
 * wanted (this diagnostic's own tests only require coverage of the circle fit itself).
 */
record RouletteScanReport(
        int totalFound,
        Map<Ring, Integer> countsByRing,
        List<Integer> missingOuterPockets,
        List<Integer> missingMiddleThirds,
        boolean missingColorRed,
        boolean missingColorBlack,
        List<String> duplicateTags,
        List<java.util.UUID> multiTagEntityIds,
        List<ScannedSegment> unknownTagSegments,
        boolean completeBoard,

        RouletteScanReport.YStats outerYStats,
        RouletteGeometry.CircleFit meanEstimator,
        RouletteGeometry.CircleFit kasaEstimator,
        double centreDisagreementDistance,
        RouletteGeometry.OppositePairCheck oppositeCheck,

        List<ScannedSegment> outerAngularOrder,
        double[] outerYawDegrees,
        RouletteGeometry.GapStats gapStats,

        Map<Ring, RadiusBand> radiusBandsByRing,
        double recommendedWidth,
        double recommendedWidthConservative,
        double recommendedHeight,

        RigidBodyVerdict rigidBodyVerdict,
        Set<String> chunkFootprint,
        List<String> caveats
) {

    record YStats(double mean, double min, double max, double stddev, boolean flat) {}

    record RadiusBand(double min, double max, double mean) {}

    record RigidBodyVerdict(
            boolean planeFlatPass, double planeStddev,
            boolean circleResidualPass, double circleRmsResidual,
            boolean axisAnglePass, double axisAngleStddevDegrees, double maxAxisDeviationFromYDegrees,
            boolean rightRotationUniformPass, double maxRightRotationDeviationDegrees,
            boolean yawPitchZeroPass, double maxAbsYawOrPitchDegrees,
            boolean billboardFixedPass
    ) {}

    /** The canonical single-zero European wheel order, emitted alongside the measured order for comparison. */
    static final List<Integer> CANONICAL_EUROPEAN_ORDER = List.of(
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1,
            20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    );

    /**
     * Confirmed against the live board (2026-07-27 scan): each MIDDLE (dozen) selector is rendered
     * as this many physical {@code BlockDisplay} segments forming one curved arc, all sharing the
     * same {@code roulette_thirds_NN} tag. This is by design, not a duplicate/defect — see the
     * package {@code CLAUDE.md}. OUTER and INNER stay strictly one entity per selector/color.
     */
    private static final int MIDDLE_SEGMENTS_PER_DOZEN = 3;
    private static final int EXPECTED_MIDDLE_TOTAL = MIDDLE_SEGMENTS_PER_DOZEN * 3;
    static final int EXPECTED_TOTAL_SEGMENTS = 37 + EXPECTED_MIDDLE_TOTAL + 2;

    // Placeholder pass/fail thresholds — tune once real board numbers exist (see package CLAUDE.md).
    private static final double PLANE_FLAT_STDDEV_THRESHOLD = 0.05;
    private static final double CIRCLE_RESIDUAL_THRESHOLD = 0.05;
    private static final double AXIS_ANGLE_STDDEV_THRESHOLD_DEG = 1.0;
    private static final double AXIS_Y_DEVIATION_THRESHOLD_DEG = 2.0;
    private static final double RIGHT_ROTATION_DEVIATION_THRESHOLD_DEG = 1.0;
    private static final double YAW_PITCH_ZERO_THRESHOLD_DEG = 0.5;

    static RouletteScanReport compute(List<ScannedSegment> segments) {
        List<String> caveats = new ArrayList<>();

        Map<Ring, Integer> countsByRing = new EnumMap<>(Ring.class);
        for (Ring r : Ring.values()) countsByRing.put(r, 0);
        for (ScannedSegment s : segments) {
            countsByRing.merge(s.ring(), 1, Integer::sum);
        }

        List<ScannedSegment> outer = segments.stream().filter(s -> s.ring() == Ring.OUTER).toList();
        List<ScannedSegment> middle = segments.stream().filter(s -> s.ring() == Ring.MIDDLE).toList();
        List<ScannedSegment> inner = segments.stream().filter(s -> s.ring() == Ring.INNER).toList();
        List<ScannedSegment> unknown = segments.stream().filter(s -> s.ring() == Ring.UNKNOWN).toList();

        List<Integer> missingOuterPockets = missingSelectors(outer, 0, 36);
        List<Integer> missingMiddleThirds = missingSelectors(middle, 1, 3);
        boolean missingColorRed = inner.stream().noneMatch(s -> "roulette_color_red".equals(s.rawTag()));
        boolean missingColorBlack = inner.stream().noneMatch(s -> "roulette_color_black".equals(s.rawTag()));

        List<String> duplicateTags = findDuplicateTags(segments);
        List<java.util.UUID> multiTagEntityIds = segments.stream()
                .filter(s -> s.allRouletteTags().size() > 1)
                .map(ScannedSegment::entityId)
                .toList();

        Map<Integer, Long> middleCountsByDozen = middle.stream()
                .collect(Collectors.groupingBy(ScannedSegment::selector, Collectors.counting()));
        boolean middleCountsConsistent = middleCountsByDozen.values().stream()
                .allMatch(count -> count == MIDDLE_SEGMENTS_PER_DOZEN);
        if (!middle.isEmpty() && !middleCountsConsistent) {
            caveats.add("MIDDLE dozens have inconsistent physical-segment counts: " + middleCountsByDozen
                    + " (expected " + MIDDLE_SEGMENTS_PER_DOZEN + " each) — a piece may be missing, extra, "
                    + "or out of scan range.");
        } else if (!middle.isEmpty()) {
            caveats.add("Each MIDDLE dozen is rendered as " + MIDDLE_SEGMENTS_PER_DOZEN + " physical arc "
                    + "segments sharing one tag — this is by design (not a duplicate); the production "
                    + "Interaction overlay needs one hitbox per physical segment, all resolving to the same SegmentRef.");
        }

        boolean completeBoard = outer.size() == 37 && middle.size() == EXPECTED_MIDDLE_TOTAL && inner.size() == 2
                && missingOuterPockets.isEmpty() && missingMiddleThirds.isEmpty()
                && !missingColorRed && !missingColorBlack && duplicateTags.isEmpty() && middleCountsConsistent;

        if (outer.isEmpty()) {
            caveats.add("No OUTER-ring (pocket) segments found — nothing to fit a circle to. "
                    + "Stand closer to the wheel or increase the scan radius.");
            if (!middle.isEmpty() || !inner.isEmpty()) {
                caveats.add("MIDDLE/INNER segments were found, but their radius bands were still "
                        + "skipped: radius is measured from the OUTER-ring circle-fit centre, which "
                        + "doesn't exist without at least one OUTER segment.");
            }
            return emptyReport(segments, countsByRing, missingOuterPockets, missingMiddleThirds,
                    missingColorRed, missingColorBlack, duplicateTags, multiTagEntityIds, unknown,
                    completeBoard, caveats);
        }

        double[] outerX = outer.stream().mapToDouble(s -> s.correctCentroid().x()).toArray();
        double[] outerY = outer.stream().mapToDouble(s -> s.correctCentroid().y()).toArray();
        double[] outerZ = outer.stream().mapToDouble(s -> s.correctCentroid().z()).toArray();

        YStats outerYStats = yStats(outerY);
        if (!outerYStats.flat()) {
            caveats.add("Outer-ring centroid Y is not flat (stddev=" + outerYStats.stddev()
                    + ") — the board may be tilted or stepped; 2D (x,z) reduction below may be invalid.");
        }

        RouletteGeometry.CircleFit meanEstimator = RouletteGeometry.meanEstimatorFit(outerX, outerZ);
        RouletteGeometry.CircleFit kasaEstimator = RouletteGeometry.kasaFit(outerX, outerZ);
        double centreDisagreement = Math.hypot(
                meanEstimator.centerX() - kasaEstimator.centerX(),
                meanEstimator.centerZ() - kasaEstimator.centerZ());

        double centerX = kasaEstimator.centerX();
        double centerZ = kasaEstimator.centerZ();

        List<ScannedSegment> outerAngularOrder = outer.stream()
                .sorted((a, b) -> Double.compare(
                        RouletteGeometry.angleDegrees(a.correctCentroid().x(), a.correctCentroid().z(), centerX, centerZ),
                        RouletteGeometry.angleDegrees(b.correctCentroid().x(), b.correctCentroid().z(), centerX, centerZ)))
                .toList();

        double[] sortedAngles = outerAngularOrder.stream()
                .mapToDouble(s -> RouletteGeometry.angleDegrees(s.correctCentroid().x(), s.correctCentroid().z(), centerX, centerZ))
                .toArray();
        double[] sortedX = outerAngularOrder.stream().mapToDouble(s -> s.correctCentroid().x()).toArray();
        double[] sortedZ = outerAngularOrder.stream().mapToDouble(s -> s.correctCentroid().z()).toArray();

        double[] outerYawDegrees = new double[outerAngularOrder.size()];
        for (int i = 0; i < outerAngularOrder.size(); i++) {
            double dx = sortedX[i] - centerX;
            double dz = sortedZ[i] - centerZ;
            outerYawDegrees[i] = RouletteGeometry.yawDegrees(dx, dz);
        }

        RouletteGeometry.GapStats gapStats = RouletteGeometry.gapStats(sortedAngles);
        RouletteGeometry.OppositePairCheck oppositeCheck = RouletteGeometry.oppositePairCheck(sortedX, sortedZ);

        Map<Ring, RadiusBand> radiusBandsByRing = radiusBands(segments, centerX, centerZ);
        RadiusBand outerBand = radiusBandsByRing.get(Ring.OUTER);

        double medianGapRadians = Math.toRadians(gapStats.median());
        double chordWidth = RouletteGeometry.chordWidth(outerBand.mean(), medianGapRadians);
        double radialBandWidth = outerBand.max() - outerBand.min();
        // radialBandWidth (inter-segment radius spread) is near-zero by construction on this board:
        // every OUTER pocket sits at the same measured radius (confirmed 2026-07-27 — spread was
        // ~0.0003, pure measurement noise), because this is a single ring of individually placed
        // segments, not a radially-banded strip. Using that spread as a width bound produced a
        // nonsensical ~0.0003-block hitbox. The segment's own scale is the real footprint — use the
        // tighter of its two horizontal extents (scale.x/scale.z) as the width bound instead.
        double avgScaleX = outer.stream().mapToDouble(s -> s.scale().x()).average().orElse(0.0);
        double avgScaleZ = outer.stream().mapToDouble(s -> s.scale().z()).average().orElse(0.0);
        double scaleWidth = Math.min(avgScaleX, avgScaleZ);
        double recommendedWidth = Math.min(chordWidth, scaleWidth);
        double recommendedWidthConservative = recommendedWidth * 0.8;
        double avgScaleY = outer.stream().mapToDouble(s -> s.scale().y()).average().orElse(0.0);
        double recommendedHeight = (outerYStats.max() - outerYStats.min()) + avgScaleY;

        if (radialBandWidth < 0.01 * Math.max(avgScaleX, avgScaleZ)) {
            caveats.add("OUTER radial-band width (" + radialBandWidth + ") is near-zero — every pocket "
                    + "sits at essentially the same measured radius (a single ring, not a radially-banded "
                    + "strip), so it was NOT used for the width recommendation; scale.x/scale.z ("
                    + avgScaleX + "/" + avgScaleZ + ") was used instead.");
        }

        RigidBodyVerdict verdict = computeRigidBodyVerdict(outerAngularOrder, outerYStats, kasaEstimator, gapStats, segments);

        Set<String> chunkFootprint = new TreeSet<>();
        for (ScannedSegment s : segments) {
            chunkFootprint.add(s.chunkX() + "," + s.chunkZ());
        }

        if (segments.size() != EXPECTED_TOTAL_SEGMENTS) {
            caveats.add("Total segments found (" + segments.size() + ") != " + EXPECTED_TOTAL_SEGMENTS
                    + " (37 outer + " + EXPECTED_MIDDLE_TOTAL + " middle [" + MIDDLE_SEGMENTS_PER_DOZEN
                    + " physical arc segments per dozen] + 2 inner). getNearbyEntities only sees loaded "
                    + "chunks — a board straddling the edge of the scan radius or view distance may be "
                    + "partially missed.");
        }
        if (!unknown.isEmpty()) {
            caveats.add(unknown.size() + " entity(ies) carried an unrecognised roulette_* tag — see the UNKNOWN rows.");
        }
        if (!duplicateTags.isEmpty()) {
            caveats.add(duplicateTags.size() + " tag/selector collision(s) found — the production click "
                    + "index would silently misroute clicks on these until resolved.");
        }
        if (outer.size() < 2) {
            caveats.add("Fewer than 2 outer-ring segments found — the opposite-pair corroboration "
                    + "check has 0 pairs and its centroid/spread fields are NaN by design, not an error.");
        }

        return new RouletteScanReport(
                segments.size(), countsByRing, missingOuterPockets, missingMiddleThirds,
                missingColorRed, missingColorBlack, duplicateTags, multiTagEntityIds, unknown, completeBoard,
                outerYStats, meanEstimator, kasaEstimator, centreDisagreement, oppositeCheck,
                outerAngularOrder, outerYawDegrees, gapStats,
                radiusBandsByRing, recommendedWidth, recommendedWidthConservative, recommendedHeight,
                verdict, chunkFootprint, caveats);
    }

    private static RouletteScanReport emptyReport(
            List<ScannedSegment> segments, Map<Ring, Integer> countsByRing,
            List<Integer> missingOuterPockets, List<Integer> missingMiddleThirds,
            boolean missingColorRed, boolean missingColorBlack, List<String> duplicateTags,
            List<java.util.UUID> multiTagEntityIds, List<ScannedSegment> unknown, boolean completeBoard,
            List<String> caveats) {
        YStats emptyY = new YStats(Double.NaN, Double.NaN, Double.NaN, Double.NaN, false);
        RouletteGeometry.CircleFit emptyFit = new RouletteGeometry.CircleFit(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        RouletteGeometry.OppositePairCheck emptyOpp = new RouletteGeometry.OppositePairCheck(Double.NaN, Double.NaN, Double.NaN, 0);
        RouletteGeometry.GapStats emptyGaps = new RouletteGeometry.GapStats(Double.NaN, Double.NaN, Double.NaN, Double.NaN, new double[0]);
        RigidBodyVerdict emptyVerdict = new RigidBodyVerdict(
                false, Double.NaN, false, Double.NaN, false, Double.NaN, Double.NaN,
                false, Double.NaN, false, Double.NaN, false);
        Set<String> chunkFootprint = new TreeSet<>();
        for (ScannedSegment s : segments) chunkFootprint.add(s.chunkX() + "," + s.chunkZ());
        return new RouletteScanReport(
                segments.size(), countsByRing, missingOuterPockets, missingMiddleThirds,
                missingColorRed, missingColorBlack, duplicateTags, multiTagEntityIds, unknown, completeBoard,
                emptyY, emptyFit, emptyFit, Double.NaN, emptyOpp,
                List.of(), new double[0], emptyGaps,
                Map.of(), Double.NaN, Double.NaN, Double.NaN,
                emptyVerdict, chunkFootprint, caveats);
    }

    private static List<Integer> missingSelectors(List<ScannedSegment> ringSegments, int lo, int hi) {
        Set<Integer> present = ringSegments.stream().map(ScannedSegment::selector).collect(Collectors.toCollection(TreeSet::new));
        List<Integer> missing = new ArrayList<>();
        for (int i = lo; i <= hi; i++) {
            if (!present.contains(i)) missing.add(i);
        }
        return missing;
    }

    /**
     * Flags a tag/selector claimed by more than one entity as a genuine collision — but only for
     * OUTER (one entity per pocket) and INNER (one entity per color). MIDDLE is deliberately
     * excluded: confirmed against the live board (2026-07-27 scan), each dozen is rendered as
     * {@value #MIDDLE_SEGMENTS_PER_DOZEN} physical arc segments sharing one {@code roulette_thirds_NN}
     * tag by design — {@link #compute} checks MIDDLE for a *different* kind of anomaly instead (an
     * inconsistent per-dozen physical-segment count).
     */
    private static List<String> findDuplicateTags(List<ScannedSegment> segments) {
        Map<String, List<ScannedSegment>> byKey = new LinkedHashMap<>();
        for (ScannedSegment s : segments) {
            if (s.ring() == Ring.UNKNOWN || s.ring() == Ring.MIDDLE) continue;
            String key = (s.ring() == Ring.INNER) ? s.rawTag() : (s.ring() + ":" + s.selector());
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        List<String> duplicates = new ArrayList<>();
        for (Map.Entry<String, List<ScannedSegment>> e : byKey.entrySet()) {
            if (e.getValue().size() > 1) {
                String tags = e.getValue().stream().map(ScannedSegment::rawTag).collect(Collectors.joining(", "));
                duplicates.add(e.getKey() + " claimed by " + e.getValue().size() + " entities (tags: " + tags + ")");
            }
        }
        return duplicates;
    }

    private static YStats yStats(double[] ys) {
        double mean = RouletteGeometry.mean(ys);
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double y : ys) {
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        double stddev = RouletteGeometry.stddev(ys);
        return new YStats(mean, min, max, stddev, stddev < PLANE_FLAT_STDDEV_THRESHOLD);
    }

    private static Map<Ring, RadiusBand> radiusBands(List<ScannedSegment> segments, double centerX, double centerZ) {
        Map<Ring, List<Double>> byRing = new EnumMap<>(Ring.class);
        for (ScannedSegment s : segments) {
            if (s.ring() == Ring.UNKNOWN) continue;
            double r = Math.hypot(s.correctCentroid().x() - centerX, s.correctCentroid().z() - centerZ);
            byRing.computeIfAbsent(s.ring(), k -> new ArrayList<>()).add(r);
        }
        Map<Ring, RadiusBand> bands = new EnumMap<>(Ring.class);
        for (Map.Entry<Ring, List<Double>> e : byRing.entrySet()) {
            double[] rs = e.getValue().stream().mapToDouble(Double::doubleValue).toArray();
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (double r : rs) { min = Math.min(min, r); max = Math.max(max, r); }
            bands.put(e.getKey(), new RadiusBand(min, max, RouletteGeometry.mean(rs)));
        }
        return bands;
    }

    private static RigidBodyVerdict computeRigidBodyVerdict(
            List<ScannedSegment> outerAngularOrder, YStats outerYStats, RouletteGeometry.CircleFit kasaEstimator,
            RouletteGeometry.GapStats gapStats, List<ScannedSegment> allSegments) {

        boolean planeFlatPass = outerYStats.flat();
        boolean circleResidualPass = kasaEstimator.rmsResidual() < CIRCLE_RESIDUAL_THRESHOLD;

        int n = outerAngularOrder.size();
        double[] stepAngles = new double[n];
        double maxAxisDeviation = 0;
        for (int i = 0; i < n; i++) {
            ScannedSegment from = outerAngularOrder.get(i);
            ScannedSegment to = outerAngularOrder.get((i + 1) % n);
            RouletteGeometry.AxisAngleDeg step = RouletteGeometry.stepAxisAngle(from.leftRotation(), to.leftRotation());
            stepAngles[i] = step.angleDegrees();
            double axisYDeviation = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, Math.abs(step.axisY())))));
            maxAxisDeviation = Math.max(maxAxisDeviation, axisYDeviation);
        }
        double axisAngleStddev = n > 0 ? RouletteGeometry.stddev(stepAngles) : Double.NaN;
        boolean axisAnglePass = axisAngleStddev < AXIS_ANGLE_STDDEV_THRESHOLD_DEG
                && maxAxisDeviation < AXIS_Y_DEVIATION_THRESHOLD_DEG;

        double maxRightRotationDeviation = 0;
        if (n > 0) {
            RouletteGeometry.Quat reference = outerAngularOrder.get(0).rightRotation();
            for (ScannedSegment s : outerAngularOrder) {
                RouletteGeometry.AxisAngleDeg step = RouletteGeometry.stepAxisAngle(reference, s.rightRotation());
                maxRightRotationDeviation = Math.max(maxRightRotationDeviation, step.angleDegrees());
            }
        } else {
            maxRightRotationDeviation = Double.NaN;
        }
        boolean rightRotationUniformPass = maxRightRotationDeviation < RIGHT_ROTATION_DEVIATION_THRESHOLD_DEG;

        double maxAbsYawOrPitch = 0;
        for (ScannedSegment s : allSegments) {
            maxAbsYawOrPitch = Math.max(maxAbsYawOrPitch, Math.max(Math.abs(s.yaw()), Math.abs(s.pitch())));
        }
        boolean yawPitchZeroPass = maxAbsYawOrPitch < YAW_PITCH_ZERO_THRESHOLD_DEG;

        boolean billboardFixedPass = allSegments.stream()
                .allMatch(s -> s.billboard() == org.bukkit.entity.Display.Billboard.FIXED);

        return new RigidBodyVerdict(
                planeFlatPass, outerYStats.stddev(),
                circleResidualPass, kasaEstimator.rmsResidual(),
                axisAnglePass, axisAngleStddev, maxAxisDeviation,
                rightRotationUniformPass, maxRightRotationDeviation,
                yawPitchZeroPass, maxAbsYawOrPitch,
                billboardFixedPass);
    }
}
