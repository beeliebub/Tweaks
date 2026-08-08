package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegionsAtStalePointerTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static Location location(World world, int chunkX, int chunkZ, List<String> ids) {
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(chunkX);
        when(chunk.getZ()).thenReturn(chunkZ);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(chunk.getPersistentDataContainer()).thenReturn(pdc);
        doReturn(ids).when(pdc).getOrDefault(any(NamespacedKey.class), any(), eq(List.of()));
        Location location = mock(Location.class);
        when(location.getChunk()).thenReturn(chunk);
        return location;
    }

    @Test
    void outOfBoundsLivePointerIsInertWithoutChangingOrphans() {
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Region region = new Region("home", OWNER, List.of(), Map.of(), Map.of(), null,
                new Region.RegionBounds(1, 1, 2, 2), "world");
        protection.regions().put("world:home", region);
        protection.orphanedRegions().add("already-orphaned");
        Set<String> before = Set.copyOf(protection.orphanedRegions());

        assertTrue(protection.regionsAt(location(world, 0, 0, List.of("home"))).isEmpty());
        assertEquals(before, protection.orphanedRegions());
    }

    @Test
    void nullBoundsLegacyPointerStillResolves() {
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Region legacy = new Region("legacy", OWNER, List.of(), Map.of());
        protection.regions().put("world:legacy", legacy);

        assertEquals(List.of(legacy),
                protection.regionsAt(location(world, 99, -99, List.of("legacy"))));
    }
}
