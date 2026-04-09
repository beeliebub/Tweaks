package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.managers.StorageManager;
import me.beeliebub.tweaks.Point;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class WarpCommand implements CommandExecutor {

    private final StorageManager manager;

    public WarpCommand(StorageManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use warps.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /warp <name>").color(NamedTextColor.YELLOW));
            return true;
        }

        String warpName = args[0];
        Optional<Point> pointOpt = manager.getWarp(warpName);
        if (pointOpt.isEmpty()) {
            player.sendMessage(Component.text("Warp '" + warpName + "' does not exist!").color(NamedTextColor.RED));
            return true;
        }

        pointOpt.get().toLocation().ifPresentOrElse(loc -> {
            player.sendMessage(Component.text("Warping to '" + warpName + "'...").color(NamedTextColor.GREEN));
            player.teleportAsync(loc);
        }, () -> player.sendMessage(Component.text("The world for this warp is not loaded.").color(NamedTextColor.RED)));

        return true;
    }

}
