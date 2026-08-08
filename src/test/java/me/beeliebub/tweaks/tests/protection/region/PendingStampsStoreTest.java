package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.PendingStampsStore;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingStampsStoreTest {

    private static Tweaks pluginWithLogger() {
        Tweaks plugin = mock(Tweaks.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        return plugin;
    }

    private static Tweaks pluginWithLogger(Logger logger) {
        Tweaks plugin = mock(Tweaks.class);
        when(plugin.getLogger()).thenReturn(logger);
        return plugin;
    }

    @Test
    void writeNowCreatesFileAndRoundTripsBackIntoCache(@TempDir Path tmp) throws IOException {
        ConcurrentHashMap<String, Set<String>> stamps = new ConcurrentHashMap<>();
        Set<String> orphans = ConcurrentHashMap.newKeySet();
        stamps.put("world:42", newSet("home", "spawn"));
        stamps.put("world:-1234567890123", newSet("admin"));

        PendingStampsStore writer = new PendingStampsStore(
                pluginWithLogger(), tmp.toFile(), stamps, orphans);
        writer.writeNow();

        assertTrue(tmp.resolve("pending_stamps.yml").toFile().exists());

        ConcurrentHashMap<String, Set<String>> reloaded = new ConcurrentHashMap<>();
        PendingStampsStore reader = new PendingStampsStore(
                pluginWithLogger(), tmp.toFile(), reloaded, ConcurrentHashMap.newKeySet());
        reader.load();

        assertEquals(Set.of("home", "spawn"), reloaded.get("world:42"));
        assertEquals(Set.of("admin"), reloaded.get("world:-1234567890123"));
    }

    @Test
    void loadIgnoresMissingFileAndPopulatesNothing(@TempDir Path tmp) {
        ConcurrentHashMap<String, Set<String>> stamps = new ConcurrentHashMap<>();
        PendingStampsStore store = new PendingStampsStore(
                pluginWithLogger(), tmp.toFile(), stamps, ConcurrentHashMap.newKeySet());
        store.load();
        assertTrue(stamps.isEmpty());
    }

    @Test
    void loadDeletesOrphanedTmpLeftByCrash(@TempDir Path tmp) throws IOException {
        File tmpFile = tmp.resolve("pending_stamps.tmp").toFile();
        Files.writeString(tmpFile.toPath(), "garbage: data");
        assertTrue(tmpFile.exists());

        PendingStampsStore store = new PendingStampsStore(pluginWithLogger(), tmp.toFile(),
                new ConcurrentHashMap<>(), ConcurrentHashMap.newKeySet());
        store.load();

        assertFalse(tmpFile.exists(), "orphaned .tmp must be removed on load");
    }

    @Test
    void writeNowReplacesExistingYamlAtomically(@TempDir Path tmp) throws IOException {
        ConcurrentHashMap<String, Set<String>> first = new ConcurrentHashMap<>();
        first.put("world:1", newSet("alpha"));
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), first,
                ConcurrentHashMap.newKeySet()).writeNow();

        ConcurrentHashMap<String, Set<String>> second = new ConcurrentHashMap<>();
        second.put("world:2", newSet("beta"));
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), second,
                ConcurrentHashMap.newKeySet()).writeNow();

        ConcurrentHashMap<String, Set<String>> reloaded = new ConcurrentHashMap<>();
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), reloaded,
                ConcurrentHashMap.newKeySet()).load();

        assertNull(reloaded.get("world:1"));
        assertEquals(Set.of("beta"), reloaded.get("world:2"));
    }

    @Test
    void writeNowLeavesNoLeftoverTmpFile(@TempDir Path tmp) throws IOException {
        ConcurrentHashMap<String, Set<String>> stamps = new ConcurrentHashMap<>();
        stamps.put("world:7", newSet("x"));
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), stamps,
                ConcurrentHashMap.newKeySet()).writeNow();

        assertFalse(tmp.resolve("pending_stamps.tmp").toFile().exists(),
                ".tmp must be moved into place, not left behind");
        assertTrue(tmp.resolve("pending_stamps.yml").toFile().exists());
    }

    @Test
    void writeNowCreatesDataFolderIfMissing(@TempDir Path tmp) throws IOException {
        File missing = tmp.resolve("subdir").toFile();
        assertFalse(missing.exists());

        ConcurrentHashMap<String, Set<String>> stamps = new ConcurrentHashMap<>();
        stamps.put("world:1", newSet("a"));
        new PendingStampsStore(pluginWithLogger(), missing, stamps,
                ConcurrentHashMap.newKeySet()).writeNow();

        assertTrue(new File(missing, "pending_stamps.yml").exists());
    }

    @Test
    void emptyStampsProducesEmptyButValidFile(@TempDir Path tmp) throws IOException {
        PendingStampsStore store = new PendingStampsStore(pluginWithLogger(), tmp.toFile(),
                new ConcurrentHashMap<>(), ConcurrentHashMap.newKeySet());
        store.writeNow();

        ConcurrentHashMap<String, Set<String>> reloaded = new ConcurrentHashMap<>();
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), reloaded,
                ConcurrentHashMap.newKeySet()).load();
        assertTrue(reloaded.isEmpty());
    }

    @Test
    void loadSkipsNonNumericChunkKeys(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("pending_stamps.yml"), """
                stamps:
                  'world:42':
                    - good
                  bogus:
                    - bad
                """);

        ConcurrentHashMap<String, Set<String>> stamps = new ConcurrentHashMap<>();
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), stamps,
                ConcurrentHashMap.newKeySet()).load();

        assertEquals(1, stamps.size());
        assertEquals(Set.of("good"), stamps.get("world:42"));
    }

    @Test
    void snapshotIsDecoupledFromLiveMapMutationAfterCall(@TempDir Path tmp) throws IOException {
        ConcurrentHashMap<String, Set<String>> stamps = new ConcurrentHashMap<>();
        Set<String> live = newSet("frozen");
        stamps.put("world:99", live);

        PendingStampsStore store = new PendingStampsStore(pluginWithLogger(), tmp.toFile(), stamps,
                ConcurrentHashMap.newKeySet());
        store.writeNow();

        // Mutate the live map after the write completes — must not affect the file.
        live.add("late");
        stamps.put("world:100", newSet("late2"));

        ConcurrentHashMap<String, Set<String>> reloaded = new ConcurrentHashMap<>();
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), reloaded,
                ConcurrentHashMap.newKeySet()).load();
        assertEquals(Set.of("frozen"), reloaded.get("world:99"));
        assertNull(reloaded.get("world:100"));
    }

    @Test
    void orphanIdsRoundTripThroughDisk(@TempDir Path tmp) throws IOException {
        ConcurrentHashMap<String, Set<String>> stamps = new ConcurrentHashMap<>();
        Set<String> orphans = ConcurrentHashMap.newKeySet();
        orphans.addAll(List.of("deleted-home", "deleted-spawn"));

        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), stamps, orphans).writeNow();

        ConcurrentHashMap<String, Set<String>> reloadedStamps = new ConcurrentHashMap<>();
        Set<String> reloadedOrphans = ConcurrentHashMap.newKeySet();
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), reloadedStamps,
                reloadedOrphans).load();

        assertEquals(Set.of("deleted-home", "deleted-spawn"), reloadedOrphans);
    }

    @Test
    void orphanPersistenceCapsAt5000AndWarnsOnlyOnce(@TempDir Path tmp) throws IOException {
        Logger logger = mock(Logger.class);
        Set<String> orphans = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < 5_001; i++) orphans.add("orphan-" + i);

        PendingStampsStore store = new PendingStampsStore(
                pluginWithLogger(logger), tmp.toFile(), new ConcurrentHashMap<>(), orphans);
        store.writeNow();
        store.writeNow();

        Set<String> reloadedOrphans = ConcurrentHashMap.newKeySet();
        new PendingStampsStore(pluginWithLogger(), tmp.toFile(), new ConcurrentHashMap<>(),
                reloadedOrphans).load();

        assertEquals(5_000, reloadedOrphans.size());
        verify(logger).warning(contains("5000"));
    }

    private static Set<String> newSet(String... values) {
        Set<String> set = ConcurrentHashMap.newKeySet();
        set.addAll(List.of(values));
        return set;
    }
}
