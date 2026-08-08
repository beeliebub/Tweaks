package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PendingStampsWorldCollisionTest {

    @Test
    void sameCoordinatesRemainIndependentAcrossWorlds() {
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        World overworld = world("world");
        World nether = world("world_nether");

        protection.claim(region("base"), overworld, 0, 0, 95, 95);
        protection.claim(region("base"), nether, 0, 0, 95, 95);

        String overworldKey = ProtectionManager.stampKey("world", 0L);
        String netherKey = ProtectionManager.stampKey("world_nether", 0L);
        assertEquals(Set.of("base"), protection.pendingStamps().get(overworldKey));
        assertEquals(Set.of("base"), protection.pendingStamps().get(netherKey));
    }

    private static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.isChunkLoaded(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        return world;
    }

    private static Region region(String id) {
        return new Region(id, java.util.UUID.randomUUID(), List.of(),
                EnumSet.noneOf(RegionFlag.class));
    }
}
