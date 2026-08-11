package me.beeliebub.tweaks.itemadmin;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.utils.ColorUtil;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingPaths;
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
import org.bukkit.plugin.java.JavaPlugin;
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
    private final JavaPlugin plugin;

    public ItemEditCommand() {
        this(null);
    }

    public ItemEditCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

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
            sender.sendMessage(Messages.ITEM_ADMIN.itemEditRequiresPlayer());
            return true;
        }
        if (!player.hasPermission(Permissions.ADMIN_ITEM_EDIT)) {
            player.sendMessage(Messages.noPermission());
            return true;
        }
        if (args.length < 2) {
            sendLoreUsage(player, label);
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.isEmpty()) {
            player.sendMessage(Messages.ITEM_ADMIN.itemEditRequiresHeldItem());
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        int lineNumber;
        try {
            lineNumber = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Messages.ITEM_ADMIN.itemEditLoreLineMustBeInteger());
            return true;
        }
        if (lineNumber < 1) {
            player.sendMessage(Messages.ITEM_ADMIN.itemEditLoreLineMustBePositive());
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        List<Component> existing = meta.hasLore() ? meta.lore() : null;
        List<Component> lore = existing == null ? new ArrayList<>() : new ArrayList<>(existing);

        switch (action) {
            case "add" -> {
                if (args.length < 3) {
                    player.sendMessage(Messages.ITEM_ADMIN.itemEditLoreAddUsage(label));
                    return true;
                }
                String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                Component parsed = ColorUtil.parse(text);

                int index = Math.min(lineNumber - 1, lore.size());
                lore.add(index, parsed);

                meta.lore(lore);
                item.setItemMeta(meta);

                player.sendMessage(Messages.ITEM_ADMIN.itemEditLoreAdded(index + 1, parsed));
                log(player, "edited lore line " + (index + 1));
            }
            case "remove" -> {
                if (lore.isEmpty()) {
                    player.sendMessage(Messages.ITEM_ADMIN.itemEditLoreEmpty());
                    return true;
                }
                if (lineNumber > lore.size()) {
                    player.sendMessage(Messages.ITEM_ADMIN.itemEditLoreLineMissing(lineNumber, lore.size()));
                    return true;
                }
                Component removed = lore.remove(lineNumber - 1);
                meta.lore(lore.isEmpty() ? null : lore);
                item.setItemMeta(meta);

                player.sendMessage(Messages.ITEM_ADMIN.itemEditLoreRemoved(lineNumber, removed));
                log(player, "removed lore line " + lineNumber);
            }
            default -> sendLoreUsage(player, label);
        }
        return true;
    }

    private void sendLoreUsage(CommandSender sender, String label) {
        sender.sendMessage(Messages.ITEM_ADMIN.itemEditUsageHeader());
        sender.sendMessage(Messages.ITEM_ADMIN.itemEditLoreAddUsageLine(label));
        sender.sendMessage(Messages.ITEM_ADMIN.itemEditLoreRemoveUsageLine(label));
    }

    private boolean handleName(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.ITEM_ADMIN.itemEditRequiresPlayer());
            return true;
        }
        if (!player.hasPermission(Permissions.ADMIN_ITEM_EDIT)) {
            player.sendMessage(Messages.noPermission());
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Messages.ITEM_ADMIN.itemEditNameUsage(label));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.isEmpty()) {
            player.sendMessage(Messages.ITEM_ADMIN.itemEditRequiresHeldItem());
            return true;
        }

        ItemMeta meta = item.getItemMeta();

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("off")) {
                meta.displayName(null);
                item.setItemMeta(meta);
                player.sendMessage(Messages.ITEM_ADMIN.itemEditNameRemoved());
                log(player, "removed item name");
                return true;
            }
            if (args[0].equalsIgnoreCase("blank")) {
                meta.displayName(Component.text(" "));
                meta.lore(null);
                meta.addItemFlags(ItemFlag.values());
                item.setItemMeta(meta);
                player.sendMessage(Messages.ITEM_ADMIN.itemEditNameBlanked());
                log(player, "blanked item name");
                return true;
            }
        }

        String raw = String.join(" ", args);
        Component name = ColorUtil.parse(raw);
        meta.displayName(name);
        item.setItemMeta(meta);

        player.sendMessage(Messages.ITEM_ADMIN.itemEditNameSet(name));
        log(player, "set item name");
        return true;
    }

    private boolean handleMore(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.ITEM_ADMIN.itemMoreRequiresPlayer());
            return true;
        }
        if (!player.hasPermission(Permissions.ADMIN_MORE)) {
            player.sendMessage(Messages.noPermission());
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.isEmpty()) {
            player.sendMessage(Messages.ITEM_ADMIN.itemMoreRequiresHeldItem());
            return true;
        }
        item.setAmount(item.getMaxStackSize());
        player.sendMessage(Messages.ITEM_ADMIN.itemMoreSuccess(item.getMaxStackSize()));
        log(player, "set held stack to max size");
        return true;
    }

    private void log(Player player, String detail) {
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) eventLog.log(LoggingPaths.ITEMADMIN_EDIT, () ->
                "[ItemAdmin] " + ConsoleEventLog.actorLabel(player.getName(), player.getUniqueId())
                        + " " + detail);
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
