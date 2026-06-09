package me.beeliebub.tweaks.tests.minigames.blackjack;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener;
import me.beeliebub.tweaks.ranks.RankManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Switch;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Geometry-layer tests for PvP Blackjack table detection, registration, and PDC
 * persistence (Tweaks-zejh). These tests exercise:
 * <ul>
 *   <li>{@link BlackjackListener#serializePvpTable} / {@link BlackjackListener#deserializePvpTable}
 *       round-trip fidelity.</li>
 *   <li>PvP button registration in the {@code pvpButtonMap} after a successful setup.</li>
 *   <li>Opposite-side validation: a table with missing side-B buttons is rejected.</li>
 * </ul>
 *
 * <p>The tests do <em>not</em> test gameplay (that is Tweaks-qha4) or entity spawning
 * (which requires a live server). {@link BlackjackListener#spawnPvpTableHologram} and
 * the chunk PDC event handlers are NOT exercised here — they depend on
 * {@link org.bukkit.entity.TextDisplay} spawning, which MockBukkit does not support.
 *
 * <p>PvP button differentiation: buttons registered via
 * {@link BlackjackListener#registerPvpButtonsForTable} land in a separate {@code pvpButtonMap}
 * (accessed via package-visible field). After registration, the test confirms the six expected
 * keys are present and that none of those keys exist in the server-table {@code buttonMap},
 * which proves unambiguous separation.
 */
class BlackjackTableGeometryTest {

    private ServerMock server;
    private Tweaks plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ---- Helpers ------------------------------------------------------------

    /**
     * Build a mocked world where every block at Y=64 in the rectangle
     * [minX..minX+w) x [minZ..minZ+d) is a solid STONE block, and every other
     * block is AIR. Returns a World mock configured to answer {@code getBlockAt}.
     */
    private static World buildTableWorld(int minX, int minZ, int w, int d, int y) {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getUID()).thenReturn(java.util.UUID.randomUUID());

        // Default: any block not explicitly set is AIR (Material.AIR.isSolid() == false).
        Block airBlock = mock(Block.class);
        when(airBlock.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(airBlock);

        // Set the solid support blocks in the footprint.
        for (int x = minX; x < minX + w; x++) {
            for (int z = minZ; z < minZ + d; z++) {
                Block solid = mock(Block.class);
                when(solid.getType()).thenReturn(Material.STONE);
                when(solid.getWorld()).thenReturn(world);
                when(solid.getX()).thenReturn(x);
                when(solid.getY()).thenReturn(y);
                when(solid.getZ()).thenReturn(z);
                Location loc = new Location(world, x, y, z);
                when(solid.getLocation()).thenReturn(loc);
                // isSolid() is called on Material, not Block.
                // We need Material.STONE.isSolid() to return true —
                // MockBukkit initialises real Material enums, so STONE.isSolid() == true.
                when(world.getBlockAt(x, y, z)).thenReturn(solid);
            }
        }
        return world;
    }

    /**
     * Create a mocked wall button {@link Block} for the given position, facing {@code facing},
     * returning it from {@code world.getBlockAt(x, y, z)}.
     */
    private static Block addWallButton(World world, int x, int y, int z, BlockFace facing) {
        Block btn = mock(Block.class);
        when(btn.getType()).thenReturn(Material.OAK_BUTTON);
        when(btn.getWorld()).thenReturn(world);
        when(btn.getX()).thenReturn(x);
        when(btn.getY()).thenReturn(y);
        when(btn.getZ()).thenReturn(z);
        Location loc = new Location(world, x, y, z);
        when(btn.getLocation()).thenReturn(loc);

        Switch sw = mock(Switch.class);
        when(sw.getFacing()).thenReturn(facing);
        when(btn.getBlockData()).thenReturn(sw);

        // Tag.BUTTONS membership check: MockBukkit's Tag.BUTTONS.isTagged() works for
        // OAK_BUTTON since MockBukkit provides real tag implementations.
        when(world.getBlockAt(x, y, z)).thenReturn(btn);
        return btn;
    }

    /**
     * Create a mocked chest block (Container) at the given position.
     */
    private static void addChestBlock(World world, int x, int y, int z) {
        Block b = mock(Block.class);
        when(b.getType()).thenReturn(Material.CHEST);
        when(b.getWorld()).thenReturn(world);
        when(b.getX()).thenReturn(x);
        when(b.getY()).thenReturn(y);
        when(b.getZ()).thenReturn(z);
        Location loc = new Location(world, x, y, z);
        when(b.getLocation()).thenReturn(loc);
        Chest chest = mock(Chest.class);
        when(b.getState()).thenReturn(chest);
        when(world.getBlockAt(x, y, z)).thenReturn(b);
    }

    // ---- serialization round-trip -------------------------------------------

    /**
     * {@code serializePvpTable} followed by {@code deserializePvpTable} must produce
     * exactly 13 pipe-delimited fields and preserve world name, coordinates, facings,
     * and backColor through a round-trip (Tweaks-9c18: physical betting removed).
     *
     * <p>Backward-compatibility: a legacy 17-field string (old format with barrel/money
     * lists in fields 13–16) must also deserialize successfully — the 4 trailing fields
     * are silently ignored.
     */
    @Test
    void serializeDeserializeRoundTrip() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getUID()).thenReturn(java.util.UUID.randomUUID());
        // deserializePvpTable calls Bukkit.getWorld(name) — MockBukkit's Bukkit
        // stub returns null unless the world is registered; bypass this by testing
        // only the serialized string and confirming the field layout.

        Location center  = new Location(world, 1.5,  65.0, 2.5);
        Location middleA = new Location(world, 2,    64,   0);
        Location middleB = new Location(world, 2,    64,   5);

        BlackjackListener.PvpTableEntry entry = new BlackjackListener.PvpTableEntry(
                center, 0xFF8800,
                middleA, BlockFace.NORTH,
                middleB, BlockFace.SOUTH);

        String serialized = BlackjackListener.serializePvpTable(entry);
        assertNotNull(serialized, "serializePvpTable must not return null");

        // Verify the new field layout: exactly 13 pipe-delimited fields.
        String[] fields = serialized.split("\\|", -1);
        assertEquals(13, fields.length,
                "PvP table must serialize to exactly 13 pipe-delimited fields; got: " + serialized);

        // Field[0]: world name
        assertEquals("world", fields[0]);
        // Fields[1-3]: center coords
        assertEquals("1.5",  fields[1]);
        assertEquals("65.0", fields[2]);
        assertEquals("2.5",  fields[3]);
        // Fields[4-6]: middleA coords
        assertEquals("2", fields[4]);
        assertEquals("64", fields[5]);
        assertEquals("0", fields[6]);
        // Field[7]: facingA
        assertEquals("NORTH", fields[7]);
        // Fields[8-10]: middleB coords
        assertEquals("2", fields[8]);
        assertEquals("64", fields[9]);
        assertEquals("5", fields[10]);
        // Field[11]: facingB
        assertEquals("SOUTH", fields[11]);
        // Field[12]: backColor (decimal RGB)
        assertEquals(String.valueOf(0xFF8800), fields[12]);

        // Backward-compatibility: a legacy 17-field string must still deserialize
        // (the 4 trailing barrel/money fields are ignored). Use a MockBukkit-registered
        // world so Bukkit.getWorld resolves.
        World bwWorld = server.addSimpleWorld("bwworld");
        String legacy17 =
                "bwworld|1.5|65.0|2.5|2|64|0|NORTH|2|64|5|SOUTH|16744448|2,64,-1|2,64,6|1,65,-1|";
        BlackjackListener.PvpTableEntry legacyEntry = BlackjackListener.deserializePvpTable(legacy17);
        assertNotNull(legacyEntry,
                "17-field legacy string must deserialize successfully (backward-compat)");
        assertEquals(bwWorld.getName(), legacyEntry.center().getWorld().getName());
        assertEquals("NORTH", legacyEntry.facingA().name());
        assertEquals("SOUTH", legacyEntry.facingB().name());
    }

    /**
     * A serialized string with null backColor must round-trip as an empty field[12].
     */
    @Test
    void serializeNullBackColorProducesEmptyField() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location center  = new Location(world, 0.5, 65.0, 0.5);
        Location middleA = new Location(world, 1, 64, -1);
        Location middleB = new Location(world, 1, 64,  4);

        BlackjackListener.PvpTableEntry entry = new BlackjackListener.PvpTableEntry(
                center, null,
                middleA, BlockFace.NORTH,
                middleB, BlockFace.SOUTH);

        String serialized = BlackjackListener.serializePvpTable(entry);
        String[] fields = serialized.split("\\|", -1);
        assertEquals(13, fields.length, "New format must have exactly 13 fields");
        assertEquals("", fields[12], "null backColor must serialize to an empty field");
    }

    /**
     * {@code deserializePvpTable} must return {@code null} when the input has fewer
     * than 13 pipe-delimited fields (the minimum for the current format).
     */
    @Test
    void deserializeMalformedInputReturnsNull() {
        assertNull(BlackjackListener.deserializePvpTable(""),
                "Empty string must deserialize to null");
        assertNull(BlackjackListener.deserializePvpTable("world|1|2|3|4|5|6|NORTH"),
                "Too few fields (8) must deserialize to null");
        // 12 fields — one short of the 13-field minimum
        assertNull(BlackjackListener.deserializePvpTable("world|1|2|3|4|5|6|NORTH|7|8|9|SOUTH"),
                "12-field string (< 13 minimum) must deserialize to null");
    }

    /**
     * {@code deserializePvpTable} must return {@code null} when a numeric field is
     * not parseable.
     */
    @Test
    void deserializeUnparseableNumberReturnsNull() {
        // 13 fields but cy (field[2]) is "notanumber"
        String raw = "world|1|notanumber|3|4|5|6|NORTH|7|8|9|SOUTH|";
        assertNull(BlackjackListener.deserializePvpTable(raw),
                "13-field string with unparseable cy must deserialize to null");
    }

    // ---- PvP button registration --------------------------------------------

    /**
     * After {@link BlackjackListener#registerPvpButtonsForTable}, the six PvP button
     * locations must be present in {@code pvpButtonMap} keyed as {@code "world:x:y:z"}.
     * None of those keys should appear in the server-table {@code buttonMap}.
     */
    @Test
    void registerPvpButtonsPopulatesPvpButtonMap() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        Location center  = new Location(world, 1.5, 65.0, 2.5);
        Location middleA = new Location(world, 2, 64, 0);  // facing NORTH
        Location middleB = new Location(world, 2, 64, 5);  // facing SOUTH

        // Facing NORTH: left = +X, right = -X
        // Side A: left=(3,64,0), mid=(2,64,0), right=(1,64,0)
        // Facing SOUTH: left = -X, right = +X
        // Side B: left=(1,64,5), mid=(2,64,5), right=(3,64,5)

        BlackjackListener.PvpTableEntry entry = new BlackjackListener.PvpTableEntry(
                center, null,
                middleA, BlockFace.NORTH,
                middleB, BlockFace.SOUTH);

        EconomyManager em = plugin.getEconomyManager();
        RankManager rm    = plugin.getRankManager();
        BlackjackListener listener = new BlackjackListener(plugin, em, rm);

        listener.registerPvpButtonsForTable(entry);

        // Verify the six expected pvpButtonMap keys are populated.
        List<String> expectedKeys = List.of(
                "world:2:64:0",  // side-A middle
                "world:3:64:0",  // side-A left  (NORTH → +X)
                "world:1:64:0",  // side-A right (NORTH → -X)
                "world:2:64:5",  // side-B middle
                "world:1:64:5",  // side-B left  (SOUTH → -X)
                "world:3:64:5"   // side-B right (SOUTH → +X)
        );

        for (String key : expectedKeys) {
            BlackjackListener.PvpButtonRef ref = listener.pvpButtonMap().get(key);
            assertNotNull(ref, "pvpButtonMap must contain key: " + key);
            assertEquals(entry, ref.table(), "PvpButtonRef must point to the registered entry");
        }

        // Confirm none of the PvP keys leaked into the server-table buttonMap.
        for (String key : expectedKeys) {
            assertFalse(listener.buttonMap().containsKey(key),
                    "Server-table buttonMap must not contain PvP key: " + key);
        }
    }

    /**
     * Side-A and side-B are unambiguously identified in the PvP button lookup.
     */
    @Test
    void pvpButtonRefTracksSideCorrectly() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        Location center  = new Location(world, 1.5, 65.0, 2.5);
        Location middleA = new Location(world, 2, 64, 0);
        Location middleB = new Location(world, 2, 64, 5);

        BlackjackListener.PvpTableEntry entry = new BlackjackListener.PvpTableEntry(
                center, null,
                middleA, BlockFace.NORTH,
                middleB, BlockFace.SOUTH);

        EconomyManager em = plugin.getEconomyManager();
        RankManager rm    = plugin.getRankManager();
        BlackjackListener listener = new BlackjackListener(plugin, em, rm);
        listener.registerPvpButtonsForTable(entry);

        // Side A buttons must all have side == 0.
        assertEquals(0, listener.pvpButtonMap().get("world:2:64:0").side(), "side-A middle must be side 0");
        assertEquals(0, listener.pvpButtonMap().get("world:3:64:0").side(), "side-A left must be side 0");
        assertEquals(0, listener.pvpButtonMap().get("world:1:64:0").side(), "side-A right must be side 0");

        // Side B buttons must all have side == 1.
        assertEquals(1, listener.pvpButtonMap().get("world:2:64:5").side(), "side-B middle must be side 1");
        assertEquals(1, listener.pvpButtonMap().get("world:1:64:5").side(), "side-B left must be side 1");
        assertEquals(1, listener.pvpButtonMap().get("world:3:64:5").side(), "side-B right must be side 1");
    }

    /**
     * {@link BlackjackListener#flatCardRotationForFacing} must produce a distinct flat-on-table
     * orientation per seat facing so each player's cards read upright from their own side.
     */
    @Test
    void flatCardRotationDiffersPerFacing() {
        org.joml.Quaternionf north = BlackjackListener.flatCardRotationForFacing(BlockFace.NORTH);
        org.joml.Quaternionf south = BlackjackListener.flatCardRotationForFacing(BlockFace.SOUTH);
        org.joml.Quaternionf east  = BlackjackListener.flatCardRotationForFacing(BlockFace.EAST);
        org.joml.Quaternionf west  = BlackjackListener.flatCardRotationForFacing(BlockFace.WEST);
        assertNotNull(north);
        assertNotNull(south);
        // Opposite seats must not share an orientation, otherwise one side reads upside-down.
        assertNotEquals(north, south, "NORTH and SOUTH seats must use different rotations");
        assertNotEquals(east, west, "EAST and WEST seats must use different rotations");
    }

    // ---- Geometry helpers (static, no server needed) -------------------------

    /**
     * {@link BlackjackListener#isValidTableRect} must return true for a 2×3 rectangle
     * of solid blocks and false if any block is non-solid.
     */
    @Test
    void isValidTableRectTrueForAllSolid() {
        // Build a 2×3 area of solid blocks at Y=64, minX=10, minZ=20.
        World world = buildTableWorld(10, 20, 2, 3, 64);
        assertTrue(BlackjackListener.isValidTableRect(world, 64, 10, 20, 2, 3),
                "2x3 solid rectangle must be valid");
    }

    @Test
    void isValidTableRectFalseIfOneBlockIsAir() {
        World world = buildTableWorld(10, 20, 2, 3, 64);
        // Make block at (11,64,21) return AIR.
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(11, 64, 21)).thenReturn(air);

        assertFalse(BlackjackListener.isValidTableRect(world, 64, 10, 20, 2, 3),
                "Rectangle with one air block must be invalid");
    }

    /**
     * {@link BlackjackListener#findTableCenterFromButton} must return the correct center
     * for a button on the NORTH face of a support block at the near edge of a 2×3 table.
     *
     * <p>Layout (viewed from above, X increases right, Z increases down):
     * <pre>
     *   Solid blocks: (1,64,0), (2,64,0), (1,64,1), (2,64,1), (1,64,2), (2,64,2)
     *   Button at (1,64,-1) facing NORTH (facing.modZ == -1 → button is on -Z face of Z=0 row)
     * </pre>
     * Note: NORTH button: facing.getModX()=0, facing.getModZ()=-1.
     * Support block behind button = button + (0,0,+1) = (1,64,0). ✓
     * Center of 2×3 footprint = (minX+1.0, 65.0, minZ+1.5) = (1.0, 65.0, 1.5).
     */
    @Test
    void findTableCenterFromButtonNorth() {
        // Support blocks at (1..2, 64, 0..2).
        World world = buildTableWorld(1, 0, 2, 3, 64);

        // The NORTH-facing button sits one block in front of the (1,64,0) support block
        // (i.e. at z=-1 relative to the support). But findTableCenterFromButton uses
        // button.getX/Y/Z directly, so we set up the button block at (1, 64, -1).
        Block button = mock(Block.class);
        when(button.getWorld()).thenReturn(world);
        when(button.getX()).thenReturn(1);
        when(button.getY()).thenReturn(64);
        when(button.getZ()).thenReturn(-1);

        Location center = BlackjackListener.findTableCenterFromButton(button, BlockFace.NORTH);

        assertNotNull(center, "findTableCenterFromButton must find the 2x3 footprint");
        // Center X = minX + 2/2.0 = 1 + 1.0 = 2.0; Center Z = minZ + 3/2.0 = 0 + 1.5 = 1.5
        assertEquals(2.0,  center.x(), 1e-6, "Center X must be 2.0 for minX=1, width=2");
        assertEquals(65.0, center.y(), 1e-6, "Center Y must be supportY + 1.0 = 65.0");
        assertEquals(1.5,  center.z(), 1e-6, "Center Z must be 1.5 for minZ=0, depth=3");
    }

    /**
     * {@link BlackjackListener#findTableCenterFromButton} must return {@code null}
     * when the footprint contains a non-solid block.
     */
    @Test
    void findTableCenterFromButtonNullWhenNoSolidFootprint() {
        // Build a world that returns AIR for every block.
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);

        Block button = mock(Block.class);
        when(button.getWorld()).thenReturn(world);
        when(button.getX()).thenReturn(0);
        when(button.getY()).thenReturn(64);
        when(button.getZ()).thenReturn(-1);

        Location center = BlackjackListener.findTableCenterFromButton(button, BlockFace.NORTH);
        assertNull(center, "findTableCenterFromButton must return null when no solid footprint exists");
    }

    // ---- Opposite-side MIDDLE button detection ------------------------------

    /**
     * Standard 2×3 build, side A facing NORTH. The two 3-wide button rows sit on the
     * long edges (one facing NORTH, the other SOUTH) separated by the 2-deep short axis.
     *
     * <p>Layout (X increases right, Z increases down):
     * <pre>
     *   Support rows (short axis, 2 deep): Z=0 and Z=1; columns X=1,2,3.
     *   Side-A buttons (NORTH, facing −Z) on the −Z face of the Z=0 row → Z=-1.
     *   Side-B buttons (SOUTH, facing +Z) on the +Z face of the Z=1 row → Z=2.
     * </pre>
     * Side-A middle is at (2,64,-1). Expected side-B middle = sideAButton + facingB*(TABLE_WIDTH+1)
     * = (2,64,-1) + (0,0,+1)*3 = (2,64,2).
     */
    @Test
    void findOppositeMiddleButtonNorth() {
        World world = buildTableWorld(1, 0, 3, 2, 64); // 3 wide (X), 2 deep (Z)

        // Side A (NORTH, facing −Z): row at Z=-1, columns X=1,2,3.
        // NORTH: left = +X, right = −X. middle X=2 → left=3, right=1.
        Block sideAMid = addWallButton(world, 2, 64, -1, BlockFace.NORTH);
        addWallButton(world, 3, 64, -1, BlockFace.NORTH); // side-A left
        addWallButton(world, 1, 64, -1, BlockFace.NORTH); // side-A right

        // Side B (SOUTH, facing +Z): row at Z=2, columns X=1,2,3.
        // SOUTH: left = −X, right = +X. middle X=2 → left=1, right=3.
        addWallButton(world, 2, 64, 2, BlockFace.SOUTH); // side-B middle
        addWallButton(world, 1, 64, 2, BlockFace.SOUTH); // side-B left
        addWallButton(world, 3, 64, 2, BlockFace.SOUTH); // side-B right

        Location middleB = BlackjackListener.findOppositeMiddleButton(world, sideAMid, BlockFace.NORTH);
        assertNotNull(middleB, "Opposite middle button must be found for a NORTH-facing side A");
        assertEquals(2, middleB.getBlockX(), "Side-B middle X must match side-A column (2)");
        assertEquals(64, middleB.getBlockY(), "Side-B middle Y must equal the button Y");
        assertEquals(2, middleB.getBlockZ(), "Side-B middle Z must be sideA + facingB*(TABLE_WIDTH+1) = 2");
    }

    /**
     * Same standard 2×3 build but side A faces EAST, proving the detection is not
     * hardcoded to the NORTH/Z axis.
     *
     * <p>Layout: support rows (short axis, 2 wide) at X=0 and X=1; long axis Z=1,2,3.
     * Side-A buttons (EAST, facing +X) on the +X face of the X=1 row → X=2.
     * Side-B buttons (WEST, facing −X) on the −X face of the X=0 row → X=-1.
     * Side-A middle at (2,64,2). Expected side-B middle = (2,64,2) + (−1,0,0)*3 = (-1,64,2).
     */
    @Test
    void findOppositeMiddleButtonEast() {
        World world = buildTableWorld(0, 1, 2, 3, 64); // 2 wide (X), 3 deep (Z)

        // Side A (EAST, facing +X): column at X=2, rows Z=1,2,3.
        // EAST: left = +Z, right = −Z. middle Z=2 → left=3, right=1.
        Block sideAMid = addWallButton(world, 2, 64, 2, BlockFace.EAST);
        addWallButton(world, 2, 64, 3, BlockFace.EAST); // side-A left
        addWallButton(world, 2, 64, 1, BlockFace.EAST); // side-A right

        // Side B (WEST, facing −X): column at X=-1, rows Z=1,2,3.
        // WEST: left = −Z, right = +Z. middle Z=2 → left=1, right=3.
        addWallButton(world, -1, 64, 2, BlockFace.WEST); // side-B middle
        addWallButton(world, -1, 64, 1, BlockFace.WEST); // side-B left
        addWallButton(world, -1, 64, 3, BlockFace.WEST); // side-B right

        Location middleB = BlackjackListener.findOppositeMiddleButton(world, sideAMid, BlockFace.EAST);
        assertNotNull(middleB, "Opposite middle button must be found for an EAST-facing side A");
        assertEquals(-1, middleB.getBlockX(), "Side-B middle X must be sideA + facingB*(TABLE_WIDTH+1) = -1");
        assertEquals(64, middleB.getBlockY(), "Side-B middle Y must equal the button Y");
        assertEquals(2, middleB.getBlockZ(), "Side-B middle Z must match side-A row (2)");
    }

    /**
     * Negative case: side-A buttons present, but the opposite side has no buttons at all.
     * The helper must return {@code null}.
     */
    @Test
    void findOppositeMiddleButtonNullWhenNoOppositeRow() {
        World world = buildTableWorld(1, 0, 3, 2, 64);

        // Only side-A buttons exist; no side-B buttons are added.
        Block sideAMid = addWallButton(world, 2, 64, -1, BlockFace.NORTH);
        addWallButton(world, 3, 64, -1, BlockFace.NORTH);
        addWallButton(world, 1, 64, -1, BlockFace.NORTH);

        Location middleB = BlackjackListener.findOppositeMiddleButton(world, sideAMid, BlockFace.NORTH);
        assertNull(middleB, "Helper must return null when no opposite-side button row exists");
    }

    /**
     * 3×2-oriented build (facing axis is 3 deep) to confirm the {TABLE_WIDTH, TABLE_DEPTH}
     * fallback: the first candidate (depth 2 → step 3) fails to validate and the helper
     * falls through to depth 3 → step 4.
     *
     * <p>Layout: support rows (short axis, 3 deep) at Z=0,1,2; long axis X=1,2 (2 wide).
     * Side-A buttons (NORTH, facing −Z) on the −Z face of Z=0 → Z=-1.
     * Side-B buttons (SOUTH, facing +Z) on the +Z face of Z=2 → Z=3.
     * Side-A middle at (1,64,-1). Expected side-B middle = (1,64,-1) + (0,0,+1)*4 = (1,64,3).
     */
    @Test
    void findOppositeMiddleButtonFallbackDepthThree() {
        World world = buildTableWorld(1, 0, 2, 3, 64); // 2 wide (X), 3 deep (Z)

        // Side A (NORTH, facing −Z): row at Z=-1, columns X=1,2.
        // NORTH: left = +X, right = −X. middle X=1 → left=2, right=0.
        Block sideAMid = addWallButton(world, 1, 64, -1, BlockFace.NORTH);
        addWallButton(world, 2, 64, -1, BlockFace.NORTH); // side-A left
        addWallButton(world, 0, 64, -1, BlockFace.NORTH); // side-A right

        // Side B (SOUTH, facing +Z): row at Z=3 (step = TABLE_DEPTH+1 = 4), columns X=1,2.
        // SOUTH: left = −X, right = +X. middle X=1 → left=0, right=2.
        addWallButton(world, 1, 64, 3, BlockFace.SOUTH); // side-B middle
        addWallButton(world, 0, 64, 3, BlockFace.SOUTH); // side-B left
        addWallButton(world, 2, 64, 3, BlockFace.SOUTH); // side-B right

        Location middleB = BlackjackListener.findOppositeMiddleButton(world, sideAMid, BlockFace.NORTH);
        assertNotNull(middleB, "Opposite middle button must be found via the depth-3 fallback");
        assertEquals(1, middleB.getBlockX(), "Side-B middle X must match side-A column (1)");
        assertEquals(64, middleB.getBlockY(), "Side-B middle Y must equal the button Y");
        assertEquals(3, middleB.getBlockZ(), "Side-B middle Z must be sideA + facingB*(TABLE_DEPTH+1) = 3");
    }

    // ---- PvP table removal (Tweaks-2m2k) ------------------------------------

    /**
     * In-world removal of a PvP table must (a) drop the matching entry from the chunk
     * PDC list under {@code tweaks:blackjack_pvp_tables} and (b) remove all six
     * {@code pvpButtonMap} keys for that table.
     *
     * <p>Uses a real MockBukkit world so the chunk PDC and {@code Bukkit.getWorld}
     * resolve (the latter is required by {@code deserializePvpTable}, which the removal
     * path uses to match entries by identity). MockBukkit cannot spawn TextDisplay
     * holograms, so no hologram is seeded; {@code removeHologramNear} is a no-op when no
     * entity exists, leaving the PDC + button-map assertions clean.
     */
    @Test
    void handlePvpRemovalClearsPdcAndButtons() {
        World world = server.addSimpleWorld("pvpremoval");

        Location center  = new Location(world, 1.5, 65.0, 2.5);
        Location middleA = new Location(world, 2, 64, 0);  // facing NORTH
        Location middleB = new Location(world, 2, 64, 5);  // facing SOUTH

        BlackjackListener.PvpTableEntry entry = new BlackjackListener.PvpTableEntry(
                center, null,
                middleA, BlockFace.NORTH,
                middleB, BlockFace.SOUTH);

        EconomyManager em = plugin.getEconomyManager();
        RankManager rm    = plugin.getRankManager();
        BlackjackListener listener = new BlackjackListener(plugin, em, rm);

        // Register the six PvP buttons and seed the center chunk's PDC list.
        listener.registerPvpButtonsForTable(entry);

        NamespacedKey pvpTablesKey = new NamespacedKey(plugin, "blackjack_pvp_tables");
        Chunk chunk = center.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String serialized = BlackjackListener.serializePvpTable(entry);
        pdc.set(pvpTablesKey, PersistentDataType.LIST.strings(),
                new ArrayList<>(List.of(serialized)));

        // Sanity: the six button keys exist and the PDC list holds the entry.
        List<String> expectedKeys = List.of(
                "pvpremoval:2:64:0", "pvpremoval:3:64:0", "pvpremoval:1:64:0",
                "pvpremoval:2:64:5", "pvpremoval:1:64:5", "pvpremoval:3:64:5");
        for (String key : expectedKeys) {
            assertNotNull(listener.pvpButtonMap().get(key),
                    "Precondition: pvpButtonMap must contain key " + key);
        }
        assertTrue(pdc.getOrDefault(pvpTablesKey, PersistentDataType.LIST.strings(), List.of())
                        .contains(serialized),
                "Precondition: PDC list must contain the seeded entry");

        // Act: remove the table in-world.
        Player admin = server.addPlayer();
        listener.handlePvpRemoval(admin, entry);

        // (a) PDC list no longer contains the entry.
        List<String> after = pdc.getOrDefault(pvpTablesKey, PersistentDataType.LIST.strings(), List.of());
        assertFalse(after.contains(serialized),
                "PvP PDC list must no longer contain the removed entry");
        assertTrue(after.isEmpty(), "PvP PDC list must be empty after removing the only entry");

        // (b) All six pvpButtonMap keys are gone.
        for (String key : expectedKeys) {
            assertNull(listener.pvpButtonMap().get(key),
                    "pvpButtonMap must no longer contain key " + key + " after removal");
        }
    }

    /**
     * Identity matching must drop the correct entry even when the stored serialized
     * string is not byte-identical to {@code serializePvpTable(entry)} — i.e. when the
     * stored doubles differ in formatting but the MIDDLE button coordinates match.
     */
    @Test
    void handlePvpRemovalMatchesByIdentityNotExactString() {
        World world = server.addSimpleWorld("pvpidentity");

        Location center  = new Location(world, 1.5, 65.0, 2.5);
        Location middleA = new Location(world, 2, 64, 0);
        Location middleB = new Location(world, 2, 64, 5);

        BlackjackListener.PvpTableEntry entry = new BlackjackListener.PvpTableEntry(
                center, null,
                middleA, BlockFace.NORTH,
                middleB, BlockFace.SOUTH);

        EconomyManager em = plugin.getEconomyManager();
        RankManager rm    = plugin.getRankManager();
        BlackjackListener listener = new BlackjackListener(plugin, em, rm);
        listener.registerPvpButtonsForTable(entry);

        // Seed with a deliberately reformatted center (1.50000 vs 1.5) but identical
        // MIDDLE button coordinates — identity match must still drop it.
        NamespacedKey pvpTablesKey = new NamespacedKey(plugin, "blackjack_pvp_tables");
        Chunk chunk = center.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String drifted =
                "pvpidentity|1.50000|65.0|2.50000|2|64|0|NORTH|2|64|5|SOUTH|";
        pdc.set(pvpTablesKey, PersistentDataType.LIST.strings(),
                new ArrayList<>(List.of(drifted)));

        Player admin = server.addPlayer();
        listener.handlePvpRemoval(admin, entry);

        List<String> after = pdc.getOrDefault(pvpTablesKey, PersistentDataType.LIST.strings(), List.of());
        assertTrue(after.isEmpty(),
                "Identity match must drop the drifted-format entry; remaining: " + after);
    }
}
