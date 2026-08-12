package me.beeliebub.tweaks.skyblock.island;

import me.beeliebub.tweaks.protection.region.Region;

import java.util.OptionalInt;

/**
 * Maps island slots to chunk coordinates without touching Bukkit. Slot zero is the origin and
 * subsequent slots follow an outward, counter-clockwise square spiral.
 */
public final class IslandGrid {

    public static final int DEFAULT_PITCH_CHUNKS = 35;
    public static final int DEFAULT_MAX_SLOT_INDEX = 100_000;
    public static final int SPAWN_EXCLUSION_RADIUS_BLOCKS = 1_000;

    private static final long BLOCKS_PER_CHUNK = 16L;
    private static final long WORLD_LIMIT = 29_999_984L;

    private final int pitchChunks;
    private final int maxSlotIndex;

    public IslandGrid() {
        this(DEFAULT_PITCH_CHUNKS, DEFAULT_MAX_SLOT_INDEX);
    }

    public IslandGrid(int maxSlotIndex) {
        this(DEFAULT_PITCH_CHUNKS, maxSlotIndex);
    }

    public IslandGrid(int pitchChunks, int maxSlotIndex) {
        if (pitchChunks <= 0) {
            throw new IllegalArgumentException("Grid pitch must be positive");
        }
        if (maxSlotIndex < 0) {
            throw new IllegalArgumentException("Maximum slot index cannot be negative");
        }
        this.pitchChunks = pitchChunks;
        this.maxSlotIndex = maxSlotIndex;
    }

    public int pitchChunks() {
        return pitchChunks;
    }

    public int maxSlotIndex() {
        return maxSlotIndex;
    }

    public int maxIndex() {
        return maxSlotIndex;
    }

    /**
     * Returns whether a slot's centre lies inside the fixed Skyblock spawn exclusion square.
     *
     * <p>Skyblock spawn is anchored to chunk 0,0, so the surrounding slots remain reserved for
     * spawn rather than player terrain. The radius is measured to the slot centre in block
     * coordinates, not to its footprint: the first eligible default-pitch slot therefore leaves
     * a fully grown {@link IslandSize#LARGE} island roughly 976 blocks from the origin. A slot is
     * excluded only when its centre is strictly inside the Chebyshev-radius square; the boundary
     * itself remains allocatable.</p>
     */
    public boolean isSpawnExcluded(int slotIndex) {
        Slot slot = slotForIndex(slotIndex);
        long centerBlockX = (long) slot.centerChunkX() * BLOCKS_PER_CHUNK + 8L;
        long centerBlockZ = (long) slot.centerChunkZ() * BLOCKS_PER_CHUNK + 8L;
        long distance = Math.max(Math.abs(centerBlockX), Math.abs(centerBlockZ));
        return distance < SPAWN_EXCLUSION_RADIUS_BLOCKS;
    }

    /** Returns the first configured slot whose centre is outside the spawn exclusion square. */
    public int firstAllocatableIndex() {
        for (int index = 0; index <= maxSlotIndex; index++) {
            if (!isSpawnExcluded(index)) return index;
        }
        throw new IllegalStateException("No Skyblock island slots remain outside the spawn exclusion square");
    }

    /** Returns the centre chunk for a valid slot index. */
    public Slot slotForIndex(int index) {
        validateIndex(index);
        long[] coordinates = coordinatesForIndex(index);
        long centerChunkX = Math.multiplyExact(coordinates[0], (long) pitchChunks);
        long centerChunkZ = Math.multiplyExact(coordinates[1], (long) pitchChunks);
        validateChunkCoordinate(centerChunkX);
        validateChunkCoordinate(centerChunkZ);
        return new Slot(index, Math.toIntExact(centerChunkX), Math.toIntExact(centerChunkZ));
    }

    /** Returns the inclusive island bounds centred on a slot. */
    public Region.RegionBounds boundsFor(int slotIndex, IslandSize size) {
        ChunkBounds bounds = chunkBoundsFor(slotIndex, size);
        return new Region.RegionBounds(bounds.minChunkX(), bounds.minChunkZ(),
                bounds.maxChunkX(), bounds.maxChunkZ());
    }

    /** Pure arithmetic form used by Bukkit-free callers and chunk sweepers. */
    public ChunkBounds chunkBoundsFor(int slotIndex, IslandSize size) {
        if (size == null) {
            throw new NullPointerException("size");
        }
        Slot slot = slotForIndex(slotIndex);
        long radius = size.radiusChunks();
        long minX = slot.centerChunkX() - radius;
        long minZ = slot.centerChunkZ() - radius;
        long maxX = slot.centerChunkX() + radius;
        long maxZ = slot.centerChunkZ() + radius;
        validateChunkCoordinate(minX);
        validateChunkCoordinate(minZ);
        validateChunkCoordinate(maxX);
        validateChunkCoordinate(maxZ);
        return new ChunkBounds(
                Math.toIntExact(minX), Math.toIntExact(minZ),
                Math.toIntExact(maxX), Math.toIntExact(maxZ));
    }

    public Region.RegionBounds boundsFor(Slot slot, IslandSize size) {
        if (slot == null) {
            throw new NullPointerException("slot");
        }
        return boundsFor(slot.index(), size);
    }

    public ChunkBounds chunkBoundsFor(Slot slot, IslandSize size) {
        if (slot == null) {
            throw new NullPointerException("slot");
        }
        return chunkBoundsFor(slot.index(), size);
    }

