package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.GeometryUtil;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class ProtectionManagerClaimTest {

    @BeforeAll
    static void initKeys() {
        try (MockedConstruction<NamespacedKey> ignored = mockConstruction(NamespacedKey.class)) {
            ProtectionKeys.init(mock(Tweaks.class));
        }
    }

    private static Region region(String id) {
        return new Region(id, UUID.randomUUID(), List.of(), EnumSet.noneOf(RegionFlag.class));
    }

    @Test
    void claimAlwaysPopulatesRegionCache() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        Region r = region("home");
        World world = mock(World.class);
        Chunk c = fakeChunk();
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(c));

        mgr.claim(r, world, 0, 0, 15, 15);

        Region cached = mgr.regions().get("home");
        assertNotNull(cached);
        assertEquals(r.id(), cached.id());
        assertEquals(r.owner(), cached.owner());
    }

    @Test
    void claimStampsBoundsFromClaimBox() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        World world = mock(World.class);
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean()))
                .thenAnswer(inv -> CompletableFuture.completedFuture(fakeChunk()));

        // Block-AABB (0,0)-(31,47) spans chunks x=[0,1], z=[0,2].
        mgr.claim(region("plot"), world, 0, 0, 31, 47);

        Region.RegionBounds bounds = mgr.regions().get("plot").bounds();
        assertNotNull(bounds);
        assertEquals(0, bounds.minChunkX());
        assertEquals(0, bounds.minChunkZ());
        assertEquals(1, bounds.maxChunkX());
        assertEquals(2, bounds.maxChunkZ());
    }

    @Test
    void claimNormalizesReversedClaimBox() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        World world = mock(World.class);
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean()))
                .thenAnswer(inv -> CompletableFuture.completedFuture(fakeChunk()));

        // Pass max coords first; bounds should still come out min < max.
        mgr.claim(region("plot"), world, 47, 31, 0, 0);

        Region.RegionBounds bounds = mgr.regions().get("plot").bounds();
        assertNotNull(bounds);
        assertEquals(0, bounds.minChunkX());
        assertEquals(0, bounds.minChunkZ());
        assertEquals(2, bounds.maxChunkX());
        assertEquals(1, bounds.maxChunkZ());
    }

    @Test
    void smallClaimUsesAsyncPathAndStampsEveryChunk() {
        Tweaks plugin = mock(Tweaks.class);
        ProtectionManager mgr = new ProtectionManager(plugin);
        World world = mock(World.class);
        List<Chunk> spawned = new ArrayList<>();
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean())).thenAnswer(inv -> {
            Chunk c = fakeChunk();
            spawned.add(c);
            return CompletableFuture.completedFuture(c);
        });

        // 1 x 5 chunks = exactly 5 chunks -> async threshold
        CompletableFuture<Void> done = mgr.claim(region("home"), world, 0, 0, 15, 79);

        assertTrue(done.isDone());
        verify(world, times(5)).getChunkAtAsync(anyInt(), anyInt(), eq(true));
        assertEquals(5, spawned.size());
        for (Chunk c : spawned) {
            verify(c).addPluginChunkTicket(plugin);
            verify(c).removePluginChunkTicket(plugin);
            verify(c.getPersistentDataContainer())
                    .set(any(NamespacedKey.class), any(), any());
        }
        assertTrue(mgr.pendingStamps().isEmpty(),
                "async path must not touch the pending-stamps map");
    }

    @Test
    void largeClaimUsesLazyPathAndPopulatesPendingStamps() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        World world = mock(World.class);

        // 1 x 6 chunks = 6 chunks, just over the threshold -> lazy
        CompletableFuture<Void> done = mgr.claim(region("admin"), world, 0, 0, 15, 95);

        assertTrue(done.isDone(), "lazy path resolves the future immediately");
        verify(world, never()).getChunkAtAsync(anyInt(), anyInt(), anyBoolean());

        long[] expected = GeometryUtil.chunkKeysInBox(0, 0, 15, 95);
        assertEquals(expected.length, mgr.pendingStamps().size());
        for (long k : expected) {
            assertTrue(mgr.pendingStamps().get(k).contains("admin"),
                    "every expected chunk key must hold the region pointer");
        }
    }

    @Test
    void thresholdIsInclusiveAtFive() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        World world = mock(World.class);
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean()))
                .thenAnswer(inv -> CompletableFuture.completedFuture(fakeChunk()));

        mgr.claim(region("five"), world, 0, 0, 15, 79); // 5 chunks
        assertTrue(mgr.pendingStamps().isEmpty(), "5-chunk claim must use async, not lazy");

        mgr.claim(region("six"), world, 0, 0, 15, 95); // 6 chunks
        assertFalse(mgr.pendingStamps().isEmpty(), "6-chunk claim must use lazy");
    }

    @Test
    void lazyPathMergesMultipleClaimsOntoTheSameChunk() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        World world = mock(World.class);

        mgr.claim(region("first"), world, 0, 0, 95, 95);   // 6x6 = 36 chunks
        mgr.claim(region("second"), world, 0, 0, 95, 95);  // same area

        long sample = GeometryUtil.chunkKey(0, 0);
        assertEquals(java.util.Set.of("first", "second"), mgr.pendingStamps().get(sample));
    }

    private static Chunk fakeChunk() {
        Chunk c = mock(Chunk.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(c.getPersistentDataContainer()).thenReturn(pdc);
        doReturn(List.of()).when(pdc)
                .getOrDefault(any(NamespacedKey.class), any(), eq(List.of()));
        return c;
    }
}
