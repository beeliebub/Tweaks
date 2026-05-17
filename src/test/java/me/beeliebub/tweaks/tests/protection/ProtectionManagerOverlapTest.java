package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class ProtectionManagerOverlapTest {

    private static final UUID PLAYER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeAll
    static void initKeys() {
        try (MockedConstruction<NamespacedKey> ignored = mockConstruction(NamespacedKey.class)) {
            ProtectionKeys.init(mock(Tweaks.class));
        }
    }

    private ProtectionManager mgr;
    private World overworld;
    private World nether;

    @BeforeEach
    void setUp() {
        mgr = new ProtectionManager(mock(Tweaks.class));
        overworld = world("overworld");
        nether = world("nether");
    }

    private static World world(String name) {
        World w = mock(World.class);
        when(w.getName()).thenReturn(name);
        when(w.getChunkAtAsync(anyInt(), anyInt(), anyBoolean()))
                .thenAnswer(inv -> CompletableFuture.completedFuture(mock(Chunk.class, RETURNS_DEEP_STUBS)));
        return w;
    }

    private static Region newRegion(String id, UUID owner) {
        return new Region(id, owner, List.of(), EnumSet.noneOf(RegionFlag.class));
    }

    @Test
    void firstClaimSucceeds() {
        ProtectionManager.ClaimResult result = mgr.tryClaim(
                newRegion("home", PLAYER_A), overworld, 0, 0, 31, 31, null);
        assertEquals(ProtectionManager.ClaimResult.OK, result);
        // tryClaim stores under the composite "<world>:<id>" key per per-world refactor.
        assertNotNull(mgr.byName(overworld, "home"));
    }

    @Test
    void duplicateIdRejected() {
        mgr.tryClaim(newRegion("home", PLAYER_A), overworld, 0, 0, 15, 15, null);
        ProtectionManager.ClaimResult result = mgr.tryClaim(
                newRegion("home", PLAYER_B), overworld, 32, 32, 47, 47, null);
        assertEquals(ProtectionManager.ClaimResult.ID_TAKEN, result);
    }

    @Test
    void overlapWithForeignRegionRejected() {
        mgr.tryClaim(newRegion("a_home", PLAYER_A), overworld, 0, 0, 31, 31, null);
        ProtectionManager.ClaimResult result = mgr.tryClaim(
                newRegion("b_intruder", PLAYER_B), overworld, 16, 16, 47, 47, null);
        assertEquals(ProtectionManager.ClaimResult.OVERLAPS_FOREIGN_REGION, result);
        assertNull(mgr.regions().get("b_intruder"));
    }

    @Test
    void overlapWithOwnRegionAllowed() {
        mgr.tryClaim(newRegion("big", PLAYER_A), overworld, 0, 0, 63, 63, null);
        // Same owner carving a smaller box inside is allowed (sub-region prep).
        ProtectionManager.ClaimResult result = mgr.tryClaim(
                newRegion("small", PLAYER_A), overworld, 16, 16, 31, 31, null);
        assertEquals(ProtectionManager.ClaimResult.OK, result);
    }

    @Test
    void sameChunkCoordsInDifferentWorldDoNotConflict() {
        mgr.tryClaim(newRegion("over", PLAYER_A), overworld, 0, 0, 31, 31, null);
        ProtectionManager.ClaimResult result = mgr.tryClaim(
                newRegion("hell", PLAYER_B), nether, 0, 0, 31, 31, null);
        assertEquals(ProtectionManager.ClaimResult.OK, result);
    }

    @Test
    void identicalRegionNamesInDifferentWorldsCoexist() {
        // Per-world uniqueness DoD: a region named "home" must be claimable
        // independently in two different worlds without conflict.
        assertEquals(ProtectionManager.ClaimResult.OK,
                mgr.tryClaim(newRegion("home", PLAYER_A), overworld, 0, 0, 31, 31, null));
        assertEquals(ProtectionManager.ClaimResult.OK,
                mgr.tryClaim(newRegion("home", PLAYER_B), nether, 0, 0, 31, 31, null));

        Region overworldHome = mgr.byName(overworld, "home");
        Region netherHome = mgr.byName(nether, "home");
        assertNotNull(overworldHome);
        assertNotNull(netherHome);
        assertNotSame(overworldHome, netherHome);
        assertEquals(PLAYER_A, overworldHome.owner());
        assertEquals(PLAYER_B, netherHome.owner());
    }

    @Test
    void sameWorldDuplicateNameStillRejected() {
        // Sanity check: per-world uniqueness must not weaken the same-world
        // duplicate guard.
        assertEquals(ProtectionManager.ClaimResult.OK,
                mgr.tryClaim(newRegion("home", PLAYER_A), overworld, 0, 0, 15, 15, null));
        assertEquals(ProtectionManager.ClaimResult.ID_TAKEN,
                mgr.tryClaim(newRegion("home", PLAYER_B), overworld, 32, 32, 47, 47, null));
    }

    @Test
    void adjacentNonOverlappingClaimAllowedAcrossOwners() {
        mgr.tryClaim(newRegion("a", PLAYER_A), overworld, 0, 0, 15, 15, null);
        // (16, 0)-(31, 15) is the next chunk over.
        ProtectionManager.ClaimResult result = mgr.tryClaim(
                newRegion("b", PLAYER_B), overworld, 16, 0, 31, 15, null);
        assertEquals(ProtectionManager.ClaimResult.OK, result);
    }

    @Test
    void setParentRejectedWhenChildExceedsParentBounds() {
        mgr.tryClaim(newRegion("plot", PLAYER_A), overworld, 0, 0, 31, 31, null);
        // Sub-claim larger than parent.
        mgr.tryClaim(newRegion("rogue", PLAYER_A), overworld, 16, 16, 63, 63, null);

        ProtectionManager.SetParentResult result = mgr.setParent("rogue", "plot");
        assertEquals(ProtectionManager.SetParentResult.NOT_CONTAINED_IN_PARENT, result);
    }

    @Test
    void setParentRejectsSiblingOverlap() {
        mgr.tryClaim(newRegion("plot", PLAYER_A), overworld, 0, 0, 63, 63, null);
        mgr.tryClaim(newRegion("sibling_a", PLAYER_A), overworld, 0, 0, 15, 15, null);
        mgr.tryClaim(newRegion("sibling_b", PLAYER_A), overworld, 0, 0, 15, 15, null);

        assertEquals(ProtectionManager.SetParentResult.OK, mgr.setParent("sibling_a", "plot"));
        assertEquals(ProtectionManager.SetParentResult.OVERLAPS_SIBLING,
                mgr.setParent("sibling_b", "plot"));
    }

    @Test
    void setParentSucceedsForContainedNonOverlappingChild() {
        mgr.tryClaim(newRegion("plot", PLAYER_A), overworld, 0, 0, 63, 63, null);
        mgr.tryClaim(newRegion("workshop", PLAYER_A), overworld, 0, 0, 15, 15, null);
        mgr.tryClaim(newRegion("lab", PLAYER_A), overworld, 32, 32, 47, 47, null);

        assertEquals(ProtectionManager.SetParentResult.OK, mgr.setParent("workshop", "plot"));
        assertEquals(ProtectionManager.SetParentResult.OK, mgr.setParent("lab", "plot"));
    }

    @Test
    void legacyRegionsWithoutBoundsBypassGeometryChecks() {
        // Manually drop a bounds-less region into the cache (simulating a
        // YAML loaded from before the bounds field existed). Reparenting it
        // should not crash and should not enforce containment.
        Region legacy = new Region("legacy", PLAYER_A, List.of(), EnumSet.noneOf(RegionFlag.class));
        mgr.regions().put("legacy", legacy);
        mgr.tryClaim(newRegion("modern", PLAYER_A), overworld, 0, 0, 15, 15, null);

        assertEquals(ProtectionManager.SetParentResult.OK,
                mgr.setParent("legacy", "modern"));
    }
}
