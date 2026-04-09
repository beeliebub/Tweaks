package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.managers.StorageManager;
import me.beeliebub.tweaks.Point;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class SetHomeCommand implements CommandExecutor {

    private final StorageManager manager;
    private final int maxHomes;

    public SetHomeCommand(StorageManager manager, int maxHomes) {
        this.manager = manager;
        this.maxHomes = maxHomes;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can set homes.").color(NamedTextColor.RED));
            return true;
        }

        UUID targetUUID = player.getUniqueId();
        String homeName = "default";

        if (args.length == 1) {
            homeName = args[0];
        } else if (args.length == 2 && player.hasPermission("tweaks.admin.sethome")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            targetUUID = target.getUniqueId();
            homeName = args[1];
        } else if (args.length > 0) {
            player.sendMessage(Component.text("Usage: /sethome <name> OR /sethome <player> <name>").color(NamedTextColor.YELLOW));
            return true;
        }

        if (targetUUID.equals(player.getUniqueId()) && !player.hasPermission("tweaks.bypass.homes")) {
            if (manager.getHomeCount(targetUUID) >= maxHomes && manager.getHome(targetUUID, homeName).isEmpty()) {
                player.sendMessage(Component.text("You have reached the maximum of " + maxHomes + " homes!").color(NamedTextColor.RED));
                return true;
            }
        }

        manager.setHome(targetUUID, homeName, Point.fromLocation(player.getLocation()));
        player.sendMessage(Component.text("Home '" + homeName + "' set successfully!").color(NamedTextColor.GREEN));
        return true;
    }
}