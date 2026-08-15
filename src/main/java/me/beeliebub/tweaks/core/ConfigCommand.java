package me.beeliebub.tweaks.core;

import me.beeliebub.tweaks.core.config.ConfigCategory;
import me.beeliebub.tweaks.core.config.ConfigGUI;
import me.beeliebub.tweaks.core.config.ConfigRegistry;
import me.beeliebub.tweaks.core.config.ConfigSetting;
import me.beeliebub.tweaks.core.config.ConfigValueEditor;
import me.beeliebub.tweaks.core.config.EditResult;
import me.beeliebub.tweaks.core.config.EditorType;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

// Admin command to change plugin config values at runtime. Dispatch only: permission gate ->
// no-args/gui -> ConfigGUI for a Player / plain usage for console -> the six pre-existing legacy
// CLI forms (unchanged wording, ConfigCommandTest pins them) -> otherwise the generic
// ConfigRegistry-driven `/tconfig <path> ...` grammar. All parse/validate/write logic lives in
// ConfigValueEditor - this class only extracts args and sends back the resulting message.
public class ConfigCommand implements CommandExecutor, TabCompleter {

    private static final String EGG_DROP_DISABLED_KEY = "egg-collector-disabled-mobs";
    private static final String SPAWNER_EGG_DISABLED_KEY = "spawner-egg-disabled-mobs";
    private static final String SPAWN_EGG_SUFFIX = "_spawn_egg";

    private static final List<String> LEGACY_TOP_LEVEL_KEYS = List.of(
            "max_homes", "max_chunks", "egg_collector_drop_chance", "eggdrop", "spawneregg", "resourceitems"
    );
    private static final List<String> TOGGLE_ACTIONS = List.of("disable", "enable");
    private static final List<String> LIST_ACTIONS = List.of("add", "remove");
    private static final List<String> WORLD_PROFILE_ACTIONS = List.of("list", "add", "remove", "edit");

    private final Tweaks plugin;
    private final ConfigValueEditor valueEditor;

    public ConfigCommand(Tweaks plugin, ResourceHuntItems resourceHuntItems, WorldProfileTable worldProfileTable) {
        this(plugin, resourceHuntItems, worldProfileTable, (path, value) -> {});
    }

    public ConfigCommand(Tweaks plugin, ResourceHuntItems resourceHuntItems,
                         WorldProfileTable worldProfileTable,
                         java.util.function.BiConsumer<String, Boolean> loggingBooleanChanged) {
        this.plugin = plugin;
        this.valueEditor = new ConfigValueEditor(plugin, resourceHuntItems, worldProfileTable,
                loggingBooleanChanged);
    }

    /** Late-bound Tier-5 recipe owner; keeps core config parsing independent of tools. */
    public void setMaterialGridHandler(java.util.function.BiFunction<ConfigSetting, List<String>, Boolean> handler) {
        valueEditor.setMaterialGridHandler(handler);
    }

    public void addConfigChangedListener(java.util.function.Consumer<String> listener) {
        valueEditor.addConfigChangedListener(listener);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission(Permissions.ADMIN_CONFIG)) {
            sender.sendMessage(Messages.noPermission());
            return true;
        }

        if (args.length < 1) {
            if (sender instanceof Player player) {
                ConfigGUI.openMainMenu(player, valueEditor);
            } else {
                sendUsage(sender, label);
            }
            return true;
        }

        String key = args[0].toLowerCase(Locale.ROOT);

