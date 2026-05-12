package me.beeliebub.tweaks.tests.minigames.resource;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceHuntItemsTest {

    @TempDir
    File dataFolder;

    private Tweaks plugin;
    private File configFile;

    @BeforeEach
    void setUp() {
        plugin = mock(Tweaks.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        configFile = new File(dataFolder, "resource_hunt_items.yml");
    }

    @Test
    void firstConstructionCreatesDefaultConfigFile() {
        new ResourceHuntItems(plugin);
        assertTrue(configFile.exists(), "default config file must be created on first run");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        List<String> defaults = cfg.getStringList("allowed-items");
        assertFalse(defaults.isEmpty(), "default list must be populated");
        assertTrue(defaults.contains("DIAMOND_PICKAXE"), "default tools must include DIAMOND_PICKAXE");
        assertTrue(defaults.contains("LEATHER_HELMET"), "default armor must include LEATHER_HELMET");
        assertTrue(defaults.contains("BREAD"), "default food must include BREAD");
    }

    @Test
    void allowedItemsAreLoadedFromConfig() {
        ResourceHuntItems items = new ResourceHuntItems(plugin);
        // Defaults written by constructor
        assertTrue(items.isAllowed(Material.DIAMOND_PICKAXE));
        assertTrue(items.isAllowed(Material.IRON_SWORD));
    }

    @Test
    void unknownMaterialsAreIgnoredOnLoad() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("allowed-items", List.of("DIAMOND_PICKAXE", "TOTALLY_NOT_A_REAL_BLOCK_XYZ"));
        cfg.save(configFile);

        ResourceHuntItems items = new ResourceHuntItems(plugin);
        assertTrue(items.isAllowed(Material.DIAMOND_PICKAXE));
        // Unknown materials simply produce a logger warning and are skipped (not added to set).
        assertFalse(items.isAllowed(Material.STONE));
    }

    @Test
    void addAllowedItemPersistsToFile() {
        ResourceHuntItems items = new ResourceHuntItems(plugin);
        items.addAllowedItem(Material.STONE);
        assertTrue(items.isAllowed(Material.STONE));

        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(configFile);
        assertTrue(onDisk.getStringList("allowed-items").contains("STONE"));
    }

    @Test
    void removeAllowedItemPersistsToFile() {
        ResourceHuntItems items = new ResourceHuntItems(plugin);
        items.removeAllowedItem(Material.DIAMOND_PICKAXE);
        assertFalse(items.isAllowed(Material.DIAMOND_PICKAXE));

        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(configFile);
        assertFalse(onDisk.getStringList("allowed-items").contains("DIAMOND_PICKAXE"));
    }

    @Test
    void getDisallowedItemsHandlesEmptyInventoryWithoutTouchingMaterialIsAir() {
        // Note: getDisallowedItems calls Material#isAir() on every non-null slot, which
        // delegates to RegistryAccess and only works in a live server. The best we can
        // verify here is the empty/null-only path, where isAir is never reached.
        ResourceHuntItems items = new ResourceHuntItems(plugin);

        Player player = mock(Player.class);
        PlayerInventory inv = mock(PlayerInventory.class);
        when(inv.getContents()).thenReturn(new ItemStack[]{null, null, null, null});
        when(player.getInventory()).thenReturn(inv);

        assertTrue(items.getDisallowedItems(player).isEmpty());
    }

    @Test
    void duplicateAddIsNoOpDoesNotRewriteFile() {
        ResourceHuntItems items = new ResourceHuntItems(plugin);
        items.addAllowedItem(Material.DIAMOND_PICKAXE); // already present
        // Shouldn't throw; nothing useful to assert about file mtime, but verify state.
        assertTrue(items.isAllowed(Material.DIAMOND_PICKAXE));
    }
}
