package me.beeliebub.tweaks.tests.core;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.ConfigCommand;
import me.beeliebub.tweaks.core.config.ConfigRegistry;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import me.beeliebub.tweaks.tests.MessageAssert;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private ResourceHuntItems resourceHuntItems;
    private ConfigCommand configCommand;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        resourceHuntItems = mock(ResourceHuntItems.class);
        configCommand = new ConfigCommand(plugin, resourceHuntItems, new WorldProfileTable(plugin));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void requiresPermission() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // Clear join message
        player.setOp(false);
        // Assuming non-op doesn't have the permission by default

        configCommand.onCommand(player, bukkitCmd, "config", new String[]{"max_homes", "5"});
        MessageAssert.assertMessageSent(player, "You do not have permission");
    }

    @Test
    void updatesMaxHomes() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // Clear join message
        player.addAttachment(plugin, Permissions.ADMIN_CONFIG, true);

        configCommand.onCommand(player, bukkitCmd, "config", new String[]{"max_homes", "10"});

        assertEquals(10, plugin.getConfig().getInt("max-homes"));
        MessageAssert.assertMessageSent(player, "Max homes has been updated live to 10");
    }

    @Test
    void updatesEggDropChance() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // Clear join message
        player.addAttachment(plugin, Permissions.ADMIN_CONFIG, true);

        configCommand.onCommand(player, bukkitCmd, "config", new String[]{"egg_collector_drop_chance", "50.5"});

        assertEquals(50.5, plugin.getConfig().getDouble("egg-collector-drop-chance"));
        MessageAssert.assertMessageSent(player, "Egg Collector drop chance has been updated live to 50.5%");
    }

    @Test
    void handlesMobToggle() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // Clear join message
        player.addAttachment(plugin, Permissions.ADMIN_CONFIG, true);

        // Test disabling a mob (e.g., zombie)
        configCommand.onCommand(player, bukkitCmd, "config", new String[]{"eggdrop", "disable", "zombie"});
        
        List<String> disabled = plugin.getConfig().getStringList("egg-collector-disabled-mobs");
        assertTrue(disabled.contains("zombie"));
        MessageAssert.assertMessageSent(player, "DISABLED");

        // Test enabling it back
        configCommand.onCommand(player, bukkitCmd, "config", new String[]{"eggdrop", "enable", "zombie"});
        disabled = plugin.getConfig().getStringList("egg-collector-disabled-mobs");
        assertFalse(disabled.contains("zombie"));
        MessageAssert.assertMessageSent(player, "ENABLED");
    }

    @Test
    void updatesResourceItems() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // Clear join message
        player.addAttachment(plugin, Permissions.ADMIN_CONFIG, true);

        configCommand.onCommand(player, bukkitCmd, "config", new String[]{"resourceitems", "add", "diamond_ore"});

        verify(resourceHuntItems).addAllowedItem(Material.DIAMOND_ORE);
        MessageAssert.assertMessageSent(player, "Added 'diamond_ore'");

        configCommand.onCommand(player, bukkitCmd, "config", new String[]{"resourceitems", "remove", "diamond_ore"});
        verify(resourceHuntItems).removeAllowedItem(Material.DIAMOND_ORE);
        MessageAssert.assertMessageSent(player, "Removed 'diamond_ore'");
    }

    // ------------------------------------------------------------ world-profiles CLI dispatch

    @Test
    void worldProfilesAddRequiresAllFourArgs() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage();
        player.addAttachment(plugin, Permissions.ADMIN_CONFIG, true);

        configCommand.onCommand(player, bukkitCmd, "config", new String[]{"world-profiles", "add", "jass:test"});
        MessageAssert.assertMessageSent(player, "Usage:");
    }

    @Test
    void worldProfilesAddThenListThenRemove() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage();
        player.addAttachment(plugin, Permissions.ADMIN_CONFIG, true);

        configCommand.onCommand(player, bukkitCmd, "config",
                new String[]{"world-profiles", "add", "jass:test", "testprofile", "Test", "AQUA"});
        MessageAssert.assertMessageSent(player, "Added 'jass:test'");

        configCommand.onCommand(player, bukkitCmd, "config", new String[]{"world-profiles", "list"});
        MessageAssert.assertMessageSent(player, "jass:test");

        configCommand.onCommand(player, bukkitCmd, "config", new String[]{"world-profiles", "remove", "jass:test"});
        MessageAssert.assertMessageSent(player, "Removed 'jass:test'");
    }

    @Test
    void worldProfilesRemoveUnknownKeyIsInvalid() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage();
        player.addAttachment(plugin, Permissions.ADMIN_CONFIG, true);

        configCommand.onCommand(player, bukkitCmd, "config", new String[]{"world-profiles", "remove", "jass:doesnotexist"});
        MessageAssert.assertMessageSent(player, "not mapped");
    }

    @Test
    void loggingListFilterPrintsOnlyLoggingCategories() {
        PlayerMock player = server.addPlayer();
        while (player.nextComponentMessage() != null) { /* drain join output */ }
        player.addAttachment(plugin, Permissions.ADMIN_CONFIG, true);

        configCommand.onCommand(player, bukkitCmd, "config", new String[]{"list", "logging"});

        StringBuilder output = new StringBuilder();
        net.kyori.adventure.text.Component message;
        while ((message = player.nextComponentMessage()) != null) {
            output.append(PlainTextComponentSerializer.plainText().serialize(message)).append('\n');
        }
        String text = output.toString();
        List<String> headers = text.lines().filter(line -> line.startsWith("--- ")).toList();
        assertEquals(ConfigRegistry.loggingCategories().size(), headers.size());
        assertEquals(ConfigRegistry.loggingCategories().stream()
                .map(category -> "--- " + category.displayName() + " ---").toList(), headers);
        assertFalse(text.contains("--- General ---"));
    }

    @Test
    void unfilteredListPreservesTheCompleteRegistryOrder() {
        PlayerMock player = server.addPlayer();
        while (player.nextComponentMessage() != null) { /* drain join output */ }
        player.addAttachment(plugin, Permissions.ADMIN_CONFIG, true);

        configCommand.onCommand(player, bukkitCmd, "config", new String[]{"list"});

        List<String> headers = new java.util.ArrayList<>();
        net.kyori.adventure.text.Component message;
        while ((message = player.nextComponentMessage()) != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(message);
            if (plain.startsWith("--- ")) headers.add(plain);
        }
        assertEquals(ConfigRegistry.categories().stream()
                .map(category -> "--- " + category.displayName() + " ---").toList(), headers);
    }

    @Test
    void loggingListFilterIsSuggested() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage();
        player.addAttachment(plugin, Permissions.ADMIN_CONFIG, true);

        assertTrue(configCommand.onTabComplete(player, bukkitCmd, "config",
                new String[]{"list", "logg"}).contains("logging"));
    }
}
