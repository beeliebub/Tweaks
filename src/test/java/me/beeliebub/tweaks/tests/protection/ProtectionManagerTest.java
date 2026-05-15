package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ProtectionManagerTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void cachesStartEmptyAndAreConcurrent() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        assertInstanceOf(ConcurrentHashMap.class, mgr.regions());
        assertInstanceOf(ConcurrentHashMap.class, mgr.pendingStamps());
        assertTrue(mgr.regions().isEmpty());
        assertTrue(mgr.pendingStamps().isEmpty());
    }

    @Test
    void regionCacheAcceptsAndReturnsRegions() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        Region r = new Region("home", OWNER, List.of(), EnumSet.noneOf(RegionFlag.class));

        mgr.regions().put("home", r);

        assertSame(r, mgr.regions().get("home"));
        assertEquals(1, mgr.regions().size());
    }

    @Test
    void pendingStampsAcceptsThreadSafeSetValues() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        Set<String> ids = ConcurrentHashMap.newKeySet();
        ids.add("home");
        ids.add("admin_zone");

        mgr.pendingStamps().put(42L, ids);

        Set<String> stored = mgr.pendingStamps().get(42L);
        assertEquals(Set.of("home", "admin_zone"), stored);
    }

    @Test
    void pluginAccessorReturnsConstructorInstance() {
        Tweaks plugin = mock(Tweaks.class);
        ProtectionManager mgr = new ProtectionManager(plugin);
        assertSame(plugin, mgr.plugin());
    }
}
