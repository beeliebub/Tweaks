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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedConstruction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Exhaustive parent-vs-child verdict matrix for every boolean flag. Each row
// in the @MethodSource declares the parent's DEFAULT rule, the child's
// DEFAULT rule, and what isAllowed must return for an OUTSIDER actor.
//
// Why DEFAULT-targeted rules: this test fixes the inheritance contract on the
// most common case (a catch-all rule on the region). Role/group interactions
// have their own dedicated suite — see TargetedFlagWithHierarchyTest. Mixing
// both axes in one matrix would obscure which rule produced each verdict.
//
// The cross-product is small enough (8 flags × 9 verdict cases) that we run
// every combination rather than spot-checking representative flags. That way
// adding a new boolean flag immediately surfaces any listener handler that
// doesn't honor the standard resolution chain.
class FlagInheritanceMatrixTest {

    private static final UUID PARENT_OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CHILD_OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
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

    // Three-valued rule state: null = no rule, true/false = explicit verdict.
    private static Map<RegionFlag, Map<FlagTarget, Boolean>> ruleFor(RegionFlag flag, Boolean defaultValue) {
        if (defaultValue == null) return Map.of();
        Map<RegionFlag, Map<FlagTarget, Boolean>> out = new HashMap<>();
        out.put(flag, Map.of(FlagTarget.DEFAULT, defaultValue));
        return out;
    }

    // ---- Standalone parent: no child involved. ----

