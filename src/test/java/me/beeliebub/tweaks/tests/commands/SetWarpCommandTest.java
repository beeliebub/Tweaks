package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.Point;
import me.beeliebub.tweaks.commands.SetWarpCommand;
import me.beeliebub.tweaks.managers.StorageManager;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SetWarpCommandTest {

    private final StorageManager storage = mock(StorageManager.class);
    private final SetWarpCommand cmd = new SetWarpCommand(storage);
    private final Command bukkitCmd = mock(Command.class);

    @Test
    void rejectsConsoleSender() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        cmd.onCommand(console, bukkitCmd, "setwarp", new String[]{"spawn"});
        verify(storage, never()).setWarp(any(), any());
    }

    @Test
    void rejectsPlayerWithoutAdminSetwarpPermission() {
        Player player = playerWithLocation();
        when(player.hasPermission(Permissions.ADMIN_SETWARP)).thenReturn(false);
        cmd.onCommand(player, bukkitCmd, "setwarp", new String[]{"spawn"});
        verify(storage, never()).setWarp(any(), any());
    }

    @Test
    void rejectsMissingArgument() {
        Player player = playerWithLocation();
        when(player.hasPermission(Permissions.ADMIN_SETWARP)).thenReturn(true);
        cmd.onCommand(player, bukkitCmd, "setwarp", new String[0]);
        verify(storage, never()).setWarp(any(), any());
    }

    @Test
    void setsWarpUsingPlayerCurrentLocation() {
        Player player = playerWithLocation();
        when(player.hasPermission(Permissions.ADMIN_SETWARP)).thenReturn(true);

        cmd.onCommand(player, bukkitCmd, "setwarp", new String[]{"home"});

        ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);
        verify(storage).setWarp(eq("home"), pointCaptor.capture());
        Point captured = pointCaptor.getValue();
        assertEquals("world", captured.worldName());
        assertEquals(1.0, captured.x());
        assertEquals(2.0, captured.y());
        assertEquals(3.0, captured.z());
    }

    @Test
    void tabCompleteFiltersExistingWarps() {
        when(storage.getWarps()).thenReturn(Set.of("spawn", "shop", "shrine"));
        List<String> result = cmd.onTabComplete(mock(Player.class), bukkitCmd, "setwarp", new String[]{"sh"});
        assertTrue(result.contains("shop"));
        assertTrue(result.contains("shrine"));
        assertFalse(result.contains("spawn"));
    }

    private Player playerWithLocation() {
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location loc = new Location(world, 1.0, 2.0, 3.0, 0f, 0f);
        when(player.getLocation()).thenReturn(loc);
        return player;
    }
}
