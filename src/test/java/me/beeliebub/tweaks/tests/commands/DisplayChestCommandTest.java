package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.commands.DisplayChestCommand;
import me.beeliebub.tweaks.managers.DisplayChestManager;
import me.beeliebub.tweaks.tests.MessageAssert;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisplayChestCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private DisplayChestManager displayChestManager;
    private DisplayChestCommand displayChestCommand;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        displayChestManager = mock(DisplayChestManager.class);
        displayChestCommand = new DisplayChestCommand(displayChestManager);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void togglesSetupMode() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // Clear join message
        when(displayChestManager.toggleSetupMode(player.getUniqueId())).thenReturn(true);

        displayChestCommand.onCommand(player, bukkitCmd, "displaychest", new String[0]);

        verify(displayChestManager).toggleSetupMode(player.getUniqueId());
        MessageAssert.assertMessageSent(player, "setup mode ENABLED");

        when(displayChestManager.toggleSetupMode(player.getUniqueId())).thenReturn(false);
        displayChestCommand.onCommand(player, bukkitCmd, "displaychest", new String[0]);
        MessageAssert.assertMessageSent(player, "setup mode DISABLED");
    }

    @Test
    void togglesRemovalMode() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // Clear join message
        when(displayChestManager.toggleRemovalMode(player.getUniqueId())).thenReturn(true);

        displayChestCommand.onCommand(player, bukkitCmd, "displaychest", new String[]{"off"});

        verify(displayChestManager).toggleRemovalMode(player.getUniqueId());
        MessageAssert.assertMessageSent(player, "removal mode ENABLED");

        when(displayChestManager.toggleRemovalMode(player.getUniqueId())).thenReturn(false);
        displayChestCommand.onCommand(player, bukkitCmd, "displaychest", new String[]{"off"});
        MessageAssert.assertMessageSent(player, "removal mode DISABLED");
    }
}
