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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedConstruction;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Material-list inheritance: when the child is silent the parent's lists must
// apply, but any rule the child specifies takes precedence — exhaustively per
// (action, allow/deny) quadrant so a new material-list flag added later
// can't sneak past the contract.
class MaterialFlagInheritanceTest {

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

    // ---- Per-action parametric coverage ----

    static Stream<Arguments> actionVariants() {
        return Stream.of(
                Arguments.of(RegionFlag.BLOCK_BREAK,
                        RegionFlag.ALLOW_BLOCK_BREAK,
                        RegionFlag.DENY_BLOCK_BREAK),
                Arguments.of(RegionFlag.BLOCK_PLACE,
                        RegionFlag.ALLOW_BLOCK_PLACE,
                        RegionFlag.DENY_BLOCK_PLACE));
    }

    @ParameterizedTest(name = "{0}: child inherits parent ALLOW when silent")
    @MethodSource("actionVariants")
    void childInheritsParentAllowList(RegionFlag base, RegionFlag allow, RegionFlag deny) {
        mgr.regions().put("parent", new Region("parent", OWNER, List.of(),
                Map.of(base, Map.of(FlagTarget.DEFAULT, false)),
                Map.of(allow, Set.of(Material.DIRT)),
                null));
        mgr.regions().put("child", new Region("child", OWNER, List.of(), Map.of(), Map.of(), "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.DIRT, base));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, base));
    }

    @ParameterizedTest(name = "{0}: child inherits parent DENY when silent")
    @MethodSource("actionVariants")
    void childInheritsParentDenyList(RegionFlag base, RegionFlag allow, RegionFlag deny) {
        mgr.regions().put("parent", new Region("parent", OWNER, List.of(),
                Map.of(base, Map.of(FlagTarget.DEFAULT, true)),
                Map.of(deny, Set.of(Material.DIAMOND_ORE)),
                null));
        mgr.regions().put("child", new Region("child", OWNER, List.of(), Map.of(), Map.of(), "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.DIAMOND_ORE, base));
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, base));
    }

    @ParameterizedTest(name = "{0}: child ALLOW extends parent ALLOW (union behavior at leaf)")
    @MethodSource("actionVariants")
    void childAllowExtendsParentAllowForUnlistedMaterials(RegionFlag base, RegionFlag allow, RegionFlag deny) {
        // Parent allows STONE; child allows DIRT. Both should be permitted —
        // child handles DIRT, parent handles STONE via the climb.
        mgr.regions().put("parent", new Region("parent", OWNER, List.of(),
                Map.of(base, Map.of(FlagTarget.DEFAULT, false)),
                Map.of(allow, Set.of(Material.STONE)),
                null));
        mgr.regions().put("child", new Region("child", OWNER, List.of(), Map.of(),
                Map.of(allow, Set.of(Material.DIRT)),
                "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, base));
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.DIRT, base));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.OAK_LOG, base));
    }

    @ParameterizedTest(name = "{0}: child DENY adds to parent DENY (union behavior at leaf)")
    @MethodSource("actionVariants")
    void childDenyExtendsParentDenyForUnlistedMaterials(RegionFlag base, RegionFlag allow, RegionFlag deny) {
        mgr.regions().put("parent", new Region("parent", OWNER, List.of(),
                Map.of(base, Map.of(FlagTarget.DEFAULT, true)),
                Map.of(deny, Set.of(Material.BEDROCK)),
                null));
        mgr.regions().put("child", new Region("child", OWNER, List.of(), Map.of(),
                Map.of(deny, Set.of(Material.BEACON)),
                "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.BEDROCK, base));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.BEACON, base));
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, base));
    }

    @ParameterizedTest(name = "{0}: child ALLOW lifts parent DENY (sub-region override)")
    @MethodSource("actionVariants")
    void childAllowReversesParentDenyOnSameMaterial(RegionFlag base, RegionFlag allow, RegionFlag deny) {
        mgr.regions().put("parent", new Region("parent", OWNER, List.of(), Map.of(),
                Map.of(deny, Set.of(Material.GRASS_BLOCK)),
                null));
        mgr.regions().put("child", new Region("child", OWNER, List.of(), Map.of(),
                Map.of(allow, Set.of(Material.GRASS_BLOCK)),
                "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.GRASS_BLOCK, base),
                "child ALLOW match short-circuits before reaching parent DENY");
    }

    @ParameterizedTest(name = "{0}: child DENY shadows parent ALLOW (sub-region override)")
    @MethodSource("actionVariants")
    void childDenyShadowsParentAllowOnSameMaterial(RegionFlag base, RegionFlag allow, RegionFlag deny) {
        mgr.regions().put("parent", new Region("parent", OWNER, List.of(), Map.of(),
                Map.of(allow, Set.of(Material.GRASS_BLOCK)),
                null));
        mgr.regions().put("child", new Region("child", OWNER, List.of(), Map.of(),
                Map.of(deny, Set.of(Material.GRASS_BLOCK)),
                "parent"));

        Location loc = locInChunkWithPdc(List.of("parent", "child"));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.GRASS_BLOCK, base));
    }

    @ParameterizedTest(name = "{0}: deep grandparent material list still applies through chain")
    @MethodSource("actionVariants")
    void grandparentMaterialListResolvesWhenInnerLinksAreSilent(
            RegionFlag base, RegionFlag allow, RegionFlag deny) {
        mgr.regions().put("gp", new Region("gp", OWNER, List.of(),
                Map.of(base, Map.of(FlagTarget.DEFAULT, false)),
                Map.of(allow, Set.of(Material.SAND)),
                null));
        mgr.regions().put("parent", new Region("parent", OWNER, List.of(),
                Map.of(), Map.of(), "gp"));
        mgr.regions().put("child", new Region("child", OWNER, List.of(),
                Map.of(), Map.of(), "parent"));

        Location loc = locInChunkWithPdc(List.of("gp", "parent", "child"));
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.SAND, base));
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.GLASS, base));
    }

    // ---- BREAK material list does not bleed into PLACE and vice versa ----

    @ParameterizedTest(name = "{0}: list flag for one action does not affect the other")
    @MethodSource("actionVariants")
    void allowAndDenyListsAreScopedToTheirAction(
            RegionFlag base, RegionFlag allow, RegionFlag deny) {
        // ALLOW_BLOCK_BREAK shouldn't let a non-member place blocks, even if
        // the same material is listed. The two actions track distinct lists.
        mgr.regions().put("home", new Region("home", OWNER, List.of(),
                Map.of(RegionFlag.BLOCK_BREAK, Map.of(FlagTarget.DEFAULT, false),
                        RegionFlag.BLOCK_PLACE, Map.of(FlagTarget.DEFAULT, false)),
                Map.of(allow, Set.of(Material.STONE)),
                null));
        Location loc = locInChunkWithPdc(List.of("home"));

        // The action under test is permitted...
        assertTrue(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, base));
        // ...but the OTHER action remains blocked.
        RegionFlag other = (base == RegionFlag.BLOCK_BREAK) ? RegionFlag.BLOCK_PLACE : RegionFlag.BLOCK_BREAK;
        assertFalse(mgr.isBlockActionAllowed(loc, OUTSIDER, Material.STONE, other));
    }
}
