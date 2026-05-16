package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.RegionSelection;
import me.beeliebub.tweaks.protection.RegionSelectionManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegionSelectionManagerTest {

    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static Player playerOf(UUID id) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(id);
        return p;
    }

    @Test
    void getReturnsNullForUnknownPlayer() {
        RegionSelectionManager mgr = new RegionSelectionManager(mock(Tweaks.class));
        assertNull(mgr.get(PLAYER));
    }

    @Test
    void getOrCreateReturnsFreshSelectionForNewPlayer() {
        RegionSelectionManager mgr = new RegionSelectionManager(mock(Tweaks.class));
        World world = mock(World.class);
        RegionSelection sel = mgr.getOrCreate(playerOf(PLAYER), world);
        assertNotNull(sel);
        assertSame(world, sel.world());
        assertFalse(sel.hasPos1());
        assertFalse(sel.hasPos2());
    }

    @Test
    void getOrCreateReturnsSameInstanceWhileWorldUnchanged() {
        RegionSelectionManager mgr = new RegionSelectionManager(mock(Tweaks.class));
        World world = mock(World.class);
        RegionSelection first = mgr.getOrCreate(playerOf(PLAYER), world);
        RegionSelection second = mgr.getOrCreate(playerOf(PLAYER), world);
        assertSame(first, second);
    }

    @Test
    void worldChangeResetsSelection() {
        RegionSelectionManager mgr = new RegionSelectionManager(mock(Tweaks.class));
        World worldA = mock(World.class);
        World worldB = mock(World.class);

        RegionSelection first = mgr.getOrCreate(playerOf(PLAYER), worldA);
        first.setPos1(42L);

        RegionSelection second = mgr.getOrCreate(playerOf(PLAYER), worldB);
        assertNotSame(first, second);
        assertSame(worldB, second.world());
        assertFalse(second.hasPos1(), "world switch must wipe prior anchors");
    }

    @Test
    void clearRemovesSelection() {
        RegionSelectionManager mgr = new RegionSelectionManager(mock(Tweaks.class));
        mgr.getOrCreate(playerOf(PLAYER), mock(World.class));
        mgr.clear(PLAYER);
        assertNull(mgr.get(PLAYER));
    }

    @Test
    void quitEventClearsSelection() {
        RegionSelectionManager mgr = new RegionSelectionManager(mock(Tweaks.class));
        Player p = playerOf(PLAYER);
        mgr.getOrCreate(p, mock(World.class));
        assertNotNull(mgr.get(PLAYER));

        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(p);

        mgr.onQuit(event);
        assertNull(mgr.get(PLAYER));
    }
}
