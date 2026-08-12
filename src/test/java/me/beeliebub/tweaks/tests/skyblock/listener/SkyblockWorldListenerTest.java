package me.beeliebub.tweaks.tests.skyblock.listener;

import me.beeliebub.tweaks.skyblock.listener.SkyblockWorldListener;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkyblockWorldListenerTest {
    @Test
    void reservedSpawnRegionIsDeniedEvenToRegionAdministrators() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        Server server = mock(Server.class);
        SkyblockWorldListener.MessageFacade messages = mock(SkyblockWorldListener.MessageFacade.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getServer()).thenReturn(server);
        doReturn(java.util.List.of(player)).when(server).getOnlinePlayers();
        SkyblockWorldListener listener = new SkyblockWorldListener(
                candidate -> candidate == world, candidate -> true,
                new SkyblockWorldListener.SpawnRules() {
                    @Override public boolean isSpawnRegion(org.bukkit.Location location) { return false; }
                    @Override public boolean isIslandTerrain(org.bukkit.Location location) { return true; }
                }, messages, null);

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player,
                "/rg info SKYBLOCK-SPAWN");
        listener.onPlayerCommandPreprocess(event);

        assertTrue(event.isCancelled());
        verify(messages).spawnRegionDenied(player);
    }

    @Test
    void ordinaryRegionCommandsRemainAdminOnly() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        Server server = mock(Server.class);
        SkyblockWorldListener.MessageFacade messages = mock(SkyblockWorldListener.MessageFacade.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getServer()).thenReturn(server);
        doReturn(java.util.List.of(player)).when(server).getOnlinePlayers();
        SkyblockWorldListener listener = new SkyblockWorldListener(
                candidate -> candidate == world, candidate -> false,
                new SkyblockWorldListener.SpawnRules() {
                    @Override public boolean isSpawnRegion(org.bukkit.Location location) { return false; }
                    @Override public boolean isIslandTerrain(org.bukkit.Location location) { return true; }
                }, messages, null);

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/region info home");
        listener.onPlayerCommandPreprocess(event);

        assertTrue(event.isCancelled());
        verify(messages).regionDenied(player);

        PlayerCommandPreprocessEvent adminEvent = new PlayerCommandPreprocessEvent(player, "/region info home");
        SkyblockWorldListener adminListener = new SkyblockWorldListener(
                candidate -> candidate == world, candidate -> true,
                new SkyblockWorldListener.SpawnRules() {
                    @Override public boolean isSpawnRegion(org.bukkit.Location location) { return false; }
                    @Override public boolean isIslandTerrain(org.bukkit.Location location) { return true; }
                }, messages, null);
        adminListener.onPlayerCommandPreprocess(adminEvent);
        assertFalse(adminEvent.isCancelled());
    }
}
