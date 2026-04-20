package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.managers.StorageManager;
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

// Admin command to delete a server warp by name
public class DelWarpCommand implements CommandExecutor, TabCompleter {

    private final StorageManager manager;

    public DelWarpCommand(StorageManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("tweaks.admin.delwarp")) {
            sender.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /delwarp <name>").color(NamedTextColor.YELLOW));
            return true;
        }

        String warpName = args[0];
        if (manager.getWarp(warpName).isEmpty()) {
            sender.sendMessage(Component.text("Warp '" + warpName + "' does not exist!").color(NamedTextColor.RED));
            return true;
        }

        manager.delWarp(warpName);
        sender.sendMessage(Component.text("Warp '" + warpName + "' deleted successfully!").color(NamedTextColor.GREEN));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return manager.getWarps().stream()
                    .filter(n -> n.startsWith(partial))
                    .toList();
        }
        return Collections.emptyList();
    }
}
