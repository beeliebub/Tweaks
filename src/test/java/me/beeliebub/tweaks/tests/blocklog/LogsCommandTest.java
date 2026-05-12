package me.beeliebub.tweaks.tests.blocklog;

import me.beeliebub.tweaks.blocklog.ChestLogManager;
import me.beeliebub.tweaks.blocklog.LogsCommand;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.tests.MessageAssert;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogsCommandTest {

    private ServerMock server;
    private ChestLogManager manager;
    private LogsCommand command;
    private PlayerMock player;
    private org.bukkit.plugin.Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("tweaks");
        manager = mock(ChestLogManager.class);
        command = new LogsCommand(manager);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testCommandNoPermission() {
        command.onCommand(player, mock(Command.class), "logs", new String[0]);
        MessageAssert.assertMessageSent(player, "You don't have permission to use /logs.");
    }

    @Test
    void testToggleInspector() {
        player.addAttachment(plugin, Permissions.ADMIN_LOGS, true);
        command.onCommand(player, mock(Command.class), "logs", new String[0]);
        MessageAssert.assertMessageSent(player, "Inspector mode enabled.");
        
        command.onCommand(player, mock(Command.class), "logs", new String[0]);
        MessageAssert.assertMessageSent(player, "Inspector mode disabled.");
    }

    @Test
    void testInspectorClick() {
        player.addAttachment(plugin, Permissions.ADMIN_LOGS, true);
        command.onCommand(player, mock(Command.class), "logs", new String[0]);
        
        Block block = server.addSimpleWorld("test").getBlockAt(0, 0, 0);
        block.setType(Material.CHEST);
        when(manager.isLoggable(block)).thenReturn(true);
        when(manager.anchor(block)).thenReturn(block);
        when(manager.read(block)).thenReturn(Collections.emptyList());

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, null, block, org.bukkit.block.BlockFace.UP);
        command.onLeftClick(event);
        
        assertTrue(event.isCancelled());
        MessageAssert.assertMessageSent(player, "No log entries for this chest.");
    }
}
