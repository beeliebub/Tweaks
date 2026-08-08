package me.beeliebub.tweaks.permissions;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.core.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

// /perms GUI hierarchy — fully Paper Dialog-driven. Multi-action dialogs hold
// paginated lists of buttons; each button's DialogAction.customClick callback
// re-opens the same dialog with fresh state after the underlying mutation,
// which is how toggles and navigation cycles work without needing a separate
// inventory click listener. The two text-entry prompts (Create Group, Search
// Player) are confirmation dialogs with a DialogInput.text field; they
// replaced the previous AsyncChatEvent-based prompts.
//
// Hierarchy:
//   MAIN ──┬─ GROUPS_LIST ──── GROUP_HUB ──┬─ GROUP_PERM_CATEGORIES ── GROUP_PERMS (paginated per category)
//          │       │                       ├─ GROUP_MEMBERS_TOGGLE
//          │       └─ CREATE_GROUP         └─ GROUP_INHERITANCE_PICKER
//          └─ USERS_LIST ──┬─ USER_HUB ──┬─ USER_PERM_CATEGORIES ── USER_PERMS (paginated per category)
//                          │             └─ USER_GROUP_PICKER
//                          └─ SEARCH_USER → USER_HUB
@SuppressWarnings("UnstableApiUsage") // Paper's Dialog API is @ApiStatus.Experimental in 26.2.
public final class PermissionGUI {

    private PermissionGUI() {}

    // Pagination layout shared by every multi-action list dialog.
    private static final int DIALOG_PAGE_SIZE = 12;
    private static final int DIALOG_COLUMNS = 2;

    // ------------------------------------------------------------------ Main

