package me.beeliebub.tweaks.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;

/** Player-facing command and dialog copy for permission administration. */
public final class PermissionMessages {

    PermissionMessages() {}

    /** Logical page nouns used by the permission-administration dialog copy. */
    public enum PageNoun { GROUP, PERMISSION, PLAYER }

    // ---------------------------------------------------------------- Commands

    public Component guiRequiresPlayer() { return red("Only players can use the GUI."); }
    public Component groupUsage() { return red("Usage: /tprm group <name> <create|delete|addperm|delperm|inherited-from>"); }
    public Component groupAlreadyExists() { return red("Group already exists."); }
    public Component groupCreated(String name) { return green("Group '" + name + "' created."); }
    public Component defaultGroupCannotBeDeleted() { return red("Cannot delete default group."); }
    public Component groupDeleted(String name) { return green("Group '" + name + "' deleted."); }
    public Component groupNotFound() { return red("Group not found."); }
    public Component groupAddPermissionUsage() { return red("Usage: /tprm group <name> addperm <permission>"); }
    public Component groupPermissionAdded(String name) { return green("Added permission to group '" + name + "'."); }
    public Component groupRemovePermissionUsage() { return red("Usage: /tprm group <name> delperm <permission>"); }
    public Component groupPermissionRemoved(String name) { return green("Removed permission from group '" + name + "'."); }
    public Component groupInheritanceUsage() { return red("Usage: /tprm group <name> inherited-from <parent|none>"); }
    public Component parentGroupNotFound() { return red("Parent group not found."); }
    public Component groupInheritanceSet(String name, String parent) {
        return green("Set inheritance for group '" + name + "' to " + (parent == null ? "none" : parent) + ".");
    }
    public Component unknownAction() { return red("Unknown action."); }
    public Component userUsage() { return red("Usage: /tprm user <player> <addperm|delperm|setgroup>"); }
    public Component playerNeverPlayed(String name) { return red("Player '" + name + "' has never played before."); }
    public Component userAddPermissionUsage() { return red("Usage: /tprm user <player> addperm <permission>"); }
    public Component userPermissionAdded(String name) { return green("Added permission to user " + name + "."); }
    public Component userRemovePermissionUsage() { return red("Usage: /tprm user <player> delperm <permission>"); }
    public Component userPermissionRemoved(String name) { return green("Removed permission from user " + name + "."); }
    public Component userSetGroupUsage() { return red("Usage: /tprm user <player> setgroup <group|none>"); }
    public Component userGroupSet(String name, String group) {
        return green("Set group for user " + name + " to " + (group == null ? "none" : group) + ".");
    }
    public List<Component> commandUsage() {
        return List.of(
                Component.text("=== Permission Commands ===", NamedTextColor.GOLD),
                Component.text("/tprm gui", NamedTextColor.YELLOW),
                Component.text("/tprm group <name> create|delete|addperm|delperm|inherited-from", NamedTextColor.YELLOW),
                Component.text("/tprm user <player> addperm|delperm|setgroup", NamedTextColor.YELLOW));
    }

    // ------------------------------------------------------------- Dialog copy

    public Component groupsLabel() { return bold("Groups", NamedTextColor.GREEN); }
    public Component groupsTooltip() { return gray("Click to manage groups."); }
    public Component playersLabel() { return bold("Players", NamedTextColor.GREEN); }
    public Component playersTooltip() { return gray("Click to manage players."); }
    public Component mainTitle() { return title("<!italic><green><bold>Permissions"); }
    public Component mainBody() { return plainGray("Select a category to manage."); }

    public Component createGroupLabel() { return bold("+ Create Group", NamedTextColor.YELLOW); }
    public Component createGroupTooltip() { return gray("Open the new group dialog."); }
    public Component backLabel() { return bold("← Back", NamedTextColor.RED); }
    public Component backToMainTooltip() { return gray("Return to the main permissions menu."); }
    public Component groupsTitle() { return title("<!italic><green>Groups"); }

    public Component groupListLabel(String name) { return bold(name, NamedTextColor.YELLOW); }
    public Component groupListTooltip(int permissionCount, String parentName) {
        Component permissions = gray("Permissions: ").append(Component.text(permissionCount, NamedTextColor.YELLOW));
        Component manage = gray("Click to manage.");
        return parentName == null ? lines(permissions, manage)
                : lines(permissions, gray("Inherits from: ").append(Component.text(parentName, NamedTextColor.YELLOW)), manage);
    }

