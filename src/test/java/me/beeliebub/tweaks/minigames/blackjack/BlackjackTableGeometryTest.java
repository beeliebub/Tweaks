package me.beeliebub.tweaks.minigames.blackjack;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link BlackjackListener#findTableCenterFromButton}.
 *
 * <p>The table footprint is a solid 2x3 (or 3x2) block area at the button's OWN Y
 * ({@code button.getY()}). Any solid block type is accepted; carpet is not required.
 * The three control buttons are wall-mounted on one of the 3-long sides and share
 * the same Y as the support blocks beneath them.
 *
 * <p>These tests are placed in the production package so that the package-visible
 * static methods are directly accessible without altering production visibility.
 * The test source root is {@code src/test/java}; no production bytecode is altered.
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
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("casino");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -----------------------------------------------------------------------
    // Helpers
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
        // Solid blocks: 3 wide (X=10..12), 2 deep (Z=20..21), at Y=64 (button's own Y)
        int supportY = 64;
        placeBlockRect(10, supportY, 20, 3, 2, Material.STONE);

        // Button air-block at (11, 64, 22), facing SOUTH
        var buttonBlock = world.getBlockAt(11, 64, 22);

        Location result = BlackjackListener.findTableCenterFromButton(buttonBlock, BlockFace.SOUTH);

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
        // Solid blocks: 2 wide (X=5..6), 3 deep (Z=30..32), at Y=64 (button's own Y)
        int supportY = 64;
        placeBlockRect(5, supportY, 30, 2, 3, Material.STONE);

        // Button air-block at (5, 64, 31), facing WEST
        var buttonBlock = world.getBlockAt(5, 64, 31);

        Location result = BlackjackListener.findTableCenterFromButton(buttonBlock, BlockFace.WEST);

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
        // Place solid blocks at Y=65 (one above the button — the old carpet layer).
        // The button's own Y=64 remains air, so the validator must return null.
        int wrongY = 65; // button.getY() + 1, the old carpet layer
        placeBlockRect(10, wrongY, 20, 3, 2, Material.STONE);

        // Button at (11, 64, 22), facing SOUTH — no solid blocks at Y=64
        var buttonBlock = world.getBlockAt(11, 64, 22);

        Location result = BlackjackListener.findTableCenterFromButton(buttonBlock, BlockFace.SOUTH);

        assertNull(result,
                "findTableCenterFromButton must return null when solid blocks exist only at "
                        + "buttonY + 1 (Y=65, the old carpet layer) but not at buttonY (Y=64). "
                        + "The validator must inspect button.getY(), not button.getY() + 1.");
    }

    // -----------------------------------------------------------------------
    // Negative: no solid blocks anywhere → must return null
    // -----------------------------------------------------------------------

    /**
     * Baseline negative case. With a fully empty world and no solid blocks,
     * the method must return {@code null}.
     */
    @Test
    void noBlocksAnywhere_returnsNull() {
        var buttonBlock = world.getBlockAt(50, 64, 50);

        Location result = BlackjackListener.findTableCenterFromButton(buttonBlock, BlockFace.NORTH);

        assertNull(result, "findTableCenterFromButton must return null when no solid blocks are present");
    }

    // -----------------------------------------------------------------------
    // Edge: partial block area (missing one cell) → must return null
    // -----------------------------------------------------------------------

    /**
     * A 3x2 rectangle with one corner block set to AIR must not produce a centre.
     * The method requires ALL cells in the rectangle to be solid.
     */
    @Test
    void partialBlockArea_missingOneCell_returnsNull() {
        int supportY = 64;
        // Fill a 3x2 rectangle at Y=64, X=10..12, Z=20..21
        placeBlockRect(10, supportY, 20, 3, 2, Material.STONE);
        // Remove one corner — breaks every possible 2x3 and 3x2 alignment that includes it
        world.getBlockAt(10, supportY, 20).setType(Material.AIR);

        var buttonBlock = world.getBlockAt(11, 64, 22);

        Location result = BlackjackListener.findTableCenterFromButton(buttonBlock, BlockFace.SOUTH);

        assertNull(result,
                "findTableCenterFromButton must return null when the solid block rectangle has a gap");
    }
}
