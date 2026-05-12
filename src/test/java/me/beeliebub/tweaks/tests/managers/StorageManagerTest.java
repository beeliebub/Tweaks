package me.beeliebub.tweaks.tests.managers;

import me.beeliebub.tweaks.Point;
import me.beeliebub.tweaks.managers.StorageManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageManagerTest {

    @TempDir
    File dataFolder;

    private StorageManager storage;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        storage = new StorageManager(plugin);
    }

    @AfterEach
    void drainAsyncWrites() throws InterruptedException {
        // StorageManager fires CompletableFuture.runAsync on every mutation; on Windows the
        // background writes can hold the temp file open longer than the test, breaking @TempDir
        // cleanup. Give the common-pool a beat to drain.
        Thread.sleep(200);
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
}
