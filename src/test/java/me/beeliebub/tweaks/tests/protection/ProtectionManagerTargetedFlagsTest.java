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

class ProtectionManagerTargetedFlagsTest {

    private static final UUID OWNER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MEMBER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OUTSIDER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

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

    private void putRegion(String id, UUID owner, List<UUID> members,
                          Map<RegionFlag, Map<FlagTarget, Boolean>> rules) {
        mgr.regions().put(id, new Region(id, owner, members, rules));
    }

    private void givePlayerGroups(UUID uuid, String... groupNames) {
        UserPermissions up = mock(UserPermissions.class);
        when(up.getGroups()).thenReturn(Set.of(groupNames));
        Map<UUID, UserPermissions> users = new HashMap<>();
        users.put(uuid, up);
        when(permissions.getUsers()).thenReturn(users);
    }

    // -------- setFlag / removeFlag mechanics --------

    @Test
    void setFlagWithTargetWritesRuleAndReturnsTrue() {
        putRegion("home", OWNER, List.of(), Map.of());
        assertTrue(mgr.setFlag("home", RegionFlag.BLOCK_BREAK, FlagTarget.OWNER, false));
        Region r = mgr.regions().get("home");
        assertEquals(Boolean.FALSE, r.rulesFor(RegionFlag.BLOCK_BREAK).get(FlagTarget.OWNER));
    }

    @Test
    void setFlagIsNoOpWhenValueUnchanged() {
        putRegion("home", OWNER, List.of(),
                Map.of(RegionFlag.PVP, Map.of(FlagTarget.DEFAULT, true)));
        assertFalse(mgr.setFlag("home", RegionFlag.PVP, FlagTarget.DEFAULT, true));
    }

    @Test
    void setFlagOnUnknownRegionReturnsFalse() {
        assertFalse(mgr.setFlag("ghost", RegionFlag.PVP, FlagTarget.DEFAULT, true));
    }

    @Test
    void removeFlagDropsRuleAndReturnsTrue() {
        putRegion("home", OWNER, List.of(),
                Map.of(RegionFlag.PVP, Map.of(
                        FlagTarget.DEFAULT, true,
                        FlagTarget.OWNER, false)));
        assertTrue(mgr.removeFlag("home", RegionFlag.PVP, FlagTarget.OWNER));
        Region r = mgr.regions().get("home");
        assertFalse(r.rulesFor(RegionFlag.PVP).containsKey(FlagTarget.OWNER));
        assertEquals(Boolean.TRUE, r.rulesFor(RegionFlag.PVP).get(FlagTarget.DEFAULT));
    }

    @Test
    void removeFlagIsNoOpWhenRuleAbsent() {
        putRegion("home", OWNER, List.of(),
                Map.of(RegionFlag.PVP, Map.of(FlagTarget.DEFAULT, true)));
        assertFalse(mgr.removeFlag("home", RegionFlag.PVP, FlagTarget.OWNER));
    }

    @Test
    void removingTheLastRuleClearsTheFlagEntry() {
        putRegion("home", OWNER, List.of(),
                Map.of(RegionFlag.PVP, Map.of(FlagTarget.DEFAULT, true)));
        assertTrue(mgr.removeFlag("home", RegionFlag.PVP, FlagTarget.DEFAULT));
        assertTrue(mgr.regions().get("home").flagRules().isEmpty());
    }

    // -------- isAllowed integration with permission groups --------

    @Test
    void groupRuleGrantsAccessOverDefaultDeny() {
        putRegion("spawn", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(
                        FlagTarget.DEFAULT, false,
                        FlagTarget.group("staff"), true)));
        givePlayerGroups(OUTSIDER, "staff");

        Location loc = locInChunkWithPdc(List.of("spawn"));
        assertTrue(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void groupRuleDeniesOwnerWhenOwnerIsAlsoInGroup() {
        putRegion("spawn", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(
                        FlagTarget.group("muted"), false)));
        givePlayerGroups(OWNER, "muted");

        Location loc = locInChunkWithPdc(List.of("spawn"));
        // Even though OWNER is the region owner, the matching group rule fires
        // before the role fallback. Result: denied.
        assertFalse(mgr.isAllowed(loc, OWNER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void ownerRuleDeniesEvenTheOwner() {
        putRegion("museum", OWNER, List.of(MEMBER),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.OWNER, false)));
        Location loc = locInChunkWithPdc(List.of("museum"));
        assertFalse(mgr.isAllowed(loc, OWNER, RegionFlag.BLOCK_BREAK));
        // Member has no matching rule and falls through to the legacy default:
        // members allowed.
        assertTrue(mgr.isAllowed(loc, MEMBER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void memberRuleOverridesLegacyDefault() {
        putRegion("museum", OWNER, List.of(MEMBER),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.MEMBER, false)));
        Location loc = locInChunkWithPdc(List.of("museum"));
        assertFalse(mgr.isAllowed(loc, MEMBER, RegionFlag.BLOCK_BREAK));
        // OWNER falls through MEMBER too (owner counts as member).
        assertFalse(mgr.isAllowed(loc, OWNER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void nullActorOnlyConsultsDefaultRule() {
        putRegion("park", OWNER, List.of(),
                Map.of(RegionFlag.EXPLOSION, Map.of(
                        FlagTarget.DEFAULT, true,
                        FlagTarget.OWNER, false)));
        Location loc = locInChunkWithPdc(List.of("park"));
        // Null actor cannot match OWNER, falls to DEFAULT.
        assertTrue(mgr.isAllowed(loc, null, RegionFlag.EXPLOSION));
    }

    @Test
    void legacyDefaultStillBlocksOutsiderWhenNoRulesAtAll() {
        putRegion("home", OWNER, List.of(MEMBER), Map.of());
        Location loc = locInChunkWithPdc(List.of("home"));
        assertFalse(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
        assertTrue(mgr.isAllowed(loc, OWNER, RegionFlag.BLOCK_BREAK));
        assertTrue(mgr.isAllowed(loc, MEMBER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void unknownPlayerImplicitlyInDefaultGroup() {
        // No user record at all -> groupsOf returns {"default"}.
        putRegion("park", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(
                        FlagTarget.group("default"), true)));
        Location loc = locInChunkWithPdc(List.of("park"));
        assertTrue(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }
}
