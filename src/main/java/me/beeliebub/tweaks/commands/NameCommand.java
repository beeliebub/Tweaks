package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.ColorUtil;
import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

// /name <name> | /name off | /name blank — admin command to set or clear the held item's display name.
// Supports legacy '&' color codes and '&#rrggbb' hex via ColorUtil. Spaces preserved naturally.
public class NameCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("off", "blank");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission(Permissions.ADMIN_ITEM_EDIT)) {
            player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /" + label + " <name> | /" + label + " off | /" + label + " blank", NamedTextColor.RED));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.isEmpty()) {
            player.sendMessage(Component.text("You must be holding an item.", NamedTextColor.RED));
            return true;
        }

        ItemMeta meta = item.getItemMeta();

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("off")) {
                meta.displayName(null);
                item.setItemMeta(meta);
                player.sendMessage(Component.text("Custom name removed.", NamedTextColor.GREEN));
                return true;
            }
            if (args[0].equalsIgnoreCase("blank")) {
                meta.displayName(Component.text(" "));
                meta.lore(null);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
                item.setItemMeta(meta);
                player.sendMessage(Component.text("Item name set to blank and all hover info removed.", NamedTextColor.GREEN));
                return true;
            }
        }

        String raw = String.join(" ", args);
        Component name = ColorUtil.parse(raw);
        meta.displayName(name);
        item.setItemMeta(meta);

        player.sendMessage(Component.text("Display name set to ", NamedTextColor.GREEN).append(name));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_ITEM_EDIT)) return Collections.emptyList();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        return Collections.emptyList();
    }
}