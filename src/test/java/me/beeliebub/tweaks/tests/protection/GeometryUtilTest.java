package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.protection.GeometryUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeometryUtilTest {

    @Test
    void chunkKeyRoundTripsPositiveAndNegative() {
        for (int x : new int[]{0, 1, -1, 100, -100, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            for (int z : new int[]{0, 1, -1, 100, -100, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                long k = GeometryUtil.chunkKey(x, z);
                assertEquals(x, GeometryUtil.chunkX(k), "x lost for (" + x + "," + z + ")");
                assertEquals(z, GeometryUtil.chunkZ(k), "z lost for (" + x + "," + z + ")");
            }
        }
    }

    @Test
    void chunkKeyMatchesBukkitLayout() {
        // Bukkit: (x & 0xFFFFFFFFL) | ((z & 0xFFFFFFFFL) << 32)
        long expected = (5L & 0xFFFFFFFFL) | ((-3L & 0xFFFFFFFFL) << 32);
        assertEquals(expected, GeometryUtil.chunkKey(5, -3));
    }

    @Test
    void blockToChunkFloorDivides() {
        assertEquals(0, GeometryUtil.blockToChunk(0));
        assertEquals(0, GeometryUtil.blockToChunk(15));
        assertEquals(1, GeometryUtil.blockToChunk(16));
        assertEquals(-1, GeometryUtil.blockToChunk(-1));
        assertEquals(-1, GeometryUtil.blockToChunk(-16));
        assertEquals(-2, GeometryUtil.blockToChunk(-17));
    }

    @Test
    void singleBlockAabbReturnsOneChunk() {
        long[] keys = GeometryUtil.chunkKeysInBox(100, 200, 100, 200);
        assertEquals(1, keys.length);
        assertEquals(GeometryUtil.chunkKey(6, 12), keys[0]);
    }

    @Test
    void aabbAcrossChunkBoundaryReturnsBothChunks() {
        long[] keys = GeometryUtil.chunkKeysInBox(15, 0, 16, 0);
        assertEquals(2, keys.length);
        assertEquals(GeometryUtil.chunkKey(0, 0), keys[0]);
        assertEquals(GeometryUtil.chunkKey(1, 0), keys[1]);
    }

    @Test
    void aabbCoveringFiveByFiveChunksReturns25Keys() {
        long[] keys = GeometryUtil.chunkKeysInBox(0, 0, 79, 79);
        assertEquals(25, keys.length);
    }

    @Test
    void aabbNormalizesSwappedEndpoints() {
        long[] forward = GeometryUtil.chunkKeysInBox(0, 0, 31, 31);
        long[] swapped = GeometryUtil.chunkKeysInBox(31, 31, 0, 0);
        assertArrayEquals(forward, swapped);
    }

    @Test
    void aabbAcrossOriginHandlesNegativeBlocks() {
        long[] keys = GeometryUtil.chunkKeysInBox(-1, -1, 16, 16);
        assertEquals(9, keys.length); // chunks (-1..1) x (-1..1)
    }

    @Test
    void rowMajorOrderingByChunkX() {
        long[] keys = GeometryUtil.chunkKeysInBox(0, 0, 31, 15);
        // 2 chunkX columns (0, 1) x 1 chunkZ row (0), expect 2 entries in cx-order
        assertEquals(2, keys.length);
        assertEquals(GeometryUtil.chunkKey(0, 0), keys[0]);
        assertEquals(GeometryUtil.chunkKey(1, 0), keys[1]);
    }

    @Test
    void isChunkCornerBlockAcceptsAllFourCorners() {
        // Chunk (0,0): blocks (0,0), (0,15), (15,0), (15,15)
        assertTrue(GeometryUtil.isChunkCornerBlock(0, 0));
        assertTrue(GeometryUtil.isChunkCornerBlock(0, 15));
        assertTrue(GeometryUtil.isChunkCornerBlock(15, 0));
        assertTrue(GeometryUtil.isChunkCornerBlock(15, 15));
    }

    @Test
    void isChunkCornerBlockRejectsInteriorBlocks() {
        assertFalse(GeometryUtil.isChunkCornerBlock(1, 1));
        assertFalse(GeometryUtil.isChunkCornerBlock(7, 7));
        assertFalse(GeometryUtil.isChunkCornerBlock(14, 14));
    }

    @Test
    void isChunkCornerBlockRejectsEdgeButNotCornerBlocks() {
        // (0, 5) is on the west edge but not at the NW/SW corner
        assertFalse(GeometryUtil.isChunkCornerBlock(0, 5));
        assertFalse(GeometryUtil.isChunkCornerBlock(5, 0));
        assertFalse(GeometryUtil.isChunkCornerBlock(15, 8));
    }

    @Test
    void isChunkCornerBlockHandlesNegativeCoordinates() {
        // Block (-1, -1) is xMod=15, zMod=15 -> SE corner of chunk (-1,-1). Corner.
        assertTrue(GeometryUtil.isChunkCornerBlock(-1, -1));
        // Block (-16, -16) is xMod=0, zMod=0 -> NW corner of chunk (-1,-1). Corner.
        assertTrue(GeometryUtil.isChunkCornerBlock(-16, -16));
        // Block (-8, -8) is interior.
        assertFalse(GeometryUtil.isChunkCornerBlock(-8, -8));
    }
}
