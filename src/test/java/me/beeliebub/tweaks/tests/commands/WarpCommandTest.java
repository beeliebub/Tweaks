package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.Point;
import me.beeliebub.tweaks.commands.WarpCommand;
import me.beeliebub.tweaks.managers.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WarpCommandTest {

    private final StorageManager storage = mock(StorageManager.class);
    private final WarpCommand cmd = new WarpCommand(storage);
    private final Command bukkitCmd = mock(Command.class);

    @Test
    void rejectsConsoleSender() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        cmd.onCommand(console, bukkitCmd, "warp", new String[]{"spawn"});
        verify(storage, never()).getWarp(any());
    }

    @Test
    void rejectsMissingArgument() {
        Player player = mock(Player.class);
        cmd.onCommand(player, bukkitCmd, "warp", new String[0]);
        verify(storage, never()).getWarp(any());
    }

    @Test
    void warnsWhenWarpDoesNotExist() {
        Player player = mock(Player.class);
        when(storage.getWarp("ghost")).thenReturn(Optional.empty());
        cmd.onCommand(player, bukkitCmd, "warp", new String[]{"ghost"});
        verify(player, never()).teleportAsync(any());
    }

    @Test
    void warnsWhenWorldUnloaded() {
        Player player = mock(Player.class);
        when(storage.getWarp("spawn")).thenReturn(Optional.of(new Point("ghost-world", 0, 0, 0, 0f, 0f)));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("ghost-world")).thenReturn(null);
            cmd.onCommand(player, bukkitCmd, "warp", new String[]{"spawn"});
        }
        verify(player, never()).teleportAsync(any());
    }

    @Test
    void teleportsAsyncWhenWarpResolves() {
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(storage.getWarp("spawn")).thenReturn(Optional.of(new Point("world", 1.0, 2.0, 3.0, 4f, 5f)));
        when(player.teleportAsync(any())).thenReturn(CompletableFuture.completedFuture(true));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            cmd.onCommand(player, bukkitCmd, "warp", new String[]{"spawn"});
        }

        ArgumentCaptor<Location> locCaptor = ArgumentCaptor.forClass(Location.class);
        verify(player).teleportAsync(locCaptor.capture());
        Location dest = locCaptor.getValue();
        assertSame(world, dest.getWorld());
        assertEquals(1.0, dest.getX());
        assertEquals(2.0, dest.getY());
        assertEquals(3.0, dest.getZ());
    }

    @Test
    void tabCompleteFiltersWarpsByPrefix() {
        when(storage.getWarps()).thenReturn(Set.of("spawn", "shop"));
        var result = cmd.onTabComplete(mock(Player.class), bukkitCmd, "warp", new String[]{"sp"});
        assertTrue(result.contains("spawn"));
        assertFalse(result.contains("shop"));
    }
}
