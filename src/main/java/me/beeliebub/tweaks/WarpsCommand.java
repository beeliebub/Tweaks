package me.beeliebub.tweaks;

import me.beeliebub.tweaks.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class WarpsCommand implements CommandExecutor {

    private final StorageManager manager;

    public WarpsCommand(StorageManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        Set<String> warps = manager.getWarps();
        if (warps.isEmpty()) {
            sender.sendMessage(Component.text("There are no warps available.").color(NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Available Warps: " + String.join(", ", warps)).color(NamedTextColor.AQUA));
        }
        return true;
    }

}
