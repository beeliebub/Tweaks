package me.beeliebub.tweaks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SetWarpCommand implements CommandExecutor {

    private final StorageManager manager;

    public SetWarpCommand(StorageManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can set warps.").color(NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("tweaks.admin.setwarp")) {
            sender.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /setwarp <name>").color(NamedTextColor.YELLOW));
            return true;
        }

        String warpName = args[0];
        manager.setWarp(warpName, Point.fromLocation(player.getLocation()));
        player.sendMessage(Component.text("Warp '" + warpName + "' set successfully!").color(NamedTextColor.GREEN));
        return true;
    }

}
