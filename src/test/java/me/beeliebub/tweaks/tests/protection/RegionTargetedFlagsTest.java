package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.protection.FlagTarget;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RegionTargetedFlagsTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static Region withRule(RegionFlag flag, FlagTarget target, boolean value) {
        Map<RegionFlag, Map<FlagTarget, Boolean>> rules = new HashMap<>();
        rules.put(flag, Map.of(target, value));
        return new Region("r", OWNER, List.of(), rules);
    }

    @Test
    void resolveFlagReturnsEmptyWhenNoRules() {
        Region r = new Region("r", OWNER, List.of(), Map.of());
        assertTrue(r.resolveFlag(RegionFlag.BLOCK_BREAK, true, true, Set.of()).isEmpty());
    }

    @Test
    void resolveFlagPicksDefaultWhenOnlyDefaultSet() {
        Region r = withRule(RegionFlag.BLOCK_BREAK, FlagTarget.DEFAULT, true);
        assertEquals(Optional.of(true),
                r.resolveFlag(RegionFlag.BLOCK_BREAK, false, false, Set.of()));
    }

    @Test
    void ownerRuleOverridesDefaultForOwner() {
        Map<RegionFlag, Map<FlagTarget, Boolean>> rules = Map.of(
                RegionFlag.BLOCK_BREAK, Map.of(
                        FlagTarget.DEFAULT, true,
                        FlagTarget.OWNER, false));
        Region r = new Region("r", OWNER, List.of(), rules);
        assertEquals(Optional.of(false),
                r.resolveFlag(RegionFlag.BLOCK_BREAK, true, true, Set.of()));
        // Non-owner still sees DEFAULT.
        assertEquals(Optional.of(true),
                r.resolveFlag(RegionFlag.BLOCK_BREAK, false, false, Set.of()));
    }

    @Test
    void memberRuleOverridesDefaultForMember() {
        Map<RegionFlag, Map<FlagTarget, Boolean>> rules = Map.of(
                RegionFlag.PVP, Map.of(
                        FlagTarget.DEFAULT, false,
                        FlagTarget.MEMBER, true));
        Region r = new Region("r", OWNER, List.of(), rules);
        assertEquals(Optional.of(true),
                r.resolveFlag(RegionFlag.PVP, false, true, Set.of()));
        assertEquals(Optional.of(false),
                r.resolveFlag(RegionFlag.PVP, false, false, Set.of()));
    }

    @Test
    void groupRuleWinsOverRoleAndDefault() {
        Map<RegionFlag, Map<FlagTarget, Boolean>> rules = Map.of(
                RegionFlag.BLOCK_BREAK, Map.of(
                        FlagTarget.DEFAULT, false,
                        FlagTarget.OWNER, false,
                        FlagTarget.group("staff"), true));
        Region r = new Region("r", OWNER, List.of(), rules);
        assertEquals(Optional.of(true),
                r.resolveFlag(RegionFlag.BLOCK_BREAK, true, true, Set.of("staff")));
        // Outside the group, OWNER takes over.
        assertEquals(Optional.of(false),
                r.resolveFlag(RegionFlag.BLOCK_BREAK, true, true, Set.of("trusted")));
    }

    @Test
    void allowingGroupRuleWinsAcrossMultipleMatchingGroups() {
        Map<RegionFlag, Map<FlagTarget, Boolean>> rules = Map.of(
                RegionFlag.BLOCK_BREAK, Map.of(
                        FlagTarget.group("muted"), false,
                        FlagTarget.group("vip"), true));
        Region r = new Region("r", OWNER, List.of(), rules);
        assertEquals(Optional.of(true),
                r.resolveFlag(RegionFlag.BLOCK_BREAK, false, false, Set.of("muted", "vip")));
    }

    @Test
    void denyingGroupRulePersistsWhenNoAllowingGroupMatches() {
        Map<RegionFlag, Map<FlagTarget, Boolean>> rules = Map.of(
                RegionFlag.BLOCK_BREAK, Map.of(
                        FlagTarget.DEFAULT, true,
                        FlagTarget.group("muted"), false));
        Region r = new Region("r", OWNER, List.of(), rules);
        assertEquals(Optional.of(false),
                r.resolveFlag(RegionFlag.BLOCK_BREAK, false, false, Set.of("muted")));
    }

    @Test
    void ownerFallsThroughToMemberWhenNoOwnerRule() {
        Map<RegionFlag, Map<FlagTarget, Boolean>> rules = Map.of(
                RegionFlag.PVP, Map.of(
                        FlagTarget.MEMBER, true));
        Region r = new Region("r", OWNER, List.of(), rules);
        assertEquals(Optional.of(true),
                r.resolveFlag(RegionFlag.PVP, true, true, Set.of()));
    }

    @Test
    void hasFlagOnlyReadsDefaultTrueRule() {
        Region defaultTrue = withRule(RegionFlag.PVP, FlagTarget.DEFAULT, true);
        assertTrue(defaultTrue.hasFlag(RegionFlag.PVP));

        Region defaultFalse = withRule(RegionFlag.PVP, FlagTarget.DEFAULT, false);
        assertFalse(defaultFalse.hasFlag(RegionFlag.PVP));

        Region ownerTrue = withRule(RegionFlag.PVP, FlagTarget.OWNER, true);
        assertFalse(ownerTrue.hasFlag(RegionFlag.PVP));
    }

    @Test
    void rulesForReturnsImmutableViewAndEmptyForUnsetFlag() {
        Map<RegionFlag, Map<FlagTarget, Boolean>> rules = Map.of(
                RegionFlag.PVP, Map.of(FlagTarget.DEFAULT, true));
        Region r = new Region("r", OWNER, List.of(), rules);

        assertTrue(r.rulesFor(RegionFlag.EXPLOSION).isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> r.rulesFor(RegionFlag.PVP).put(FlagTarget.OWNER, false));
    }

    @Test
    void flagRulesIsDeepImmutable() {
        Map<RegionFlag, Map<FlagTarget, Boolean>> source = new HashMap<>();
        Map<FlagTarget, Boolean> inner = new HashMap<>();
        inner.put(FlagTarget.DEFAULT, true);
        source.put(RegionFlag.PVP, inner);
        Region r = new Region("r", OWNER, List.of(), source);

        // Outer mutation cannot leak.
        source.clear();
        assertTrue(r.flagRules().containsKey(RegionFlag.PVP));

        // Inner mutation cannot leak.
        inner.put(FlagTarget.OWNER, false);
        assertFalse(r.flagRules().get(RegionFlag.PVP).containsKey(FlagTarget.OWNER));
    }

    @Test
    void withFlagRuleAddsAndRemovesAtomically() {
        Region r = new Region("r", OWNER, List.of(), Map.of());
        Region added = r.withFlagRule(RegionFlag.PVP, FlagTarget.DEFAULT, true);
        assertEquals(Boolean.TRUE, added.rulesFor(RegionFlag.PVP).get(FlagTarget.DEFAULT));
        // Original instance unchanged.
        assertTrue(r.rulesFor(RegionFlag.PVP).isEmpty());

        Region cleared = added.withFlagRule(RegionFlag.PVP, FlagTarget.DEFAULT, null);
        assertTrue(cleared.rulesFor(RegionFlag.PVP).isEmpty());
        assertTrue(cleared.flagRules().isEmpty(),
                "removing the only rule for a flag drops the flag entry too");
    }

    @Test
    void withFlagRuleRetainsSiblingTargets() {
        Region r = new Region("r", OWNER, List.of(),
                Map.of(RegionFlag.PVP, Map.of(
                        FlagTarget.DEFAULT, true,
                        FlagTarget.OWNER, false)));
        Region updated = r.withFlagRule(RegionFlag.PVP, FlagTarget.MEMBER, true);
        Map<FlagTarget, Boolean> rules = updated.rulesFor(RegionFlag.PVP);
        assertEquals(Boolean.TRUE, rules.get(FlagTarget.DEFAULT));
        assertEquals(Boolean.FALSE, rules.get(FlagTarget.OWNER));
        assertEquals(Boolean.TRUE, rules.get(FlagTarget.MEMBER));
    }
}
