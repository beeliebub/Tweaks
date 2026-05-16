package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.FlagTarget;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SubRegionHierarchyTest {

    private static final UUID PARENT_OWNER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CHILD_OWNER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
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

    private void putRegion(String id, UUID owner, List<UUID> members,
                          Map<RegionFlag, Map<FlagTarget, Boolean>> rules, String parent) {
        mgr.regions().put(id, new Region(id, owner, members, rules, parent));
    }

    // ---- setParent mutator ----

    @Test
    void setParentReturnsUnknownChildForMissingRegion() {
        assertEquals(ProtectionManager.SetParentResult.UNKNOWN_CHILD,
                mgr.setParent("ghost", "anything"));
    }

    @Test
    void setParentReturnsUnknownParentForMissingParent() {
        putRegion("home", PARENT_OWNER, List.of(), Map.of(), null);
        assertEquals(ProtectionManager.SetParentResult.UNKNOWN_PARENT,
                mgr.setParent("home", "ghost"));
    }

    @Test
    void setParentRejectsSelfReference() {
        putRegion("home", PARENT_OWNER, List.of(), Map.of(), null);
        assertEquals(ProtectionManager.SetParentResult.SELF_REFERENCE,
                mgr.setParent("home", "home"));
    }

    @Test
    void setParentRejectsCycles() {
        putRegion("a", PARENT_OWNER, List.of(), Map.of(), null);
        putRegion("b", PARENT_OWNER, List.of(), Map.of(), "a");
        putRegion("c", PARENT_OWNER, List.of(), Map.of(), "b");
        // Try to make 'a' a child of 'c' — that closes the cycle a->b->c->a.
        assertEquals(ProtectionManager.SetParentResult.CYCLE,
                mgr.setParent("a", "c"));
    }

    @Test
    void setParentNoChangeWhenAlreadyAtRequestedParent() {
        putRegion("parent", PARENT_OWNER, List.of(), Map.of(), null);
        putRegion("child", CHILD_OWNER, List.of(), Map.of(), "parent");
        assertEquals(ProtectionManager.SetParentResult.NO_CHANGE,
                mgr.setParent("child", "parent"));
    }

    @Test
    void setParentSwapsPointerAndPreservesEverythingElse() {
        Map<RegionFlag, Map<FlagTarget, Boolean>> rules =
                Map.of(RegionFlag.PVP, Map.of(FlagTarget.DEFAULT, true));
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(), Map.of()));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(OUTSIDER), rules));

        assertEquals(ProtectionManager.SetParentResult.OK,
                mgr.setParent("child", "parent"));

        Region updated = mgr.regions().get("child");
        assertEquals("parent", updated.parentId());
        assertEquals(CHILD_OWNER, updated.owner());
        assertEquals(List.of(OUTSIDER), updated.members());
        assertEquals(Boolean.TRUE, updated.rulesFor(RegionFlag.PVP).get(FlagTarget.DEFAULT));
    }

    @Test
    void unsetParentReturnsRegionToTopLevel() {
        putRegion("parent", PARENT_OWNER, List.of(), Map.of(), null);
        putRegion("child", CHILD_OWNER, List.of(), Map.of(), "parent");
        assertEquals(ProtectionManager.SetParentResult.OK,
                mgr.setParent("child", null));
        assertNull(mgr.regions().get("child").parentId());
    }

    // ---- isAllowed with hierarchy ----

    @Test
    void childInheritsFlagFromParentWhenChildIsSilent() {
        putRegion("parent", PARENT_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, true)),
                null);
        putRegion("child", CHILD_OWNER, List.of(), Map.of(), "parent");

        // PDC at this loc references BOTH parent and child (overlapping claim).
        // After leaf-filtering only 'child' is considered, and its empty rule
        // set falls through to parent.
        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertTrue(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void childOverridesParentWhenItSpecifiesAFlag() {
        putRegion("parent", PARENT_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, true)),
                null);
        putRegion("child", CHILD_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, false)),
                "parent");

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void leafFilteringIgnoresParentWhenChildIsAlsoPresent() {
        // Parent is permissive on its own DEFAULT, but child wants outsider blocked.
        putRegion("parent", PARENT_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, true)),
                null);
        putRegion("child", CHILD_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, false)),
                "parent");

        // Even though parent allows, leaf filter throws out parent in favor of child.
        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void roleIsReEvaluatedAtEachLevelOfTheChain() {
        // Member of parent but not child.
        // Child has no rule -> falls to parent where DEFAULT=false but isMember=true.
        // resolveFlag at parent sees no MEMBER rule, sees DEFAULT=false, returns false.
        // Result: blocked.
        putRegion("parent", PARENT_OWNER, List.of(OUTSIDER),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, false)),
                null);
        putRegion("child", CHILD_OWNER, List.of(), Map.of(), "parent");

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void leafLegacyDefaultUsesLeafRoles() {
        // No rules anywhere. Outsider is a member of parent but not child.
        // Leaf is child; outsider is NOT a member of child; legacy default blocks.
        putRegion("parent", PARENT_OWNER, List.of(OUTSIDER), Map.of(), null);
        putRegion("child", CHILD_OWNER, List.of(), Map.of(), "parent");

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void unrelatedOverlappingLeavesStillIntersect() {
        // Two unrelated leaves at same location, no parent relation.
        // Player is member of A but not B. A permits via membership; B blocks.
        putRegion("a", PARENT_OWNER, List.of(OUTSIDER), Map.of(), null);
        putRegion("b", PARENT_OWNER, List.of(), Map.of(), null);

        Location loc = locInChunkWithPdc(List.of("a", "b"));
        // A: outsider is a member -> legacy default permits.
        // B: outsider is NOT a member -> legacy default blocks.
        // Both must permit -> blocked.
        assertFalse(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void deepHierarchyChainWalksUntilMatch() {
        // grandparent allows; parent and child are silent.
        putRegion("grandparent", PARENT_OWNER, List.of(),
                Map.of(RegionFlag.PVP, Map.of(FlagTarget.DEFAULT, true)),
                null);
        putRegion("parent", PARENT_OWNER, List.of(), Map.of(), "grandparent");
        putRegion("child", CHILD_OWNER, List.of(), Map.of(), "parent");

        Location loc = locInChunkWithPdc(List.of("grandparent", "parent", "child"));
        assertTrue(mgr.isAllowed(loc, OUTSIDER, RegionFlag.PVP));
    }
}
