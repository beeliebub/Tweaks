package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionWriter;
import me.beeliebub.tweaks.utils.GeometryUtil;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProtectionManagerResizeTest {

    private static final UUID OWNER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID FOREIGN_OWNER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private ProtectionManager protection;
    private World world;

    @BeforeEach
    void setUp() {
        protection = new ProtectionManager(mock(Tweaks.class));
        world = mock(World.class);
        when(world.getName()).thenReturn("skyblock");
    }

    @Test
    void resizeStampsOnlyNewChunksMergesExistingPointersAndQueuesOneSnapshot() {
        Region original = region("island", OWNER, new Region.RegionBounds(0, 0, 0, 0));
        protection.regions().put(ProtectionManager.keyOf(original), original);

        List<Chunk> loaded = new ArrayList<>();
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean())).thenAnswer(invocation -> {
            Chunk chunk = chunkWithPointers(List.of("safezone"));
            loaded.add(chunk);
            return CompletableFuture.completedFuture(chunk);
        });

        RegionWriter writer = mock(RegionWriter.class);
        protection.setWriter(writer);
        String cacheKey = ProtectionManager.keyOf(world.getName(), original.id());
        protection.orphanedRegions().add(cacheKey);

        Region.RegionBounds replacement = new Region.RegionBounds(0, 0, 1, 1);
        assertTrue(protection.resize(world, original.id(), replacement));

        Region updated = protection.regions().get(cacheKey);
        assertNotSame(original, updated);
        assertEquals(replacement, updated.bounds());
        assertEquals(3, loaded.size(), "the existing chunk must not be re-stamped");
        verify(world, times(3)).getChunkAtAsync(anyInt(), anyInt(), eq(true));
        verify(world, never()).getChunkAtAsync(eq(0), eq(0), eq(true));
        for (Chunk chunk : loaded) {
            verify(chunk.getPersistentDataContainer())
                    .set(any(NamespacedKey.class), any(), eq(List.of("safezone", "island")));
        }
        verify(writer, times(1)).queue(same(updated));
        verify(writer, never()).untombstone(any());
        assertTrue(protection.orphanedRegions().contains(cacheKey));
    }

    @Test
    void resizeAddsOnlyNewChunksToPendingStampsForLargeExpansion() {
        Region original = region("island", OWNER, new Region.RegionBounds(0, 0, 0, 0));
        protection.regions().put(ProtectionManager.keyOf(original), original);
        long existingKey = GeometryUtil.chunkKey(0, 0);
        protection.pendingStamps().put(
                ProtectionManager.stampKey(world.getName(), existingKey), Set.of(original.id()));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

        Region.RegionBounds replacement = new Region.RegionBounds(0, 0, 2, 2);
        assertTrue(protection.resize(world, original.id(), replacement));

        assertEquals(Set.of(original.id()), protection.pendingStamps().get(
                ProtectionManager.stampKey(world.getName(), existingKey)));
        for (long key : GeometryUtil.chunkKeysInBox(0, 0, 47, 47)) {
            if (key == existingKey) continue;
            assertTrue(protection.pendingStamps().get(
                    ProtectionManager.stampKey(world.getName(), key)).contains(original.id()));
        }
        verify(world, never()).getChunkAtAsync(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void resizeRejectsForeignOverlapWithoutChangingLiveRegion() {
        Region original = region("island", OWNER, new Region.RegionBounds(0, 0, 0, 0));
        Region foreign = region("foreign", FOREIGN_OWNER, new Region.RegionBounds(1, 1, 1, 1));
        protection.regions().put(ProtectionManager.keyOf(original), original);
        protection.regions().put(ProtectionManager.keyOf(foreign), foreign);
        RegionWriter writer = mock(RegionWriter.class);
        protection.setWriter(writer);

        assertFalse(protection.resize(world, original.id(), new Region.RegionBounds(0, 0, 2, 2)));

        assertSame(original, protection.regions().get(ProtectionManager.keyOf(original)));
        verifyNoInteractions(writer);
        assertTrue(protection.pendingStamps().isEmpty());
    }

    @Test
    void resizeRejectsWorldMismatchAndUnchangedBounds() {
        Region original = region("island", OWNER, new Region.RegionBounds(0, 0, 1, 1));
        protection.regions().put(ProtectionManager.keyOf(original), original);
        RegionWriter writer = mock(RegionWriter.class);
        protection.setWriter(writer);

        World otherWorld = mock(World.class);
        when(otherWorld.getName()).thenReturn("nether");

        assertFalse(protection.resize(otherWorld, original.id(), new Region.RegionBounds(0, 0, 2, 2)));
        assertFalse(protection.resize(world, original.id(), original.bounds()));
        assertSame(original, protection.regions().get(ProtectionManager.keyOf(original)));
        verifyNoInteractions(writer);
    }

    private static Region region(String id, UUID owner, Region.RegionBounds bounds) {
        return new Region(id, owner, List.of(), Map.of(), Map.of(), null, bounds, "skyblock");
    }

    private static Chunk chunkWithPointers(List<String> pointers) {
        Chunk chunk = mock(Chunk.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(chunk.getPersistentDataContainer()).thenReturn(pdc);
        doReturn(pointers).when(pdc)
                .getOrDefault(any(NamespacedKey.class), any(), eq(List.of()));
        return chunk;
    }
}
