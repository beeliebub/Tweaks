package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.commands.HomesCommand;
import me.beeliebub.tweaks.managers.StorageManager;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class HomesCommandTest {

    private final StorageManager storage = mock(StorageManager.class);
    private final HomesCommand cmd = new HomesCommand(storage);
    private final Command bukkitCmd = mock(Command.class);

    @Test
    void consoleWithoutTargetIsToldToProvideOne() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        cmd.onCommand(console, bukkitCmd, "homes", new String[0]);
        verify(console).sendMessage(argThat((net.kyori.adventure.text.Component c) -> c != null));
        verify(storage, never()).getHomes(any());
    }

    @Test
    void playerWithNoHomesGetsEmptyMessage() {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Bee");
        when(storage.getHomes(uuid)).thenReturn(Set.of());

        cmd.onCommand(player, bukkitCmd, "homes", new String[0]);
        verify(player).sendMessage(argThat(componentContains("Bee has no homes")));
    }

    @Test
    void playerWithHomesGetsListedNames() {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Bee");

        Set<String> homes = new LinkedHashSet<>();
        homes.add("base");
        homes.add("mine");
        when(storage.getHomes(uuid)).thenReturn(homes);

        cmd.onCommand(player, bukkitCmd, "homes", new String[0]);
        verify(player).sendMessage(argThat(componentContains("base")));
        verify(player).sendMessage(argThat(componentContains("mine")));
    }

    @Test
    void adminCanQueryAnotherPlayerByName() {
        Player admin = mock(Player.class);
        when(admin.hasPermission(Permissions.ADMIN_HOMES)).thenReturn(true);
        when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
        when(admin.getName()).thenReturn("Admin");

        UUID targetUuid = UUID.randomUUID();
        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(targetUuid);
        when(target.getName()).thenReturn("Bob");

        when(storage.getHomes(targetUuid)).thenReturn(Set.of("hideout"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(target);
            cmd.onCommand(admin, bukkitCmd, "homes", new String[]{"Bob"});
        }
        verify(admin).sendMessage(argThat(componentContains("Bob")));
        verify(admin).sendMessage(argThat(componentContains("hideout")));
    }

    @Test
    void nonAdminCannotQueryAnotherPlayerAndFallsBackToSelfIfPlayer() {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Bee");
        when(player.hasPermission(Permissions.ADMIN_HOMES)).thenReturn(false);
        when(storage.getHomes(uuid)).thenReturn(Set.of("base"));

        cmd.onCommand(player, bukkitCmd, "homes", new String[]{"Bob"});
        // Without ADMIN_HOMES, the lookup must fall back to the player's own homes.
        verify(storage).getHomes(uuid);
    }

    private static org.mockito.ArgumentMatcher<net.kyori.adventure.text.Component> componentContains(String needle) {
        return c -> c != null
                && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(c).toLowerCase().contains(needle.toLowerCase());
    }
}
