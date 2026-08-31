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
import java.util.concurrent.CompletableFuture;
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
    private static final String UNLISTED_CATEGORY = "unlisted";

    private record PermissionEntry(String permission, String description) {}

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
        PermissionManager.StateSnapshot previous = manager.snapshotState();
        manager.getGroups().put(key, new PermissionGroup(trimmed));
        persistGroups(player, manager, previous,
                () -> {
                    player.sendMessage(Messages.PERMISSIONS.groupCreated(trimmed));
                    openGroupsMenu(player, manager, 0);
                },
                () -> openGroupsMenu(player, manager, 0));
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
        PermissionManager.StateSnapshot previous = manager.snapshotState();
        if (!manager.deleteGroup(groupName)) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        persistGroupsAndUsers(player, manager, previous,
                () -> {
                    manager.refreshAllOnlinePlayers();
                    player.sendMessage(Messages.PERMISSIONS.groupDeleted(groupName));
                    openGroupsMenu(player, manager, 0);
                },
                () -> openGroupsMenu(player, manager, 0));
    }

    public static void openGroupPermCategories(Player player, PermissionManager manager, String groupName) {
        openGroupPermCategories(player, manager, groupName, scanExternal(manager));
    }

    private static void openGroupPermCategories(Player player, PermissionManager manager,
                                                 String groupName,
                                                 List<ExternalPermissionCatalog.ExternalCategory> external) {
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
                    p -> openGroupPerms(p, manager, name, catKey, 0, external)));
        }

        for (ExternalPermissionCatalog.ExternalCategory category : external) {
            long grantedCount = category.nodes().stream()
                    .filter(node -> group.hasDirectPermission(node.node()))
                    .count();
            buttons.add(dialogButton(
                    Messages.PERMISSIONS.categoryLabel(category.displayName()),
                    Messages.PERMISSIONS.categoryTooltip(category.nodes().size(), grantedCount),
                    p -> openGroupPerms(p, manager, name, category.key(), 0, external)));
        }

        List<String> unlisted = unlistedPermissions(group.getPermissions(), external);
        if (!unlisted.isEmpty()) {
            buttons.add(dialogButton(
                    Messages.PERMISSIONS.unlistedCategoryLabel(),
                    Messages.PERMISSIONS.unlistedCategoryTooltip(unlisted.size()),
                    p -> openGroupPerms(p, manager, name, UNLISTED_CATEGORY, 0, external)));
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
        openGroupPerms(player, manager, groupName, category, page, scanExternal(manager));
    }

    private static void openGroupPerms(Player player, PermissionManager manager, String groupName,
                                       String category, int page,
                                       List<ExternalPermissionCatalog.ExternalCategory> external) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        String name = group.getName();
        List<PermissionEntry> entries = groupPermissionEntries(group, category, external);
        String catDisplay = permissionCategoryDisplay(category, external);

        int totalPages = Math.max(1, (entries.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, entries.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            PermissionEntry entry = entries.get(i);
            String perm = entry.permission();
            boolean has = group.hasDirectPermission(perm);
            Component label = Messages.PERMISSIONS.permissionToggleLabel(perm, has);
            Component tip = Messages.PERMISSIONS.permissionToggleTooltip(perm, entry.description(), has);
            buttons.add(dialogButton(label, tip,
                    p -> toggleGroupPermission(p, manager, name, perm, category, currentPage, external)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target -> openGroupPerms(target, manager, name, category, currentPage - 1, external),
                target -> openGroupPerms(target, manager, name, category, currentPage + 1, external));

        ActionButton back = dialogButton(
                Messages.PERMISSIONS.backToCategoriesLabel(),
                Messages.PERMISSIONS.categoriesTooltip(),
                p -> openGroupPermCategories(p, manager, name));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.permissionsTitle(catDisplay, name))
                .body(List.of(DialogBody.plainMessage(Messages.PERMISSIONS.pageSummary(
                        entries.size(), me.beeliebub.tweaks.core.PermissionMessages.PageNoun.PERMISSION, currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void toggleGroupPermission(Player player, PermissionManager manager, String groupName,
                                               String permission, String category, int returnPage,
                                               List<ExternalPermissionCatalog.ExternalCategory> external) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        PermissionManager.StateSnapshot previous = manager.snapshotState();
        if (group.hasDirectPermission(permission)) group.removePermission(permission);
        else group.addPermission(permission);
        persistGroups(player, manager, previous,
                () -> {
                    manager.refreshAllOnlinePlayers();
                    openGroupPerms(player, manager, group.getName(), category, returnPage, external);
                },
                () -> openGroupPerms(player, manager, groupName, category, returnPage, external));
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
        PermissionManager.StateSnapshot previous = manager.snapshotState();
        UserPermissions u = manager.getUserPermissions(target);
        if (u.hasGroup(groupName)) u.removeGroup(groupName);
        else u.addGroup(groupName);
        persistUsers(player, manager, previous,
                () -> {
                    refreshOnlinePlayer(manager, target);
                    openGroupMembersToggle(player, manager, groupName, returnPage);
                },
                () -> openGroupMembersToggle(player, manager, groupName, returnPage));
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
        PermissionManager.StateSnapshot previous = manager.snapshotState();
        group.setParentName(parentName == null ? null : parentName.toLowerCase());
        String name = group.getName();
        persistGroups(player, manager, previous,
                () -> {
                    manager.refreshAllOnlinePlayers();
                    player.sendMessage(Messages.PERMISSIONS.groupInheritanceSetFromGui(name, parentName));
                    openGroupHub(player, manager, name);
                },
                () -> openGroupHub(player, manager, name));
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
        PermissionManager.StateSnapshot previous = manager.snapshotState();
        manager.getUsers().remove(targetUuid);
        persistUsers(player, manager, previous,
                () -> {
                    refreshOnlinePlayer(manager, targetUuid);
                    String displayName = Bukkit.getOfflinePlayer(targetUuid).getName();
                    player.sendMessage(Messages.PERMISSIONS.userReset(
                            displayName == null ? targetUuid.toString() : displayName));
                    openUserHub(player, manager, targetUuid);
                },
                () -> openUserHub(player, manager, targetUuid));
    }

    public static void openUserPermCategories(Player player, PermissionManager manager, UUID targetUuid) {
        openUserPermCategories(player, manager, targetUuid, scanExternal(manager));
    }

    private static void openUserPermCategories(Player player, PermissionManager manager, UUID targetUuid,
                                                List<ExternalPermissionCatalog.ExternalCategory> external) {
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
                    p -> openUserPerms(p, manager, targetUuid, catKey, 0, external)));
        }

        for (ExternalPermissionCatalog.ExternalCategory category : external) {
            long grantedCount = category.nodes().stream()
                    .filter(node -> user.hasDirectPermission(node.node()))
                    .count();
            buttons.add(dialogButton(
                    Messages.PERMISSIONS.categoryLabel(category.displayName()),
                    Messages.PERMISSIONS.categoryTooltip(category.nodes().size(), grantedCount),
                    p -> openUserPerms(p, manager, targetUuid, category.key(), 0, external)));
        }

        List<String> unlisted = unlistedPermissions(user.getPermissions(), external);
        if (!unlisted.isEmpty()) {
            buttons.add(dialogButton(
                    Messages.PERMISSIONS.unlistedCategoryLabel(),
                    Messages.PERMISSIONS.unlistedCategoryTooltip(unlisted.size()),
                    p -> openUserPerms(p, manager, targetUuid, UNLISTED_CATEGORY, 0, external)));
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
        openUserPerms(player, manager, targetUuid, category, page, scanExternal(manager));
    }

    private static void openUserPerms(Player player, PermissionManager manager, UUID targetUuid,
                                      String category, int page,
                                      List<ExternalPermissionCatalog.ExternalCategory> external) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        UserPermissions user = manager.getUserPermissions(targetUuid);
        String name = target.getName() == null ? targetUuid.toString() : target.getName();
        List<PermissionEntry> entries = userPermissionEntries(user, category, external);
        String catDisplay = permissionCategoryDisplay(category, external);

        int totalPages = Math.max(1, (entries.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, entries.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            PermissionEntry entry = entries.get(i);
            String perm = entry.permission();
            boolean has = user.hasDirectPermission(perm);
            Component label = Messages.PERMISSIONS.permissionToggleLabel(perm, has);
            Component tip = Messages.PERMISSIONS.permissionToggleTooltip(perm, entry.description(), has);
            buttons.add(dialogButton(label, tip,
                    p -> toggleUserPermission(p, manager, targetUuid, perm, category, currentPage, external)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target2 -> openUserPerms(target2, manager, targetUuid, category, currentPage - 1, external),
                target2 -> openUserPerms(target2, manager, targetUuid, category, currentPage + 1, external));

        ActionButton back = dialogButton(
                Messages.PERMISSIONS.backToCategoriesLabel(),
                Messages.PERMISSIONS.categoriesTooltip(),
                p -> openUserPermCategories(p, manager, targetUuid));

        DialogBase base = DialogBase.builder(Messages.PERMISSIONS.permissionsTitle(catDisplay, name))
                .body(List.of(DialogBody.plainMessage(Messages.PERMISSIONS.pageSummary(
                        entries.size(), me.beeliebub.tweaks.core.PermissionMessages.PageNoun.PERMISSION, currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void toggleUserPermission(Player player, PermissionManager manager, UUID targetUuid,
                                              String permission, String category, int returnPage,
                                              List<ExternalPermissionCatalog.ExternalCategory> external) {
        PermissionManager.StateSnapshot previous = manager.snapshotState();
        UserPermissions u = manager.getUserPermissions(targetUuid);
        if (u.hasDirectPermission(permission)) u.removePermission(permission);
        else u.addPermission(permission);
        persistUsers(player, manager, previous,
                () -> {
                    refreshOnlinePlayer(manager, targetUuid);
                    openUserPerms(player, manager, targetUuid, category, returnPage, external);
                },
                () -> openUserPerms(player, manager, targetUuid, category, returnPage, external));
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
        PermissionManager.StateSnapshot previous = manager.snapshotState();
        UserPermissions u = manager.getUserPermissions(targetUuid);
        if (u.hasGroup(groupName)) u.removeGroup(groupName);
        else u.addGroup(groupName);
        persistUsers(player, manager, previous,
                () -> {
                    refreshOnlinePlayer(manager, targetUuid);
                    openUserGroupPicker(player, manager, targetUuid, returnPage);
                },
                () -> openUserGroupPicker(player, manager, targetUuid, returnPage));
    }

    // -------------------------------------------------------------- Helpers

    private static void persistGroups(Player player, PermissionManager manager,
                                      PermissionManager.StateSnapshot previous,
                                      Runnable onSuccess, Runnable onFailure) {
        PermissionManager.StateSnapshot expected = manager.snapshotState();
        completePersistence(player, manager, previous, expected, queueGroups(manager), onSuccess, onFailure);
    }

    private static void persistUsers(Player player, PermissionManager manager,
                                     PermissionManager.StateSnapshot previous,
                                     Runnable onSuccess, Runnable onFailure) {
        PermissionManager.StateSnapshot expected = manager.snapshotState();
        completePersistence(player, manager, previous, expected, queueUsers(manager), onSuccess, onFailure);
    }

    private static void persistGroupsAndUsers(Player player, PermissionManager manager,
                                              PermissionManager.StateSnapshot previous,
                                              Runnable onSuccess, Runnable onFailure) {
        PermissionManager.StateSnapshot expected = manager.snapshotState();
        CompletableFuture<Boolean> groups = queueGroups(manager);
        CompletableFuture<Boolean> both = groups.thenCompose(saved ->
                saved ? queueUsers(manager) : CompletableFuture.completedFuture(false));
        completePersistence(player, manager, previous, expected, both, onSuccess, onFailure);
    }

    private static CompletableFuture<Boolean> queueGroups(PermissionManager manager) {
        try {
            CompletableFuture<Boolean> future = manager.saveGroupsAsync();
            if (future != null) return future;
            manager.saveGroups();
            return CompletableFuture.completedFuture(true);
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(false);
        }
    }

    private static CompletableFuture<Boolean> queueUsers(PermissionManager manager) {
        try {
            CompletableFuture<Boolean> future = manager.saveUsersAsync();
            if (future != null) return future;
            manager.saveUsers();
            return CompletableFuture.completedFuture(true);
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(false);
        }
    }

    private static void completePersistence(Player player, PermissionManager manager,
                                             PermissionManager.StateSnapshot previous,
                                             PermissionManager.StateSnapshot expected,
                                             CompletableFuture<Boolean> persistence,
                                             Runnable onSuccess, Runnable onFailure) {
        persistence.whenComplete((saved, error) -> {
            Runnable completion = () -> {
                if (error == null && Boolean.TRUE.equals(saved)) {
                    onSuccess.run();
                    return;
                }
                if (manager.stateMatches(expected)) {
                    manager.restoreState(previous);
                }
                player.sendMessage(Messages.PERMISSIONS.storageFailed());
                onFailure.run();
            };
            var plugin = manager.getPlugin();
            if ((plugin == null || !plugin.isEnabled())
                    && (error != null || !Boolean.TRUE.equals(saved))
                    && manager.stateMatches(expected)) {
                manager.restoreState(previous);
            }
            runOnMain(manager, completion);
        });
    }

    private static void runOnMain(PermissionManager manager, Runnable action) {
        var plugin = manager.getPlugin();
        if (plugin == null || !plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) action.run();
        else Bukkit.getScheduler().runTask(plugin, action);
    }

    private static List<ExternalPermissionCatalog.ExternalCategory> scanExternal(PermissionManager manager) {
        return new ExternalPermissionCatalog(manager.getPlugin()).scan();
    }

    private static ExternalPermissionCatalog.ExternalCategory findExternal(
            String key, List<ExternalPermissionCatalog.ExternalCategory> external) {
        return external.stream()
                .filter(category -> category.key().equals(key))
                .findFirst()
                .orElse(null);
    }

    private static List<PermissionEntry> groupPermissionEntries(
            PermissionGroup group, String category,
            List<ExternalPermissionCatalog.ExternalCategory> external) {
        ExternalPermissionCatalog.ExternalCategory externalCategory = findExternal(category, external);
        if (externalCategory != null) {
            return externalCategory.nodes().stream()
                    .map(node -> new PermissionEntry(node.node(), node.description()))
                    .toList();
        }
        if (UNLISTED_CATEGORY.equals(category)) {
            return unlistedPermissions(group.getPermissions(), external).stream()
                    .map(permission -> new PermissionEntry(permission, permission))
                    .toList();
        }
        return Permissions.getPermissionsByCategory(category).stream()
                .map(permission -> new PermissionEntry(permission, Permissions.getDescription(permission)))
                .toList();
    }

    private static List<PermissionEntry> userPermissionEntries(
            UserPermissions user, String category,
            List<ExternalPermissionCatalog.ExternalCategory> external) {
        ExternalPermissionCatalog.ExternalCategory externalCategory = findExternal(category, external);
        if (externalCategory != null) {
            return externalCategory.nodes().stream()
                    .map(node -> new PermissionEntry(node.node(), node.description()))
                    .toList();
        }
        if (UNLISTED_CATEGORY.equals(category)) {
            return unlistedPermissions(user.getPermissions(), external).stream()
                    .map(permission -> new PermissionEntry(permission, permission))
                    .toList();
        }
        return Permissions.getPermissionsByCategory(category).stream()
                .map(permission -> new PermissionEntry(permission, Permissions.getDescription(permission)))
                .toList();
    }

    private static String permissionCategoryDisplay(
            String category, List<ExternalPermissionCatalog.ExternalCategory> external) {
        ExternalPermissionCatalog.ExternalCategory externalCategory = findExternal(category, external);
        if (externalCategory != null) {
            return externalCategory.displayName();
        }
        if (UNLISTED_CATEGORY.equals(category)) {
            return Messages.PERMISSIONS.unlistedCategoryName();
        }
        return Permissions.getCategories().getOrDefault(category, category);
    }

    private static List<String> unlistedPermissions(
            Set<String> directPermissions, List<ExternalPermissionCatalog.ExternalCategory> external) {
        Set<String> known = new HashSet<>(Permissions.getAllPermissions());
        external.forEach(category -> category.nodes().forEach(node -> known.add(node.node())));
        return directPermissions.stream()
                .filter(permission -> permission != null && !known.contains(permission.toLowerCase()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

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
