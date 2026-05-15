package me.beeliebub.tweaks.protection;

// Pure math helpers for going between block AABBs, chunk (x, z) pairs, and
// the 64-bit chunk keys used as map keys throughout the protection system.
//
// chunkKey layout matches Bukkit's Chunk.getChunkKey: low 32 bits = chunkX,
// high 32 bits = chunkZ, both as signed two's complement. Round-trip via
// chunkX/chunkZ accessors. Working in packed longs avoids allocating
// Location/Coordinate objects when iterating the thousands of chunks that a
// large administrative claim may touch.
public final class GeometryUtil {

    private static final int CHUNK_SHIFT = 4; // 16 blocks per chunk axis (1 << 4)

    private GeometryUtil() {}

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    public static int chunkX(long key) {
        return (int) (key & 0xFFFFFFFFL);
    }

    public static int chunkZ(long key) {
        return (int) (key >>> 32);
    }

    public static int blockToChunk(int block) {
        return block >> CHUNK_SHIFT; // arithmetic shift handles negative blocks correctly
    }

    // True iff (blockX, blockZ) is one of the four corner blocks of its
    // chunk — the 2x2 set of blocks at xMod ∈ {0, 15} AND zMod ∈ {0, 15}.
    // Used by the wand listener to restrict selection clicks to chunk
    // corners, which gives the player a tactile "snap to grid" cue.
    public static boolean isChunkCornerBlock(int blockX, int blockZ) {
        int xMod = Math.floorMod(blockX, 16);
        int zMod = Math.floorMod(blockZ, 16);
        return (xMod == 0 || xMod == 15) && (zMod == 0 || zMod == 15);
    }

    // Every chunk key intersected by the inclusive block-AABB (x1/z1, x2/z2).
    // Order is row-major in (chunkX, chunkZ). Endpoints may be supplied in
    // any order — the box is normalized internally.
    public static long[] chunkKeysInBox(int x1, int z1, int x2, int z2) {
        int minCx = blockToChunk(Math.min(x1, x2));
        int maxCx = blockToChunk(Math.max(x1, x2));
        int minCz = blockToChunk(Math.min(z1, z2));
        int maxCz = blockToChunk(Math.max(z1, z2));

        int count = (maxCx - minCx + 1) * (maxCz - minCz + 1);
        long[] keys = new long[count];
        int i = 0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                keys[i++] = chunkKey(cx, cz);
            }
        }
        return keys;
    }
}
