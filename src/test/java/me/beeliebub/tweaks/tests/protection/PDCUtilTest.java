package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.PDCUtil;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PDCUtilTest {

    @BeforeAll
    static void initKeys() {
        try (MockedConstruction<NamespacedKey> ignored = mockConstruction(NamespacedKey.class)) {
            ProtectionKeys.init(mock(Tweaks.class));
        }
    }

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
        assertEquals(List.of("home", "spawn"), PDCUtil.read(chunk));
    }

    @Test
    void readReturnsEmptyWhenPdcUnset() {
        Chunk chunk = chunkWithExisting(List.of());
        assertTrue(PDCUtil.read(chunk).isEmpty());
    }

    @Test
    void appendSingleIdWritesMergedList() {
        Chunk chunk = chunkWithExisting(List.of("home"));
        PDCUtil.append(chunk, "admin");

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc).set(any(NamespacedKey.class), any(), eq(List.of("home", "admin")));
    }

    @Test
    void appendSkipsWriteWhenIdAlreadyPresent() {
        Chunk chunk = chunkWithExisting(List.of("home", "spawn"));
        PDCUtil.append(chunk, "home");

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc, never()).set(any(NamespacedKey.class), any(), any());
    }

    @Test
    void appendCollectionDedupsAgainstExisting() {
        Chunk chunk = chunkWithExisting(List.of("home", "spawn"));
        PDCUtil.append(chunk, List.of("home", "admin", "spawn", "vault"));

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc).set(any(NamespacedKey.class), any(), eq(List.of("home", "spawn", "admin", "vault")));
    }

    @Test
    void appendOntoEmptyChunkStartsTheList() {
        Chunk chunk = chunkWithExisting(List.of());
        PDCUtil.append(chunk, List.of("home"));

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc).set(any(NamespacedKey.class), any(), eq(List.of("home")));
    }

    @Test
    void appendNoOpsOnEmptyInput() {
        Chunk chunk = chunkWithExisting(List.of("home"));
        PDCUtil.append(chunk, List.of());

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc, never()).set(any(NamespacedKey.class), any(), any());
    }

    @Test
    void removeStripsDeadIdsLeavingOthers() {
        Chunk chunk = chunkWithExisting(List.of("home", "dead", "spawn"));
        PDCUtil.remove(chunk, Set.of("dead"));

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc).set(any(NamespacedKey.class), any(), eq(List.of("home", "spawn")));
        verify(pdc, never()).remove(any(NamespacedKey.class));
    }

    @Test
    void removeDeletesKeyWhenListBecomesEmpty() {
        Chunk chunk = chunkWithExisting(List.of("dead1", "dead2"));
        PDCUtil.remove(chunk, Set.of("dead1", "dead2"));

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc).remove(any(NamespacedKey.class));
        verify(pdc, never()).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
    }

    @Test
    void removeNoOpsWhenNothingMatches() {
        Chunk chunk = chunkWithExisting(List.of("home", "spawn"));
        PDCUtil.remove(chunk, Set.of("dead"));

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc, never()).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
        verify(pdc, never()).remove(any(NamespacedKey.class));
    }

    @Test
    void removeNoOpsOnEmptyChunk() {
        Chunk chunk = chunkWithExisting(List.of());
        PDCUtil.remove(chunk, Set.of("dead"));

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        verify(pdc, never()).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
        verify(pdc, never()).remove(any(NamespacedKey.class));
    }

    @Test
    void removeNoOpsOnEmptyDeadSet() {
        Chunk chunk = mock(Chunk.class);
        PDCUtil.remove(chunk, Set.of());
        verifyNoInteractions(chunk);
    }
}
