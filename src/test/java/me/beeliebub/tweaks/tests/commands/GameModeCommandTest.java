package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.commands.GameModeCommand;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameModeCommandTest {

    private final GameModeCommand cmd = new GameModeCommand();

    private Command commandNamed(String name) {
        Command c = mock(Command.class);
        when(c.getName()).thenReturn(name);
        return c;
    }

    @Test
    void rejectsSenderWithoutAdminGamemodePermission() {
        Player player = mock(Player.class);
        when(player.hasPermission(Permissions.ADMIN_GAMEMODE)).thenReturn(false);
        boolean handled = cmd.onCommand(player, commandNamed("survival"), "survival", new String[0]);
        assertTrue(handled);
        verify(player, never()).setGameMode(any());
    }

    @Test
    void rejectsConsoleSender() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(console.hasPermission(Permissions.ADMIN_GAMEMODE)).thenReturn(true);
        cmd.onCommand(console, commandNamed("creative"), "creative", new String[0]);
        // Should send a message but never attempt a setGameMode (Console isn't a Player).
        verify(console, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void survivalAliasSetsSurvivalMode() {
        Player player = mock(Player.class);
        when(player.hasPermission(Permissions.ADMIN_GAMEMODE)).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        cmd.onCommand(player, commandNamed("survival"), "survival", new String[0]);
        verify(player).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void creativeAliasSetsCreativeMode() {
        Player player = mock(Player.class);
        when(player.hasPermission(Permissions.ADMIN_GAMEMODE)).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        cmd.onCommand(player, commandNamed("creative"), "creative", new String[0]);
        verify(player).setGameMode(GameMode.CREATIVE);
    }

    @Test
    void noOpsWhenAlreadyInRequestedMode() {
        Player player = mock(Player.class);
        when(player.hasPermission(Permissions.ADMIN_GAMEMODE)).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        cmd.onCommand(player, commandNamed("survival"), "survival", new String[0]);
        verify(player, never()).setGameMode(any());
    }

    @Test
    void unknownAliasReturnsFalseToTriggerUsageMessage() {
        Player player = mock(Player.class);
        when(player.hasPermission(Permissions.ADMIN_GAMEMODE)).thenReturn(true);
        boolean handled = cmd.onCommand(player, commandNamed("adventure"), "adventure", new String[0]);
        assertFalse(handled, "unknown alias must return false so Bukkit emits the usage hint");
        verify(player, never()).setGameMode(any());
    }

    @Test
    void aliasMatchingIsCaseInsensitive() {
        Player player = mock(Player.class);
        when(player.hasPermission(Permissions.ADMIN_GAMEMODE)).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        cmd.onCommand(player, commandNamed("CREATIVE"), "CREATIVE", new String[0]);
        verify(player).setGameMode(GameMode.CREATIVE);
    }
}
