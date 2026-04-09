package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.Point;
import me.beeliebub.tweaks.managers.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class SpawnCommand implements CommandExecutor {

    private final StorageManager manager;

    public SpawnCommand(StorageManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can teleport to spawn.").color(NamedTextColor.RED));
            return true;
        }

        String warpName = "spawn";
        Optional<Point> pointOpt = manager.getWarp(warpName);
        if (pointOpt.isEmpty()) {
            player.sendMessage(Component.text("Spawn has not been set!").color(NamedTextColor.RED));
            return true;
        }

        pointOpt.get().toLocation().ifPresentOrElse(loc -> {
            player.sendMessage(Component.text("Teleporting to spawn...").color(NamedTextColor.GREEN));
            player.teleportAsync(loc);
        }, () -> player.sendMessage(Component.text("The world for spawn is not loaded.").color(NamedTextColor.RED)));

        return true;
    }
}
