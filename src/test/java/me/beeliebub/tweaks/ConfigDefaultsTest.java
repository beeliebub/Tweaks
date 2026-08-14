package me.beeliebub.tweaks;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Tweaks#reconcileConfigDefaults directly (package-private, intentionally not public -
 * see the root CLAUDE.md's Test Layout note).
 *
 * Bukkit's ConfigurationSection#get(path) already falls through to a defaults chain the moment
 * setDefaults() has been called once, regardless of copyDefaults or how many times reconciliation
 * runs afterward - so asserting against plugin.getConfig() alone would pass even if
 * reconcileConfigDefaults()'s body were a no-op, since MockBukkit.load() already called it once
 * during onEnable(). These tests instead edit the on-disk config.yml directly, force a fresh
 * in-memory reload via plugin.reloadConfig() (a new FileConfiguration with no copyDefaults set),
 * then read the file straight off disk after reconciliation - the only way to prove reconciliation
 * itself is responsible for persisting the restored value, not merely for making it gettable.
 */
class ConfigDefaultsTest {

    private ServerMock server;
    private Tweaks plugin;
    private File configFile;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Rewrites the live config.yml on disk directly, bypassing plugin.getConfig() entirely. */
    private void writeLiveFile(String path, Object value) {
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(configFile);
        onDisk.set(path, value);
        try {
            onDisk.save(configFile);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        plugin.reloadConfig();
    }

    private YamlConfiguration readLiveFile() {
        return YamlConfiguration.loadConfiguration(configFile);
    }

    @Test
    void missingKeyIsRestoredFromBundledDefault() {
        writeLiveFile("max_chunks", null);

        plugin.reconcileConfigDefaults();

        assertEquals(200, readLiveFile().getInt("max_chunks", -1));
    }

    @Test
    void adminModifiedValueIsNotClobbered() {
        writeLiveFile("max-homes", 42);

        plugin.reconcileConfigDefaults();

        assertEquals(42, readLiveFile().getInt("max-homes", -1));
    }

    @Test
    void explicitEmptyListIsNotResurrectedToBundledDefault() {
        writeLiveFile("fly-worlds", List.of());

        plugin.reconcileConfigDefaults();

        assertTrue(readLiveFile().getStringList("fly-worlds").isEmpty());
    }

    @Test
    void liveKeyAbsentFromBundleIsLeftUntouched() {
        writeLiveFile("some-admin-only-key-not-in-bundle", "keep-me");

        plugin.reconcileConfigDefaults();

        assertEquals("keep-me", readLiveFile().getString("some-admin-only-key-not-in-bundle"));
    }

    @Test
    void reconciliationIsIdempotentAcrossRepeatedBoots() {
        plugin.reconcileConfigDefaults();
        plugin.reconcileConfigDefaults();

        YamlConfiguration onDisk = readLiveFile();
        assertEquals(15, onDisk.getInt("max-homes", -1));
        assertEquals(200, onDisk.getInt("max_chunks", -1));
    }

    @Test
    void lotteryReseedAmountIsRestoredFromBundledDefault() {
        writeLiveFile("lottery.reseed-amount", null);
        writeLiveFile("lottery.pot-multiplier", null);

        plugin.reconcileConfigDefaults();

        assertEquals(10000, readLiveFile().getInt("lottery.reseed-amount", -1));
        assertEquals(0.6D, readLiveFile().getDouble("lottery.pot-multiplier", -1.0D));
    }

    @Test
    void customizedLotteryFallbackBaseSurvivesMultiplierReconciliation() {
        writeLiveFile("lottery.reseed-amount", 12_345L);
        writeLiveFile("lottery.pot-multiplier", null);

        plugin.reconcileConfigDefaults();

        assertEquals(12_345L, readLiveFile().getLong("lottery.reseed-amount", -1L));
        assertEquals(0.6D, readLiveFile().getDouble("lottery.pot-multiplier", -1.0D));
    }

    @Test
    void skyblockTemplateBlockBudgetIsRestoredFromBundledDefault() {
        writeLiveFile("skyblock.template-max-blocks", null);

        plugin.reconcileConfigDefaults();

        assertEquals(250000, readLiveFile().getInt("skyblock.template-max-blocks", -1));
    }

    @Test
    void discordBridgeDefaultsAreReconciled() {
        writeLiveFile("discord.webhook-name", null);
        writeLiveFile("discord.webhook-avatar-url", null);
        writeLiveFile("discord.betting-channel-id", null);
        writeLiveFile("discord.betting-enabled", null);

        plugin.reconcileConfigDefaults();

        YamlConfiguration config = readLiveFile();
        assertEquals("House", config.getString("discord.webhook-name"));
        assertEquals("", config.getString("discord.webhook-avatar-url"));
        assertEquals("", config.getString("discord.betting-channel-id"));
        assertTrue(config.getBoolean("discord.betting-enabled"));
    }
}
