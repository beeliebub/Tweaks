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

// Documents the conflict-resolution rules when an admin sets contradictory
// flag values, both within a single region and across a parent/child chain.
// Pinning these down protects against future "fixes" that might silently
// reorder precedence and break server-admin config that relied on the old
// behavior.
class ContradictoryFlagsTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OUTSIDER = UUID.fromString("99999999-9999-9999-9999-999999999999");

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

    // -------- Material list internal conflict (ALLOW + DENY on same material) --------

    @Test
    void allowAndDenyOnSameMaterialDenyWinsForBreak() {
        // Documented contract: DENY > ALLOW. Safer default for accidental
        // admin config where they list the same block under both keys.
        mgr.regions().put("home", new Region("home", OWNER, List.of(), Map.of(),
                Map.of(
                        RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.DIRT),
                        RegionFlag.DENY_BLOCK_BREAK, Set.of(Material.DIRT)),
                null));
        Location loc = locInChunkWithPdc(List.of("home"));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.DIRT, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void allowAndDenyOnSameMaterialDenyWinsForPlace() {
        mgr.regions().put("home", new Region("home", OWNER, List.of(), Map.of(),
                Map.of(
                        RegionFlag.ALLOW_BLOCK_PLACE, Set.of(Material.OAK_PLANKS),
                        RegionFlag.DENY_BLOCK_PLACE, Set.of(Material.OAK_PLANKS)),
                null));
        Location loc = locInChunkWithPdc(List.of("home"));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.OAK_PLANKS, RegionFlag.BLOCK_PLACE));
    }

    @Test
    void allowAndDenyDisjointMaterialsBothApply() {
        mgr.regions().put("home", new Region("home", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, false)),
                Map.of(
                        RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.GRASS_BLOCK),
                        RegionFlag.DENY_BLOCK_BREAK, Set.of(Material.BEDROCK)),
                null));
        Location loc = locInChunkWithPdc(List.of("home"));

        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.GRASS_BLOCK, RegionFlag.BLOCK_BREAK));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.BEDROCK, RegionFlag.BLOCK_BREAK));
        // Unlisted: falls through to BLOCK_BREAK=false -> deny.
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, RegionFlag.BLOCK_BREAK));
    }

    // -------- Material list vs base boolean flag --------

    @Test
    void allowListOverridesPermissiveBooleanIsNoopForUnlistedMaterials() {
        // Permissive boolean already lets everything through; ALLOW list is
        // redundant but must not flip stone to deny.
        mgr.regions().put("home", new Region("home", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, true)),
                Map.of(RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.GRASS_BLOCK)),
                null));
        Location loc = locInChunkWithPdc(List.of("home"));
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.GRASS_BLOCK, RegionFlag.BLOCK_BREAK));
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void denyListOverridesPermissiveBooleanOnlyForListedMaterial() {
        mgr.regions().put("home", new Region("home", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, true)),
                Map.of(RegionFlag.DENY_BLOCK_BREAK, Set.of(Material.DIAMOND_ORE)),
                null));
        Location loc = locInChunkWithPdc(List.of("home"));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.DIAMOND_ORE, RegionFlag.BLOCK_BREAK));
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void allowListOverridesRestrictiveBooleanOnlyForListedMaterial() {
        mgr.regions().put("home", new Region("home", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, false)),
                Map.of(RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.GRASS_BLOCK, Material.DIRT)),
                null));
        Location loc = locInChunkWithPdc(List.of("home"));
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.GRASS_BLOCK, RegionFlag.BLOCK_BREAK));
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.DIRT, RegionFlag.BLOCK_BREAK));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, RegionFlag.BLOCK_BREAK));
    }

    // -------- Material conflicts across hierarchy (parent vs child) --------

    @Test
    void childDenyOverridesParentAllowForSameMaterial() {
        mgr.regions().put("parent", new Region("parent", OWNER, List.of(), Map.of(),
                Map.of(RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.DIRT)),
                null));
        mgr.regions().put("child", new Region("child", OWNER, List.of(), Map.of(),
                Map.of(RegionFlag.DENY_BLOCK_BREAK, Set.of(Material.DIRT)),
                "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.DIRT, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void childAllowOverridesParentDenyForSameMaterial() {
        mgr.regions().put("parent", new Region("parent", OWNER, List.of(), Map.of(),
                Map.of(RegionFlag.DENY_BLOCK_BREAK, Set.of(Material.DIRT)),
                null));
        mgr.regions().put("child", new Region("child", OWNER, List.of(), Map.of(),
                Map.of(RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.DIRT)),
                "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.DIRT, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void childMaterialListInheritsForUnlistedMaterialsFromParent() {
        // Child says nothing about stone; parent's DENY catches it.
        mgr.regions().put("parent", new Region("parent", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, true)),
                Map.of(RegionFlag.DENY_BLOCK_BREAK, Set.of(Material.STONE)),
                null));
        mgr.regions().put("child", new Region("child", OWNER, List.of(), Map.of(),
                Map.of(RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.DIRT)),
                "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.DIRT, RegionFlag.BLOCK_BREAK),
                "child ALLOW grants dirt");
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, RegionFlag.BLOCK_BREAK),
                "child silent on stone -> parent DENY applies");
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.GRAVEL, RegionFlag.BLOCK_BREAK),
                "neither list mentions gravel -> parent's permissive boolean applies");
    }

    @Test
    void childBooleanOverridesParentMaterialList() {
        // Parent specifically allows stone. Child slams BLOCK_BREAK=false.
        // The child's BOOLEAN rule matches before the chain reaches the
        // parent's material list, so stone is denied.
        mgr.regions().put("parent", new Region("parent", OWNER, List.of(), Map.of(),
                Map.of(RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.STONE)),
                null));
        mgr.regions().put("child", new Region("child", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, false)),
                Map.of(), "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void parentMaterialListBeatsChildSilentBooleanAtBlockLevel() {
        // Child has no rules at all -> its block-action chain reaches the
        // parent unchanged. Parent's DENY list catches stone.
        mgr.regions().put("parent", new Region("parent", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, true)),
                Map.of(RegionFlag.DENY_BLOCK_BREAK, Set.of(Material.STONE)),
                null));
        mgr.regions().put("child", new Region("child", OWNER, List.of(), Map.of(),
                Map.of(), "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, RegionFlag.BLOCK_BREAK));
    }

    // -------- Material flag mutators reject mismatched flag types --------

    @Test
    void cannotPutBooleanRuleOnMaterialFlag() {
        mgr.regions().put("home", new Region("home", OWNER, List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> mgr.setFlag("home", RegionFlag.ALLOW_BLOCK_BREAK, FlagTarget.DEFAULT, true));
    }

    @Test
    void cannotPutMaterialListOnBooleanFlag() {
        mgr.regions().put("home", new Region("home", OWNER, List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> mgr.setMaterials("home", RegionFlag.BLOCK_BREAK, Set.of(Material.STONE)));
    }

    @Test
    void regionConstructorIgnoresMaterialEntriesOnNonMaterialFlags() {
        // Defensive: even if an EnumMap shoves a boolean flag into the
        // materialFlags arg, the canonical constructor filters it out.
        Region r = new Region("home", OWNER, List.of(), Map.of(),
                Map.of(RegionFlag.PVP, EnumSet.of(Material.STONE)),
                null);
        assertTrue(r.materialFlags().isEmpty());
    }

    // -------- isAllowed vs isBlockActionAllowed: material flags are ignored
    // by the plain boolean path. --------

    @Test
    void plainIsAllowedIgnoresMaterialLists() {
        // BLOCK_BREAK=false at DEFAULT; ALLOW list grants stone. But plain
        // isAllowed (no material context) only checks the boolean — block.
        mgr.regions().put("home", new Region("home", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, false)),
                Map.of(RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.STONE)),
                null));
        Location loc = locInChunkWithPdc(List.of("home"));
        assertFalse(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
        // While isBlockActionAllowed with stone DOES honor the material list.
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, RegionFlag.BLOCK_BREAK));
    }
}
