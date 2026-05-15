package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.ChunkListener;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import me.beeliebub.tweaks.protection.ProtectionManager;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ChunkListenerTest {

    @BeforeAll
    static void initKeys() {
        try (MockedConstruction<NamespacedKey> ignored = mockConstruction(NamespacedKey.class)) {
            ProtectionKeys.init(mock(Tweaks.class));
        }
    }

    private ProtectionManager protection;
    private ChunkListener listener;

    @BeforeEach
    void setUp() {
        protection = new ProtectionManager(mock(Tweaks.class));
        listener = new ChunkListener(protection);
    }

    private static Chunk chunkAt(long key, List<String> existingPdc) {
        Chunk chunk = mock(Chunk.class);
        when(chunk.getChunkKey()).thenReturn(key);
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
        protection.pendingStamps().put(42L, pending);

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer())
                .set(any(NamespacedKey.class), any(), argThat(arg ->
                        arg instanceof List<?> l && Set.copyOf(l).equals(Set.of("home", "admin"))));
        assertFalse(protection.pendingStamps().containsKey(42L),
                "drained pending key must be removed atomically");
    }

    @Test
    void noStampsForUnrelatedChunkKey() {
        Chunk chunk = chunkAt(7L, List.of());
        Set<String> pending = ConcurrentHashMap.newKeySet();
        pending.add("home");
        protection.pendingStamps().put(99L, pending);

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer(), never())
                .set(any(NamespacedKey.class), any(), any());
        assertTrue(protection.pendingStamps().containsKey(99L),
                "unrelated pending entry must not be touched");
    }

    @Test
    void mergesPendingStampsIntoExistingPdcList() {
        Chunk chunk = chunkAt(1L, List.of("existing"));
        Set<String> pending = ConcurrentHashMap.newKeySet();
        pending.add("new");
        protection.pendingStamps().put(1L, pending);

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer())
                .set(any(NamespacedKey.class), any(), eq(List.of("existing", "new")));
    }

    @Test
    void stripsOrphanedRegionsFromPdc() {
        Chunk chunk = chunkAt(5L, List.of("alive", "dead"));
        protection.orphanedRegions().add("dead");

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer())
                .set(any(NamespacedKey.class), any(), eq(List.of("alive")));
    }

    @Test
    void deletesKeyWhenAllPointersAreOrphaned() {
        Chunk chunk = chunkAt(5L, List.of("dead1", "dead2"));
        protection.orphanedRegions().add("dead1");
        protection.orphanedRegions().add("dead2");

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer()).remove(any(NamespacedKey.class));
    }

    @Test
    void leavesPdcAloneWhenNoOrphansMatch() {
        Chunk chunk = chunkAt(5L, List.of("alive1", "alive2"));
        protection.orphanedRegions().add("unrelated_dead");

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer(), never())
                .set(any(NamespacedKey.class), any(), any());
        verify(chunk.getPersistentDataContainer(), never()).remove(any(NamespacedKey.class));
    }

    @Test
    void skipsOrphanScanWhenChunkPdcEmpty() {
        Chunk chunk = chunkAt(5L, List.of());
        protection.orphanedRegions().add("dead");

        listener.onChunkLoad(eventFor(chunk));

        verify(chunk.getPersistentDataContainer(), never())
                .set(any(NamespacedKey.class), any(), any());
        verify(chunk.getPersistentDataContainer(), never()).remove(any(NamespacedKey.class));
    }

    @Test
    void emptyPendingSetIsTreatedAsNoStamp() {
        Chunk chunk = chunkAt(8L, List.of());
        protection.pendingStamps().put(8L, ConcurrentHashMap.newKeySet());

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
}
