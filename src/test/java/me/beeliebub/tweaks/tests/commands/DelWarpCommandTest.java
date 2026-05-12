package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.commands.DelWarpCommand;
import me.beeliebub.tweaks.managers.StorageManager;
import me.beeliebub.tweaks.Point;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class DelWarpCommandTest {

    private final StorageManager storage = mock(StorageManager.class);
    private final DelWarpCommand cmd = new DelWarpCommand(storage);
    private final Command bukkitCmd = mock(Command.class);

    @Test
    void rejectsSenderWithoutAdminDelwarpPermission() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(Permissions.ADMIN_DELWARP)).thenReturn(false);
        cmd.onCommand(sender, bukkitCmd, "delwarp", new String[]{"spawn"});
        verify(storage, never()).delWarp(any());
    }

    @Test
    void rejectsMissingArgument() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(Permissions.ADMIN_DELWARP)).thenReturn(true);
        cmd.onCommand(sender, bukkitCmd, "delwarp", new String[0]);
        verify(storage, never()).delWarp(any());
        verify(sender).sendMessage(argThat((net.kyori.adventure.text.Component c) -> c != null));
    }

    @Test
    void warnsWhenWarpDoesNotExist() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(Permissions.ADMIN_DELWARP)).thenReturn(true);
        when(storage.getWarp("ghost")).thenReturn(Optional.empty());
        cmd.onCommand(sender, bukkitCmd, "delwarp", new String[]{"ghost"});
        verify(storage, never()).delWarp(any());
    }

    @Test
    void deletesExistingWarp() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(Permissions.ADMIN_DELWARP)).thenReturn(true);
        when(storage.getWarp("spawn"))
                .thenReturn(Optional.of(new Point("world", 0, 0, 0, 0f, 0f)));
        cmd.onCommand(sender, bukkitCmd, "delwarp", new String[]{"spawn"});
        verify(storage).delWarp("spawn");
    }

    @Test
    void tabCompleteWithNoArgsReturnsEmpty() {
        CommandSender sender = mock(CommandSender.class);
        List<String> result = cmd.onTabComplete(sender, bukkitCmd, "delwarp", new String[0]);
        assertTrue(result == null || result.isEmpty());
    }

    @Test
    void tabCompleteFirstArgFiltersWarpsByPrefix() {
        when(storage.getWarps()).thenReturn(Set.of("spawn", "shop", "shrine"));
        List<String> result = cmd.onTabComplete(mock(CommandSender.class), bukkitCmd, "delwarp", new String[]{"sh"});
        assertTrue(result.contains("shop"));
        assertTrue(result.contains("shrine"));
        assertFalse(result.contains("spawn"));
    }
}
