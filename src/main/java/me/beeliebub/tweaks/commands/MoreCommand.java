package me.beeliebub.tweaks.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class MoreCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("tweaks.admin.more")) {
            player.sendMessage(Component.text("You don't have permission to use this command!", NamedTextColor.RED));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.isEmpty()) {
            player.sendMessage(Component.text("You must be holding an item!", NamedTextColor.RED));
            return true;
        }

        item.setAmount(item.getMaxStackSize());
        player.sendMessage(Component.text("Stack maximized to " + item.getMaxStackSize() + "!", NamedTextColor.GREEN));
        return true;
    }
}