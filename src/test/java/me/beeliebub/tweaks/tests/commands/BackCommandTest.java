package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.commands.BackCommand;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private ResourceHuntItems resourceHuntItems;
    private BackCommand backCommand;
    private NamespacedKey backKey;
    private final org.bukkit.command.Command bukkitCmd = mock(org.bukkit.command.Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        resourceHuntItems = mock(ResourceHuntItems.class);
        backCommand = new BackCommand(plugin, resourceHuntItems);
        backKey = new NamespacedKey(plugin, "back_location");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onTeleportSavesLocation() {
        PlayerMock player = server.addPlayer();
        Location from = new Location(player.getWorld(), 10, 64, 10);
        Location to = new Location(player.getWorld(), 20, 64, 20);
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, from, to, PlayerTeleportEvent.TeleportCause.COMMAND);

        backCommand.onTeleport(event);

        String stored = player.getPersistentDataContainer().get(backKey, PersistentDataType.STRING);
        assertNotNull(stored);
        assertTrue(stored.contains("10.0,64.0,10.0"));
    }

    @Test
    void onTeleportIgnoresExcludedCauses() {
        PlayerMock player = server.addPlayer();
        Location from = new Location(player.getWorld(), 10, 64, 10);
        Location to = new Location(player.getWorld(), 20, 64, 20);

        PlayerTeleportEvent event = new PlayerTeleportEvent(player, from, to, PlayerTeleportEvent.TeleportCause.EXIT_BED);
        backCommand.onTeleport(event);
        assertNull(player.getPersistentDataContainer().get(backKey, PersistentDataType.STRING));

        event = new PlayerTeleportEvent(player, from, to, PlayerTeleportEvent.TeleportCause.DISMOUNT);
        backCommand.onTeleport(event);
        assertNull(player.getPersistentDataContainer().get(backKey, PersistentDataType.STRING));
    }

    @Test
    void onDeathSavesLocation() {
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(player.getWorld(), 5, 70, 5));
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);

        backCommand.onDeath(event);

        String stored = player.getPersistentDataContainer().get(backKey, PersistentDataType.STRING);
        assertNotNull(stored);
        assertTrue(stored.contains("5.0,70.0,5.0"));
    }

    @Test
    void backCommandTeleportsPlayer() {
        PlayerMock player = server.addPlayer();
        String serialized = player.getWorld().getName() + ",100,64,100,0,0";
        player.getPersistentDataContainer().set(backKey, PersistentDataType.STRING, serialized);

        backCommand.onCommand(player, bukkitCmd, "back", new String[0]);

        assertEquals(100, player.getLocation().getX());
        assertEquals(64, player.getLocation().getY());
        assertEquals(100, player.getLocation().getZ());
    }

    @Test
    void backCommandFailsWithNoLocation() {
        PlayerMock player = server.addPlayer();
        backCommand.onCommand(player, bukkitCmd, "back", new String[0]);
        assertNotNull(player.nextMessage());
    }
}
