package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.Tweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class ConfigCommand implements CommandExecutor, TabCompleter {

    private final Tweaks plugin;

    public ConfigCommand(Tweaks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("tweaks.admin.config")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " max_homes <value>").color(NamedTextColor.RED));
            return true;
        }

        String key = args[0].toLowerCase();

        switch (key) {
            case "max_homes" -> {
                try {
                    int newMax = Integer.parseInt(args[1]);
                    if (newMax <= 0) {
                        sender.sendMessage(Component.text("Max homes must be a positive integer.").color(NamedTextColor.RED));
                        return true;
                    }
                    plugin.getConfig().set("max-homes", newMax);
                    plugin.saveConfig();
                    sender.sendMessage(Component.text("Max homes has been updated live to " + newMax + "!").color(NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid number format. Please provide a valid integer.").color(NamedTextColor.RED));
                }
            }
            default -> sender.sendMessage(Component.text("Unknown config key: " + key).color(NamedTextColor.RED));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("tweaks.admin.config")) return Collections.emptyList();

        if (args.length == 1) {
            return List.of("max_homes").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}