package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.Point;
import me.beeliebub.tweaks.commands.DelHomeCommand;
import me.beeliebub.tweaks.managers.StorageManager;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DelHomeCommandTest {

    private final StorageManager storage = mock(StorageManager.class);
    private final DelHomeCommand cmd = new DelHomeCommand(storage);
    private final Command bukkitCmd = mock(Command.class);

    @Test
    void rejectsConsoleSender() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        cmd.onCommand(console, bukkitCmd, "delhome", new String[]{"base"});
        verify(storage, never()).delHome(any(), anyString());
    }

    @Test
    void deletesNamedHomeForCallingPlayer() {
        UUID uuid = UUID.randomUUID();
        Player player = playerWithUuid(uuid);
        when(storage.getHome(uuid, "base"))
                .thenReturn(Optional.of(new Point("world", 0, 0, 0, 0f, 0f)));

        cmd.onCommand(player, bukkitCmd, "delhome", new String[]{"base"});
        verify(storage).delHome(uuid, "base");
    }

    @Test
    void deletesDefaultHomeWhenCalledWithNoArgs() {
        UUID uuid = UUID.randomUUID();
        Player player = playerWithUuid(uuid);
        when(storage.getHome(uuid, "default"))
                .thenReturn(Optional.of(new Point("world", 0, 0, 0, 0f, 0f)));

        cmd.onCommand(player, bukkitCmd, "delhome", new String[0]);
        verify(storage).delHome(uuid, "default");
    }

    @Test
    void warnsWhenHomeDoesNotExist() {
        UUID uuid = UUID.randomUUID();
        Player player = playerWithUuid(uuid);
        when(storage.getHome(uuid, "ghost")).thenReturn(Optional.empty());

        cmd.onCommand(player, bukkitCmd, "delhome", new String[]{"ghost"});
        verify(storage, never()).delHome(any(), anyString());
    }

    @Test
    void adminCanDeleteAnotherPlayersHome() {
        UUID adminUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        Player admin = playerWithUuid(adminUuid);
        when(admin.hasPermission(Permissions.ADMIN_DELHOME)).thenReturn(true);

        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(targetUuid);

        when(storage.getHome(targetUuid, "base"))
                .thenReturn(Optional.of(new Point("world", 0, 0, 0, 0f, 0f)));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(target);
            cmd.onCommand(admin, bukkitCmd, "delhome", new String[]{"Bob", "base"});
        }
        verify(storage).delHome(targetUuid, "base");
    }

    @Test
    void nonAdminTwoArgInvocationShowsUsage() {
        UUID uuid = UUID.randomUUID();
        Player player = playerWithUuid(uuid);
        when(player.hasPermission(Permissions.ADMIN_DELHOME)).thenReturn(false);

        cmd.onCommand(player, bukkitCmd, "delhome", new String[]{"Bob", "base"});
        verify(storage, never()).delHome(any(), anyString());
    }

    private Player playerWithUuid(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }
}
