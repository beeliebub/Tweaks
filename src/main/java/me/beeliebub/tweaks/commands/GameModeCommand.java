package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

// Sets the executing player's gamemode. A single executor backs both /survival
// and /creative; the target GameMode is resolved from command.getName() so
// future aliases on the same command resolve to the right mode.
public class GameModeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission(Permissions.ADMIN_GAMEMODE)) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can change their gamemode.", NamedTextColor.RED));
            return true;
        }

        GameMode target = switch (command.getName().toLowerCase()) {
            case "survival" -> GameMode.SURVIVAL;
            case "creative" -> GameMode.CREATIVE;
            default -> null;
        };
        if (target == null) {
            return false;
        }

        if (player.getGameMode() == target) {
            player.sendMessage(Component.text("Already in " + target.name().toLowerCase() + " mode.", NamedTextColor.YELLOW));
            return true;
        }

        player.setGameMode(target);
        player.sendMessage(Component.text("Gamemode set to " + target.name().toLowerCase() + ".", NamedTextColor.GREEN));
        return true;
    }
}