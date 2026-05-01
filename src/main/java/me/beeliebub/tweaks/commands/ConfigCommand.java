package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
            "max_homes", "egg_collector_drop_chance", "eggdrop", "spawneregg", "resourceitems"
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

        if (!sender.hasPermission("tweaks.admin.config")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender, label);
            return true;
        }

        String key = args[0].toLowerCase(Locale.ROOT);

        switch (key) {
            case "max_homes" -> handleMaxHomes(sender, label, args);
            case "egg_collector_drop_chance" -> handleEggDropChance(sender, label, args);
            case "eggdrop" -> handleMobToggle(sender, label, args,
                    "eggdrop", EGG_DROP_DISABLED_KEY, "Egg Collector drops");
            case "spawneregg" -> handleMobToggle(sender, label, args,
                    "spawneregg", SPAWNER_EGG_DISABLED_KEY, "Spawn-egg use on spawners");
            case "resourceitems" -> handleResourceItems(sender, label, args);
            default -> sender.sendMessage(Component.text("Unknown config key: " + key).color(NamedTextColor.RED));
        }

        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Usage:").color(NamedTextColor.RED));
        sender.sendMessage(Component.text("  /" + label + " max_homes <int>").color(NamedTextColor.RED));
        sender.sendMessage(Component.text("  /" + label + " egg_collector_drop_chance <0.0-100.0>").color(NamedTextColor.RED));
        sender.sendMessage(Component.text("  /" + label + " eggdrop <disable|enable> <mob>").color(NamedTextColor.RED));
        sender.sendMessage(Component.text("  /" + label + " spawneregg <disable|enable> <mob>").color(NamedTextColor.RED));
        sender.sendMessage(Component.text("  /" + label + " resourceitems <add|remove> <item>").color(NamedTextColor.RED));
    }

    private void handleMaxHomes(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " max_homes <int>").color(NamedTextColor.RED));
            return;
        }
        try {
            int newMax = Integer.parseInt(args[1]);
            if (newMax <= 0) {
                sender.sendMessage(Component.text("Max homes must be a positive integer.").color(NamedTextColor.RED));
                return;
            }
            plugin.getConfig().set("max-homes", newMax);
            plugin.saveConfig();
            sender.sendMessage(Component.text("Max homes has been updated live to " + newMax + "!").color(NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number format. Please provide a valid integer.").color(NamedTextColor.RED));
        }
    }

    private void handleEggDropChance(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " egg_collector_drop_chance <0.0-100.0>").color(NamedTextColor.RED));
            return;
        }
        try {
            double chance = Double.parseDouble(args[1]);
            if (chance < 0.0 || chance > 100.0) {
                sender.sendMessage(Component.text("Drop chance must be between 0.0 and 100.0.").color(NamedTextColor.RED));
                return;
            }
            plugin.getConfig().set("egg-collector-drop-chance", chance);
            plugin.saveConfig();
            sender.sendMessage(Component.text("Egg Collector drop chance has been updated live to " + chance + "%!").color(NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number format. Please provide a valid decimal number.").color(NamedTextColor.RED));
        }
    }

    private void handleMobToggle(CommandSender sender, String label, String[] args,
                                 String subcommand, String configKey, String featureLabel) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /" + label + " " + subcommand + " <disable|enable> <mob>")
                    .color(NamedTextColor.RED));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (!action.equals("disable") && !action.equals("enable")) {
            sender.sendMessage(Component.text("Action must be 'disable' or 'enable'.").color(NamedTextColor.RED));
            return;
        }

        String mob = normalizeMob(args[2]);
        if (Material.matchMaterial(mob + SPAWN_EGG_SUFFIX) == null) {
            sender.sendMessage(Component.text("Unknown mob (no spawn egg exists): " + args[2]).color(NamedTextColor.RED));
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

        if (action.equals("disable")) {
            sender.sendMessage(Component.text(featureLabel + " for '" + mob + "' "
                    + (changed ? "are now DISABLED." : "were already disabled.")).color(NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(featureLabel + " for '" + mob + "' "
                    + (changed ? "are now ENABLED." : "were already enabled.")).color(NamedTextColor.GREEN));
        }
    }

    private void handleResourceItems(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /" + label + " resourceitems <add|remove> <item>")
                    .color(NamedTextColor.RED));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        Material mat = Material.matchMaterial(args[2]);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + args[2]).color(NamedTextColor.RED));
            return;
        }

        if (action.equals("add")) {
            resourceHuntItems.addAllowedItem(mat);
            sender.sendMessage(Component.text("Added '" + mat.name().toLowerCase() + "' to resource world allowed items.", NamedTextColor.GREEN));
        } else if (action.equals("remove")) {
            resourceHuntItems.removeAllowedItem(mat);
            sender.sendMessage(Component.text("Removed '" + mat.name().toLowerCase() + "' from resource world allowed items.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Action must be 'add' or 'remove'.", NamedTextColor.RED));
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
        if (!sender.hasPermission("tweaks.admin.config")) return Collections.emptyList();

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