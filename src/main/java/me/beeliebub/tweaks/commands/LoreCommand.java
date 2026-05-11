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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// /lore <add|remove> <line#> [text] — admin command to edit the held item's lore.
// add:    inserts <text> at the 1-indexed line position (clamped to end+1).
// remove: deletes the line at the 1-indexed position.
// Supports legacy '&' color codes and '&#rrggbb' hex via ColorUtil.
public class LoreCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ACTIONS = List.of("add", "remove");

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
        if (args.length < 2) {
            sendUsage(player, label);
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.isEmpty()) {
            player.sendMessage(Component.text("You must be holding an item.", NamedTextColor.RED));
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        int lineNumber;
        try {
            lineNumber = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Line number must be an integer.", NamedTextColor.RED));
            return true;
        }
        if (lineNumber < 1) {
            player.sendMessage(Component.text("Line number must be 1 or greater.", NamedTextColor.RED));
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        List<Component> existing = meta.hasLore() ? meta.lore() : null;
        List<Component> lore = existing == null ? new ArrayList<>() : new ArrayList<>(existing);

        switch (action) {
            case "add" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /" + label + " add <line#> <text>", NamedTextColor.RED));
                    return true;
                }
                String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                Component parsed = ColorUtil.parse(text);

                int index = Math.min(lineNumber - 1, lore.size());
                lore.add(index, parsed);

                meta.lore(lore);
                item.setItemMeta(meta);

                player.sendMessage(Component.text("Added lore line " + (index + 1) + ":", NamedTextColor.GREEN)
                        .append(Component.text(" ", NamedTextColor.GRAY))
                        .append(parsed));
            }
            case "remove" -> {
                if (lore.isEmpty()) {
                    player.sendMessage(Component.text("This item has no lore to remove.", NamedTextColor.RED));
                    return true;
                }
                if (lineNumber > lore.size()) {
                    player.sendMessage(Component.text("Line " + lineNumber + " does not exist (item has "
                            + lore.size() + " lore line" + (lore.size() == 1 ? "" : "s") + ").", NamedTextColor.RED));
                    return true;
                }
                Component removed = lore.remove(lineNumber - 1);
                meta.lore(lore.isEmpty() ? null : lore);
                item.setItemMeta(meta);

                player.sendMessage(Component.text("Removed lore line " + lineNumber + ":", NamedTextColor.GREEN)
                        .append(Component.text(" ", NamedTextColor.GRAY))
                        .append(removed));
            }
            default -> sendUsage(player, label);
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Usage:", NamedTextColor.RED));
        sender.sendMessage(Component.text("  /" + label + " add <line#> <text>", NamedTextColor.RED));
        sender.sendMessage(Component.text("  /" + label + " remove <line#>", NamedTextColor.RED));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_ITEM_EDIT)) return Collections.emptyList();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return ACTIONS.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        if (args.length == 2 && sender instanceof Player p) {
            ItemStack item = p.getInventory().getItemInMainHand();
            int loreSize = 0;
            if (!item.isEmpty() && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                List<Component> existing = item.getItemMeta().lore();
                loreSize = existing == null ? 0 : existing.size();
            }
            String action = args[0].toLowerCase(Locale.ROOT);
            int max = action.equals("add") ? Math.max(loreSize + 1, 1) : loreSize;
            List<String> out = new ArrayList<>(max);
            for (int i = 1; i <= max; i++) out.add(String.valueOf(i));
            return out;
        }

        return Collections.emptyList();
    }
}