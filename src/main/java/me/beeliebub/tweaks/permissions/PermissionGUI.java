package me.beeliebub.tweaks.permissions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * Handles GUI creation for the permission system.
 */
public class PermissionGUI {

    public static final String TITLE_GROUPS = "Manage Groups";
    public static final String TITLE_USERS = "Manage Users";
    public static final String TITLE_SELECT_GROUP = "Select Group for ";
    public static final String PREFIX_GROUP_EDITOR = "Group: ";
    public static final String PREFIX_USER_EDITOR = "User: ";

    public static void openGroupsMenu(Player player, PermissionManager manager) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TITLE_GROUPS).color(NamedTextColor.GOLD));
        
        List<String> groups = new ArrayList<>(manager.getGroups().keySet());
        for (int i = 0; i < groups.size() && i < 45; i++) {
            String name = groups.get(i);
            inv.setItem(i, createItem(Material.CHEST, name, NamedTextColor.YELLOW, List.of("Click to edit permissions")));
        }

        inv.setItem(49, createItem(Material.PLAYER_HEAD, "Manage Users", NamedTextColor.AQUA, List.of("Click to view users")));
        inv.setItem(48, createItem(Material.NETHER_STAR, "Create Group", NamedTextColor.GREEN, List.of("Click to create a new group")));
        player.openInventory(inv);
    }

    public static void openGroupEditor(Player player, String groupName, int page, PermissionManager manager) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(PREFIX_GROUP_EDITOR + groupName + " (Page " + (page + 1) + ")").color(NamedTextColor.GOLD));
        
        List<String> allPerms = Permissions.getAllPermissions();
        int start = page * 45;
        for (int i = 0; i < 45 && (start + i) < allPerms.size(); i++) {
            String perm = allPerms.get(start + i);
            boolean has = group.hasDirectPermission(perm);
            Material mat = has ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            inv.setItem(i, createItem(mat, perm, has ? NamedTextColor.GREEN : NamedTextColor.RED, List.of("Click to toggle")));
        }

        if (page > 0) inv.setItem(45, createItem(Material.ARROW, "Previous Page", NamedTextColor.YELLOW, null));
        if ((start + 45) < allPerms.size()) inv.setItem(53, createItem(Material.ARROW, "Next Page", NamedTextColor.YELLOW, null));
        
        inv.setItem(49, createItem(Material.BARRIER, "Back to Groups", NamedTextColor.RED, null));
        player.openInventory(inv);
    }

    public static void openUsersMenu(Player player, int page, PermissionManager manager) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TITLE_USERS + " (Page " + (page + 1) + ")").color(NamedTextColor.GOLD));
        
        Set<UUID> allUuids = new HashSet<>();
        Bukkit.getOnlinePlayers().forEach(p -> allUuids.add(p.getUniqueId()));
        allUuids.addAll(manager.getUsers().keySet());

        List<OfflinePlayer> players = allUuids.stream()
                .map(Bukkit::getOfflinePlayer)
                .sorted((a, b) -> {
                    String nameA = a.getName() == null ? "" : a.getName();
                    String nameB = b.getName() == null ? "" : b.getName();
                    return nameA.compareToIgnoreCase(nameB);
                })
                .toList();

        int start = page * 45;
        for (int i = 0; i < 45 && (start + i) < players.size(); i++) {
            OfflinePlayer target = players.get(start + i);
            String name = target.getName() == null ? target.getUniqueId().toString() : target.getName();
            boolean online = target.isOnline();
            
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.displayName(Component.text(name).color(online ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                meta.lore(List.of(Component.text(online ? "Online" : "Offline").color(NamedTextColor.DARK_GRAY),
                                  Component.text("Click to edit permissions").color(NamedTextColor.GRAY)));
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        if (page > 0) inv.setItem(45, createItem(Material.ARROW, "Previous Page", NamedTextColor.YELLOW, null));
        if ((start + 45) < players.size()) inv.setItem(53, createItem(Material.ARROW, "Next Page", NamedTextColor.YELLOW, null));

        inv.setItem(49, createItem(Material.BARRIER, "Back to Groups", NamedTextColor.RED, null));
        inv.setItem(48, createItem(Material.COMPASS, "Search Player", NamedTextColor.AQUA, List.of("Click to search for an offline player")));
        player.openInventory(inv);
    }

    public static void openUserEditor(Player player, UUID targetUuid, int page, PermissionManager manager) {
        UserPermissions user = manager.getUserPermissions(targetUuid);
        String name = Bukkit.getOfflinePlayer(targetUuid).getName();
        if (name == null) name = targetUuid.toString();

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(PREFIX_USER_EDITOR + name + " (Page " + (page + 1) + ")").color(NamedTextColor.GOLD));
        
        List<String> allPerms = Permissions.getAllPermissions();
        int start = page * 45;
        for (int i = 0; i < 45 && (start + i) < allPerms.size(); i++) {
            String perm = allPerms.get(start + i);
            boolean has = user.hasDirectPermission(perm);
            Material mat = has ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            inv.setItem(i, createItem(mat, perm, has ? NamedTextColor.GREEN : NamedTextColor.RED, List.of("Click to toggle")));
        }

        if (page > 0) inv.setItem(45, createItem(Material.ARROW, "Previous Page", NamedTextColor.YELLOW, null));
        if ((start + 45) < allPerms.size()) inv.setItem(53, createItem(Material.ARROW, "Next Page", NamedTextColor.YELLOW, null));

        inv.setItem(49, createItem(Material.BARRIER, "Back to Users", NamedTextColor.RED, null));
        inv.setItem(48, createItem(Material.BOOK, "Change Group", NamedTextColor.AQUA, List.of("Current: " + (user.getGroupName() == null ? "default" : user.getGroupName()))));
        inv.setItem(47, createItem(Material.TNT, "Reset User", NamedTextColor.RED, List.of("Clear all overrides and set to default group")));
        
        player.openInventory(inv);
    }

    public static void openGroupSelectionMenu(Player player, UUID targetUuid, PermissionManager manager) {
        String name = Bukkit.getOfflinePlayer(targetUuid).getName();
        if (name == null) name = targetUuid.toString();

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TITLE_SELECT_GROUP + name).color(NamedTextColor.GOLD));
        
        List<String> groups = new ArrayList<>(manager.getGroups().keySet());
        for (int i = 0; i < groups.size() && i < 45; i++) {
            String gName = groups.get(i);
            inv.setItem(i, createItem(Material.CHEST, gName, NamedTextColor.YELLOW, List.of("Click to assign to this group")));
        }

        inv.setItem(49, createItem(Material.BARRIER, "Cancel", NamedTextColor.RED, null));
        player.openInventory(inv);
    }

    private static ItemStack createItem(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).color(color));
            if (lore != null) {
                List<Component> compLore = new ArrayList<>();
                for (String l : lore) compLore.add(Component.text(l).color(NamedTextColor.GRAY));
                meta.lore(compLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
