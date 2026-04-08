package me.beeliebub.tweaks;

import me.beeliebub.tweaks.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class DelWarpCommand implements CommandExecutor {

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

}
