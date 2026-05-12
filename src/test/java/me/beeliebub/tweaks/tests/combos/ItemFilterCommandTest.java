package me.beeliebub.tweaks.tests.combos;

import me.beeliebub.tweaks.combos.ItemFilterCommand;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ItemFilterCommandTest {

    private ServerMock server;
    private JavaPlugin plugin;
    private ItemFilterCommand cmd;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("tweaks");
        cmd = new ItemFilterCommand(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private NamespacedKey enabledKey() { return new NamespacedKey(plugin, "itemfilter_enabled"); }
    private NamespacedKey modeKey()    { return new NamespacedKey(plugin, "itemfilter_mode"); }
    private NamespacedKey whitelistKey() { return new NamespacedKey(plugin, "itemfilter_whitelist"); }

    @Test
    void filterDefaultsToDisabledAndWhitelistMode() {
        PlayerMock player = server.addPlayer();
        // Disabled: any item should be allowed
        assertTrue(cmd.allowsPickup(player, new ItemStack(Material.STONE)));
    }

    @Test
    void toggleEnablesAndDisablesFilter() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"toggle"});
        assertEquals((byte) 1,
                player.getPersistentDataContainer().get(enabledKey(), PersistentDataType.BYTE));
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"toggle"});
        assertEquals((byte) 0,
                player.getPersistentDataContainer().get(enabledKey(), PersistentDataType.BYTE));
    }

    @Test
    void modeToggleAlternatesBetweenWhitelistAndBlacklist() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"mode"});
        assertEquals("blacklist",
                player.getPersistentDataContainer().get(modeKey(), PersistentDataType.STRING));
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"mode"});
        assertEquals("whitelist",
                player.getPersistentDataContainer().get(modeKey(), PersistentDataType.STRING));
    }

    @Test
    void addInsertsMaterialIntoActiveList() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"add", "stone"});
        List<String> list = player.getPersistentDataContainer()
                .get(whitelistKey(), PersistentDataType.LIST.strings());
        assertNotNull(list);
        assertTrue(list.contains("minecraft:stone"));
    }

    @Test
    void addRejectsUnknownMaterial() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"add", "totally_made_up"});
        List<String> list = player.getPersistentDataContainer()
                .get(whitelistKey(), PersistentDataType.LIST.strings());
        assertTrue(list == null || list.isEmpty(),
                "unknown material must not be added to the list");
    }

    @Test
    void removeStripsMaterialFromActiveList() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"add", "stone"});
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"remove", "stone"});
        List<String> list = player.getPersistentDataContainer()
                .get(whitelistKey(), PersistentDataType.LIST.strings());
        assertTrue(list == null || !list.contains("minecraft:stone"));
    }

    @Test
    void listClearWipesActiveList() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"add", "stone"});
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"add", "dirt"});
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"list", "clear"});
        List<String> list = player.getPersistentDataContainer()
                .get(whitelistKey(), PersistentDataType.LIST.strings());
        assertTrue(list == null || list.isEmpty());
    }

    @Test
    void allowsPickupRespectsWhitelistWhenEnabled() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"toggle"});            // enable
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"add", "stone"});      // whitelist stone

        assertTrue(cmd.allowsPickup(player, new ItemStack(Material.STONE)));
        assertFalse(cmd.allowsPickup(player, new ItemStack(Material.DIRT)));
    }

    @Test
    void allowsPickupRespectsBlacklistWhenEnabled() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"toggle"});            // enable
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"mode"});              // blacklist
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"add", "stone"});      // blacklist stone

        assertFalse(cmd.allowsPickup(player, new ItemStack(Material.STONE)));
        assertTrue(cmd.allowsPickup(player, new ItemStack(Material.DIRT)));
    }

    @Test
    void allowsPickupAlwaysAllowsAirAndNullItems() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"toggle"});            // enable
        // Empty whitelist + filter on would normally block stone, but air is always allowed.
        assertTrue(cmd.allowsPickup(player, null));
        assertTrue(cmd.allowsPickup(player, new ItemStack(Material.AIR)));
    }

    @Test
    void tabCompleteOffersTopLevelSubcommands() {
        PlayerMock player = server.addPlayer();
        List<String> result = cmd.onTabComplete(player, bukkitCmd, "if", new String[]{""});
        assertTrue(result.contains("toggle"));
        assertTrue(result.contains("mode"));
        assertTrue(result.contains("add"));
        assertTrue(result.contains("remove"));
        assertTrue(result.contains("list"));
    }

    @Test
    void tabCompleteForRemoveOnlySuggestsAlreadyAddedItems() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "if", new String[]{"add", "stone"});
        List<String> result = cmd.onTabComplete(player, bukkitCmd, "if", new String[]{"remove", ""});
        assertTrue(result.contains("stone"));
        assertFalse(result.contains("dirt"), "dirt not in list, must not appear");
    }
}
