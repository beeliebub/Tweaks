package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.protection.region.Region;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for group-aware membership/manager resolution on {@link Region}.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@code isMember(UUID, Set)} is true when the UUID is owner/manager/member OR the
 *       actor's groups intersect {@code memberGroups} OR {@code managerGroups}.</li>
 *   <li>{@code isManager(UUID, Set)} is true when the UUID is a listed manager OR the
 *       actor's groups intersect {@code managerGroups}.</li>
 *   <li>A manager is always a member.</li>
 *   <li>Null or empty groups sets fall back to plain UUID membership only.</li>
 *   <li>Groups are already lowercased by callers (ProtectionManager#groupsOf); the region
 *       stores them lowercase too, so comparison is a plain contains.</li>
 *   <li>All {@code add/removeMemberGroup} / {@code add/removeManagerGroup} mutators return a
 *       new Region and do not mutate the original.</li>
 * </ul>
 */
class RegionGroupMembershipTest {

    private static final UUID OWNER    = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    /** Build a minimal region that has one memberGroup and one managerGroup. */
    private static Region regionWithGroups() {
        return new Region(
                "home", OWNER, List.of(MEMBER), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of("staff"),
                List.of("admins"));
    }

    // -------------------------------------------------------------------------
    // isMember(UUID, Set) — group path
    // -------------------------------------------------------------------------

    @Test
    void outsiderWhoseMemberGroupIntersectsIsAMember() {
        Region r = regionWithGroups();
        assertTrue(r.isMember(OUTSIDER, Set.of("staff")),
                "OUTSIDER whose group 'staff' matches memberGroups must be a member");
    }

    @Test
    void outsiderWhoseManagerGroupIntersectsIsAlsoAMember() {
        Region r = regionWithGroups();
        assertTrue(r.isMember(OUTSIDER, Set.of("admins")),
                "OUTSIDER whose group 'admins' matches managerGroups must also be a member");
    }

    @Test
    void outsiderWithNoMatchingGroupIsNotAMember() {
        Region r = regionWithGroups();
        assertFalse(r.isMember(OUTSIDER, Set.of("peasants")),
                "OUTSIDER with no matching group must not be a member");
    }

    @Test
    void nullGroupsSetFallsBackToUuidMembershipOnly() {
        Region r = regionWithGroups();
        assertFalse(r.isMember(OUTSIDER, null),
                "null groups must not grant access via groups");
        assertTrue(r.isMember(MEMBER, null),
                "a listed UUID member must still pass with null groups");
        assertTrue(r.isMember(OWNER, null),
                "the owner must still pass with null groups");
    }

    @Test
    void emptyGroupsSetFallsBackToUuidMembershipOnly() {
        Region r = regionWithGroups();
        assertFalse(r.isMember(OUTSIDER, Set.of()),
                "empty groups must not grant access via groups");
        assertTrue(r.isMember(MEMBER, Set.of()),
                "a listed UUID member must still pass with empty groups");
    }

    // -------------------------------------------------------------------------
    // isManager(UUID, Set) — group path
    // -------------------------------------------------------------------------

    @Test
    void outsiderWhoseManagerGroupIntersectsIsManager() {
        Region r = regionWithGroups();
        assertTrue(r.isManager(OUTSIDER, Set.of("admins")),
                "OUTSIDER whose group 'admins' matches managerGroups must be a manager");
    }

    @Test
    void outsiderWhoseMemberGroupOnlyIntersectsIsNotManager() {
        Region r = regionWithGroups();
        assertFalse(r.isManager(OUTSIDER, Set.of("staff")),
                "OUTSIDER in only memberGroups must NOT be a manager");
    }

    @Test
    void nullGroupsSetFallsBackToUuidManagerList() {
        Region r = regionWithGroups();
        assertFalse(r.isManager(OUTSIDER, null),
                "null groups must not grant manager access");
    }

    @Test
    void emptyGroupsSetFallsBackToUuidManagerList() {
        Region r = regionWithGroups();
        assertFalse(r.isManager(OUTSIDER, Set.of()),
                "empty groups must not grant manager access");
    }

    // -------------------------------------------------------------------------
    // Manager-is-always-member invariant via groups
    // -------------------------------------------------------------------------

    @Test
    void groupManagerIsAlsoGroupMember() {
        Region r = regionWithGroups();
        Set<String> groups = Set.of("admins");
        assertTrue(r.isManager(OUTSIDER, groups),
                "group manager check must pass for 'admins'");
        assertTrue(r.isMember(OUTSIDER, groups),
                "a group manager must also be a group member");
    }

    // -------------------------------------------------------------------------
    // Case-insensitivity: groups arrive lowercased, region stores lowercase
    // -------------------------------------------------------------------------

    @Test
    void groupNamesAreStoredLowercaseAndMatchLowercasedInput() {
        // Region.addMemberGroup normalises to lowercase; callers also lowercase.
        Region r = new Region(
                "home", OWNER, List.of(), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of("staff"),
                List.of());

        // The region stores "staff"; the incoming groups set also contains "staff".
        assertTrue(r.isMember(OUTSIDER, Set.of("staff")));
        // Passing "STAFF" does NOT match because callers are responsible for lowercasing.
        // This documents the contract: groups must already be lowercase when passed in.
        assertFalse(r.isMember(OUTSIDER, Set.of("STAFF")),
                "group lookup is case-sensitive by design — callers must lowercase first");
    }

    // -------------------------------------------------------------------------
    // Immutability: addMemberGroup does not mutate the original
    // -------------------------------------------------------------------------

    @Test
    void addMemberGroupDoesNotMutateOriginal() {
        Region base = new Region(
                "home", OWNER, List.of(), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of("staff"), List.of());

        Region extended = base.addMemberGroup("builders");

        assertFalse(base.memberGroups().contains("builders"),
                "addMemberGroup must not mutate the original Region");
        assertTrue(extended.memberGroups().contains("builders"),
                "the returned Region must contain the new group");
        assertTrue(extended.memberGroups().contains("staff"),
                "the returned Region must still contain the previous group");
    }

    @Test
    void addManagerGroupDoesNotMutateOriginal() {
        Region base = new Region(
                "home", OWNER, List.of(), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of(), List.of("admins"));

        Region extended = base.addManagerGroup("mods");

        assertFalse(base.managerGroups().contains("mods"),
                "addManagerGroup must not mutate the original Region");
        assertTrue(extended.managerGroups().contains("mods"),
                "the returned Region must contain the new manager group");
        assertTrue(extended.managerGroups().contains("admins"),
                "the returned Region must still contain the previous manager group");
    }

    // -------------------------------------------------------------------------
    // removeMemberGroup / removeManagerGroup immutability
    // -------------------------------------------------------------------------

    @Test
    void removeMemberGroupDoesNotMutateOriginal() {
        Region base = new Region(
                "home", OWNER, List.of(), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of("staff", "builders"), List.of());

        Region trimmed = base.removeMemberGroup("staff");

        assertTrue(base.memberGroups().contains("staff"),
                "removeMemberGroup must not mutate the original Region");
        assertFalse(trimmed.memberGroups().contains("staff"),
                "the returned Region must not contain the removed group");
        assertTrue(trimmed.memberGroups().contains("builders"),
                "the returned Region must still contain un-removed groups");
    }

    @Test
    void removeManagerGroupDoesNotMutateOriginal() {
        Region base = new Region(
                "home", OWNER, List.of(), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of(), List.of("admins", "mods"));

        Region trimmed = base.removeManagerGroup("admins");

        assertTrue(base.managerGroups().contains("admins"),
                "removeManagerGroup must not mutate the original Region");
        assertFalse(trimmed.managerGroups().contains("admins"));
        assertTrue(trimmed.managerGroups().contains("mods"));
    }

    // -------------------------------------------------------------------------
    // Normalisation: addMemberGroup lowercases names regardless of input casing
    // -------------------------------------------------------------------------

    @Test
    void addMemberGroupNormalisesToLowercase() {
        Region base = new Region(
                "home", OWNER, List.of(), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of(), List.of());

        Region r = base.addMemberGroup("BUILDERS");

        assertTrue(r.memberGroups().contains("builders"),
                "addMemberGroup must lowercase the group name");
        assertFalse(r.memberGroups().contains("BUILDERS"),
                "un-normalised form must not be stored");
    }

    @Test
    void addMemberGroupIsIdempotent() {
        Region base = new Region(
                "home", OWNER, List.of(), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of("staff"), List.of());

        Region r = base.addMemberGroup("staff");

        assertSame(base, r, "addMemberGroup with an already-present group must return 'this'");
        assertEquals(1, r.memberGroups().size(),
                "idempotent add must not create duplicates");
    }

    @Test
    void addManagerGroupNormalisesToLowercase() {
        Region base = new Region(
                "home", OWNER, List.of(), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of(), List.of());

        Region r = base.addManagerGroup("Admins");

        assertTrue(r.managerGroups().contains("admins"));
        assertFalse(r.managerGroups().contains("Admins"));
    }

    // -------------------------------------------------------------------------
    // withMemberGroups / withManagerGroups wholesale replacement
    // -------------------------------------------------------------------------

    @Test
    void withMemberGroupsReplacesListAndNormalisesNames() {
        Region base = new Region(
                "home", OWNER, List.of(), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of("oldgroup"), List.of());

        Region r = base.withMemberGroups(List.of("Alpha", "beta", "GAMMA"));

        assertEquals(List.of("alpha", "beta", "gamma"), r.memberGroups(),
                "withMemberGroups must lowercase all names");
        assertFalse(r.memberGroups().contains("oldgroup"),
                "withMemberGroups replaces the entire list");
    }

    @Test
    void withManagerGroupsNullCollapsesToEmpty() {
        Region base = new Region(
                "home", OWNER, List.of(), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of(), List.of("admins"));

        Region r = base.withManagerGroups(null);

        assertTrue(r.managerGroups().isEmpty(),
                "withManagerGroups(null) must produce an empty list");
    }

    // -------------------------------------------------------------------------
    // Owner is always a member/manager regardless of groups
    // -------------------------------------------------------------------------

    @Test
    void ownerIsAlwaysMemberEvenWithEmptyGroups() {
        Region r = regionWithGroups();
        assertTrue(r.isMember(OWNER, Set.of()),
                "owner must always be a member");
        assertTrue(r.isMember(OWNER, null),
                "owner must always be a member with null groups too");
    }

    // -------------------------------------------------------------------------
    // Intersection with multiple groups: any match is sufficient
    // -------------------------------------------------------------------------

    @Test
    void actorWithMultipleGroupsMatchesOnAnyIntersection() {
        Region r = new Region(
                "home", OWNER, List.of(), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of("vips"), List.of());

        assertTrue(r.isMember(OUTSIDER, Set.of("peasants", "vips", "tourists")),
                "any group in the actor's set matching memberGroups must grant membership");
    }
}
