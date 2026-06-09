package me.beeliebub.tweaks.minigames.blackjack;

import org.bukkit.block.BlockFace;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for {@link BlackjackListener#flatCardRotationForFacing(BlockFace)}.
 *
 * <p>The card model's local axes before rotation are: face normal along +Z and the
 * card's top edge along +Y. For every cardinal facing the rotation must:
 * <ol>
 *   <li>Lay the card flat, face-up on the table — the face normal must rotate to +Y.</li>
 *   <li>Orient the top edge toward the seat so the card text reads upright from that seat:</li>
 *   <ul>
 *     <li>SOUTH (yaw 0) — top edge → (0, 0, −1), upright for the +Z seat</li>
 *     <li>NORTH (yaw π) — top edge → (0, 0, +1), upright for the −Z seat</li>
 *     <li>EAST  (yaw +π/2) — top edge → (−1, 0, 0), upright for the +X seat</li>
 *     <li>WEST  (yaw −π/2) — top edge → (+1, 0, 0), upright for the −X seat</li>
 *   </ul>
 * </ol>
 *
 * <p>The card's top edge always points to the far side of the table from the seated player
 * (the −facing direction), which is what makes the text read right-way-up from that seat.
 * EAST/WEST were previously swapped, rendering E/W-table cards upside-down (Tweaks-940b).
 *
 * <p>Spin relationships:
 * NORTH = SOUTH rotated 180° about world-Y (they are the opposite-seat pair).
 * EAST  = SOUTH rotated +90° about world-Y; WEST = SOUTH rotated −90°.
 *
 * <p>Placed in the production package so the package-visible static method is reachable
 * without widening production visibility. The test source root is {@code src/test/java};
 * no production bytecode is altered.
 */
class BlackjackCardRotationTest {

    private static final float EPS = 1e-6f;

    /** Local face normal of the card model before any rotation. */
    private static Vector3f faceNormal() {
        return new Vector3f(0f, 0f, 1f);
    }

    /** Local top-edge direction of the card model before any rotation. */
    private static Vector3f topEdge() {
        return new Vector3f(0f, 1f, 0f);
    }

    private static void assertVecEquals(Vector3f expected, Vector3f actual, String message) {
        assertEquals(expected.x, actual.x, EPS, message + " (x)");
        assertEquals(expected.y, actual.y, EPS, message + " (y)");
        assertEquals(expected.z, actual.z, EPS, message + " (z)");
    }

    // =========================================================================
    // Face-normal tests — all four facings must lie flat face-up (+Y normal)
    // =========================================================================

    @ParameterizedTest
    @EnumSource(value = BlockFace.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void allFacingsLieFlat_normalPointsUp(BlockFace facing) {
        Quaternionf rotation = BlackjackListener.flatCardRotationForFacing(facing);
        Vector3f normal = rotation.transform(faceNormal());
        assertVecEquals(new Vector3f(0f, 1f, 0f), normal,
                facing + " card face normal must point straight up (+Y) so the card lies flat, face up");
    }

    // =========================================================================
    // Top-edge tests — each facing produces a deterministic heading
    // =========================================================================

    @Test
    void southCardTopPointsTowardNegativeZ() {
        // SOUTH: yaw=0, so no vertical spin — top edge tips from +Y to −Z.
        Quaternionf rotation = BlackjackListener.flatCardRotationForFacing(BlockFace.SOUTH);
        Vector3f top = rotation.transform(topEdge());
        assertVecEquals(new Vector3f(0f, 0f, -1f), top,
                "SOUTH top edge must point toward −Z (reads upright from the +Z seat)");
    }

    @Test
    void northCardTopPointsTowardPositiveZ() {
        // NORTH: yaw=π flips Z, so top edge points to +Z.
        Quaternionf rotation = BlackjackListener.flatCardRotationForFacing(BlockFace.NORTH);
        Vector3f top = rotation.transform(topEdge());
        assertVecEquals(new Vector3f(0f, 0f, 1f), top,
                "NORTH top edge must point toward +Z (reads upright from the −Z seat)");
    }

    @Test
    void westCardTopPointsTowardPositiveX() {
        // WEST: yaw=−π/2 rotates so top edge points to +X (away from the −X seat).
        Quaternionf rotation = BlackjackListener.flatCardRotationForFacing(BlockFace.WEST);
        Vector3f top = rotation.transform(topEdge());
        assertVecEquals(new Vector3f(1f, 0f, 0f), top,
                "WEST top edge must point toward +X (reads upright from the −X seat)");
    }

    @Test
    void eastCardTopPointsTowardNegativeX() {
        // EAST: yaw=+π/2 rotates so top edge points to −X (away from the +X seat).
        Quaternionf rotation = BlackjackListener.flatCardRotationForFacing(BlockFace.EAST);
        Vector3f top = rotation.transform(topEdge());
        assertVecEquals(new Vector3f(-1f, 0f, 0f), top,
                "EAST top edge must point toward −X (reads upright from the +X seat)");
    }

    // =========================================================================
    // Spin relationships between opposite / adjacent facings
    // =========================================================================

    @Test
    void northAndSouthAreSpun180AboutVertical() {
        // NORTH and SOUTH are the two opposite seats of the same table axis.
        // Their rotations must differ by exactly a 180° world-Y spin, which flips X
        // and Z while leaving Y unchanged. This ensures each hand reads upright from
        // its own seat.
        Quaternionf south = BlackjackListener.flatCardRotationForFacing(BlockFace.SOUTH);
        Quaternionf north = BlackjackListener.flatCardRotationForFacing(BlockFace.NORTH);
        Quaternionf worldYHalfTurn = new Quaternionf().rotateY((float) Math.PI);

        for (Vector3f basis : new Vector3f[]{
                new Vector3f(1f, 0f, 0f),
                new Vector3f(0f, 1f, 0f),
                new Vector3f(0f, 0f, 1f)}) {
            Vector3f viaNorth = north.transform(new Vector3f(basis));
            Vector3f viaSouthThenSpin = worldYHalfTurn.transform(south.transform(new Vector3f(basis)));
            assertVecEquals(viaSouthThenSpin, viaNorth,
                    "NORTH rotation must equal SOUTH rotation followed by a 180° world-Y spin");
        }
    }

    @Test
    void eastAndWestAreSpun180AboutVertical() {
        // EAST and WEST are also an opposite-seat pair on the perpendicular table axis.
        Quaternionf east = BlackjackListener.flatCardRotationForFacing(BlockFace.EAST);
        Quaternionf west = BlackjackListener.flatCardRotationForFacing(BlockFace.WEST);
        Quaternionf worldYHalfTurn = new Quaternionf().rotateY((float) Math.PI);

        for (Vector3f basis : new Vector3f[]{
                new Vector3f(1f, 0f, 0f),
                new Vector3f(0f, 1f, 0f),
                new Vector3f(0f, 0f, 1f)}) {
            Vector3f viaEast = east.transform(new Vector3f(basis));
            Vector3f viaWestThenSpin = worldYHalfTurn.transform(west.transform(new Vector3f(basis)));
            assertVecEquals(viaWestThenSpin, viaEast,
                    "EAST rotation must equal WEST rotation followed by a 180° world-Y spin");
        }
    }

    @Test
    void eastIsSouthRotatedPlus90AboutVertical() {
        // EAST = SOUTH rotated +90° about world-Y (CCW when viewed from above).
        Quaternionf south = BlackjackListener.flatCardRotationForFacing(BlockFace.SOUTH);
        Quaternionf east  = BlackjackListener.flatCardRotationForFacing(BlockFace.EAST);
        Quaternionf worldYPlus90 = new Quaternionf().rotateY((float) (Math.PI / 2));

        for (Vector3f basis : new Vector3f[]{
                new Vector3f(1f, 0f, 0f),
                new Vector3f(0f, 1f, 0f),
                new Vector3f(0f, 0f, 1f)}) {
            Vector3f viaEast = east.transform(new Vector3f(basis));
            Vector3f viaSouthThenPlus90 = worldYPlus90.transform(south.transform(new Vector3f(basis)));
            assertVecEquals(viaSouthThenPlus90, viaEast,
                    "EAST rotation must equal SOUTH rotation followed by a +90° world-Y spin");
        }
    }

    @Test
    void westIsSouthRotatedMinus90AboutVertical() {
        // WEST = SOUTH rotated −90° about world-Y (CW when viewed from above).
        Quaternionf south = BlackjackListener.flatCardRotationForFacing(BlockFace.SOUTH);
        Quaternionf west  = BlackjackListener.flatCardRotationForFacing(BlockFace.WEST);
        Quaternionf worldYMinus90 = new Quaternionf().rotateY((float) (-Math.PI / 2));

        for (Vector3f basis : new Vector3f[]{
                new Vector3f(1f, 0f, 0f),
                new Vector3f(0f, 1f, 0f),
                new Vector3f(0f, 0f, 1f)}) {
            Vector3f viaWest = west.transform(new Vector3f(basis));
            Vector3f viaSouthThenMinus90 = worldYMinus90.transform(south.transform(new Vector3f(basis)));
            assertVecEquals(viaSouthThenMinus90, viaWest,
                    "WEST rotation must equal SOUTH rotation followed by a −90° world-Y spin");
        }
    }
}
