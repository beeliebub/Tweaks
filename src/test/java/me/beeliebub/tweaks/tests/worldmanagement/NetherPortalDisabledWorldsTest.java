package me.beeliebub.tweaks.tests.worldmanagement;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.worldmanagement.WorldRuleListener;
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

/**
 * Regression pinning the {@code disabled-nether-portal-worlds} config migration:
 * {@code WorldRuleListener} previously hardcoded {@code RESOURCE_WORLD_KEY}/
 * {@code RESOURCE_NETHER_WORLD_KEY}; this list-driven read must match that byte-for-byte, plus be
 * live-editable and support removal/addition of arbitrary world keys.
 */
class NetherPortalDisabledWorldsTest {

    private Tweaks plugin;
    private FileConfiguration config;

    @BeforeEach
    void setUp() {
        plugin = mock(Tweaks.class);
        config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getStringList("disabled-nether-portal-worlds"))
                .thenReturn(List.of("jass:resource", "jass:resource_nether"));
    }

    private PlayerPortalEvent netherPortalEventFrom(String worldKey) {
        World world = mock(World.class);
        NamespacedKey key = mock(NamespacedKey.class);
        when(key.asString()).thenReturn(worldKey);
        when(world.getKey()).thenReturn(key);
        Location from = new Location(world, 0, 64, 0);
        Player player = mock(Player.class);

        PlayerPortalEvent event = mock(PlayerPortalEvent.class);
        when(event.getFrom()).thenReturn(from);
        when(event.getCause()).thenReturn(TeleportCause.NETHER_PORTAL);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    @Test
    void matchesThePreviouslyHardcodedResourceWorldKey() {
        WorldRuleListener listener = new WorldRuleListener(plugin, mock(ProtectionManager.class));
        PlayerPortalEvent event = netherPortalEventFrom("jass:resource");
        listener.onPortal(event);
        verify(event).setCancelled(true);
    }

    @Test
    void matchesThePreviouslyHardcodedResourceNetherWorldKey() {
        WorldRuleListener listener = new WorldRuleListener(plugin, mock(ProtectionManager.class));
        PlayerPortalEvent event = netherPortalEventFrom("jass:resource_nether");
        listener.onPortal(event);
        verify(event).setCancelled(true);
    }

    @Test
    void allowsNetherPortalsFromWorldsNotInTheList() {
        WorldRuleListener listener = new WorldRuleListener(plugin, mock(ProtectionManager.class));
        PlayerPortalEvent event = netherPortalEventFrom("minecraft:overworld");
        listener.onPortal(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void listCanBeExtendedToANewWorldKeyAtRuntime() {
        when(config.getStringList("disabled-nether-portal-worlds"))
                .thenReturn(List.of("jass:archive"));
        WorldRuleListener listener = new WorldRuleListener(plugin, mock(ProtectionManager.class));
        PlayerPortalEvent event = netherPortalEventFrom("jass:archive");
        listener.onPortal(event);
        verify(event).setCancelled(true);
    }

    @Test
    void emptyListLeavesAllNetherPortalsAlone() {
        when(config.getStringList("disabled-nether-portal-worlds")).thenReturn(List.of());
        WorldRuleListener listener = new WorldRuleListener(plugin, mock(ProtectionManager.class));
        PlayerPortalEvent event = netherPortalEventFrom("jass:resource");
        listener.onPortal(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void worldKeyMatchingIsCaseInsensitive() {
        WorldRuleListener listener = new WorldRuleListener(plugin, mock(ProtectionManager.class));
        PlayerPortalEvent event = netherPortalEventFrom("JASS:RESOURCE");
        listener.onPortal(event);
        verify(event).setCancelled(true);
    }
}
