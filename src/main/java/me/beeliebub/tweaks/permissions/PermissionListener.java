package me.beeliebub.tweaks.permissions;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.UUID;

// Routes clicks/drags inside the /perms GUI hierarchy.
//
// Click routing is data-driven: PermissionGUI populates a PermissionHolder with
// per-slot action/payload maps. This listener cancels every click in the GUI,
// then dispatches by holder kind + slot to the matching open-* method on
// PermissionGUI or to a backend mutation on PermissionManager.
//
// Async chat handling is preserved for the two prompt types (CREATE_GROUP,
// SEARCH_USER); the GUI is reopened on the main thread once the prompt resolves.
public class PermissionListener implements Listener {

    private final PermissionManager manager;

    public PermissionListener(PermissionManager manager) {
        this.manager = manager;
    }

    // ---------------------------------------------------------------- Chat

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PermissionManager.PromptType prompt = manager.getPrompt(player.getUniqueId());
        if (prompt == null) return;

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        manager.setPrompt(player.getUniqueId(), null);

        switch (prompt) {
            case CREATE_GROUP -> handleCreateGroupPrompt(player, message);
            case SEARCH_USER -> handleSearchPlayerPrompt(player, message);
        }
    }

    private void handleCreateGroupPrompt(Player player, String message) {
        if (message.isEmpty() || message.contains(" ")) {
            player.sendMessage(Component.text("Invalid group name. Names cannot contain spaces.", NamedTextColor.RED));
            return;
        }
        String key = message.toLowerCase();
        if (manager.getGroups().containsKey(key)) {
            player.sendMessage(Component.text("Group already exists.", NamedTextColor.RED));
            return;
        }
        manager.getGroups().put(key, new PermissionGroup(message));
        manager.saveGroups();
        player.sendMessage(Component.text("Group '" + message + "' created.", NamedTextColor.GREEN));
        Bukkit.getScheduler().runTask(manager.getPlugin(), () -> PermissionGUI.openGroupsMenu(player, manager, 0));
    }

    @SuppressWarnings("deprecation")
    private void handleSearchPlayerPrompt(Player player, String message) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(message);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(Component.text("Player '" + message + "' has never played before.", NamedTextColor.RED));
            return;
        }
        Bukkit.getScheduler().runTask(manager.getPlugin(),
                () -> PermissionGUI.openUserHub(player, manager, target.getUniqueId()));
    }

    // ----------------------------------------------------------- GUI clicks

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PermissionHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PermissionHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        String action = holder.actionAt(slot);
        String s = holder.stringAt(slot);
        UUID u = holder.uuidAt(slot);

        switch (holder.kind()) {
            case MAIN -> dispatchMain(player, action);
            case GROUPS_LIST -> dispatchGroupsList(player, holder, action, s);
            case GROUP_HUB -> dispatchGroupHub(player, holder, action);
            case GROUP_PERMS -> dispatchGroupPerms(player, holder, action, s);
            case GROUP_MEMBERS_TOGGLE -> dispatchGroupMembersToggle(player, holder, action, u);
            case GROUP_INHERITANCE_PICKER -> dispatchGroupInheritancePicker(player, holder, action, s);
            case USERS_LIST -> dispatchUsersList(player, holder, action, u);
            case USER_HUB -> dispatchUserHub(player, holder, action);
            case USER_PERMS -> dispatchUserPerms(player, holder, action, s);
            case USER_GROUP_PICKER -> dispatchUserGroupPicker(player, holder, action, s);
        }
    }

    private void dispatchMain(Player player, String action) {
        if (action == null) return;
        switch (action) {
            case PermissionGUI.ACTION_OPEN_GROUPS -> PermissionGUI.openGroupsMenu(player, manager, 0);
            case PermissionGUI.ACTION_OPEN_USERS -> PermissionGUI.openUsersMenu(player, manager, 0);
        }
    }

    private void dispatchGroupsList(Player player, PermissionHolder holder, String action, String groupName) {
        if (action != null) {
            switch (action) {
                case PermissionGUI.ACTION_MAIN -> PermissionGUI.openMainMenu(player, manager);
                case PermissionGUI.ACTION_NEXT -> PermissionGUI.openGroupsMenu(player, manager, holder.page() + 1);
                case PermissionGUI.ACTION_PREV -> PermissionGUI.openGroupsMenu(player, manager, Math.max(0, holder.page() - 1));
                case PermissionGUI.ACTION_CREATE_GROUP -> {
                    player.closeInventory();
                    manager.setPrompt(player.getUniqueId(), PermissionManager.PromptType.CREATE_GROUP);
                    player.sendMessage(Component.text("Type the new group name in chat.", NamedTextColor.YELLOW));
                }
                default -> {}
            }
            return;
        }
        if (groupName != null) {
            PermissionGUI.openGroupHub(player, manager, groupName);
        }
    }

    private void dispatchGroupHub(Player player, PermissionHolder holder, String action) {
        if (action == null) return;
        String groupName = holder.groupName();
        if (groupName == null) return;
        switch (action) {
            case PermissionGUI.ACTION_BACK -> PermissionGUI.openGroupsMenu(player, manager, 0);
            case PermissionGUI.ACTION_EDIT_PERMS -> PermissionGUI.openGroupPerms(player, manager, groupName, 0);
            case PermissionGUI.ACTION_MANAGE_MEMBERS -> PermissionGUI.openGroupMembersToggle(player, manager, groupName, 0);
            case PermissionGUI.ACTION_MANAGE_INHERITANCE -> PermissionGUI.openGroupInheritancePicker(player, manager, groupName, 0);
            case PermissionGUI.ACTION_DELETE_GROUP -> {
                if (groupName.equalsIgnoreCase("default")) {
                    player.sendMessage(Component.text("Cannot delete default group.", NamedTextColor.RED));
                    return;
                }
                manager.getGroups().remove(groupName.toLowerCase());
                manager.saveGroups();
                refreshAllInGroup(groupName);
                player.sendMessage(Component.text("Group '" + groupName + "' deleted.", NamedTextColor.GREEN));
                PermissionGUI.openGroupsMenu(player, manager, 0);
            }
            default -> {}
        }
    }

    private void dispatchGroupPerms(Player player, PermissionHolder holder, String action, String permission) {
        String groupName = holder.groupName();
        if (groupName == null) return;
        if (action != null) {
            switch (action) {
                case PermissionGUI.ACTION_BACK -> PermissionGUI.openGroupHub(player, manager, groupName);
                case PermissionGUI.ACTION_NEXT -> PermissionGUI.openGroupPerms(player, manager, groupName, holder.page() + 1);
                case PermissionGUI.ACTION_PREV -> PermissionGUI.openGroupPerms(player, manager, groupName, Math.max(0, holder.page() - 1));
                default -> {}
            }
            return;
        }
        if (permission != null) {
            PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
            if (group == null) return;
            if (group.hasDirectPermission(permission)) group.removePermission(permission);
            else group.addPermission(permission);
            manager.saveGroups();
            refreshAllInGroup(group.getName());
            PermissionGUI.openGroupPerms(player, manager, groupName, holder.page());
        }
    }

    private void dispatchGroupMembersToggle(Player player, PermissionHolder holder, String action, UUID target) {
        String groupName = holder.groupName();
        if (groupName == null) return;
        if (action != null) {
            switch (action) {
                case PermissionGUI.ACTION_BACK -> PermissionGUI.openGroupHub(player, manager, groupName);
                case PermissionGUI.ACTION_NEXT -> PermissionGUI.openGroupMembersToggle(player, manager, groupName, holder.page() + 1);
                case PermissionGUI.ACTION_PREV -> PermissionGUI.openGroupMembersToggle(player, manager, groupName, Math.max(0, holder.page() - 1));
                default -> {}
            }
            return;
        }
        if (target != null) {
            UserPermissions u = manager.getUserPermissions(target);
            if (u.hasGroup(groupName)) {
                u.removeGroup(groupName);
            } else {
                u.addGroup(groupName);
            }

            manager.saveUsers();
            refreshPlayer(target);
            PermissionGUI.openGroupMembersToggle(player, manager, groupName, holder.page());
        }
    }

    private void dispatchGroupInheritancePicker(Player player, PermissionHolder holder, String action, String parentName) {
        String groupName = holder.groupName();
        if (groupName == null) return;
        if (action != null) {
            switch (action) {
                case PermissionGUI.ACTION_BACK -> PermissionGUI.openGroupHub(player, manager, groupName);
                case PermissionGUI.ACTION_NEXT -> PermissionGUI.openGroupInheritancePicker(player, manager, groupName, holder.page() + 1);
                case PermissionGUI.ACTION_PREV -> PermissionGUI.openGroupInheritancePicker(player, manager, groupName, Math.max(0, holder.page() - 1));
                default -> {}
            }
            return;
        }
        if (parentName != null) {
            PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
            if (group == null) return;

            String newParent = parentName.equalsIgnoreCase("none") ? null : parentName.toLowerCase();
            group.setParentName(newParent);
            manager.saveGroups();
            refreshAllInGroup(groupName);

            player.sendMessage(Component.text("Set inheritance for " + groupName + " to " + (newParent == null ? "none" : newParent) + ".", NamedTextColor.GREEN));
            PermissionGUI.openGroupHub(player, manager, groupName);
        }
    }

    private void dispatchUsersList(Player player, PermissionHolder holder, String action, UUID target) {
        if (action != null) {
            switch (action) {
                case PermissionGUI.ACTION_MAIN -> PermissionGUI.openMainMenu(player, manager);
                case PermissionGUI.ACTION_NEXT -> PermissionGUI.openUsersMenu(player, manager, holder.page() + 1);
                case PermissionGUI.ACTION_PREV -> PermissionGUI.openUsersMenu(player, manager, Math.max(0, holder.page() - 1));
                case PermissionGUI.ACTION_SEARCH_PLAYER -> {
                    player.closeInventory();
                    manager.setPrompt(player.getUniqueId(), PermissionManager.PromptType.SEARCH_USER);
                    player.sendMessage(Component.text("Type a player name in chat.", NamedTextColor.YELLOW));
                }
                default -> {}
            }
            return;
        }
        if (target != null) {
            PermissionGUI.openUserHub(player, manager, target);
        }
    }

    private void dispatchUserHub(Player player, PermissionHolder holder, String action) {
        if (action == null) return;
        UUID target = holder.userUuid();
        if (target == null) return;
        switch (action) {
            case PermissionGUI.ACTION_BACK -> PermissionGUI.openUsersMenu(player, manager, 0);
            case PermissionGUI.ACTION_EDIT_PERMS -> PermissionGUI.openUserPerms(player, manager, target, 0);
            case PermissionGUI.ACTION_EDIT_GROUPS -> PermissionGUI.openUserGroupPicker(player, manager, target, 0);
            case PermissionGUI.ACTION_RESET_USER -> {
                manager.getUsers().remove(target);
                manager.saveUsers();
                refreshPlayer(target);
                String name = Bukkit.getOfflinePlayer(target).getName();
                player.sendMessage(Component.text("Reset " + (name == null ? target.toString() : name) + " to defaults.", NamedTextColor.GREEN));
                PermissionGUI.openUserHub(player, manager, target);
            }
            default -> {}
        }
    }

    private void dispatchUserPerms(Player player, PermissionHolder holder, String action, String permission) {
        UUID target = holder.userUuid();
        if (target == null) return;
        if (action != null) {
            switch (action) {
                case PermissionGUI.ACTION_BACK -> PermissionGUI.openUserHub(player, manager, target);
                case PermissionGUI.ACTION_NEXT -> PermissionGUI.openUserPerms(player, manager, target, holder.page() + 1);
                case PermissionGUI.ACTION_PREV -> PermissionGUI.openUserPerms(player, manager, target, Math.max(0, holder.page() - 1));
                default -> {}
            }
            return;
        }
        if (permission != null) {
            UserPermissions u = manager.getUserPermissions(target);
            if (u.hasDirectPermission(permission)) u.removePermission(permission);
            else u.addPermission(permission);
            manager.saveUsers();
            refreshPlayer(target);
            PermissionGUI.openUserPerms(player, manager, target, holder.page());
        }
    }

    private void dispatchUserGroupPicker(Player player, PermissionHolder holder, String action, String groupName) {
        UUID target = holder.userUuid();
        if (target == null) return;
        if (action != null) {
            switch (action) {
                case PermissionGUI.ACTION_BACK -> PermissionGUI.openUserHub(player, manager, target);
                case PermissionGUI.ACTION_NEXT -> PermissionGUI.openUserGroupPicker(player, manager, target, holder.page() + 1);
                case PermissionGUI.ACTION_PREV -> PermissionGUI.openUserGroupPicker(player, manager, target, Math.max(0, holder.page() - 1));
                default -> {}
            }
            return;
        }
        if (groupName != null) {
            UserPermissions u = manager.getUserPermissions(target);
            if (u.hasGroup(groupName)) {
                u.removeGroup(groupName);
            } else {
                u.addGroup(groupName);
            }
            manager.saveUsers();
            refreshPlayer(target);
            PermissionGUI.openUserGroupPicker(player, manager, target, holder.page());
        }
    }

    private void refreshPlayer(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) manager.refreshPlayer(p);
    }

    private void refreshAllInGroup(String groupName) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UserPermissions u = manager.getUsers().get(p.getUniqueId());
            boolean inGroup;
            if (u == null || u.getGroups().isEmpty()) {
                inGroup = groupName.equalsIgnoreCase("default");
            } else {
                inGroup = u.hasGroup(groupName);
            }
            if (inGroup) {
                manager.refreshPlayer(p);
            }
        }
    }
}