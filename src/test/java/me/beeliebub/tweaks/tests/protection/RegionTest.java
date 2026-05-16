package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RegionTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void recordAccessorsReturnConstructorValues() {
        Region r = new Region("home", OWNER, List.of(MEMBER), EnumSet.of(RegionFlag.PVP));
        assertEquals("home", r.id());
        assertEquals(OWNER, r.owner());
        assertEquals(List.of(MEMBER), r.members());
        assertEquals(EnumSet.of(RegionFlag.PVP), r.flags());
    }

    @Test
    void emptyMembersAndFlagsAreAllowed() {
        Region r = new Region("empty", OWNER, List.of(), EnumSet.noneOf(RegionFlag.class));
        assertTrue(r.members().isEmpty());
        assertTrue(r.flags().isEmpty());
    }

    @Test
    void membersListIsDefensivelyCopiedAndImmutable() {
        List<UUID> source = new ArrayList<>(List.of(MEMBER));
        Region r = new Region("home", OWNER, source, EnumSet.noneOf(RegionFlag.class));

        source.add(OUTSIDER);

        assertEquals(List.of(MEMBER), r.members());
        assertThrows(UnsupportedOperationException.class, () -> r.members().add(OUTSIDER));
    }

    @Test
    void flagsSetIsDefensivelyCopied() {
        EnumSet<RegionFlag> source = EnumSet.of(RegionFlag.PVP);
        Region r = new Region("home", OWNER, List.of(), source);

        source.add(RegionFlag.EXPLOSION);

        assertEquals(EnumSet.of(RegionFlag.PVP), r.flags());
    }

    @Test
    void isOwnerOnlyMatchesOwnerUuid() {
        Region r = new Region("home", OWNER, List.of(MEMBER), EnumSet.noneOf(RegionFlag.class));
        assertTrue(r.isOwner(OWNER));
        assertFalse(r.isOwner(MEMBER));
        assertFalse(r.isOwner(OUTSIDER));
    }

    @Test
    void isMemberIncludesOwnerAndExplicitMembers() {
        Region r = new Region("home", OWNER, List.of(MEMBER), EnumSet.noneOf(RegionFlag.class));
        assertTrue(r.isMember(OWNER));
        assertTrue(r.isMember(MEMBER));
        assertFalse(r.isMember(OUTSIDER));
    }

    @Test
    void hasFlagReflectsConstructorFlags() {
        Region r = new Region("home", OWNER, List.of(), EnumSet.of(RegionFlag.PVP, RegionFlag.EXPLOSION));
        assertTrue(r.hasFlag(RegionFlag.PVP));
        assertTrue(r.hasFlag(RegionFlag.EXPLOSION));
        assertFalse(r.hasFlag(RegionFlag.BLOCK_BREAK));
    }
}
