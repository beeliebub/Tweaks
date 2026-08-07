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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

/**
 * economy.streak-multipliers (EditorType.NUMBER_MAP) key-range enforcement. The valid key range
 * (1-7) mirrors EconomyListener.MAX_STREAK - see ConfigRegistry's bounded(...) call and
 * ConfigValueEditor#mapPut's key-range check.
 */
class StreakMultiplierEditTest {

    private ServerMock server;
    private Tweaks plugin;
    private ConfigValueEditor editor;
    private ConfigSetting setting;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        editor = new ConfigValueEditor(plugin, mock(ResourceHuntItems.class), new WorldProfileTable(plugin));
        setting = ConfigRegistry.byPath("economy.streak-multipliers").orElseThrow();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void acceptsEveryDayOneThroughSeven() {
        for (int day = 1; day <= 7; day++) {
            EditResult result = editor.mapPut(setting, String.valueOf(day), "9.0");
            assertInstanceOf(EditResult.Ok.class, result, "day " + day + " should be accepted");
            assertEquals(9.0, plugin.getConfig().getDouble("economy.streak-multipliers." + day));
        }
    }

    @Test
    void rejectsDayZeroWithoutWriting() {
        EditResult result = editor.mapPut(setting, "0", "9.0");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertFalse(plugin.getConfig().contains("economy.streak-multipliers.0"));
    }

    @Test
    void rejectsDayEightWithoutWriting() {
        EditResult result = editor.mapPut(setting, "8", "9.0");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertFalse(plugin.getConfig().contains("economy.streak-multipliers.8"));
    }

    @Test
    void rejectsNegativeDayWithoutWriting() {
        EditResult result = editor.mapPut(setting, "-1", "9.0");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertFalse(plugin.getConfig().contains("economy.streak-multipliers.-1"));
    }

    @Test
    void rejectsNonNumericDayWithoutWriting() {
        EditResult result = editor.mapPut(setting, "monday", "9.0");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertFalse(plugin.getConfig().contains("economy.streak-multipliers.monday"));
    }

    // ------------------------------------------------------------ Value finiteness (economy corruption guard)

    @Test
    void rejectsNaNValueWithoutWriting() {
        EditResult result = editor.mapPut(setting, "3", "NaN");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertEquals(1.2, plugin.getConfig().getDouble("economy.streak-multipliers.3")); // bundled default, unchanged
    }

    @Test
    void rejectsPositiveInfinityValueWithoutWriting() {
        EditResult result = editor.mapPut(setting, "3", "Infinity");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertEquals(1.2, plugin.getConfig().getDouble("economy.streak-multipliers.3"));
    }

    @Test
    void rejectsNegativeInfinityValueWithoutWriting() {
        EditResult result = editor.mapPut(setting, "3", "-Infinity");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertEquals(1.2, plugin.getConfig().getDouble("economy.streak-multipliers.3"));
    }
}
