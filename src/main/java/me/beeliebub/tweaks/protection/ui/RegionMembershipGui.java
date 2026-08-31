package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Owns the members and managers screens while {@link RegionGUI} remains the public hub. */
@SuppressWarnings("UnstableApiUsage")
final class RegionMembershipGui {

    private static final int COLUMNS = 2;

    private RegionMembershipGui() {}

    static void openMembersMenu(Player player, Region region, ProtectionManager pm,
                                PermissionManager permissions) {
        Region fresh = RegionGUI.refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Messages.PROTECTION.text(Text.REGION_GONE));
            return;
        }
        List<UUID> members = new ArrayList<>(fresh.members());
        members.sort(java.util.Comparator.comparing(RegionGUI::lookupName, String.CASE_INSENSITIVE_ORDER));
        List<String> groups = new ArrayList<>(fresh.memberGroups());
        int total = members.size() + groups.size();

        List<ActionButton> buttons = new ArrayList<>();
        for (UUID target : members) {
                buttons.add(RegionGUI.dialogButton(
                        Messages.PROTECTION.text(Text.GUI_MEMBER_REMOVE, RegionGUI.lookupName(target)),
                        Messages.PROTECTION.text(Text.GUI_MEMBER_REMOVE_TIP),
                        p -> removeMember(p, fresh, pm, permissions, target)));
        }
        for (String group : groups) {
            buttons.add(RegionGUI.dialogButton(
                    Messages.PROTECTION.text(Text.GUI_GROUP_REMOVE, group),
                    Messages.PROTECTION.text(Text.GUI_GROUP_REMOVE_TIP),
                    p -> removeMemberGroup(p, fresh, pm, permissions, group)));
        }
        buttons.add(RegionGUI.dialogButton(Messages.PROTECTION.text(Text.GUI_ADD_MEMBER),
                Messages.PROTECTION.text(Text.GUI_ADD_MEMBER_TIP),
                p -> openAddMemberDialog(p, fresh, pm, permissions)));
        ActionButton back = RegionGUI.dialogButton(Messages.PROTECTION.text(Text.GUI_BACK_REGION),
                Messages.PROTECTION.text(Text.GUI_BACK_REGION_TIP),
                p -> RegionGUI.openRegionHub(p, fresh, pm, permissions));
        DialogBase base = DialogBase.builder(Messages.PROTECTION.title(Text.GUI_MEMBERS_TITLE, fresh.id()))
                .body(List.of(io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(
                        RegionGUI.listSummary(total, Text.GUI_WORD_MEMBER, Text.GUI_WORD_MEMBERS))))
                .build();
        Dialog dialog = Dialog.create(b -> b.empty().base(base)
                .type(DialogType.multiAction(buttons).columns(COLUMNS).exitAction(back).build()));
        player.showDialog(dialog);
    }

    private static void removeMember(Player player, Region region, ProtectionManager pm,
                                     PermissionManager permissions, UUID target) {
        World world = RegionGUI.worldOf(region);
        if (!pm.removeMember(world, region.id(), target)) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MEMBER_NOT_PRESENT));
        } else {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MEMBER_REMOVED,
                    RegionGUI.lookupName(target), region.id()));
        }
        openMembersMenu(player, region, pm, permissions);
    }

    private static void removeMemberGroup(Player player, Region region, ProtectionManager pm,
                                          PermissionManager permissions, String group) {
        if (!pm.removeMemberGroup(RegionGUI.worldOf(region), region.id(), group)) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MEMBER_GROUP_NOT_PRESENT, group));
        } else {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MEMBER_GROUP_REMOVED, group, region.id()));
        }
        openMembersMenu(player, region, pm, permissions);
    }

    private static void openAddMemberDialog(Player player, Region region, ProtectionManager pm,
                                            PermissionManager permissions) {
        RegionGuiSupport.openTextInputDialog(player,
                Messages.PROTECTION.title(Text.GUI_ADD_MEMBER_TITLE),
                Messages.PROTECTION.text(Text.GUI_ADD_MEMBER_PROMPT, region.id()),
                "player_name", Messages.PROTECTION.text(Text.GUI_INPUT_PLAYER_NAME), 16,
                Messages.PROTECTION.text(Text.GUI_APPLY_NAME_TIP),
                (p, name) -> addMemberSubmission(p, region, pm, permissions, name),
                p -> openMembersMenu(p, region, pm, permissions));
    }

    static boolean routeAddMemberInput(Player player, Region region, ProtectionManager pm, String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("group:")) return false;
        String group = trimmed.substring(6).trim();
        if (group.isEmpty()) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_GROUP_NAME_REQUIRED));
            return false;
        }
        if (!pm.addMemberGroup(RegionGUI.worldOf(region), region.id(), group)) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MEMBER_GROUP_EXISTS, group));
            return false;
        }
        if ("default".equalsIgnoreCase(group)) {
            player.sendMessage(Messages.PROTECTION.text(Text.MEMBER_GROUP_DEFAULT_WARNING, region.id()));
        }
        player.sendMessage(Messages.PROTECTION.text(Text.GUI_MEMBER_GROUP_ADDED, group, region.id()));
        return true;
    }

    private static void addMemberSubmission(Player player, Region region, ProtectionManager pm,
                                            PermissionManager permissions, String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("group:")) {
            routeAddMemberInput(player, region, pm, trimmed);
            openMembersMenu(player, region, pm, permissions);
            return;
        }
        OfflinePlayer target = RegionGUI.validatePlayerName(player, raw);
        if (target == null) { openMembersMenu(player, region, pm, permissions); return; }
        if (region.owner().equals(target.getUniqueId())) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_OWNER_MEMBER));
        } else if (!pm.addMember(RegionGUI.worldOf(region), region.id(), target.getUniqueId())) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MEMBER_EXISTS));
        } else {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MEMBER_ADDED, target.getName(), region.id()));
        }
        openMembersMenu(player, region, pm, permissions);
    }

    static void openManagersMenu(Player player, Region region, ProtectionManager pm,
                                 PermissionManager permissions) {
        if (!RegionAuth.isOwnerOrAdmin(player, region, pm)) {
            player.sendMessage(Messages.PROTECTION.text(Text.MANAGER_EDIT_AUTH));
            return;
        }
        Region fresh = RegionGUI.refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Messages.PROTECTION.text(Text.REGION_GONE));
            return;
        }
        List<UUID> managers = new ArrayList<>(fresh.managers());
        managers.sort(java.util.Comparator.comparing(RegionGUI::lookupName, String.CASE_INSENSITIVE_ORDER));
        List<String> groups = new ArrayList<>(fresh.managerGroups());
        int total = managers.size() + groups.size();
        List<ActionButton> buttons = new ArrayList<>();
        for (UUID target : managers) {
            buttons.add(RegionGUI.dialogButton(
                    Messages.PROTECTION.text(Text.GUI_MEMBER_REMOVE, RegionGUI.lookupName(target)),
                    Messages.PROTECTION.text(Text.GUI_MANAGER_REMOVE_TIP),
                    p -> handleRemoveManager(p, fresh, pm, permissions, target)));
        }
        for (String group : groups) {
            buttons.add(RegionGUI.dialogButton(Messages.PROTECTION.text(Text.GUI_GROUP_REMOVE, group),
                    Messages.PROTECTION.text(Text.GUI_MANAGER_GROUP_REMOVE_TIP),
                    p -> handleRemoveManagerGroup(p, fresh, pm, permissions, group)));
        }
        buttons.add(RegionGUI.dialogButton(Messages.PROTECTION.text(Text.GUI_ADD_MANAGER),
                Messages.PROTECTION.text(Text.GUI_ADD_MEMBER_TIP),
                p -> openAddManagerDialog(p, fresh, pm, permissions)));
        ActionButton back = RegionGUI.dialogButton(Messages.PROTECTION.text(Text.GUI_BACK_REGION),
                Messages.PROTECTION.text(Text.GUI_BACK_REGION_TIP),
                p -> RegionGUI.openRegionHub(p, fresh, pm, permissions));
        DialogBase base = DialogBase.builder(Messages.PROTECTION.title(Text.GUI_MANAGERS_TITLE, fresh.id()))
                .body(List.of(io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(
                        RegionGUI.listSummary(total, Text.GUI_WORD_MANAGER, Text.GUI_WORD_MANAGERS))))
                .build();
        Dialog dialog = Dialog.create(b -> b.empty().base(base)
                .type(DialogType.multiAction(buttons).columns(COLUMNS).exitAction(back).build()));
        player.showDialog(dialog);
    }

    /** Compatibility overload for callers that used the former page argument. */
    static void openManagersMenu(Player player, Region region, ProtectionManager pm,
                                 PermissionManager permissions, int ignoredPage) {
        openManagersMenu(player, region, pm, permissions);
    }

    private static void handleRemoveManager(Player player, Region region, ProtectionManager pm,
                                            PermissionManager permissions, UUID target) {
        if (!RegionAuth.isOwnerOrAdmin(player, region, pm)) {
            player.sendMessage(Messages.PROTECTION.text(Text.MANAGER_EDIT_AUTH));
            return;
        }
        if (!pm.removeManager(RegionGUI.worldOf(region), region.id(), target)) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MANAGER_NOT_PRESENT));
        } else {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MANAGER_DEMOTED,
                    RegionGUI.lookupName(target), region.id()));
        }
        openManagersMenu(player, region, pm, permissions);
    }

    static void handleRemoveManager(Player player, Region region, ProtectionManager pm,
                                    PermissionManager permissions, UUID target, int ignoredPage) {
        handleRemoveManager(player, region, pm, permissions, target);
    }

    private static void handleRemoveManagerGroup(Player player, Region region, ProtectionManager pm,
                                                 PermissionManager permissions, String group) {
        if (!RegionAuth.isOwnerOrAdmin(player, region, pm)) {
            player.sendMessage(Messages.PROTECTION.text(Text.MANAGER_EDIT_AUTH));
            return;
        }
        if (!pm.removeManagerGroup(RegionGUI.worldOf(region), region.id(), group)) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MANAGER_GROUP_NOT_PRESENT, group));
        } else {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MANAGER_GROUP_REMOVED, group, region.id()));
        }
        openManagersMenu(player, region, pm, permissions);
    }

    static void handleRemoveManagerGroup(Player player, Region region, ProtectionManager pm,
                                         PermissionManager permissions, String group, int ignoredPage) {
        handleRemoveManagerGroup(player, region, pm, permissions, group);
    }

    static void openAddManagerDialog(Player player, Region region, ProtectionManager pm,
                                     PermissionManager permissions) {
        if (!RegionAuth.isOwnerOrAdmin(player, region, pm)) {
            player.sendMessage(Messages.PROTECTION.text(Text.MANAGER_EDIT_AUTH));
            return;
        }
        RegionGuiSupport.openTextInputDialog(player,
                Messages.PROTECTION.title(Text.GUI_ADD_MANAGER_TITLE),
                Messages.PROTECTION.text(Text.GUI_ADD_MANAGER_PROMPT, region.id()),
                "player_name", Messages.PROTECTION.text(Text.GUI_INPUT_PLAYER_NAME), 16,
                Messages.PROTECTION.text(Text.GUI_APPLY_NAME_TIP),
                (p, name) -> handleAddManagerSubmission(p, region, pm, permissions, name),
                p -> openManagersMenu(p, region, pm, permissions));
    }

    static boolean routeAddManagerInput(Player player, Region region, ProtectionManager pm, String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("group:")) return false;
        String group = trimmed.substring(6).trim();
        if (group.isEmpty()) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_GROUP_NAME_REQUIRED));
            return false;
        }
        if (!pm.addManagerGroup(RegionGUI.worldOf(region), region.id(), group)) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MANAGER_GROUP_EXISTS, group));
            return false;
        }
        if ("default".equalsIgnoreCase(group)) {
            player.sendMessage(Messages.PROTECTION.text(Text.MANAGER_GROUP_DEFAULT_WARNING, region.id()));
        }
        player.sendMessage(Messages.PROTECTION.text(Text.GUI_MANAGER_GROUP_ADDED, group, region.id()));
        return true;
    }

    static void handleAddManagerSubmission(Player player, Region region, ProtectionManager pm,
                                           PermissionManager permissions, String raw) {
        if (!RegionAuth.isOwnerOrAdmin(player, region, pm)) {
            player.sendMessage(Messages.PROTECTION.text(Text.MANAGER_EDIT_AUTH));
            return;
        }
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("group:")) {
            routeAddManagerInput(player, region, pm, trimmed);
            openManagersMenu(player, region, pm, permissions);
            return;
        }
        OfflinePlayer target = RegionGUI.validatePlayerName(player, raw);
        if (target == null) { openManagersMenu(player, region, pm, permissions); return; }
        if (region.owner().equals(target.getUniqueId())) {
            player.sendMessage(Messages.PROTECTION.text(Text.OWNER_MANAGER));
        } else if (!pm.addManager(RegionGUI.worldOf(region), region.id(), target.getUniqueId())) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MANAGER_EXISTS));
        } else {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MANAGER_PROMOTED, target.getName(), region.id()));
        }
        openManagersMenu(player, region, pm, permissions);
    }
}
