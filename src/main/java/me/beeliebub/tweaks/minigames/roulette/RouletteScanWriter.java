package me.beeliebub.tweaks.minigames.roulette;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Formats a scan into a TSV file and writes it. Formatting is pure (no I/O); {@link #writeNow}
 * is the only method that touches disk, and it is always called from an async task by
 * {@link RouletteScanCommand} — never from the main thread.
 */
final class RouletteScanWriter {

    private RouletteScanWriter() {}

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    static File scanFile(File dataFolder, String worldName) {
        File dir = new File(dataFolder, "roulette-scans");
        String name = "scan-" + safeName(worldName) + "-" + LocalDateTime.now().format(FILE_TIMESTAMP) + ".tsv";
        return new File(dir, name);
    }

    private static String safeName(String s) {
        return s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    static void writeNow(File target, String content) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
    }

    static String buildTsv(List<ScannedSegment> segments, RouletteScanReport report, String worldName) {
        StringBuilder sb = new StringBuilder();
        appendPreamble(sb, segments, report, worldName);
        appendHeader(sb);
        for (ScannedSegment s : segments) {
            appendRow(sb, s);
        }
        return sb.toString();
    }

    private static void appendPreamble(StringBuilder sb, List<ScannedSegment> segments, RouletteScanReport report, String worldName) {
        sb.append("# Roulette board scan — world=").append(worldName)
                .append(" generated=").append(LocalDateTime.now()).append('\n');
        sb.append("# Read-only diagnostic — never mutates the board. See minigames/roulette/CLAUDE.md.\n");
        sb.append("#\n");
        sb.append("# Total segments found: ").append(report.totalFound())
                .append(" (expected ").append(RouletteScanReport.EXPECTED_TOTAL_SEGMENTS)
                .append(" = 37 outer + 9 middle [3 physical arc segments per dozen] + 2 inner)\n");
        for (Ring r : Ring.values()) {
            sb.append("#   ").append(r).append(": ").append(report.countsByRing().getOrDefault(r, 0)).append('\n');
        }
        sb.append("# Complete (all pockets/thirds/colors present, no anomalies): ").append(report.completeBoard()).append('\n');
        if (!report.missingOuterPockets().isEmpty()) sb.append("# Missing outer pockets: ").append(report.missingOuterPockets()).append('\n');
        if (!report.missingMiddleThirds().isEmpty()) sb.append("# Missing middle thirds: ").append(report.missingMiddleThirds()).append('\n');
        if (report.missingColorRed()) sb.append("# Missing color: red\n");
        if (report.missingColorBlack()) sb.append("# Missing color: black\n");
        for (String d : report.duplicateTags()) sb.append("# DUPLICATE: ").append(d).append('\n');
        for (ScannedSegment u : report.unknownTagSegments()) {
            sb.append("# UNKNOWN TAG: entity=").append(u.entityId()).append(" tags=").append(u.allRouletteTags()).append('\n');
        }
        for (java.util.UUID id : report.multiTagEntityIds()) {
            sb.append("# MULTI-TAG ENTITY (contract expects exactly one roulette_* tag): ").append(id).append('\n');
        }
        sb.append("#\n");
        sb.append("# Chunk footprint (cx,cz): ").append(report.chunkFootprint()).append('\n');
        sb.append("#\n");
        sb.append("# --- Wheel centre ---\n");
        sb.append("# Mean estimator:  center=(").append(f(report.meanEstimator().centerX())).append(", ")
                .append(f(report.meanEstimator().centerZ())).append(") radius=").append(f(report.meanEstimator().radius()))
                .append(" rmsResidual=").append(f(report.meanEstimator().rmsResidual())).append('\n');
        sb.append("# Kasa fit (primary): center=(").append(f(report.kasaEstimator().centerX())).append(", ")
                .append(f(report.kasaEstimator().centerZ())).append(") radius=").append(f(report.kasaEstimator().radius()))
                .append(" rmsResidual=").append(f(report.kasaEstimator().rmsResidual())).append('\n');
        sb.append("# Centre disagreement (mean vs kasa): ").append(f(report.centreDisagreementDistance())).append('\n');
        sb.append("# Opposite-pair corroboration (NOT the estimator, 37 is odd — see CLAUDE.md): centroid=(")
                .append(f(report.oppositeCheck().centroidX())).append(", ").append(f(report.oppositeCheck().centroidZ()))
                .append(") spreadStddev=").append(f(report.oppositeCheck().spreadStddev()))
                .append(" pairCount=").append(report.oppositeCheck().pairCount()).append('\n');
        sb.append("#\n");
        sb.append("# --- Plane & radius bands ---\n");
        sb.append("# Outer-ring centroid Y: mean=").append(f(report.outerYStats().mean())).append(" min=").append(f(report.outerYStats().min()))
                .append(" max=").append(f(report.outerYStats().max())).append(" stddev=").append(f(report.outerYStats().stddev()))
                .append(" flat=").append(report.outerYStats().flat()).append('\n');
        for (var e : report.radiusBandsByRing().entrySet()) {
            sb.append("# ").append(e.getKey()).append(" radius band: min=").append(f(e.getValue().min()))
                    .append(" max=").append(f(e.getValue().max())).append(" mean=").append(f(e.getValue().mean())).append('\n');
        }
        sb.append("#\n");
        sb.append("# --- Angular order & gaps ---\n");
        sb.append("# Measured wheel order (outer pockets, angular order): ")
                .append(report.outerAngularOrder().stream().map(s -> String.valueOf(s.selector())).toList()).append('\n');
        sb.append("# Canonical single-zero European order (reference):    ").append(RouletteScanReport.CANONICAL_EUROPEAN_ORDER).append('\n');
        sb.append("# Gaps (deg): min=").append(f(report.gapStats().min())).append(" median=").append(f(report.gapStats().median()))
                .append(" max=").append(f(report.gapStats().max())).append(" stddev=").append(f(report.gapStats().stddev())).append('\n');
        sb.append("#\n");
        sb.append("# --- Recommended Interaction sizing ---\n");
        sb.append("# width=").append(f(report.recommendedWidth())).append(" widthConservative(0.8x)=")
                .append(f(report.recommendedWidthConservative())).append(" height=").append(f(report.recommendedHeight())).append('\n');
        sb.append("#\n");
        sb.append("# --- Rigid-body-rotation verdict (PASS/CHECK, placeholder thresholds — see package CLAUDE.md) ---\n");
        var v = report.rigidBodyVerdict();
        sb.append("# plane flat:            ").append(pass(v.planeFlatPass())).append(" stddev=").append(f(v.planeStddev())).append('\n');
        sb.append("# circle residual:       ").append(pass(v.circleResidualPass())).append(" rms=").append(f(v.circleRmsResidual())).append('\n');
        sb.append("# pairwise axis/angle:   ").append(pass(v.axisAnglePass())).append(" angleStddev=").append(f(v.axisAngleStddevDegrees()))
                .append(" maxAxisDeviationFromY=").append(f(v.maxAxisDeviationFromYDegrees())).append('\n');
        sb.append("# rightRotation uniform: ").append(pass(v.rightRotationUniformPass())).append(" maxDeviation=").append(f(v.maxRightRotationDeviationDegrees())).append('\n');
        sb.append("# yaw/pitch all-zero:    ").append(pass(v.yawPitchZeroPass())).append(" maxAbs=").append(f(v.maxAbsYawOrPitchDegrees())).append('\n');
        sb.append("# billboard all-FIXED:   ").append(pass(v.billboardFixedPass())).append('\n');
        sb.append("#\n");
        for (String c : report.caveats()) {
            sb.append("# CAVEAT: ").append(c).append('\n');
        }
        sb.append("#\n");
    }

    private static void appendHeader(StringBuilder sb) {
        sb.append(String.join("\t",
                "entityId", "entityType", "world", "ring", "selector", "rawTag", "numericForm", "allTags",
                "x", "y", "z", "yaw", "pitch",
                "translationX", "translationY", "translationZ",
                "leftRotX", "leftRotY", "leftRotZ", "leftRotW",
                "scaleX", "scaleY", "scaleZ",
                "rightRotX", "rightRotY", "rightRotZ", "rightRotW",
                "billboard", "interpDuration", "interpDelay", "teleportDuration",
                "displayWidth", "displayHeight", "viewRange", "brightness", "glowing", "glowColor",
                "blockData",
                "planCentroidX", "planCentroidY", "planCentroidZ",
                "correctCentroidX", "correctCentroidY", "correctCentroidZ",
                "centroidDeltaX", "centroidDeltaY", "centroidDeltaZ",
                "chunkX", "chunkZ")).append('\n');
    }

    private static void appendRow(StringBuilder sb, ScannedSegment s) {
        double deltaX = s.correctCentroid().x() - s.planCentroid().x();
        double deltaY = s.correctCentroid().y() - s.planCentroid().y();
        double deltaZ = s.correctCentroid().z() - s.planCentroid().z();
        sb.append(String.join("\t",
                s.entityId().toString(), s.entityType().toString(), s.worldName(),
                s.ring().toString(), String.valueOf(s.selector()), s.rawTag(), s.numericForm().toString(),
                s.allRouletteTags().toString(),
                f(s.x()), f(s.y()), f(s.z()), f(s.yaw()), f(s.pitch()),
                f(s.translation().x()), f(s.translation().y()), f(s.translation().z()),
                f(s.leftRotation().x()), f(s.leftRotation().y()), f(s.leftRotation().z()), f(s.leftRotation().w()),
                f(s.scale().x()), f(s.scale().y()), f(s.scale().z()),
                f(s.rightRotation().x()), f(s.rightRotation().y()), f(s.rightRotation().z()), f(s.rightRotation().w()),
                s.billboard().toString(), String.valueOf(s.interpolationDuration()), String.valueOf(s.interpolationDelay()),
                String.valueOf(s.teleportDuration()),
                f(s.displayWidth()), f(s.displayHeight()), f(s.viewRange()),
                s.brightness() == null ? "" : s.brightness().toString(),
                String.valueOf(s.glowing()), s.glowColorOverride() == null ? "" : s.glowColorOverride().toString(),
                s.blockDataString(),
                f(s.planCentroid().x()), f(s.planCentroid().y()), f(s.planCentroid().z()),
                f(s.correctCentroid().x()), f(s.correctCentroid().y()), f(s.correctCentroid().z()),
                f(deltaX), f(deltaY), f(deltaZ),
                String.valueOf(s.chunkX()), String.valueOf(s.chunkZ())
        )).append('\n');
    }

    private static String pass(boolean b) { return b ? "PASS" : "CHECK"; }

    private static String f(double d) { return String.format(Locale.ROOT, "%.6f", d); }
}
