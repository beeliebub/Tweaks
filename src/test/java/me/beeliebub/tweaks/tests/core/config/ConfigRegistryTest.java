package me.beeliebub.tweaks.tests.core.config;

import me.beeliebub.tweaks.core.config.ConfigCategory;
import me.beeliebub.tweaks.core.config.ConfigRegistry;
import me.beeliebub.tweaks.core.config.ConfigSetting;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registry invariants that would otherwise only surface at runtime: an empty category is a dead
 * button in the GUI, a duplicate path/alias means one setting silently shadows another, and a
 * generic (non-sentinel) path that doesn't exist in the bundled config.yml means /tconfig would
 * write a key that reconcileConfigDefaults() never bundles a default for.
 */
class ConfigRegistryTest {

    private static final Set<String> SENTINEL_PATHS = Set.of(
            ConfigRegistry.EGGDROP_SENTINEL_PATH,
            ConfigRegistry.SPAWNEREGG_SENTINEL_PATH,
            ConfigRegistry.RESOURCEITEMS_SENTINEL_PATH);

    @Test
    void noCategoryIsEmpty() {
        for (ConfigCategory category : ConfigRegistry.categories()) {
            assertFalse(category.settings().isEmpty(), "category '" + category.key() + "' has no settings");
        }
    }

    @Test
    void noDuplicatePaths() {
        Set<String> seen = new HashSet<>();
        for (ConfigCategory category : ConfigRegistry.categories()) {
            for (ConfigSetting setting : category.settings()) {
                assertTrue(seen.add(setting.path().toLowerCase()), "duplicate path: " + setting.path());
            }
        }
    }

    @Test
    void noDuplicateCliAliases() {
        Set<String> seen = new HashSet<>();
        for (ConfigCategory category : ConfigRegistry.categories()) {
            for (ConfigSetting setting : category.settings()) {
                for (String alias : setting.cliAliases()) {
                    assertTrue(seen.add(alias.toLowerCase()), "duplicate CLI alias: " + alias);
                }
            }
        }
    }

    @Test
    void everyNonSentinelPathExistsInBundledConfig() throws Exception {
        YamlConfiguration bundled = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in, "bundled config.yml not found on the test classpath");
            bundled.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        for (ConfigCategory category : ConfigRegistry.categories()) {
            for (ConfigSetting setting : category.settings()) {
                if (SENTINEL_PATHS.contains(setting.path())) continue;
                assertTrue(bundled.contains(setting.path()),
                        "registry path '" + setting.path() + "' is missing from the bundled config.yml");
            }
        }
    }

    @Test
    void byCliAliasAndByPathResolveEveryRegisteredSetting() {
        for (ConfigCategory category : ConfigRegistry.categories()) {
            for (ConfigSetting setting : category.settings()) {
                assertTrue(ConfigRegistry.byPath(setting.path()).isPresent(),
                        "byPath could not resolve '" + setting.path() + "'");
                for (String alias : setting.cliAliases()) {
                    assertTrue(ConfigRegistry.byCliAlias(alias).isPresent(),
                            "byCliAlias could not resolve '" + alias + "'");
                }
            }
        }
    }

    @Test
    void discordSettlementGroupWindowAllowsThirtySecondsButNotMore() {
        ConfigSetting setting = ConfigRegistry.byPath("discord.group-window-seconds").orElseThrow();

        assertEquals(1.0, setting.min());
        assertEquals(30.0, setting.max());
    }

    @Test
    void discordBridgeSettingsAreRegistered() {
        assertTrue(ConfigRegistry.byPath("discord.webhook-name").isPresent());
        assertTrue(ConfigRegistry.byPath("discord.webhook-avatar-url").isPresent());
        assertTrue(ConfigRegistry.byPath("discord.betting-channel-id").isPresent());
        assertTrue(ConfigRegistry.byPath("discord.betting-enabled").isPresent());
    }

    @Test
    void menuGroupsPreserveTheFullRegistryOrder() {
        List<ConfigCategory> main = ConfigRegistry.mainMenuCategories();
        List<ConfigCategory> logging = ConfigRegistry.loggingCategories();
        assertEquals(16, main.size(), "the main menu must retain all sixteen registered categories");
        assertEquals(20, logging.size(), "logging categories must paginate as twenty categories over two pages");
        assertFalse(main.stream().anyMatch(category -> category.group() == ConfigCategory.Group.LOGGING));
        assertTrue(main.stream().allMatch(category -> category.group() == ConfigCategory.Group.MAIN));
        assertTrue(logging.stream().allMatch(category -> category.group() == ConfigCategory.Group.LOGGING));
        assertFalse(logging.isEmpty());

        List<ConfigCategory> recombined = new ArrayList<>(main);
        recombined.addAll(logging);
        assertEquals(ConfigRegistry.categories(), recombined);
    }
}
