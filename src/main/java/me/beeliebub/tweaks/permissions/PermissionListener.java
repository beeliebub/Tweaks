package me.beeliebub.tweaks.permissions;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

/**
 * Listens for GUI clicks and chat prompts to manage permissions.
 */
public class PermissionListener implements Listener {
    private final PermissionManager manager;

    public PermissionListener(PermissionManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PermissionManager.PromptType prompt = manager.getPrompt(player.getUniqueId());
        if (prompt == null) return;

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        manager.setPrompt(player.getUniqueId(), null);

        if (prompt == PermissionManager.PromptType.CREATE_GROUP) {
            if (message.isEmpty() || message.contains(" ")) {
                player.sendMessage(Component.text("Invalid group name. Names cannot contain spaces.").color(NamedTextColor.RED));
                return;
            }
            if (manager.getGroups().containsKey(message.toLowerCase())) {
                player.sendMessage(Component.text("Group already exists.").color(NamedTextColor.RED));
                return;
            }

            manager.getGroups().put(message.toLowerCase(), new PermissionGroup(message));
            manager.saveGroups();
            player.sendMessage(Component.text("Group '" + message + "' created!").color(NamedTextColor.GREEN));
            
            // Reopen GUI on main thread
            Bukkit.getScheduler().runTask(manager.getPlugin(), () -> PermissionGUI.openGroupsMenu(player, manager));
        } else if (prompt == PermissionManager.PromptType.SEARCH_USER) {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(message);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                player.sendMessage(Component.text("Player '" + message + "' has never played before.").color(NamedTextColor.RED));
                return;
            }

            Bukkit.getScheduler().runTask(manager.getPlugin(), () -> PermissionGUI.openUserEditor(player, target.getUniqueId(), 0, manager));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.startsWith(PermissionGUI.TITLE_GROUPS) && 
            !title.startsWith(PermissionGUI.TITLE_USERS) &&
            !title.startsWith(PermissionGUI.PREFIX_GROUP_EDITOR) &&
            !title.startsWith(PermissionGUI.PREFIX_USER_EDITOR)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        if (title.equals(PermissionGUI.TITLE_GROUPS)) {
            handleGroupsMenu(player, clicked, itemName);
        } else if (title.startsWith(PermissionGUI.TITLE_USERS)) {
            handleUsersMenu(player, clicked, itemName, title);
        } else if (title.startsWith(PermissionGUI.PREFIX_GROUP_EDITOR)) {
            handleGroupEditor(player, clicked, itemName, title);
        } else if (title.startsWith(PermissionGUI.PREFIX_USER_EDITOR)) {
            handleUserEditor(player, clicked, itemName, title);
        } else if (title.startsWith(PermissionGUI.TITLE_SELECT_GROUP)) {
            handleGroupSelection(player, clicked, itemName, title);
        }
    }

    private void handleGroupsMenu(Player player, ItemStack clicked, String itemName) {
        if (clicked.getType() == Material.CHEST) {
            PermissionGUI.openGroupEditor(player, itemName, 0, manager);
        } else if (clicked.getType() == Material.PLAYER_HEAD) {
            PermissionGUI.openUsersMenu(player, 0, manager);
        } else if (clicked.getType() == Material.NETHER_STAR) {
            player.closeInventory();
            manager.setPrompt(player.getUniqueId(), PermissionManager.PromptType.CREATE_GROUP);
            player.sendMessage(Component.text("Please type the new group name in chat.").color(NamedTextColor.YELLOW));
        }
    }

    private void handleUsersMenu(Player player, ItemStack clicked, String itemName, String title) {
        int page = extractPage(title);
        if (clicked.getType() == Material.PLAYER_HEAD) {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(itemName);
            if (target.hasPlayedBefore() || target.isOnline()) {
                PermissionGUI.openUserEditor(player, target.getUniqueId(), 0, manager);
            }
        } else if (clicked.getType() == Material.COMPASS) {
            player.closeInventory();
            manager.setPrompt(player.getUniqueId(), PermissionManager.PromptType.SEARCH_USER);
            player.sendMessage(Component.text("Please type the player name to search for.").color(NamedTextColor.YELLOW));
        } else if (itemName.equals("Next Page")) {
            PermissionGUI.openUsersMenu(player, page + 1, manager);
        } else if (itemName.equals("Previous Page")) {
            PermissionGUI.openUsersMenu(player, page - 1, manager);
        } else if (itemName.equals("Back to Groups")) {
            PermissionGUI.openGroupsMenu(player, manager);
        }
    }

