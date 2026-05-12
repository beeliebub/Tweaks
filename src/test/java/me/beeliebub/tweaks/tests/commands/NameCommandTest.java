package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.commands.NameCommand;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class NameCommandTest {

    private ServerMock server;
    private NameCommand nameCommand;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin("tweaks");
        nameCommand = new NameCommand();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void setNameRequiresPermission() {
        PlayerMock player = server.addPlayer();
        nameCommand.onCommand(player, bukkitCmd, "name", new String[]{"Test Name"});
        
        ItemStack held = player.getInventory().getItemInMainHand();
        assertFalse(held.hasItemMeta() && held.getItemMeta().hasDisplayName());
    }

    @Test
    void setNameWorksWithPermission() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(MockBukkit.getMock().getPluginManager().getPlugin("tweaks"), Permissions.ADMIN_ITEM_EDIT, true);
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        player.getInventory().setItemInMainHand(item);

        nameCommand.onCommand(player, bukkitCmd, "name", new String[]{"New", "Name"});

        ItemStack held = player.getInventory().getItemInMainHand();
        assertTrue(held.hasItemMeta());
        assertTrue(held.getItemMeta().hasDisplayName());
    }

    @Test
    void setNameOffClearsName() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(MockBukkit.getMock().getPluginManager().getPlugin("tweaks"), Permissions.ADMIN_ITEM_EDIT, true);
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Custom Name"));
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);

        nameCommand.onCommand(player, bukkitCmd, "name", new String[]{"off"});

        ItemStack held = player.getInventory().getItemInMainHand();
        assertFalse(held.hasItemMeta() && held.getItemMeta().hasDisplayName());
    }
}
