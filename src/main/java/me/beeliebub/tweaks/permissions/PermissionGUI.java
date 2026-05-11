package me.beeliebub.tweaks.permissions;

import me.beeliebub.tweaks.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// /perms GUI hierarchy. All menus are 54-slot chests with a gray-glass perimeter
// border, MiniMessage gradient titles (gold/amber theme), and a red-glass "Back"
// tile at slot 45 wherever applicable. Lists (groups, users, permissions, group
// members) populate a 4×7 inner grid (28 cells) and paginate via lime-glass
// "Next"/"Prev" tiles at slots 53 and 46.
//
// Hierarchy:
//   MAIN ──┬─ GROUPS_LIST ──── GROUP_HUB ──┬─ GROUP_PERMS
//          │                              ├─ GROUP_ADD_PLAYER
//          │                              └─ GROUP_REMOVE_PLAYER
//          └─ USERS_LIST ───── USER_HUB ──┬─ USER_PERMS
//                                          └─ USER_GROUP_PICKER
public final class PermissionGUI {

    private PermissionGUI() {}

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final int MENU_SIZE = 54;

    // 4×7 inner content grid (rows 1-4, cols 1-7) for paginated list entries.
    private static final int[] INNER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    static final int PAGE_CAPACITY = INNER_SLOTS.length; // 28

    // Bottom-row action slots.
    static final int BACK_SLOT = 45;
    static final int PREV_PAGE_SLOT = 46;
    static final int NEXT_PAGE_SLOT = 53;
    static final int CREATE_GROUP_SLOT = 49;
    static final int SEARCH_USER_SLOT = 49;

    // Hub action slots (group/user editor hubs).
    static final int HUB_EDIT_PERMS_SLOT = 20;
    static final int HUB_SECONDARY_SLOT = 22; // Members (Group) / Change Group (User)
    static final int HUB_TERTIARY_SLOT = 24;  // Inheritance (Group) / Reset User (User)
    static final int HUB_DELETE_GROUP_SLOT = 31;

    // Main-menu category slots.
    static final int MAIN_GROUPS_SLOT = 20;
    static final int MAIN_PLAYERS_SLOT = 24;

    // Action keys (used in PermissionHolder.actionAt → PermissionListener routing).
    static final String ACTION_BACK = "back";
    static final String ACTION_MAIN = "main";
    static final String ACTION_NEXT = "next";
    static final String ACTION_PREV = "prev";
    static final String ACTION_CREATE_GROUP = "create_group";
    static final String ACTION_SEARCH_PLAYER = "search_player";
    static final String ACTION_OPEN_GROUPS = "open_groups";
    static final String ACTION_OPEN_USERS = "open_users";
    static final String ACTION_EDIT_PERMS = "edit_perms";
    static final String ACTION_MANAGE_MEMBERS = "manage_members";
    static final String ACTION_MANAGE_INHERITANCE = "manage_inheritance";
    static final String ACTION_DELETE_GROUP = "delete_group";
    static final String ACTION_EDIT_GROUPS = "edit_groups";
    static final String ACTION_RESET_USER = "reset_user";

    // ------------------------------------------------------------------ Main

    public static void openMainMenu(Player player, PermissionManager manager) {
        PermissionHolder holder = new PermissionHolder(PermissionHolder.MenuKind.MAIN, null, null, 0);
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE,
                MM.deserialize("<!italic><green><bold>Permissions"));
        holder.attach(inv);

        inv.setItem(MAIN_GROUPS_SLOT, makeIcon(Material.BOOKSHELF,
                MM.deserialize("<!italic><green>Groups"),
                List.of(gray("Click to manage groups."))));
        holder.mapAction(MAIN_GROUPS_SLOT, ACTION_OPEN_GROUPS);

