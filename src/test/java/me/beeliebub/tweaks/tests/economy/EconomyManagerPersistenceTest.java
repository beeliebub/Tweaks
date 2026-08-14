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
        economy.setBalance(id, 123L);
        assertEquals(123L, economy.getBalance(id));
    }

    @Test
    void saveAllFlushesToDisk() throws Exception {
        UUID id = UUID.randomUUID();
        economy.setBalance(id, 777L);

        economy.saveAll().get(5, TimeUnit.SECONDS);

        File file = new File(dataFolder, "players/" + id + ".yml");
        assertTrue(file.exists());
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(file);
        assertEquals(777L, onDisk.getLong("balance"));
    }

    @Test
    void unloadAndSavePlayerPersistsToDiskAndClearsCache() throws InterruptedException {
        UUID id = UUID.randomUUID();
        economy.setBalance(id, 50L);

        economy.unloadAndSavePlayer(id);

        // Balance reads default to 0.0 once unloaded, since the cache entry is gone and getBalance
        // never touches disk (a documented, deliberate quirk — see economy/CLAUDE.md).
        assertEquals(0L, economy.getBalance(id));

        File file = new File(dataFolder, "players/" + id + ".yml");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline && !file.exists()) {
            Thread.sleep(20);
        }
        assertTrue(file.exists(), "unload must persist the removed player's data to disk");
        assertEquals(50L, YamlConfiguration.loadConfiguration(file).getLong("balance"));
    }

    @Test
    void loadPlayerReadsBackPersistedValueAfterUnload() throws InterruptedException {
        UUID id = UUID.randomUUID();
        economy.setBalance(id, 200L);
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

        assertEquals(200L, economy.getBalance(id));
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
        economy.setBalance(id, 10L);

        // A directory at the exact target path makes config.save(file) throw IOException.
        File playersDir = new File(dataFolder, "players");
        assertTrue(playersDir.isDirectory() || playersDir.mkdirs());
        File blocked = new File(playersDir, id + ".yml");
        economy.saveAll().get(5, TimeUnit.SECONDS);
        if (blocked.exists()) assertTrue(blocked.delete());
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

    @Test
    void fractionalBalanceFloorsAndLoadPlayerRewritesItAsAnInteger() throws Exception {
        UUID id = UUID.randomUUID();
        writeBalance(id, 110.00000000000001D);

        economy.loadPlayer(id);
        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);

        File file = new File(dataFolder, "players/" + id + ".yml");
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(file);
        assertEquals(110L, economy.getBalance(id));
        assertEquals(110L, onDisk.getLong("balance"));
        assertEquals("110", onDisk.getString("balance"));
    }

    @Test
    void nonFiniteAndOverCeilingBalancesClampAndLogTheOriginalValue() throws Exception {
        UUID nanId = UUID.randomUUID();
        writeBalance(nanId, Double.NaN);
        economy.loadPlayer(nanId);

        UUID highId = UUID.randomUUID();
        double overCeiling = Math.nextUp((double) EconomyManager.MAX_BALANCE);
        writeBalance(highId, overCeiling);
        economy.loadPlayer(highId);
        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);

        assertEquals(0L, economy.getBalance(nanId));
        assertEquals(EconomyManager.MAX_BALANCE, economy.getBalance(highId));
        assertEquals(1L, logRecords.stream().filter(record -> record.getLevel() == Level.SEVERE
                && record.getMessage().contains(nanId.toString())
                && record.getMessage().contains("NaN")).count());
        assertEquals(1L, logRecords.stream().filter(record -> record.getLevel() == Level.SEVERE
                && record.getMessage().contains(highId.toString())
                && record.getMessage().contains(Double.toString(overCeiling))).count());

        assertEquals(0L, YamlConfiguration.loadConfiguration(
                new File(dataFolder, "players/" + nanId + ".yml")).getLong("balance"));
        assertEquals(EconomyManager.MAX_BALANCE, YamlConfiguration.loadConfiguration(
                new File(dataFolder, "players/" + highId + ".yml")).getLong("balance"));
    }

    @Test
    void nonNumericBalanceFailsClosedWithoutOverwritingTheOriginalFile() throws Exception {
        UUID id = UUID.randomUUID();
        File file = new File(dataFolder, "players/" + id + ".yml");
        file.getParentFile().mkdirs();
        YamlConfiguration seed = new YamlConfiguration();
        seed.set("balance", "not-a-number");
        seed.save(file);

        economy.loadPlayer(id);

        assertEquals(0L, economy.getBalance(id));
        assertEquals(me.beeliebub.tweaks.economy.BalanceMutationResult.REJECTED_UNREPRESENTABLE,
                economy.setBalance(id, 100L));
        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);
        assertEquals("not-a-number", YamlConfiguration.loadConfiguration(file).getString("balance"));
    }

    private void writeBalance(UUID id, double balance) throws Exception {
        File file = new File(dataFolder, "players/" + id + ".yml");
        file.getParentFile().mkdirs();
        YamlConfiguration seed = new YamlConfiguration();
        seed.set("balance", balance);
        seed.save(file);
    }
}
