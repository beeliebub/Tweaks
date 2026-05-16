package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.FlagTarget;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MaterialFlagsTest {

    private static final UUID OWNER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OUTSIDER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @BeforeAll
    static void initKeys() {
        try (MockedConstruction<NamespacedKey> ignored = mockConstruction(NamespacedKey.class)) {
            ProtectionKeys.init(mock(Tweaks.class));
        }
    }

    private ProtectionManager mgr;

    @BeforeEach
    void newManager() {
        mgr = new ProtectionManager(mock(Tweaks.class));
    }

    private static Location locInChunkWithPdc(List<String> pdcIds) {
        Chunk chunk = mock(Chunk.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(chunk.getPersistentDataContainer()).thenReturn(pdc);
        doReturn(pdcIds).when(pdc).getOrDefault(any(NamespacedKey.class), any(), eq(List.of()));
        Location loc = mock(Location.class);
        when(loc.getChunk()).thenReturn(chunk);
        return loc;
    }

    // ---- enum classifier ----

    @Test
    void isMaterialFlagPicksOutTheFourListFlags() {
        assertTrue(RegionFlag.ALLOW_BLOCK_BREAK.isMaterialFlag());
        assertTrue(RegionFlag.DENY_BLOCK_BREAK.isMaterialFlag());
        assertTrue(RegionFlag.ALLOW_BLOCK_PLACE.isMaterialFlag());
        assertTrue(RegionFlag.DENY_BLOCK_PLACE.isMaterialFlag());
        assertFalse(RegionFlag.BLOCK_BREAK.isMaterialFlag());
        assertFalse(RegionFlag.PVP.isMaterialFlag());
    }

    // ---- Region#resolveMaterial ----

    @Test
    void resolveMaterialReturnsDenyForBlacklistedMaterial() {
        Region r = new Region("r", OWNER, List.of(), Map.of(),
                Map.of(RegionFlag.DENY_BLOCK_BREAK, Set.of(Material.BEDROCK)), null);
        assertEquals(java.util.Optional.of(false),
                r.resolveMaterial(RegionFlag.DENY_BLOCK_BREAK, RegionFlag.ALLOW_BLOCK_BREAK, Material.BEDROCK));
    }

    @Test
    void resolveMaterialReturnsAllowForWhitelistedMaterial() {
        Region r = new Region("r", OWNER, List.of(), Map.of(),
                Map.of(RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.GRASS_BLOCK)), null);
        assertEquals(java.util.Optional.of(true),
                r.resolveMaterial(RegionFlag.DENY_BLOCK_BREAK, RegionFlag.ALLOW_BLOCK_BREAK, Material.GRASS_BLOCK));
    }

    @Test
    void denyWinsWhenMaterialIsInBothLists() {
        Region r = new Region("r", OWNER, List.of(), Map.of(),
                Map.of(
                        RegionFlag.DENY_BLOCK_BREAK, Set.of(Material.STONE),
                        RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.STONE)),
                null);
        assertEquals(java.util.Optional.of(false),
                r.resolveMaterial(RegionFlag.DENY_BLOCK_BREAK, RegionFlag.ALLOW_BLOCK_BREAK, Material.STONE));
    }

    @Test
    void resolveMaterialReturnsEmptyForUnlistedMaterial() {
        Region r = new Region("r", OWNER, List.of(), Map.of(),
                Map.of(RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.GRASS_BLOCK)), null);
        assertTrue(r.resolveMaterial(
                RegionFlag.DENY_BLOCK_BREAK, RegionFlag.ALLOW_BLOCK_BREAK, Material.DIAMOND_ORE).isEmpty());
    }

    // ---- isBlockActionAllowed integration ----

    @Test
    void allowListOverridesRestrictiveBooleanFlag() {
        // BLOCK_BREAK default=false (non-member blocked), but ALLOW list lets
        // grass blocks through.
        mgr.regions().put("home", new Region("home", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, false)),
                Map.of(RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.GRASS_BLOCK)),
                null));
        Location loc = locInChunkWithPdc(List.of("home"));

        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.GRASS_BLOCK, RegionFlag.BLOCK_BREAK));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void denyListOverridesPermissiveBooleanFlag() {
        // BLOCK_BREAK default=true (everyone breaks), but DENY shields beacons.
        mgr.regions().put("arena", new Region("arena", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, true)),
                Map.of(RegionFlag.DENY_BLOCK_BREAK, Set.of(Material.BEACON)),
                null));
        Location loc = locInChunkWithPdc(List.of("arena"));

        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, RegionFlag.BLOCK_BREAK));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.BEACON, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void childMaterialListOverridesParentBoolean() {
        // Parent permits everything; child denies diamond ore.
        mgr.regions().put("plot", new Region("plot", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, true)),
                Map.of(), null));
        mgr.regions().put("sub", new Region("sub", OWNER, List.of(), Map.of(),
                Map.of(RegionFlag.DENY_BLOCK_BREAK, Set.of(Material.DIAMOND_ORE)),
                "plot"));

        Location loc = locInChunkWithPdc(List.of("plot", "sub"));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.DIAMOND_ORE, RegionFlag.BLOCK_BREAK));
        // Stone falls through child's empty rules to parent's permissive boolean.
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void wildernessAllowsEverything() {
        Location loc = locInChunkWithPdc(List.of());
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void rejectsNonBreakOrPlaceFlag() {
        Location loc = locInChunkWithPdc(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, RegionFlag.PVP));
    }

    // ---- mutators ----

    @Test
    void setMaterialsReplacesEntireList() {
        mgr.regions().put("home", new Region("home", OWNER, List.of(),
                Map.of(), Map.of(RegionFlag.ALLOW_BLOCK_BREAK, EnumSet.of(Material.STONE)), null));
        assertTrue(mgr.setMaterials("home", RegionFlag.ALLOW_BLOCK_BREAK,
                Set.of(Material.DIRT, Material.GRASS_BLOCK)));
        Set<Material> after = mgr.regions().get("home").materialsFor(RegionFlag.ALLOW_BLOCK_BREAK);
        assertEquals(EnumSet.of(Material.DIRT, Material.GRASS_BLOCK), after);
    }

    @Test
    void setMaterialsToEmptySetClearsTheEntry() {
        mgr.regions().put("home", new Region("home", OWNER, List.of(),
                Map.of(), Map.of(RegionFlag.ALLOW_BLOCK_BREAK, EnumSet.of(Material.STONE)), null));
        assertTrue(mgr.setMaterials("home", RegionFlag.ALLOW_BLOCK_BREAK, Set.of()));
        assertTrue(mgr.regions().get("home").materialFlags().isEmpty());
    }

    @Test
    void setMaterialsNoOpOnIdenticalSet() {
        mgr.regions().put("home", new Region("home", OWNER, List.of(),
                Map.of(), Map.of(RegionFlag.ALLOW_BLOCK_BREAK, EnumSet.of(Material.STONE)), null));
        assertFalse(mgr.setMaterials("home", RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.STONE)));
    }

    @Test
    void addMaterialsAppendsAndDedupes() {
        mgr.regions().put("home", new Region("home", OWNER, List.of(),
                Map.of(), Map.of(RegionFlag.ALLOW_BLOCK_BREAK, EnumSet.of(Material.STONE)), null));
        assertTrue(mgr.addMaterials("home", RegionFlag.ALLOW_BLOCK_BREAK,
                Set.of(Material.STONE, Material.DIRT)));
        assertEquals(EnumSet.of(Material.STONE, Material.DIRT),
                mgr.regions().get("home").materialsFor(RegionFlag.ALLOW_BLOCK_BREAK));
        assertFalse(mgr.addMaterials("home", RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.STONE)));
    }

    @Test
    void removeMaterialsDropsEntries() {
        mgr.regions().put("home", new Region("home", OWNER, List.of(),
                Map.of(),
                Map.of(RegionFlag.DENY_BLOCK_BREAK, EnumSet.of(Material.BEDROCK, Material.BEACON)),
                null));
        assertTrue(mgr.removeMaterials("home", RegionFlag.DENY_BLOCK_BREAK, Set.of(Material.BEACON)));
        assertEquals(EnumSet.of(Material.BEDROCK),
                mgr.regions().get("home").materialsFor(RegionFlag.DENY_BLOCK_BREAK));
    }

    @Test
    void clearMaterialsRemovesEntry() {
        mgr.regions().put("home", new Region("home", OWNER, List.of(),
                Map.of(), Map.of(RegionFlag.ALLOW_BLOCK_BREAK, EnumSet.of(Material.STONE)), null));
        assertTrue(mgr.clearMaterials("home", RegionFlag.ALLOW_BLOCK_BREAK));
        assertTrue(mgr.regions().get("home").materialsFor(RegionFlag.ALLOW_BLOCK_BREAK).isEmpty());
    }

    @Test
    void setFlagRejectsMaterialFlag() {
        mgr.regions().put("home", new Region("home", OWNER, List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> mgr.setFlag("home", RegionFlag.ALLOW_BLOCK_BREAK, FlagTarget.DEFAULT, true));
    }

    @Test
    void setMaterialsRejectsBooleanFlag() {
        mgr.regions().put("home", new Region("home", OWNER, List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> mgr.setMaterials("home", RegionFlag.BLOCK_BREAK, Set.of(Material.STONE)));
    }
}
