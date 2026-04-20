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

// Lists all saved home names for a player. Admins can view other players' homes.
public class HomesCommand implements CommandExecutor, TabCompleter {

    private final StorageManager manager;

    public HomesCommand(StorageManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player) && args.length == 0) {
            sender.sendMessage(Component.text("Console must specify a player: /homes <player>").color(NamedTextColor.RED));
            return true;
        }

        UUID targetUUID;
        String targetName;

        if (args.length == 1 && sender.hasPermission("tweaks.admin.homes")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            targetUUID = target.getUniqueId();
            targetName = target.getName() != null ? target.getName() : args[0];
        } else if (sender instanceof Player player) {
            targetUUID = player.getUniqueId();
            targetName = player.getName();
        } else {
            sender.sendMessage(Component.text("Usage: /homes OR /homes <player>").color(NamedTextColor.YELLOW));
            return true;
        }

        Set<String> homes = manager.getHomes(targetUUID);
        if (homes.isEmpty()) {
            sender.sendMessage(Component.text(targetName + " has no homes set.").color(NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Homes for " + targetName + ": " + String.join(", ", homes)).color(NamedTextColor.AQUA));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("tweaks.admin.homes")) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .toList();
        }
        return Collections.emptyList();
    }
}