    public Component createLabel() { return bold("Create", NamedTextColor.GREEN); }
    public Component createTooltip() { return plainGray("Create the group with the entered name."); }
    public Component cancelLabel() { return bold("Cancel", NamedTextColor.RED); }
    public Component cancelGroupsTooltip() { return gray("Return to the groups list."); }
    public Component createGroupTitle() { return title("<!italic><green><bold>Create Group"); }
    public Component createGroupBody() { return plainGray("Enter the new group name. No spaces."); }
    public Component groupNameInput() { return plainYellow("Group Name"); }
    public Component invalidGroupName() { return red("Invalid group name. Names cannot contain spaces."); }

    public Component editPermissionsLabel() { return bold("Edit Permissions", NamedTextColor.GREEN); }
    public Component directPermissionsTooltip(int count) { return gray(count + " direct permission(s)."); }
    public Component manageMembersLabel() { return bold("Manage Members", NamedTextColor.AQUA); }
    public Component manageMembersTooltip() { return gray("Add or remove players via toggle."); }
    public Component inheritanceLabel() { return bold("Inheritance", NamedTextColor.YELLOW); }
    public Component inheritanceTooltip(String parentName) {
        return lines(gray("Inherits from: ").append(Component.text(parentName == null ? "none" : parentName, NamedTextColor.YELLOW)),
                gray("Click to choose parent group."));
    }
    public Component deleteGroupLabel() { return bold("Delete Group", NamedTextColor.RED); }
    public Component deleteGroupWarning() { return red("CAUTION: This cannot be undone!"); }
    public Component backToGroupsLabel() { return bold("← Back to Groups", NamedTextColor.RED); }
    public Component groupsListTooltip() { return gray("Return to the groups list."); }
    public Component groupTitle(String name) { return title("<!italic><green>Group: " + name); }
    public Component groupBody() { return plainGray("Manage settings for this group."); }

    public Component categoryLabel(String category) { return bold(category, NamedTextColor.YELLOW); }
    public Component categoryTooltip(int total, long granted) {
        return lines(gray(total + " permission(s) — " + granted + " granted."), gray("Click to view and toggle."));
    }
    public Component backToGroupLabel() { return bold("← Back to Group", NamedTextColor.RED); }
    public Component groupMenuTooltip() { return gray("Return to the group menu."); }
    public Component permissionsTitle(String categoryOrScope, String name) {
        return title("<!italic><green>" + categoryOrScope + ": " + name);
    }
    public Component groupPermissionsTitle(String name) { return permissionsTitle("Permissions", name); }
    public Component userPermissionsTitle(String name) { return permissionsTitle("Permissions", name); }
    public Component selectCategoryBody() { return plainGray("Select a category to edit."); }

    public Component permissionToggleLabel(String permission, boolean granted) {
        return bold((granted ? "✓ " : "✗ ") + permission, granted ? NamedTextColor.GREEN : NamedTextColor.RED);
    }
    public Component permissionToggleTooltip(boolean granted) { return gray(granted ? "Click to revoke." : "Click to grant."); }
    public Component backToCategoriesLabel() { return bold("← Back to Categories", NamedTextColor.RED); }
    public Component categoriesTooltip() { return gray("Return to the category list."); }

    public Component playerMembershipLabel(String name, boolean member, NamedTextColor unselectedColor) {
        return bold((member ? "✓ " : "✗ ") + name, member ? NamedTextColor.GREEN : unselectedColor);
    }
    public Component playerDetailsTooltip(boolean online, String groups, boolean isMember) {
        return lines(Component.text(online ? "Online" : "Offline", online ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY),
                gray("Groups: ").append(Component.text(groups, NamedTextColor.YELLOW)),
                gray(isMember ? "Click to remove from this group." : "Click to add to this group."));
    }
    public Component membersTitle(String name) { return title("<!italic><green>Members: " + name); }

