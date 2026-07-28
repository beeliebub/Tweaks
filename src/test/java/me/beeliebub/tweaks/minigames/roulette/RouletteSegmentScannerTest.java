package me.beeliebub.tweaks.minigames.roulette;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RouletteSegmentScanner#parseTag} and
 * {@link RouletteSegmentScanner#validateCompleteness}. Both are pure functions over strings/records
 * with no Bukkit dependency, so no MockBukkit is needed — the {@code scan()} entry point itself
 * (which walks live {@code BlockDisplay} entities) is left to real-server smoke tests.
 *
 * <p>Placed in the production package (no {@code tests.} prefix) so package-private access to
 * {@link RouletteSegmentScanner}'s static helpers and nested records is legal without widening
 * production visibility — the same Test Layout exception already used by
 * {@code RouletteGeometryTest} and {@code RouletteBoardStoreTest}.
 */
class RouletteSegmentScannerTest {

    // -------------------------------------------------------------------------
    // parseTag
    // -------------------------------------------------------------------------

    @Test
    void parsesStraightPocketZero() {
        RouletteSegmentScanner.ParsedRef ref = RouletteSegmentScanner.parseTag("roulette_00");
        assertNotNull(ref);
        assertEquals(BetType.STRAIGHT, ref.type());
        assertEquals(0, ref.selector());
    }

    @Test
    void parsesStraightPocketThirtySix() {
        RouletteSegmentScanner.ParsedRef ref = RouletteSegmentScanner.parseTag("roulette_36");
        assertNotNull(ref);
        assertEquals(BetType.STRAIGHT, ref.type());
        assertEquals(36, ref.selector());
    }

    @Test
    void parsesUnpaddedStraightPocket() {
        RouletteSegmentScanner.ParsedRef ref = RouletteSegmentScanner.parseTag("roulette_7");
        assertNotNull(ref);
        assertEquals(BetType.STRAIGHT, ref.type());
        assertEquals(7, ref.selector());
    }

    @Test
    void rejectsOutOfRangeStraightPocket() {
        assertNull(RouletteSegmentScanner.parseTag("roulette_37"),
                "pocket 37 is out of the 0-36 range and must not classify");
    }

    @Test
    void parsesDozenTags() {
        for (int i = 1; i <= 3; i++) {
            RouletteSegmentScanner.ParsedRef ref = RouletteSegmentScanner.parseTag("roulette_thirds_0" + i);
            assertNotNull(ref, "roulette_thirds_0" + i + " must classify");
            assertEquals(BetType.DOZEN, ref.type());
            assertEquals(i, ref.selector());
        }
    }

    @Test
    void rejectsOutOfRangeDozenSelector() {
        assertNull(RouletteSegmentScanner.parseTag("roulette_thirds_04"),
                "dozen selector 4 is out of the 1-3 range and must not classify");
        assertNull(RouletteSegmentScanner.parseTag("roulette_thirds_00"),
                "dozen selector 0 is out of the 1-3 range and must not classify");
    }

    @Test
    void parsesColorTags() {
        RouletteSegmentScanner.ParsedRef red = RouletteSegmentScanner.parseTag("roulette_color_red");
        RouletteSegmentScanner.ParsedRef black = RouletteSegmentScanner.parseTag("roulette_color_black");

        assertNotNull(red);
        assertEquals(BetType.COLOR, red.type());
        assertEquals(RouletteBet.COLOR_RED, red.selector());

        assertNotNull(black);
        assertEquals(BetType.COLOR, black.type());
        assertEquals(RouletteBet.COLOR_BLACK, black.selector());
    }

    @Test
    void rejectsUnknownTag() {
        assertNull(RouletteSegmentScanner.parseTag("roulette_color_green"),
                "an unrecognised color tag must not classify");
        assertNull(RouletteSegmentScanner.parseTag("not_a_roulette_tag"));
        assertNull(RouletteSegmentScanner.parseTag(""));
    }

    @Test
    void rejectsMalformedNumericSuffix() {
        assertNull(RouletteSegmentScanner.parseTag("roulette_abc"),
                "a non-numeric pocket suffix must not classify");
        assertNull(RouletteSegmentScanner.parseTag("roulette_"),
                "an empty pocket suffix must not classify");
    }

    // -------------------------------------------------------------------------
    // validateCompleteness
    // -------------------------------------------------------------------------

    /** Builds a complete, valid 48-segment board: 37 STRAIGHT + 9 (3-per-tag) DOZEN + 2 COLOR. */
    private static List<RouletteSegmentScanner.SegmentScan> completeBoard() {
        List<RouletteSegmentScanner.SegmentScan> segments = new ArrayList<>();
        RouletteGeometry.Vec3 zero = new RouletteGeometry.Vec3(0, 0, 0);
        RouletteGeometry.Quat identity = new RouletteGeometry.Quat(0, 0, 0, 1);

        for (int pocket = 0; pocket <= 36; pocket++) {
            segments.add(segment(BetType.STRAIGHT, pocket, "roulette_" + pocket, zero, identity));
        }
        for (int dozen = 1; dozen <= 3; dozen++) {
            for (int arc = 0; arc < 3; arc++) {
                segments.add(segment(BetType.DOZEN, dozen, "roulette_thirds_0" + dozen, zero, identity));
            }
        }
        segments.add(segment(BetType.COLOR, RouletteBet.COLOR_RED, "roulette_color_red", zero, identity));
        segments.add(segment(BetType.COLOR, RouletteBet.COLOR_BLACK, "roulette_color_black", zero, identity));
        return segments;
    }

    private static RouletteSegmentScanner.SegmentScan segment(BetType type, int selector, String rawTag,
                                                               RouletteGeometry.Vec3 zero, RouletteGeometry.Quat identity) {
        return new RouletteSegmentScanner.SegmentScan(
                UUID.randomUUID(), type, selector, rawTag, zero, zero, identity, zero, identity);
    }

    @Test
    void completeBoardHasNoProblems() {
        List<String> problems = RouletteSegmentScanner.validateCompleteness(completeBoard());
        assertTrue(problems.isEmpty(), "a full 48-segment board must have no problems: " + problems);
    }

    @Test
    void completeBoardHasExactlyFortyEightSegments() {
        assertEquals(48, completeBoard().size());
    }

    @Test
    void missingStraightPocketIsReported() {
        List<RouletteSegmentScanner.SegmentScan> segments = new ArrayList<>(completeBoard());
        segments.removeIf(s -> s.type() == BetType.STRAIGHT && s.selector() == 19);

        List<String> problems = RouletteSegmentScanner.validateCompleteness(segments);

        assertTrue(problems.stream().anyMatch(p -> p.contains("Missing STRAIGHT pocket 19")),
                "removing pocket 19 must be reported as missing: " + problems);
    }

    @Test
    void duplicateStraightPocketIsReported() {
        List<RouletteSegmentScanner.SegmentScan> segments = new ArrayList<>(completeBoard());
        RouletteGeometry.Vec3 zero = new RouletteGeometry.Vec3(0, 0, 0);
        RouletteGeometry.Quat identity = new RouletteGeometry.Quat(0, 0, 0, 1);
        segments.add(segment(BetType.STRAIGHT, 5, "roulette_5", zero, identity));

        List<String> problems = RouletteSegmentScanner.validateCompleteness(segments);

        assertTrue(problems.stream().anyMatch(p -> p.contains("Duplicate STRAIGHT pocket 5")),
                "a duplicate pocket 5 must be reported: " + problems);
    }

    @Test
    void dozenWithWrongArcCountIsReported() {
        List<RouletteSegmentScanner.SegmentScan> segments = new ArrayList<>(completeBoard());
        // Remove one of dozen 2's three physical arc segments.
        boolean removedOne = segments.removeIf(s -> s.type() == BetType.DOZEN && s.selector() == 2);
        assertTrue(removedOne);
        RouletteGeometry.Vec3 zero = new RouletteGeometry.Vec3(0, 0, 0);
        RouletteGeometry.Quat identity = new RouletteGeometry.Quat(0, 0, 0, 1);
        // Add back only 2 of the 3.
        segments.add(segment(BetType.DOZEN, 2, "roulette_thirds_02", zero, identity));
        segments.add(segment(BetType.DOZEN, 2, "roulette_thirds_02", zero, identity));

        List<String> problems = RouletteSegmentScanner.validateCompleteness(segments);

        assertTrue(problems.stream().anyMatch(p -> p.contains("DOZEN 2 has 2 physical segments, expected 3")),
                "a dozen with only 2 physical segments must be reported: " + problems);
    }

    @Test
    void missingColorIsReported() {
        List<RouletteSegmentScanner.SegmentScan> segments = new ArrayList<>(completeBoard());
        segments.removeIf(s -> s.type() == BetType.COLOR && s.selector() == RouletteBet.COLOR_BLACK);

        List<String> problems = RouletteSegmentScanner.validateCompleteness(segments);

        assertTrue(problems.stream().anyMatch(p -> p.contains("Missing COLOR black")),
                "removing the black color segment must be reported: " + problems);
    }

    @Test
    void emptyBoardReportsEveryPocketDozenAndColorMissing() {
        List<String> problems = RouletteSegmentScanner.validateCompleteness(List.of());
        // 37 missing pockets + 3 missing dozens + 2 missing colors = 42 problems.
        assertEquals(42, problems.size());
    }
}
