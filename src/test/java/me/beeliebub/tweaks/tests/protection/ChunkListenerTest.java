package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.ProtectionListeners;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.ui.RegionSelectionManager;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ChunkListenerTest {

    private ProtectionManager protection;
    private ProtectionListeners listener;

    @BeforeEach
    void setUp() {
        Tweaks plugin = mock(Tweaks.class);
        protection = new ProtectionManager(plugin);
        listener = new ProtectionListeners(plugin, protection, mock(RegionSelectionManager.class));
    }

    private static Chunk chunkAt(long key, List<String> existingPdc) {
        Chunk chunk = mock(Chunk.class);
        when(chunk.getChunkKey()).thenReturn(key);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(chunk.getWorld()).thenReturn(world);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(chunk.getPersistentDataContainer()).thenReturn(pdc);
        doReturn(existingPdc).when(pdc)
                .getOrDefault(any(NamespacedKey.class), any(), eq(List.of()));
        return chunk;
    }

    private static ChunkLoadEvent eventFor(Chunk chunk) {
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        when(event.getChunk()).thenReturn(chunk);
        return event;
    }

    @Test
    void appliesPendingStampsAndRemovesEntryFromMap() {
        Chunk chunk = chunkAt(42L, List.of());
        Set<String> pending = ConcurrentHashMap.newKeySet();
        pending.add("home");
        pending.add("admin");
        protection.pendingStamps().put(ProtectionManager.stampKey("world", 42L), pending);

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer())
                .set(any(NamespacedKey.class), any(), argThat(arg ->
                        arg instanceof List<?> l && Set.copyOf(l).equals(Set.of("home", "admin"))));
        assertFalse(protection.pendingStamps().containsKey(ProtectionManager.stampKey("world", 42L)),
                "drained pending key must be removed atomically");
    }

    @Test
    void noStampsForUnrelatedChunkKey() {
        Chunk chunk = chunkAt(7L, List.of());
        Set<String> pending = ConcurrentHashMap.newKeySet();
        pending.add("home");
        protection.pendingStamps().put(ProtectionManager.stampKey("world", 99L), pending);

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer(), never())
                .set(any(NamespacedKey.class), any(), any());
        assertTrue(protection.pendingStamps().containsKey(ProtectionManager.stampKey("world", 99L)),
                "unrelated pending entry must not be touched");
    }

    @Test
    void mergesPendingStampsIntoExistingPdcList() {
        Chunk chunk = chunkAt(1L, List.of("existing"));
        Set<String> pending = ConcurrentHashMap.newKeySet();
        pending.add("new");
        protection.pendingStamps().put(ProtectionManager.stampKey("world", 1L), pending);

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer())
                .set(any(NamespacedKey.class), any(), eq(List.of("existing", "new")));
    }

    @Test
    void stripsOrphanedRegionsFromPdc() {
        Chunk chunk = chunkAt(5L, List.of("alive", "dead"));
        protection.orphanedRegions().add("world:dead");

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer())
                .set(any(NamespacedKey.class), any(), eq(List.of("alive")));
    }

    @Test
    void deletesKeyWhenAllPointersAreOrphaned() {
        Chunk chunk = chunkAt(5L, List.of("dead1", "dead2"));
        protection.orphanedRegions().add("world:dead1");
        protection.orphanedRegions().add("world:dead2");

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer()).remove(any(NamespacedKey.class));
    }

    @Test
    void leavesPdcAloneWhenNoOrphansMatch() {
        Chunk chunk = chunkAt(5L, List.of("alive1", "alive2"));
        protection.orphanedRegions().add("world:unrelated_dead");

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer(), never())
                .set(any(NamespacedKey.class), any(), any());
        verify(chunk.getPersistentDataContainer(), never()).remove(any(NamespacedKey.class));
    }

    @Test
    void skipsOrphanScanWhenChunkPdcEmpty() {
        Chunk chunk = chunkAt(5L, List.of());
        protection.orphanedRegions().add("world:dead");

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer(), never())
                .set(any(NamespacedKey.class), any(), any());
        verify(chunk.getPersistentDataContainer(), never()).remove(any(NamespacedKey.class));
    }

    @Test
    void emptyPendingSetIsTreatedAsNoStamp() {
        Chunk chunk = chunkAt(8L, List.of());
        protection.pendingStamps().put(ProtectionManager.stampKey("world", 8L), ConcurrentHashMap.newKeySet());

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer(), never())
                .set(any(NamespacedKey.class), any(), any());
    }

    @Test
    void noPendingNoOrphansLeavesChunkUntouched() {
        Chunk chunk = chunkAt(1L, List.of("existing"));

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer(), never())
                .set(any(NamespacedKey.class), any(), any());
        verify(chunk.getPersistentDataContainer(), never()).remove(any(NamespacedKey.class));
    }

    @Test
    void stripsLiveRegionPointerWhenChunkFallsOutsideItsBounds() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        protection.regions().put("world:live", new Region(
                "live", UUID.randomUUID(), List.of(), Map.of(), Map.of(), null,
                new Region.RegionBounds(0, 0, 0, 0), "world"));
        Chunk outOfBounds = chunkAt(6L, List.of("live"));
        when(outOfBounds.getWorld()).thenReturn(world);
        when(outOfBounds.getX()).thenReturn(1);
        when(outOfBounds.getZ()).thenReturn(0);

        listener.onChunkLoad(eventFor(outOfBounds));

        verify(outOfBounds.getPersistentDataContainer()).remove(any(NamespacedKey.class));

        Chunk inBounds = chunkAt(0L, List.of("live"));
        when(inBounds.getWorld()).thenReturn(world);
        when(inBounds.getX()).thenReturn(0);
        when(inBounds.getZ()).thenReturn(0);
        listener.onChunkLoad(eventFor(inBounds));
        verify(inBounds.getPersistentDataContainer(), never()).set(any(NamespacedKey.class), any(), any());
        verify(inBounds.getPersistentDataContainer(), never()).remove(any(NamespacedKey.class));
    }

    @Test
    void preservesUnresolvedNonOrphanPointerForRecovery() {
        Chunk chunk = chunkAt(12L, List.of("unparseable"));

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer(), never())
                .set(any(NamespacedKey.class), any(), any());
        verify(chunk.getPersistentDataContainer(), never()).remove(any(NamespacedKey.class));
    }
}
