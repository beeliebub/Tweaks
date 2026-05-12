package me.beeliebub.tweaks.tests.minigames.andrew;

import me.beeliebub.tweaks.minigames.andrew.WhackArena;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class WhackArenaTest {

    private final World world = mock(World.class);

    private Location loc(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    @Test
    void normalisesCornersIntoMinMaxBoundingBox() {
        WhackArena arena = new WhackArena(loc(10, 20, 30), loc(0, 5, 25));
        assertEquals(0, arena.getMinX());
        assertEquals(5, arena.getMinY());
        assertEquals(25, arena.getMinZ());
        assertEquals(10, arena.getMaxX());
        assertEquals(20, arena.getMaxY());
        assertEquals(30, arena.getMaxZ());
        assertSame(world, arena.getWorld());
    }

    @Test
    void containsAcceptsPointsInsideAndOnTheBoundary() {
        WhackArena arena = new WhackArena(loc(0, 0, 0), loc(10, 10, 10));
        assertTrue(arena.contains(loc(0, 0, 0)));
        assertTrue(arena.contains(loc(10, 10, 10)));
        assertTrue(arena.contains(loc(5, 5, 5)));
    }

    @Test
    void containsRejectsPointsOutsideEachAxis() {
        WhackArena arena = new WhackArena(loc(0, 0, 0), loc(10, 10, 10));
        assertFalse(arena.contains(loc(-1, 5, 5)));
        assertFalse(arena.contains(loc(11, 5, 5)));
        assertFalse(arena.contains(loc(5, 5, -1)));
        assertFalse(arena.contains(loc(5, 5, 11)));
    }

    @Test
    void containsRejectsPointsInOtherWorlds() {
        WhackArena arena = new WhackArena(loc(0, 0, 0), loc(10, 10, 10));
        World otherWorld = mock(World.class);
        Location elsewhere = new Location(otherWorld, 5, 5, 5);
        assertFalse(arena.contains(elsewhere));
    }

    @Test
    void spawnBlocksStartEmptyAndUnmodifiable() {
        WhackArena arena = new WhackArena(loc(0, 0, 0), loc(1, 1, 1));
        assertTrue(arena.getSpawnBlocks().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> arena.getSpawnBlocks().add(loc(0, 0, 0)));
    }

    @Test
    void addAndClearSpawnBlocksMutateInternalList() {
        WhackArena arena = new WhackArena(loc(0, 0, 0), loc(10, 10, 10));
        arena.addSpawnBlock(loc(1, 2, 3));
        arena.addSpawnBlock(loc(4, 5, 6));
        assertEquals(2, arena.getSpawnBlocks().size());
        arena.clearSpawnBlocks();
        assertTrue(arena.getSpawnBlocks().isEmpty());
    }

    @Test
    void scanForBlocksFindsMatchingTypesAndReturnsCount() {
        WhackArena arena = new WhackArena(loc(0, 0, 0), loc(1, 0, 1));
        // 4 cells: (0,0,0), (1,0,0), (0,0,1), (1,0,1)
        Block emerald = mock(Block.class);
        when(emerald.getType()).thenReturn(Material.EMERALD_BLOCK);
        when(emerald.getLocation()).thenReturn(loc(0, 0, 0));
        Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);
        Block diamond = mock(Block.class);
        when(diamond.getType()).thenReturn(Material.DIAMOND_BLOCK);
        when(diamond.getLocation()).thenReturn(loc(1, 0, 1));

        when(world.getBlockAt(0, 0, 0)).thenReturn(emerald);
        when(world.getBlockAt(1, 0, 0)).thenReturn(stone);
        when(world.getBlockAt(0, 0, 1)).thenReturn(stone);
        when(world.getBlockAt(1, 0, 1)).thenReturn(diamond);

        int count = arena.scanForBlocks(Material.EMERALD_BLOCK, Material.DIAMOND_BLOCK);
        assertEquals(2, count);
        assertEquals(2, arena.getSpawnBlocks().size());
    }

    @Test
    void scanForBlocksReplacesPreviousSpawnBlocks() {
        WhackArena arena = new WhackArena(loc(0, 0, 0), loc(0, 0, 0));
        arena.addSpawnBlock(loc(99, 99, 99));

        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);

        arena.scanForBlocks(Material.STONE);
        assertTrue(arena.getSpawnBlocks().isEmpty(),
                "scanForBlocks must clear previous spawn blocks even when nothing matches");
    }
}
