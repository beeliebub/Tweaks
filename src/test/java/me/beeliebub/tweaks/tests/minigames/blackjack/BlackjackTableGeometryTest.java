package me.beeliebub.tweaks.tests.minigames.blackjack;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackTableStore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for {@link BlackjackTableStore#findTableCenterFromButton} and
 * {@link BlackjackTableStore#isValidTableRect}, both public static helpers.
 *
 * <p>The table footprint is a solid 2x3 (or 3x2) block area at the button's OWN Y
 * ({@code button.getY()}). Any solid block type is accepted; carpet is not required.
 * The three control buttons are wall-mounted on one of the 3-long sides and share
 * the same Y as the support blocks beneath them.
 *
 * <p>This file merges two previously separate test roots that duplicated the same
 * simple name: one used a real MockBukkit {@code World} with physically placed blocks
 * (covering SOUTH/WEST facings and the carpet-layer regression), the other used pure
 * Mockito-mocked {@code World}/{@code Block} objects (covering NORTH facing and
 * {@code isValidTableRect} directly). Both styles are kept since they exercise the
 * helpers through genuinely different test doubles.
 *
 * <h2>Physical layout (SOUTH-facing button example)</h2>
 * <pre>
 *   Y=65  [table surface / top face of support blocks — cards rest here]
 *   Y=64  [solid][solid][solid]  X=10,11,12  Z=20  ← support blocks (button's Y)
 *         [solid][solid][solid]  X=10,11,12  Z=21
 *         [button]               at (11,64,22)  ← wall-mounted, faces SOUTH (away from table)
 * </pre>
 * Button at (11,64,22) faces SOUTH. {@code startX = 11 - 0 = 11}, {@code startZ = 22 - 1 = 21}.
 * Brute-force anchor offsets find the 3x2 solid block rectangle at Y=64.
 * Expected centre: {@code cx=11.5, cy=65.0} (top face = supportY + 1.0), {@code cz=21.0}.
 */
class BlackjackTableGeometryTest {

    private ServerMock server;
    private Tweaks plugin;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        world = server.addSimpleWorld("casino");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -----------------------------------------------------------------------
    // Real-world helper (physically placed blocks in a MockBukkit ServerMock)
    // -----------------------------------------------------------------------

    /**
     * Places a filled solid-block rectangle in the world and verifies that the
     * chosen material is actually solid under MockBukkit. Fails fast with an
     * explicit message if {@code block.isSolid()} is false, so the caller knows
     * to pick a different material rather than getting a spurious geometry failure.
     */
    private void placeBlockRect(int minX, int y, int minZ, int w, int d, Material block) {
        assertTrue(block.isSolid(),
                "MockBukkit must recognise " + block + " as a solid material. "
                        + "If this fails, pick a Material whose isSolid() returns true.");
        for (int x = minX; x < minX + w; x++) {
            for (int z = minZ; z < minZ + d; z++) {
                world.getBlockAt(x, y, z).setType(block);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Mocked-world helper (pure Mockito, no MockBukkit ServerMock world)
    // -----------------------------------------------------------------------

    /**
     * Build a mocked world where every block at Y=64 in the rectangle
     * [minX..minX+w) x [minZ..minZ+d) is a solid STONE block, and every other
     * block is AIR.
     */
    private static World buildTableWorld(int minX, int minZ, int w, int d, int y) {
        World mockWorld = mock(World.class);
        when(mockWorld.getName()).thenReturn("world");
        when(mockWorld.getUID()).thenReturn(java.util.UUID.randomUUID());

        Block airBlock = mock(Block.class);
        when(airBlock.getType()).thenReturn(Material.AIR);
        when(mockWorld.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(airBlock);

        for (int x = minX; x < minX + w; x++) {
            for (int z = minZ; z < minZ + d; z++) {
                Block solid = mock(Block.class);
                when(solid.getType()).thenReturn(Material.STONE);
                when(solid.getWorld()).thenReturn(mockWorld);
                when(solid.getX()).thenReturn(x);
                when(solid.getY()).thenReturn(y);
                when(solid.getZ()).thenReturn(z);
                Location loc = new Location(mockWorld, x, y, z);
                when(solid.getLocation()).thenReturn(loc);
                when(mockWorld.getBlockAt(x, y, z)).thenReturn(solid);
            }
        }
        return mockWorld;
    }

    // -----------------------------------------------------------------------
    // Happy-path: SOUTH-facing button with correct solid blocks at button's Y
    // -----------------------------------------------------------------------

    /**
     * Core regression guard. The solid support blocks sit at {@code button.getY()}
     * (Y=64) and the table surface (top face) is therefore at Y+1.0 = 65.0.
     *
     * <p>Layout:
     * <ul>
     *   <li>Support blocks Y=64, X in [10,12], Z in {20,21} — a 3-wide x 2-deep rectangle.</li>
     *   <li>Button air-block at (11, 64, 22), {@code BlockFace.SOUTH}.</li>
     *   <li>Expected centre: cx=11.5, cy=65.0 (top face of the support blocks = supportY + 1.0),
     *       cz=21.0.</li>
     * </ul>
     */
    @Test
    void southFacing_correctSolidBlocksAtButtonY_returnsExpectedCenter() {
        int supportY = 64;
        placeBlockRect(10, supportY, 20, 3, 2, Material.STONE);

        var buttonBlock = world.getBlockAt(11, 64, 22);

        Location result = BlackjackTableStore.findTableCenterFromButton(buttonBlock, BlockFace.SOUTH);

        assertNotNull(result,
                "findTableCenterFromButton must return a non-null Location when solid blocks are "
                        + "present at button.getY() in the opposite-facing direction. "
                        + "A null result indicates the validator is looking at the wrong Y.");
        assertEquals(65.0, result.y(), 1e-9,
                "cy must be the top face of the support blocks = supportY + 1.0 = 65.0. "
                        + "No carpet-thickness offset (0.0625) should be added.");
        assertEquals(11.5, result.x(), 1e-9,
                "cx must be the X midpoint of the 3-wide rectangle [10,12] = 11.5");
        assertEquals(21.0, result.z(), 1e-9,
                "cz must be the Z midpoint of the 2-deep rectangle [20,21] = 21.0");
    }

    // -----------------------------------------------------------------------
    // Second facing: WEST — proves opposite-facing anchor is axis-correct
    // -----------------------------------------------------------------------

    /**
     * WEST-facing button: the facing direction is along the negative-X axis
     * ({@code modX=-1, modZ=0}), so the support block anchor is in the +X direction
     * from the button (opposite of facing).
     *
     * <p>Layout:
     * <ul>
     *   <li>Support blocks Y=64, X in {5,6}, Z in [30,32] — a 2-wide x 3-deep rectangle.</li>
     *   <li>Button air-block at (5, 64, 31), {@code BlockFace.WEST}.</li>
     *   <li>{@code startX = 5 - (-1) = 6}, {@code startZ = 31 - 0 = 31}.</li>
     *   <li>Expected centre: cx=6.0, cy=65.0 (top face of the support blocks = supportY + 1.0),
     *       cz=31.5.</li>
     * </ul>
     */
    @Test
    void westFacing_correctSolidBlocksAtButtonY_returnsExpectedCenter() {
        int supportY = 64;
        placeBlockRect(5, supportY, 30, 2, 3, Material.STONE);

        var buttonBlock = world.getBlockAt(5, 64, 31);

        Location result = BlackjackTableStore.findTableCenterFromButton(buttonBlock, BlockFace.WEST);

        assertNotNull(result,
                "findTableCenterFromButton must return a non-null Location for a WEST-facing "
                        + "button with solid blocks at button.getY() in the +X direction.");
        assertEquals(65.0, result.y(), 1e-9,
                "cy must be the top face of the support blocks = supportY + 1.0 = 65.0");
        assertEquals(6.0, result.x(), 1e-9,
                "cx must be the X midpoint of the 2-wide rectangle [5,6] = 6.0");
        assertEquals(31.5, result.z(), 1e-9,
                "cz must be the Z midpoint of the 3-deep rectangle [30,32] = 31.5");
    }

    // -----------------------------------------------------------------------
    // Third facing: NORTH, via a fully mocked World (no ServerMock block placement)
    // -----------------------------------------------------------------------

    /**
     * A NORTH-facing button sits in front of a 2×3 footprint. The center must be
     * at the correct geometric midpoint.
     *
     * <p>Layout: solid blocks at X=1..2, Z=0..2, Y=64.
     * Button at (1,64,-1) facing NORTH.
     */
    @Test
    void findTableCenterFromButtonNorth() {
        World mockWorld = buildTableWorld(1, 0, 2, 3, 64);

        Block button = mock(Block.class);
        when(button.getWorld()).thenReturn(mockWorld);
        when(button.getX()).thenReturn(1);
        when(button.getY()).thenReturn(64);
        when(button.getZ()).thenReturn(-1);

        Location center = BlackjackTableStore.findTableCenterFromButton(button, BlockFace.NORTH);

        assertNotNull(center, "findTableCenterFromButton must find the 2x3 footprint");
        assertEquals(2.0,  center.x(), 1e-6, "Center X must be 2.0 for minX=1, width=2");
        assertEquals(65.0, center.y(), 1e-6, "Center Y must be supportY + 1.0 = 65.0");
        assertEquals(1.5,  center.z(), 1e-6, "Center Z must be 1.5 for minZ=0, depth=3");
    }

    // -----------------------------------------------------------------------
    // Negative: solid blocks only at the OLD carpet layer (Y+1) → must return null
    // -----------------------------------------------------------------------

    /**
     * Strongest regression guard against the old carpet-based model. Solid blocks are
     * placed only one block ABOVE the button ({@code buttonY + 1 = 65} — the layer
     * where carpet used to sit) with no blocks at the button's own Y (64).
     *
     * <p>The validator must look at {@code button.getY()} (Y=64), not one above it.
     * Since Y=64 is all air, the result must be {@code null}. A non-null result here
     * would indicate the validator is still checking the wrong Y.
     */
    @Test
    void solidBlocksOnlyOneLayerTooHigh_returnsNull() {
        int wrongY = 65; // button.getY() + 1, the old carpet layer
        placeBlockRect(10, wrongY, 20, 3, 2, Material.STONE);

        var buttonBlock = world.getBlockAt(11, 64, 22);

        Location result = BlackjackTableStore.findTableCenterFromButton(buttonBlock, BlockFace.SOUTH);

        assertNull(result,
                "findTableCenterFromButton must return null when solid blocks exist only at "
                        + "buttonY + 1 (Y=65, the old carpet layer) but not at buttonY (Y=64). "
                        + "The validator must inspect button.getY(), not button.getY() + 1.");
    }

    // -----------------------------------------------------------------------
    // Negative: no solid blocks anywhere → must return null
    // -----------------------------------------------------------------------

    @Test
    void noBlocksAnywhere_returnsNull() {
        var buttonBlock = world.getBlockAt(50, 64, 50);

        Location result = BlackjackTableStore.findTableCenterFromButton(buttonBlock, BlockFace.NORTH);

        assertNull(result, "findTableCenterFromButton must return null when no solid blocks are present");
    }

    @Test
    void findTableCenterFromButtonNullWhenNoSolidFootprint() {
        World mockWorld = mock(World.class);
        when(mockWorld.getName()).thenReturn("world");
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(mockWorld.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);

        Block button = mock(Block.class);
        when(button.getWorld()).thenReturn(mockWorld);
        when(button.getX()).thenReturn(0);
        when(button.getY()).thenReturn(64);
        when(button.getZ()).thenReturn(-1);

        Location center = BlackjackTableStore.findTableCenterFromButton(button, BlockFace.NORTH);
        assertNull(center, "findTableCenterFromButton must return null when no solid footprint exists");
    }

    // -----------------------------------------------------------------------
    // Edge: partial block area (missing one cell) → must return null
    // -----------------------------------------------------------------------

    @Test
    void partialBlockArea_missingOneCell_returnsNull() {
        int supportY = 64;
        placeBlockRect(10, supportY, 20, 3, 2, Material.STONE);
        world.getBlockAt(10, supportY, 20).setType(Material.AIR);

        var buttonBlock = world.getBlockAt(11, 64, 22);

        Location result = BlackjackTableStore.findTableCenterFromButton(buttonBlock, BlockFace.SOUTH);

        assertNull(result,
                "findTableCenterFromButton must return null when the solid block rectangle has a gap");
    }

    // -----------------------------------------------------------------------
    // isValidTableRect — direct coverage via mocked World
    // -----------------------------------------------------------------------

    @Test
    void isValidTableRectTrueForAllSolid() {
        World mockWorld = buildTableWorld(10, 20, 2, 3, 64);
        assertTrue(BlackjackTableStore.isValidTableRect(mockWorld, 64, 10, 20, 2, 3),
                "2x3 solid rectangle must be valid");
    }

    @Test
    void isValidTableRectFalseIfOneBlockIsAir() {
        World mockWorld = buildTableWorld(10, 20, 2, 3, 64);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(mockWorld.getBlockAt(11, 64, 21)).thenReturn(air);

        assertFalse(BlackjackTableStore.isValidTableRect(mockWorld, 64, 10, 20, 2, 3),
                "Rectangle with one air block must be invalid");
    }
}