    private void handleGroupEditor(Player player, ItemStack clicked, String itemName, String title) {
        String groupName = title.substring(PermissionGUI.PREFIX_GROUP_EDITOR.length()).split(" \\(")[0];
        int page = extractPage(title);

        if (clicked.getType().name().contains("STAINED_GLASS_PANE")) {
            PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
            if (group != null) {
                if (group.hasDirectPermission(itemName)) group.removePermission(itemName);
                else group.addPermission(itemName);
                manager.saveGroups();
                refreshAllInGroup(groupName);
                PermissionGUI.openGroupEditor(player, groupName, page, manager);
            }
        } else if (itemName.equals("Next Page")) {
            PermissionGUI.openGroupEditor(player, groupName, page + 1, manager);
        } else if (itemName.equals("Previous Page")) {
            PermissionGUI.openGroupEditor(player, groupName, page - 1, manager);
        } else if (itemName.equals("Back to Groups")) {
            PermissionGUI.openGroupsMenu(player, manager);
        }
    }

    private void handleUserEditor(Player player, ItemStack clicked, String itemName, String title) {
        String userName = title.substring(PermissionGUI.PREFIX_USER_EDITOR.length()).split(" \\(")[0];
        @SuppressWarnings("deprecation")
        UUID uuid = Bukkit.getOfflinePlayer(userName).getUniqueId();
        int page = extractPage(title);

        if (clicked.getType().name().contains("STAINED_GLASS_PANE")) {
            UserPermissions user = manager.getUserPermissions(uuid);
            if (user.hasDirectPermission(itemName)) user.removePermission(itemName);
            else user.addPermission(itemName);
            manager.saveUsers();
            refreshPlayer(uuid);
            PermissionGUI.openUserEditor(player, uuid, page, manager);
        } else if (clicked.getType() == Material.BOOK) {
            PermissionGUI.openGroupSelectionMenu(player, uuid, manager);
        } else if (clicked.getType() == Material.TNT) {
            manager.getUsers().remove(uuid);
            manager.saveUsers();
            refreshPlayer(uuid);
            player.sendMessage(Component.text("User permissions and group reset.").color(NamedTextColor.GREEN));
            PermissionGUI.openUsersMenu(player, 0, manager);
        } else if (itemName.equals("Next Page")) {
            PermissionGUI.openUserEditor(player, uuid, page + 1, manager);
        } else if (itemName.equals("Previous Page")) {
            PermissionGUI.openUserEditor(player, uuid, page - 1, manager);
        } else if (itemName.equals("Back to Users")) {
            PermissionGUI.openUsersMenu(player, 0, manager);
        }
    }

    private void handleGroupSelection(Player player, ItemStack clicked, String itemName, String title) {
        String userName = title.substring(PermissionGUI.TITLE_SELECT_GROUP.length());
        @SuppressWarnings("deprecation")
        UUID uuid = Bukkit.getOfflinePlayer(userName).getUniqueId();

        if (clicked.getType() == Material.CHEST) {
            UserPermissions user = manager.getUserPermissions(uuid);
            user.setGroupName(itemName.toLowerCase());
            manager.saveUsers();
            refreshPlayer(uuid);
            player.sendMessage(Component.text("User " + userName + " assigned to group " + itemName + ".").color(NamedTextColor.GREEN));
            PermissionGUI.openUserEditor(player, uuid, 0, manager);
        } else if (itemName.equals("Cancel")) {
            PermissionGUI.openUserEditor(player, uuid, 0, manager);
        }
    }

    private int extractPage(String title) {
        try {
            String[] parts = title.split("Page ");
            if (parts.length < 2) return 0;
            return Integer.parseInt(parts[1].replace(")", "")) - 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private void refreshPlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) manager.refreshPlayer(player);
    }

    private void refreshAllInGroup(String groupName) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UserPermissions user = manager.getUsers().get(player.getUniqueId());
            if (user != null && groupName.equalsIgnoreCase(user.getGroupName())) {
                manager.refreshPlayer(player);
            } else if (user == null && groupName.equalsIgnoreCase("default")) {
                manager.refreshPlayer(player);
            }
        }
    }
}
