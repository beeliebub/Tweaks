package me.beeliebub.tweaks.itemadmin;

import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// Dispatcher for the three simple admin item-edit commands. All operate on the
// held item and were previously LoreCommand, NameCommand, and MoreCommand. The
// shared shape — held-item guard, permission gate, ColorUtil-based formatting —
// is the reason to merge.
public class ItemEditCommand implements CommandExecutor, TabCompleter {

    private static final List<String> LORE_ACTIONS = List.of("add", "remove");
    private static final List<String> NAME_SUBCOMMANDS = List.of("off", "blank");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "lore" -> handleLore(sender, label, args);
            case "name" -> handleName(sender, label, args);
            case "more" -> handleMore(sender);
            default -> false;
        };
    }

    private boolean handleLore(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission(Permissions.ADMIN_ITEM_EDIT)) {
            player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sendLoreUsage(player, label);
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
            default -> sendLoreUsage(player, label);
        }
        return true;
    }

    private void sendLoreUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Usage:", NamedTextColor.RED));
        sender.sendMessage(Component.text("  /" + label + " add <line#> <text>", NamedTextColor.RED));
        sender.sendMessage(Component.text("  /" + label + " remove <line#>", NamedTextColor.RED));
    }

    private boolean handleName(CommandSender sender, String label, String[] args) {
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
                meta.addItemFlags(ItemFlag.values());
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

    private boolean handleMore(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission(Permissions.ADMIN_MORE)) {
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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String @NotNull [] args) {
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "lore" -> loreTab(sender, args);
            case "name" -> nameTab(sender, args);
            default -> Collections.emptyList();
        };
    }

    private List<String> loreTab(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_ITEM_EDIT)) return Collections.emptyList();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return LORE_ACTIONS.stream().filter(s -> s.startsWith(prefix)).toList();
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

    private List<String> nameTab(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_ITEM_EDIT)) return Collections.emptyList();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return NAME_SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return Collections.emptyList();
    }
}