    public Component parentCandidateLabel(String name, boolean selected) {
        return bold((selected ? "✓ " : "  ") + name, selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
    }
    public Component parentCandidateTooltip(int permissionCount, String parentName, boolean selected) {
        Component permissions = gray("Permissions: ").append(Component.text(permissionCount, NamedTextColor.YELLOW));
        Component action = gray(selected ? "Currently the parent." : "Click to set as parent.");
        return parentName == null ? lines(permissions, action)
                : lines(permissions, gray("Inherits from: ").append(Component.text(parentName, NamedTextColor.YELLOW)), action);
    }
    public Component noneLabel() { return bold("✗ None", NamedTextColor.RED); }
    public Component clearInheritanceTooltip() { return gray("Clear inheritance."); }
    public Component inheritanceTitle(String name) { return title("<!italic><green>Inheritance: " + name); }
    public Component groupInheritanceSetFromGui(String groupName, String parentName) {
        return green("Set inheritance for " + groupName + " to " + (parentName == null ? "none" : parentName) + ".");
    }

    public Component playerListLabel(String name) { return bold(name, NamedTextColor.YELLOW); }
    public Component playerListTooltip(boolean online, String groups) {
        return lines(Component.text(online ? "Online" : "Offline", online ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY),
                gray("Groups: ").append(Component.text(groups, NamedTextColor.YELLOW)), gray("Click to edit permissions."));
    }
    public Component searchPlayerLabel() { return bold("⌕ Search Player", NamedTextColor.AQUA); }
    public Component searchPlayerTooltip() { return gray("Open the player search dialog."); }
    public Component playersTitle() { return title("<!italic><green>Players"); }

    public Component searchLabel() { return bold("Search", NamedTextColor.GREEN); }
    public Component searchTooltip() { return plainGray("Open the matching player's hub."); }
    public Component cancelPlayersTooltip() { return gray("Return to the players list."); }
    public Component searchPlayerTitle() { return title("<!italic><green><bold>Search Player"); }
    public Component searchPlayerBody() { return plainGray("Enter a player name. The player must have joined before."); }
    public Component playerNameInput() { return plainYellow("Player Name"); }
    public Component invalidPlayerName() { return red("Invalid player name."); }

    public Component editGroupsLabel() { return bold("Edit Groups", NamedTextColor.AQUA); }
    public Component editGroupsTooltip(String groups) {
        return lines(gray("Groups: ").append(Component.text(groups, NamedTextColor.YELLOW)),
                gray("Click to toggle group memberships."));
    }
    public Component resetUserLabel() { return bold("Reset User", NamedTextColor.RED); }
    public Component resetUserTooltip() { return lines(gray("Clear all direct permissions"), gray("and reset to the default group.")); }
    public Component backToPlayersLabel() { return bold("← Back to Players", NamedTextColor.RED); }
    public Component playersListTooltip() { return gray("Return to the players list."); }
    public Component backToUserLabel() { return bold("← Back to User", NamedTextColor.RED); }
    public Component userTitle(String name) { return title("<!italic><green>User: " + name); }
    public Component userBody() { return plainGray("Manage settings for this player."); }
    public Component userReset(String name) { return green("Reset " + name + " to defaults."); }
    public Component userMenuTooltip() { return gray("Return to the user menu."); }

    public Component userGroupTooltip(int permissionCount, String parentName, boolean member) {
        Component permissions = gray("Permissions: ").append(Component.text(permissionCount, NamedTextColor.YELLOW));
        Component action = gray(member ? "Click to remove." : "Click to add.");
        return parentName == null ? lines(permissions, action)
                : lines(permissions, gray("Inherits from: ").append(Component.text(parentName, NamedTextColor.YELLOW)), action);
    }
    public Component editGroupsTitle(String name) { return title("<!italic><green>Edit Groups: " + name); }

    public Component pageSummary(int total, PageNoun noun, int currentPage, int totalPages) {
        String singular = switch (noun) {
            case GROUP -> "group";
            case PERMISSION -> "permission";
            case PLAYER -> "player";
        };
        String pluralized = total == 1 ? singular : singular + "s";
        return plainGray(total + " " + pluralized + " — Page " + (currentPage + 1) + " of " + totalPages);
    }
    public Component previousPageLabel() { return bold("◀ Prev Page", NamedTextColor.GREEN); }
    public Component nextPageLabel() { return bold("Next Page ▶", NamedTextColor.GREEN); }
    public Component pageTooltip(int page, int totalPages) { return gray("Page " + page + " of " + totalPages); }

    private static Component title(String miniMessage) { return Messages.MM.deserialize(miniMessage); }
    private static Component plainGray(String text) { return gray(text).decoration(TextDecoration.ITALIC, false); }
    private static Component plainYellow(String text) { return Component.text(text, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false); }
    private static Component bold(String text, NamedTextColor color) { return Component.text(text, color, TextDecoration.BOLD); }
    private static Component red(String text) { return Component.text(text, NamedTextColor.RED); }
    private static Component green(String text) { return Component.text(text, NamedTextColor.GREEN); }
    private static Component gray(String text) { return Component.text(text, NamedTextColor.GRAY); }
    private static Component lines(Component... lines) {
        Component result = Component.empty();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) result = result.append(Component.newline());
            result = result.append(lines[i].decoration(TextDecoration.ITALIC, false));
        }
        return result;
    }
}