    public static void openMainMenu(Player player, PermissionManager manager) {
        ActionButton groupsButton = dialogButton(
                Messages.PERMISSIONS.groupsLabel(),
                Messages.PERMISSIONS.groupsTooltip(),
                p -> openGroupsMenu(p, manager, 0));

        ActionButton playersButton = dialogButton(
                Messages.PERMISSIONS.playersLabel(),
                Messages.PERMISSIONS.playersTooltip(),
                p -> openUsersMenu(p, manager, 0));

        DialogBase base = DialogBase.builder(
                        Messages.PERMISSIONS.mainTitle())
                .body(List.of(
                        DialogBody.plainMessage(Messages.PERMISSIONS.mainBody())
                ))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(List.of(groupsButton, playersButton))
                        .columns(2)
                        .build()));

        player.showDialog(dialog);
    }

    // ----------------------------------------------------------- Groups list

    public static void openGroupsMenu(Player player, PermissionManager manager, int page) {
        List<String> groupNames = new ArrayList<>(manager.getGroups().keySet());
        groupNames.sort(String.CASE_INSENSITIVE_ORDER);

        int totalPages = Math.max(1, (groupNames.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, groupNames.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            PermissionGroup group = manager.getGroups().get(groupNames.get(i));
            buttons.add(groupListEntryButton(group, manager));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target -> openGroupsMenu(target, manager, currentPage - 1),
                target -> openGroupsMenu(target, manager, currentPage + 1));

        buttons.add(dialogButton(
                Messages.PERMISSIONS.createGroupLabel(),
                Messages.PERMISSIONS.createGroupTooltip(),
                p -> openCreateGroupDialog(p, manager)));

        ActionButton back = dialogButton(
                Messages.PERMISSIONS.backLabel(),
                Messages.PERMISSIONS.backToMainTooltip(),
                p -> openMainMenu(p, manager));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.groupsTitle())
                .body(List.of(DialogBody.plainMessage(Messages.PERMISSIONS.pageSummary(
                        groupNames.size(), me.beeliebub.tweaks.core.PermissionMessages.PageNoun.GROUP, currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static ActionButton groupListEntryButton(PermissionGroup group, PermissionManager manager) {
        return dialogButton(Messages.PERMISSIONS.groupListLabel(group.getName()),
                Messages.PERMISSIONS.groupListTooltip(group.getPermissions().size(), group.getParentName()),
                p -> openGroupHub(p, manager, group.getName()));
    }

    // ----------------------------------------------------------- Create Group

    public static void openCreateGroupDialog(Player player, PermissionManager manager) {
        ActionButton create = ActionButton.builder(
                        Messages.PERMISSIONS.createLabel()
                                .decoration(TextDecoration.ITALIC, false))
                .tooltip(Messages.PERMISSIONS.createTooltip())
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                handleCreateGroupSubmission(p, manager, view.getText("group_name"));
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Messages.PERMISSIONS.cancelLabel(),
                Messages.PERMISSIONS.cancelGroupsTooltip(),
                p -> openGroupsMenu(p, manager, 0));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.createGroupTitle())
                .body(List.of(DialogBody.plainMessage(
                        Messages.PERMISSIONS.createGroupBody())))
                .inputs(List.of(
                        DialogInput.text("group_name",
                                        Messages.PERMISSIONS.groupNameInput())
                                .maxLength(32)
                                .build()
                ))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(create, cancel)));

        player.showDialog(dialog);
    }

    private static void handleCreateGroupSubmission(Player player, PermissionManager manager, String rawName) {
        if (!canManage(player)) return;
        String trimmed = rawName == null ? "" : rawName.trim();
        if (trimmed.isEmpty() || trimmed.contains(" ")) {
            player.sendMessage(Messages.PERMISSIONS.invalidGroupName());
            openGroupsMenu(player, manager, 0);
            return;
        }
        String key = trimmed.toLowerCase();
        if (manager.getGroups().containsKey(key)) {
            player.sendMessage(Messages.PERMISSIONS.groupAlreadyExists());
            openGroupsMenu(player, manager, 0);
            return;
        }
        manager.getGroups().put(key, new PermissionGroup(trimmed));
        manager.saveGroups();
        player.sendMessage(Messages.PERMISSIONS.groupCreated(trimmed));
        openGroupsMenu(player, manager, 0);
    }

    // ---------------------------------------------------------- Group editor

    public static void openGroupHub(Player player, PermissionManager manager, String groupName) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        String name = group.getName();

        List<ActionButton> buttons = new ArrayList<>();

        buttons.add(dialogButton(
                Messages.PERMISSIONS.editPermissionsLabel(),
                Messages.PERMISSIONS.directPermissionsTooltip(group.getPermissions().size()),
                p -> openGroupPermCategories(p, manager, name)));

        if (!name.equalsIgnoreCase("default")) {
            buttons.add(dialogButton(
                    Messages.PERMISSIONS.manageMembersLabel(),
                    Messages.PERMISSIONS.manageMembersTooltip(),
                    p -> openGroupMembersToggle(p, manager, name, 0)));
        }

        buttons.add(dialogButton(
                Messages.PERMISSIONS.inheritanceLabel(),
                Messages.PERMISSIONS.inheritanceTooltip(group.getParentName()),
                p -> openGroupInheritancePicker(p, manager, name, 0)));

        if (!name.equalsIgnoreCase("default")) {
            buttons.add(dialogButton(
                    Messages.PERMISSIONS.deleteGroupLabel(),
                    Messages.PERMISSIONS.deleteGroupWarning(),
                    p -> handleDeleteGroup(p, manager, name)));
        }

        ActionButton back = dialogButton(
                Messages.PERMISSIONS.backToGroupsLabel(),
                Messages.PERMISSIONS.groupsListTooltip(),
                p -> openGroupsMenu(p, manager, 0));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.groupTitle(name))
                .body(List.of(DialogBody.plainMessage(
                        Messages.PERMISSIONS.groupBody())))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void handleDeleteGroup(Player player, PermissionManager manager, String groupName) {
        if (groupName.equalsIgnoreCase("default")) {
            player.sendMessage(Messages.PERMISSIONS.defaultGroupCannotBeDeleted());
            openGroupHub(player, manager, groupName);
            return;
        }
        if (!manager.deleteGroup(groupName)) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        manager.saveGroups();
        manager.saveUsers();
        manager.refreshAllOnlinePlayers();
        player.sendMessage(Messages.PERMISSIONS.groupDeleted(groupName));
        openGroupsMenu(player, manager, 0);
    }

    public static void openGroupPermCategories(Player player, PermissionManager manager, String groupName) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        String name = group.getName();
        LinkedHashMap<String, String> categories = Permissions.getCategories();

        List<ActionButton> buttons = new ArrayList<>();
        for (Map.Entry<String, String> entry : categories.entrySet()) {
            String catKey = entry.getKey();
            String catDisplay = entry.getValue();
            List<String> catPerms = Permissions.getPermissionsByCategory(catKey);
            long grantedCount = catPerms.stream().filter(group::hasDirectPermission).count();
            Component label = Messages.PERMISSIONS.categoryLabel(catDisplay);
            Component tip = Messages.PERMISSIONS.categoryTooltip(catPerms.size(), grantedCount);
            buttons.add(dialogButton(label, tip,
                    p -> openGroupPerms(p, manager, name, catKey, 0)));
        }

        ActionButton back = dialogButton(
                Messages.PERMISSIONS.backToGroupLabel(),
                Messages.PERMISSIONS.groupMenuTooltip(),
                p -> openGroupHub(p, manager, name));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.groupPermissionsTitle(name))
                .body(List.of(DialogBody.plainMessage(
                        Messages.PERMISSIONS.selectCategoryBody())))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    public static void openGroupPerms(Player player, PermissionManager manager, String groupName, String category, int page) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        String name = group.getName();
        List<String> catPerms = Permissions.getPermissionsByCategory(category);
        String catDisplay = Permissions.getCategories().getOrDefault(category, category);

        int totalPages = Math.max(1, (catPerms.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, catPerms.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            String perm = catPerms.get(i);
            boolean has = group.hasDirectPermission(perm);
            Component label = Messages.PERMISSIONS.permissionToggleLabel(perm, has);
            Component tip = Messages.PERMISSIONS.permissionToggleTooltip(perm, Permissions.getDescription(perm), has);
            buttons.add(dialogButton(label, tip,
                    p -> toggleGroupPermission(p, manager, name, perm, category, currentPage)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target -> openGroupPerms(target, manager, name, category, currentPage - 1),
                target -> openGroupPerms(target, manager, name, category, currentPage + 1));

        ActionButton back = dialogButton(
                Messages.PERMISSIONS.backToCategoriesLabel(),
                Messages.PERMISSIONS.categoriesTooltip(),
                p -> openGroupPermCategories(p, manager, name));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.permissionsTitle(catDisplay, name))
                .body(List.of(DialogBody.plainMessage(Messages.PERMISSIONS.pageSummary(
                        catPerms.size(), me.beeliebub.tweaks.core.PermissionMessages.PageNoun.PERMISSION, currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void toggleGroupPermission(Player player, PermissionManager manager, String groupName, String permission, String category, int returnPage) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        if (group.hasDirectPermission(permission)) group.removePermission(permission);
        else group.addPermission(permission);
        manager.saveGroups();
        manager.refreshAllOnlinePlayers();
        openGroupPerms(player, manager, group.getName(), category, returnPage);
    }

    public static void openGroupMembersToggle(Player player, PermissionManager manager, String groupName, int page) {
        if (groupName.equalsIgnoreCase("default")) {
            openGroupHub(player, manager, groupName);
            return;
        }
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        String name = group.getName();
        List<UUID> all = knownPlayerIds(manager);

        int totalPages = Math.max(1, (all.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, all.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            UUID uuid = all.get(i);
            OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
            UserPermissions u = manager.getUsers().get(uuid);
            boolean isMember = u != null && u.hasGroup(name);
            String playerName = target.getName() == null ? uuid.toString() : target.getName();
            String groupsLabel = groupSummary(u);

            Component label = Messages.PERMISSIONS.playerMembershipLabel(playerName, isMember, NamedTextColor.GRAY);
            Component tip = Messages.PERMISSIONS.playerDetailsTooltip(target.isOnline(), groupsLabel, isMember);
            buttons.add(dialogButton(label, tip,
                    p -> toggleGroupMembership(p, manager, name, uuid, currentPage)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target -> openGroupMembersToggle(target, manager, name, currentPage - 1),
                target -> openGroupMembersToggle(target, manager, name, currentPage + 1));

        ActionButton back = dialogButton(
                Messages.PERMISSIONS.backToGroupLabel(),
                Messages.PERMISSIONS.groupMenuTooltip(),
                p -> openGroupHub(p, manager, name));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.membersTitle(name))
                .body(List.of(DialogBody.plainMessage(Messages.PERMISSIONS.pageSummary(
                        all.size(), me.beeliebub.tweaks.core.PermissionMessages.PageNoun.PLAYER, currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void toggleGroupMembership(Player player, PermissionManager manager, String groupName, UUID target, int returnPage) {
        if (groupName.equalsIgnoreCase("default")) {
            openGroupHub(player, manager, groupName);
            return;
        }
        UserPermissions u = manager.getUserPermissions(target);
        if (u.hasGroup(groupName)) u.removeGroup(groupName);
        else u.addGroup(groupName);
        manager.saveUsers();
        refreshOnlinePlayer(manager, target);
        openGroupMembersToggle(player, manager, groupName, returnPage);
    }

    public static void openGroupInheritancePicker(Player player, PermissionManager manager, String groupName, int page) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        String name = group.getName();
        List<String> candidates = new ArrayList<>(manager.getGroups().keySet());
        candidates.remove(name.toLowerCase());
        candidates.sort(String.CASE_INSENSITIVE_ORDER);

        int totalPages = Math.max(1, (candidates.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, candidates.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            PermissionGroup candidate = manager.getGroups().get(candidates.get(i));
            boolean isCurrentParent = candidate.getName().equalsIgnoreCase(group.getParentName());

            Component label = Messages.PERMISSIONS.parentCandidateLabel(candidate.getName(), isCurrentParent);
            buttons.add(dialogButton(label, Messages.PERMISSIONS.parentCandidateTooltip(
                    candidate.getPermissions().size(), candidate.getParentName(), isCurrentParent),
                    p -> setGroupParent(p, manager, name, candidate.getName())));
        }

        buttons.add(dialogButton(
                Messages.PERMISSIONS.noneLabel(),
                Messages.PERMISSIONS.clearInheritanceTooltip(),
                p -> setGroupParent(p, manager, name, null)));

        addPageNavButtons(buttons, currentPage, totalPages,
                target -> openGroupInheritancePicker(target, manager, name, currentPage - 1),
                target -> openGroupInheritancePicker(target, manager, name, currentPage + 1));

        ActionButton back = dialogButton(
                Messages.PERMISSIONS.backToGroupLabel(),
                Messages.PERMISSIONS.groupMenuTooltip(),
                p -> openGroupHub(p, manager, name));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.inheritanceTitle(name))
                .body(List.of(DialogBody.plainMessage(Messages.PERMISSIONS.pageSummary(
                        candidates.size(), me.beeliebub.tweaks.core.PermissionMessages.PageNoun.GROUP, currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void setGroupParent(Player player, PermissionManager manager, String groupName, String parentName) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        group.setParentName(parentName == null ? null : parentName.toLowerCase());
        manager.saveGroups();
        manager.refreshAllOnlinePlayers();
        player.sendMessage(Messages.PERMISSIONS.groupInheritanceSetFromGui(group.getName(), parentName));
        openGroupHub(player, manager, group.getName());
    }

    // ------------------------------------------------------------ Users list

    public static void openUsersMenu(Player player, PermissionManager manager, int page) {
        List<UUID> uuids = onlinePlayerIds();

        int totalPages = Math.max(1, (uuids.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, uuids.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            UUID uuid = uuids.get(i);
            OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
            UserPermissions u = manager.getUsers().get(uuid);
            String playerName = target.getName() == null ? uuid.toString() : target.getName();
            String groupsLabel = groupSummary(u);

            Component label = Messages.PERMISSIONS.playerListLabel(playerName);
            Component tip = Messages.PERMISSIONS.playerListTooltip(target.isOnline(), groupsLabel);
            buttons.add(dialogButton(label, tip, p -> openUserHub(p, manager, uuid)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target -> openUsersMenu(target, manager, currentPage - 1),
                target -> openUsersMenu(target, manager, currentPage + 1));

        buttons.add(dialogButton(
                Messages.PERMISSIONS.searchPlayerLabel(),
                Messages.PERMISSIONS.searchPlayerTooltip(),
                p -> openSearchUserDialog(p, manager)));

        ActionButton back = dialogButton(
                Messages.PERMISSIONS.backLabel(),
                Messages.PERMISSIONS.backToMainTooltip(),
                p -> openMainMenu(p, manager));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.playersTitle())
                .body(List.of(DialogBody.plainMessage(Messages.PERMISSIONS.pageSummary(
                        uuids.size(), me.beeliebub.tweaks.core.PermissionMessages.PageNoun.PLAYER, currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    // ----------------------------------------------------------- Search Player

    public static void openSearchUserDialog(Player player, PermissionManager manager) {
        ActionButton submit = ActionButton.builder(
                        Messages.PERMISSIONS.searchLabel()
                                .decoration(TextDecoration.ITALIC, false))
                .tooltip(Messages.PERMISSIONS.searchTooltip())
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                handleSearchPlayerSubmission(p, manager, view.getText("player_name"));
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Messages.PERMISSIONS.cancelLabel(),
                Messages.PERMISSIONS.cancelPlayersTooltip(),
                p -> openUsersMenu(p, manager, 0));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.searchPlayerTitle())
                .body(List.of(DialogBody.plainMessage(
                        Messages.PERMISSIONS.searchPlayerBody())))
                .inputs(List.of(
                        DialogInput.text("player_name",
                                        Messages.PERMISSIONS.playerNameInput())
                                .maxLength(16)
                                .build()
                ))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(submit, cancel)));

        player.showDialog(dialog);
    }

    private static void handleSearchPlayerSubmission(Player player, PermissionManager manager, String rawName) {
        if (!canManage(player)) return;
        String trimmed = rawName == null ? "" : rawName.trim();
        if (trimmed.isEmpty()) {
            player.sendMessage(Messages.PERMISSIONS.invalidPlayerName());
            openUsersMenu(player, manager, 0);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(trimmed);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(Messages.PERMISSIONS.playerNeverPlayed(trimmed));
            openUsersMenu(player, manager, 0);
            return;
        }
        openUserHub(player, manager, target.getUniqueId());
    }

    // ----------------------------------------------------------- User editor

    public static void openUserHub(Player player, PermissionManager manager, UUID targetUuid) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        UserPermissions user = manager.getUserPermissions(targetUuid);
        String name = target.getName() == null ? targetUuid.toString() : target.getName();
        String groupsLabel = groupSummary(user);

        List<ActionButton> buttons = new ArrayList<>();

        buttons.add(dialogButton(
                Messages.PERMISSIONS.editPermissionsLabel(),
                Messages.PERMISSIONS.directPermissionsTooltip(user.getPermissions().size()),
                p -> openUserPermCategories(p, manager, targetUuid)));

        buttons.add(dialogButton(
                Messages.PERMISSIONS.editGroupsLabel(),
                Messages.PERMISSIONS.editGroupsTooltip(groupsLabel),
                p -> openUserGroupPicker(p, manager, targetUuid, 0)));

        buttons.add(dialogButton(
                Messages.PERMISSIONS.resetUserLabel(),
                Messages.PERMISSIONS.resetUserTooltip(),
                p -> resetUser(p, manager, targetUuid)));

        ActionButton back = dialogButton(
                Messages.PERMISSIONS.backToPlayersLabel(),
                Messages.PERMISSIONS.playersListTooltip(),
                p -> openUsersMenu(p, manager, 0));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.userTitle(name))
                .body(List.of(DialogBody.plainMessage(
                        Messages.PERMISSIONS.userBody())))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void resetUser(Player player, PermissionManager manager, UUID targetUuid) {
        manager.getUsers().remove(targetUuid);
        manager.saveUsers();
        refreshOnlinePlayer(manager, targetUuid);
        String displayName = Bukkit.getOfflinePlayer(targetUuid).getName();
        player.sendMessage(Messages.PERMISSIONS.userReset(
                displayName == null ? targetUuid.toString() : displayName));
        openUserHub(player, manager, targetUuid);
    }

    public static void openUserPermCategories(Player player, PermissionManager manager, UUID targetUuid) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        UserPermissions user = manager.getUserPermissions(targetUuid);
        String name = target.getName() == null ? targetUuid.toString() : target.getName();
        LinkedHashMap<String, String> categories = Permissions.getCategories();

        List<ActionButton> buttons = new ArrayList<>();
        for (Map.Entry<String, String> entry : categories.entrySet()) {
            String catKey = entry.getKey();
            String catDisplay = entry.getValue();
            List<String> catPerms = Permissions.getPermissionsByCategory(catKey);
            long grantedCount = catPerms.stream().filter(user::hasDirectPermission).count();
            Component label = Messages.PERMISSIONS.categoryLabel(catDisplay);
            Component tip = Messages.PERMISSIONS.categoryTooltip(catPerms.size(), grantedCount);
            buttons.add(dialogButton(label, tip,
                    p -> openUserPerms(p, manager, targetUuid, catKey, 0)));
        }

        ActionButton back = dialogButton(
                Messages.PERMISSIONS.backToUserLabel(),
                Messages.PERMISSIONS.userMenuTooltip(),
                p -> openUserHub(p, manager, targetUuid));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.userPermissionsTitle(name))
                .body(List.of(DialogBody.plainMessage(
                        Messages.PERMISSIONS.selectCategoryBody())))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    public static void openUserPerms(Player player, PermissionManager manager, UUID targetUuid, String category, int page) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        UserPermissions user = manager.getUserPermissions(targetUuid);
        String name = target.getName() == null ? targetUuid.toString() : target.getName();
        List<String> catPerms = Permissions.getPermissionsByCategory(category);
        String catDisplay = Permissions.getCategories().getOrDefault(category, category);

        int totalPages = Math.max(1, (catPerms.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, catPerms.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            String perm = catPerms.get(i);
            boolean has = user.hasDirectPermission(perm);
            Component label = Messages.PERMISSIONS.permissionToggleLabel(perm, has);
            Component tip = Messages.PERMISSIONS.permissionToggleTooltip(perm, Permissions.getDescription(perm), has);
            buttons.add(dialogButton(label, tip,
                    p -> toggleUserPermission(p, manager, targetUuid, perm, category, currentPage)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target2 -> openUserPerms(target2, manager, targetUuid, category, currentPage - 1),
                target2 -> openUserPerms(target2, manager, targetUuid, category, currentPage + 1));

        ActionButton back = dialogButton(
                Messages.PERMISSIONS.backToCategoriesLabel(),
                Messages.PERMISSIONS.categoriesTooltip(),
                p -> openUserPermCategories(p, manager, targetUuid));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.permissionsTitle(catDisplay, name))
                .body(List.of(DialogBody.plainMessage(Messages.PERMISSIONS.pageSummary(
                        catPerms.size(), me.beeliebub.tweaks.core.PermissionMessages.PageNoun.PERMISSION, currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void toggleUserPermission(Player player, PermissionManager manager, UUID targetUuid, String permission, String category, int returnPage) {
        UserPermissions u = manager.getUserPermissions(targetUuid);
        if (u.hasDirectPermission(permission)) u.removePermission(permission);
        else u.addPermission(permission);
        manager.saveUsers();
        refreshOnlinePlayer(manager, targetUuid);
        openUserPerms(player, manager, targetUuid, category, returnPage);
    }

    public static void openUserGroupPicker(Player player, PermissionManager manager, UUID targetUuid, int page) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        UserPermissions user = manager.getUserPermissions(targetUuid);
        String name = target.getName() == null ? targetUuid.toString() : target.getName();

        List<String> groupNames = new ArrayList<>(manager.getGroups().keySet());
        groupNames.removeIf(groupKey -> groupKey.equalsIgnoreCase("default"));
        groupNames.sort(String.CASE_INSENSITIVE_ORDER);

        int totalPages = Math.max(1, (groupNames.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, groupNames.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            PermissionGroup group = manager.getGroups().get(groupNames.get(i));
            boolean isMember = user.hasGroup(group.getName());

            Component label = Messages.PERMISSIONS.playerMembershipLabel(group.getName(), isMember, NamedTextColor.YELLOW);
            buttons.add(dialogButton(label, Messages.PERMISSIONS.userGroupTooltip(
                    group.getPermissions().size(), group.getParentName(), isMember),
                    p -> toggleUserGroup(p, manager, targetUuid, group.getName(), currentPage)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target2 -> openUserGroupPicker(target2, manager, targetUuid, currentPage - 1),
                target2 -> openUserGroupPicker(target2, manager, targetUuid, currentPage + 1));

        ActionButton back = dialogButton(
                Messages.PERMISSIONS.backToUserLabel(),
                Messages.PERMISSIONS.userMenuTooltip(),
                p -> openUserHub(p, manager, targetUuid));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.editGroupsTitle(name))
                .body(List.of(DialogBody.plainMessage(Messages.PERMISSIONS.pageSummary(
                        groupNames.size(), me.beeliebub.tweaks.core.PermissionMessages.PageNoun.GROUP, currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void toggleUserGroup(Player player, PermissionManager manager, UUID targetUuid, String groupName, int returnPage) {
        if (groupName.equalsIgnoreCase("default")) {
            openUserGroupPicker(player, manager, targetUuid, returnPage);
            return;
        }
        UserPermissions u = manager.getUserPermissions(targetUuid);
        if (u.hasGroup(groupName)) u.removeGroup(groupName);
        else u.addGroup(groupName);
        manager.saveUsers();
        refreshOnlinePlayer(manager, targetUuid);
        openUserGroupPicker(player, manager, targetUuid, returnPage);
    }

    // -------------------------------------------------------------- Helpers

    // --- Dialog helpers (group-branch and main menu) ---

    private static ClickCallback.Options unlimitedClicks() {
        return ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build();
    }

    private static ActionButton dialogButton(Component label, Component tooltip, Consumer<Player> action) {
        return ActionButton.builder(label.decoration(TextDecoration.ITALIC, false))
                .tooltip(tooltip.decoration(TextDecoration.ITALIC, false))
                .action(DialogAction.customClick(
                        (_, audience) -> {
                            if (audience instanceof Player p) {
                                if (!canManage(p)) return;
                                action.accept(p);
                            }
                        },
                        unlimitedClicks()))
                .build();
    }

    static boolean canManage(Player player) {
        if (player.hasPermission(Permissions.ADMIN_PERMISSIONS)) return true;
        player.sendMessage(Messages.noPermission());
        return false;
    }

    private static Component joinLines(Component... lines) {
        Component result = Component.empty();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) result = result.append(Component.newline());
            result = result.append(lines[i].decoration(TextDecoration.ITALIC, false));
        }
        return result;
    }

    private static void addPageNavButtons(List<ActionButton> buttons, int currentPage, int totalPages,
                                          Consumer<Player> prevAction, Consumer<Player> nextAction) {
        if (currentPage > 0) {
            buttons.add(dialogButton(
                    Messages.PERMISSIONS.previousPageLabel(),
                    Messages.PERMISSIONS.pageTooltip(currentPage, totalPages),
                    prevAction));
        }
        if (currentPage + 1 < totalPages) {
            buttons.add(dialogButton(
                    Messages.PERMISSIONS.nextPageLabel(),
                    Messages.PERMISSIONS.pageTooltip(currentPage + 2, totalPages),
                    nextAction));
        }
    }

    private static void refreshOnlinePlayer(PermissionManager manager, UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) manager.refreshPlayer(p);
    }

    static List<UUID> onlinePlayerIds() {
        return Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Player::getUniqueId)
                .toList();
    }

    private static List<UUID> knownPlayerIds(PermissionManager manager) {
        Set<UUID> all = new HashSet<>();
        Bukkit.getOnlinePlayers().forEach(p -> all.add(p.getUniqueId()));
        all.addAll(manager.getUsers().keySet());

        return all.stream()
                .sorted(Comparator.comparing((UUID u) -> {
                    String n = Bukkit.getOfflinePlayer(u).getName();
                    return n == null ? u.toString() : n;
                }, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String groupSummary(UserPermissions user) {
        List<String> names = new ArrayList<>();
        names.add("default");
        if (user != null) {
            user.getGroups().stream()
                    .filter(name -> !name.equalsIgnoreCase("default"))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(names::add);
        }
        return String.join(", ", names);
    }
}
