package me.beeliebub.tweaks.tests.core.config;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.config.ConfigRegistry;
import me.beeliebub.tweaks.core.config.ConfigSetting;
import me.beeliebub.tweaks.core.config.ConfigValueEditor;
import me.beeliebub.tweaks.core.config.EditResult;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

/** fly-advancement (EditorType.NAMESPACED_KEY) accept/reject cases. */
class ConfigValueEditorKeyTest {

    private ServerMock server;
    private Tweaks plugin;
    private ConfigValueEditor editor;
    private ConfigSetting setting;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        editor = new ConfigValueEditor(plugin, mock(ResourceHuntItems.class), new WorldProfileTable(plugin));
        setting = ConfigRegistry.byPath("fly-advancement").orElseThrow();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rejectsMalformedNamespacedKeyWithoutWriting() {
        EditResult result = editor.applyScalar(setting, "not a valid key!!");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertEquals("jass:fly", plugin.getConfig().getString("fly-advancement")); // bundled default
    }

    @Test
    void rejectsBlankValueWithoutWriting() {
        EditResult result = editor.applyScalar(setting, "");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertEquals("jass:fly", plugin.getConfig().getString("fly-advancement"));
    }

    @Test
    void acceptsValidNamespacedKey() {
        EditResult result = editor.applyScalar(setting, "jass:new_fly_advancement");

        assertInstanceOf(EditResult.Ok.class, result);
        assertEquals("jass:new_fly_advancement", plugin.getConfig().getString("fly-advancement"));
    }

    @Test
    void lowercasesOnWrite() {
        EditResult result = editor.applyScalar(setting, "JASS:NEW_FLY_ADVANCEMENT");

        assertInstanceOf(EditResult.Ok.class, result);
        assertEquals("jass:new_fly_advancement", plugin.getConfig().getString("fly-advancement"));
    }
}
