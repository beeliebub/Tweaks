package me.beeliebub.tweaks.tests.profiles;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Pins WorldProfileTable's read-resolution semantics against the historical hardcoded behavior of
 * SeparatorListener (exact-then-substring-then-fallback) and TabManager (exact-then-fallback), plus
 * the list-storage-shape regression this class exists to avoid (see its class Javadoc and
 * profiles/CLAUDE.md's WorldProfileTable entry for the full design rationale).
 */
class WorldProfileTableTest {

    private ServerMock server;
    private Tweaks plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static String plain(net.kyori.adventure.text.Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void exactMatchWinsOverSubstring() {
        WorldProfileTable table = new WorldProfileTable(plugin);
        // "arch" is a substring of the bundled "jass:archive" exact entry - exact must win.
        table.putEntry("arch", "archsub", "ArchSub", NamedTextColor.RED);

        assertEquals("archive", table.profileFor("jass:archive"));
        // A key that isn't itself registered but contains the substring entry falls through to it.
        assertEquals("archsub", table.profileFor("some:archrelated"));
    }

    @Test
    void substringFallbackResolvesLikeHistoricalSeparatorListener() {
        WorldProfileTable table = new WorldProfileTable(plugin);

        assertEquals("archive", table.profileFor("jass:archive_whatever"));
        assertEquals("lobby", table.profileFor("jass:lobby_instance_2"));
        assertEquals("pi", table.profileFor("jass:pi_backup"));
    }

    @Test
    void unknownKeyResolvesToFallbackProfileAndTag() {
        WorldProfileTable table = new WorldProfileTable(plugin);

        assertEquals("standard", table.profileFor("totally:unknown"));
        assertTrue(plain(table.tagFor("totally:unknown")).contains("Survival"));
    }

    @Test
    void sortKeyForReproducesBundledDefaultsExactly() {
        WorldProfileTable table = new WorldProfileTable(plugin);

        assertEquals("a", table.sortKeyFor("lobby"));
        assertEquals("b", table.sortKeyFor("standard"));
        assertEquals("c", table.sortKeyFor("archive"));
        assertEquals("d", table.sortKeyFor("pi"));
    }

    @Test
    void sortKeyForUnknownProfileIsDeterministicAndNonNullTrailingKey() {
        WorldProfileTable table = new WorldProfileTable(plugin);

        String key = table.sortKeyFor("brandnewprofile");
        assertTrue(key != null && key.startsWith("zz_"));
        assertEquals(key, table.sortKeyFor("brandnewprofile")); // deterministic across calls
    }

    @Test
    void malformedEntryIsSkippedWithoutThrowing() {
        WorldProfileTable table = new WorldProfileTable(plugin);

        Map<String, Object> valid = new LinkedHashMap<>();
        valid.put("world", "jass:valid");
        valid.put("profile", "validprofile");
        valid.put("tag", "Valid");
        valid.put("color", "GREEN");

        Map<String, Object> missingProfile = new LinkedHashMap<>();
        missingProfile.put("world", "jass:broken");
        missingProfile.put("tag", "Broken");
        missingProfile.put("color", "GREEN");

        Map<String, Object> badColor = new LinkedHashMap<>();
        badColor.put("world", "jass:badcolor");
        badColor.put("profile", "badcolorprofile");
        badColor.put("tag", "BadColor");
        badColor.put("color", "NOT_A_COLOR");

        plugin.getConfig().set("world-profiles", List.of(valid, missingProfile, badColor));

        assertDoesNotThrow(() -> table.reload(plugin.getConfig()));

        assertTrue(table.entry("jass:valid").isPresent());
        assertTrue(table.entry("jass:broken").isEmpty());
        assertTrue(table.entry("jass:badcolor").isEmpty());
    }

    @Test
    void malformedFallbackFallsBackToInCodeDefaultWithoutThrowing() {
        // A type mismatch (string instead of section) reliably reproduces "missing/malformed" here
        // regardless of Tweaks#reconcileConfigDefaults' sticky copyDefaults(true) - see
        // ConfigDefaultsTest's own note on why get()-based assertions can be misleading for that.
        plugin.getConfig().set("world-profile-fallback", "not-a-section");

        WorldProfileTable table = assertDoesNotThrow(() -> new WorldProfileTable(plugin));

        assertEquals("standard", table.fallback().profile());
        assertTrue(plain(table.fallback().tagComponent()).contains("Survival"));
    }

    @Test
    void bundledDefaultsMatchHistoricalWorldTagsExactly() {
        WorldProfileTable table = new WorldProfileTable(plugin);

        record Case(String worldKey, String expectedLabel) {}
        for (Case c : new Case[] {
                new Case("jass:lobby", "Lobby"),
                new Case("jass:archive", "Archive"),
                new Case("jass:pi", "Pi"),
                new Case("minecraft:overworld", "Survival"),
                new Case("minecraft:the_nether", "Nether"),
                new Case("minecraft:the_end", "End"),
                new Case("jass:resource", "Resource"),
                new Case("jass:resource_nether", "Resource"),
        }) {
            String plainTag = plain(table.tagFor(c.worldKey()));
            assertTrue(plainTag.contains(c.expectedLabel()),
                    "tag for '" + c.worldKey() + "' should contain '" + c.expectedLabel() + "' but was '" + plainTag + "'");
        }
    }

    @Test
    void removeThenReloadDoesNotResurrectABundledDefaultKey() {
        WorldProfileTable table = new WorldProfileTable(plugin);
        assertTrue(table.entry("jass:lobby").isPresent());

        table.removeEntry("jass:lobby");
        // Simulate a later reconciliation/reload pass re-deriving from the live (sticky
        // copyDefaults(true)) config - a keyed-map storage shape would resurrect this key.
        table.reload(plugin.getConfig());

        assertTrue(table.entry("jass:lobby").isEmpty());
        assertEquals("standard", table.profileFor("jass:lobby"));
    }
}
