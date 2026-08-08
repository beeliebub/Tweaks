package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.ProtectionListeners;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.ui.RegionSelectionManager;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrphanWorldScopeTest {

    @Test
    void unclaimInOneWorldDoesNotStripSameNamedLiveRegionElsewhere() {
        Tweaks plugin = mock(Tweaks.class);
        ProtectionManager protection = new ProtectionManager(plugin);
        World alpha = world("alpha");
        World beta = world("beta");
        protection.regions().put("alpha:base", region("base", "alpha"));
        protection.regions().put("beta:base", region("base", "beta"));

        assertEquals(ProtectionManager.UnclaimResult.OK, protection.unclaim(alpha, "base").result());
        assertTrue(protection.orphanedRegions().contains("alpha:base"));
        assertFalse(protection.orphanedRegions().contains("beta:base"));

        Chunk chunk = mock(Chunk.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(chunk.getWorld()).thenReturn(beta);
        when(chunk.getChunkKey()).thenReturn(0L);
        when(chunk.getX()).thenReturn(0);
        when(chunk.getZ()).thenReturn(0);
        when(chunk.getPersistentDataContainer()).thenReturn(pdc);
        doReturn(List.of("base")).when(pdc).getOrDefault(any(NamespacedKey.class), any(), eq(List.of()));

        new ProtectionListeners(plugin, protection, mock(RegionSelectionManager.class))
                .onChunkLoad(mockEvent(chunk));
        verify(pdc, never()).remove(any(NamespacedKey.class));
    }

    private static ChunkLoadEvent mockEvent(Chunk chunk) {
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        when(event.getChunk()).thenReturn(chunk);
        return event;
    }

    private static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }

    private static Region region(String id, String world) {
        return new Region(id, java.util.UUID.randomUUID(), List.of(), Map.of(), Map.of(), null,
                new Region.RegionBounds(0, 0, 0, 0), world);
    }
}
