package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ProtectionManagerMutationTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private ProtectionManager mgr;

    @BeforeEach
    void setUp() {
        mgr = new ProtectionManager(mock(Tweaks.class));
        mgr.regions().put("home", new Region("home", OWNER, List.of(MEMBER),
                EnumSet.of(RegionFlag.PVP)));
    }

    @Test
    void unclaimRemovesFromCacheAndMarksOrphan() {
        assertTrue(mgr.unclaim("home"));
        assertFalse(mgr.regions().containsKey("home"));
        assertTrue(mgr.orphanedRegions().contains("home"));
    }

    @Test
    void unclaimUnknownRegionReturnsFalse() {
        assertFalse(mgr.unclaim("ghost"));
        assertTrue(mgr.orphanedRegions().isEmpty());
    }

    @Test
    void addMemberAppendsAndPersistsImmutability() {
        Region before = mgr.regions().get("home");
        assertTrue(mgr.addMember("home", OTHER));

        Region after = mgr.regions().get("home");
        assertNotSame(before, after, "must replace with a new Region");
        assertEquals(List.of(MEMBER, OTHER), after.members());
        assertEquals(before.flags(), after.flags());
        assertEquals(before.owner(), after.owner());
    }

    @Test
    void addMemberIdempotentForExistingMember() {
        assertFalse(mgr.addMember("home", MEMBER));
    }

    @Test
    void addMemberToUnknownRegionFails() {
        assertFalse(mgr.addMember("ghost", OTHER));
    }

    @Test
    void removeMemberDropsAndPersists() {
        assertTrue(mgr.removeMember("home", MEMBER));
        assertEquals(List.of(), mgr.regions().get("home").members());
    }

    @Test
    void removeMemberMissingIsNoOp() {
        assertFalse(mgr.removeMember("home", OTHER));
    }

    @Test
    void setFlagAddsAndRemoves() {
        assertTrue(mgr.setFlag("home", RegionFlag.EXPLOSION, true));
        assertTrue(mgr.regions().get("home").hasFlag(RegionFlag.EXPLOSION));

        assertTrue(mgr.setFlag("home", RegionFlag.EXPLOSION, false));
        assertFalse(mgr.regions().get("home").hasFlag(RegionFlag.EXPLOSION));
    }

    @Test
    void setFlagToCurrentValueIsNoOp() {
        assertFalse(mgr.setFlag("home", RegionFlag.PVP, true));
        assertFalse(mgr.setFlag("home", RegionFlag.EXPLOSION, false));
    }

    @Test
    void setFlagAcrossEmptyFlagsHandlesEmptyCopyOf() {
        mgr.regions().put("plain", new Region("plain", OWNER, List.of(),
                EnumSet.noneOf(RegionFlag.class)));
        assertTrue(mgr.setFlag("plain", RegionFlag.PVP, true));
        assertTrue(mgr.regions().get("plain").hasFlag(RegionFlag.PVP));
    }
}
