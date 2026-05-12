package me.beeliebub.tweaks.tests.combos;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.combos.AfkCommand;
import me.beeliebub.tweaks.combos.TabManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AfkCommandTest {

    private ServerMock server;
    private AfkCommand cmd;
    private WorldMock world;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        // AfkCommand's constructor only stores the plugin reference; it's used inside start()
        // for runTaskTimer scheduling, which we don't exercise here. A Mockito mock of Tweaks
        // is therefore safe — and necessary because MockBukkit.createMockPlugin returns a
        // MockPlugin, not a Tweaks subclass.
        Tweaks plugin = mock(Tweaks.class);
        cmd = new AfkCommand(plugin);
        // TabManager isn't required for the AFK toggle itself; explicit null check in production.
        cmd.setTabManager(new TabManager());
        world = new WorldMock(Material.GRASS_BLOCK, 0);
        server.addWorld(world);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock playerAt(double x, double y, double z) {
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, x, y, z));
        return player;
    }

    @Test
    void initiallyNotAfk() {
        assertFalse(cmd.isAfk(playerAt(0, 64, 0)));
    }

    @Test
    void commandTogglesAfkOn() {
        PlayerMock player = playerAt(0, 64, 0);
        cmd.onCommand(player, bukkitCmd, "afk", new String[0]);
        assertTrue(cmd.isAfk(player));
        assertTrue(player.isSleepingIgnored());
    }

    @Test
    void secondCommandTogglesAfkOff() {
        PlayerMock player = playerAt(0, 64, 0);
        cmd.onCommand(player, bukkitCmd, "afk", new String[0]);
        cmd.onCommand(player, bukkitCmd, "afk", new String[0]);
        assertFalse(cmd.isAfk(player));
        assertFalse(player.isSleepingIgnored());
    }

    @Test
    void rotationOnlyMoveDoesNotExitAfk() {
        PlayerMock player = playerAt(0, 64, 0);
        cmd.onCommand(player, bukkitCmd, "afk", new String[0]);
        Location from = new Location(world, 0, 64, 0, 0f, 0f);
        Location to = new Location(world, 0, 64, 0, 90f, 45f); // rotation only
        cmd.onMove(new PlayerMoveEvent(player, from, to));
        assertTrue(cmd.isAfk(player), "rotation-only moves must not exit AFK");
    }

    @Test
    void movingFurtherThanOneBlockExitsAfk() {
        PlayerMock player = playerAt(0, 64, 0);
        cmd.onCommand(player, bukkitCmd, "afk", new String[0]);
        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 5, 64, 0); // 5 blocks away
        cmd.onMove(new PlayerMoveEvent(player, from, to));
        assertFalse(cmd.isAfk(player));
    }

    @Test
    void movingLessThanOneBlockKeepsAfk() {
        PlayerMock player = playerAt(0, 64, 0);
        cmd.onCommand(player, bukkitCmd, "afk", new String[0]);
        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 0.5, 64, 0); // half a block away (sq distance 0.25 < 1)
        cmd.onMove(new PlayerMoveEvent(player, from, to));
        assertTrue(cmd.isAfk(player), "small wiggles inside the 1-block radius must not exit AFK");
    }

    @Test
    void changingWorldsExitsAfk() {
        PlayerMock player = playerAt(0, 64, 0);
        cmd.onCommand(player, bukkitCmd, "afk", new String[0]);
        WorldMock other = new WorldMock(Material.STONE, 0);
        server.addWorld(other);
        // The move event used here has a different-world destination.
        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(other, 0, 64, 0);
        cmd.onMove(new PlayerMoveEvent(player, from, to));
        assertFalse(cmd.isAfk(player));
    }

    @Test
    void teleportFurtherThanOneBlockExitsAfk() {
        PlayerMock player = playerAt(0, 64, 0);
        cmd.onCommand(player, bukkitCmd, "afk", new String[0]);
        cmd.onTeleport(new PlayerTeleportEvent(player,
                new Location(world, 0, 64, 0),
                new Location(world, 100, 64, 100),
                TeleportCause.COMMAND));
        assertFalse(cmd.isAfk(player));
    }

    @Test
    void quitClearsSleepingIgnoredAndAfkState() {
        PlayerMock player = playerAt(0, 64, 0);
        cmd.onCommand(player, bukkitCmd, "afk", new String[0]);
        cmd.onQuit(new PlayerQuitEvent(player, (net.kyori.adventure.text.Component) null));
        assertFalse(cmd.isAfk(player));
        assertFalse(player.isSleepingIgnored());
    }
}
