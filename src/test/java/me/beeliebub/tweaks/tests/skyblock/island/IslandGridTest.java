package me.beeliebub.tweaks.tests.skyblock.island;

import me.beeliebub.tweaks.skyblock.island.IslandGrid;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandGridTest {

    @Test
    void followsOutwardSpiralAtPitchThirtyFive() {
        IslandGrid grid = new IslandGrid();

        assertEquals(0, grid.slotForIndex(0).x());
        assertEquals(0, grid.slotForIndex(0).z());
        assertEquals(35, grid.slotForIndex(1).x());
        assertEquals(0, grid.slotForIndex(1).z());
        assertEquals(35, grid.slotForIndex(2).x());
        assertEquals(35, grid.slotForIndex(2).z());
        assertEquals(-35, grid.slotForIndex(4).x());
        assertEquals(35, grid.slotForIndex(4).z());
        assertEquals(35, grid.slotForIndex(8).x());
        assertEquals(-35, grid.slotForIndex(8).z());
    }

    @Test
    void inverseLookupReturnsEveryGeneratedSpiralSlot() {
        IslandGrid grid = new IslandGrid(35, 120);

        for (int index = 0; index <= grid.maxIndex(); index++) {
            IslandGrid.Slot slot = grid.slotForIndex(index);

            assertEquals(index, grid.slotContaining(slot.centerChunkX(), slot.centerChunkZ()),
                    "slot lookup should invert index " + index);
        }
    }

    @Test
    void cellLookupDoesNotTreatVoidGapAsIslandFootprint() {
        IslandGrid grid = new IslandGrid();

        assertEquals(0, grid.slotContaining(0, 0));
        assertEquals(1, grid.slotContaining(18, 0));
        assertTrue(grid.contains(1, IslandSize.SMALL, 38, 0));
        assertFalse(grid.contains(1, IslandSize.SMALL, 18, 0));
        assertFalse(grid.contains(1, IslandSize.SMALL, 39, 0));
        assertEquals(1, grid.slotContaining(35, 0));
    }

    @Test
    void boundsAreConcentric() {
        IslandGrid grid = new IslandGrid();
        IslandGrid.ChunkBounds bounds = grid.chunkBoundsFor(0, IslandSize.LARGE);

        assertEquals(-9, bounds.minChunkX());
        assertEquals(-9, bounds.minChunkZ());
        assertEquals(9, bounds.maxChunkX());
        assertEquals(9, bounds.maxChunkZ());
    }

    @Test
    void slotIndexAndCellLookupHonorMaximum() {
        IslandGrid grid = new IslandGrid(35, 2);

        assertEquals(2, grid.maxIndex());
        assertThrows(IllegalArgumentException.class, () -> grid.slotForIndex(3));
        IslandGrid.Slot last = grid.slotForIndex(2);
        assertEquals(2, grid.slotContaining(last.centerChunkX(), last.centerChunkZ()));
        assertEquals(-1, grid.slotContaining(0, 35));
        assertTrue(grid.findSlotContaining(0, 35).isEmpty());
    }

    @Test
    void originSlotsAreExcludedAndFirstAllocatableSlotIsNine() {
        IslandGrid grid = new IslandGrid();

        for (int index = 0; index <= 8; index++) {
            assertTrue(grid.isSpawnExcluded(index), "origin ring slot " + index + " must be excluded");
        }
        assertFalse(grid.isSpawnExcluded(9));
        assertEquals(9, grid.firstAllocatableIndex());
        assertEquals(70, grid.slotForIndex(9).centerChunkX());
        assertEquals(-35, grid.slotForIndex(9).centerChunkZ());
    }

    @Test
    void exclusionScanFailsWhenAllConfiguredSlotsAreReserved() {
        IslandGrid grid = new IslandGrid(35, 8);

        assertThrows(IllegalStateException.class, grid::firstAllocatableIndex);
    }

    @Test
    void exclusionUsesStrictChebyshevBoundary() {
        IslandGrid boundary = new IslandGrid(62, 2);
        assertFalse(boundary.isSpawnExcluded(1));

        IslandGrid diagonal = new IslandGrid(55, 2);
        assertTrue(diagonal.isSpawnExcluded(1));
        assertTrue(diagonal.isSpawnExcluded(2));
    }
}
