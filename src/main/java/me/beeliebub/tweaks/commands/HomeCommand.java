package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.Point;
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

// Teleports a player to one of their saved homes. Admins can teleport to other players' homes.
public class HomeCommand implements CommandExecutor, TabCompleter {

    private final StorageManager storage;

    public HomeCommand(StorageManager storage) {
        this.storage = storage;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can teleport.").color(NamedTextColor.RED));
            return true;
        }

        UUID targetUUID;
        String homeName;

        if (args.length == 0) {
            targetUUID = player.getUniqueId();
            homeName = "default";
        } else if (args.length == 1) {
            targetUUID = player.getUniqueId();
            homeName = args[0];
        } else if (args.length == 2 && player.hasPermission("tweaks.admin.home")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            targetUUID = target.getUniqueId();
            homeName = args[1];
        } else {
            player.sendMessage(Component.text("Usage: /home [name] | /home <player> <name>").color(NamedTextColor.YELLOW));
            return true;
        }

        Optional<Point> pointOpt = storage.getHome(targetUUID, homeName);
        if (pointOpt.isEmpty()) {
            player.sendMessage(Component.text("Home not found!").color(NamedTextColor.RED));
            return true;
        }

        pointOpt.get().toLocation().ifPresentOrElse(loc -> {
            player.sendMessage(Component.text("Teleporting to " + homeName + "...").color(NamedTextColor.GREEN));

            player.teleportAsync(loc).thenAccept(success -> {
                if (!success) {
                    player.sendMessage(Component.text("Teleportation failed. Is the destination safe?").color(NamedTextColor.RED));
                }
            });
        }, () -> {
            player.sendMessage(Component.text("The world this home is in is not loaded!").color(NamedTextColor.DARK_RED));
        });

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(storage.getHomes(player.getUniqueId()));
            if (player.hasPermission("tweaks.admin.home")) {
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && player.hasPermission("tweaks.admin.home")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            return storage.getHomes(target.getUniqueId()).stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}