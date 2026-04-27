package me.beeliebub.tweaks.combos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Per-player item pickup filter. Whitelist mode picks up only listed items;
// blacklist mode picks up everything except listed items. State lives on the
// player's PDC, which is fine because pickups only happen while online.
public class ItemFilterCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String MODE_WHITELIST = "whitelist";
    private static final String MODE_BLACKLIST = "blacklist";

    private final NamespacedKey enabledKey;
    private final NamespacedKey modeKey;
    private final NamespacedKey whitelistKey;
    private final NamespacedKey blacklistKey;

    public ItemFilterCommand(JavaPlugin plugin) {
        this.enabledKey   = new NamespacedKey(plugin, "itemfilter_enabled");
        this.modeKey      = new NamespacedKey(plugin, "itemfilter_mode");
        this.whitelistKey = new NamespacedKey(plugin, "itemfilter_whitelist");
        this.blacklistKey = new NamespacedKey(plugin, "itemfilter_blacklist");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /itemfilter.")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showStatus(player);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "toggle" -> handleToggle(player);
            case "mode"   -> handleMode(player);
            case "add"    -> handleAdd(player, args);
            case "remove" -> handleRemove(player, args);
            case "list"   -> { showList(player); yield true; }
            default -> {
                player.sendMessage(Component.text(
                        "Usage: /if [toggle | mode | add <item> | remove <item> | list]",
                        NamedTextColor.YELLOW));
                yield true;
            }
        };
    }

    private void showStatus(Player player) {
        boolean enabled = isEnabled(player);
        String mode = getMode(player);
        List<String> list = getList(player, mode);
        player.sendMessage(Component.text("ItemFilter: ", NamedTextColor.GRAY)
                .append(Component.text(enabled ? "ENABLED" : "DISABLED",
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text(" • Mode: ", NamedTextColor.GRAY))
                .append(Component.text(mode, NamedTextColor.AQUA))
                .append(Component.text(" • Items: ", NamedTextColor.GRAY))
                .append(Component.text(Integer.toString(list.size()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Use /if list to see your " + mode + ".",
                NamedTextColor.DARK_GRAY));
    }

    private boolean handleToggle(Player player) {
        boolean newState = !isEnabled(player);
        setEnabled(player, newState);
        player.sendMessage(Component.text("ItemFilter " + (newState ? "enabled." : "disabled."),
                newState ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        return true;
    }

    private boolean handleMode(Player player) {
        String next = getMode(player).equals(MODE_WHITELIST) ? MODE_BLACKLIST : MODE_WHITELIST;
        setMode(player, next);
        player.sendMessage(Component.text("ItemFilter mode set to ", NamedTextColor.GREEN)
                .append(Component.text(next, NamedTextColor.AQUA))
                .append(Component.text(".", NamedTextColor.GREEN)));
        return true;
    }

    private boolean handleAdd(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /if add <item>", NamedTextColor.YELLOW));
            return true;
        }
        Material material = Material.matchMaterial(args[1]);
        if (material == null || !material.isItem()) {
            player.sendMessage(Component.text("Unknown item: " + args[1], NamedTextColor.RED));
            return true;
        }
        String mode = getMode(player);
        List<String> list = new ArrayList<>(getList(player, mode));
        String key = material.getKey().toString();
        if (list.contains(key)) {
            player.sendMessage(Component.text(prettyMaterial(material) + " is already in your " + mode + ".",
                    NamedTextColor.YELLOW));
            return true;
        }
        list.add(key);
        setList(player, mode, list);
        player.sendMessage(Component.text("Added ", NamedTextColor.GREEN)
                .append(Component.text(prettyMaterial(material), NamedTextColor.AQUA))
                .append(Component.text(" to your " + mode + ".", NamedTextColor.GREEN)));
        return true;
    }

    private boolean handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /if remove <item>", NamedTextColor.YELLOW));
            return true;
        }
        Material material = Material.matchMaterial(args[1]);
        if (material == null) {
            player.sendMessage(Component.text("Unknown item: " + args[1], NamedTextColor.RED));
            return true;
        }
        String mode = getMode(player);
        List<String> list = new ArrayList<>(getList(player, mode));
        if (!list.remove(material.getKey().toString())) {
            player.sendMessage(Component.text(prettyMaterial(material) + " isn't in your " + mode + ".",
                    NamedTextColor.YELLOW));
            return true;
        }
        setList(player, mode, list);
        player.sendMessage(Component.text("Removed ", NamedTextColor.GREEN)
                .append(Component.text(prettyMaterial(material), NamedTextColor.AQUA))
                .append(Component.text(" from your " + mode + ".", NamedTextColor.GREEN)));
        return true;
    }

    private void showList(Player player) {
        String mode = getMode(player);
        List<String> list = getList(player, mode);
        if (list.isEmpty()) {
            player.sendMessage(Component.text("Your " + mode + " is empty.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("Your " + mode + " (" + list.size() + "):", NamedTextColor.GRAY));
        for (String entry : list) {
            String shortName = entry.startsWith("minecraft:") ? entry.substring(10) : entry;
            player.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(shortName, NamedTextColor.AQUA)));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!allowsPickup(player, event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    // Public so Telekinesis (and any other inventory-routing enchant path) can consult the
    // filter before adding items directly to a player's inventory. Returns true if the item
    // should be allowed through (filter disabled, item in whitelist, or item not in blacklist).
    public boolean allowsPickup(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return true;
        if (!isEnabled(player)) return true;
        String mode = getMode(player);
        boolean inList = getList(player, mode).contains(item.getType().getKey().toString());
        return mode.equals(MODE_WHITELIST) ? inList : !inList;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            return prefixFilter(List.of("toggle", "mode", "add", "remove", "list"), args[0]);
        }
        if (args.length != 2) return List.of();

        String partial = args[1].toLowerCase(Locale.ROOT);

        if (args[0].equalsIgnoreCase("remove") && sender instanceof Player p) {
            // Suggest only what's actually in the player's current list
            List<String> entries = getList(p, getMode(p)).stream()
                    .map(s -> s.startsWith("minecraft:") ? s.substring(10) : s)
                    .toList();
            return prefixFilter(entries, args[1]);
        }
        if (args[0].equalsIgnoreCase("add")) {
            List<String> matches = new ArrayList<>(50);
            for (Material m : Material.values()) {
                if (!m.isItem()) continue;
                String name = m.getKey().getKey();
                if (name.startsWith(partial)) {
                    matches.add(name);
                    if (matches.size() >= 50) break;
                }
            }
            return matches;
        }
        return List.of();
    }

    private static List<String> prefixFilter(List<String> options, String partial) {
        String p = partial.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }

    // PDC accessors

    private boolean isEnabled(Player player) {
        Byte b = player.getPersistentDataContainer().get(enabledKey, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    private void setEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(enabledKey, PersistentDataType.BYTE,
                (byte) (enabled ? 1 : 0));
    }

    private String getMode(Player player) {
        String s = player.getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        return MODE_BLACKLIST.equals(s) ? MODE_BLACKLIST : MODE_WHITELIST;
    }

    private void setMode(Player player, String mode) {
        player.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode);
    }

    private List<String> getList(Player player, String mode) {
        NamespacedKey key = mode.equals(MODE_BLACKLIST) ? blacklistKey : whitelistKey;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        List<String> list = pdc.get(key, PersistentDataType.LIST.strings());
        return list != null ? list : List.of();
    }

    private void setList(Player player, String mode, List<String> list) {
        NamespacedKey key = mode.equals(MODE_BLACKLIST) ? blacklistKey : whitelistKey;
        player.getPersistentDataContainer().set(key, PersistentDataType.LIST.strings(), list);
    }

    private static String prettyMaterial(Material material) {
        String[] parts = material.getKey().getKey().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }
        return sb.toString();
    }
}