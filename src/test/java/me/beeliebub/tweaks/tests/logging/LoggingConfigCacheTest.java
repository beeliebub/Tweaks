package me.beeliebub.tweaks.tests.logging;

import me.beeliebub.tweaks.logging.LoggingConfigCache;
import me.beeliebub.tweaks.logging.LoggingPaths;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingConfigCacheTest {

    @Test
    void primeReadsEveryKnownPathAndDefaultsToOff() {
        YamlConfiguration config = new YamlConfiguration();
        config.set(LoggingPaths.ECONOMY_BALANCE_SET, true);

        LoggingConfigCache cache = new LoggingConfigCache();
        cache.prime(config);

        assertEquals(LoggingPaths.allPaths().size(), cache.size());
        assertTrue(cache.enabled(LoggingPaths.ECONOMY_BALANCE_SET));
        assertFalse(cache.enabled(LoggingPaths.ECONOMY_BALANCE_ADD));
    }

    @Test
    void writeThroughOnlyAcceptsLoggingPaths() {
        LoggingConfigCache cache = new LoggingConfigCache();

        cache.update(LoggingPaths.CORE_CONFIG_CHANGED, true);
        cache.update("unrelated.boolean", true);

        assertTrue(cache.enabled(LoggingPaths.CORE_CONFIG_CHANGED));
        assertFalse(cache.enabled("unrelated.boolean"));
        assertEquals(1, cache.size());
    }
}
