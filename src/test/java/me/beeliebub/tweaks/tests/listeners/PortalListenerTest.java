package me.beeliebub.tweaks.tests.listeners;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.listeners.PortalListener;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class PortalListenerTest {

    private Tweaks plugin;
    private FileConfiguration config;

    @BeforeEach
    void setUp() {
        plugin = mock(Tweaks.class);
        config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getStringList("disabled-end-portal-worlds"))
                .thenReturn(List.of("jass:archive", "jass:resource"));
    }

    private PlayerPortalEvent eventFromWorld(String namespace, String key, TeleportCause cause) {
        World world = mock(World.class);
        NamespacedKey worldKey = mock(NamespacedKey.class);
        when(worldKey.asString()).thenReturn(namespace + ":" + key);
        when(world.getKey()).thenReturn(worldKey);
        Location from = new Location(world, 0, 64, 0);
        Player player = mock(Player.class);

        PlayerPortalEvent event = mock(PlayerPortalEvent.class);
        when(event.getFrom()).thenReturn(from);
        when(event.getCause()).thenReturn(cause);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    @Test
    void cancelsEndPortalFromConfiguredDisabledWorld() {
        PortalListener listener = new PortalListener(plugin);
        PlayerPortalEvent event = eventFromWorld("jass", "archive", TeleportCause.END_PORTAL);
        listener.onPortal(event);
        verify(event).setCancelled(true);
    }

    @Test
    void allowsEndPortalFromOtherWorlds() {
        PortalListener listener = new PortalListener(plugin);
        PlayerPortalEvent event = eventFromWorld("minecraft", "overworld", TeleportCause.END_PORTAL);
        listener.onPortal(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void cancelsNetherPortalFromResourceWorld() {
        PortalListener listener = new PortalListener(plugin);
        PlayerPortalEvent event = eventFromWorld("jass", "resource", TeleportCause.NETHER_PORTAL);
        listener.onPortal(event);
        verify(event).setCancelled(true);
    }

    @Test
    void cancelsNetherPortalFromResourceNetherWorld() {
        PortalListener listener = new PortalListener(plugin);
        PlayerPortalEvent event = eventFromWorld("jass", "resource_nether", TeleportCause.NETHER_PORTAL);
        listener.onPortal(event);
        verify(event).setCancelled(true);
    }

    @Test
    void allowsNetherPortalFromRegularWorlds() {
        PortalListener listener = new PortalListener(plugin);
        PlayerPortalEvent event = eventFromWorld("minecraft", "overworld", TeleportCause.NETHER_PORTAL);
        listener.onPortal(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void worldKeyMatchingIsCaseInsensitive() {
        PortalListener listener = new PortalListener(plugin);
        // The constructor lowercases entries; the listener also lowercases the world key.
        PlayerPortalEvent event = eventFromWorld("JASS", "ARCHIVE", TeleportCause.END_PORTAL);
        listener.onPortal(event);
        verify(event).setCancelled(true);
    }

    @Test
    void unrelatedTeleportCausesAreUntouched() {
        PortalListener listener = new PortalListener(plugin);
        PlayerPortalEvent event = eventFromWorld("jass", "archive", TeleportCause.PLUGIN);
        listener.onPortal(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void emptyDisabledListLeavesAllEndPortalsAlone() {
        when(config.getStringList("disabled-end-portal-worlds")).thenReturn(List.of());
        PortalListener listener = new PortalListener(plugin);
        PlayerPortalEvent event = eventFromWorld("jass", "archive", TeleportCause.END_PORTAL);
        listener.onPortal(event);
        verify(event, never()).setCancelled(true);
    }
}
