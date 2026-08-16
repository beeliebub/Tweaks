package me.beeliebub.tweaks.tests.core.config;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.config.ConfigRegistry;
import me.beeliebub.tweaks.core.config.ConfigSetting;
import me.beeliebub.tweaks.core.config.ConfigValueEditor;
import me.beeliebub.tweaks.core.config.EditResult;
import me.beeliebub.tweaks.logging.LoggingPaths;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import me.beeliebub.tweaks.tools.augments.SlotCalculator;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

/**
 * Exercises ConfigValueEditor directly - the parse/validate/write core both ConfigCommand (CLI)
 * and ConfigGUI (Dialog) delegate to. ConfigGUI's real Dialog construction is covered separately.
 */
class ConfigValueEditorTest {

    private ServerMock server;
    private Tweaks plugin;
    private ResourceHuntItems resourceHuntItems;
    private ConfigValueEditor editor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        resourceHuntItems = mock(ResourceHuntItems.class);
        editor = new ConfigValueEditor(plugin, resourceHuntItems, new WorldProfileTable(plugin));
        editor.setSlotCapacityKeyValidator(SlotCalculator::isValidCapacityKey);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static String plain(EditResult result) {
        return PlainTextComponentSerializer.plainText().serialize(result.message());
    }

    // ------------------------------------------------------------ Legacy scalar (max-homes)

    @Test
    void legacyMaxHomesRejectsNonPositiveWithoutWriting() {
        EditResult result = editor.setMaxHomes("0");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertEquals(15, plugin.getConfig().getInt("max-homes")); // bundled default, unchanged
    }

    @Test
    void legacyMaxHomesRejectsNonNumericWithoutWriting() {
        EditResult result = editor.setMaxHomes("not-a-number");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertEquals(15, plugin.getConfig().getInt("max-homes"));
    }

    @Test
    void legacyMaxHomesAcceptsPositive() {
        EditResult result = editor.setMaxHomes("7");

        assertInstanceOf(EditResult.Ok.class, result);
        assertEquals(7, plugin.getConfig().getInt("max-homes"));
    }

    @Test
    void failedSaveDoesNotReportSuccess() {
        Tweaks saveBlocked = spy(plugin);
        doNothing().when(saveBlocked).saveConfig();
        ConfigValueEditor saveBlockedEditor = new ConfigValueEditor(saveBlocked, resourceHuntItems,
                new WorldProfileTable(saveBlocked));

        EditResult result = saveBlockedEditor.setMaxHomes("7");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertEquals(15, plugin.getConfig().getInt("max-homes"));
    }

    // ------------------------------------------------------------ Generic scalar (percent)

    @Test
    void genericScalarRejectsOutOfRangeWithoutWriting() {
        ConfigSetting setting = ConfigRegistry.byPath("egg-collector-drop-chance").orElseThrow();

        EditResult result = editor.applyScalar(setting, "150");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertEquals(0.5, plugin.getConfig().getDouble("egg-collector-drop-chance")); // bundled default
    }

    @Test
    void genericScalarAcceptsInRange() {
        ConfigSetting setting = ConfigRegistry.byPath("egg-collector-drop-chance").orElseThrow();

        EditResult result = editor.applyScalar(setting, "42.0");

        assertInstanceOf(EditResult.Ok.class, result);
        assertEquals(42.0, plugin.getConfig().getDouble("egg-collector-drop-chance"));
    }

    @Test
    void failedLargeLongSaveDoesNotCollapseThroughDoublePrecision() {
        ConfigSetting setting = ConfigRegistry.byPath("itemadmin.tool-protect.warn-cooldown-ms").orElseThrow();
        long persistedValue = 9_007_199_254_740_992L;
        long attemptedValue = 9_007_199_254_740_993L;
        plugin.getConfig().set(setting.path(), persistedValue);
        plugin.saveConfig();

        Tweaks saveBlocked = spy(plugin);
        doNothing().when(saveBlocked).saveConfig();
        ConfigValueEditor saveBlockedEditor = new ConfigValueEditor(saveBlocked, resourceHuntItems,
                new WorldProfileTable(saveBlocked));

        EditResult result = saveBlockedEditor.applyScalar(setting, Long.toString(attemptedValue));

        assertInstanceOf(EditResult.Invalid.class, result);
        assertEquals(persistedValue, plugin.getConfig().getLong(setting.path()));
    }

    @Test
    void confirmedLoggingBooleanWriteUpdatesTheLiveCacheCallback() {
        ConfigSetting setting = ConfigRegistry.byPath(LoggingPaths.CORE_CONFIG_CHANGED).orElseThrow();
        AtomicReference<String> changedPath = new AtomicReference<>();
        AtomicReference<Boolean> changedValue = new AtomicReference<>();
        ConfigValueEditor callbackEditor = new ConfigValueEditor(plugin, resourceHuntItems,
                new WorldProfileTable(plugin), (path, value) -> {
                    changedPath.set(path);
                    changedValue.set(value);
                });

        EditResult result = callbackEditor.applyScalar(setting, "true");

        assertInstanceOf(EditResult.Ok.class, result);
        assertEquals(LoggingPaths.CORE_CONFIG_CHANGED, changedPath.get());
        assertEquals(Boolean.TRUE, changedValue.get());
    }

    @Test
    void genericScalarDispatchesLegacyPathsToLegacyWording() {
        // "max-homes" is a sentinel-free but legacy-wording path - applyScalar must route it to
        // setMaxHomes rather than the generic engine, so the response wording stays pinned.
        ConfigSetting setting = ConfigRegistry.byPath("max-homes").orElseThrow();

        EditResult result = editor.applyScalar(setting, "9");

        assertEquals(9, plugin.getConfig().getInt("max-homes"));
        assertTrue(plain(result).contains("Max homes has been updated live to 9"));
    }

