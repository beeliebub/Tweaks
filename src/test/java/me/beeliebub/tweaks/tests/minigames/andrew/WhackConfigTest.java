package me.beeliebub.tweaks.tests.minigames.andrew;

import me.beeliebub.tweaks.minigames.andrew.WhackConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WhackConfigTest {

    @TempDir
    File dataFolder;

    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getResource("whack.yml")).thenReturn(null);
        doNothing().when(plugin).saveResource(eq("whack.yml"), anyBoolean());
    }

    private void writeWhackYml(String body) throws IOException {
        File f = new File(dataFolder, "whack.yml");
        java.nio.file.Files.writeString(f.toPath(), body);
    }

    @Test
    void loadAppliesAllDocumentedDefaultsWhenFileIsEmpty() throws IOException {
        writeWhackYml("");
        WhackConfig cfg = new WhackConfig(plugin);

        assertEquals(180, cfg.getRoundDuration());
        assertEquals(1, cfg.getDifficulty());
        assertEquals("andrewkm", cfg.getProfileName());
        assertEquals(60, cfg.getMannequinLifespan());
        assertEquals(60, cfg.getBaseSpawnInterval());
        assertEquals(10, cfg.getMinSpawnInterval());
        assertEquals(10, cfg.getMaxAlive());
        assertTrue(cfg.isShowActionbar());
        assertTrue(cfg.isAnnounceResults());
        assertEquals("", cfg.getFirstPlaceReward());
        assertEquals("", cfg.getSecondPlaceReward());
        assertEquals("", cfg.getThirdPlaceReward());
        assertEquals("ClarinetPhoenix", cfg.getSecondaryProfileName());
        assertEquals(3, cfg.getSecondaryPoints());
        assertEquals(10.0, cfg.getSecondaryChance());
        assertEquals("Videowiz92", cfg.getTernaryProfileName());
        assertEquals(2, cfg.getTernaryPoints());
        assertEquals(10.0, cfg.getTernaryChance());
    }

    @Test
    void loadHonoursOverriddenValues() throws IOException {
        writeWhackYml("""
                round-duration: 240
                difficulty: 7
                profile-name: customAndrew
                mannequin-lifespan: 100
                base-spawn-interval: 50
                min-spawn-interval: 15
                max-alive: 12
                show-actionbar: false
                announce-results: false
                rewards:
                  first: 'gold'
                  second: 'silver'
                  third: 'bronze'
                secondary-profile:
                  name: 'BonusGuy'
                  points: 5
                  chance: 25.5
                ternary-profile:
                  name: 'PenaltyGuy'
                  points: 3
                  chance: 7.25
                """);
        WhackConfig cfg = new WhackConfig(plugin);

        assertEquals(240, cfg.getRoundDuration());
        assertEquals(7, cfg.getDifficulty());
        assertEquals("customAndrew", cfg.getProfileName());
        assertEquals(100, cfg.getMannequinLifespan());
        assertEquals(50, cfg.getBaseSpawnInterval());
        assertEquals(15, cfg.getMinSpawnInterval());
        assertEquals(12, cfg.getMaxAlive());
        assertFalse(cfg.isShowActionbar());
        assertFalse(cfg.isAnnounceResults());
        assertEquals("gold", cfg.getFirstPlaceReward());
        assertEquals("silver", cfg.getSecondPlaceReward());
        assertEquals("bronze", cfg.getThirdPlaceReward());
        assertEquals("BonusGuy", cfg.getSecondaryProfileName());
        assertEquals(5, cfg.getSecondaryPoints());
        assertEquals(25.5, cfg.getSecondaryChance());
        assertEquals("PenaltyGuy", cfg.getTernaryProfileName());
        assertEquals(3, cfg.getTernaryPoints());
        assertEquals(7.25, cfg.getTernaryChance());
    }

    @Test
    void difficultyIsClampedIntoOneToTen() throws IOException {
        writeWhackYml("difficulty: 999\n");
        assertEquals(10, new WhackConfig(plugin).getDifficulty());

        writeWhackYml("difficulty: -50\n");
        assertEquals(1, new WhackConfig(plugin).getDifficulty());
    }

    @Test
    void setPlaceRewardPersistsToConfigAndUpdatesGetter() throws IOException {
        writeWhackYml("");
        WhackConfig cfg = new WhackConfig(plugin);

        cfg.setPlaceReward(1, "trophy_gold");
        cfg.setPlaceReward(2, "trophy_silver");
        cfg.setPlaceReward(3, "trophy_bronze");

        assertEquals("trophy_gold", cfg.getFirstPlaceReward());
        assertEquals("trophy_silver", cfg.getSecondPlaceReward());
        assertEquals("trophy_bronze", cfg.getThirdPlaceReward());

        // Verify written to disk
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(new File(dataFolder, "whack.yml"));
        assertEquals("trophy_gold", onDisk.getString("rewards.first"));
        assertEquals("trophy_silver", onDisk.getString("rewards.second"));
        assertEquals("trophy_bronze", onDisk.getString("rewards.third"));
    }

    @Test
    void setPlaceRewardIgnoresInvalidPlace() throws IOException {
        writeWhackYml("");
        WhackConfig cfg = new WhackConfig(plugin);
        cfg.setPlaceReward(99, "nope"); // out of range, no-op
        assertEquals("", cfg.getFirstPlaceReward());
        assertEquals("", cfg.getSecondPlaceReward());
        assertEquals("", cfg.getThirdPlaceReward());
    }

    @Test
    void loadArenaReturnsNullWhenNoArenaPersisted() throws IOException {
        writeWhackYml("");
        WhackConfig cfg = new WhackConfig(plugin);
        assertNull(cfg.loadArena());
    }
}
