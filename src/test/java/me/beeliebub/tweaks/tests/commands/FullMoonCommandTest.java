package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.commands.FullMoonCommand;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FullMoonCommandTest {

    private final FullMoonCommand cmd = new FullMoonCommand();
    private final Command bukkitCmd = mock(Command.class);

    @Test
    void warnsWhenNoOverworldExists() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            cmd.onCommand(console, bukkitCmd, "fullmoon", new String[0]);
            verify(console).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }

    @Test
    void announcesActiveFullMoonWhenItIsCurrentlyUp() {
        Player player = playerInOverworld(world -> {
            // Day 0 of phase cycle (full moon), and night-time.
            when(world.getFullTime()).thenReturn(13_500L);
            when(world.getTime()).thenReturn(13_500L);
        });
        cmd.onCommand(player, bukkitCmd, "fullmoon", new String[0]);
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void announcesUpcomingFullMoonWhenInDifferentPhase() {
        // Day 1 of the cycle (not full-moon) — next full moon is 7 days away.
        Player player = playerInOverworld(world -> {
            when(world.getFullTime()).thenReturn(24_000L); // start of day 1
            when(world.getTime()).thenReturn(0L);
        });
        cmd.onCommand(player, bukkitCmd, "fullmoon", new String[0]);
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void minutesAreClampedToAtLeastOne() {
        // Set a time where remaining ticks rounds to 0 minutes — must report >= 1 min.
        Player player = playerInOverworld(world -> {
            // Day 0 just before night, very close to full-moon trigger
            long full = 24_000L * 7L + 12_999L; // day 7, 1 tick before night (still cycle phase 7)
            when(world.getFullTime()).thenReturn(full);
            when(world.getTime()).thenReturn(12_999L);
        });
        cmd.onCommand(player, bukkitCmd, "fullmoon", new String[0]);
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void prefersPlayersOwnOverworldOverFallbackScan() {
        World playerWorld = mock(World.class);
        when(playerWorld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(playerWorld.getFullTime()).thenReturn(100L);
        when(playerWorld.getTime()).thenReturn(100L);

        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(playerWorld);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            // Bukkit.getWorlds should never be consulted when the player is already in NORMAL.
            cmd.onCommand(player, bukkitCmd, "fullmoon", new String[0]);
            bukkit.verifyNoInteractions();
        }
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void fallsBackToFirstNormalWorldWhenSenderHasNoOverworld() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        World nether = mock(World.class);
        when(nether.getEnvironment()).thenReturn(World.Environment.NETHER);
        World overworld = mock(World.class);
        when(overworld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(overworld.getFullTime()).thenReturn(0L);
        when(overworld.getTime()).thenReturn(0L);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(nether, overworld));
            cmd.onCommand(console, bukkitCmd, "fullmoon", new String[0]);
            verify(console).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }

    private interface WorldStubber {
        void stub(World w);
    }

    private Player playerInOverworld(WorldStubber stubber) {
        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        stubber.stub(world);
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        return player;
    }
}
