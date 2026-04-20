package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.managers.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DelHomeCommand implements CommandExecutor, TabCompleter {

    private final StorageManager manager;

    public DelHomeCommand(StorageManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can delete homes.").color(NamedTextColor.RED));
            return true;
        }

        UUID targetUUID = player.getUniqueId();
        String homeName = "default";

        if (args.length == 1) {
            homeName = args[0];
        } else if (args.length == 2 && player.hasPermission("tweaks.admin.delhome")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            targetUUID = target.getUniqueId();
            homeName = args[1];
        } else if (args.length > 0) {
            player.sendMessage(Component.text("Usage: /delhome <name> OR /delhome <player> <name>").color(NamedTextColor.YELLOW));
            return true;
        }

        if (manager.getHome(targetUUID, homeName).isEmpty()) {
            player.sendMessage(Component.text("Home '" + homeName + "' does not exist!").color(NamedTextColor.RED));
            return true;
        }

        manager.delHome(targetUUID, homeName);
        player.sendMessage(Component.text("Home '" + homeName + "' deleted successfully!").color(NamedTextColor.GREEN));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(manager.getHomes(player.getUniqueId()));
            if (player.hasPermission("tweaks.admin.delhome")) {
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && player.hasPermission("tweaks.admin.delhome")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            return manager.getHomes(target.getUniqueId()).stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}
