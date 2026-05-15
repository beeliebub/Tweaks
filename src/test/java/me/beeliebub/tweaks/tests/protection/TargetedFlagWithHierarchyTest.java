package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.UserPermissions;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Role/group targeted rules walking the parent chain. The key wrinkle here is
// that membership and ownership are re-evaluated at each link, because a
// player can be the owner of a sub-region without belonging to its parent
// (and vice versa). These tests pin down which rule fires at which level so
// future reorderings of the resolution chain can't silently change verdicts.
class TargetedFlagWithHierarchyTest {

    private static final UUID PARENT_OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CHILD_OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PARENT_MEMBER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OUTSIDER = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @BeforeAll
    static void initKeys() {
        try (MockedConstruction<NamespacedKey> ignored = mockConstruction(NamespacedKey.class)) {
            ProtectionKeys.init(mock(Tweaks.class));
        }
    }

    private Tweaks plugin;
    private PermissionManager permissions;
    private ProtectionManager mgr;

    @BeforeEach
    void newManager() {
        plugin = mock(Tweaks.class);
        permissions = mock(PermissionManager.class);
        when(plugin.getPermissionManager()).thenReturn(permissions);
        when(permissions.getUsers()).thenReturn(new HashMap<>());
        mgr = new ProtectionManager(plugin);
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

    private void givePlayerGroups(UUID uuid, String... groupNames) {
        UserPermissions up = mock(UserPermissions.class);
        when(up.getGroups()).thenReturn(Set.of(groupNames));
        Map<UUID, UserPermissions> users = new HashMap<>();
        users.put(uuid, up);
        when(permissions.getUsers()).thenReturn(users);
    }

    // -------- Role re-evaluation at each link --------

    @Test
    void childOwnerRuleAppliesEvenWhenActorIsNotParentMember() {
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, false))));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.OWNER, true)),
                "parent"));

        // CHILD_OWNER is owner of child only. The child OWNER=true rule fires
        // before the chain reaches parent's restrictive DEFAULT.
        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertTrue(mgr.isAllowed(loc, CHILD_OWNER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void childSilentParentOwnerRuleAppliesAcrossChain() {
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(),
                Map.of(RegionFlag.PVP, Map.of(FlagTarget.OWNER, false))));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(), Map.of(), "parent"));

        // PARENT_OWNER is owner of parent, not child. Child has no rule, climbs
        // to parent where OWNER=false applies.
        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isAllowed(loc, PARENT_OWNER, RegionFlag.PVP));
    }

    @Test
    void childOwnerRuleMissForNonOwnerFallsThroughToParent() {
        // Child has BLOCK_BREAK[OWNER]=true but the actor is parent's owner,
        // not the child's owner. Child's rule misses -> climb -> parent rule.
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, false))));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.OWNER, true)),
                "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isAllowed(loc, PARENT_OWNER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void memberRoleEvaluatedPerLink() {
        // PARENT_MEMBER is a member of parent only. At child the rule on
        // MEMBER doesn't apply (PARENT_MEMBER isn't a child member); climb to
        // parent where MEMBER=true fires.
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(PARENT_MEMBER),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.MEMBER, true))));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.MEMBER, false)),
                "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertTrue(mgr.isAllowed(loc, PARENT_MEMBER, RegionFlag.BLOCK_BREAK));
    }

    // -------- Group rules across hierarchy --------

    @Test
    void groupRuleOnParentAppliesIfChildSilent() {
        givePlayerGroups(OUTSIDER, "staff");
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(
                        FlagTarget.DEFAULT, false,
                        FlagTarget.group("staff"), true))));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(), Map.of(), "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertTrue(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void groupRuleOnChildBeatsParentGroupRule() {
        givePlayerGroups(OUTSIDER, "staff");
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.group("staff"), true))));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.group("staff"), false)),
                "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void groupRuleOnNonMatchingGroupFallsThroughToParent() {
        givePlayerGroups(OUTSIDER, "trusted");
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, true))));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.group("staff"), false)),
                "parent"));

        // OUTSIDER is in "trusted" not "staff" -> child rule doesn't match;
        // climb to parent where DEFAULT=true applies.
        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertTrue(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void allowingGroupRuleAtChildBeatsDenyingGroupAtParent() {
        givePlayerGroups(OUTSIDER, "muted");
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(),
                Map.of(RegionFlag.PVP, Map.of(FlagTarget.group("muted"), false))));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(),
                Map.of(RegionFlag.PVP, Map.of(FlagTarget.group("muted"), true)),
                "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertTrue(mgr.isAllowed(loc, OUTSIDER, RegionFlag.PVP));
    }

    @Test
    void multipleGroupRulesAtSameLevelAllowWins() {
        givePlayerGroups(OUTSIDER, "muted", "vip");
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(),
                Map.of(RegionFlag.PVP, Map.of(
                        FlagTarget.group("muted"), false,
                        FlagTarget.group("vip"), true))));

        Location loc = locInChunkWithPdc(List.of("parent"));
        assertTrue(mgr.isAllowed(loc, OUTSIDER, RegionFlag.PVP));
    }

    // -------- Targeted rule + material list intermix --------

    @Test
    void materialDenyAtParentWinsOverGroupAllowAtChildWhenChildIsSilent() {
        // Defensive: child has nothing about block break. Parent's DENY list
        // catches DIAMOND_ORE for everyone, including staff.
        givePlayerGroups(OUTSIDER, "staff");
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(
                        FlagTarget.DEFAULT, true,
                        FlagTarget.group("staff"), true)),
                Map.of(RegionFlag.DENY_BLOCK_BREAK, Set.of(org.bukkit.Material.DIAMOND_ORE)),
                null));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(),
                Map.of(), Map.of(), "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER,
                org.bukkit.Material.DIAMOND_ORE, RegionFlag.BLOCK_BREAK));
        // Non-diamond falls past the list to the staff group rule.
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER,
                org.bukkit.Material.STONE, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void childBooleanRuleBeatsParentMaterialAllow() {
        givePlayerGroups(OUTSIDER, "staff");
        // Parent says staff may always break, AND specifically allows stone for everyone.
        // Child says no one breaks. Child's boolean wins for staff at the child link.
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.group("staff"), true)),
                Map.of(RegionFlag.ALLOW_BLOCK_BREAK, Set.of(org.bukkit.Material.STONE)),
                null));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, false)),
                Map.of(), "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        // Stone material check: child's material list is empty -> reaches boolean.
        // Child's BLOCK_BREAK rule: group "staff" not specified, only DEFAULT=false.
        // DEFAULT applies -> deny. Parent never consulted.
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER,
                org.bukkit.Material.STONE, RegionFlag.BLOCK_BREAK));
    }

    // -------- Legacy default uses leaf's membership --------

    @Test
    void legacyDefaultUsesLeafMembershipNotParentMembership() {
        // Player is a member of parent but not child. No rules anywhere.
        // Legacy default at leaf: child membership = false -> deny.
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(OUTSIDER),
                Map.of()));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(),
                Map.of(), "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void legacyDefaultLeafMemberAllowed() {
        // Player is a member of child only. Legacy default at leaf permits.
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(),
                Map.of()));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(OUTSIDER),
                Map.of(), "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertTrue(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }
}
