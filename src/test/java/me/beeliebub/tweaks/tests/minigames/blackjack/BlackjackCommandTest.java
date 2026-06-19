package me.beeliebub.tweaks.tests.minigames.blackjack;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackCommand;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.tests.MessageAssert;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Arg-parsing and permission-gating tests for {@link BlackjackCommand}.
 *
 * <p>{@link BlackjackListener} is mocked so these tests stay fast and do not
 * require block placement or entity spawning. The listener's public-API methods
 * ({@link BlackjackListener#beginTableSetup} and {@link BlackjackListener#beginTableRemoval})
 * are verified via Mockito interaction assertions.
 */
class BlackjackCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private BlackjackListener listenerMock;
    private BlackjackCommand command;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        listenerMock = mock(BlackjackListener.class);
        EconomyManager em = plugin.getEconomyManager();
        command = new BlackjackCommand(em, listenerMock);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -------------------------------------------------------------------------
    // Non-player sender rejected
    // -------------------------------------------------------------------------

    @Test
    void consoleSenderIsRejected() {
        ConsoleCommandSender console = server.getConsoleSender();

        boolean result = command.onCommand(console, bukkitCmd, "blackjack", new String[]{"createtable", "100"});

        assertTrue(result, "onCommand must return true (consumed) for console sender");
        // Listener must never be called.
        verifyNoInteractions(listenerMock);
    }

    // -------------------------------------------------------------------------
    // No args → usage message
    // -------------------------------------------------------------------------

    @Test
    void noArgsShowsUsage() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BLACKJACK_CREATETABLE, true);

        command.onCommand(player, bukkitCmd, "blackjack", new String[0]);

        MessageAssert.assertMessageSent(player, "createtable");
        verifyNoInteractions(listenerMock);
    }

    // -------------------------------------------------------------------------
    // Unknown subcommand → usage message
    // -------------------------------------------------------------------------

    @Test
    void unknownSubcommandShowsUsage() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BLACKJACK_CREATETABLE, true);

        command.onCommand(player, bukkitCmd, "blackjack", new String[]{"unknownsub"});

        MessageAssert.assertMessageSent(player, "createtable");
        verifyNoInteractions(listenerMock);
    }

    // -------------------------------------------------------------------------
    // createtable — permission gate
    // -------------------------------------------------------------------------

    @Test
    void createTableWithoutPermissionIsRejected() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BLACKJACK_CREATETABLE, false);

        command.onCommand(player, bukkitCmd, "blackjack", new String[]{"createtable", "100"});

        MessageAssert.assertMessageSent(player, "permission");
        verifyNoInteractions(listenerMock);
    }

    // -------------------------------------------------------------------------
    // createtable — arg validation
    // -------------------------------------------------------------------------

    @Test
    void createTableWithNoBetArgShowsUsage() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BLACKJACK_CREATETABLE, true);

        command.onCommand(player, bukkitCmd, "blackjack", new String[]{"createtable"});

        MessageAssert.assertMessageSent(player, "createtable <bet>");
        verifyNoInteractions(listenerMock);
    }

    @Test
    void createTableWithNonNumericBetIsRejected() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BLACKJACK_CREATETABLE, true);

        command.onCommand(player, bukkitCmd, "blackjack", new String[]{"createtable", "notanumber"});

        MessageAssert.assertMessageSent(player, "whole number");
        verifyNoInteractions(listenerMock);
    }

    @Test
    void createTableWithZeroBetIsRejected() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BLACKJACK_CREATETABLE, true);

        command.onCommand(player, bukkitCmd, "blackjack", new String[]{"createtable", "0"});

        MessageAssert.assertMessageSent(player, "greater than 0");
        verifyNoInteractions(listenerMock);
    }

    @Test
    void createTableWithNegativeBetIsRejected() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BLACKJACK_CREATETABLE, true);

        command.onCommand(player, bukkitCmd, "blackjack", new String[]{"createtable", "-50"});

        MessageAssert.assertMessageSent(player, "greater than 0");
        verifyNoInteractions(listenerMock);
    }

    @Test
    void createTableWithValidBetDelegatesToListener() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BLACKJACK_CREATETABLE, true);

        command.onCommand(player, bukkitCmd, "blackjack", new String[]{"createtable", "500"});

        // backColor is null when no hex arg is supplied.
        verify(listenerMock, times(1)).beginTableSetup(player, 500, null);
    }

    @Test
    void createTableWithHashPrefixedHexColorDelegatesToListener() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BLACKJACK_CREATETABLE, true);

        command.onCommand(player, bukkitCmd, "blackjack", new String[]{"createtable", "100", "#FF8800"});

        verify(listenerMock, times(1)).beginTableSetup(player, 100, 0xFF8800);
    }

    @Test
    void createTableWithBarePlainhexColorDelegatesToListener() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BLACKJACK_CREATETABLE, true);

        command.onCommand(player, bukkitCmd, "blackjack", new String[]{"createtable", "200", "0000FF"});

        verify(listenerMock, times(1)).beginTableSetup(player, 200, 0x0000FF);
    }

    @Test
    void createTableWithInvalidHexColorIsRejected() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BLACKJACK_CREATETABLE, true);

        command.onCommand(player, bukkitCmd, "blackjack", new String[]{"createtable", "100", "GGGGGG"});

        MessageAssert.assertMessageSent(player, "Invalid color");
        verifyNoInteractions(listenerMock);
    }

    // -------------------------------------------------------------------------
    // removetable — permission gate
    // -------------------------------------------------------------------------

    @Test
    void removeTableWithoutPermissionIsRejected() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BLACKJACK_REMOVETABLE, false);

        command.onCommand(player, bukkitCmd, "blackjack", new String[]{"removetable"});

        MessageAssert.assertMessageSent(player, "permission");
        verifyNoInteractions(listenerMock);
    }

    @Test
    void removeTableWithPermissionDelegatesToListener() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BLACKJACK_REMOVETABLE, true);

        command.onCommand(player, bukkitCmd, "blackjack", new String[]{"removetable"});

        verify(listenerMock, times(1)).beginTableRemoval(player);
    }
}
