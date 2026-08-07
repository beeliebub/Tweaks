package me.beeliebub.tweaks.deathinventory;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Lives in the production package (not the tests.* mirror) because it exercises
// DeathInventoryManager#maxAgeMillis, a package-private accessor - see the root CLAUDE.md's
// "Test Layout" section for this project's documented exception. Mirrors
// DeathInventoryManagerTest's Mockito-based JavaPlugin stubbing (no MockBukkit needed here,
// same as that test class).
class DeathInventoryRetentionConfigTest {

    @TempDir
    Path tempDataFolder;

    private DeathInventoryManager managerWith(YamlConfiguration cfg) {
        org.bukkit.plugin.java.JavaPlugin mockPlugin =
                Mockito.mock(org.bukkit.plugin.java.JavaPlugin.class);
        Mockito.when(mockPlugin.getDataFolder()).thenReturn(tempDataFolder.toFile());
        Mockito.when(mockPlugin.getConfig()).thenReturn(cfg);
        Mockito.when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("DeathInventoryRetentionConfigTest"));
        return new DeathInventoryManager(mockPlugin);
    }

    @Test
    void defaultsToThirtyDaysWhenKeyAbsent() {
        DeathInventoryManager manager = managerWith(new YamlConfiguration());
        assertEquals(30L * 24 * 60 * 60 * 1000, manager.maxAgeMillis());
    }

    @Test
    void liveEditTakesEffectWithoutReconstruction() {
        YamlConfiguration cfg = new YamlConfiguration();
        DeathInventoryManager manager = managerWith(cfg);

        cfg.set("deathinventory.retention-days", 1);
        assertEquals(24L * 60 * 60 * 1000, manager.maxAgeMillis());
    }

    @Test
    void zeroOrNegativeClampsToOneDay() {
        YamlConfiguration cfg = new YamlConfiguration();
        DeathInventoryManager manager = managerWith(cfg);

        cfg.set("deathinventory.retention-days", 0);
        assertEquals(24L * 60 * 60 * 1000, manager.maxAgeMillis());

        cfg.set("deathinventory.retention-days", -3);
        assertEquals(24L * 60 * 60 * 1000, manager.maxAgeMillis());
    }
}
