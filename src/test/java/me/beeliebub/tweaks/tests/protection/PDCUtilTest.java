package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.utils.PDCUtil;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PDCUtilTest {

    // PDCUtil no longer resolves its key from a static - the caller (ProtectionManager) passes
    // it in - so any NamespacedKey stands in here; nothing under test cares about its identity.
    private static final NamespacedKey KEY = mock(NamespacedKey.class);

    private static Chunk chunkWithExisting(List<String> existing) {
        Chunk chunk = mock(Chunk.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(chunk.getPersistentDataContainer()).thenReturn(pdc);
        doReturn(existing).when(pdc).getOrDefault(any(NamespacedKey.class), any(), eq(List.of()));
        return chunk;
    }

    @Test
    void readReturnsListFromPdc() {
        Chunk chunk = chunkWithExisting(List.of("home", "spawn"));
        assertEquals(List.of("home", "spawn"), PDCUtil.read(chunk, KEY));
    }

    @Test
    void readReturnsEmptyWhenPdcUnset() {
        Chunk chunk = chunkWithExisting(List.of());
        assertTrue(PDCUtil.read(chunk, KEY).isEmpty());
    }

    @Test
    void appendSingleIdWritesMergedList() {
        Chunk chunk = chunkWithExisting(List.of("home"));
        PDCUtil.append(chunk, "admin", KEY);

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc).set(any(NamespacedKey.class), any(), eq(List.of("home", "admin")));
    }

    @Test
    void appendSkipsWriteWhenIdAlreadyPresent() {
        Chunk chunk = chunkWithExisting(List.of("home", "spawn"));
        PDCUtil.append(chunk, "home", KEY);

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc, never()).set(any(NamespacedKey.class), any(), any());
    }

    @Test
    void appendCollectionDedupsAgainstExisting() {
        Chunk chunk = chunkWithExisting(List.of("home", "spawn"));
        PDCUtil.append(chunk, List.of("home", "admin", "spawn", "vault"), KEY);

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc).set(any(NamespacedKey.class), any(), eq(List.of("home", "spawn", "admin", "vault")));
    }

    @Test
    void appendOntoEmptyChunkStartsTheList() {
        Chunk chunk = chunkWithExisting(List.of());
        PDCUtil.append(chunk, List.of("home"), KEY);

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc).set(any(NamespacedKey.class), any(), eq(List.of("home")));
    }

    @Test
    void appendNoOpsOnEmptyInput() {
        Chunk chunk = chunkWithExisting(List.of("home"));
        PDCUtil.append(chunk, List.of(), KEY);

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc, never()).set(any(NamespacedKey.class), any(), any());
    }

    @Test
    void removeStripsDeadIdsLeavingOthers() {
        Chunk chunk = chunkWithExisting(List.of("home", "dead", "spawn"));
        PDCUtil.remove(chunk, Set.of("dead"), KEY);

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc).set(any(NamespacedKey.class), any(), eq(List.of("home", "spawn")));
        verify(pdc, never()).remove(any(NamespacedKey.class));
    }

    @Test
    void removeDeletesKeyWhenListBecomesEmpty() {
        Chunk chunk = chunkWithExisting(List.of("dead1", "dead2"));
        PDCUtil.remove(chunk, Set.of("dead1", "dead2"), KEY);

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc).remove(any(NamespacedKey.class));
        verify(pdc, never()).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
    }

    @Test
    void removeNoOpsWhenNothingMatches() {
        Chunk chunk = chunkWithExisting(List.of("home", "spawn"));
        PDCUtil.remove(chunk, Set.of("dead"), KEY);

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc, never()).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
        verify(pdc, never()).remove(any(NamespacedKey.class));
    }

    @Test
    void removeNoOpsOnEmptyChunk() {
        Chunk chunk = chunkWithExisting(List.of());
        PDCUtil.remove(chunk, Set.of("dead"), KEY);

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc, never()).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
        verify(pdc, never()).remove(any(NamespacedKey.class));
    }

    @Test
    void removeNoOpsOnEmptyDeadSet() {
        Chunk chunk = mock(Chunk.class);
        PDCUtil.remove(chunk, Set.of(), KEY);
        verifyNoInteractions(chunk);
    }
}