    /**
     * Returns the square-cell index containing a chunk. The cell is deliberately the full pitch
     * square, not the island footprint; callers must check the returned slot's island bounds.
     *
     * @return the configured slot index, or {@code -1} when the cell is outside the configured
     *         index range
     */
    public int slotContaining(int chunkX, int chunkZ) {
        long cellX = Math.floorDiv((long) chunkX + pitchChunks / 2L, pitchChunks);
        long cellZ = Math.floorDiv((long) chunkZ + pitchChunks / 2L, pitchChunks);
        long index = spiralIndex(cellX, cellZ);
        if (index > maxSlotIndex) {
            return -1;
        }
        if (index < 0 || index > Integer.MAX_VALUE) {
            return -1;
        }
        return (int) index;
    }

    /** A non-throwing form for world-boundary and stale-coordinate checks. */
    public OptionalInt findSlotContaining(int chunkX, int chunkZ) {
        try {
            int slot = slotContaining(chunkX, chunkZ);
            return slot < 0 ? OptionalInt.empty() : OptionalInt.of(slot);
        } catch (IllegalArgumentException exception) {
            return OptionalInt.empty();
        }
    }

    public OptionalInt trySlotContaining(int chunkX, int chunkZ) {
        return findSlotContaining(chunkX, chunkZ);
    }

    /** Checks the actual island footprint, excluding the void gap around it. */
    public boolean contains(int slotIndex, IslandSize size, int chunkX, int chunkZ) {
        return chunkBoundsFor(slotIndex, size).contains(chunkX, chunkZ);
    }

    public boolean isWithinFootprint(int slotIndex, IslandSize size, int chunkX, int chunkZ) {
        return contains(slotIndex, size, chunkX, chunkZ);
    }

    private void validateIndex(int index) {
        if (index < 0 || index > maxSlotIndex) {
            throw new IllegalArgumentException(
                    "Slot index must be between 0 and " + maxSlotIndex + ": " + index);
        }
    }

    private void validateChunkCoordinate(long chunkCoordinate) {
        long minBlock = chunkCoordinate * BLOCKS_PER_CHUNK;
        long maxBlock = minBlock + BLOCKS_PER_CHUNK - 1L;
        if (minBlock < -WORLD_LIMIT || maxBlock > WORLD_LIMIT) {
            throw new IllegalArgumentException(
                    "Slot coordinate exceeds the Minecraft world limit: " + chunkCoordinate);
        }
        if (chunkCoordinate < Integer.MIN_VALUE || chunkCoordinate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Slot coordinate does not fit a chunk coordinate: " + chunkCoordinate);
        }
    }

    private long[] coordinatesForIndex(long index) {
        if (index == 0) {
            return new long[]{0L, 0L};
        }

        long layer = (long) Math.ceil((Math.sqrt(index + 1.0D) - 1.0D) / 2.0D);
        while (maximumIndexForLayer(layer) < index) {
            layer++;
        }
        while (layer > 0 && maximumIndexForLayer(layer - 1) >= index) {
            layer--;
        }

        long base = (2L * layer - 1L) * (2L * layer - 1L);
        long offset = index - base;
        long side = 2L * layer;

        if (offset < side) {
            return new long[]{layer, -layer + 1L + offset};
        }
        offset -= side;
        if (offset < side) {
            return new long[]{layer - 1L - offset, layer};
        }
        offset -= side;
        if (offset < side) {
            return new long[]{-layer, layer - 1L - offset};
        }
        offset -= side;
        return new long[]{-layer + 1L + offset, -layer};
    }

    private long spiralIndex(long cellX, long cellZ) {
        long layer = Math.max(Math.abs(cellX), Math.abs(cellZ));
        if (layer == 0) {
            return 0L;
        }

        long base = (2L * layer - 1L) * (2L * layer - 1L);
        long side = 2L * layer;

        if (cellX == layer && cellZ > -layer) {
            return base + cellZ + layer - 1L;
        }
        if (cellZ == layer && cellX < layer) {
            return base + side + layer - 1L - cellX;
        }
        if (cellX == -layer && cellZ < layer) {
            return base + 2L * side + layer - 1L - cellZ;
        }
        return base + 3L * side + cellX + layer - 1L;
    }

    private long maximumIndexForLayer(long layer) {
        return (2L * layer + 1L) * (2L * layer + 1L) - 1L;
    }

    public record Slot(int index, int centerChunkX, int centerChunkZ) {
        public int x() {
            return centerChunkX;
        }

        public int z() {
            return centerChunkZ;
        }

        public int chunkX() {
            return centerChunkX;
        }

        public int chunkZ() {
            return centerChunkZ;
        }
    }

    public record ChunkBounds(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        public ChunkBounds {
            if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) {
                throw new IllegalArgumentException("Chunk bounds must have ordered endpoints");
            }
        }

        public boolean contains(int chunkX, int chunkZ) {
            return chunkX >= minChunkX && chunkX <= maxChunkX
                    && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
        }

        public int width() {
            return maxChunkX - minChunkX + 1;
        }

        public int height() {
            return maxChunkZ - minChunkZ + 1;
        }

        public int chunkCount() {
            return Math.multiplyExact(width(), height());
        }

        public Region.RegionBounds toRegionBounds() {
            return new Region.RegionBounds(minChunkX, minChunkZ, maxChunkX, maxChunkZ);
        }
    }
}
