package me.beeliebub.tweaks.core;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

// Admin command to change plugin config values at runtime
public class ConfigCommand implements CommandExecutor, TabCompleter {

    private static final String EGG_DROP_DISABLED_KEY = "egg-collector-disabled-mobs";
    private static final String SPAWNER_EGG_DISABLED_KEY = "spawner-egg-disabled-mobs";
    private static final String SPAWN_EGG_SUFFIX = "_spawn_egg";

    private static final List<String> TOP_LEVEL_KEYS = List.of(
            "max_homes", "max_chunks", "egg_collector_drop_chance", "eggdrop", "spawneregg", "resourceitems"
    );
    private static final List<String> TOGGLE_ACTIONS = List.of("disable", "enable");
    private static final List<String> LIST_ACTIONS = List.of("add", "remove");

    private final Tweaks plugin;
    private final ResourceHuntItems resourceHuntItems;

    public ConfigCommand(Tweaks plugin, ResourceHuntItems resourceHuntItems) {
        this.plugin = plugin;
        this.resourceHuntItems = resourceHuntItems;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission(Permissions.ADMIN_CONFIG)) {
            sender.sendMessage(Messages.noPermission());
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender, label);
            return true;
        }

        String key = args[0].toLowerCase(Locale.ROOT);

        switch (key) {
            case "max_homes" -> handleMaxHomes(sender, label, args);
            case "max_chunks" -> handleMaxChunks(sender, label, args);
            case "egg_collector_drop_chance" -> handleEggDropChance(sender, label, args);
            case "eggdrop" -> handleMobToggle(sender, label, args,
                    EGG_DROP_DISABLED_KEY, false);
            case "spawneregg" -> handleMobToggle(sender, label, args,
                    SPAWNER_EGG_DISABLED_KEY, true);
            case "resourceitems" -> handleResourceItems(sender, label, args);
            default -> sender.sendMessage(Messages.COMMANDS.configUnknownKey(key));
        }

        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        Messages.COMMANDS.configUsage(label).forEach(sender::sendMessage);
    }

    private void handleMaxHomes(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.COMMANDS.configMaxHomesUsage(label));
            return;
        }
        try {
            int newMax = Integer.parseInt(args[1]);
            if (newMax <= 0) {
                sender.sendMessage(Messages.COMMANDS.configMaxHomesMustBePositive());
                return;
            }
            plugin.getConfig().set("max-homes", newMax);
            plugin.saveConfig();
            sender.sendMessage(Messages.COMMANDS.configMaxHomesUpdated(newMax));
        } catch (NumberFormatException e) {
            sender.sendMessage(Messages.invalidNumber());
        }
    }

    private void handleMaxChunks(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.COMMANDS.configMaxChunksUsage(label));
            return;
        }
        try {
            int newMax = Integer.parseInt(args[1]);
            if (newMax < 1) {
                sender.sendMessage(Messages.COMMANDS.configMaxChunksMustBePositive());
                return;
            }
            plugin.getConfig().set("max_chunks", newMax);
            plugin.saveConfig();
            sender.sendMessage(Messages.COMMANDS.configMaxChunksUpdated(newMax));
        } catch (NumberFormatException e) {
            sender.sendMessage(Messages.invalidNumber());
        }
    }

    private void handleEggDropChance(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.COMMANDS.configEggDropChanceUsage(label));
            return;
        }
        try {
            double chance = Double.parseDouble(args[1]);
            if (chance < 0.0 || chance > 100.0) {
                sender.sendMessage(Messages.COMMANDS.configEggDropChanceRange());
                return;
            }
            plugin.getConfig().set("egg-collector-drop-chance", chance);
            plugin.saveConfig();
            sender.sendMessage(Messages.COMMANDS.configEggDropChanceUpdated(chance));
        } catch (NumberFormatException e) {
            sender.sendMessage(Messages.invalidDecimal());
        }
    }

    private void handleMobToggle(CommandSender sender, String label, String[] args,
                                 String configKey, boolean spawnerEggFeature) {
        if (args.length < 3) {
            sender.sendMessage(Messages.COMMANDS.configMobToggleUsage(label, spawnerEggFeature));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (!action.equals("disable") && !action.equals("enable")) {
            sender.sendMessage(Messages.COMMANDS.configMobToggleActionInvalid());
            return;
        }

        String mob = normalizeMob(args[2]);
        if (Material.matchMaterial(mob + SPAWN_EGG_SUFFIX) == null) {
            sender.sendMessage(Messages.COMMANDS.configMobUnknown(args[2]));
            return;
        }

        // getStringList returns an empty list when the key is missing; the set() call below
        // creates the section automatically, so admins never need to touch config.yml by hand.
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(configKey));
        boolean changed;
        if (action.equals("disable")) {
            if (list.stream().anyMatch(s -> s.equalsIgnoreCase(mob))) {
                changed = false;
            } else {
                list.add(mob);
                changed = true;
            }
        } else {
            changed = list.removeIf(s -> s.equalsIgnoreCase(mob));
        }

        plugin.getConfig().set(configKey, list);
        plugin.saveConfig();

        sender.sendMessage(Messages.COMMANDS.configMobToggleResult(
                spawnerEggFeature, mob, action.equals("disable"), changed));
    }

    private void handleResourceItems(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.COMMANDS.configResourceItemsUsage(label));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        Material mat = Material.matchMaterial(args[2]);
        if (mat == null) {
            sender.sendMessage(Messages.COMMANDS.configUnknownMaterial(args[2]));
            return;
        }

        if (action.equals("add")) {
            resourceHuntItems.addAllowedItem(mat);
            sender.sendMessage(Messages.COMMANDS.configResourceItemAdded(mat.name().toLowerCase()));
        } else if (action.equals("remove")) {
            resourceHuntItems.removeAllowedItem(mat);
            sender.sendMessage(Messages.COMMANDS.configResourceItemRemoved(mat.name().toLowerCase()));
        } else {
            sender.sendMessage(Messages.COMMANDS.configResourceItemActionInvalid());
        }
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
            return TOP_LEVEL_KEYS.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        String key = args[0].toLowerCase(Locale.ROOT);

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

        return Collections.emptyList();
    }
}
