package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProtectionManagerLookupTest {

    private static final UUID OWNER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MEMBER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OUTSIDER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @BeforeAll
    static void initKeys() {
        try (MockedConstruction<NamespacedKey> ignored = mockConstruction(NamespacedKey.class)) {
            ProtectionKeys.init(mock(Tweaks.class));
        }
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

    private static Region region(String id, EnumSet<RegionFlag> flags, UUID... members) {
        return new Region(id, OWNER, List.of(members), flags);
    }

    @Test
    void regionsAtReturnsEmptyForUnprotectedChunk() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        Location loc = locInChunkWithPdc(List.of());
        assertTrue(mgr.regionsAt(loc).isEmpty());
    }

    @Test
    void regionsAtResolvesPointersToCachedRegions() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        Region home = region("home", EnumSet.noneOf(RegionFlag.class));
        Region safezone = region("safezone", EnumSet.noneOf(RegionFlag.class));
        mgr.regions().put("home", home);
        mgr.regions().put("safezone", safezone);

        Location loc = locInChunkWithPdc(List.of("home", "safezone"));
        List<Region> found = mgr.regionsAt(loc);

        assertEquals(2, found.size());
        assertTrue(found.contains(home));
        assertTrue(found.contains(safezone));
    }

    @Test
    void regionsAtSkipsOrphanedPointers() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        Region home = region("home", EnumSet.noneOf(RegionFlag.class));
        mgr.regions().put("home", home);

        // PDC still has 'dead' but cache no longer does — should be filtered.
        Location loc = locInChunkWithPdc(List.of("home", "dead"));
        List<Region> found = mgr.regionsAt(loc);

        assertEquals(List.of(home), found);
    }

    @Test
    void unprotectedChunkAllowsEveryAction() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        Location loc = locInChunkWithPdc(List.of());
        assertTrue(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
        assertTrue(mgr.isAllowed(loc, null, RegionFlag.EXPLOSION));
    }

    @Test
    void memberOfSingleRegionMayActWithoutFlagSet() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        mgr.regions().put("home", region("home", EnumSet.noneOf(RegionFlag.class), MEMBER));
        Location loc = locInChunkWithPdc(List.of("home"));

        assertTrue(mgr.isAllowed(loc, MEMBER, RegionFlag.BLOCK_BREAK));
        assertTrue(mgr.isAllowed(loc, OWNER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void outsiderBlockedWhenFlagUnset() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        mgr.regions().put("home", region("home", EnumSet.noneOf(RegionFlag.class)));
        Location loc = locInChunkWithPdc(List.of("home"));

        assertFalse(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void outsiderAllowedWhenFlagSet() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        mgr.regions().put("home", region("home", EnumSet.of(RegionFlag.BLOCK_BREAK)));
        Location loc = locInChunkWithPdc(List.of("home"));

        assertTrue(mgr.isAllowed(loc, OUTSIDER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void overlappingRegionsRequireAllToPermit() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        // Player's home: actor is a member -> permitted by this region.
        mgr.regions().put("home", region("home", EnumSet.noneOf(RegionFlag.class), MEMBER));
        // Admin safezone overlapping: actor is NOT a member, flag not set.
        mgr.regions().put("safezone", region("safezone", EnumSet.noneOf(RegionFlag.class)));

        Location loc = locInChunkWithPdc(List.of("home", "safezone"));

        // Even though actor is a member of `home`, the safezone overlap blocks.
        assertFalse(mgr.isAllowed(loc, MEMBER, RegionFlag.BLOCK_BREAK));
    }

    @Test
    void nullActorOnlyConsultsFlagAcrossAllRegions() {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        mgr.regions().put("a", region("a", EnumSet.of(RegionFlag.EXPLOSION)));
        mgr.regions().put("b", region("b", EnumSet.of(RegionFlag.EXPLOSION)));

        Location loc = locInChunkWithPdc(List.of("a", "b"));
        assertTrue(mgr.isAllowed(loc, null, RegionFlag.EXPLOSION));

        mgr.regions().put("b", region("b", EnumSet.noneOf(RegionFlag.class)));
        assertFalse(mgr.isAllowed(loc, null, RegionFlag.EXPLOSION));
    }
}
