package me.beeliebub.tweaks.core.config;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.profiles.WorldProfileEntry;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.io.File;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Pure parse/validate/write core for every {@code /tconfig} mutation. Both {@code ConfigCommand}
 * (CLI) and {@code ConfigGUI} (Dialog) call into this class rather than touching
 * {@code plugin.getConfig()} directly themselves - it has no Dialog import so it stays
 * constructible and unit-testable without constructing a Dialog (see this package's CLAUDE.md).
 *
 * <p>The six pre-existing legacy CLI forms (max_homes, max_chunks, egg_collector_drop_chance,
 * eggdrop, spawneregg, resourceitems) keep dedicated methods reproducing their exact historical
 * response wording via {@code Messages.COMMANDS} - a wording change there is a behavior-visible
 * regression pinned by {@code ConfigCommandTest}. Settings beyond those legacy forms go through
 * the generic engine below ({@link #applyScalar},
 * {@link #listAdd}, {@link #listRemove}, {@link #mapPut}) and {@code Messages.CONFIG} instead.
 * {@link #listAdd}/{@link #listRemove} route the three sentinel-path settings back to the legacy
 * toggle/delegate methods so the registry (and therefore the GUI) can still present them uniformly.
 *
 * <p>Every public mutation method is wrapped by {@link #guarded}: unlike the CLI (Bukkit's command
 * dispatcher wraps {@code onCommand} in its own try/catch), a {@code DialogAction.customClick}
 * callback has no such backstop anywhere in this codebase, so an unexpected exception here (e.g. a
 * Dialog text-input field submitted empty - {@code Double.parseDouble(null)} throws
 * {@code NullPointerException}, not {@code NumberFormatException}) would otherwise fail invisibly
 * inside the callback instead of reaching the player and the log.
 */
public final class ConfigValueEditor {

    private static final String EGG_DROP_DISABLED_KEY = "egg-collector-disabled-mobs";
    private static final String SPAWNER_EGG_DISABLED_KEY = "spawner-egg-disabled-mobs";
    private static final String SPAWN_EGG_SUFFIX = "_spawn_egg";

    private final Tweaks plugin;
    private final ResourceHuntItems resourceHuntItems;
    private final WorldProfileTable worldProfileTable;
    private final BiConsumer<String, Boolean> loggingBooleanChanged;
    private BiFunction<ConfigSetting, List<String>, Boolean> materialGridHandler;
    private final List<java.util.function.Consumer<String>> configChangedListeners = new CopyOnWriteArrayList<>();

    public ConfigValueEditor(Tweaks plugin, ResourceHuntItems resourceHuntItems, WorldProfileTable worldProfileTable) {
        this(plugin, resourceHuntItems, worldProfileTable, (path, value) -> {});
    }

    public ConfigValueEditor(Tweaks plugin, ResourceHuntItems resourceHuntItems,
                             WorldProfileTable worldProfileTable,
                             BiConsumer<String, Boolean> loggingBooleanChanged) {
        this.plugin = plugin;
        this.resourceHuntItems = resourceHuntItems;
        this.worldProfileTable = worldProfileTable;
        this.loggingBooleanChanged = loggingBooleanChanged == null ? (path, value) -> {} : loggingBooleanChanged;
    }

    /** Registers the late Tier-5 owner of a MATERIAL_GRID setting without coupling core to tools. */
    public void setMaterialGridHandler(BiFunction<ConfigSetting, List<String>, Boolean> handler) {
        this.materialGridHandler = handler;
    }

    public void addConfigChangedListener(java.util.function.Consumer<String> listener) {
        if (listener != null) configChangedListeners.add(listener);
    }

    private EditResult guarded(Supplier<EditResult> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Unexpected error handling a /tconfig edit.", e);
            return new EditResult.Invalid(Messages.CONFIG.unexpectedError());
        }
    }

    // ------------------------------------------------------------ Legacy (verbatim wording)

    public EditResult setMaxHomes(String rawValue) {
        return guarded(() -> {
            int newMax;
            try {
                newMax = Integer.parseInt(rawValue);
            } catch (NumberFormatException e) {
                return new EditResult.Invalid(Messages.invalidNumber());
            }
            if (newMax <= 0) {
                return new EditResult.Invalid(Messages.COMMANDS.configMaxHomesMustBePositive());
            }
            if (!writeAndVerify("max-homes", newMax)) {
                return new EditResult.Invalid(Messages.CONFIG.saveFailed("Max Homes"));
            }
            return new EditResult.Ok(Messages.COMMANDS.configMaxHomesUpdated(newMax));
        });
    }

    public EditResult setMaxChunks(String rawValue) {
        return guarded(() -> {
            int newMax;
            try {
                newMax = Integer.parseInt(rawValue);
            } catch (NumberFormatException e) {
                return new EditResult.Invalid(Messages.invalidNumber());
            }
            if (newMax < 1) {
                return new EditResult.Invalid(Messages.COMMANDS.configMaxChunksMustBePositive());
            }
            if (!writeAndVerify("max_chunks", newMax)) {
                return new EditResult.Invalid(Messages.CONFIG.saveFailed("Max Chunks"));
            }
            return new EditResult.Ok(Messages.COMMANDS.configMaxChunksUpdated(newMax));
        });
    }

    public EditResult setEggCollectorDropChance(String rawValue) {
        return guarded(() -> {
            double chance;
            try {
                chance = Double.parseDouble(rawValue);
            } catch (NumberFormatException | NullPointerException e) {
                return new EditResult.Invalid(Messages.invalidDecimal());
            }
            if (chance < 0.0 || chance > 100.0) {
                return new EditResult.Invalid(Messages.COMMANDS.configEggDropChanceRange());
            }
            if (!writeAndVerify("egg-collector-drop-chance", chance)) {
                return new EditResult.Invalid(Messages.CONFIG.saveFailed("Egg Collector Drop Chance"));
            }
            return new EditResult.Ok(Messages.COMMANDS.configEggDropChanceUpdated(chance));
        });
    }

    public EditResult toggleEggDrop(String action, String rawMob) {
        return guarded(() -> toggleMobList(action, rawMob, EGG_DROP_DISABLED_KEY, false));
    }

    public EditResult toggleSpawnerEgg(String action, String rawMob) {
        return guarded(() -> toggleMobList(action, rawMob, SPAWNER_EGG_DISABLED_KEY, true));
    }

    private EditResult toggleMobList(String action, String rawMob, String configKey, boolean spawnerEggFeature) {
        if (action == null || rawMob == null) {
            return new EditResult.Invalid(Messages.COMMANDS.configMobToggleActionInvalid());
        }
        String normalizedAction = action.toLowerCase(Locale.ROOT);
        if (!normalizedAction.equals("disable") && !normalizedAction.equals("enable")) {
            return new EditResult.Invalid(Messages.COMMANDS.configMobToggleActionInvalid());
        }

        String mob = normalizeMob(rawMob);
        if (Material.matchMaterial(mob + SPAWN_EGG_SUFFIX) == null) {
            return new EditResult.Invalid(Messages.COMMANDS.configMobUnknown(rawMob));
        }

        // getStringList returns an empty list when the key is missing; the set() call below
        // creates the section automatically, so admins never need to touch config.yml by hand.
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(configKey));
        boolean changed;
        if (normalizedAction.equals("disable")) {
            if (list.stream().anyMatch(s -> s.equalsIgnoreCase(mob))) {
                changed = false;
            } else {
                list.add(mob);
                changed = true;
            }
        } else {
            changed = list.removeIf(s -> s.equalsIgnoreCase(mob));
        }

        if (!writeAndVerify(configKey, list)) {
            return new EditResult.Invalid(Messages.CONFIG.saveFailed(
                    spawnerEggFeature ? "Spawner Egg Disabled Mobs" : "Egg Collector Disabled Mobs"));
        }

        return new EditResult.Ok(Messages.COMMANDS.configMobToggleResult(
                spawnerEggFeature, mob, normalizedAction.equals("disable"), changed));
    }

    public EditResult resourceItems(String action, String rawMaterial) {
        return guarded(() -> {
            Material mat = rawMaterial == null ? null : Material.matchMaterial(rawMaterial);
            if (mat == null) {
                return new EditResult.Invalid(Messages.COMMANDS.configUnknownMaterial(String.valueOf(rawMaterial)));
            }

            String normalizedAction = action == null ? "" : action.toLowerCase(Locale.ROOT);
            if (normalizedAction.equals("add")) {
                resourceHuntItems.addAllowedItem(mat);
                if (resourceHuntItems.lastSaveFailed()) {
                    return new EditResult.Invalid(Messages.COMMANDS.configResourceItemsSaveFailed());
                }
                return new EditResult.Ok(Messages.COMMANDS.configResourceItemAdded(mat.name().toLowerCase(Locale.ROOT)));
            } else if (normalizedAction.equals("remove")) {
                resourceHuntItems.removeAllowedItem(mat);
                if (resourceHuntItems.lastSaveFailed()) {
                    return new EditResult.Invalid(Messages.COMMANDS.configResourceItemsSaveFailed());
                }
                return new EditResult.Ok(Messages.COMMANDS.configResourceItemRemoved(mat.name().toLowerCase(Locale.ROOT)));
            }
            return new EditResult.Invalid(Messages.COMMANDS.configResourceItemActionInvalid());
        });
    }

    private static String normalizeMob(String raw) {
        String mob = raw.toLowerCase(Locale.ROOT);
        if (mob.startsWith("minecraft:")) mob = mob.substring("minecraft:".length());
        return mob;
    }

    // ------------------------------------------------------------ Generic engine (future settings)

    /** Applies a scalar edit, dispatching to legacy wording for the three settings that demand it. */
    public EditResult applyScalar(ConfigSetting setting, String rawValue) {
        return switch (setting.path()) {
            case "max-homes" -> setMaxHomes(rawValue);
            case "max_chunks" -> setMaxChunks(rawValue);
            case "egg-collector-drop-chance" -> setEggCollectorDropChance(rawValue);
            default -> guarded(() -> setScalarGeneric(setting, rawValue));
        };
    }

    private EditResult setScalarGeneric(ConfigSetting setting, String rawValue) {
        return switch (setting.type()) {
            case INT -> setBoundedNumber(setting, rawValue, true);
            case LONG -> setLong(setting, rawValue);
            case DOUBLE, PERCENT -> setBoundedNumber(setting, rawValue, false);
            case BOOLEAN -> setBoolean(setting, rawValue);
            case MATERIAL -> setMaterial(setting, rawValue);
            case NAMESPACED_KEY -> setNamespacedKey(setting, rawValue);
            case STRING -> setString(setting, rawValue);
            default -> new EditResult.Invalid(Messages.CONFIG.notAScalarSetting(setting.displayName()));
        };
    }

    private EditResult setString(ConfigSetting setting, String rawValue) {
        String value = rawValue == null ? "" : rawValue;
        if (setting.path().equals("tools.cash-item.pdc-key")) {
            if (value.isBlank() || NamespacedKey.fromString("tweaks:" + value) == null) {
                return new EditResult.Invalid(Messages.CONFIG.invalidNamespacedKey(value));
            }
        }
        if (!writeAndVerify(setting.path(), value)) {
            return new EditResult.Invalid(Messages.CONFIG.saveFailed(setting.displayName()));
        }
        return new EditResult.Ok(Messages.CONFIG.updated(setting.displayName(), value));
    }

    private EditResult setBoundedNumber(ConfigSetting setting, String rawValue, boolean integral) {
        Object value;
        double parsed;
        try {
            if (integral) {
                long integer = Long.parseLong(rawValue);
                parsed = integer;
                value = integer >= Integer.MIN_VALUE && integer <= Integer.MAX_VALUE
                        ? (int) integer : integer;
            } else {
                parsed = Double.parseDouble(rawValue);
                value = parsed;
            }
        } catch (NumberFormatException | NullPointerException e) {
            return new EditResult.Invalid(integral ? Messages.invalidNumber() : Messages.invalidDecimal());
        }
        if (Double.isNaN(parsed) || Double.isInfinite(parsed) || outOfBounds(setting, parsed)) {
            return new EditResult.Invalid(Messages.CONFIG.outOfRange(setting.displayName(), setting.min(), setting.max()));
        }
        if (!writeAndVerify(setting.path(), value)) {
            return new EditResult.Invalid(Messages.CONFIG.saveFailed(setting.displayName()));
        }
        return new EditResult.Ok(Messages.CONFIG.updated(setting.displayName(), String.valueOf(value)));
    }

    private EditResult setLong(ConfigSetting setting, String rawValue) {
        long parsed;
        try {
            parsed = Long.parseLong(rawValue);
        } catch (NumberFormatException e) {
            return new EditResult.Invalid(Messages.invalidNumber());
        }
        if (outOfBounds(setting, parsed)) {
            return new EditResult.Invalid(Messages.CONFIG.outOfRange(setting.displayName(), setting.min(), setting.max()));
        }
        if (!writeAndVerify(setting.path(), parsed)) {
            return new EditResult.Invalid(Messages.CONFIG.saveFailed(setting.displayName()));
        }
        return new EditResult.Ok(Messages.CONFIG.updated(setting.displayName(), String.valueOf(parsed)));
    }

    private static boolean outOfBounds(ConfigSetting setting, double value) {
        return (setting.min() != null && value < setting.min()) || (setting.max() != null && value > setting.max());
    }

    private EditResult setBoolean(ConfigSetting setting, String rawValue) {
        if (!"true".equalsIgnoreCase(rawValue) && !"false".equalsIgnoreCase(rawValue)) {
            return new EditResult.Invalid(Messages.CONFIG.invalidBoolean());
        }
        boolean value = Boolean.parseBoolean(rawValue);
        if (!writeAndVerify(setting.path(), value)) {
            return new EditResult.Invalid(Messages.CONFIG.saveFailed(setting.displayName()));
        }
        if (setting.path().startsWith("logging.")) {
            loggingBooleanChanged.accept(setting.path(), value);
        }
        return new EditResult.Ok(Messages.CONFIG.updated(setting.displayName(), String.valueOf(value)));
    }

    private static final String PROTECTION_SELECTION_TOOL_PATH = "protection.selection-tool";

    private EditResult setMaterial(ConfigSetting setting, String rawValue) {
        Material mat = rawValue == null ? null : Material.matchMaterial(rawValue);
        // A MATERIAL setting always represents an item a player holds/interacts with, never a
        // block-only or non-item material - Material#isItem() is Paper's own check for "has an
        // item form," but AIR/CAVE_AIR/VOID_AIR all report isItem() == true (an empty ItemStack
        // is legal) despite not being holdable, so isAir() must be excluded separately.
        if (mat == null || mat.isAir() || !mat.isItem()) {
            return new EditResult.Invalid(Messages.COMMANDS.configUnknownMaterial(String.valueOf(rawValue)));
        }
        if ((setting.path().equals("tools.augments.gem-material")
                || setting.path().equals("tools.repair-kit.material")) && mat.isBlock()) {
            return new EditResult.Invalid(Messages.COMMANDS.configUnknownMaterial(String.valueOf(rawValue)));
        }
        if (!writeAndVerify(setting.path(), mat.name())) {
            return new EditResult.Invalid(Messages.CONFIG.saveFailed(setting.displayName()));
        }
        // protection.selection-tool is also cached live in Services (the one overwritable field -
        // see its Javadoc) so ProtectionListeners' wand check doesn't need its own live re-read.
        if (setting.path().equals(PROTECTION_SELECTION_TOOL_PATH)) {
            plugin.setProtectionSelectionTool(mat);
        }
        return new EditResult.Ok(Messages.CONFIG.updated(setting.displayName(), mat.name()));
    }

    private EditResult setNamespacedKey(ConfigSetting setting, String rawValue) {
        NamespacedKey key = rawValue == null ? null : NamespacedKey.fromString(rawValue.toLowerCase(Locale.ROOT));
        if (key == null) {
            return new EditResult.Invalid(Messages.CONFIG.invalidNamespacedKey(String.valueOf(rawValue)));
        }
        if (!writeAndVerify(setting.path(), key.toString())) {
            return new EditResult.Invalid(Messages.CONFIG.saveFailed(setting.displayName()));
        }
        return new EditResult.Ok(Messages.CONFIG.updated(setting.displayName(), key.toString()));
    }

    /** Edits one positional cell in a nine-cell crafting recipe. */
    public EditResult materialGridCell(ConfigSetting setting, String rawIndex, String rawMaterial) {
        return guarded(() -> {
            if (setting.type() != EditorType.MATERIAL_GRID) {
                return new EditResult.Invalid(Messages.CONFIG.notAListSetting(setting.displayName()));
            }
            int index;
            try {
                index = Integer.parseInt(rawIndex);
            } catch (NumberFormatException | NullPointerException e) {
                return new EditResult.Invalid(Messages.invalidNumber());
            }
            if (index < 0 || index >= 9) {
                return new EditResult.Invalid(Messages.CONFIG.gridCellRange());
            }
            Material material = "air".equalsIgnoreCase(String.valueOf(rawMaterial))
                    ? Material.AIR : Material.matchMaterial(rawMaterial);
            if (material == null || (!material.isAir() && (!material.isItem() || material == Material.AIR))) {
                return new EditResult.Invalid(Messages.COMMANDS.configUnknownMaterial(String.valueOf(rawMaterial)));
            }

            List<String> previous = currentGridValues(setting);
            List<String> next = new ArrayList<>(previous);
            next.set(index, material.isAir() ? "air" : material.name().toLowerCase(Locale.ROOT));
            if (!writeAndVerify(setting.path(), next)) {
                return new EditResult.Invalid(Messages.CONFIG.saveFailed(setting.displayName()));
            }
            if (materialGridHandler != null && !Boolean.TRUE.equals(materialGridHandler.apply(setting, List.copyOf(next)))) {
                writeAndVerify(setting.path(), previous);
                return new EditResult.Invalid(Messages.CONFIG.recipeRejected());
            }
            return new EditResult.Ok(Messages.CONFIG.gridCellUpdated(index, next.get(index)));
        });
    }

    public EditResult listAdd(ConfigSetting setting, String rawValue) {
        if (setting.path().equals(ConfigRegistry.EGGDROP_SENTINEL_PATH)) return toggleEggDrop("disable", rawValue);
        if (setting.path().equals(ConfigRegistry.SPAWNEREGG_SENTINEL_PATH)) return toggleSpawnerEgg("disable", rawValue);
        if (setting.path().equals(ConfigRegistry.RESOURCEITEMS_SENTINEL_PATH)) return resourceItems("add", rawValue);
        return guarded(() -> mutateStringList(setting, rawValue, true));
    }

    public EditResult listRemove(ConfigSetting setting, String rawValue) {
        if (setting.path().equals(ConfigRegistry.EGGDROP_SENTINEL_PATH)) return toggleEggDrop("enable", rawValue);
        if (setting.path().equals(ConfigRegistry.SPAWNEREGG_SENTINEL_PATH)) return toggleSpawnerEgg("enable", rawValue);
        if (setting.path().equals(ConfigRegistry.RESOURCEITEMS_SENTINEL_PATH)) return resourceItems("remove", rawValue);
        return guarded(() -> mutateStringList(setting, rawValue, false));
    }

    private EditResult mutateStringList(ConfigSetting setting, String rawValue, boolean adding) {
        if (rawValue == null) {
            return new EditResult.Invalid(Messages.CONFIG.notAScalarSetting(setting.displayName()));
        }
        boolean lowercase = setting.type() == EditorType.WORLD_KEY_LIST;
        String value = lowercase ? rawValue.toLowerCase(Locale.ROOT) : rawValue;
        if (setting.type() == EditorType.MATERIAL_LIST) {
            Material material = Material.matchMaterial(value);
            if (material == null || material.isAir() || !material.isItem()) {
                return new EditResult.Invalid(Messages.COMMANDS.configUnknownMaterial(rawValue));
            }
            value = material.name().toLowerCase(Locale.ROOT);
        }
        final String normalizedValue = value;

        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(setting.path()));
        boolean changed;
        if (adding) {
            changed = list.stream().noneMatch(s -> s.equalsIgnoreCase(normalizedValue));
            if (changed) list.add(normalizedValue);
        } else {
            changed = list.removeIf(s -> s.equalsIgnoreCase(normalizedValue));
        }

        if (!writeAndVerify(setting.path(), list)) {
            return new EditResult.Invalid(Messages.CONFIG.saveFailed(setting.displayName()));
        }
        return new EditResult.Ok(adding
                ? Messages.CONFIG.listEntryAdded(setting.displayName(), normalizedValue, changed)
                : Messages.CONFIG.listEntryRemoved(setting.displayName(), normalizedValue, changed));
    }

    public EditResult mapPut(ConfigSetting setting, String rawKey, String rawValue) {
        return guarded(() -> {
            if (setting.type() != EditorType.NUMBER_MAP) {
                return new EditResult.Invalid(Messages.CONFIG.notAMapSetting(setting.displayName()));
            }
            // ConfigurationSection#set treats '.' as a path separator - an unsanitized key
            // containing one would silently create a nested section instead of one flat map
            // entry, while this method's own confirmation message would still echo it back as
            // if it were written flat. Reject rather than silently diverge from what's on disk.
            if (rawKey == null || rawKey.isBlank() || rawKey.contains(".")) {
                return new EditResult.Invalid(Messages.CONFIG.invalidMapKey(String.valueOf(rawKey)));
            }
            // For NUMBER_MAP settings, min()/max() bound the valid *key* (e.g. streak day 1-7),
            // not a value - reusing the same bounds fields the scalar path uses for value bounds.
            if (setting.min() != null || setting.max() != null) {
                double keyAsNumber;
                try {
                    keyAsNumber = Double.parseDouble(rawKey);
                } catch (NumberFormatException e) {
                    return new EditResult.Invalid(Messages.CONFIG.outOfRange(setting.displayName(), setting.min(), setting.max()));
                }
                if (outOfBounds(setting, keyAsNumber)) {
                    return new EditResult.Invalid(Messages.CONFIG.outOfRange(setting.displayName(), setting.min(), setting.max()));
                }
            }
            double value;
            try {
                value = Double.parseDouble(rawValue);
            } catch (NumberFormatException | NullPointerException e) {
                return new EditResult.Invalid(Messages.invalidDecimal());
            }
            // Mirrors setBoundedNumber's finite check - a NaN/Infinite value here (e.g.
            // economy.streak-multipliers) would otherwise flow straight into balance math and
            // permanently poison a player's balance to NaN/Infinity.
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return new EditResult.Invalid(Messages.invalidDecimal());
            }
            if (setting.path().equals("tools.augments.slot-prices")
                    || setting.path().equals("tools.augments.slot-capacity")
                    || setting.path().equals("tools.augments.quality-slot-cost")) {
                if (value < (setting.path().endsWith("quality-slot-cost") ? 1 : 0)
                        || value != Math.rint(value)) {
                    return new EditResult.Invalid(Messages.invalidDecimal());
                }
                String normalizedKey = rawKey.toLowerCase(Locale.ROOT);
                if (setting.path().equals("tools.augments.slot-prices")) {
                    try {
                        int level = Integer.parseInt(rawKey);
                        if (level < 1 || level > 64) {
                            return new EditResult.Invalid(Messages.CONFIG.invalidMapKey(rawKey));
                        }
                        normalizedKey = Integer.toString(level);
                    } catch (NumberFormatException e) {
                        return new EditResult.Invalid(Messages.CONFIG.invalidMapKey(rawKey));
                    }
                } else if (setting.path().equals("tools.augments.quality-slot-cost")
                        && !Set.of("none", "uncommon", "rare", "epic", "legendary").contains(normalizedKey)) {
                    return new EditResult.Invalid(Messages.CONFIG.invalidMapKey(rawKey));
                }
                Object persistedValue = (long) value;
                String path = setting.path() + "." + normalizedKey;
                if (!writeAndVerify(path, persistedValue)) {
                    return new EditResult.Invalid(Messages.CONFIG.saveFailed(setting.displayName()));
                }
                return new EditResult.Ok(Messages.CONFIG.mapEntryUpdated(setting.displayName(), normalizedKey, value));
            }
            String path = setting.path() + "." + rawKey;
            if (!writeAndVerify(path, value)) {
                return new EditResult.Invalid(Messages.CONFIG.saveFailed(setting.displayName()));
            }
            return new EditResult.Ok(Messages.CONFIG.mapEntryUpdated(setting.displayName(), rawKey, value));
        });
    }

    /**
     * Persists one config value and confirms it by loading the file independently of the live
     * configuration object. Bukkit's {@code saveConfig()} swallows its {@code IOException}, so
     * an in-memory re-read would report success even when the old value is still on disk.
     */
    private boolean writeAndVerify(String path, Object value) {
        Object previous = plugin.getConfig().get(path);
        plugin.getConfig().set(path, value);
        plugin.saveConfig();

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (configFile.isFile()) {
            var persisted = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile)
                    .get(path);
            if (configurationValuesEqual(value, persisted)) {
                for (java.util.function.Consumer<String> listener : configChangedListeners) {
                    try {
                        listener.accept(path);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.WARNING, "Config change listener failed for " + path, e);
                    }
                }
                return true;
            }
        }

        plugin.getConfig().set(path, previous);
        return false;
    }

    private static boolean configurationValuesEqual(Object expected, Object actual) {
        if (expected instanceof Number expectedNumber && actual instanceof Number actualNumber) {
            return numericValuesEqual(expectedNumber, actualNumber);
        }
        if (expected instanceof List<?> expectedList && actual instanceof List<?> actualList) {
            if (expectedList.size() != actualList.size()) return false;
            for (int i = 0; i < expectedList.size(); i++) {
                if (!configurationValuesEqual(expectedList.get(i), actualList.get(i))) return false;
            }
            return true;
        }
        return Objects.equals(expected, actual);
    }

    private static boolean numericValuesEqual(Number expected, Number actual) {
        if (isFloatingPoint(expected) || isFloatingPoint(actual)) {
            double expectedDouble = expected.doubleValue();
            double actualDouble = actual.doubleValue();
            if (!Double.isFinite(expectedDouble) || !Double.isFinite(actualDouble)) {
                return Double.compare(expectedDouble, actualDouble) == 0;
            }
            return decimalValue(expected).compareTo(decimalValue(actual)) == 0;
        }
        return integralValue(expected).equals(integralValue(actual));
    }

    private static boolean isFloatingPoint(Number value) {
        return value instanceof Float || value instanceof Double || value instanceof BigDecimal;
    }

    private static BigDecimal decimalValue(Number value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof BigInteger integer) return new BigDecimal(integer);
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(value.longValue());
        }
        return new BigDecimal(value.toString());
    }

    private static BigInteger integralValue(Number value) {
        if (value instanceof BigInteger integer) return integer;
        if (value instanceof BigDecimal decimal) return decimal.toBigIntegerExact();
        return BigInteger.valueOf(value.longValue());
    }

    // ------------------------------------------------------------ world-profiles (special-cased,
    // like eggdrop/spawneregg/resourceitems - not a ConfigRegistry entry. See core/config/CLAUDE.md.)

    /** Adds a brand-new world-key entry. Profile is specified up front - there's no existing data to orphan for a key that never existed. */
    public EditResult worldProfileAdd(String worldKey, String profile, String label, String colorName) {
        return guarded(() -> {
            // NamespacedKey.fromString both validates the real Bukkit world-key charset
            // ([a-z0-9._-]+:[a-z0-9._/-]+) and normalizes case - an unrestricted worldKey could
            // otherwise carry characters with no legitimate use here.
            NamespacedKey parsedKey = worldKey == null ? null : NamespacedKey.fromString(worldKey.toLowerCase(Locale.ROOT));
            if (parsedKey == null) {
                return new EditResult.Invalid(Messages.CONFIG.worldProfileInvalidWorldKey());
            }
            String key = parsedKey.toString();
            if (worldProfileTable.entry(key).isPresent()) {
                return new EditResult.Invalid(Messages.CONFIG.worldProfileDuplicateKey(key));
            }
            for (String existing : worldProfileTable.worldKeysOrdered()) {
                if (!isDimensionVariant(key, existing) && (key.contains(existing) || existing.contains(key))) {
                    return new EditResult.Invalid(Messages.CONFIG.worldProfileCollidesWith(key, existing));
                }
            }
            if (profile == null || profile.isBlank() || profile.contains(".")) {
                return new EditResult.Invalid(Messages.CONFIG.worldProfileInvalidProfile());
            }
            if (label == null || label.isBlank() || label.contains("[") || label.contains("]") || label.contains(".")) {
                return new EditResult.Invalid(Messages.CONFIG.worldProfileInvalidLabel());
            }
            NamedTextColor color = parseColor(colorName);
            if (color == null) {
                return new EditResult.Invalid(Messages.CONFIG.worldProfileInvalidColor(String.valueOf(colorName)));
            }
            worldProfileTable.putEntry(key, profile, label, color);
            return new EditResult.Ok(Messages.CONFIG.worldProfileAdded(key, profile, label, colorName));
        });
    }

    private static final List<String> DIMENSION_SUFFIXES = List.of("_nether", "_the_end");

    /**
     * True when {@code a} and {@code b} are the same base world key with one of Bukkit's standard
     * per-dimension suffixes appended to the other (e.g. {@code jass:resource} /
     * {@code jass:resource_nether}) - the one substring relationship between two world keys that is
     * expected and intentional rather than a likely admin typo, since Bukkit derives a world's
     * nether/end dimension folder by appending exactly one of these two suffixes to its base name.
     * Any other substring relationship stays rejected by the collision check above: an unrelated
     * prefix/superstring pair (e.g. {@code jass:res} alongside {@code jass:resource}) would leave a
     * currently-unregistered third world's key resolution silently dependent on list declaration
     * order in {@link WorldProfileTable#profileFor}'s substring-fallback tier.
     */
    private static boolean isDimensionVariant(String a, String b) {
        for (String suffix : DIMENSION_SUFFIXES) {
            if (a.equals(b + suffix) || b.equals(a + suffix)) return true;
        }
        return false;
    }

    public EditResult worldProfileRemove(String worldKey) {
        return guarded(() -> {
            if (worldKey == null || worldKey.isBlank()) {
                return new EditResult.Invalid(Messages.CONFIG.worldProfileInvalidWorldKey());
            }
            String key = worldKey.toLowerCase(Locale.ROOT);
            if (worldProfileTable.entry(key).isEmpty()) {
                return new EditResult.Invalid(Messages.CONFIG.worldProfileNotFound(key));
            }
            worldProfileTable.removeEntry(key);
            return new EditResult.Ok(Messages.CONFIG.worldProfileRemoved(key));
        });
    }

    /** Edits only tag/color of an existing entry - this method has no profile parameter at all, so renaming an entry's profile (and orphaning its ec_/xp_ storage keys) is structurally impossible here. */
    public EditResult worldProfileEdit(String worldKey, String label, String colorName) {
        return guarded(() -> {
            if (worldKey == null || worldKey.isBlank()) {
                return new EditResult.Invalid(Messages.CONFIG.worldProfileInvalidWorldKey());
            }
            String key = worldKey.toLowerCase(Locale.ROOT);
            if (worldProfileTable.entry(key).isEmpty()) {
                return new EditResult.Invalid(Messages.CONFIG.worldProfileNotFound(key));
            }
            if (label == null || label.isBlank() || label.contains("[") || label.contains("]") || label.contains(".")) {
                return new EditResult.Invalid(Messages.CONFIG.worldProfileInvalidLabel());
            }
            NamedTextColor color = parseColor(colorName);
            if (color == null) {
                return new EditResult.Invalid(Messages.CONFIG.worldProfileInvalidColor(String.valueOf(colorName)));
            }
            worldProfileTable.updateDisplay(key, label, color);
            return new EditResult.Ok(Messages.CONFIG.worldProfileUpdated(key));
        });
    }

    private static NamedTextColor parseColor(String raw) {
        if (raw == null) return null;
        return NamedTextColor.NAMES.value(raw.toLowerCase(Locale.ROOT));
    }

    public List<String> worldProfileKeys() {
        return worldProfileTable.worldKeysOrdered();
    }

    public List<String> worldProfileKnownProfiles() {
        return worldProfileTable.knownProfilesSorted();
    }

    public String worldProfileEntryDisplay(String worldKey) {
        Optional<WorldProfileEntry> entry = worldProfileTable.entry(worldKey);
        if (entry.isEmpty()) return "";
        WorldProfileEntry e = entry.get();
        String colorName = NamedTextColor.NAMES.key(e.color());
        // Includes profile - it's only settable at add-time (config-file-only afterward), so this
        // is the only place an admin can see which profile an existing world key is mapped to
        // without opening config.yml by hand.
        return e.worldKey() + " -> profile '" + e.profile() + "', [" + e.tagText() + "] ("
                + (colorName == null ? "" : colorName) + ")";
    }

    // ------------------------------------------------------------ Read helpers (GUI display)

    public String currentValueDisplay(ConfigSetting setting) {
        return switch (setting.type()) {
            case STRING_LIST, WORLD_KEY_LIST, MOB_LIST, MATERIAL_LIST -> String.join(", ", currentListValues(setting));
            case MATERIAL_GRID -> {
                List<String> values = currentGridValues(setting);
                yield String.join(", ", values);
            }
            case NUMBER_MAP -> {
                var section = plugin.getConfig().getConfigurationSection(setting.path());
                yield section == null ? "" : String.join(", ", section.getKeys(false));
            }
            default -> String.valueOf(plugin.getConfig().get(setting.path()));
        };
    }

    /** Sorted {@code key -> "key: value"} display rows for a NUMBER_MAP setting's GUI screen. */
    public List<String> currentMapEntryLabels(ConfigSetting setting) {
        var section = plugin.getConfig().getConfigurationSection(setting.path());
        if (section == null) return List.of();
        return section.getKeys(false).stream()
                .sorted()
                .map(key -> key + ": " + section.getDouble(key))
                .toList();
    }

    /** The raw sorted keys backing {@link #currentMapEntryLabels}, for building edit callbacks. */
    public List<String> currentMapKeys(ConfigSetting setting) {
        var section = plugin.getConfig().getConfigurationSection(setting.path());
        return section == null ? List.of() : section.getKeys(false).stream().sorted().toList();
    }

    /** A single NUMBER_MAP entry's current value, for the per-entry edit screen's body text. */
    public String currentMapEntryValue(ConfigSetting setting, String key) {
        var section = plugin.getConfig().getConfigurationSection(setting.path());
        return section == null ? "" : String.valueOf(section.getDouble(key));
    }

    public List<String> currentListValues(ConfigSetting setting) {
        if (setting.path().equals(ConfigRegistry.RESOURCEITEMS_SENTINEL_PATH)) {
            return resourceHuntItems.getAllowedItems().stream()
                    .map(m -> m.name().toLowerCase(Locale.ROOT))
                    .sorted()
                    .toList();
        }
        if (setting.path().equals(ConfigRegistry.EGGDROP_SENTINEL_PATH)) {
            return plugin.getConfig().getStringList(EGG_DROP_DISABLED_KEY).stream().sorted().toList();
        }
        if (setting.path().equals(ConfigRegistry.SPAWNEREGG_SENTINEL_PATH)) {
            return plugin.getConfig().getStringList(SPAWNER_EGG_DISABLED_KEY).stream().sorted().toList();
        }
        return plugin.getConfig().getStringList(setting.path()).stream().sorted().toList();
    }

    /** Returns exactly nine lower-case material names, with missing cells represented by air. */
    public List<String> currentGridValues(ConfigSetting setting) {
        List<String> configured = plugin.getConfig().getStringList(setting.path());
        List<String> values = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            String value = i < configured.size() ? configured.get(i) : "air";
            values.add(value == null || value.isBlank() ? "air" : value.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(values);
    }
}