    // ------------------------------------------------------------ Generic scalar (string)

    @Test
    void stringSettingWritesAndReadsBackRawValue() {
        ConfigSetting setting = ConfigRegistry.byPath("discord.channel-id").orElseThrow();

        EditResult result = editor.applyScalar(setting, "123456789012345678");

        assertInstanceOf(EditResult.Ok.class, result);
        assertEquals("123456789012345678", plugin.getConfig().getString(setting.path()));
    }

    @Test
    void stringSettingAcceptsEmptyValueAsClear() {
        ConfigSetting setting = ConfigRegistry.byPath("discord.channel-id").orElseThrow();
        editor.applyScalar(setting, "123");

        EditResult result = editor.applyScalar(setting, "");

        assertInstanceOf(EditResult.Ok.class, result);
        assertEquals("", plugin.getConfig().getString(setting.path()));
    }

    @Test
    void failedStringSaveRestoresPreviousValue() {
        ConfigSetting setting = ConfigRegistry.byPath("discord.channel-id").orElseThrow();
        plugin.getConfig().set(setting.path(), "old");
        plugin.saveConfig();
        Tweaks saveBlocked = spy(plugin);
        doNothing().when(saveBlocked).saveConfig();
        ConfigValueEditor saveBlockedEditor = new ConfigValueEditor(saveBlocked, resourceHuntItems,
                new WorldProfileTable(saveBlocked));

        EditResult result = saveBlockedEditor.applyScalar(setting, "new");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertEquals("old", plugin.getConfig().getString(setting.path()));
    }

    // ------------------------------------------------------------ List add/remove idempotency

    @Test
    void mobListDisableIsIdempotent() {
        editor.toggleEggDrop("disable", "zombie");
        EditResult second = editor.toggleEggDrop("disable", "zombie");

        assertEquals(1, plugin.getConfig().getStringList("egg-collector-disabled-mobs").size());
        assertTrue(plain(second).toLowerCase().contains("already"));
    }

    @Test
    void mobListEnableOnAbsentEntryIsIdempotent() {
        EditResult result = editor.toggleEggDrop("enable", "zombie"); // never disabled

        assertTrue(plugin.getConfig().getStringList("egg-collector-disabled-mobs").isEmpty());
        assertTrue(plain(result).toLowerCase().contains("already"));
    }

    @Test
    void mobListRejectsUnknownMobWithoutWriting() {
        EditResult result = editor.toggleEggDrop("disable", "not_a_real_mob_xyz");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertTrue(plugin.getConfig().getStringList("egg-collector-disabled-mobs").isEmpty());
    }

    @Test
    void listAddAndListRemoveRouteMobListSentinelToToggle() {
        ConfigSetting setting = ConfigRegistry.byPath(ConfigRegistry.EGGDROP_SENTINEL_PATH).orElseThrow();

        editor.listAdd(setting, "zombie");
        assertTrue(plugin.getConfig().getStringList("egg-collector-disabled-mobs").contains("zombie"));

        editor.listRemove(setting, "zombie");
        assertTrue(plugin.getConfig().getStringList("egg-collector-disabled-mobs").isEmpty());
    }

    @Test
    void currentListValuesReflectsDisabledMobs() {
        editor.toggleEggDrop("disable", "zombie");
        editor.toggleEggDrop("disable", "creeper");

        ConfigSetting setting = ConfigRegistry.byPath(ConfigRegistry.EGGDROP_SENTINEL_PATH).orElseThrow();
        assertEquals(2, editor.currentListValues(setting).size());
    }

    @Test
    void materialGridEditsOneCellAndInvokesTheLateRecipeOwner() {
        ConfigSetting setting = ConfigRegistry.byPath("tools.repair-kit.recipe").orElseThrow();
        AtomicReference<List<String>> handled = new AtomicReference<>();
        editor.setMaterialGridHandler((ignored, cells) -> {
            handled.set(List.copyOf(cells));
            return true;
        });

        EditResult result = editor.materialGridCell(setting, "4", "nether_star");

        assertInstanceOf(EditResult.Ok.class, result);
        assertEquals("nether_star", plugin.getConfig().getStringList(setting.path()).get(4));
        assertEquals("nether_star", handled.get().get(4));
        assertEquals(9, handled.get().size());
    }

    @Test
    void slotCapacityAcceptsFamilyAndMaterialKeys() {
        ConfigSetting setting = ConfigRegistry.byPath("tools.augments.slot-capacity").orElseThrow();

        assertInstanceOf(EditResult.Ok.class, editor.mapPut(setting, "wooden", "4"));
        assertEquals(4, plugin.getConfig().getInt("tools.augments.slot-capacity.wooden"));
        assertInstanceOf(EditResult.Ok.class, editor.mapPut(setting, "netherite_pickaxe", "12"));
        assertEquals(12, plugin.getConfig().getInt("tools.augments.slot-capacity.netherite_pickaxe"));
    }

    @Test
    void slotCapacityRejectsUnknownKeysWithoutWriting() {
        ConfigSetting setting = ConfigRegistry.byPath("tools.augments.slot-capacity").orElseThrow();

        EditResult result = editor.mapPut(setting, "wood", "4");

        assertInstanceOf(EditResult.Invalid.class, result);
        assertTrue(!plugin.getConfig().contains("tools.augments.slot-capacity.wood"));
    }
}
