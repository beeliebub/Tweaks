package me.beeliebub.tweaks.tests.core.config;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.config.ConfigValueEditor;
import me.beeliebub.tweaks.core.config.EditResult;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Validation rules for the world-profiles add/remove/edit CRUD - see core/config/CLAUDE.md's
 * "world-profiles" section for why this is hand-built rather than a generic EditorType, and for
 * the design rationale behind each rule exercised below.
 */
class ConfigValueEditorWorldProfileTest {

    private ServerMock server;
    private Tweaks plugin;
    private WorldProfileTable table;
    private ConfigValueEditor editor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        table = new WorldProfileTable(plugin);
        editor = new ConfigValueEditor(plugin, mock(ResourceHuntItems.class), table);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ------------------------------------------------------------ add

    @Test
    void addRejectsDuplicateKey() {
        EditResult result = editor.worldProfileAdd("jass:lobby", "other", "Other", "RED");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertTrue(table.entry("jass:lobby").isPresent());
        // The original entry must be untouched, not overwritten.
        assertTrue(table.entry("jass:lobby").get().profile().equals("lobby"));
    }

    @Test
    void addRejectsNewKeyThatIsSubstringOfExisting() {
        // "jass:arch" is a substring of the bundled "jass:archive" entry.
        EditResult result = editor.worldProfileAdd("jass:arch", "other", "Other", "RED");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertTrue(table.entry("jass:arch").isEmpty());
    }

    @Test
    void addRejectsMalformedWorldKey() {
        EditResult result = editor.worldProfileAdd("not a valid key!!", "profile", "Label", "RED");

        assertInstanceOf(EditResult.Invalid.class, result);
    }

    @Test
    void addRejectsExistingKeyThatIsSubstringOfNewKey() {
        // "jass:archive" (existing) is a substring of this new key.
        EditResult result = editor.worldProfileAdd("jass:archiveland", "other", "Other", "RED");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertTrue(table.entry("jass:archiveland").isEmpty());
    }

    @Test
    void addRejectsBlankProfile() {
        EditResult result = editor.worldProfileAdd("jass:new", " ", "Label", "RED");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertTrue(table.entry("jass:new").isEmpty());
    }

    @Test
    void addRejectsProfileContainingDot() {
        EditResult result = editor.worldProfileAdd("jass:new", "bad.profile", "Label", "RED");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertTrue(table.entry("jass:new").isEmpty());
    }

    @Test
    void addRejectsBlankLabel() {
        EditResult result = editor.worldProfileAdd("jass:new", "newprofile", "", "RED");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertTrue(table.entry("jass:new").isEmpty());
    }

    @Test
    void addRejectsBracketedLabel() {
        EditResult result = editor.worldProfileAdd("jass:new", "newprofile", "[Bad]", "RED");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertTrue(table.entry("jass:new").isEmpty());
    }

    @Test
    void addRejectsUnknownColorWithoutFallingBackSilently() {
        EditResult result = editor.worldProfileAdd("jass:new", "newprofile", "Label", "NOT_A_REAL_COLOR");

        assertInstanceOf(EditResult.Invalid.class, result);
        // Proves hard-reject, not silent-fallback-to-default: the entry must not exist at all.
        assertTrue(table.entry("jass:new").isEmpty());
    }

    @Test
    void addAcceptsValidNewEntry() {
        EditResult result = editor.worldProfileAdd("jass:new", "newprofile", "New", "AQUA");

        assertInstanceOf(EditResult.Ok.class, result);
        assertTrue(table.entry("jass:new").isPresent());
        assertTrue(table.entry("jass:new").get().profile().equals("newprofile"));
    }

    @Test
    void addAllowsSharingAnExistingProfileAcrossMultipleWorldKeys() {
        // jass:resource and jass:resource_nether already both map to "standard" - a second world
        // key sharing a profile must not be rejected as a uniqueness violation.
        EditResult result = editor.worldProfileAdd("jass:new_standard_world", "standard", "New", "GREEN");

        assertInstanceOf(EditResult.Ok.class, result);
    }

    // ------------------------------------------------------------ remove

    @Test
    void removeRejectsUnknownKey() {
        EditResult result = editor.worldProfileRemove("jass:doesnotexist");

        assertInstanceOf(EditResult.Invalid.class, result);
    }

    @Test
    void removeAcceptsKnownKey() {
        EditResult result = editor.worldProfileRemove("jass:lobby");

        assertInstanceOf(EditResult.Ok.class, result);
        assertTrue(table.entry("jass:lobby").isEmpty());
    }

    @Test
    void removingTheLastEntrySucceedsWithNoSpecialCaseError() {
        for (String key : table.worldKeysOrdered()) {
            EditResult result = editor.worldProfileRemove(key);
            assertInstanceOf(EditResult.Ok.class, result);
        }

        assertTrue(table.worldKeysOrdered().isEmpty());
    }

    // ------------------------------------------------------------ edit

    @Test
    void editRejectsUnknownKey() {
        EditResult result = editor.worldProfileEdit("jass:doesnotexist", "Label", "RED");

        assertInstanceOf(EditResult.Invalid.class, result);
    }

    @Test
    void editRejectsUnknownColorWithoutFallingBackSilently() {
        EditResult result = editor.worldProfileEdit("jass:lobby", "Lobby", "NOT_A_REAL_COLOR");

        assertInstanceOf(EditResult.Invalid.class, result);
        // Original tag/color must be untouched.
        assertTrue(table.entry("jass:lobby").get().tagText().equals("Lobby"));
    }

    @Test
    void editAcceptsValidChangeAndLeavesProfileUnchanged() {
        EditResult result = editor.worldProfileEdit("jass:lobby", "NewLabel", "RED");

        assertInstanceOf(EditResult.Ok.class, result);
        assertTrue(table.entry("jass:lobby").get().profile().equals("lobby")); // unchanged
        assertTrue(table.entry("jass:lobby").get().tagText().equals("NewLabel"));
    }
}
