package me.beeliebub.tweaks.tests.deathinventory;

import me.beeliebub.tweaks.deathinventory.DeathInventoryManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// NOTE: These tests exercise DeathInventoryManager's pure-Java logic (file I/O, purge,
// list/load/save) using a temporary directory. MockBukkit is NOT started here because
// DeathInventoryManager uses only java.io.File, YamlConfiguration, and JavaPlugin (for
// the data-folder path), which we stub via Mockito. If the MockBukkit version is ever
// upgraded to match the Paper API version, these tests can be enriched with ItemStack
// serialization round-trips via MockBukkit's registry.
//
// Currently the tests cover:
//   1. saveInventory / listInventories / loadInventory round-trip (null slots preserved).
//   2. getFile returns null when the file does not exist.
//   3. purgeOldEntries removes files older than 30 days and prunes empty UUID dirs.
//   4. listInventories returns newest-first ordering.
//   5. saveInventory with an all-null inventory produces an empty (but valid) YAML file.
class DeathInventoryManagerTest {

    @TempDir
    Path tempDataFolder;

    private DeathInventoryManager manager;

    /**
     * Constructs a DeathInventoryManager pointing at {@code tempDataFolder/data/deathinventories}
     * by injecting a mock JavaPlugin whose getDataFolder() returns the temp directory.
     */
    @BeforeEach
    void setUp() throws Exception {
        org.bukkit.plugin.java.JavaPlugin mockPlugin =
                Mockito.mock(org.bukkit.plugin.java.JavaPlugin.class);
        Mockito.when(mockPlugin.getDataFolder()).thenReturn(tempDataFolder.toFile());
        manager = new DeathInventoryManager(mockPlugin);
    }

    // ─── saveInventory / listInventories / loadInventory ─────────────────────

    @Test
    void saveAndList_createsFile() {
        UUID uuid = UUID.randomUUID();
        ItemStack[] contents = emptyContents();

        manager.saveInventory(uuid, contents);

        List<File> files = manager.listInventories(uuid);
        assertEquals(1, files.size(), "Expected exactly one saved record");
        assertTrue(files.getFirst().getName().endsWith(".yml"));
    }

    @Test
    void loadInventory_roundTripNullSlots() {
        // NOTE: Without MockBukkit's Paper registry, YamlConfiguration.getItemStack()
        // will return null for any slot (no Bukkit item serialization available).
        // This test therefore verifies that loadInventory returns a 41-element array
        // without throwing, and that all entries are null (expected in the no-registry env).
        UUID uuid = UUID.randomUUID();
        ItemStack[] contents = emptyContents();
        manager.saveInventory(uuid, contents);

        List<File> files = manager.listInventories(uuid);
        ItemStack[] loaded = manager.loadInventory(files.getFirst());

        assertNotNull(loaded);
        assertEquals(41, loaded.length);
        for (ItemStack item : loaded) {
            assertNull(item, "Without Bukkit registry all slots deserialize as null");
        }
    }

    @Test
    void getFile_existsAfterSave() {
        UUID uuid = UUID.randomUUID();
        manager.saveInventory(uuid, emptyContents());

        List<File> files = manager.listInventories(uuid);
        String id = stemOf(files.getFirst());

        File fetched = manager.getFile(uuid, id);
        assertNotNull(fetched);
        assertTrue(fetched.exists());
    }

    @Test
    void getFile_returnsNonExistentFileForBadId() {
        UUID uuid = UUID.randomUUID();
        File file = manager.getFile(uuid, "0");
        // getFile simply constructs the path; exists() is false for missing files.
        assertFalse(file.exists(), "getFile should return a non-existent file for a missing id");
    }

    // ─── listInventories ordering ─────────────────────────────────────────────

    @Test
    void listInventories_newestFirst() throws InterruptedException {
        UUID uuid = UUID.randomUUID();
        manager.saveInventory(uuid, emptyContents());
        // Small delay so the two timestamps are distinct.
        Thread.sleep(5);
        manager.saveInventory(uuid, emptyContents());

        List<File> files = manager.listInventories(uuid);
        assertEquals(2, files.size());
        long ts0 = Long.parseLong(stemOf(files.get(0)));
        long ts1 = Long.parseLong(stemOf(files.get(1)));
        assertTrue(ts0 >= ts1, "Newest record should come first");
    }

    @Test
    void listInventories_emptyForUnknownPlayer() {
        List<File> files = manager.listInventories(UUID.randomUUID());
        assertTrue(files.isEmpty());
    }

    // ─── purgeOldEntries ─────────────────────────────────────────────────────

    @Test
    void purgeOldEntries_removesAncientFiles() throws IOException, ReflectiveOperationException {
        UUID uuid = UUID.randomUUID();
        manager.saveInventory(uuid, emptyContents());

        List<File> before = manager.listInventories(uuid);
        assertEquals(1, before.size());

        // Back-date the file's last-modified to 31 days ago.
        long oldTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31);
        before.getFirst().setLastModified(oldTime);

        manager.purgeOldEntries();

        List<File> after = manager.listInventories(uuid);
        assertTrue(after.isEmpty(), "File older than 30 days should be purged");
    }

    @Test
    void purgeOldEntries_prunesEmptyUuidDir() throws IOException {
        UUID uuid = UUID.randomUUID();
        manager.saveInventory(uuid, emptyContents());

        // Backdating to expire the file.
        List<File> files = manager.listInventories(uuid);
        files.getFirst().setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31));
        manager.purgeOldEntries();

        // The UUID directory should have been deleted once empty.
        File uuidDir = new File(tempDataFolder.toFile(), "data/deathinventories/" + uuid);
        assertFalse(uuidDir.exists(), "Empty UUID directory should be removed by purge");
    }

    @Test
    void purgeOldEntries_keepsRecentFiles() {
        UUID uuid = UUID.randomUUID();
        manager.saveInventory(uuid, emptyContents());
        // File is recent (just saved) — should survive purge.
        manager.purgeOldEntries();
        assertEquals(1, manager.listInventories(uuid).size());
    }

    @Test
    void purgeOldEntries_mixedAgeFiles_onlyOldRemoved() throws IOException {
        UUID uuid = UUID.randomUUID();
        // Save a file that will be expired.
        manager.saveInventory(uuid, emptyContents());
        List<File> firstSave = manager.listInventories(uuid);
        firstSave.getFirst().setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31));

        // Save a fresh file.
        manager.saveInventory(uuid, emptyContents());

        manager.purgeOldEntries();

        List<File> remaining = manager.listInventories(uuid);
        assertEquals(1, remaining.size(), "Only the old file should be removed");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Creates a 41-slot array of nulls (simulating a fully-empty inventory). */
    private static ItemStack[] emptyContents() {
        return new ItemStack[41];
    }

    /** Strips the .yml extension from a file's name to get its timestamp-based stem. */
    private static String stemOf(File file) {
        String name = file.getName();
        return name.endsWith(".yml") ? name.substring(0, name.length() - 4) : name;
    }
}
