package me.beeliebub.tweaks.tests;

import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link YamlStore}. The assertion that {@link YamlStore#writeAsync} runs its
 * filler on the calling thread pins the class's load-bearing design decision (see class Javadoc):
 * building the {@link YamlConfiguration} <em>is</em> the snapshot, so it must happen synchronously
 * before the async write, never inside the async lambda.
 */
class YamlStoreTest {

    @TempDir
    File tempDir;

    private JavaPlugin plugin;
    private List<LogRecord> logRecords;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        Logger logger = Logger.getLogger("YamlStoreTest-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logRecords = new ArrayList<>();
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { logRecords.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getDataFolder()).thenReturn(tempDir);
    }

    @AfterEach
    void tearDown() {
        // Best-effort drain so Windows @TempDir cleanup doesn't race a lingering async write.
        try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    // ---- Layer 1: static primitives ---------------------------------------------

    @Test
    void saveNowWritesFileAndReturnsTrue() {
        File file = new File(tempDir, "example.yml");
        boolean ok = YamlStore.saveNow(plugin, file, "example", cfg -> cfg.set("key", "value"));

        assertTrue(ok);
        assertTrue(file.exists());
        assertEquals("value", YamlStore.load(file).getString("key"));
    }

    @Test
    void loadReturnsEmptyConfigForMissingFile() {
        File file = new File(tempDir, "missing.yml");
        YamlConfiguration config = YamlStore.load(file);
        assertNotNull(config);
        assertTrue(config.getKeys(false).isEmpty());
    }

    @Test
    void saveNowReturnsFalseAndLogsWithThrowableWhenTargetUnwritable() {
        // A directory at the exact target path makes config.save(file) throw IOException on
        // every platform — the portable way to force the failure path without OS-specific
        // permission flags.
        File blocked = new File(tempDir, "blocked.yml");
        assertTrue(blocked.mkdirs());

        boolean ok = YamlStore.saveNow(plugin, blocked, "example", cfg -> cfg.set("key", "value"));

        assertFalse(ok);
        assertEquals(1, logRecords.size());
        assertEquals(Level.WARNING, logRecords.getFirst().getLevel());
        assertNotNull(logRecords.getFirst().getThrown(), "the IOException must be attached, not just its message");
    }

    @Test
    void saveNowReturnsFalseAndLogsWhenFillerThrows() {
        File file = new File(tempDir, "example2.yml");

        boolean ok = YamlStore.saveNow(plugin, file, "example", cfg -> {
            throw new IllegalStateException("boom");
        });

        assertFalse(ok);
        assertFalse(file.exists(), "a filler failure must never produce a partial file");
        assertEquals(1, logRecords.size());
        assertEquals(Level.WARNING, logRecords.getFirst().getLevel());
        assertNotNull(logRecords.getFirst().getThrown());
    }

    // ---- Layer 2: keyed directory store ------------------------------------------

    @Test
    void writeAsyncRunsFillerOnCallingThread() {
        YamlStore store = new YamlStore(plugin, new File(tempDir, "store"), "test data");
        Thread callingThread = Thread.currentThread();
        Thread[] fillerThread = new Thread[1];

        store.writeAsync("k", cfg -> {
            fillerThread[0] = Thread.currentThread();
            cfg.set("v", 1);
        }).join();

        assertSame(callingThread, fillerThread[0],
                "the filler must run on the calling thread — it IS the snapshot, per the class contract");
    }

    @Test
    void writeAsyncRoundTripsThroughFileFor() throws Exception {
        YamlStore store = new YamlStore(plugin, new File(tempDir, "store"), "test data");
        store.writeAsync("player1", cfg -> cfg.set("balance", 42)).get(2, TimeUnit.SECONDS);

        assertTrue(store.exists("player1"));
        assertEquals(42, store.read("player1").getInt("balance"));
        assertEquals(new File(tempDir, "store/player1.yml"), store.fileFor("player1"));
    }

    @Test
    void writeAsyncFutureCompletesExceptionallyOnUnwritableTarget() {
        YamlStore store = new YamlStore(plugin, new File(tempDir, "store2"), "test data");
        assertTrue(new File(tempDir, "store2/blocked.yml").mkdirs());

        var future = store.writeAsync("blocked", cfg -> cfg.set("v", 1));

        Exception thrown = assertThrows(Exception.class, future::join);
        assertNotNull(thrown);
        assertFalse(logRecords.isEmpty(), "the failure must still be logged even though the future is exceptional");
        assertEquals(Level.WARNING, logRecords.getFirst().getLevel());
        assertNotNull(logRecords.getFirst().getThrown());
    }

    @Test
    void writeAsyncCompletesExceptionallyAndLogsWhenFillerThrows() {
        YamlStore store = new YamlStore(plugin, new File(tempDir, "store6"), "test data");

        var future = store.writeAsync("k", cfg -> {
            throw new IllegalStateException("boom");
        });

        assertTrue(future.isCompletedExceptionally());
        assertFalse(store.exists("k"), "a filler failure must never reach the disk write");
        assertFalse(logRecords.isEmpty(), "the failure must be logged");
        assertEquals(Level.WARNING, logRecords.getFirst().getLevel());
        assertNotNull(logRecords.getFirst().getThrown());
    }

    @Test
    void deleteAsyncRemovesExistingFile() throws Exception {
        YamlStore store = new YamlStore(plugin, new File(tempDir, "store3"), "test data");
        store.writeAsync("k", cfg -> cfg.set("v", 1)).get(2, TimeUnit.SECONDS);
        assertTrue(store.exists("k"));

        store.deleteAsync("k").get(2, TimeUnit.SECONDS);

        assertFalse(store.exists("k"));
    }

    @Test
    void deleteAsyncIsNoOpForMissingFile() throws Exception {
        YamlStore store = new YamlStore(plugin, new File(tempDir, "store4"), "test data");
        assertDoesNotThrow(() -> store.deleteAsync("nope").get(2, TimeUnit.SECONDS));
    }

    @Test
    void readAsyncResolvesToParsedConfig() throws Exception {
        YamlStore store = new YamlStore(plugin, new File(tempDir, "store5"), "test data");
        store.writeAsync("k", cfg -> cfg.set("v", "hello")).get(2, TimeUnit.SECONDS);

        YamlConfiguration read = store.readAsync("k").get(2, TimeUnit.SECONDS);

        assertEquals("hello", read.getString("v"));
    }

    @Test
    void constructorCreatesMissingDirectory() {
        File dir = new File(tempDir, "brand-new-subdir");
        assertFalse(dir.exists());
        new YamlStore(plugin, dir, "test data");
        assertTrue(dir.isDirectory());
    }
}