        if (key.equals("gui")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.CONFIG.guiRequiresPlayer());
                return true;
            }
            ConfigGUI.openMainMenu(player, valueEditor);
            return true;
        }

        if (key.equals("list")) {
            handleList(sender, args);
            return true;
        }

        switch (key) {
            case "max_homes" -> handleMaxHomes(sender, label, args);
            case "max_chunks" -> handleMaxChunks(sender, label, args);
            case "egg_collector_drop_chance" -> handleEggDropChance(sender, label, args);
            case "eggdrop" -> handleMobToggle(sender, label, args,
                    "eggdrop", false);
            case "spawneregg" -> handleMobToggle(sender, label, args,
                    "spawneregg", true);
            case "resourceitems" -> handleResourceItems(sender, label, args);
            case "world-profiles" -> handleWorldProfiles(sender, label, args);
            default -> handleGenericPath(sender, label, key, args);
        }

        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        Messages.COMMANDS.configUsage(label).forEach(sender::sendMessage);
        Messages.CONFIG.additionalUsageLines(label).forEach(sender::sendMessage);
    }

    private void sendResult(CommandSender sender, EditResult result) {
        sender.sendMessage(result.message());
    }

    // ------------------------------------------------------------ Legacy (unchanged wording)

    private void handleMaxHomes(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.COMMANDS.configMaxHomesUsage(label));
            return;
        }
        sendResult(sender, valueEditor.setMaxHomes(args[1]));
    }

    private void handleMaxChunks(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.COMMANDS.configMaxChunksUsage(label));
            return;
        }
        sendResult(sender, valueEditor.setMaxChunks(args[1]));
    }

    private void handleEggDropChance(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.COMMANDS.configEggDropChanceUsage(label));
            return;
        }
        sendResult(sender, valueEditor.setEggCollectorDropChance(args[1]));
    }

    private void handleMobToggle(CommandSender sender, String label, String[] args,
                                 String subcommand, boolean spawnerEggFeature) {
        if (args.length < 3) {
            sender.sendMessage(Messages.COMMANDS.configMobToggleUsage(label, spawnerEggFeature));
            return;
        }
        EditResult result = spawnerEggFeature
                ? valueEditor.toggleSpawnerEgg(args[1], args[2])
                : valueEditor.toggleEggDrop(args[1], args[2]);
        sendResult(sender, result);
    }

    private void handleResourceItems(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.COMMANDS.configResourceItemsUsage(label));
            return;
        }
        sendResult(sender, valueEditor.resourceItems(args[1], args[2]));
    }

    // ------------------------------------------------------------ world-profiles (special-cased,
    // like eggdrop/spawneregg/resourceitems - not a ConfigRegistry entry)

    private void handleWorldProfiles(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.CONFIG.worldProfileUsage(label));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> handleWorldProfileList(sender);
            case "add" -> {
                if (args.length < 6) {
                    sender.sendMessage(Messages.CONFIG.worldProfileAddUsage(label));
                    return;
                }
                sendResult(sender, valueEditor.worldProfileAdd(args[2], args[3], args[4], args[5]));
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.CONFIG.worldProfileRemoveUsage(label));
                    return;
                }
                sendResult(sender, valueEditor.worldProfileRemove(args[2]));
            }
            case "edit" -> {
                if (args.length < 5) {
                    sender.sendMessage(Messages.CONFIG.worldProfileEditUsage(label));
                    return;
                }
                sendResult(sender, valueEditor.worldProfileEdit(args[2], args[3], args[4]));
            }
            default -> sender.sendMessage(Messages.CONFIG.worldProfileUsage(label));
        }
    }

    private void handleWorldProfileList(CommandSender sender) {
        List<String> keys = valueEditor.worldProfileKeys();
        if (keys.isEmpty()) {
            sender.sendMessage(Messages.CONFIG.worldProfileListEmpty());
            return;
        }
        for (String key : keys) {
            sender.sendMessage(Messages.CONFIG.worldProfileListEntry(valueEditor.worldProfileEntryDisplay(key)));
        }
    }

    // ------------------------------------------------------------ Generic (future settings)

    private void handleList(CommandSender sender, String[] args) {
        String categoryFilter = args.length >= 2 ? args[1] : null;
        boolean loggingFilter = categoryFilter != null && categoryFilter.equalsIgnoreCase("logging");
        if (categoryFilter != null && !loggingFilter && ConfigRegistry.category(categoryFilter).isEmpty()) {
            sender.sendMessage(Messages.CONFIG.unknownCategory(categoryFilter));
            return;
        }
        for (ConfigCategory category : ConfigRegistry.categories()) {
            if (loggingFilter && category.group() != ConfigCategory.Group.LOGGING) continue;
            if (!loggingFilter && categoryFilter != null && !category.key().equalsIgnoreCase(categoryFilter)) continue;
            sender.sendMessage(Messages.CONFIG.categoryHeader(category.displayName()));
            for (ConfigSetting setting : category.settings()) {
                sender.sendMessage(Messages.CONFIG.settingListEntry(setting.displayName(),
                        valueEditor.currentValueDisplay(setting)));
            }
        }
    }

    private void handleGenericPath(CommandSender sender, String label, String pathKey, String[] args) {
        Optional<ConfigSetting> settingOpt = ConfigRegistry.byPath(pathKey);
        if (settingOpt.isEmpty()) {
            sender.sendMessage(Messages.COMMANDS.configUnknownKey(pathKey));
            return;
        }
        ConfigSetting setting = settingOpt.get();

        switch (setting.type()) {
            case MATERIAL_GRID -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.CONFIG.gridUsage(label, pathKey));
                    return;
                }
                sendResult(sender, valueEditor.materialGridCell(setting, args[1], args[2]));
            }
            case STRING_LIST, WORLD_KEY_LIST, MOB_LIST, MATERIAL_LIST -> {
                if (args.length < 3 || !(args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                    sender.sendMessage(Messages.CONFIG.listUsage(label, pathKey));
                    return;
                }
                EditResult result = args[1].equalsIgnoreCase("add")
                        ? valueEditor.listAdd(setting, args[2])
                        : valueEditor.listRemove(setting, args[2]);
                sendResult(sender, result);
                logLoggingChange(sender, setting, args[2], result);
            }
            case NUMBER_MAP -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.CONFIG.mapUsage(label, pathKey));
                    return;
                }
                EditResult result = valueEditor.mapPut(setting, args[1], args[2]);
                sendResult(sender, result);
            }
            default -> {
                if (args.length < 2) {
                    sender.sendMessage(Messages.CONFIG.scalarUsage(label, pathKey));
                    return;
                }
                EditResult result = valueEditor.applyScalar(setting, args[1]);
                sendResult(sender, result);
                logLoggingChange(sender, setting, args[1], result);
            }
        }
    }

    private void logLoggingChange(CommandSender sender, ConfigSetting setting, String value,
                                  EditResult result) {
        if (!(result instanceof EditResult.Ok) || !setting.path().startsWith("logging.")) return;
        ConsoleEventLog eventLog = plugin.getConsoleEventLog();
        if (eventLog == null) return;
        String actorName = sender instanceof Player player ? player.getName() : null;
        java.util.UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
        eventLog.log(me.beeliebub.tweaks.logging.LoggingPaths.CORE_CONFIG_CHANGED, () ->
                "[Core] " + ConsoleEventLog.actorLabel(actorName, actorId)
                        + " changed " + setting.displayName() + " to " + value);
    }

    private static String normalizeMob(String raw) {
        String mob = raw.toLowerCase(Locale.ROOT);
        if (mob.startsWith("minecraft:")) mob = mob.substring("minecraft:".length());
        return mob;
    }

    private static List<String> spawnEggMobs() {
        return Arrays.stream(Material.values())
                .filter(m -> m.getKey().getKey().endsWith(SPAWN_EGG_SUFFIX))
                .map(m -> {
                    String k = m.getKey().getKey();
                    return k.substring(0, k.length() - SPAWN_EGG_SUFFIX.length());
                })
                .sorted()
                .toList();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_CONFIG)) return Collections.emptyList();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> tokens = new ArrayList<>(LEGACY_TOP_LEVEL_KEYS);
            tokens.add("gui");
            tokens.add("list");
            tokens.add("world-profiles");
            for (String alias : ConfigRegistry.allCliAliases()) {
                if (!tokens.contains(alias)) tokens.add(alias);
            }
            return tokens.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        String key = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2 && key.equals("list")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> categories = new ArrayList<>(ConfigRegistry.categories().stream()
                    .map(ConfigCategory::key)
                    .filter(s -> s.startsWith(prefix))
                    .toList());
            if ("logging".startsWith(prefix)) categories.add("logging");
            return categories;
        }

        if (args.length == 2 && (key.equals("eggdrop") || key.equals("spawneregg"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return TOGGLE_ACTIONS.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        if (args.length == 2 && key.equals("resourceitems")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return LIST_ACTIONS.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        if (args.length == 3 && (key.equals("eggdrop") || key.equals("spawneregg"))) {
            String prefix = normalizeMob(args[2]);
            Stream<String> source = spawnEggMobs().stream();
            // For "enable", suggest currently-disabled mobs first so admins can revert their own changes.
            if (args[1].equalsIgnoreCase("enable")) {
                String configKey = key.equals("eggdrop") ? EGG_DROP_DISABLED_KEY : SPAWNER_EGG_DISABLED_KEY;
                List<String> disabled = plugin.getConfig().getStringList(configKey);
                if (!disabled.isEmpty()) {
                    source = disabled.stream().map(s -> s.toLowerCase(Locale.ROOT));
                }
            }
            return source.filter(s -> s.startsWith(prefix)).toList();
        }

        if (args.length == 3 && key.equals("resourceitems")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return Arrays.stream(Material.values())
                    .map(m -> m.name().toLowerCase(Locale.ROOT))
                    .filter(s -> s.startsWith(prefix))
                    .sorted()
                    .limit(100)
                    .toList();
        }

        if (args.length == 2 && key.equals("world-profiles")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return WORLD_PROFILE_ACTIONS.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        if (args.length == 3 && key.equals("world-profiles")
                && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("edit"))) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return valueEditor.worldProfileKeys().stream().filter(s -> s.startsWith(prefix)).toList();
        }

        if (args.length == 4 && key.equals("world-profiles") && args[1].equalsIgnoreCase("add")) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return valueEditor.worldProfileKnownProfiles().stream().filter(s -> s.startsWith(prefix)).toList();
        }

        if (args.length == 6 && key.equals("world-profiles") && args[1].equalsIgnoreCase("add")) {
            String prefix = args[5].toLowerCase(Locale.ROOT);
            return NamedTextColor.NAMES.keys().stream()
                    .filter(s -> s.startsWith(prefix))
                    .sorted()
                    .toList();
        }

        if (args.length == 5 && key.equals("world-profiles") && args[1].equalsIgnoreCase("edit")) {
            String prefix = args[4].toLowerCase(Locale.ROOT);
            return NamedTextColor.NAMES.keys().stream()
                    .filter(s -> s.startsWith(prefix))
                    .sorted()
                    .toList();
        }

        // Generic `/tconfig <path> add|remove` completion for future list-type settings.
        if (args.length == 2) {
            Optional<ConfigSetting> settingOpt = ConfigRegistry.byPath(key);
            if (settingOpt.isPresent() && isListType(settingOpt.get().type())) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return LIST_ACTIONS.stream().filter(s -> s.startsWith(prefix)).toList();
            }
        }

        return Collections.emptyList();
    }

    private static boolean isListType(EditorType type) {
        return type == EditorType.STRING_LIST || type == EditorType.WORLD_KEY_LIST
                || type == EditorType.MOB_LIST || type == EditorType.MATERIAL_LIST;
    }
}
