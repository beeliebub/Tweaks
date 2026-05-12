package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.commands.LoreCommand;
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

class LoreCommandTest {

    private ServerMock server;
    private LoreCommand loreCommand;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin("tweaks");
        loreCommand = new LoreCommand();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void addLoreRequiresPermission() {
        PlayerMock player = server.addPlayer();
        loreCommand.onCommand(player, bukkitCmd, "lore", new String[]{"add", "1", "test"});
        
        ItemStack held = player.getInventory().getItemInMainHand();
        assertFalse(held.hasItemMeta() && held.getItemMeta().hasLore());
    }

    @Test
    void addLoreWorksWithPermission() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(MockBukkit.getMock().getPluginManager().getPlugin("tweaks"), Permissions.ADMIN_ITEM_EDIT, true);
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        player.getInventory().setItemInMainHand(item);

        loreCommand.onCommand(player, bukkitCmd, "lore", new String[]{"add", "1", "Test Lore"});

        ItemStack held = player.getInventory().getItemInMainHand();
        assertTrue(held.hasItemMeta());
        assertTrue(held.getItemMeta().hasLore());
        assertEquals(1, held.getItemMeta().lore().size());
    }

    @Test
    void removeLoreWorks() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(MockBukkit.getMock().getPluginManager().getPlugin("tweaks"), Permissions.ADMIN_ITEM_EDIT, true);
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.lore(java.util.List.of(net.kyori.adventure.text.Component.text("To Remove")));
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);

        loreCommand.onCommand(player, bukkitCmd, "lore", new String[]{"remove", "1"});

        ItemStack held = player.getInventory().getItemInMainHand();
        assertFalse(held.hasItemMeta() && held.getItemMeta().hasLore());
    }
}
