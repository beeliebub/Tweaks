package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.commands.TPACommand;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.tests.MessageAssert;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TPACommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private ResourceHuntItems resourceHuntItems;
    private TPACommand tpaCommand;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        resourceHuntItems = mock(ResourceHuntItems.class);
        tpaCommand = new TPACommand(plugin, resourceHuntItems);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void tpaRequestSentSuccessfully() {
        PlayerMock sender = server.addPlayer("Sender");
        PlayerMock target = server.addPlayer("Target");
        sender.nextComponentMessage(); // Clear join message
        target.nextComponentMessage(); // Clear join message

        boolean result = tpaCommand.onCommand(sender, bukkitCmd, "tpa", new String[]{"Target"});

        assertTrue(result);
        
        // Check if target received the request message
        MessageAssert.assertMessageSent(sender, "TPA request sent to Target");
        MessageAssert.assertMessageSent(target, "Sender wants to teleport to you");
    }

    @Test
    void tpaAcceptTeleportsRequester() {
        PlayerMock sender = server.addPlayer("Sender");
        PlayerMock target = server.addPlayer("Target");
        sender.nextComponentMessage();
        target.nextComponentMessage();

        tpaCommand.onCommand(sender, bukkitCmd, "tpa", new String[]{"Target"});
        
        // Clear messages from TPA request
        sender.nextComponentMessage();
        target.nextComponentMessage();
        target.nextComponentMessage();

        boolean result = tpaCommand.onCommand(target, bukkitCmd, "tpaccept", new String[0]);

        assertTrue(result);
        // Teleport is async in TPACommand, so we need to run pending tasks.
        server.getScheduler().performOneTick();
    }

    @Test
    void tpaDenyNotifiesRequester() {
        PlayerMock sender = server.addPlayer("Sender");
        PlayerMock target = server.addPlayer("Target");
        sender.nextComponentMessage();
        target.nextComponentMessage();

        tpaCommand.onCommand(sender, bukkitCmd, "tpa", new String[]{"Target"});
        
        // Clear messages
        sender.nextComponentMessage();
        target.nextComponentMessage();
        target.nextComponentMessage();

        boolean result = tpaCommand.onCommand(target, bukkitCmd, "tpdeny", new String[0]);

        assertTrue(result);
        MessageAssert.assertMessageSent(target, "TPA request denied");
        MessageAssert.assertMessageSent(sender, "Target denied your TPA request");
    }

    @Test
    void tpaToSelfFails() {
        PlayerMock sender = server.addPlayer("Sender");
        sender.nextComponentMessage();

        boolean result = tpaCommand.onCommand(sender, bukkitCmd, "tpa", new String[]{"Sender"});

        assertTrue(result);
        MessageAssert.assertMessageSent(sender, "You can't send a TPA request to yourself");
    }

    @Test
    void tpahereRequestSentSuccessfully() {
        PlayerMock sender = server.addPlayer("Sender");
        PlayerMock target = server.addPlayer("Target");
        sender.nextComponentMessage();
        target.nextComponentMessage();

        boolean result = tpaCommand.onCommand(sender, bukkitCmd, "tpahere", new String[]{"Target"});

        assertTrue(result);
        MessageAssert.assertMessageSent(sender, "TPA request sent to Target");
        MessageAssert.assertMessageSent(target, "Sender wants you to teleport to them");
    }

    @Test
    void tpacceptWithNoRequestFails() {
        PlayerMock target = server.addPlayer("Target");
        target.nextComponentMessage();

        boolean result = tpaCommand.onCommand(target, bukkitCmd, "tpaccept", new String[0]);

        assertTrue(result);
        MessageAssert.assertMessageSent(target, "You have no pending TPA requests");
    }
}
