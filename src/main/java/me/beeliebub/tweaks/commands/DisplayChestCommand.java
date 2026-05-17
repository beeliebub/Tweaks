package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.managers.DisplayChestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class DisplayChestCommand implements CommandExecutor {

    private final DisplayChestManager displayChestManager;

    public DisplayChestCommand(DisplayChestManager displayChestManager) {
        this.displayChestManager = displayChestManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("off")) {
            boolean enabled = displayChestManager.toggleRemovalMode(player.getUniqueId());
            if (enabled) {
                player.sendMessage(Component.text("Display Chest removal mode ENABLED. Click a chest to remove its item display.").color(NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Display Chest removal mode DISABLED.").color(NamedTextColor.RED));
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("hand")) {
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand.getType() == Material.AIR) {
                player.sendMessage(Component.text("Hold an item in your main hand to set it as the display source.").color(NamedTextColor.RED));
                return true;
            }
            boolean enabled = displayChestManager.toggleSetupMode(player.getUniqueId());
            if (enabled) {
                displayChestManager.setHandOverride(player.getUniqueId(), inHand.clone());
                player.sendMessage(Component.text("Display Chest setup mode ENABLED (hand override). Click a chest to display the held item.").color(NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Display Chest setup mode DISABLED.").color(NamedTextColor.RED));
            }
            return true;
        }

        boolean enabled = displayChestManager.toggleSetupMode(player.getUniqueId());
        if (enabled) {
            player.sendMessage(Component.text("Display Chest setup mode ENABLED. Click a chest to display its top-left item.").color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Display Chest setup mode DISABLED.").color(NamedTextColor.RED));
        }

        return true;
    }
}
