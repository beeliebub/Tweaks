package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.FlagTarget;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import me.beeliebub.tweaks.protection.region.RegionWriter;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtectionManagerTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static World mockWorld(String name) {
        World w = mock(World.class);
        when(w.getName()).thenReturn(name);
        return w;
    }

    @Test
    void cachesStartEmptyAndAreConcurrent() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        assertInstanceOf(ConcurrentHashMap.class, mgr.regions());
        assertInstanceOf(ConcurrentHashMap.class, mgr.pendingStamps());
        // Global regions are per-world and lazy-initialised — the cache starts empty.
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

        String key = ProtectionManager.stampKey("alpha", 42L);
        mgr.pendingStamps().put(key, ids);

        Set<String> stored = mgr.pendingStamps().get(key);
        assertEquals(Set.of("home", "admin_zone"), stored);
    }

    @Test
    void pluginAccessorReturnsConstructorInstance() {
        Tweaks plugin = mock(Tweaks.class);
        ProtectionManager mgr = new ProtectionManager(plugin);
        assertSame(plugin, mgr.plugin());
    }

    // ------------------------------------------------------------------
    // Global region behaviour
    // ------------------------------------------------------------------

    @Test
    void globalRegionLazyInitsPerWorldWithServerOwnerAndNoBounds() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        World w = mockWorld("alpha");
        Region global = mgr.globalRegion(w);
        assertNotNull(global);
        assertEquals(ProtectionManager.GLOBAL_REGION_ID, global.id());
        assertEquals(ProtectionManager.SERVER_OWNER, global.owner());
        // Owner sentinel must not match any real player's UUID.
        assertFalse(global.isOwner(OWNER));
        assertFalse(global.isMember(OWNER));
        // No bounds keeps it out of overlap iteration; worldName scopes it.
        assertNull(global.bounds());
        assertEquals("alpha", global.worldName());
        assertTrue(ProtectionManager.isGlobal(global));
        assertFalse(ProtectionManager.isGlobal(
                new Region("home", OWNER, List.of(), EnumSet.noneOf(RegionFlag.class))));
        // Same world returns the same cached entry; cache key is composite.
        assertSame(global, mgr.globalRegion(w));
        assertTrue(mgr.regions().containsKey("alpha:" + ProtectionManager.GLOBAL_REGION_ID));
    }

    @Test
    void perWorldGlobalsAreIndependent() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        World alpha = mockWorld("alpha");
        World beta = mockWorld("beta");

        // Flag set in alpha must not surface in beta's global.
        assertTrue(mgr.setFlag(alpha, ProtectionManager.GLOBAL_REGION_ID,
                RegionFlag.BLOCK_BREAK, FlagTarget.DEFAULT, false));

        Region alphaGlobal = mgr.globalRegion(alpha);
        Region betaGlobal = mgr.globalRegion(beta);
        assertNotSame(alphaGlobal, betaGlobal);
        assertEquals(Boolean.FALSE,
                alphaGlobal.rulesFor(RegionFlag.BLOCK_BREAK).get(FlagTarget.DEFAULT));
        assertNull(betaGlobal.rulesFor(RegionFlag.BLOCK_BREAK).get(FlagTarget.DEFAULT));
    }

    @Test
    void unclaimRefusesGlobalRegion() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        World w = mockWorld("alpha");
        mgr.globalRegion(w); // ensure it exists in the cache
        assertEquals(ProtectionManager.UnclaimResult.GLOBAL_DENIED,
                mgr.unclaim(ProtectionManager.GLOBAL_REGION_ID).result());
        // The per-world global must still be in the cache after the rejected unclaim.
        assertNotNull(mgr.globalRegion(w));
        assertFalse(mgr.orphanedRegions().contains(ProtectionManager.GLOBAL_REGION_ID));
    }

    @Test
    void setParentRefusesGlobalAsChild() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        Region home = new Region("home", OWNER, List.of(), EnumSet.noneOf(RegionFlag.class));
        mgr.regions().put("home", home);
        assertEquals(ProtectionManager.SetParentResult.UNKNOWN_CHILD,
                mgr.setParent(ProtectionManager.GLOBAL_REGION_ID, "home"));
    }

    @Test
    void globalRegionFlagEditUsesWorldAwareMutator() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        World w = mockWorld("alpha");
        // Admin sets BLOCK_BREAK=false on the world's global region — should mutate cleanly.
        assertTrue(mgr.setFlag(w, ProtectionManager.GLOBAL_REGION_ID,
                RegionFlag.BLOCK_BREAK, FlagTarget.DEFAULT, false));
        // No-op when re-applying the same value.
        assertFalse(mgr.setFlag(w, ProtectionManager.GLOBAL_REGION_ID,
                RegionFlag.BLOCK_BREAK, FlagTarget.DEFAULT, false));
        assertEquals(Boolean.FALSE,
                mgr.globalRegion(w).rulesFor(RegionFlag.BLOCK_BREAK).get(FlagTarget.DEFAULT));
        // Removing the rule should leave the cache entry intact.
        assertTrue(mgr.removeFlag(w, ProtectionManager.GLOBAL_REGION_ID,
                RegionFlag.BLOCK_BREAK, FlagTarget.DEFAULT));
        assertNotNull(mgr.globalRegion(w));
        // The string-id overload deliberately refuses globals (ambiguous without world).
        assertFalse(mgr.setFlag(ProtectionManager.GLOBAL_REGION_ID,
                RegionFlag.BLOCK_BREAK, FlagTarget.DEFAULT, true));
    }

    // ------------------------------------------------------------------
    // Persistence — flag mutations must trigger writer.queue so YAML survives
    // a restart. Pre-fix, only entity-list / member / manager mutations queued
    // writes; boolean and material flag changes were lost on reload.
    // ------------------------------------------------------------------

    @Test
    void setFlagOnRegularRegionQueuesPersistence() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        RegionWriter writer = mock(RegionWriter.class);
        mgr.setWriter(writer);
        World w = mockWorld("alpha");
        mgr.regions().put("alpha:plot", new Region(
                "plot", OWNER, List.of(), Map.of(), Map.of(),
                null, null, "alpha", List.of(), Map.of()));

        assertTrue(mgr.setFlag(w, "plot", RegionFlag.PVP, FlagTarget.DEFAULT, true));

        verify(writer).queue(argThat(updated ->
                "plot".equals(updated.id())
                        && Boolean.TRUE.equals(updated.rulesFor(RegionFlag.PVP).get(FlagTarget.DEFAULT))));
    }

    @Test
    void setFlagOnGlobalRegionQueuesPersistence() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        RegionWriter writer = mock(RegionWriter.class);
        mgr.setWriter(writer);
        World w = mockWorld("alpha");

        assertTrue(mgr.setFlag(w, ProtectionManager.GLOBAL_REGION_ID,
                RegionFlag.BLOCK_BREAK, FlagTarget.DEFAULT, false));

        verify(writer).queue(argThat(updated ->
                ProtectionManager.GLOBAL_REGION_ID.equals(updated.id())
                        && "alpha".equals(updated.worldName())
                        && Boolean.FALSE.equals(updated.rulesFor(RegionFlag.BLOCK_BREAK)
                                .get(FlagTarget.DEFAULT))));
    }

    @Test
    void removeFlagQueuesPersistence() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        RegionWriter writer = mock(RegionWriter.class);
        mgr.setWriter(writer);
        World w = mockWorld("alpha");
        // Seed an initial rule via the same path so we have something to remove.
        assertTrue(mgr.setFlag(w, ProtectionManager.GLOBAL_REGION_ID,
                RegionFlag.PVP, FlagTarget.DEFAULT, true));

        assertTrue(mgr.removeFlag(w, ProtectionManager.GLOBAL_REGION_ID,
                RegionFlag.PVP, FlagTarget.DEFAULT));

        // Two writes total: one for the set, one for the remove. The final
        // queued state must no longer hold the PVP rule.
        verify(writer).queue(argThat(updated ->
                ProtectionManager.GLOBAL_REGION_ID.equals(updated.id())
                        && updated.rulesFor(RegionFlag.PVP).isEmpty()));
    }

    @Test
    void setMaterialsQueuesPersistence() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        RegionWriter writer = mock(RegionWriter.class);
        mgr.setWriter(writer);
        World w = mockWorld("alpha");
        mgr.regions().put("alpha:plot", new Region(
                "plot", OWNER, List.of(), Map.of(), Map.of(),
                null, null, "alpha", List.of(), Map.of()));

        assertTrue(mgr.setMaterials(w, "plot", RegionFlag.ALLOW_BLOCK_BREAK,
                Set.of(Material.STONE, Material.DIRT)));

        verify(writer).queue(argThat(updated ->
                "plot".equals(updated.id())
                        && updated.materialsFor(RegionFlag.ALLOW_BLOCK_BREAK)
                                .equals(Set.of(Material.STONE, Material.DIRT))));
    }

    @Test
    void clearMaterialsQueuesPersistence() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        RegionWriter writer = mock(RegionWriter.class);
        mgr.setWriter(writer);
        World w = mockWorld("alpha");
        mgr.regions().put("alpha:plot", new Region(
                "plot", OWNER, List.of(),
                Map.of(),
                Map.of(RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.STONE)),
                null, null, "alpha", List.of(), Map.of()));

        assertTrue(mgr.clearMaterials(w, "plot", RegionFlag.ALLOW_BLOCK_BREAK));

        verify(writer).queue(argThat(updated ->
                "plot".equals(updated.id())
                        && updated.materialsFor(RegionFlag.ALLOW_BLOCK_BREAK).isEmpty()));
    }

    @Test
    void migrateLegacyRegionsLeavesGlobalUntouched() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        Region legacy = new Region("legacy", OWNER, List.of(), EnumSet.noneOf(RegionFlag.class));
        mgr.regions().put("legacy", legacy);
        World w = mockWorld("world");
        Region global = mgr.globalRegion(w);

        int migrated = mgr.migrateLegacyRegions("world");

        assertEquals(1, migrated);
        // Per-world global keeps its composite key and is not re-migrated.
        assertSame(global, mgr.regions().get("world:" + ProtectionManager.GLOBAL_REGION_ID));
    }
}
