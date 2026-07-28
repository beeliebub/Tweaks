package me.beeliebub.tweaks.tests.economy;

import me.beeliebub.tweaks.economy.EconomyManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Persistence-focused unit tests for {@link EconomyManager}'s {@code YamlStore} migration,
 * constructed via a bare Mockito-mocked {@link JavaPlugin} (no MockBukkit), mirroring
 * {@code StorageManagerTest}'s pattern.
 *
 * <p>This deliberately avoids {@link EconomyManager#setTabManager} — {@code refreshTabFor}
 * short-circuits on a null {@code TabManager} before ever touching the {@code Bukkit} static API,
 * so the disk-persistence behavior can be exercised without a running server, independent of
 * {@code EconomyManagerTest}'s full-MockBukkit coverage of the balance/rank round trips.
 */
class EconomyManagerPersistenceTest {

    @TempDir
    File dataFolder;

    private EconomyManager economy;
    private List<LogRecord> logRecords;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);

        Logger logger = Logger.getLogger("EconomyManagerPersistenceTest-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logRecords = new ArrayList<>();
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { logRecords.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        when(plugin.getLogger()).thenReturn(logger);

        economy = new EconomyManager(plugin);
    }

    @AfterEach
    void drainAsyncWrites() {
        // Mirrors StorageManagerTest: writes go through YamlStore.writeAsync on the common pool,
        // so wait for it to go idle rather than racing @TempDir cleanup on Windows.
        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);
    }

    @Test
    void setBalanceRoundTripsWithoutTabManagerWired() {
        UUID id = UUID.randomUUID();
        economy.setBalance(id, 123.45D);
        assertEquals(123.45D, economy.getBalance(id));
    }

    @Test
    void saveAllFlushesToDisk() throws Exception {
        UUID id = UUID.randomUUID();
        economy.setBalance(id, 777.0D);

        economy.saveAll().get(5, TimeUnit.SECONDS);

        File file = new File(dataFolder, "players/" + id + ".yml");
        assertTrue(file.exists());
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(file);
        assertEquals(777.0D, onDisk.getDouble("balance"));
    }

    @Test
    void unloadAndSavePlayerPersistsToDiskAndClearsCache() throws InterruptedException {
        UUID id = UUID.randomUUID();
        economy.setBalance(id, 50.0D);

        economy.unloadAndSavePlayer(id);

        // Balance reads default to 0.0 once unloaded, since the cache entry is gone and getBalance
        // never touches disk (a documented, deliberate quirk — see economy/CLAUDE.md).
        assertEquals(0.0D, economy.getBalance(id));

        File file = new File(dataFolder, "players/" + id + ".yml");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline && !file.exists()) {
            Thread.sleep(20);
        }
        assertTrue(file.exists(), "unload must persist the removed player's data to disk");
        assertEquals(50.0D, YamlConfiguration.loadConfiguration(file).getDouble("balance"));
    }

    @Test
    void loadPlayerReadsBackPersistedValueAfterUnload() throws InterruptedException {
        UUID id = UUID.randomUUID();
        economy.setBalance(id, 200.0D);
        economy.setRank(id, 3);
        economy.unloadAndSavePlayer(id);

        // file.exists() flips true as soon as the async write opens/creates the file, which can
        // race ahead of config.save(file) actually flushing content — poll until the write has
        // landed in full (rank present with the expected value) rather than merely on file
        // existence, or loadPlayer below can read a still-empty file and cache stale defaults.
        File file = new File(dataFolder, "players/" + id + ".yml");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline
                && YamlConfiguration.loadConfiguration(file).getInt("rank", -1) != 3) {
            Thread.sleep(20);
        }

        economy.loadPlayer(id);

        assertEquals(200.0D, economy.getBalance(id));
        assertEquals(3, economy.getRank(id));
    }

    // -------------------------------------------------------------------------
    // Regression coverage: saveAll() now completes exceptionally on a write failure
    // (previously it could only ever succeed, since each write swallowed its own IOException
    // before this migration) — and the failure is still logged with the throwable attached.
    // -------------------------------------------------------------------------

    @Test
    void saveAllCompletesExceptionallyAndLogsWhenAPlayerFileIsBlocked() throws Exception {
        UUID id = UUID.randomUUID();
        economy.setBalance(id, 10.0D);

        // A directory at the exact target path makes config.save(file) throw IOException.
        File playersDir = new File(dataFolder, "players");
        assertTrue(playersDir.isDirectory() || playersDir.mkdirs());
        File blocked = new File(playersDir, id + ".yml");
        assertTrue(blocked.mkdirs());

        var future = economy.saveAll();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS),
                "saveAll() must complete exceptionally when a write fails");
        assertNotNull(ex.getCause());

        assertFalse(logRecords.isEmpty(), "the failure must still be logged");
        assertEquals(Level.WARNING, logRecords.getFirst().getLevel());
        assertNotNull(logRecords.getFirst().getThrown(),
                "the IOException must be attached to the log record, not just its message string");
    }
}
