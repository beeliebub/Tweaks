package me.beeliebub.tweaks.tests.utils;

import me.beeliebub.tweaks.utils.SafeGroundLocator;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SafeGroundLocatorTest {

    @Test
    void defersWorldAndChunkReadsUntilAsyncChunkCompletes() {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        CompletableFuture<Chunk> chunkFuture = new CompletableFuture<>();
        when(world.getChunkAtAsync(1, 2, true)).thenReturn(chunkFuture);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(128);
        when(world.getHighestBlockYAt(17, 34)).thenReturn(10);

        Block ground = mock(Block.class);
        when(ground.getType()).thenReturn(Material.STONE);
        Block passable = mock(Block.class);
        when(passable.isPassable()).thenReturn(true);
        when(chunk.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int y = invocation.getArgument(1, Integer.class);
            return y == 10 ? ground : passable;
        });

        CompletableFuture<SafeGroundLocator.Result> resultFuture =
                SafeGroundLocator.findSafeCenter(world, 17.5, 34.5, 90f, 0f);

        assertFalse(resultFuture.isDone());
        verify(world, org.mockito.Mockito.never()).getHighestBlockYAt(anyInt(), anyInt());

        chunkFuture.complete(chunk);
        SafeGroundLocator.Result result = resultFuture.join();

        assertTrue(result.groundFound());
        assertEquals(17.5, result.location().getX());
        assertEquals(11.0, result.location().getY());
        assertEquals(34.5, result.location().getZ());
        verify(world).getHighestBlockYAt(17, 34);
        verify(chunk).getBlock(1, 10, 2);
        verify(chunk).getBlock(1, 11, 2);
        verify(chunk).getBlock(1, 12, 2);
    }

    @Test
    void returnsNonMutatingFallbackWhenColumnHasNoSolidGround() {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(world.getChunkAtAsync(0, 0, true)).thenReturn(CompletableFuture.completedFuture(chunk));
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getHighestBlockYAt(4, 7)).thenReturn(-64);

        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(air.isPassable()).thenReturn(true);
        when(chunk.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(air);

        SafeGroundLocator.Result result = SafeGroundLocator
                .findSafeCenter(world, 4.5, 7.5, 0f, 0f).join();

        assertFalse(result.groundFound());
        assertEquals(64.0, result.location().getY());
        verify(world).getHighestBlockYAt(4, 7);
        verify(chunk, org.mockito.Mockito.atLeastOnce()).getBlock(4, -64, 7);
    }
}
