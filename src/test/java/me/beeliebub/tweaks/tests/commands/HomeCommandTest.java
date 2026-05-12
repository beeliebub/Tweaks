package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.Point;
import me.beeliebub.tweaks.commands.HomeCommand;
import me.beeliebub.tweaks.managers.StorageManager;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HomeCommandTest {

    private final StorageManager storage = mock(StorageManager.class);
    private final HomeCommand cmd = new HomeCommand(storage);
    private final Command bukkitCmd = mock(Command.class);

    @Test
    void rejectsConsoleSender() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        cmd.onCommand(console, bukkitCmd, "home", new String[0]);
        verify(storage, never()).getHome(any(), any());
    }

    @Test
    void noArgUsesDefaultHomeName() {
        UUID uuid = UUID.randomUUID();
        Player player = playerWithUuid(uuid);
        when(storage.getHome(uuid, "default")).thenReturn(Optional.empty());
        cmd.onCommand(player, bukkitCmd, "home", new String[0]);
        verify(storage).getHome(uuid, "default");
    }

    @Test
    void singleArgUsesNamedHome() {
        UUID uuid = UUID.randomUUID();
        Player player = playerWithUuid(uuid);
        when(storage.getHome(uuid, "base")).thenReturn(Optional.empty());
        cmd.onCommand(player, bukkitCmd, "home", new String[]{"base"});
        verify(storage).getHome(uuid, "base");
    }

    @Test
    void warnsWhenHomeNotFound() {
        UUID uuid = UUID.randomUUID();
        Player player = playerWithUuid(uuid);
        when(storage.getHome(uuid, "ghost")).thenReturn(Optional.empty());
        cmd.onCommand(player, bukkitCmd, "home", new String[]{"ghost"});
        verify(player, never()).teleportAsync(any());
    }

    @Test
    void warnsWhenWorldUnloaded() {
        UUID uuid = UUID.randomUUID();
        Player player = playerWithUuid(uuid);
        when(storage.getHome(uuid, "base"))
                .thenReturn(Optional.of(new Point("ghost-world", 0, 0, 0, 0f, 0f)));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("ghost-world")).thenReturn(null);
            cmd.onCommand(player, bukkitCmd, "home", new String[]{"base"});
        }
        verify(player, never()).teleportAsync(any());
    }

    @Test
    void teleportsToResolvedHome() {
        UUID uuid = UUID.randomUUID();
        Player player = playerWithUuid(uuid);
        World world = mock(World.class);
        when(storage.getHome(uuid, "base"))
                .thenReturn(Optional.of(new Point("world", 1.0, 64.0, 1.0, 0f, 0f)));
        when(player.teleportAsync(any())).thenReturn(CompletableFuture.completedFuture(true));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            cmd.onCommand(player, bukkitCmd, "home", new String[]{"base"});
        }
        verify(player).teleportAsync(any(Location.class));
    }

    @Test
    void adminCanTeleportToAnotherPlayersHome() {
        UUID adminUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        World world = mock(World.class);

        Player admin = playerWithUuid(adminUuid);
        when(admin.hasPermission(Permissions.ADMIN_HOME)).thenReturn(true);
        when(admin.teleportAsync(any())).thenReturn(CompletableFuture.completedFuture(true));

        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(targetUuid);

        when(storage.getHome(targetUuid, "base"))
                .thenReturn(Optional.of(new Point("world", 0, 0, 0, 0f, 0f)));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(target);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            cmd.onCommand(admin, bukkitCmd, "home", new String[]{"Bob", "base"});
        }
        verify(storage).getHome(targetUuid, "base");
        verify(admin).teleportAsync(any(Location.class));
    }

    @Test
    void nonAdminTwoArgInvocationShowsUsageHint() {
        UUID uuid = UUID.randomUUID();
        Player player = playerWithUuid(uuid);
        when(player.hasPermission(Permissions.ADMIN_HOME)).thenReturn(false);
        cmd.onCommand(player, bukkitCmd, "home", new String[]{"Bob", "base"});
        verify(storage, never()).getHome(any(), any());
    }

    @Test
    void tabCompleteOffersOwnHomesPlusOnlinePlayersForAdmin() {
        UUID uuid = UUID.randomUUID();
        Player player = playerWithUuid(uuid);
        when(player.hasPermission(Permissions.ADMIN_HOME)).thenReturn(true);
        when(storage.getHomes(uuid)).thenReturn(Set.of("base", "mine"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.List.of());
            var result = cmd.onTabComplete(player, bukkitCmd, "home", new String[]{"b"});
            assertTrue(result.contains("base"));
            assertFalse(result.contains("mine"));
        }
    }

    private Player playerWithUuid(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }
}
