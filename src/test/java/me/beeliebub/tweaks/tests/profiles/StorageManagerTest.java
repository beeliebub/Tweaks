package me.beeliebub.tweaks.tests.profiles;

import me.beeliebub.tweaks.utils.Point;
import me.beeliebub.tweaks.profiles.StorageManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageManagerTest {

    @TempDir
    File dataFolder;

    private StorageManager storage;
    private List<LogRecord> logRecords;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);

        Logger logger = Logger.getLogger("StorageManagerTest-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logRecords = new ArrayList<>();
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { logRecords.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        when(plugin.getLogger()).thenReturn(logger);

        storage = new StorageManager(plugin);
    }

    @AfterEach
    void drainAsyncWrites() {
        // StorageManager's writes go through YamlStore.writeAsync, which uses the common pool
        // with no explicit executor. awaitQuiescence blocks only until the pool is actually idle
        // (or the timeout elapses) rather than always waiting a fixed duration — this replaces a
        // blind Thread.sleep(200) that existed purely so @TempDir cleanup on Windows wouldn't race
        // a still-open file handle from a background write.
        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);
    }

    private static Point pt(String world, double x, double y, double z) {
        return new Point(world, x, y, z, 0f, 0f);
    }

    @Test
    void constructorCreatesHomesAndInventoriesDirectories() {
        assertTrue(new File(dataFolder, "homes").isDirectory());
        assertTrue(new File(dataFolder, "inventories").isDirectory());
    }

    @Test
    void getWarpReturnsEmptyForUnknownName() {
        assertTrue(storage.getWarp("missing").isEmpty());
    }

    @Test
    void setAndGetWarpRoundTrips() {
        Point p = pt("world", 1.0, 64.0, -1.0);
        storage.setWarp("Spawn", p);
        Optional<Point> got = storage.getWarp("SPAWN");
        assertTrue(got.isPresent());
        assertEquals(p, got.get());
    }

    @Test
    void delWarpRemovesEntry() {
        storage.setWarp("a", pt("world", 0, 0, 0));
        storage.delWarp("A");
        assertTrue(storage.getWarp("a").isEmpty());
    }

    @Test
    void getWarpsReflectsCurrentSet() {
        storage.setWarp("a", pt("world", 0, 0, 0));
        storage.setWarp("b", pt("world", 1, 1, 1));
        assertTrue(storage.getWarps().contains("a"));
        assertTrue(storage.getWarps().contains("b"));
    }

    @Test
    void getHomeReturnsEmptyForUnknownPlayer() {
        assertTrue(storage.getHome(UUID.randomUUID(), "any").isEmpty());
    }

    @Test
    void setAndGetHomeRoundTripsWithCaseInsensitiveName() {
        UUID uuid = UUID.randomUUID();
        Point p = pt("world", 10.0, 64.0, 10.0);
        storage.setHome(uuid, "Base", p);
        assertEquals(p, storage.getHome(uuid, "BASE").orElseThrow());
        assertEquals(p, storage.getHome(uuid, "base").orElseThrow());
    }

    @Test
    void getHomeCountReflectsHomesPerPlayer() {
        UUID uuid = UUID.randomUUID();
        assertEquals(0, storage.getHomeCount(uuid));
        storage.setHome(uuid, "a", pt("world", 0, 0, 0));
        storage.setHome(uuid, "b", pt("world", 0, 0, 0));
        assertEquals(2, storage.getHomeCount(uuid));
    }

    @Test
    void delHomeRemovesEntryAndCleansEmptyMap() {
        UUID uuid = UUID.randomUUID();
        storage.setHome(uuid, "a", pt("world", 0, 0, 0));
        storage.delHome(uuid, "A");
        assertEquals(0, storage.getHomeCount(uuid));
        assertTrue(storage.getHomes(uuid).isEmpty());
    }

    @Test
    void getHomesReturnsEmptySetForUnknownPlayer() {
        assertTrue(storage.getHomes(UUID.randomUUID()).isEmpty());
    }

    @Test
    void warpsArePersistedToYamlFile() throws InterruptedException {
        storage.setWarp("spawn", pt("world", 1.5, 64.0, -1.5));
        // Wait for async write
        File warpsFile = new File(dataFolder, "warps.yml");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline && !warpsFile.exists()) {
            Thread.sleep(20);
        }
        assertTrue(warpsFile.exists(), "warps.yml should be written by saveWarpsAsync");

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(warpsFile);
        assertEquals("world", cfg.getString("spawn.world"));
        assertEquals(1.5, cfg.getDouble("spawn.x"));
        assertEquals(64.0, cfg.getDouble("spawn.y"));
        assertEquals(-1.5, cfg.getDouble("spawn.z"));
    }

    @Test
    void cacheInventoryAndGetCachedInventoryRoundTrip() {
        UUID uuid = UUID.randomUUID();
        storage.cacheInventory(uuid, "survival", "BASE64-DATA");
        assertEquals("BASE64-DATA", storage.getCachedInventory(uuid, "survival"));
    }

    @Test
    void getCachedInventoryReturnsNullForUnknownPlayerOrProfile() {
        UUID uuid = UUID.randomUUID();
        assertNull(storage.getCachedInventory(uuid, "any"));
        storage.cacheInventory(uuid, "survival", "data");
        assertNull(storage.getCachedInventory(uuid, "creative"));
    }

    @Test
    void unloadAndSaveRemovesPlayerFromInMemoryCache() {
        UUID uuid = UUID.randomUUID();
        storage.cacheInventory(uuid, "survival", "data");
        storage.unloadAndSavePlayerInventoriesAsync(uuid);
        assertNull(storage.getCachedInventory(uuid, "survival"),
                "in-memory cache must be cleared on unload");
    }

    // -------------------------------------------------------------------------
    // Homes are persisted to disk (previously only warps had a genuine disk assertion)
    // -------------------------------------------------------------------------

    @Test
    void homesArePersistedToYamlFile() throws InterruptedException {
        UUID uuid = UUID.randomUUID();
        storage.setHome(uuid, "base", pt("world", 5.5, 70.0, -2.5));

        File homeFile = new File(new File(dataFolder, "homes"), uuid + ".yml");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline && !homeFile.exists()) {
            Thread.sleep(20);
        }
        assertTrue(homeFile.exists(), "homes/<uuid>.yml should be written by savePlayerHomesAsync");

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(homeFile);
        assertEquals("world", cfg.getString("base.world"));
        assertEquals(5.5, cfg.getDouble("base.x"));
        assertEquals(70.0, cfg.getDouble("base.y"));
        assertEquals(-2.5, cfg.getDouble("base.z"));
    }

    // -------------------------------------------------------------------------
    // Edge case 2: deleting a player's last home must delete the stale file, not just
    // leave it stale on disk (a naive migration could skip the write, or write an empty doc).
    // -------------------------------------------------------------------------

    @Test
    void deletingLastHomeDeletesTheHomeFileFromDisk() throws InterruptedException {
        UUID uuid = UUID.randomUUID();
        storage.setHome(uuid, "base", pt("world", 0, 64, 0));
        File homeFile = new File(new File(dataFolder, "homes"), uuid + ".yml");

        long createDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < createDeadline && !homeFile.exists()) {
            Thread.sleep(20);
        }
        assertTrue(homeFile.exists(), "precondition: the home file must exist before deletion");

        storage.delHome(uuid, "base");

        long deleteDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deleteDeadline && homeFile.exists()) {
            Thread.sleep(20);
        }
        assertFalse(homeFile.exists(),
                "deleting a player's last home must delete the stale file, or it would reappear on restart");
    }

    // -------------------------------------------------------------------------
    // Edge case 4: unload must write the detached snapshot to disk, not just clear the cache.
    // -------------------------------------------------------------------------

    @Test
    void unloadAndSavePlayerInventoriesAsyncWritesToDiskAfterCacheRemoval() throws InterruptedException {
        UUID uuid = UUID.randomUUID();
        storage.cacheInventory(uuid, "survival", "BASE64-DATA");

        storage.unloadAndSavePlayerInventoriesAsync(uuid);

        File invFile = new File(new File(dataFolder, "inventories"), uuid + ".yml");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline && !invFile.exists()) {
            Thread.sleep(20);
        }
        assertTrue(invFile.exists(),
                "unload must persist the detached snapshot to disk, not just clear the in-memory cache");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(invFile);
        assertEquals("BASE64-DATA", cfg.getString("survival"));
    }

    // -------------------------------------------------------------------------
    // Edge case 5: an empty/unknown-player save must never create or truncate a file.
    // -------------------------------------------------------------------------

    @Test
    void savePlayerInventoriesAsyncOnUnknownPlayerNeverCreatesFile() {
        UUID uuid = UUID.randomUUID();
        storage.savePlayerInventoriesAsync(uuid);

        ForkJoinPool.commonPool().awaitQuiescence(1, TimeUnit.SECONDS);
        File invFile = new File(new File(dataFolder, "inventories"), uuid + ".yml");
        assertFalse(invFile.exists(), "an empty/unknown player must never produce a file");
    }

    // -------------------------------------------------------------------------
    // Regression test for the fixed drift bug: savePlayerInventoriesAsync's IOException path
    // must log at WARNING with the throwable attached, the same as every other save path — this
    // fails against the pre-migration code, which logged only e.getMessage() as a bare string.
    // -------------------------------------------------------------------------

    @Test
    void savePlayerInventoriesAsyncLogsThrowableOnIOException() throws InterruptedException {
        UUID uuid = UUID.randomUUID();
        storage.cacheInventory(uuid, "survival", "data");

        // A directory at the exact target path makes config.save(file) throw IOException.
        File invDir = new File(dataFolder, "inventories");
        assertTrue(invDir.isDirectory() || invDir.mkdirs());
        File blocked = new File(invDir, uuid + ".yml");
        assertTrue(blocked.mkdirs());

        storage.savePlayerInventoriesAsync(uuid);
        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);

        assertFalse(logRecords.isEmpty(), "the save failure must be logged");
        LogRecord record = logRecords.getFirst();
        assertEquals(Level.WARNING, record.getLevel());
        assertNotNull(record.getThrown(),
                "the IOException must be attached to the log record, not just its message string");
    }
}
