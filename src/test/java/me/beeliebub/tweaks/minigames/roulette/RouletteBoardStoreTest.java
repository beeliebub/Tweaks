package me.beeliebub.tweaks.minigames.roulette;

import me.beeliebub.tweaks.utils.GeometryUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RouletteBoardStore#serializeBoard}, {@link RouletteBoardStore#deserializeBoard},
 * and {@link RouletteBoardStore#chunkKeysTouchedBy}.
 *
 * <p>Placed in the production package (no {@code tests.} prefix) so package-private access to the
 * static helpers and the {@link RouletteBoardStore.BoardEntry} record is legal without widening
 * production visibility — mirrors the exception already documented in the root {@code CLAUDE.md}'s
 * Test Layout section for {@code BlackjackTableStoreTest} and {@code RouletteGeometryTest}.
 *
 * <p>The format under test is
 * {@code "worldName|cx|cy|cz|minBet|maxBet|scanRadius|ctrlX|ctrlY|ctrlZ|ctrlFacing"} — 11
 * pipe-delimited fields, no optional trailing field (unlike Blackjack's back-color).
 *
 * <p>PDC read/write, the hitbox/control indices, and entity spawning are NOT covered here — those
 * require chunk PDC and {@code Interaction}/{@code BlockDisplay} entity support beyond what
 * MockBukkit's world PDC provides cleanly. They are left to real-server smoke tests (see the
 * package {@code CLAUDE.md}).
 */
class RouletteBoardStoreTest {

    private ServerMock server;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("tweaks");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -------------------------------------------------------------------------
    // Happy-path round-trip
    // -------------------------------------------------------------------------

    @Test
    void roundTripPreservesAllFields() {
        World world = server.addSimpleWorld("testworld");
        Location center = new Location(world, 10.5, 65.0, -3.5);
        Location control = new Location(world, 12, 65, -5);

        RouletteBoardStore.BoardEntry entry = new RouletteBoardStore.BoardEntry(
                center, 10, 500, 16, control, BlockFace.NORTH, true);
        String serialized = RouletteBoardStore.serializeBoard(entry);
        RouletteBoardStore.BoardEntry roundTripped = RouletteBoardStore.deserializeBoard(serialized);

        assertNotNull(roundTripped, "deserializeBoard must return a non-null entry for valid input");
        assertEquals("testworld", roundTripped.center().getWorld().getName(), "world name must round-trip");
        assertEquals(10.5, roundTripped.center().x(), 1e-9, "x must round-trip");
        assertEquals(65.0, roundTripped.center().y(), 1e-9, "y must round-trip");
        assertEquals(-3.5, roundTripped.center().z(), 1e-9, "z must round-trip");
        assertEquals(10, roundTripped.minBet(), "minBet must round-trip");
        assertEquals(500, roundTripped.maxBet(), "maxBet must round-trip");
        assertEquals(16, roundTripped.scanRadius(), "scanRadius must round-trip");
        assertEquals(12, roundTripped.control().getBlockX(), "control x must round-trip");
        assertEquals(65, roundTripped.control().getBlockY(), "control y must round-trip");
        assertEquals(-5, roundTripped.control().getBlockZ(), "control z must round-trip");
        assertEquals(BlockFace.NORTH, roundTripped.controlFacing(), "facing must round-trip");
        assertTrue(roundTripped.discordEnabled(), "Discord designation must round-trip");
    }

    @Test
    void serializedFormatIsTwelvePipeDelimitedFields() {
        World world = server.addSimpleWorld("overworld");
        Location center = new Location(world, 1.0, 2.0, 3.0);
        Location control = new Location(world, 1, 2, 0);

        RouletteBoardStore.BoardEntry entry = new RouletteBoardStore.BoardEntry(
                center, 5, 50, 16, control, BlockFace.WEST, false);
        String serialized = RouletteBoardStore.serializeBoard(entry);

        String[] parts = serialized.split("\\|", -1);
        assertEquals(12, parts.length, "serialized board string must have exactly 12 pipe-delimited parts");
        assertEquals("overworld", parts[0], "first part must be the world name");
        assertEquals("5", parts[4], "fifth part (index 4) must be minBet");
        assertEquals("50", parts[5], "sixth part (index 5) must be maxBet");
        assertEquals("16", parts[6], "seventh part (index 6) must be scanRadius");
        assertEquals(BlockFace.WEST.name(), parts[10], "BlockFace must be index 10");
        assertEquals("0", parts[11], "false designation must be encoded as 0");
    }

    @Test
    void allFourCardinalFacingsRoundTrip() {
        World world = server.addSimpleWorld("facingworld");
        Location center = new Location(world, 5.0, 64.0, 5.0);
        Location control = new Location(world, 5, 64, 3);

        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            RouletteBoardStore.BoardEntry entry = new RouletteBoardStore.BoardEntry(center, 1, 100, 16, control, face, false);
            String serialized = RouletteBoardStore.serializeBoard(entry);
            RouletteBoardStore.BoardEntry roundTripped = RouletteBoardStore.deserializeBoard(serialized);
            assertNotNull(roundTripped, "deserializeBoard must succeed for facing " + face);
            assertEquals(face, roundTripped.controlFacing(), "facing must round-trip for " + face);
        }
    }

    // -------------------------------------------------------------------------
    // Negative-path: malformed input returns null
    // -------------------------------------------------------------------------

    @Test
    void tooFewPartsReturnsNull() {
        assertNull(RouletteBoardStore.deserializeBoard("world|10.0|65.0|20.0|1|100|16|10|65|20"),
                "string with fewer than 11 parts must return null");
    }

    @Test
    void tooManyPartsReturnsNull() {
        assertNull(RouletteBoardStore.deserializeBoard("world|10.0|65.0|20.0|1|100|16|10|65|20|NORTH|extra"),
                "string with more than 11 parts must return null");
    }

    @Test
    void emptyStringReturnsNull() {
        assertNull(RouletteBoardStore.deserializeBoard(""), "empty string must return null");
    }

    @Test
    void singlePartReturnsNull() {
        assertNull(RouletteBoardStore.deserializeBoard("justworld"), "single-token string must return null");
    }

    @Test
    void nonNumericCenterCoordinateReturnsNull() {
        assertNull(RouletteBoardStore.deserializeBoard("world|notanumber|65.0|0.0|1|100|16|10|65|0|NORTH"),
                "unparseable x coordinate must cause deserializeBoard to return null");
    }

    @Test
    void nonNumericMinBetReturnsNull() {
        assertNull(RouletteBoardStore.deserializeBoard("world|10.5|65.0|0.0|notabet|100|16|10|65|0|NORTH"),
                "unparseable minBet must cause deserializeBoard to return null");
    }

    @Test
    void nonNumericScanRadiusReturnsNull() {
        assertNull(RouletteBoardStore.deserializeBoard("world|10.5|65.0|0.0|1|100|notaradius|10|65|0|NORTH"),
                "unparseable scanRadius must cause deserializeBoard to return null");
    }

    @Test
    void invalidBlockFaceNameReturnsNull() {
        assertNull(RouletteBoardStore.deserializeBoard("world|10.5|65.0|0.0|1|100|16|10|65|0|NOTAFACE"),
                "an invalid BlockFace name must cause deserializeBoard to return null");
    }

    @Test
    void unknownWorldReturnsNull() {
        assertNull(RouletteBoardStore.deserializeBoard("missingworld|10.5|65.0|0.0|1|100|16|10|65|0|NORTH"),
                "a world that is not loaded must cause deserializeBoard to return null");
    }

    @Test
    void legacyElevenFieldRowDefaultsToUndesignated() {
        World world = server.addSimpleWorld("legacyworld");
        RouletteBoardStore.BoardEntry entry = RouletteBoardStore.deserializeBoard(
                "legacyworld|10.5|65.0|0.5|1|100|16|10|65|0|NORTH");
        assertNotNull(entry);
        assertFalse(entry.discordEnabled());
        assertEquals(world, entry.center().getWorld());
    }

    @Test
    void invalidDesignationAndNonFiniteRowsReturnNull() {
        server.addSimpleWorld("validworld");
        assertNull(RouletteBoardStore.deserializeBoard(
                "validworld|10|65|0|1|100|16|10|65|0|NORTH|2"));
        assertNull(RouletteBoardStore.deserializeBoard(
                "validworld|10|65|0|1|100|16|10|65|0|NORTH|"));
        assertNull(RouletteBoardStore.deserializeBoard(
                "validworld|NaN|65|0|1|100|16|10|65|0|NORTH"));
        assertNull(RouletteBoardStore.deserializeBoard(
                "validworld|10|65|0|0|100|16|10|65|0|NORTH"));
    }

    @Test
    void designationUpdatesOnlyMatchingPhysicalRowAndInvalidatesCache() {
        World world = server.addSimpleWorld("designationworld");
        Location center = new Location(world, 10.5, 65, 0.5);
        Location control = new Location(world, 12, 65, 0);
        RouletteBoardStore.BoardEntry entry = new RouletteBoardStore.BoardEntry(
                center, 1, 100, 16, control, BlockFace.NORTH, false);
        RouletteBoardStore.BoardEntry sibling = new RouletteBoardStore.BoardEntry(
                new Location(world, 50.5, 65, 0.5), 2, 200, 16,
                new Location(world, 52, 65, 0), BlockFace.SOUTH, false);
        RouletteBoardStore store = new RouletteBoardStore(plugin);
        store.persistBoard(entry);
        store.persistBoard(sibling);
        store.loadBoardsForWorld(world); // prime the cache

        RouletteBoardStore.BoardEntry designated = store.setDiscordDesignation(entry, true);

        assertNotNull(designated);
        assertTrue(designated.discordEnabled());
        List<RouletteBoardStore.BoardEntry> reloaded = store.loadBoardsForWorld(world);
        assertTrue(reloaded.stream().anyMatch(RouletteBoardStore.BoardEntry::discordEnabled));
        assertTrue(reloaded.contains(sibling));
        assertNull(store.setDiscordDesignation(
                new RouletteBoardStore.BoardEntry(new Location(world, 99, 65, 99), 1, 1, 1,
                        new Location(world, 99, 65, 99), BlockFace.NORTH, false), true));
    }

    @Test
    void designatedActiveBoardFiltersInactiveRuntimeEntries() {
        World world = server.addSimpleWorld("active-designation");
        RouletteBoardStore.BoardEntry entry = new RouletteBoardStore.BoardEntry(
                new Location(world, 10, 65, 10), 1, 100, 2,
                new Location(world, 10, 65, 10), BlockFace.NORTH, true);
        RouletteBoardStore store = new RouletteBoardStore(plugin);
        store.persistBoard(entry);
        assertTrue(store.designatedActiveBoard().isEmpty());
        store.registerHitboxes(entry, Map.of(UUID.randomUUID(),
                new RouletteBoardStore.SegmentRef(entry, BetType.STRAIGHT, 1)));
        assertEquals(entry, store.designatedActiveBoard().orElseThrow());
    }

    // -------------------------------------------------------------------------
    // chunkKeysTouchedBy
    // -------------------------------------------------------------------------

    @Test
    void chunkKeysTouchedByCoversSingleChunkForSmallRadius() {
        World world = server.addSimpleWorld("chunkworld");
        // Center well inside chunk (0,0) with a small radius that never crosses a chunk border.
        Location center = new Location(world, 8, 65, 8);
        Location control = new Location(world, 8, 65, 8);
        RouletteBoardStore.BoardEntry entry = new RouletteBoardStore.BoardEntry(center, 1, 100, 2, control, BlockFace.NORTH, false);

        long[] keys = RouletteBoardStore.chunkKeysTouchedBy(entry);

        assertEquals(1, keys.length, "a small radius fully inside one chunk must touch exactly one chunk");
        assertEquals(GeometryUtil.chunkKey(0, 0), keys[0]);
    }

    @Test
    void chunkKeysTouchedByCoversMultipleChunksForLargeRadius() {
        World world = server.addSimpleWorld("chunkworld2");
        Location center = new Location(world, 0, 65, 0);
        Location control = new Location(world, 0, 65, 0);
        // Radius 16 from origin spans chunks -1..1 on both axes (block range -16..16).
        RouletteBoardStore.BoardEntry entry = new RouletteBoardStore.BoardEntry(center, 1, 100, 16, control, BlockFace.NORTH, false);

        long[] keys = RouletteBoardStore.chunkKeysTouchedBy(entry);

        assertEquals(9, keys.length, "a radius-16 footprint centered at the origin must touch a 3x3 chunk grid");
    }
}