        inv.setItem(MAIN_PLAYERS_SLOT, playerHead(Bukkit.getOfflinePlayer("Videowiz92"),
                gray("Click to manage players.")));
        // Overwrite the name set by playerHead to match the theme
        ItemStack head = inv.getItem(MAIN_PLAYERS_SLOT);
        if (head != null) {
            ItemMeta meta = head.getItemMeta();
            meta.displayName(MM.deserialize("<!italic><green>Players"));
            head.setItemMeta(meta);
        }
        holder.mapAction(MAIN_PLAYERS_SLOT, ACTION_OPEN_USERS);

        fillBorder(inv);
        player.openInventory(inv);
    }

    // ----------------------------------------------------------- Groups list

    public static void openGroupsMenu(Player player, PermissionManager manager, int page) {
        PermissionHolder holder = new PermissionHolder(PermissionHolder.MenuKind.GROUPS_LIST, null, null, page);
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE,
                MM.deserialize("<!italic><green>Groups"));
        holder.attach(inv);

        List<String> groupNames = new ArrayList<>(manager.getGroups().keySet());
        groupNames.sort(String.CASE_INSENSITIVE_ORDER);
        placePage(inv, holder, groupNames, page, (slot, name) -> {
            PermissionGroup group = manager.getGroups().get(name);
            inv.setItem(slot, groupIcon(group));
            holder.mapString(slot, name);
        });

        inv.setItem(BACK_SLOT, backIcon("Back to Permissions"));
        holder.mapAction(BACK_SLOT, ACTION_MAIN);

        inv.setItem(CREATE_GROUP_SLOT, makeIcon(Material.WRITABLE_BOOK,
                Component.text("Create Group", NamedTextColor.YELLOW, TextDecoration.BOLD),
                List.of(gray("You will be prompted for the name in chat."))));
        holder.mapAction(CREATE_GROUP_SLOT, ACTION_CREATE_GROUP);

        addPagination(inv, holder, page, groupNames.size());
        fillBorder(inv);
        player.openInventory(inv);
    }

    // ---------------------------------------------------------- Group editor

    public static void openGroupHub(Player player, PermissionManager manager, String groupName) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        PermissionHolder holder = new PermissionHolder(PermissionHolder.MenuKind.GROUP_HUB, group.getName(), null, 0);
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE,
                MM.deserialize("<!italic><green>Group: " + group.getName()));
        holder.attach(inv);

        inv.setItem(HUB_EDIT_PERMS_SLOT, makeIcon(Material.WRITABLE_BOOK,
                Component.text("Edit Permissions", NamedTextColor.GREEN, TextDecoration.BOLD),
                List.of(
                        gray(group.getPermissions().size() + " direct permission(s).")
                )));
        holder.mapAction(HUB_EDIT_PERMS_SLOT, ACTION_EDIT_PERMS);

        inv.setItem(HUB_SECONDARY_SLOT, makeIcon(Material.PLAYER_HEAD,
                Component.text("Manage Members", NamedTextColor.AQUA, TextDecoration.BOLD),
                List.of(gray("Add or remove players via toggle."))));
        holder.mapAction(HUB_SECONDARY_SLOT, ACTION_MANAGE_MEMBERS);

        inv.setItem(HUB_TERTIARY_SLOT, makeIcon(Material.BOOK,
                Component.text("Inheritance", NamedTextColor.YELLOW, TextDecoration.BOLD),
                List.of(
                        Component.text("Inherits from: ", NamedTextColor.GRAY)
                                .append(Component.text(group.getParentName() == null ? "none" : group.getParentName(), NamedTextColor.YELLOW)),
                        gray("Click to choose parent group.")
                )));
        holder.mapAction(HUB_TERTIARY_SLOT, ACTION_MANAGE_INHERITANCE);

        if (!group.getName().equalsIgnoreCase("default")) {
            inv.setItem(HUB_DELETE_GROUP_SLOT, makeIcon(Material.TNT,
                    Component.text("Delete Group", NamedTextColor.RED, TextDecoration.BOLD),
                    List.of(red("CAUTION: This cannot be undone!"))));
            holder.mapAction(HUB_DELETE_GROUP_SLOT, ACTION_DELETE_GROUP);
        }

        inv.setItem(BACK_SLOT, backIcon("Back to Groups"));
        holder.mapAction(BACK_SLOT, ACTION_BACK);

        fillBorder(inv);
        player.openInventory(inv);
    }

    public static void openGroupPerms(Player player, PermissionManager manager, String groupName, int page) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        PermissionHolder holder = new PermissionHolder(PermissionHolder.MenuKind.GROUP_PERMS, group.getName(), null, page);
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE,
                MM.deserialize("<!italic><green>Group Perms: " + group.getName()));
        holder.attach(inv);

        List<String> allPerms = Permissions.getAllPermissions();
        placePage(inv, holder, allPerms, page, (slot, perm) -> {
            inv.setItem(slot, permIcon(perm, group.hasDirectPermission(perm)));
            holder.mapString(slot, perm);
        });

        inv.setItem(BACK_SLOT, backIcon("Back to Group"));
        holder.mapAction(BACK_SLOT, ACTION_BACK);

        addPagination(inv, holder, page, allPerms.size());
        fillBorder(inv);
        player.openInventory(inv);
    }

    public static void openGroupMembersToggle(Player player, PermissionManager manager, String groupName, int page) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        PermissionHolder holder = new PermissionHolder(PermissionHolder.MenuKind.GROUP_MEMBERS_TOGGLE, group.getName(), null, page);
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE,
                MM.deserialize("<!italic><green>Members: " + group.getName()));
        holder.attach(inv);

        List<UUID> allKnown = listPlayers(manager, uuid -> true);
        placePage(inv, holder, allKnown, page, (slot, uuid) -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
            UserPermissions u = manager.getUsers().get(uuid);
            boolean isMember = u != null && u.hasGroup(group.getName());
            String groupsLabel = (u == null || u.getGroups().isEmpty())
                    ? "default"
                    : String.join(", ", u.getGroups());

            Component lore1 = Component.text(target.isOnline() ? "Online" : "Offline",
                    target.isOnline() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY);
            Component lore2 = Component.text("Groups: ", NamedTextColor.GRAY)
                    .append(Component.text(groupsLabel, NamedTextColor.YELLOW));
            Component lore3 = gray(isMember ? "Click to remove from this group." : "Click to add to this group.");

            ItemStack head = playerHead(target, lore1, lore2, lore3);
            if (isMember) {
                ItemMeta meta = head.getItemMeta();
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                head.setItemMeta(meta);
            }
            inv.setItem(slot, head);
            holder.mapUuid(slot, uuid);
        });

        inv.setItem(BACK_SLOT, backIcon("Back to Group"));
        holder.mapAction(BACK_SLOT, ACTION_BACK);

        addPagination(inv, holder, page, allKnown.size());
        fillBorder(inv);
        player.openInventory(inv);
    }

    public static void openGroupInheritancePicker(Player player, PermissionManager manager, String groupName, int page) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        PermissionHolder holder = new PermissionHolder(PermissionHolder.MenuKind.GROUP_INHERITANCE_PICKER, group.getName(), null, page);
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE,
                MM.deserialize("<!italic><green>Inheritance: " + group.getName()));
        holder.attach(inv);

        List<String> candidates = new ArrayList<>(manager.getGroups().keySet());
        candidates.remove(group.getName().toLowerCase()); // Cannot inherit from self
        // Also remove groups that already inherit from this group to prevent cycles (simplified)
        candidates.sort(String.CASE_INSENSITIVE_ORDER);

        placePage(inv, holder, candidates, page, (slot, gName) -> {
            PermissionGroup candidate = manager.getGroups().get(gName);
            boolean isCurrentParent = gName.equalsIgnoreCase(group.getParentName());
            ItemStack icon = groupIcon(candidate);
            if (isCurrentParent) {
                ItemMeta meta = icon.getItemMeta();
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                icon.setItemMeta(meta);
            }
            inv.setItem(slot, icon);
            holder.mapString(slot, gName);
        });

        // "None" option to clear inheritance
        int noneSlot = 49;
        inv.setItem(noneSlot, makeIcon(Material.BARRIER,
                Component.text("None", NamedTextColor.RED, TextDecoration.BOLD),
                List.of(gray("Clear inheritance."))));
        holder.mapString(noneSlot, "none");

        inv.setItem(BACK_SLOT, backIcon("Back to Group"));
        holder.mapAction(BACK_SLOT, ACTION_BACK);

        addPagination(inv, holder, page, candidates.size());
        fillBorder(inv);
        player.openInventory(inv);
    }

    // ------------------------------------------------------------ Users list

    public static void openUsersMenu(Player player, PermissionManager manager, int page) {
        PermissionHolder holder = new PermissionHolder(PermissionHolder.MenuKind.USERS_LIST, null, null, page);
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE,
                MM.deserialize("<!italic><green>Players"));
        holder.attach(inv);

        List<UUID> uuids = listPlayers(manager, uuid -> true);
        placePage(inv, holder, uuids, page, (slot, uuid) -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
            UserPermissions u = manager.getUsers().get(uuid);
            String groupsLabel = (u == null || u.getGroups().isEmpty())
                    ? "default"
                    : String.join(", ", u.getGroups());
            Component lore1 = Component.text(target.isOnline() ? "Online" : "Offline",
                    target.isOnline() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY);
            Component lore2 = Component.text("Groups: ", NamedTextColor.GRAY)
                    .append(Component.text(groupsLabel, NamedTextColor.YELLOW));
            Component lore3 = gray("Click to edit permissions.");
            inv.setItem(slot, playerHead(target, lore1, lore2, lore3));
            holder.mapUuid(slot, uuid);
        });

        inv.setItem(BACK_SLOT, backIcon("Back to Permissions"));
        holder.mapAction(BACK_SLOT, ACTION_MAIN);

        inv.setItem(SEARCH_USER_SLOT, makeIcon(Material.COMPASS,
                Component.text("Search Player", NamedTextColor.AQUA, TextDecoration.BOLD),
                List.of(gray("Type a player name in chat."))));
        holder.mapAction(SEARCH_USER_SLOT, ACTION_SEARCH_PLAYER);

        addPagination(inv, holder, page, uuids.size());
        fillBorder(inv);
        player.openInventory(inv);
    }

    // ----------------------------------------------------------- User editor

    public static void openUserHub(Player player, PermissionManager manager, UUID targetUuid) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        UserPermissions user = manager.getUserPermissions(targetUuid);
        String name = target.getName() == null ? targetUuid.toString() : target.getName();

        PermissionHolder holder = new PermissionHolder(PermissionHolder.MenuKind.USER_HUB, null, targetUuid, 0);
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE,
                MM.deserialize("<!italic><green>User: " + name));
        holder.attach(inv);

        inv.setItem(HUB_EDIT_PERMS_SLOT, makeIcon(Material.WRITABLE_BOOK,
                Component.text("Edit Permissions", NamedTextColor.GREEN, TextDecoration.BOLD),
                List.of(gray(user.getPermissions().size() + " direct permission(s)."))));
        holder.mapAction(HUB_EDIT_PERMS_SLOT, ACTION_EDIT_PERMS);

        String groupsLabel = user.getGroups().isEmpty()
                ? "default"
                : String.join(", ", user.getGroups());
        inv.setItem(HUB_SECONDARY_SLOT, makeIcon(Material.BOOKSHELF,
                Component.text("Edit Groups", NamedTextColor.AQUA, TextDecoration.BOLD),
                List.of(
                        Component.text("Groups: ", NamedTextColor.GRAY)
                                .append(Component.text(groupsLabel, NamedTextColor.YELLOW)),
                        gray("Click to toggle group memberships.")
                )));
        holder.mapAction(HUB_SECONDARY_SLOT, ACTION_EDIT_GROUPS);

        inv.setItem(HUB_TERTIARY_SLOT, makeIcon(Material.TNT,
                Component.text("Reset User", NamedTextColor.RED, TextDecoration.BOLD),
                List.of(
                        gray("Clear all direct permissions"),
                        gray("and reset to the default group.")
                )));
        holder.mapAction(HUB_TERTIARY_SLOT, ACTION_RESET_USER);

        inv.setItem(BACK_SLOT, backIcon("Back to Players"));
        holder.mapAction(BACK_SLOT, ACTION_BACK);

        fillBorder(inv);
        player.openInventory(inv);
    }

    public static void openUserPerms(Player player, PermissionManager manager, UUID targetUuid, int page) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        UserPermissions user = manager.getUserPermissions(targetUuid);
        String name = target.getName() == null ? targetUuid.toString() : target.getName();

        PermissionHolder holder = new PermissionHolder(PermissionHolder.MenuKind.USER_PERMS, null, targetUuid, page);
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE,
                MM.deserialize("<!italic><green>User Perms: " + name));
        holder.attach(inv);

        List<String> allPerms = Permissions.getAllPermissions();
        placePage(inv, holder, allPerms, page, (slot, perm) -> {
            inv.setItem(slot, permIcon(perm, user.hasDirectPermission(perm)));
            holder.mapString(slot, perm);
        });

        inv.setItem(BACK_SLOT, backIcon("Back to User"));
        holder.mapAction(BACK_SLOT, ACTION_BACK);

        addPagination(inv, holder, page, allPerms.size());
        fillBorder(inv);
        player.openInventory(inv);
    }

    public static void openUserGroupPicker(Player player, PermissionManager manager, UUID targetUuid, int page) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String name = target.getName() == null ? targetUuid.toString() : target.getName();
        UserPermissions user = manager.getUserPermissions(targetUuid);

        PermissionHolder holder = new PermissionHolder(PermissionHolder.MenuKind.USER_GROUP_PICKER, null, targetUuid, page);
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE,
                MM.deserialize("<!italic><green>Edit Groups: " + name));
        holder.attach(inv);

        List<String> groupNames = new ArrayList<>(manager.getGroups().keySet());
        groupNames.sort(String.CASE_INSENSITIVE_ORDER);
        placePage(inv, holder, groupNames, page, (slot, gName) -> {
            PermissionGroup group = manager.getGroups().get(gName);
            boolean isMember = user.hasGroup(group.getName());

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Permissions: ", NamedTextColor.GRAY)
                    .append(Component.text(group.getPermissions().size(), NamedTextColor.YELLOW)));
            if (group.getParentName() != null) {
                lore.add(Component.text("Inherits from: ", NamedTextColor.GRAY)
                        .append(Component.text(group.getParentName(), NamedTextColor.YELLOW)));
            }
            lore.add(gray(isMember ? "Click to remove." : "Click to add."));

            ItemStack icon = makeIcon(Material.CHEST,
                    Component.text(group.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD),
                    lore);
            if (isMember) {
                ItemMeta meta = icon.getItemMeta();
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                icon.setItemMeta(meta);
            }
            inv.setItem(slot, icon);
            holder.mapString(slot, gName);
        });

        inv.setItem(BACK_SLOT, backIcon("Back to User"));
        holder.mapAction(BACK_SLOT, ACTION_BACK);

        addPagination(inv, holder, page, groupNames.size());
        fillBorder(inv);
        player.openInventory(inv);
    }

    // -------------------------------------------------------------- Helpers

    @FunctionalInterface
    private interface SlotPlacer<T> {
        void place(int slot, T item);
    }

    private static <T> void placePage(Inventory inv, PermissionHolder holder, List<T> items, int page, SlotPlacer<T> placer) {
        int start = Math.max(0, page) * PAGE_CAPACITY;
        for (int i = 0; i < PAGE_CAPACITY && (start + i) < items.size(); i++) {
            placer.place(INNER_SLOTS[i], items.get(start + i));
        }
    }

    private static void addPagination(Inventory inv, PermissionHolder holder, int page, int totalItems) {
        if (page > 0) {
            inv.setItem(PREV_PAGE_SLOT, makeIcon(Material.LIME_STAINED_GLASS_PANE,
                    Component.text("Prev", NamedTextColor.GREEN, TextDecoration.BOLD),
                    List.of(gray("Page " + page))));
            holder.mapAction(PREV_PAGE_SLOT, ACTION_PREV);
        }
        int nextStart = (page + 1) * PAGE_CAPACITY;
        if (nextStart < totalItems) {
            inv.setItem(NEXT_PAGE_SLOT, makeIcon(Material.LIME_STAINED_GLASS_PANE,
                    Component.text("Next", NamedTextColor.GREEN, TextDecoration.BOLD),
                    List.of(gray("Page " + (page + 2)))));
            holder.mapAction(NEXT_PAGE_SLOT, ACTION_NEXT);
        }
    }

    private static List<UUID> listPlayers(PermissionManager manager, java.util.function.Predicate<UUID> filter) {
        Set<UUID> all = new HashSet<>();
        Bukkit.getOnlinePlayers().forEach(p -> all.add(p.getUniqueId()));
        all.addAll(manager.getUsers().keySet());

        return all.stream()
                .filter(filter)
                .sorted(Comparator.comparing((UUID u) -> {
                    String n = Bukkit.getOfflinePlayer(u).getName();
                    return n == null ? u.toString() : n;
                }, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static ItemStack groupIcon(PermissionGroup group) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Permissions: ", NamedTextColor.GRAY)
                .append(Component.text(group.getPermissions().size(), NamedTextColor.YELLOW)));
        if (group.getParentName() != null) {
            lore.add(Component.text("Inherits from: ", NamedTextColor.GRAY)
                    .append(Component.text(group.getParentName(), NamedTextColor.YELLOW)));
        }
        lore.add(gray("Click to manage."));
        return makeIcon(Material.CHEST,
                Component.text(group.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD),
                lore);
    }

    private static ItemStack playerHead(OfflinePlayer target, Component... loreLines) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            String name = target.getName() == null ? target.getUniqueId().toString() : target.getName();
            meta.displayName(Component.text(name, NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (Component c : loreLines) lore.add(c.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack permIcon(String perm, boolean has) {
        Material mat = has ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        return makeIcon(mat,
                Component.text(perm, has ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD),
                List.of(gray(has ? "Click to revoke." : "Click to grant.")));
    }

    private static ItemStack backIcon(String hover) {
        return makeIcon(Material.RED_STAINED_GLASS_PANE,
                Component.text("Back", NamedTextColor.RED, TextDecoration.BOLD),
                List.of(gray(hover)));
    }

    private static Component gray(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private static Component red(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    private static Component green(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    private static Component aqua(String text) {
        return Component.text(text, NamedTextColor.AQUA);
    }

    private static Component yellow(String text) {
        return Component.text(text, NamedTextColor.YELLOW);
    }

    private static ItemStack makeIcon(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            List<Component> cleaned = new ArrayList<>(lore.size());
            for (Component c : lore) cleaned.add(c.decoration(TextDecoration.ITALIC, false));
            meta.lore(cleaned);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void fillBorder(Inventory inv) {
        ItemStack filler = makeIcon(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        int size = inv.getSize();
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean edge = row == 0 || row == rows - 1 || col == 0 || col == 8;
            if (edge && inv.getItem(slot) == null) inv.setItem(slot, filler);
        }
    }
}