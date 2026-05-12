package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.Point;
import me.beeliebub.tweaks.commands.SetHomeCommand;
import me.beeliebub.tweaks.managers.StorageManager;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SetHomeCommandTest {

    private final StorageManager storage = mock(StorageManager.class);
    private final SetHomeCommand cmd = new SetHomeCommand(storage, /* maxHomes */ 3);
    private final Command bukkitCmd = mock(Command.class);

    @Test
    void rejectsConsoleSender() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        cmd.onCommand(console, bukkitCmd, "sethome", new String[0]);
        verify(storage, never()).setHome(any(), anyString(), any());
    }

    @Test
    void refusesToSetHomeInResourceWorld() {
        Player player = playerInWorld("jass:resource");
        cmd.onCommand(player, bukkitCmd, "sethome", new String[0]);
        verify(storage, never()).setHome(any(), anyString(), any());
    }

    @Test
    void setsDefaultHomeWhenNoNameGiven() {
        UUID uuid = UUID.randomUUID();
        Player player = playerInWorld("world");
        when(player.getUniqueId()).thenReturn(uuid);
        when(storage.getHomeCount(uuid)).thenReturn(0);
        when(storage.getHome(uuid, "default")).thenReturn(Optional.empty());

        cmd.onCommand(player, bukkitCmd, "sethome", new String[0]);
        verify(storage).setHome(eq(uuid), eq("default"), any(Point.class));
    }

    @Test
    void setsNamedHome() {
        UUID uuid = UUID.randomUUID();
        Player player = playerInWorld("world");
        when(player.getUniqueId()).thenReturn(uuid);
        when(storage.getHomeCount(uuid)).thenReturn(0);
        when(storage.getHome(uuid, "base")).thenReturn(Optional.empty());

        cmd.onCommand(player, bukkitCmd, "sethome", new String[]{"base"});
        verify(storage).setHome(eq(uuid), eq("base"), any(Point.class));
    }

    @Test
    void enforcesMaxHomesLimitForNonBypassers() {
        UUID uuid = UUID.randomUUID();
        Player player = playerInWorld("world");
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.hasPermission(Permissions.BYPASS_HOMES)).thenReturn(false);
        when(storage.getHomeCount(uuid)).thenReturn(3); // at the limit (maxHomes=3)
        when(storage.getHome(uuid, "another")).thenReturn(Optional.empty());

        cmd.onCommand(player, bukkitCmd, "sethome", new String[]{"another"});
        verify(storage, never()).setHome(any(), anyString(), any());
    }

    @Test
    void allowsOverwritingExistingHomeAtLimit() {
        UUID uuid = UUID.randomUUID();
        Player player = playerInWorld("world");
        when(player.getUniqueId()).thenReturn(uuid);
        when(storage.getHomeCount(uuid)).thenReturn(3);
        // Reusing an existing name doesn't grow the count, so it must be allowed.
        when(storage.getHome(uuid, "base"))
                .thenReturn(Optional.of(new Point("world", 0, 0, 0, 0f, 0f)));

        cmd.onCommand(player, bukkitCmd, "sethome", new String[]{"base"});
        verify(storage).setHome(eq(uuid), eq("base"), any(Point.class));
    }

    @Test
    void bypassPermissionSkipsLimitCheck() {
        UUID uuid = UUID.randomUUID();
        Player player = playerInWorld("world");
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.hasPermission(Permissions.BYPASS_HOMES)).thenReturn(true);
        when(storage.getHomeCount(uuid)).thenReturn(99); // far above limit
        when(storage.getHome(uuid, "extra")).thenReturn(Optional.empty());

        cmd.onCommand(player, bukkitCmd, "sethome", new String[]{"extra"});
        verify(storage).setHome(eq(uuid), eq("extra"), any(Point.class));
    }

    @Test
    void adminCanSetHomeForAnotherPlayerByName() {
        UUID adminUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        Player admin = playerInWorld("world");
        when(admin.getUniqueId()).thenReturn(adminUuid);
        when(admin.hasPermission(Permissions.ADMIN_SETHOME)).thenReturn(true);

        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(targetUuid);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(target);
            cmd.onCommand(admin, bukkitCmd, "sethome", new String[]{"Bob", "base"});
        }
        verify(storage).setHome(eq(targetUuid), eq("base"), any(Point.class));
    }

    private Player playerInWorld(String worldKey) {
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        NamespacedKey key = mock(NamespacedKey.class);
        when(key.asString()).thenReturn(worldKey);
        when(world.getKey()).thenReturn(key);
        Location loc = new Location(world, 0, 0, 0, 0f, 0f);
        when(player.getLocation()).thenReturn(loc);
        when(player.getWorld()).thenReturn(world);
        return player;
    }
}
