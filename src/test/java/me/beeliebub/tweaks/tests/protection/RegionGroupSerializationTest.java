package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionLoader;
import me.beeliebub.tweaks.protection.RegionWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Round-trip serialization tests for Region member/manager group entries.
 *
 * <p>Verified contract: {@link RegionWriter#writeNow} serialises group names back into the
 * {@code members}/{@code managers} YAML list using a {@code group:} prefix; {@link RegionLoader}
 * strips that prefix (case-insensitive), lowercases the name, deduplicates, and drops bare
 * {@code "group:"} (empty-name) entries. The {@code managers} block is written when there are
 * UUID managers OR managerGroups.
 */
class RegionGroupSerializationTest {

    private static final UUID OWNER   = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER  = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MANAGER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final RegionLoader loader = new RegionLoader(Logger.getLogger("test"));

    // -------------------------------------------------------------------------
    // Helper: write a region and reload it from the temp directory.
    // -------------------------------------------------------------------------

    private Region writeAndReload(Path tmp, Region region) throws Exception {
        RegionWriter writer = new RegionWriter(mock(Tweaks.class), tmp.toFile());
        writer.writeNow(region);

        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();
        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(1, loaded, "exactly one region must load back");

        // Cache key depends on worldName: null-world regions use plain id.
        String key = (region.worldName() == null || region.worldName().isEmpty())
                ? region.id()
                : region.worldName() + ":" + region.id();
        Region back = cache.get(key);
        assertNotNull(back, "reloaded region must be present under key '" + key + "'");
        return back;
    }

    // -------------------------------------------------------------------------
    // 1. Mixed UUID-members and group-members round-trip
    // -------------------------------------------------------------------------

    @Test
    void mixedUuidAndGroupMembersRoundTrip(@TempDir Path tmp) throws Exception {
        Region original = new Region(
                "plot", OWNER, List.of(MEMBER), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of("builders", "staff"),
                List.of());

        Region back = writeAndReload(tmp, original);

        assertEquals(List.of(MEMBER), back.members(),
                "UUID members must survive the round-trip");
        assertEquals(List.of("builders", "staff"), back.memberGroups(),
                "member groups must survive the round-trip");
        assertTrue(back.managerGroups().isEmpty(),
                "managerGroups must be empty when none were stored");
    }

    // -------------------------------------------------------------------------
    // 2. Mixed UUID-managers and group-managers round-trip
    // -------------------------------------------------------------------------

    @Test
    void mixedUuidAndGroupManagersRoundTrip(@TempDir Path tmp) throws Exception {
        Region original = new Region(
                "plot", OWNER, List.of(MEMBER), Map.of(), Map.of(),
                null, null, null, List.of(MANAGER), Map.of(), 0,
                List.of(),
                List.of("admins"));

        Region back = writeAndReload(tmp, original);

        assertTrue(back.isManager(MANAGER), "UUID manager must survive the round-trip");
        assertEquals(List.of("admins"), back.managerGroups(),
                "manager groups must survive the round-trip");
        assertTrue(back.memberGroups().isEmpty(),
                "memberGroups must be empty when none were stored");
    }

    // -------------------------------------------------------------------------
    // 3. Only managerGroups (no UUID managers) still writes the managers block
    // -------------------------------------------------------------------------

    @Test
    void onlyManagerGroupsWritesManagersBlock(@TempDir Path tmp) throws Exception {
        Region original = new Region(
                "plot", OWNER, List.of(), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of(),
                List.of("mods"));

        Region back = writeAndReload(tmp, original);

        assertTrue(back.managers().isEmpty(),
                "no UUID managers should be present after reload");
        assertEquals(List.of("mods"), back.managerGroups(),
                "managerGroups-only region must have its group survive the round-trip");
    }

    // -------------------------------------------------------------------------
    // 4. Hand-written YAML with mixed-case group prefix lowercases the name
    // -------------------------------------------------------------------------

    @Test
    void handWrittenYamlWithMixedCaseGroupPrefixNormalisesToLowercase(@TempDir Path tmp)
            throws Exception {
        String yaml = """
                id: home
                owner: %s
                members:
                  - %s
                  - group:Builders
                  - GROUP:STAFF
                """.formatted(OWNER, MEMBER);
        Files.writeString(tmp.resolve("home.yml"), yaml);

        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();
        int loaded = loader.load(tmp.toFile(), cache);

        assertEquals(1, loaded);
        Region r = cache.get("home");
        assertNotNull(r);
        assertEquals(List.of(MEMBER), r.members(),
                "UUID members should be present");
        assertTrue(r.memberGroups().contains("builders"),
                "mixed-case 'group:Builders' must be lowercased to 'builders'");
        assertTrue(r.memberGroups().contains("staff"),
                "all-caps 'GROUP:STAFF' must be lowercased to 'staff'");
        assertEquals(2, r.memberGroups().size(),
                "there must be exactly two member groups");
    }

    // -------------------------------------------------------------------------
    // 5. Empty group name ("group:") is silently dropped
    // -------------------------------------------------------------------------

    @Test
    void emptyGroupNameAfterPrefixIsDropped(@TempDir Path tmp) throws Exception {
        String yaml = """
                id: home
                owner: %s
                members:
                  - %s
                  - group:
                """.formatted(OWNER, MEMBER);
        Files.writeString(tmp.resolve("home.yml"), yaml);

        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();
        loader.load(tmp.toFile(), cache);

        Region r = cache.get("home");
        assertNotNull(r);
        assertTrue(r.memberGroups().isEmpty(),
                "a bare 'group:' entry with no name must be dropped");
        assertEquals(List.of(MEMBER), r.members(),
                "the UUID member must still be present");
    }

    // -------------------------------------------------------------------------
    // 6. Duplicate group names are deduplicated on round-trip
    // -------------------------------------------------------------------------

    @Test
    void duplicateGroupNamesAreDeduplicatedOnLoad(@TempDir Path tmp) throws Exception {
        String yaml = """
                id: home
                owner: %s
                members:
                  - group:builders
                  - group:Builders
                  - group:BUILDERS
                """.formatted(OWNER);
        Files.writeString(tmp.resolve("home.yml"), yaml);

        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();
        loader.load(tmp.toFile(), cache);

        Region r = cache.get("home");
        assertNotNull(r);
        assertEquals(1, r.memberGroups().size(),
                "duplicate group names (differing only in case) must collapse to one entry");
        assertEquals("builders", r.memberGroups().get(0));
    }

    // -------------------------------------------------------------------------
    // 7. Full round-trip with BOTH member and manager groups plus UUID lists
    // -------------------------------------------------------------------------

    @Test
    void fullGroupRoundTripWithBothRoles(@TempDir Path tmp) throws Exception {
        Region original = new Region(
                "fort", OWNER, List.of(MEMBER), Map.of(), Map.of(),
                null, null, null, List.of(MANAGER), Map.of(), 0,
                List.of("builders", "vips"),
                List.of("admins", "mods"));

        Region back = writeAndReload(tmp, original);

        assertEquals(List.of(MEMBER), back.members());
        assertTrue(back.isManager(MANAGER));
        assertEquals(List.of("builders", "vips"), back.memberGroups());
        assertEquals(List.of("admins", "mods"), back.managerGroups());
    }

    // -------------------------------------------------------------------------
    // 8. Empty members + empty member groups still writes the members key
    // -------------------------------------------------------------------------

    @Test
    void emptyMembersBlockIsAlwaysWritten(@TempDir Path tmp) throws Exception {
        // Even a region with no members at all must round-trip without losing its id/owner.
        Region original = new Region(
                "empty", OWNER, List.of(), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0,
                List.of(), List.of());

        Region back = writeAndReload(tmp, original);

        assertTrue(back.members().isEmpty());
        assertTrue(back.memberGroups().isEmpty());
        assertTrue(back.managerGroups().isEmpty());
        assertEquals(OWNER, back.owner());
    }
}
