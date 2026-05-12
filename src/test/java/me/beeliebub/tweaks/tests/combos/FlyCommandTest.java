package me.beeliebub.tweaks.tests.combos;

import me.beeliebub.tweaks.combos.FlyCommand;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlyCommandTest {

    private ServerMock server;
    private JavaPlugin plugin;
    private FlyCommand cmd;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("tweaks");
        // Pre-seed config with a fly-worlds list so canFly() returns true for our test world.
        plugin.getConfig().set("fly-worlds", List.of("custom:flyzone"));
        plugin.getConfig().set("fly-advancement", "jass:test");
        cmd = new FlyCommand(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock playerInWorld(String namespace, String key) {
        WorldMock world = new WorldMock(Material.GRASS_BLOCK, 0) {
            @Override
            public NamespacedKey getKey() {
                return new NamespacedKey(namespace, key);
            }
        };
        server.addWorld(world);
        PlayerMock player = server.addPlayer();
        player.teleport(world.getSpawnLocation());
        return player;
    }

    @Test
    void rejectsConsoleSender() {
        ConsoleCommandSender console = server.getConsoleSender();
        boolean handled = cmd.onCommand(console, bukkitCmd, "fly", new String[0]);
        assertTrue(handled);
    }

    @Test
    void enablesFlightInDefaultFlyWorld() {
        PlayerMock player = playerInWorld("custom", "flyzone");
        cmd.onCommand(player, bukkitCmd, "fly", new String[0]);
        assertTrue(player.getAllowFlight());
        assertTrue(player.isFlying());
    }

    @Test
    void deniesFlightInNonFlyWorldWithoutAdvancement() {
        PlayerMock player = playerInWorld("custom", "regular");
        cmd.onCommand(player, bukkitCmd, "fly", new String[0]);
        assertFalse(player.getAllowFlight(), "no advancement, not a fly-world: must stay grounded");
    }

    @Test
    void disablesFlightWhenAlreadyFlying() {
        PlayerMock player = playerInWorld("custom", "flyzone");
        // Enable first…
        cmd.onCommand(player, bukkitCmd, "fly", new String[0]);
        assertTrue(player.getAllowFlight());
        // …then toggle off.
        cmd.onCommand(player, bukkitCmd, "fly", new String[0]);
        assertFalse(player.getAllowFlight());
        assertFalse(player.isFlying());
    }

    @Test
    void enablingFlightStoresPdcMarker() {
        PlayerMock player = playerInWorld("custom", "flyzone");
        cmd.onCommand(player, bukkitCmd, "fly", new String[0]);
        Boolean stored = player.getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "fly_enabled"), PersistentDataType.BOOLEAN);
        assertEquals(Boolean.TRUE, stored);
    }

    @Test
    void disablingFlightClearsPdcMarker() {
        PlayerMock player = playerInWorld("custom", "flyzone");
        cmd.onCommand(player, bukkitCmd, "fly", new String[0]);
        cmd.onCommand(player, bukkitCmd, "fly", new String[0]);
        Boolean stored = player.getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "fly_enabled"), PersistentDataType.BOOLEAN);
        assertEquals(Boolean.FALSE, stored);
    }

    @Test
    void onJoinRestoresFlightWhenPdcSaysEnabledAndPlayerStillQualifies() {
        PlayerMock player = playerInWorld("custom", "flyzone");
        // Pretend we previously enabled flight and the player logged out.
        player.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "fly_enabled"), PersistentDataType.BOOLEAN, true);

        cmd.onPlayerJoin(new PlayerJoinEvent(player, ""));
        assertTrue(player.getAllowFlight(), "should restore flight on rejoin in fly-world");
    }

    @Test
    void onJoinDoesNotRestoreFlightWhenPlayerNoLongerQualifies() {
        PlayerMock player = playerInWorld("custom", "regular");
        player.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "fly_enabled"), PersistentDataType.BOOLEAN, true);

        cmd.onPlayerJoin(new PlayerJoinEvent(player, ""));
        assertFalse(player.getAllowFlight(), "must drop flight if player loses access");
    }

    @Test
    void onJoinIsNoOpWhenPdcMarkerIsMissingOrFalse() {
        PlayerMock player = playerInWorld("custom", "flyzone");
        cmd.onPlayerJoin(new PlayerJoinEvent(player, ""));
        assertFalse(player.getAllowFlight(), "no marker means no flight to restore");
    }

    @Test
    void flyWorldNameMatchingLowercasesConfiguredEntriesOnLoad() {
        // The constructor calls toLowerCase() on every entry in fly-worlds. NamespacedKey
        // itself rejects uppercase characters, so we can't test mixed-case worlds at runtime;
        // instead we verify a lowercased config entry resolves correctly even when the user
        // types it uppercased in config.yml.
        plugin.getConfig().set("fly-worlds", List.of("CUSTOM:FLYZONE"));
        FlyCommand secondCmd = new FlyCommand(plugin);
        PlayerMock player = playerInWorld("custom", "flyzone");
        secondCmd.onCommand(player, bukkitCmd, "fly", new String[0]);
        assertTrue(player.getAllowFlight(),
                "uppercased fly-worlds entries must be normalised to match the world key");
    }
}
