package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.PendingStampsStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PendingStampsStoreTest {

    private static Tweaks pluginWithLogger() {
        Tweaks plugin = mock(Tweaks.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        return plugin;
    }

    @Test
    void writeNowCreatesFileAndRoundTripsBackIntoCache(@TempDir Path tmp) throws IOException {
        ConcurrentHashMap<Long, Set<String>> stamps = new ConcurrentHashMap<>();
        stamps.put(42L, newSet("home", "spawn"));
        stamps.put(-1234567890123L, newSet("admin"));

        PendingStampsStore writer = new PendingStampsStore(pluginWithLogger(), tmp.toFile(), stamps);
        writer.writeNow();

        assertTrue(tmp.resolve("pending_stamps.yml").toFile().exists());

        ConcurrentHashMap<Long, Set<String>> reloaded = new ConcurrentHashMap<>();
        PendingStampsStore reader = new PendingStampsStore(pluginWithLogger(), tmp.toFile(), reloaded);
        reader.load();

        assertEquals(Set.of("home", "spawn"), reloaded.get(42L));
        assertEquals(Set.of("admin"), reloaded.get(-1234567890123L));
    }

    @Test
    void loadIgnoresMissingFileAndPopulatesNothing(@TempDir Path tmp) {
        ConcurrentHashMap<Long, Set<String>> stamps = new ConcurrentHashMap<>();
        PendingStampsStore store = new PendingStampsStore(pluginWithLogger(), tmp.toFile(), stamps);
        store.load();
        assertTrue(stamps.isEmpty());
    }

    @Test
    void loadDeletesOrphanedTmpLeftByCrash(@TempDir Path tmp) throws IOException {
        File tmpFile = tmp.resolve("pending_stamps.tmp").toFile();
        Files.writeString(tmpFile.toPath(), "garbage: data");
        assertTrue(tmpFile.exists());

        PendingStampsStore store = new PendingStampsStore(pluginWithLogger(), tmp.toFile(),
                new ConcurrentHashMap<>());
        store.load();

        assertFalse(tmpFile.exists(), "orphaned .tmp must be removed on load");
    }

    @Test
    void writeNowReplacesExistingYamlAtomically(@TempDir Path tmp) throws IOException {
        ConcurrentHashMap<Long, Set<String>> first = new ConcurrentHashMap<>();
        first.put(1L, newSet("alpha"));
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), first).writeNow();

        ConcurrentHashMap<Long, Set<String>> second = new ConcurrentHashMap<>();
        second.put(2L, newSet("beta"));
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), second).writeNow();

        ConcurrentHashMap<Long, Set<String>> reloaded = new ConcurrentHashMap<>();
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), reloaded).load();

        assertNull(reloaded.get(1L));
        assertEquals(Set.of("beta"), reloaded.get(2L));
    }

    @Test
    void writeNowLeavesNoLeftoverTmpFile(@TempDir Path tmp) throws IOException {
        ConcurrentHashMap<Long, Set<String>> stamps = new ConcurrentHashMap<>();
        stamps.put(7L, newSet("x"));
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), stamps).writeNow();

        assertFalse(tmp.resolve("pending_stamps.tmp").toFile().exists(),
                ".tmp must be moved into place, not left behind");
        assertTrue(tmp.resolve("pending_stamps.yml").toFile().exists());
    }

    @Test
    void writeNowCreatesDataFolderIfMissing(@TempDir Path tmp) throws IOException {
        File missing = tmp.resolve("subdir").toFile();
        assertFalse(missing.exists());

        ConcurrentHashMap<Long, Set<String>> stamps = new ConcurrentHashMap<>();
        stamps.put(1L, newSet("a"));
        new PendingStampsStore(pluginWithLogger(), missing, stamps).writeNow();

        assertTrue(new File(missing, "pending_stamps.yml").exists());
    }

    @Test
    void emptyStampsProducesEmptyButValidFile(@TempDir Path tmp) throws IOException {
        PendingStampsStore store = new PendingStampsStore(pluginWithLogger(), tmp.toFile(),
                new ConcurrentHashMap<>());
        store.writeNow();

        ConcurrentHashMap<Long, Set<String>> reloaded = new ConcurrentHashMap<>();
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), reloaded).load();
        assertTrue(reloaded.isEmpty());
    }

    @Test
    void loadSkipsNonNumericChunkKeys(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("pending_stamps.yml"), """
                stamps:
                  '42':
                    - good
                  bogus:
                    - bad
                """);

        ConcurrentHashMap<Long, Set<String>> stamps = new ConcurrentHashMap<>();
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), stamps).load();

        assertEquals(1, stamps.size());
        assertEquals(Set.of("good"), stamps.get(42L));
    }

    @Test
    void snapshotIsDecoupledFromLiveMapMutationAfterCall(@TempDir Path tmp) throws IOException {
        ConcurrentHashMap<Long, Set<String>> stamps = new ConcurrentHashMap<>();
        Set<String> live = newSet("frozen");
        stamps.put(99L, live);

        PendingStampsStore store = new PendingStampsStore(pluginWithLogger(), tmp.toFile(), stamps);
        store.writeNow();

        // Mutate the live map after the write completes — must not affect the file.
        live.add("late");
        stamps.put(100L, newSet("late2"));

        ConcurrentHashMap<Long, Set<String>> reloaded = new ConcurrentHashMap<>();
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), reloaded).load();
        assertEquals(Set.of("frozen"), reloaded.get(99L));
        assertNull(reloaded.get(100L));
    }

    private static Set<String> newSet(String... values) {
        Set<String> set = ConcurrentHashMap.newKeySet();
        set.addAll(List.of(values));
        return set;
    }
}
