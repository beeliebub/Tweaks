package me.beeliebub.tweaks.tests.profiles;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.profiles.WorldProfileEntry;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WorldProfileTable's own (unvalidated) mutators round-tripping through a real FileConfiguration.
 * Validation itself is ConfigValueEditor's job - see ConfigValueEditorWorldProfileTest.
 */
class WorldProfileTableCrudTest {

    private ServerMock server;
    private Tweaks plugin;
    private WorldProfileTable table;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        table = new WorldProfileTable(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void putEntryPersistsAndIsVisibleAfterReload() {
        table.putEntry("jass:test", "testprofile", "Test", NamedTextColor.AQUA);
        table.reload(plugin.getConfig());

        Optional<WorldProfileEntry> entry = table.entry("jass:test");
        assertTrue(entry.isPresent());
        assertEquals("testprofile", entry.get().profile());
        assertEquals("Test", entry.get().tagText());
        assertEquals(NamedTextColor.AQUA, entry.get().color());
        assertEquals("testprofile", table.profileFor("jass:test"));
    }

    @Test
    void removeEntryRemovesAndFallsBackToFallbackResolution() {
        table.putEntry("jass:test", "testprofile", "Test", NamedTextColor.AQUA);
        table.removeEntry("jass:test");

        assertTrue(table.entry("jass:test").isEmpty());
        assertEquals(table.fallback().profile(), table.profileFor("jass:test"));
    }

    @Test
    void updateDisplayChangesTagColorButLeavesProfileUnchanged() {
        table.putEntry("jass:test", "testprofile", "Test", NamedTextColor.AQUA);

        table.updateDisplay("jass:test", "Updated", NamedTextColor.RED);

        WorldProfileEntry entry = table.entry("jass:test").orElseThrow();
        assertEquals("testprofile", entry.profile()); // unchanged
        assertEquals("Updated", entry.tagText());
        assertEquals(NamedTextColor.RED, entry.color());
    }

    @Test
    void removingEveryEntryLeavesOnlyFallbackProfileKnown() {
        for (String worldKey : table.worldKeysOrdered()) {
            table.removeEntry(worldKey);
        }

        assertTrue(table.worldKeysOrdered().isEmpty());
        assertEquals(1, table.knownProfiles().size());
        assertTrue(table.knownProfiles().contains(table.fallback().profile()));
    }
}
