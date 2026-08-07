package me.beeliebub.tweaks.tests.core.config;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.config.ConfigRegistry;
import me.beeliebub.tweaks.core.config.ConfigSetting;
import me.beeliebub.tweaks.core.config.ConfigValueEditor;
import me.beeliebub.tweaks.core.config.EditResult;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

/**
 * protection.selection-tool (EditorType.MATERIAL) accept/reject cases, plus the live-effect
 * requirement: a write must be visible via Tweaks#getProtectionSelectionTool() immediately, with no
 * plugin/manager reconstruction - see ConfigValueEditor#setMaterial's
 * PROTECTION_SELECTION_TOOL_PATH special-case and Services' write-once-exception Javadoc.
 */
class ConfigValueEditorMaterialTest {

    private ServerMock server;
    private Tweaks plugin;
    private ConfigValueEditor editor;
    private ConfigSetting setting;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        editor = new ConfigValueEditor(plugin, mock(ResourceHuntItems.class), new WorldProfileTable(plugin));
        setting = ConfigRegistry.byPath("protection.selection-tool").orElseThrow();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rejectsUnknownMaterialNameWithoutWriting() {
        EditResult result = editor.applyScalar(setting, "not_a_real_material");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertEquals("STONE_AXE", plugin.getConfig().getString("protection.selection-tool"));
    }

    @Test
    void rejectsAirAsNotHoldable() {
        EditResult result = editor.applyScalar(setting, "AIR");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertEquals("STONE_AXE", plugin.getConfig().getString("protection.selection-tool"));
    }

    @Test
    void rejectsBlockOnlyMaterialAsNotHoldable() {
        // WATER has no item form - Material#isItem() is false.
        EditResult result = editor.applyScalar(setting, "WATER");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertEquals("STONE_AXE", plugin.getConfig().getString("protection.selection-tool"));
    }

    @Test
    void acceptsHoldableMaterialAndTakesEffectLiveWithoutReconstruction() {
        EditResult result = editor.applyScalar(setting, "DIAMOND_AXE");

        assertInstanceOf(EditResult.Ok.class, result);
        assertEquals("DIAMOND_AXE", plugin.getConfig().getString("protection.selection-tool"));
        // Live-effect: Services' cached field is updated in the same call, not just config.yml.
        assertEquals(Material.DIAMOND_AXE, plugin.getProtectionSelectionTool());
    }
}