    @ParameterizedTest(name = "[parent only] {0} default=true permits outsider")
    @EnumSource(value = RegionFlag.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"ALLOW_BLOCK_BREAK", "DENY_BLOCK_BREAK", "ALLOW_BLOCK_PLACE", "DENY_BLOCK_PLACE"})
    void parentDefaultTruePermitsOutsider(RegionFlag flag) {
        mgr.regions().put("home", new Region("home", PARENT_OWNER, List.of(),
                ruleFor(flag, true)));
        Location loc = locInChunkWithPdc(List.of("home"));
        assertTrue(mgr.isAllowed(loc, OUTSIDER, flag));
    }

    @ParameterizedTest(name = "[parent only] {0} default=false denies outsider")
    @EnumSource(value = RegionFlag.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"ALLOW_BLOCK_BREAK", "DENY_BLOCK_BREAK", "ALLOW_BLOCK_PLACE", "DENY_BLOCK_PLACE"})
    void parentDefaultFalseDeniesOutsider(RegionFlag flag) {
        mgr.regions().put("home", new Region("home", PARENT_OWNER, List.of(),
                ruleFor(flag, false)));
        Location loc = locInChunkWithPdc(List.of("home"));
        assertFalse(mgr.isAllowed(loc, OUTSIDER, flag));
    }

    @ParameterizedTest(name = "[parent only] {0} silent rule denies outsider via legacy default")
    @EnumSource(value = RegionFlag.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"ALLOW_BLOCK_BREAK", "DENY_BLOCK_BREAK", "ALLOW_BLOCK_PLACE", "DENY_BLOCK_PLACE"})
    void parentSilentDeniesOutsider(RegionFlag flag) {
        mgr.regions().put("home", new Region("home", PARENT_OWNER, List.of(),
                ruleFor(flag, null)));
        Location loc = locInChunkWithPdc(List.of("home"));
        assertFalse(mgr.isAllowed(loc, OUTSIDER, flag));
    }

    @ParameterizedTest(name = "[parent only] {0} silent rule permits member via legacy default")
    @EnumSource(value = RegionFlag.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"ALLOW_BLOCK_BREAK", "DENY_BLOCK_BREAK", "ALLOW_BLOCK_PLACE", "DENY_BLOCK_PLACE"})
    void parentSilentPermitsOwner(RegionFlag flag) {
        mgr.regions().put("home", new Region("home", PARENT_OWNER, List.of(),
                ruleFor(flag, null)));
        Location loc = locInChunkWithPdc(List.of("home"));
        assertTrue(mgr.isAllowed(loc, PARENT_OWNER, flag));
    }

    // ---- Sub-region inheritance matrix. ----

    // Each row: parent default rule, child default rule, expected verdict for OUTSIDER.
    // null = the region has no rule on this flag (silent).
    private static Stream<Arguments> inheritanceCases() {
        return Stream.of(
                Arguments.of(null,  null,  false, "both silent -> legacy default for non-member at leaf"),
                Arguments.of(true,  null,  true,  "child silent inherits parent true"),
                Arguments.of(false, null,  false, "child silent inherits parent false"),
                Arguments.of(null,  true,  true,  "child true overrides parent silence"),
                Arguments.of(null,  false, false, "child false overrides parent silence"),
                Arguments.of(true,  false, false, "child false beats parent true"),
                Arguments.of(false, true,  true,  "child true beats parent false"),
                Arguments.of(true,  true,  true,  "matching agreements"),
                Arguments.of(false, false, false, "matching denies")
        );
    }

    private static Stream<Arguments> inheritanceCasesAcrossAllBooleanFlags() {
        return Stream.of(
                        RegionFlag.BLOCK_BREAK, RegionFlag.BLOCK_PLACE, RegionFlag.CONTAINER_ACCESS,
                        RegionFlag.INTERACT, RegionFlag.REDSTONE, RegionFlag.EXPLOSION,
                        RegionFlag.PVP, RegionFlag.MOB_GRIEFING)
                .flatMap(flag -> inheritanceCases().map(args -> Arguments.of(
                        flag, args.get()[0], args.get()[1], args.get()[2], args.get()[3])));
    }

    @ParameterizedTest(name = "[child={2} parent={1}] {0}: {4}")
    @MethodSource("inheritanceCasesAcrossAllBooleanFlags")
    void childInheritsOrOverridesParentForEveryBooleanFlag(
            RegionFlag flag, Boolean parentRule, Boolean childRule, boolean expected, String narrative) {
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(),
                ruleFor(flag, parentRule)));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(),
                ruleFor(flag, childRule), "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertEquals(expected, mgr.isAllowed(loc, OUTSIDER, flag),
                "Case: " + narrative);
    }

    // ---- Cross-flag isolation: a rule on flag X must not leak to flag Y. ----

    @ParameterizedTest(name = "{0} verdict does not leak to siblings")
    @EnumSource(value = RegionFlag.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"ALLOW_BLOCK_BREAK", "DENY_BLOCK_BREAK", "ALLOW_BLOCK_PLACE", "DENY_BLOCK_PLACE"})
    void permissiveRuleOnOneFlagDoesNotLeakToOthers(RegionFlag permitted) {
        mgr.regions().put("home", new Region("home", PARENT_OWNER, List.of(),
                ruleFor(permitted, true)));
        Location loc = locInChunkWithPdc(List.of("home"));
        for (RegionFlag other : RegionFlag.values()) {
            if (other.isMaterialFlag() || other == permitted) continue;
            assertFalse(mgr.isAllowed(loc, OUTSIDER, other),
                    permitted + "=true should not enable " + other);
        }
    }

    // ---- Deep chain: grandparent->parent->child. The innermost rule wins. ----

    @ParameterizedTest(name = "[3-level chain] {0} grandparent={1} parent={2} child={3} -> outsider {4}")
    @MethodSource("threeLevelCases")
    void deepChainResolvesInnermostThenClimbs(
            RegionFlag flag, Boolean gp, Boolean p, Boolean c, boolean expected) {
        mgr.regions().put("gp", new Region("gp", PARENT_OWNER, List.of(),
                ruleFor(flag, gp), null));
        mgr.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(),
                ruleFor(flag, p), "gp"));
        mgr.regions().put("child", new Region("child", CHILD_OWNER, List.of(),
                ruleFor(flag, c), "parent"));

        Location loc = locInChunkWithPdc(List.of("gp", "parent", "child"));
        assertEquals(expected, mgr.isAllowed(loc, OUTSIDER, flag));
    }

    private static Stream<Arguments> threeLevelCases() {
        // Tests "first-match wins, walks up from leaf" semantics. Each row
        // covers a different position for the matching rule.
        RegionFlag[] flags = {RegionFlag.BLOCK_BREAK, RegionFlag.PVP, RegionFlag.EXPLOSION};
        Stream.Builder<Arguments> b = Stream.builder();
        for (RegionFlag f : flags) {
            //                 gp,   p,    c,     expected
            b.add(Arguments.of(f, true,  null,  null,  true)); // grandparent answers
            b.add(Arguments.of(f, false, null,  null,  false));
            b.add(Arguments.of(f, true,  false, null,  false)); // parent answers, beats grandparent
            b.add(Arguments.of(f, false, true,  null,  true));
            b.add(Arguments.of(f, false, false, true,  true));  // child answers, beats both
            b.add(Arguments.of(f, true,  true,  false, false));
        }
        return b.build();
    }
}
