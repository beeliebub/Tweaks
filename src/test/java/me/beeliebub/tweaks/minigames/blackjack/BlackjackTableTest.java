package me.beeliebub.tweaks.minigames.blackjack;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BlackjackListener#serializeTable} and
 * {@link BlackjackListener#deserializeTable}.
 *
 * <p>Placed in the production package so that package-private access to the
 * static helpers and the {@link BlackjackListener.TableEntry} record is legal
 * without modifying production visibility. The test source root is
 * {@code src/test/java}, so no production bytecode is altered.
 *
 * <p>The format under test is {@code "worldName|cx|cy|cz|bet|bx|by|bz|facing[|backColor]"}
 * where cx/cy/cz are doubles, bet is an int, bx/by/bz are ints (middle-button
 * block coordinates), facing is a {@link BlockFace} name string, and the optional
 * 10th field backColor is a decimal RGB integer.
 *
 * <p>Note: the 2x3 solid-block detection logic ({@code findTableCenterFromButton}),
 * entity/hologram spawning, and PDC persistence are NOT covered here —
 * those paths require physical block placement and {@code Display}/{@code Interaction}
 * entity support that MockBukkit does not fully provide. They are left to
 * real-server smoke tests.
 */
class BlackjackTableTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
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
        Location middleButton = new Location(world, 10, 65, -5);
        int bet = 250;

        String serialized = BlackjackListener.serializeTable(center, bet, middleButton, BlockFace.NORTH, null);
        BlackjackListener.TableEntry entry = BlackjackListener.deserializeTable(serialized);

        assertNotNull(entry, "deserializeTable must return a non-null entry for valid input");
        assertEquals("testworld", entry.center().getWorld().getName(),
                "world name must round-trip");
        assertEquals(10.5,  entry.center().x(),   1e-9, "x must round-trip");
        assertEquals(65.0,  entry.center().y(),   1e-9, "y must round-trip");
        assertEquals(-3.5,  entry.center().z(),   1e-9, "z must round-trip");
        assertEquals(bet,   entry.bet(),           "bet must round-trip");
        assertEquals(10,    entry.middleButton().getBlockX(), "button x must round-trip");
        assertEquals(65,    entry.middleButton().getBlockY(), "button y must round-trip");
        assertEquals(-5,    entry.middleButton().getBlockZ(), "button z must round-trip");
        assertEquals(BlockFace.NORTH, entry.facing(), "facing must round-trip");
        assertNull(entry.backColor(), "backColor must be null when not supplied");
    }

    @Test
    void integerCoordinatesRoundTrip() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0.0, 64.0, 0.0);
        Location middleButton = new Location(world, 0, 64, -1);

        String serialized = BlackjackListener.serializeTable(center, 100, middleButton, BlockFace.SOUTH, null);
        BlackjackListener.TableEntry entry = BlackjackListener.deserializeTable(serialized);

        assertNotNull(entry);
        assertEquals(0.0,  entry.center().x(), 1e-9);
        assertEquals(64.0, entry.center().y(), 1e-9);
        assertEquals(0.0,  entry.center().z(), 1e-9);
        assertEquals(100,  entry.bet());
        assertEquals(0,    entry.middleButton().getBlockX());
        assertEquals(64,   entry.middleButton().getBlockY());
        assertEquals(-1,   entry.middleButton().getBlockZ());
        assertEquals(BlockFace.SOUTH, entry.facing());
    }

    @Test
    void halfBlockCentreCoordinatesRoundTrip() {
        // The actual table center is always a .5 value on X and Z when the block
        // footprint has even width/depth (2x3). Pin this contract.
        World world = server.addSimpleWorld("casino");
        Location center = new Location(world, 100.5, 72.0, 200.5);
        Location middleButton = new Location(world, 100, 72, 198);

        String serialized = BlackjackListener.serializeTable(center, 50, middleButton, BlockFace.EAST, null);
        BlackjackListener.TableEntry entry = BlackjackListener.deserializeTable(serialized);

        assertNotNull(entry);
        assertEquals(100.5, entry.center().x(), 1e-9);
        assertEquals(200.5, entry.center().z(), 1e-9);
        assertEquals(BlockFace.EAST, entry.facing());
    }

    @Test
    void serializedFormatIsCorrectPipeDelimitedString() {
        World world = server.addSimpleWorld("overworld");
        Location center = new Location(world, 1.0, 2.0, 3.0);
        Location middleButton = new Location(world, 1, 2, 0);

        String serialized = BlackjackListener.serializeTable(center, 10, middleButton, BlockFace.WEST, null);

        // Without backColor, format is "worldName|cx|cy|cz|bet|bx|by|bz|facing" (9 fields).
        String[] parts = serialized.split("\\|", -1);
        assertEquals(9, parts.length, "serialized string without backColor must have exactly 9 pipe-delimited parts");
        assertEquals("overworld",       parts[0], "first part must be the world name");
        assertEquals("10",              parts[4], "fifth part (index 4) must be the bet as an integer string");
        assertEquals(BlockFace.WEST.name(), parts[8], "last part (index 8) must be the BlockFace name");
    }

    @Test
    void allFourCardinalFacingsRoundTrip() {
        World world = server.addSimpleWorld("facingworld");
        Location center = new Location(world, 5.0, 64.0, 5.0);
        Location btn = new Location(world, 5, 64, 3);

        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            String serialized = BlackjackListener.serializeTable(center, 100, btn, face, null);
            BlackjackListener.TableEntry entry = BlackjackListener.deserializeTable(serialized);
            assertNotNull(entry, "deserializeTable must succeed for facing " + face);
            assertEquals(face, entry.facing(), "facing must round-trip for " + face);
        }
    }

    // -------------------------------------------------------------------------
    // Negative-path: malformed input returns null
    // -------------------------------------------------------------------------

    @Test
    void tooFewPartsReturnsNull() {
        // Only 5 parts — missing button coords and facing.
        assertNull(BlackjackListener.deserializeTable("world|10.0|65.0|20.0|100"),
                "string with fewer than 9 parts must return null");
    }

    @Test
    void eightPartsReturnsNull() {
        // 8 parts — missing the facing field.
        assertNull(BlackjackListener.deserializeTable("world|10.0|65.0|20.0|100|10|65|20"),
                "string with 8 parts (missing facing) must return null");
    }

    @Test
    void emptyStringReturnsNull() {
        assertNull(BlackjackListener.deserializeTable(""),
                "empty string must return null");
    }

    @Test
    void singlePartReturnsNull() {
        assertNull(BlackjackListener.deserializeTable("justworld"),
                "single-token string must return null");
    }

    @Test
    void nonNumericXReturnsNull() {
        assertNull(BlackjackListener.deserializeTable("world|notanumber|65.0|0.0|100|10|65|0|NORTH"),
                "unparseable x coordinate must cause deserializeTable to return null");
    }

    @Test
    void nonNumericYReturnsNull() {
        assertNull(BlackjackListener.deserializeTable("world|10.5|bad|0.0|100|10|65|0|NORTH"),
                "unparseable y coordinate must cause deserializeTable to return null");
    }

    @Test
    void nonNumericZReturnsNull() {
        assertNull(BlackjackListener.deserializeTable("world|10.5|65.0|notaz|100|10|65|0|NORTH"),
                "unparseable z coordinate must cause deserializeTable to return null");
    }

    @Test
    void nonNumericBetReturnsNull() {
        assertNull(BlackjackListener.deserializeTable("world|10.5|65.0|0.0|notabet|10|65|0|NORTH"),
                "unparseable bet value must cause deserializeTable to return null");
    }

    @Test
    void nonNumericButtonXReturnsNull() {
        assertNull(BlackjackListener.deserializeTable("world|10.5|65.0|0.0|100|notanumber|65|0|NORTH"),
                "unparseable button x coordinate must cause deserializeTable to return null");
    }

    @Test
    void nonNumericButtonYReturnsNull() {
        assertNull(BlackjackListener.deserializeTable("world|10.5|65.0|0.0|100|10|bad|0|NORTH"),
                "unparseable button y coordinate must cause deserializeTable to return null");
    }

    @Test
    void nonNumericButtonZReturnsNull() {
        assertNull(BlackjackListener.deserializeTable("world|10.5|65.0|0.0|100|10|65|notaz|NORTH"),
                "unparseable button z coordinate must cause deserializeTable to return null");
    }

    @Test
    void invalidBlockFaceNameReturnsNull() {
        assertNull(BlackjackListener.deserializeTable("world|10.5|65.0|0.0|100|10|65|0|NOTAFACE"),
                "an invalid BlockFace name must cause deserializeTable to return null");
    }

    @Test
    void unknownWorldReturnsNull() {
        // "missingworld" was never added to the server mock, so Bukkit.getWorld() returns null.
        assertNull(BlackjackListener.deserializeTable("missingworld|10.5|65.0|0.0|100|10|65|0|NORTH"),
                "a world that is not loaded must cause deserializeTable to return null");
    }

    @Test
    void pipeInWorldNameDocumentedBehaviour() {
        // Document the known limitation: world names containing "|" would split
        // incorrectly with the chosen format. This test records current behaviour —
        // deserialize returns null (wrong field count or parse error) rather than
        // silently producing a bad entry.
        // This is acceptable because Bukkit world names cannot contain "|".
        //
        // With split("\\|", 9): parts[0]="world", parts[1]="nether", parts[2]="10.0",
        // parts[3]="65.0", parts[4]="0.0|100|10|65|0|NORTH" — bet parse will fail
        // because "0.0|100|10|65|0|NORTH" is not a valid integer.
        String weird = "world|nether|10.0|65.0|0.0|100|10|65|0|NORTH";
        BlackjackListener.TableEntry entry = BlackjackListener.deserializeTable(weird);
        assertNull(entry, "pipe in world name produces an unparseable field");
    }

    // -------------------------------------------------------------------------
    // Back-color round-trip (10-field format)
    // -------------------------------------------------------------------------

    @Test
    void backColorRoundTrips() {
        World world = server.addSimpleWorld("colorworld");
        Location center = new Location(world, 5.5, 65.0, 8.5);
        Location middleButton = new Location(world, 5, 65, 6);
        int backColor = 0xFF8800; // orange

        String serialized = BlackjackListener.serializeTable(center, 100, middleButton, BlockFace.NORTH, backColor);
        BlackjackListener.TableEntry entry = BlackjackListener.deserializeTable(serialized);

        assertNotNull(entry);
        assertEquals(backColor & 0xFFFFFF, entry.backColor(),
                "backColor must round-trip through serialize/deserialize");
    }

    @Test
    void serializedFormatWithBackColorHasTenParts() {
        World world = server.addSimpleWorld("tenpartworld");
        Location center = new Location(world, 1.0, 64.0, 1.0);
        Location middleButton = new Location(world, 1, 64, -1);

        String serialized = BlackjackListener.serializeTable(center, 50, middleButton, BlockFace.SOUTH, 0x0000FF);
        String[] parts = serialized.split("\\|", -1);
        assertEquals(10, parts.length, "serialized string with backColor must have exactly 10 pipe-delimited parts");
        assertEquals("255", parts[9], "10th part must be the decimal RGB integer for backColor 0x0000FF = 255");
    }

    @Test
    void legacyNineFieldStringDeserializesWithNullBackColor() {
        World world = server.addSimpleWorld("legacyworld");
        // Manually craft a legacy 9-field string (no backColor field).
        String legacy = "legacyworld|5.5|65.0|8.5|100|5|65|6|NORTH";
        BlackjackListener.TableEntry entry = BlackjackListener.deserializeTable(legacy);

        assertNotNull(entry, "legacy 9-field string must still deserialize successfully");
        assertNull(entry.backColor(), "backColor must be null for legacy 9-field entries");
        assertEquals(100, entry.bet());
        assertEquals(BlockFace.NORTH, entry.facing());
    }

    // -------------------------------------------------------------------------
    // Multiple worlds do not cross-contaminate resolution
    // -------------------------------------------------------------------------

    @Test
    void tablesInDifferentWorldsResolveToCorrectWorld() {
        World alpha = server.addSimpleWorld("alpha");
        World beta  = server.addSimpleWorld("beta");

        Location btnAlpha = new Location(alpha, 1, 64, -1);
        Location btnBeta  = new Location(beta,  2, 64, -1);

        String sAlpha = BlackjackListener.serializeTable(new Location(alpha, 1.0, 64.0, 1.0), 10, btnAlpha, BlockFace.NORTH, null);
        String sBeta  = BlackjackListener.serializeTable(new Location(beta,  2.0, 64.0, 2.0), 20, btnBeta,  BlockFace.SOUTH, null);

        BlackjackListener.TableEntry eAlpha = BlackjackListener.deserializeTable(sAlpha);
        BlackjackListener.TableEntry eBeta  = BlackjackListener.deserializeTable(sBeta);

        assertNotNull(eAlpha);
        assertNotNull(eBeta);
        assertEquals("alpha", eAlpha.center().getWorld().getName());
        assertEquals("beta",  eBeta.center().getWorld().getName());
        assertEquals(10, eAlpha.bet());
        assertEquals(20, eBeta.bet());
    }
}
