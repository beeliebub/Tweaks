package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.ProtectionListeners;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import me.beeliebub.tweaks.protection.ui.RegionSelectionManager;
import org.bukkit.World;
import org.bukkit.event.world.WorldLoadEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalRegionLifecycleTest {

    @Test
    void worldLoadMaterializesAnIsolatedGlobalRegion() {
        Tweaks plugin = mock(Tweaks.class);
        ProtectionManager protection = new ProtectionManager(plugin);
        ProtectionListeners listeners = new ProtectionListeners(plugin, protection,
                mock(RegionSelectionManager.class));
        World alpha = world("alpha");
        World beta = world("beta");

        listeners.onWorldLoad(new WorldLoadEvent(alpha));
        assertNotNull(protection.regions().get("alpha:__global__"));
        assertTrue(protection.setFlag(alpha, ProtectionManager.GLOBAL_REGION_ID,
                RegionFlag.BLOCK_BREAK,
                me.beeliebub.tweaks.protection.region.FlagTarget.DEFAULT, false));
        assertNull(protection.globalRegion(beta).rulesFor(RegionFlag.BLOCK_BREAK)
                .get(me.beeliebub.tweaks.protection.region.FlagTarget.DEFAULT));
    }

    private static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }
}
