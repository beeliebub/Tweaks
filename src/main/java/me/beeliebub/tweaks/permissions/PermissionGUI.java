package me.beeliebub.tweaks.permissions;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
//   MAIN ──┬─ GROUPS_LIST ──── GROUP_HUB ──┬─ GROUP_PERMS
//          │       │                       ├─ GROUP_MEMBERS_TOGGLE
//          │       └─ CREATE_GROUP         └─ GROUP_INHERITANCE_PICKER
//          └─ USERS_LIST ──┬─ USER_HUB ──┬─ USER_PERMS
//                          │             └─ USER_GROUP_PICKER
//                          └─ SEARCH_USER → USER_HUB
@SuppressWarnings("UnstableApiUsage") // Paper's Dialog API is @ApiStatus.Experimental in 26.1.2.
public final class PermissionGUI {

    private PermissionGUI() {}

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Pagination layout shared by every multi-action list dialog.
    private static final int DIALOG_PAGE_SIZE = 12;
    private static final int DIALOG_COLUMNS = 2;

    // ------------------------------------------------------------------ Main

    public static void openMainMenu(Player player, PermissionManager manager) {
        ActionButton groupsButton = dialogButton(
                Component.text("Groups", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("Click to manage groups.", NamedTextColor.GRAY),
                p -> openGroupsMenu(p, manager, 0));

        ActionButton playersButton = dialogButton(
                Component.text("Players", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("Click to manage players.", NamedTextColor.GRAY),
                p -> openUsersMenu(p, manager, 0));

        DialogBase base = DialogBase.builder(
                        MM.deserialize("<!italic><green><bold>Permissions"))
                .body(List.of(
                        DialogBody.plainMessage(Component.text("Select a category to manage.", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false))
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
                Component.text("+ Create Group", NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("Open the new group dialog.", NamedTextColor.GRAY),
                p -> openCreateGroupDialog(p, manager)));

        ActionButton back = dialogButton(
                Component.text("← Back", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the main permissions menu.", NamedTextColor.GRAY),
                p -> openMainMenu(p, manager));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>Groups"))
                .body(List.of(DialogBody.plainMessage(pageSummary(groupNames.size(), "group", currentPage, totalPages))))
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
        Component label = Component.text(group.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD);
        List<Component> tipLines = new ArrayList<>();
        tipLines.add(Component.text("Permissions: ", NamedTextColor.GRAY)
                .append(Component.text(group.getPermissions().size(), NamedTextColor.YELLOW)));
        if (group.getParentName() != null) {
            tipLines.add(Component.text("Inherits from: ", NamedTextColor.GRAY)
                    .append(Component.text(group.getParentName(), NamedTextColor.YELLOW)));
        }
        tipLines.add(Component.text("Click to manage.", NamedTextColor.GRAY));
        return dialogButton(label, joinLines(tipLines.toArray(new Component[0])),
                p -> openGroupHub(p, manager, group.getName()));
    }

    // ----------------------------------------------------------- Create Group

    public static void openCreateGroupDialog(Player player, PermissionManager manager) {
        ActionButton create = ActionButton.builder(
                        Component.text("Create", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .decoration(TextDecoration.ITALIC, false))
                .tooltip(Component.text("Create the group with the entered name.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                handleCreateGroupSubmission(p, manager, view.getText("group_name"));
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Component.text("Cancel", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the groups list.", NamedTextColor.GRAY),
                p -> openGroupsMenu(p, manager, 0));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green><bold>Create Group"))
                .body(List.of(DialogBody.plainMessage(
                        Component.text("Enter the new group name. No spaces.", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false))))
                .inputs(List.of(
                        DialogInput.text("group_name",
                                        Component.text("Group Name", NamedTextColor.YELLOW)
                                                .decoration(TextDecoration.ITALIC, false))
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
        String trimmed = rawName == null ? "" : rawName.trim();
        if (trimmed.isEmpty() || trimmed.contains(" ")) {
            player.sendMessage(Component.text("Invalid group name. Names cannot contain spaces.", NamedTextColor.RED));
            openGroupsMenu(player, manager, 0);
            return;
        }
        String key = trimmed.toLowerCase();
        if (manager.getGroups().containsKey(key)) {
            player.sendMessage(Component.text("Group already exists.", NamedTextColor.RED));
            openGroupsMenu(player, manager, 0);
            return;
        }
        manager.getGroups().put(key, new PermissionGroup(trimmed));
        manager.saveGroups();
        player.sendMessage(Component.text("Group '" + trimmed + "' created.", NamedTextColor.GREEN));
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
                Component.text("Edit Permissions", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text(group.getPermissions().size() + " direct permission(s).", NamedTextColor.GRAY),
                p -> openGroupPerms(p, manager, name, 0)));

        buttons.add(dialogButton(
                Component.text("Manage Members", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text("Add or remove players via toggle.", NamedTextColor.GRAY),
                p -> openGroupMembersToggle(p, manager, name, 0)));

        buttons.add(dialogButton(
                Component.text("Inheritance", NamedTextColor.YELLOW, TextDecoration.BOLD),
                joinLines(
                        Component.text("Inherits from: ", NamedTextColor.GRAY)
                                .append(Component.text(group.getParentName() == null ? "none" : group.getParentName(),
                                        NamedTextColor.YELLOW)),
                        Component.text("Click to choose parent group.", NamedTextColor.GRAY)),
                p -> openGroupInheritancePicker(p, manager, name, 0)));

        if (!name.equalsIgnoreCase("default")) {
            buttons.add(dialogButton(
                    Component.text("Delete Group", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("CAUTION: This cannot be undone!", NamedTextColor.RED),
                    p -> handleDeleteGroup(p, manager, name)));
        }

        ActionButton back = dialogButton(
                Component.text("← Back to Groups", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the groups list.", NamedTextColor.GRAY),
                p -> openGroupsMenu(p, manager, 0));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>Group: " + name))
                .body(List.of(DialogBody.plainMessage(
                        Component.text("Manage settings for this group.", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false))))
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
            player.sendMessage(Component.text("Cannot delete default group.", NamedTextColor.RED));
            openGroupHub(player, manager, groupName);
            return;
        }
        manager.getGroups().remove(groupName.toLowerCase());
        manager.saveGroups();
        refreshAllInGroupForPlayers(manager, groupName);
        player.sendMessage(Component.text("Group '" + groupName + "' deleted.", NamedTextColor.GREEN));
        openGroupsMenu(player, manager, 0);
    }

    public static void openGroupPerms(Player player, PermissionManager manager, String groupName, int page) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        String name = group.getName();
        List<String> allPerms = Permissions.getAllPermissions();

        int totalPages = Math.max(1, (allPerms.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, allPerms.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            String perm = allPerms.get(i);
            boolean has = group.hasDirectPermission(perm);
            Component label = Component.text((has ? "✓ " : "✗ ") + perm,
                    has ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD);
            Component tip = Component.text(has ? "Click to revoke." : "Click to grant.",
                    NamedTextColor.GRAY);
            buttons.add(dialogButton(label, tip,
                    p -> toggleGroupPermission(p, manager, name, perm, currentPage)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target -> openGroupPerms(target, manager, name, currentPage - 1),
                target -> openGroupPerms(target, manager, name, currentPage + 1));

        ActionButton back = dialogButton(
                Component.text("← Back to Group", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the group menu.", NamedTextColor.GRAY),
                p -> openGroupHub(p, manager, name));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>Group Perms: " + name))
                .body(List.of(DialogBody.plainMessage(pageSummary(allPerms.size(), "permission", currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void toggleGroupPermission(Player player, PermissionManager manager, String groupName, String permission, int returnPage) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        if (group.hasDirectPermission(permission)) group.removePermission(permission);
        else group.addPermission(permission);
        manager.saveGroups();
        refreshAllInGroupForPlayers(manager, group.getName());
        openGroupPerms(player, manager, group.getName(), returnPage);
    }

    public static void openGroupMembersToggle(Player player, PermissionManager manager, String groupName, int page) {
        PermissionGroup group = manager.getGroups().get(groupName.toLowerCase());
        if (group == null) {
            openGroupsMenu(player, manager, 0);
            return;
        }
        String name = group.getName();
        List<UUID> all = listPlayers(manager, _ -> true);

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
            String groupsLabel = (u == null || u.getGroups().isEmpty())
                    ? "default"
                    : String.join(", ", u.getGroups());

            Component label = Component.text((isMember ? "✓ " : "✗ ") + playerName,
                    isMember ? NamedTextColor.GREEN : NamedTextColor.GRAY, TextDecoration.BOLD);
            Component tip = joinLines(
                    Component.text(target.isOnline() ? "Online" : "Offline",
                            target.isOnline() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY),
                    Component.text("Groups: ", NamedTextColor.GRAY)
                            .append(Component.text(groupsLabel, NamedTextColor.YELLOW)),
                    Component.text(isMember ? "Click to remove from this group." : "Click to add to this group.",
                            NamedTextColor.GRAY));
            buttons.add(dialogButton(label, tip,
                    p -> toggleGroupMembership(p, manager, name, uuid, currentPage)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target -> openGroupMembersToggle(target, manager, name, currentPage - 1),
                target -> openGroupMembersToggle(target, manager, name, currentPage + 1));

        ActionButton back = dialogButton(
                Component.text("← Back to Group", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the group menu.", NamedTextColor.GRAY),
                p -> openGroupHub(p, manager, name));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>Members: " + name))
                .body(List.of(DialogBody.plainMessage(pageSummary(all.size(), "player", currentPage, totalPages))))
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

            Component label = Component.text((isCurrentParent ? "✓ " : "  ") + candidate.getName(),
                    isCurrentParent ? NamedTextColor.GREEN : NamedTextColor.YELLOW, TextDecoration.BOLD);
            List<Component> tipLines = new ArrayList<>();
            tipLines.add(Component.text("Permissions: ", NamedTextColor.GRAY)
                    .append(Component.text(candidate.getPermissions().size(), NamedTextColor.YELLOW)));
            if (candidate.getParentName() != null) {
                tipLines.add(Component.text("Inherits from: ", NamedTextColor.GRAY)
                        .append(Component.text(candidate.getParentName(), NamedTextColor.YELLOW)));
            }
            tipLines.add(Component.text(isCurrentParent ? "Currently the parent." : "Click to set as parent.",
                    NamedTextColor.GRAY));
            buttons.add(dialogButton(label, joinLines(tipLines.toArray(new Component[0])),
                    p -> setGroupParent(p, manager, name, candidate.getName())));
        }

        buttons.add(dialogButton(
                Component.text("✗ None", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Clear inheritance.", NamedTextColor.GRAY),
                p -> setGroupParent(p, manager, name, null)));

        addPageNavButtons(buttons, currentPage, totalPages,
                target -> openGroupInheritancePicker(target, manager, name, currentPage - 1),
                target -> openGroupInheritancePicker(target, manager, name, currentPage + 1));

        ActionButton back = dialogButton(
                Component.text("← Back to Group", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the group menu.", NamedTextColor.GRAY),
                p -> openGroupHub(p, manager, name));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>Inheritance: " + name))
                .body(List.of(DialogBody.plainMessage(pageSummary(candidates.size(), "group", currentPage, totalPages))))
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
        refreshAllInGroupForPlayers(manager, group.getName());
        player.sendMessage(Component.text("Set inheritance for " + group.getName() + " to "
                + (parentName == null ? "none" : parentName) + ".", NamedTextColor.GREEN));
        openGroupHub(player, manager, group.getName());
    }

    // ------------------------------------------------------------ Users list

    public static void openUsersMenu(Player player, PermissionManager manager, int page) {
        List<UUID> uuids = listPlayers(manager, _ -> true);

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
            String groupsLabel = (u == null || u.getGroups().isEmpty())
                    ? "default"
                    : String.join(", ", u.getGroups());

            Component label = Component.text(playerName, NamedTextColor.YELLOW, TextDecoration.BOLD);
            Component tip = joinLines(
                    Component.text(target.isOnline() ? "Online" : "Offline",
                            target.isOnline() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY),
                    Component.text("Groups: ", NamedTextColor.GRAY)
                            .append(Component.text(groupsLabel, NamedTextColor.YELLOW)),
                    Component.text("Click to edit permissions.", NamedTextColor.GRAY));
            buttons.add(dialogButton(label, tip, p -> openUserHub(p, manager, uuid)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target -> openUsersMenu(target, manager, currentPage - 1),
                target -> openUsersMenu(target, manager, currentPage + 1));

        buttons.add(dialogButton(
                Component.text("⌕ Search Player", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text("Open the player search dialog.", NamedTextColor.GRAY),
                p -> openSearchUserDialog(p, manager)));

        ActionButton back = dialogButton(
                Component.text("← Back", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the main permissions menu.", NamedTextColor.GRAY),
                p -> openMainMenu(p, manager));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>Players"))
                .body(List.of(DialogBody.plainMessage(pageSummary(uuids.size(), "player", currentPage, totalPages))))
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
                        Component.text("Search", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .decoration(TextDecoration.ITALIC, false))
                .tooltip(Component.text("Open the matching player's hub.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                handleSearchPlayerSubmission(p, manager, view.getText("player_name"));
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Component.text("Cancel", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the players list.", NamedTextColor.GRAY),
                p -> openUsersMenu(p, manager, 0));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green><bold>Search Player"))
                .body(List.of(DialogBody.plainMessage(
                        Component.text("Enter a player name. The player must have joined before.",
                                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))))
                .inputs(List.of(
                        DialogInput.text("player_name",
                                        Component.text("Player Name", NamedTextColor.YELLOW)
                                                .decoration(TextDecoration.ITALIC, false))
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
        String trimmed = rawName == null ? "" : rawName.trim();
        if (trimmed.isEmpty()) {
            player.sendMessage(Component.text("Invalid player name.", NamedTextColor.RED));
            openUsersMenu(player, manager, 0);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(trimmed);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(Component.text("Player '" + trimmed + "' has never played before.", NamedTextColor.RED));
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
        String groupsLabel = user.getGroups().isEmpty()
                ? "default"
                : String.join(", ", user.getGroups());

        List<ActionButton> buttons = new ArrayList<>();

        buttons.add(dialogButton(
                Component.text("Edit Permissions", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text(user.getPermissions().size() + " direct permission(s).", NamedTextColor.GRAY),
                p -> openUserPerms(p, manager, targetUuid, 0)));

        buttons.add(dialogButton(
                Component.text("Edit Groups", NamedTextColor.AQUA, TextDecoration.BOLD),
                joinLines(
                        Component.text("Groups: ", NamedTextColor.GRAY)
                                .append(Component.text(groupsLabel, NamedTextColor.YELLOW)),
                        Component.text("Click to toggle group memberships.", NamedTextColor.GRAY)),
                p -> openUserGroupPicker(p, manager, targetUuid, 0)));

        buttons.add(dialogButton(
                Component.text("Reset User", NamedTextColor.RED, TextDecoration.BOLD),
                joinLines(
                        Component.text("Clear all direct permissions", NamedTextColor.GRAY),
                        Component.text("and reset to the default group.", NamedTextColor.GRAY)),
                p -> resetUser(p, manager, targetUuid)));

        ActionButton back = dialogButton(
                Component.text("← Back to Players", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the players list.", NamedTextColor.GRAY),
                p -> openUsersMenu(p, manager, 0));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>User: " + name))
                .body(List.of(DialogBody.plainMessage(
                        Component.text("Manage settings for this player.", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false))))
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
        player.sendMessage(Component.text("Reset " + (displayName == null ? targetUuid.toString() : displayName)
                + " to defaults.", NamedTextColor.GREEN));
        openUserHub(player, manager, targetUuid);
    }

    public static void openUserPerms(Player player, PermissionManager manager, UUID targetUuid, int page) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        UserPermissions user = manager.getUserPermissions(targetUuid);
        String name = target.getName() == null ? targetUuid.toString() : target.getName();
        List<String> allPerms = Permissions.getAllPermissions();

        int totalPages = Math.max(1, (allPerms.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, allPerms.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            String perm = allPerms.get(i);
            boolean has = user.hasDirectPermission(perm);
            Component label = Component.text((has ? "✓ " : "✗ ") + perm,
                    has ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD);
            Component tip = Component.text(has ? "Click to revoke." : "Click to grant.",
                    NamedTextColor.GRAY);
            buttons.add(dialogButton(label, tip,
                    p -> toggleUserPermission(p, manager, targetUuid, perm, currentPage)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target2 -> openUserPerms(target2, manager, targetUuid, currentPage - 1),
                target2 -> openUserPerms(target2, manager, targetUuid, currentPage + 1));

        ActionButton back = dialogButton(
                Component.text("← Back to User", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the user menu.", NamedTextColor.GRAY),
                p -> openUserHub(p, manager, targetUuid));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>User Perms: " + name))
                .body(List.of(DialogBody.plainMessage(pageSummary(allPerms.size(), "permission", currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void toggleUserPermission(Player player, PermissionManager manager, UUID targetUuid, String permission, int returnPage) {
        UserPermissions u = manager.getUserPermissions(targetUuid);
        if (u.hasDirectPermission(permission)) u.removePermission(permission);
        else u.addPermission(permission);
        manager.saveUsers();
        refreshOnlinePlayer(manager, targetUuid);
        openUserPerms(player, manager, targetUuid, returnPage);
    }

    public static void openUserGroupPicker(Player player, PermissionManager manager, UUID targetUuid, int page) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        UserPermissions user = manager.getUserPermissions(targetUuid);
        String name = target.getName() == null ? targetUuid.toString() : target.getName();

        List<String> groupNames = new ArrayList<>(manager.getGroups().keySet());
        groupNames.sort(String.CASE_INSENSITIVE_ORDER);

        int totalPages = Math.max(1, (groupNames.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, groupNames.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            PermissionGroup group = manager.getGroups().get(groupNames.get(i));
            boolean isMember = user.hasGroup(group.getName());

            Component label = Component.text((isMember ? "✓ " : "✗ ") + group.getName(),
                    isMember ? NamedTextColor.GREEN : NamedTextColor.YELLOW, TextDecoration.BOLD);
            List<Component> tipLines = new ArrayList<>();
            tipLines.add(Component.text("Permissions: ", NamedTextColor.GRAY)
                    .append(Component.text(group.getPermissions().size(), NamedTextColor.YELLOW)));
            if (group.getParentName() != null) {
                tipLines.add(Component.text("Inherits from: ", NamedTextColor.GRAY)
                        .append(Component.text(group.getParentName(), NamedTextColor.YELLOW)));
            }
            tipLines.add(Component.text(isMember ? "Click to remove." : "Click to add.",
                    NamedTextColor.GRAY));
            buttons.add(dialogButton(label, joinLines(tipLines.toArray(new Component[0])),
                    p -> toggleUserGroup(p, manager, targetUuid, group.getName(), currentPage)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target2 -> openUserGroupPicker(target2, manager, targetUuid, currentPage - 1),
                target2 -> openUserGroupPicker(target2, manager, targetUuid, currentPage + 1));

        ActionButton back = dialogButton(
                Component.text("← Back to User", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the user menu.", NamedTextColor.GRAY),
                p -> openUserHub(p, manager, targetUuid));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>Edit Groups: " + name))
                .body(List.of(DialogBody.plainMessage(pageSummary(groupNames.size(), "group", currentPage, totalPages))))
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
                                action.accept(p);
                            }
                        },
                        unlimitedClicks()))
                .build();
    }

    private static Component joinLines(Component... lines) {
        Component result = Component.empty();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) result = result.append(Component.newline());
            result = result.append(lines[i].decoration(TextDecoration.ITALIC, false));
        }
        return result;
    }

    private static Component pageSummary(int total, String noun, int currentPage, int totalPages) {
        String pluralized = total == 1 ? noun : noun + "s";
        return Component.text(total + " " + pluralized + " — Page " + (currentPage + 1) + " of " + totalPages,
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static void addPageNavButtons(List<ActionButton> buttons, int currentPage, int totalPages,
                                          Consumer<Player> prevAction, Consumer<Player> nextAction) {
        if (currentPage > 0) {
            buttons.add(dialogButton(
                    Component.text("◀ Prev Page", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("Page " + currentPage + " of " + totalPages, NamedTextColor.GRAY),
                    prevAction));
        }
        if (currentPage + 1 < totalPages) {
            buttons.add(dialogButton(
                    Component.text("Next Page ▶", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("Page " + (currentPage + 2) + " of " + totalPages, NamedTextColor.GRAY),
                    nextAction));
        }
    }

    private static void refreshOnlinePlayer(PermissionManager manager, UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) manager.refreshPlayer(p);
    }

    private static void refreshAllInGroupForPlayers(PermissionManager manager, String groupName) {
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
}