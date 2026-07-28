package me.beeliebub.tweaks.minigames.roulette;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Production tag-to-bet parsing and centroid capture for a live physical roulette board. Unlike
 * the diagnostic {@code RouletteScanCommand}/{@code ScannedSegment}/{@code RouletteTagParser}
 * (kept separate per the package {@code CLAUDE.md}), this is the scan a real board
 * registration/reactivation runs: it walks nearby {@code BlockDisplay}s tagged {@code roulette_*},
 * classifies each into a {@link BetType} + selector (its own parser — {@code RouletteTagParser}'s
 * {@link Ring} vocabulary and color-blind selector don't map onto {@link BetType} directly), and
 * validates the fixed 37 STRAIGHT + 9 (3-per-tag) DOZEN + 2 COLOR = 48 segment contract.
 */
final class RouletteSegmentScanner {

    private static final String THIRDS_PREFIX = "roulette_thirds_";
    private static final String COLOR_RED_TAG = "roulette_color_red";
    private static final String COLOR_BLACK_TAG = "roulette_color_black";
    private static final String POCKET_PREFIX = "roulette_";

    private RouletteSegmentScanner() {}

    /** One physical segment entity, classified and with its transform captured at scan time. */
    record SegmentScan(UUID segmentEntityId, BetType type, int selector, String rawTag,
                        RouletteGeometry.Vec3 centroid, RouletteGeometry.Vec3 translation,
                        RouletteGeometry.Quat leftRotation, RouletteGeometry.Vec3 scale,
                        RouletteGeometry.Quat rightRotation) {}

    /**
     * Result of one scan attempt. {@code problems} is empty iff the board is complete
     * (exactly 37 STRAIGHT + 3x3 DOZEN + 2 COLOR); {@code wheelCentre} is {@code null} whenever
     * {@code problems} is non-empty — an incomplete scan has nothing trustworthy to average.
     */
    record ScanOutcome(List<SegmentScan> segments, List<String> problems, RouletteGeometry.Vec3 wheelCentre) {
        boolean isComplete() {
            return problems.isEmpty();
        }
    }

    /** Package-visible so tests can call {@link #parseTag} directly. */
    record ParsedRef(BetType type, int selector) {}

    /**
     * Walks entities within {@code radius} of {@code origin}, classifies tagged BlockDisplays.
     * A single malformed segment (e.g. corrupted transform data) is logged and skipped rather
     * than aborting the scan for the other 47 — mirrors the diagnostic {@code RouletteScanCommand.scan}
     * convention of guarding each individual capture.
     */
    static ScanOutcome scan(Location origin, int radius, JavaPlugin plugin) {
        List<SegmentScan> segments = new ArrayList<>();
        for (Entity entity : origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!(entity instanceof BlockDisplay display)) {
                continue;
            }
            for (String tag : display.getScoreboardTags()) {
                ParsedRef ref = parseTag(tag);
                if (ref != null) {
                    try {
                        segments.add(capture(display, ref, tag));
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.WARNING, "Roulette: skipping segment entity "
                                + display.getUniqueId() + " (tag " + tag + ") after a capture error", e);
                    }
                    break; // one roulette_* tag classifies the whole entity; first match wins
                }
            }
        }
        List<String> problems = validateCompleteness(segments);
        RouletteGeometry.Vec3 centre = problems.isEmpty() ? meanCentre(segments) : null;
        return new ScanOutcome(segments, problems, centre);
    }

    private static SegmentScan capture(BlockDisplay display, ParsedRef ref, String rawTag) {
        Location loc = display.getLocation();
        RouletteGeometry.Vec3 entityPos = new RouletteGeometry.Vec3(loc.getX(), loc.getY(), loc.getZ());

        Transformation t = display.getTransformation();
        Vector3f translation = t.getTranslation();
        Quaternionf leftRotation = t.getLeftRotation();
        Vector3f scale = t.getScale();
        Quaternionf rightRotation = t.getRightRotation();

        RouletteGeometry.Vec3 translationVec = new RouletteGeometry.Vec3(translation.x(), translation.y(), translation.z());
        RouletteGeometry.Quat leftRotationQuat = new RouletteGeometry.Quat(leftRotation.x(), leftRotation.y(), leftRotation.z(), leftRotation.w());
        RouletteGeometry.Vec3 scaleVec = new RouletteGeometry.Vec3(scale.x(), scale.y(), scale.z());
        RouletteGeometry.Quat rightRotationQuat = new RouletteGeometry.Quat(rightRotation.x(), rightRotation.y(), rightRotation.z(), rightRotation.w());

        RouletteGeometry.Vec3 centroid = RouletteGeometry.correctCentroid(
                entityPos, translationVec, scaleVec, leftRotationQuat, rightRotationQuat);

        return new SegmentScan(display.getUniqueId(), ref.type(), ref.selector(), rawTag, centroid,
                translationVec, leftRotationQuat, scaleVec, rightRotationQuat);
    }

    /** Never throws — an unrecognised {@code roulette_*} tag simply comes back {@code null}. */
    static ParsedRef parseTag(String tag) {
        if (tag.startsWith(THIRDS_PREFIX)) {
            Integer selector = parsePositiveInt(tag.substring(THIRDS_PREFIX.length()));
            if (selector != null && selector >= 1 && selector <= 3) {
                return new ParsedRef(BetType.DOZEN, selector);
            }
            return null;
        }
        if (tag.equals(COLOR_RED_TAG)) {
            return new ParsedRef(BetType.COLOR, RouletteBet.COLOR_RED);
        }
        if (tag.equals(COLOR_BLACK_TAG)) {
            return new ParsedRef(BetType.COLOR, RouletteBet.COLOR_BLACK);
        }
        if (tag.startsWith(POCKET_PREFIX)) {
            Integer selector = parsePositiveInt(tag.substring(POCKET_PREFIX.length()));
            if (selector != null && selector >= 0 && selector <= 36) {
                return new ParsedRef(BetType.STRAIGHT, selector);
            }
        }
        return null;
    }

    private static Integer parsePositiveInt(String s) {
        if (s.isEmpty() || s.length() > 3) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return null;
            }
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Exactly 37 STRAIGHT pockets (0-36), 3 DOZEN selectors with exactly 3 physical segments
     * each, and both COLOR selectors exactly once — 48 total. Returns one human-readable problem
     * string per violation found; empty means complete. Package-visible so tests can call it
     * directly against hand-built {@link SegmentScan} lists, with no Bukkit/entity dependency.
     */
    static List<String> validateCompleteness(List<SegmentScan> segments) {
        List<String> problems = new ArrayList<>();
        Set<Integer> straightSeen = new HashSet<>();
        Map<Integer, Integer> dozenCounts = new HashMap<>();
        Set<Integer> colorSeen = new HashSet<>();

        for (SegmentScan s : segments) {
            if (!isFinite(s.centroid()) || !isFinite(s.scale())) {
                problems.add("Non-finite geometry for segment " + s.rawTag());
            }
            switch (s.type()) {
                case STRAIGHT -> {
                    if (!straightSeen.add(s.selector())) {
                        problems.add("Duplicate STRAIGHT pocket " + s.selector());
                    }
                }
                case DOZEN -> dozenCounts.merge(s.selector(), 1, Integer::sum);
                case COLOR -> {
                    if (!colorSeen.add(s.selector())) {
                        problems.add("Duplicate COLOR selector " + s.selector());
                    }
                }
            }
        }
        for (int pocket = 0; pocket <= 36; pocket++) {
            if (!straightSeen.contains(pocket)) {
                problems.add("Missing STRAIGHT pocket " + pocket);
            }
        }
        for (int dozen = 1; dozen <= 3; dozen++) {
            int count = dozenCounts.getOrDefault(dozen, 0);
            if (count != 3) {
                problems.add("DOZEN " + dozen + " has " + count + " physical segments, expected 3");
            }
        }
        if (!colorSeen.contains(RouletteBet.COLOR_RED)) {
            problems.add("Missing COLOR red");
        }
        if (!colorSeen.contains(RouletteBet.COLOR_BLACK)) {
            problems.add("Missing COLOR black");
        }
        return problems;
    }

    private static boolean isFinite(RouletteGeometry.Vec3 v) {
        return Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z());
    }

    private static RouletteGeometry.Vec3 meanCentre(List<SegmentScan> segments) {
        double sx = 0, sy = 0, sz = 0;
        for (SegmentScan s : segments) {
            sx += s.centroid().x();
            sy += s.centroid().y();
            sz += s.centroid().z();
        }
        int n = segments.size();
        return new RouletteGeometry.Vec3(sx / n, sy / n, sz / n);
    }
}
