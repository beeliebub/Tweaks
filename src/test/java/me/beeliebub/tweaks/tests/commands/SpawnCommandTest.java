package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.Point;
import me.beeliebub.tweaks.commands.SpawnCommand;
import me.beeliebub.tweaks.managers.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpawnCommandTest {

    private final StorageManager storage = mock(StorageManager.class);
    private final SpawnCommand cmd = new SpawnCommand(storage);
    private final Command bukkitCmd = mock(Command.class);

    @Test
    void rejectsConsoleSender() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        cmd.onCommand(console, bukkitCmd, "spawn", new String[0]);
        verify(storage, never()).getWarp(any());
    }

    @Test
    void warnsWhenSpawnNotConfigured() {
        Player player = mock(Player.class);
        when(storage.getWarp("spawn")).thenReturn(Optional.empty());
        cmd.onCommand(player, bukkitCmd, "spawn", new String[0]);
        verify(player, never()).teleportAsync(any());
    }

    @Test
    void warnsWhenSpawnWorldUnloaded() {
        Player player = mock(Player.class);
        when(storage.getWarp("spawn"))
                .thenReturn(Optional.of(new Point("ghost-world", 0, 0, 0, 0f, 0f)));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("ghost-world")).thenReturn(null);
            cmd.onCommand(player, bukkitCmd, "spawn", new String[0]);
        }
        verify(player, never()).teleportAsync(any());
    }

    @Test
    void teleportsToSpawnWhenConfigured() {
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(storage.getWarp("spawn"))
                .thenReturn(Optional.of(new Point("world", 1.0, 2.0, 3.0, 4f, 5f)));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            cmd.onCommand(player, bukkitCmd, "spawn", new String[0]);
        }
        verify(player).teleportAsync(any(Location.class));
    }
}
