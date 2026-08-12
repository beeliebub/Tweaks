package me.beeliebub.tweaks.tests.skyblock.island;

import me.beeliebub.tweaks.skyblock.SkyblockConfig;
import me.beeliebub.tweaks.skyblock.island.IslandStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IslandStoreSchemaTest {
    @Test
    void missingSlotFailsClosedDuringStartupLoad(@TempDir Path directory) throws IOException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        Path file = directory.resolve("skyblock/islands/missing.yml");
        Files.createDirectories(file.getParent());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("owner", UUID.randomUUID().toString());
        yaml.save(file.toFile());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new IslandStore(plugin, mock(SkyblockConfig.class)).loadAll());
        assertTrue(error.getMessage().contains("missing.yml"));
        assertTrue(error.getCause().getMessage().contains("slot is missing"));
    }
}
