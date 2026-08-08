package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.protection.region.FlagTarget;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

// /region gui dashboard. Mirrors the Paper Dialog idiom used by PermissionGUI:
// every screen is a multi-action or confirmation dialog, every navigation is a
// DialogAction.customClick callback that re-opens the next screen, and toggle/
// mutate buttons re-open the same screen with fresh state.
//
// This file contains the top-level hub (openRegionHub) plus stubs for the five
// submenu entry points filled in by sibling beads (rgtl, bz8e, khlz). Keeping
// the stubs as package-private methods in this same class lets the sibling
// beads layer behavior on without re-wiring the hub.
@SuppressWarnings("UnstableApiUsage") // Paper Dialog API is @ApiStatus.Experimental in 26.2.
public final class RegionGUI {

    private RegionGUI() {}

    private static final int DIALOG_COLUMNS = 1;
    // List-screen layout. Two columns at twelve entries per page matches the
    // PermissionGUI cadence — large enough to scan an active region's roster
    // without scrolling, small enough that pagination buttons stay above the
    // fold for tall dialogs (header + 12 entries + 2 nav buttons + add + back).
    private static final int LIST_COLUMNS = 2;
    private static final int LIST_PAGE_SIZE = 12;

    // Open the region management dashboard for `region`. The caller is
    // responsible for the authorization check (isOwnerManagerOrAdmin); this
    // method assumes the player is allowed to see the hub.
    public static void openRegionHub(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager) {
        List<ActionButton> buttons = new ArrayList<>();

        buttons.add(dialogButton(
                Messages.PROTECTION.text(Text.GUI_EDIT_FLAGS),
                Messages.PROTECTION.text(Text.GUI_EDIT_FLAGS_TIP),
                p -> openFlagsMenu(p, region, pm, permissionManager)));

        buttons.add(dialogButton(
                Messages.PROTECTION.text(Text.GUI_EDIT_MEMBERS),
                Messages.PROTECTION.text(Text.GUI_EDIT_MEMBERS_TIP),
                p -> openMembersMenu(p, region, pm, permissionManager)));

        // Manager-list editing, sub-region nesting, and unclaim are owner-only on purpose:
        // delegating a manager the ability to edit the manager set (or unclaim/reparent) would
        // let a promoted role entrench a colluding co-manager or destroy the very region they
        // were promoted to maintain. Hide all three buttons from non-owners so the UI matches
        // the underlying auth model — mirrored by the same RegionAuth.isOwnerOrAdmin guard
        // inside openManagersMenu/handleAddManagerSubmission/handleRemoveManager(Group) below,
        // for defense in depth against a stale dialog reference.
        if (RegionAuth.isOwnerOrAdmin(player, region, pm)) {
            buttons.add(dialogButton(
                    Messages.PROTECTION.text(Text.GUI_EDIT_MANAGERS),
                    Messages.PROTECTION.text(Text.GUI_EDIT_MANAGERS_TIP),
                    p -> openManagersMenu(p, region, pm, permissionManager)));

            buttons.add(dialogButton(
                    Messages.PROTECTION.text(Text.GUI_SUBREGIONS),
                    Messages.PROTECTION.text(Text.GUI_SUBREGIONS_TIP),
                    p -> openSubRegionsMenu(p, region, pm, permissionManager)));

            buttons.add(dialogButton(
                    Messages.PROTECTION.text(Text.GUI_UNCLAIM),
                    Messages.PROTECTION.text(Text.GUI_UNCLAIM_TIP),
                    p -> openUnclaimConfirmation(p, region, pm, permissionManager)));
        }

        DialogBase base = DialogBase.builder(Messages.PROTECTION.title(Text.GUI_REGION_TITLE, region.id()))
                .body(List.of(DialogBody.plainMessage(
                        Messages.PROTECTION.text(Text.GUI_REGION_BODY)
                                .decoration(TextDecoration.ITALIC, false))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .build()));

        player.showDialog(dialog);
    }

    // ------------------------------------------------------------------
    // Submenu stubs — implemented by Tweaks-rgtl / -bz8e / -khlz.
    // ------------------------------------------------------------------

    // ------------------------------------------------------------ Flags menu

    static void openFlagsMenu(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager) {
        openFlagsMenu(player, region, pm, permissionManager, 0);
    }

    private static void openFlagsMenu(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager, int page) {
        Region fresh = refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Messages.PROTECTION.text(Text.REGION_GONE));
            return;
        }
        RegionFlag[] all = RegionFlag.values();
        int totalPages = Math.max(1, (all.length + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, all.length);

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            RegionFlag flag = all[i];
            buttons.add(flagListEntryButton(fresh, pm, permissionManager, flag));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                p -> openFlagsMenu(p, fresh, pm, permissionManager, currentPage - 1),
                p -> openFlagsMenu(p, fresh, pm, permissionManager, currentPage + 1));

        ActionButton back = dialogButton(
                Messages.PROTECTION.text(Text.GUI_BACK_REGION),
                Messages.PROTECTION.text(Text.GUI_BACK_REGION_TIP),
                p -> openRegionHub(p, fresh, pm, permissionManager));

        DialogBase base = DialogBase.builder(Messages.PROTECTION.title(Text.GUI_FLAGS_TITLE, fresh.id()))
                .body(List.of(DialogBody.plainMessage(
                        pageSummary(all.length, Text.GUI_WORD_FLAG, Text.GUI_WORD_FLAGS, currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(LIST_COLUMNS)
                        .exitAction(back)
                        .build()));
        player.showDialog(dialog);
    }

    // Per-flag entry on the flag list. Annotates the label with the current
    // rule/list size so the player can see at a glance which flags are
    // configured. Click routes to the appropriate detail screen based on
    // whether the flag is boolean / material-list / entity-list.
    private static ActionButton flagListEntryButton(Region region, ProtectionManager pm, PermissionManager permissionManager, RegionFlag flag) {
        int count;
        Text singular;
        Text plural;
        if (flag.isMaterialFlag()) {
            count = region.materialsFor(flag).size();
            singular = Text.GUI_WORD_MATERIAL;
            plural = Text.GUI_WORD_MATERIALS;
        } else if (flag.isEntityFlag()) {
            count = region.entitiesFor(flag).size();
            singular = Text.GUI_WORD_ENTITY_TYPE;
            plural = Text.GUI_WORD_ENTITY_TYPES;
        } else {
            count = region.rulesFor(flag).size();
            singular = Text.GUI_WORD_RULE;
            plural = Text.GUI_WORD_RULES;
        }
        String summary = Messages.PROTECTION.value(
                count == 0 ? Text.GUI_FLAG_SUMMARY : Text.GUI_FLAG_SUMMARY_ACTIVE, count,
                Messages.PROTECTION.value(count == 1 ? singular : plural));
        Component label = Messages.PROTECTION.flagLabel(flag.name(), count > 0);
        Component tip = Messages.PROTECTION.text(Text.GUI_FLAG_TIP, summary);
        return dialogButton(label, tip, p -> openFlagDetail(p, region, pm, permissionManager, flag));
    }

    // Route by flag type to the appropriate detail screen.
    private static void openFlagDetail(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager, RegionFlag flag) {
        if (flag.isMaterialFlag()) {
            openMaterialListMenu(player, region, pm, permissionManager, flag, 0);
        } else if (flag.isEntityFlag()) {
            openEntityListMenu(player, region, pm, permissionManager, flag, 0);
        } else {
            openBooleanTargetsMenu(player, region, pm, permissionManager, flag);
        }
    }

    // --- Boolean flag: target-by-target tri-state (Unset / True / False) ---

    private static void openBooleanTargetsMenu(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager, RegionFlag flag) {
        Region fresh = refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Messages.PROTECTION.text(Text.REGION_GONE));
            return;
        }
        Map<FlagTarget, Boolean> rules = fresh.rulesFor(flag);

        List<ActionButton> buttons = new ArrayList<>();
        for (FlagTarget t : List.of(FlagTarget.OWNER, FlagTarget.MANAGER, FlagTarget.MEMBER, FlagTarget.DEFAULT)) {
            buttons.add(booleanTargetButton(fresh, pm, permissionManager, flag, t, rules.get(t)));
        }
        // Emit one button per registered permission group, preserving the current
        // rule value (allowed/denied/unset) for groups that already have a rule.
        // Using a Set to guard against duplicates is defensive but harmless.
        if (permissionManager != null) {
            java.util.Set<String> seen = new java.util.LinkedHashSet<>(permissionManager.getGroups().keySet());
            for (String groupName : seen) {
                FlagTarget groupTarget = FlagTarget.group(groupName);
                buttons.add(booleanTargetButton(fresh, pm, permissionManager, flag, groupTarget, rules.get(groupTarget)));
            }
        } else {
            // Fallback: no PermissionManager available — show only groups that already
            // have a configured rule, preserving the previous behaviour.
            for (Map.Entry<FlagTarget, Boolean> e : rules.entrySet()) {
                if (e.getKey().type() == FlagTarget.Type.GROUP) {
                    buttons.add(booleanTargetButton(fresh, pm, permissionManager, flag, e.getKey(), e.getValue()));
                }
            }
        }

        ActionButton back = dialogButton(
                Messages.PROTECTION.text(Text.GUI_BACK_FLAGS),
                Messages.PROTECTION.text(Text.GUI_BACK_FLAGS_TIP),
                p -> openFlagsMenu(p, fresh, pm, permissionManager));

        DialogBase base = DialogBase.builder(Messages.PROTECTION.title(
                Text.GUI_FLAG_DETAIL_TITLE, flag.name(), fresh.id()))
                .body(List.of(DialogBody.plainMessage(
                        Messages.PROTECTION.text(Text.GUI_BOOLEAN_BODY)
                                .decoration(TextDecoration.ITALIC, false))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(LIST_COLUMNS)
                        .exitAction(back)
                        .build()));
        player.showDialog(dialog);
    }

    // Tri-state cycle button. Each click advances Unset -> True -> False -> Unset.
    // Persists via setFlag (true/false) or removeFlag (cycling back to Unset).
    private static ActionButton booleanTargetButton(Region region, ProtectionManager pm, PermissionManager permissionManager,
                                                    RegionFlag flag, FlagTarget target, Boolean current) {
        Component label;
        if (current == null) {
            label = Messages.PROTECTION.text(Text.GUI_BOOLEAN_UNSET, target.toKey());
        } else if (current) {
            label = Messages.PROTECTION.text(Text.GUI_BOOLEAN_TRUE, target.toKey());
        } else {
            label = Messages.PROTECTION.text(Text.GUI_BOOLEAN_FALSE, target.toKey());
        }
        Component tip = Messages.PROTECTION.text(Text.GUI_BOOLEAN_TIP);
        return dialogButton(label, tip, p -> cycleBooleanRule(p, region, pm, permissionManager, flag, target, current));
    }

    private static void cycleBooleanRule(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager,
                                         RegionFlag flag, FlagTarget target, Boolean current) {
        if ((current == null || current) && target.type() == FlagTarget.Type.GROUP
                && "default".equals(target.groupName())) {
            RegionFlagEditor.warnDefaultGroupTarget(player, target, region.id());
        }
        World w = worldOf(region);
        boolean ok;
        if (current == null) {
            ok = pm.setFlag(w, region.id(), flag, target, true);
        } else if (current) {
            ok = pm.setFlag(w, region.id(), flag, target, false);
        } else {
            ok = pm.removeFlag(w, region.id(), flag, target);
        }
        if (!ok) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_RULE_NO_CHANGE));
        }
        openBooleanTargetsMenu(player, region, pm, permissionManager, flag);
    }

    // --- Material list flag (ALLOW_BLOCK_BREAK etc.) ---

    private static void openMaterialListMenu(Player player, Region region, ProtectionManager pm,
                                             PermissionManager permissionManager, RegionFlag flag, int page) {
        Region fresh = refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Messages.PROTECTION.text(Text.REGION_GONE));
            return;
        }
        List<Material> materials = new ArrayList<>(fresh.materialsFor(flag));
        materials.sort(java.util.Comparator.comparing(Enum::name));

        int totalPages = Math.max(1, (materials.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, materials.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            Material m = materials.get(i);
            buttons.add(dialogButton(
                    Messages.PROTECTION.text(Text.GUI_REMOVE_ENTRY, m.name()),
                    Messages.PROTECTION.text(Text.GUI_REMOVE_LIST_TIP),
                    p -> handleRemoveMaterial(p, fresh, pm, permissionManager, flag, m, currentPage)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                p -> openMaterialListMenu(p, fresh, pm, permissionManager, flag, currentPage - 1),
                p -> openMaterialListMenu(p, fresh, pm, permissionManager, flag, currentPage + 1));

        buttons.add(dialogButton(
                Messages.PROTECTION.text(Text.GUI_ADD_MATERIAL),
                Messages.PROTECTION.text(Text.GUI_ADD_MATERIAL_TIP),
                p -> openAddMaterialDialog(p, fresh, pm, permissionManager, flag)));

        ActionButton back = dialogButton(
                Messages.PROTECTION.text(Text.GUI_BACK_FLAGS),
                Messages.PROTECTION.text(Text.GUI_BACK_FLAGS_TIP),
                p -> openFlagsMenu(p, fresh, pm, permissionManager));

        DialogBase base = DialogBase.builder(Messages.PROTECTION.title(
                Text.GUI_FLAG_DETAIL_TITLE, flag.name(), fresh.id()))
                .body(List.of(DialogBody.plainMessage(pageSummary(materials.size(),
                        Text.GUI_WORD_MATERIAL, Text.GUI_WORD_MATERIALS, currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(LIST_COLUMNS)
                        .exitAction(back)
                        .build()));
        player.showDialog(dialog);
    }

    private static void handleRemoveMaterial(Player player, Region region, ProtectionManager pm,
                                             PermissionManager permissionManager, RegionFlag flag, Material material, int returnPage) {
        EnumSet<Material> current = EnumSet.noneOf(Material.class);
        current.addAll(region.materialsFor(flag));
        if (!current.remove(material)) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MATERIAL_NOT_PRESENT));
            openMaterialListMenu(player, region, pm, permissionManager, flag, returnPage);
            return;
        }
        if (!pm.setMaterials(worldOf(region), region.id(), flag, current)) {
            player.sendMessage(Messages.PROTECTION.text(Text.MATERIAL_NO_CHANGE));
        } else {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MATERIAL_REMOVED,
                    material.name(), flag.name()));
        }
        openMaterialListMenu(player, region, pm, permissionManager, flag, returnPage);
    }

    private static void openAddMaterialDialog(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager, RegionFlag flag) {
        RegionGuiSupport.openTextInputDialog(player,
                Messages.PROTECTION.title(Text.GUI_ADD_MATERIAL_TITLE),
                Messages.PROTECTION.text(Text.GUI_ADD_MATERIAL_PROMPT),
                "material_name", Messages.PROTECTION.text(Text.GUI_INPUT_VALUE), 32,
                Messages.PROTECTION.text(Text.GUI_APPLY_VALUE_TIP),
                (p, raw) -> handleAddMaterialSubmission(p, region, pm, permissionManager, flag, raw),
                p -> openMaterialListMenu(p, region, pm, permissionManager, flag, 0));
    }

    private static void handleAddMaterialSubmission(Player player, Region region, ProtectionManager pm,
                                                    PermissionManager permissionManager, RegionFlag flag, String rawValue) {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.isEmpty()) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_INVALID_MATERIAL));
            openMaterialListMenu(player, region, pm, permissionManager, flag, 0);
            return;
        }
        Material m = Material.matchMaterial(trimmed);
        if (m == null || !m.isBlock()) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_UNKNOWN_BLOCK, trimmed));
            openMaterialListMenu(player, region, pm, permissionManager, flag, 0);
            return;
        }
        EnumSet<Material> current = EnumSet.noneOf(Material.class);
        current.addAll(region.materialsFor(flag));
        if (!current.add(m)) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MATERIAL_EXISTS));
            openMaterialListMenu(player, region, pm, permissionManager, flag, 0);
            return;
        }
        if (!pm.setMaterials(worldOf(region), region.id(), flag, current)) {
            player.sendMessage(Messages.PROTECTION.text(Text.MATERIAL_NO_CHANGE));
        } else {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_MATERIAL_ADDED, m.name(), flag.name()));
        }
        openMaterialListMenu(player, region, pm, permissionManager, flag, 0);
    }

    // --- Entity list flag (ALLOW_MOB_SPAWN / DENY_MOB_SPAWN) ---

    private static void openEntityListMenu(Player player, Region region, ProtectionManager pm,
                                           PermissionManager permissionManager, RegionFlag flag, int page) {
        Region fresh = refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Messages.PROTECTION.text(Text.REGION_GONE));
            return;
        }
        List<EntityType> entities = new ArrayList<>(fresh.entitiesFor(flag));
        entities.sort(java.util.Comparator.comparing(Enum::name));

        int totalPages = Math.max(1, (entities.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, entities.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            EntityType t = entities.get(i);
            buttons.add(dialogButton(
                    Messages.PROTECTION.text(Text.GUI_REMOVE_ENTRY, t.name()),
                    Messages.PROTECTION.text(Text.GUI_REMOVE_LIST_TIP),
                    p -> handleRemoveEntity(p, fresh, pm, permissionManager, flag, t, currentPage)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                p -> openEntityListMenu(p, fresh, pm, permissionManager, flag, currentPage - 1),
                p -> openEntityListMenu(p, fresh, pm, permissionManager, flag, currentPage + 1));

        buttons.add(dialogButton(
                Messages.PROTECTION.text(Text.GUI_ADD_ENTITY),
                Messages.PROTECTION.text(Text.GUI_ADD_ENTITY_TIP),
                p -> openAddEntityDialog(p, fresh, pm, permissionManager, flag)));

        ActionButton back = dialogButton(
                Messages.PROTECTION.text(Text.GUI_BACK_FLAGS),
                Messages.PROTECTION.text(Text.GUI_BACK_FLAGS_TIP),
                p -> openFlagsMenu(p, fresh, pm, permissionManager));

        DialogBase base = DialogBase.builder(Messages.PROTECTION.title(
                Text.GUI_FLAG_DETAIL_TITLE, flag.name(), fresh.id()))
                .body(List.of(DialogBody.plainMessage(pageSummary(entities.size(),
                        Text.GUI_WORD_ENTITY_TYPE, Text.GUI_WORD_ENTITY_TYPES, currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(LIST_COLUMNS)
                        .exitAction(back)
                        .build()));
        player.showDialog(dialog);
    }

    private static void handleRemoveEntity(Player player, Region region, ProtectionManager pm,
                                           PermissionManager permissionManager, RegionFlag flag, EntityType type, int returnPage) {
        EnumSet<EntityType> current = EnumSet.noneOf(EntityType.class);
        current.addAll(region.entitiesFor(flag));
        if (!current.remove(type)) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_ENTITY_NOT_PRESENT));
            openEntityListMenu(player, region, pm, permissionManager, flag, returnPage);
            return;
        }
        if (!pm.setEntities(worldOf(region), region.id(), flag, current)) {
            player.sendMessage(Messages.PROTECTION.text(Text.ENTITY_NO_CHANGE));
        } else {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_ENTITY_REMOVED,
                    type.name(), flag.name()));
        }
        openEntityListMenu(player, region, pm, permissionManager, flag, returnPage);
    }

    private static void openAddEntityDialog(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager, RegionFlag flag) {
        RegionGuiSupport.openTextInputDialog(player,
                Messages.PROTECTION.title(Text.GUI_ADD_ENTITY_TITLE),
                Messages.PROTECTION.text(Text.GUI_ADD_ENTITY_PROMPT),
                "entity_name", Messages.PROTECTION.text(Text.GUI_INPUT_VALUE), 48,
                Messages.PROTECTION.text(Text.GUI_APPLY_VALUE_TIP),
                (p, raw) -> handleAddEntitySubmission(p, region, pm, permissionManager, flag, raw),
                p -> openEntityListMenu(p, region, pm, permissionManager, flag, 0));
    }

    private static void handleAddEntitySubmission(Player player, Region region, ProtectionManager pm,
                                                  PermissionManager permissionManager, RegionFlag flag, String rawValue) {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.isEmpty()) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_INVALID_ENTITY));
            openEntityListMenu(player, region, pm, permissionManager, flag, 0);
            return;
        }
        EntityType t;
        try {
            t = EntityType.valueOf(trimmed.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            player.sendMessage(Messages.PROTECTION.text(Text.UNKNOWN_ENTITY, trimmed));
            openEntityListMenu(player, region, pm, permissionManager, flag, 0);
            return;
        }
        EnumSet<EntityType> current = EnumSet.noneOf(EntityType.class);
        current.addAll(region.entitiesFor(flag));
        if (!current.add(t)) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_ENTITY_EXISTS));
            openEntityListMenu(player, region, pm, permissionManager, flag, 0);
            return;
        }
        if (!pm.setEntities(worldOf(region), region.id(), flag, current)) {
            player.sendMessage(Messages.PROTECTION.text(Text.ENTITY_NO_CHANGE));
        } else {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_ENTITY_ADDED, t.name(), flag.name()));
        }
        openEntityListMenu(player, region, pm, permissionManager, flag, 0);
    }

    // ---------------------------------------------------------- Members menu

    static void openMembersMenu(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager) {
        RegionMembershipGui.openMembersMenu(player, region, pm, permissionManager);
    }

    // Public for testing: routes a raw add-member input to the correct
    // ProtectionManager mutator. Returns true on success, false on no-op
    // (already present) or invalid input. Keeps group-prefix detection in one
    // testable place that unit tests can exercise without a Paper Dialog.
    public static boolean routeAddMemberInput(Player player, Region region, ProtectionManager pm, String rawName) {
        return RegionMembershipGui.routeAddMemberInput(player, region, pm, rawName);
    }

    // Public for testing: same routing for the add-manager dialog.
    public static boolean routeAddManagerInput(Player player, Region region, ProtectionManager pm, String rawName) {
        return RegionMembershipGui.routeAddManagerInput(player, region, pm, rawName);
    }

    // ---------------------------------------------------------- Managers menu

    static void openManagersMenu(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager) {
        RegionMembershipGui.openManagersMenu(player, region, pm, permissionManager);
    }

    // Package-visible (not private) so RegionGUIManagerAuthTest can exercise the owner-only
    // guard directly instead of via reflection.
    static void openManagersMenu(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager, int page) {
        RegionMembershipGui.openManagersMenu(player, region, pm, permissionManager, page);
    }

    // Package-visible (not private) so RegionGUIManagerAuthTest can exercise the owner-only
    // guard directly instead of via reflection.
    static void handleRemoveManager(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager, UUID target, int returnPage) {
        RegionMembershipGui.handleRemoveManager(player, region, pm, permissionManager, target, returnPage);
    }

    // Package-visible (not private) so RegionGUIManagerAuthTest can exercise the owner-only
    // guard directly instead of via reflection.
    static void handleRemoveManagerGroup(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager, String groupName, int returnPage) {
        RegionMembershipGui.handleRemoveManagerGroup(player, region, pm, permissionManager, groupName, returnPage);
    }

    // Package-visible (not private) so RegionGUIManagerAuthTest can exercise the owner-only
    // guard directly instead of via reflection.
    static void openAddManagerDialog(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager) {
        RegionMembershipGui.openAddManagerDialog(player, region, pm, permissionManager);
    }

    // Package-visible (not private) so RegionGUIManagerAuthTest can exercise the owner-only
    // guard directly instead of via reflection.
    static void handleAddManagerSubmission(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager, String rawName) {
        RegionMembershipGui.handleAddManagerSubmission(player, region, pm, permissionManager, rawName);
    }

    // Resolve a raw name from the input field to an OfflinePlayer with a real
    // UUID. Returns null and notifies the player on any validation failure.
    @SuppressWarnings("deprecation") // getOfflinePlayer(String) is the only sync path from a literal name.
    static OfflinePlayer validatePlayerName(Player actor, String rawName) {
        String trimmed = rawName == null ? "" : rawName.trim();
        if (trimmed.isEmpty()) {
            actor.sendMessage(Messages.PROTECTION.text(Text.GUI_INVALID_PLAYER));
            return null;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(trimmed);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            actor.sendMessage(Messages.PROTECTION.text(Text.GUI_PLAYER_NEVER_PLAYED, trimmed));
            return null;
        }
        return target;
    }

    // -------------------------------------------------------- Sub-regions menu

    static void openSubRegionsMenu(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager) {
        if (!RegionAuth.isOwnerOrAdmin(player, region, pm)) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_SUBREGION_AUTH));
            return;
        }
        Region fresh = refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Messages.PROTECTION.text(Text.REGION_GONE));
            return;
        }

        List<ActionButton> buttons = new ArrayList<>();
        if (fresh.hasParent()) {
            buttons.add(dialogButton(
                    Messages.PROTECTION.text(Text.GUI_UNSET_PARENT),
                    Messages.PROTECTION.text(Text.GUI_UNSET_PARENT_TIP, fresh.id()),
                    p -> handleUnsetParent(p, fresh, pm, permissionManager)));
        }
        buttons.add(dialogButton(
                Messages.PROTECTION.text(fresh.hasParent()
                        ? Text.GUI_CHANGE_PARENT : Text.GUI_SET_PARENT),
                Messages.PROTECTION.text(Text.GUI_SET_PARENT_TIP),
                p -> openSetParentDialog(p, fresh, pm, permissionManager)));

        ActionButton back = dialogButton(
                Messages.PROTECTION.text(Text.GUI_BACK_REGION),
                Messages.PROTECTION.text(Text.GUI_BACK_REGION_TIP),
                p -> openRegionHub(p, fresh, pm, permissionManager));

        String parentLabel = fresh.hasParent() ? fresh.parentId()
                : Messages.PROTECTION.value(Text.GUI_NO_PARENT);
        DialogBase base = DialogBase.builder(Messages.PROTECTION.title(Text.GUI_SUBREGIONS_TITLE, fresh.id()))
                .body(List.of(DialogBody.plainMessage(
                        Messages.PROTECTION.text(Text.GUI_CURRENT_PARENT, parentLabel)
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

    private static void handleUnsetParent(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager) {
        reportSetParentResult(player, region, pm, permissionManager, null,
                pm.setParent(worldOf(region), region.id(), null, RegionAuth.isAdmin(player, pm)));
    }

    private static void openSetParentDialog(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager) {
        RegionGuiSupport.openTextInputDialog(player,
                Messages.PROTECTION.title(Text.GUI_SET_PARENT_TITLE),
                Messages.PROTECTION.text(Text.GUI_SET_PARENT_PROMPT, region.id()),
                "parent_id", Messages.PROTECTION.text(Text.GUI_INPUT_VALUE), 32,
                Messages.PROTECTION.text(Text.GUI_APPLY_VALUE_TIP),
                (p, raw) -> handleSetParentSubmission(p, region, pm, permissionManager, raw),
                p -> openSubRegionsMenu(p, region, pm, permissionManager));
    }

    private static void handleSetParentSubmission(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager, String rawValue) {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.isEmpty()) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_PARENT_EMPTY));
            openSubRegionsMenu(player, region, pm, permissionManager);
            return;
        }
        Region parent = pm.byName(worldOf(region), trimmed);
        if (parent == null) {
            player.sendMessage(Messages.PROTECTION.text(Text.PARENT_UNKNOWN, trimmed));
            openSubRegionsMenu(player, region, pm, permissionManager);
            return;
        }
        if (!RegionAuth.isOwnerOrAdmin(player, parent, pm)) {
            player.sendMessage(Messages.PROTECTION.text(Text.PARENT_TARGET_AUTH));
            openSubRegionsMenu(player, region, pm, permissionManager);
            return;
        }
        reportSetParentResult(player, region, pm, permissionManager, trimmed,
                pm.setParent(worldOf(region), region.id(), trimmed, RegionAuth.isAdmin(player, pm)));
    }

    private static void reportSetParentResult(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager,
                                              String parentId, ProtectionManager.SetParentResult result) {
        switch (result) {
            case OK -> {
                if (parentId == null) {
                    player.sendMessage(Messages.PROTECTION.text(Text.PARENT_TOP_LEVEL, region.id()));
                } else {
                    player.sendMessage(Messages.PROTECTION.text(Text.PARENT_SUCCESS,
                            region.id(), parentId));
                }
            }
            case NO_CHANGE -> player.sendMessage(Messages.PROTECTION.text(Text.PARENT_NO_CHANGE));
            case UNKNOWN_CHILD -> player.sendMessage(Messages.PROTECTION.text(
                    Text.REGION_NOT_FOUND, region.id()));
            case UNKNOWN_PARENT -> player.sendMessage(Messages.PROTECTION.text(Text.PARENT_UNKNOWN, parentId));
            case SELF_REFERENCE -> player.sendMessage(Messages.PROTECTION.text(Text.PARENT_SELF));
            case CYCLE -> player.sendMessage(Messages.PROTECTION.text(Text.PARENT_CYCLE));
            case DIFFERENT_WORLDS -> player.sendMessage(Messages.PROTECTION.text(Text.PARENT_OTHER_WORLD));
            case DIFFERENT_OWNER -> player.sendMessage(Messages.PROTECTION.text(Text.PARENT_DIFFERENT_OWNER));
            case NOT_CONTAINED_IN_PARENT -> player.sendMessage(Messages.PROTECTION.text(
                    Text.PARENT_NOT_CONTAINED));
            case OVERLAPS_SIBLING -> player.sendMessage(Messages.PROTECTION.text(
                    Text.PARENT_SIBLING_OVERLAP));
        }
        openSubRegionsMenu(player, region, pm, permissionManager);
    }

    // ----------------------------------------------------- Unclaim confirmation

    static void openUnclaimConfirmation(Player player, Region region, ProtectionManager pm, PermissionManager permissionManager) {
        if (!RegionAuth.isOwnerOrAdmin(player, region, pm)) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_UNCLAIM_AUTH));
            return;
        }
        Region fresh = refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Messages.PROTECTION.text(Text.REGION_GONE));
            return;
        }

        ActionButton yes = dialogButton(
                Messages.PROTECTION.text(Text.GUI_UNCLAIM_YES),
                Messages.PROTECTION.text(Text.GUI_UNCLAIM_YES_TIP),
                p -> handleUnclaim(p, fresh, pm));

        ActionButton no = dialogButton(
                Messages.PROTECTION.text(Text.GUI_UNCLAIM_NO),
                Messages.PROTECTION.text(Text.GUI_RETURN_NO_CHANGES),
                p -> openRegionHub(p, fresh, pm, permissionManager));

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(
                Messages.PROTECTION.text(Text.GUI_UNCLAIM_BODY)
                        .decoration(TextDecoration.ITALIC, false)));
        int descendantCount = pm.descendantsOf(worldOf(fresh), fresh.id()).size();
        if (descendantCount > 0) {
            body.add(DialogBody.plainMessage(
                    Messages.PROTECTION.text(Text.GUI_UNCLAIM_BODY_CASCADE, descendantCount)
                            .decoration(TextDecoration.ITALIC, false)));
        }

        DialogBase base = DialogBase.builder(Messages.PROTECTION.title(Text.GUI_UNCLAIM_TITLE, fresh.id()))
                .body(body)
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(yes, no)));
        player.showDialog(dialog);
    }

    // Keep the GUI confirmation on the same cascade-and-refund path as the CLI command.
    // Re-check authorization when the callback runs so a stale dialog cannot bypass the
    // owner/admin gate.
    private static void handleUnclaim(Player player, Region region, ProtectionManager pm) {
        if (!RegionAuth.isOwnerOrAdmin(player, region, pm)) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_UNCLAIM_AUTH));
            return;
        }
        UnclaimSubcommand.unclaimWithRefund(player, pm, region);
    }

    // Auth is now centralized in RegionAuth (formerly a local copy here — identical semantics,
    // since this class only ever calls it with a real Player, never a bare CommandSender).

    // ------------------------------------------------------------------
    // Compatibility façade for package tests and existing UI callers. The
    // implementations live in RegionGuiSupport so screens focus on region
    // actions rather than Paper Dialog and context plumbing.
    // ------------------------------------------------------------------

    static ClickCallback.Options unlimitedClicks() {
        return RegionGuiSupport.unlimitedClicks();
    }

    // Re-resolve `stale` against the live cache. Region records are immutable;
    // each mutation builds a new record and swaps the cache entry, so any
    // long-held reference goes stale immediately on add/remove. Prefer the
    // world-aware lookup when possible to disambiguate same-id regions across
    // worlds.
    static Region refreshRegion(ProtectionManager pm, Region stale) {
        return RegionGuiSupport.refreshRegion(pm, stale);
    }

    // World context for a region's mutator calls. Per-world regions (including
    // the per-world globals) carry their worldName; passing it to the World-aware
    // mutator overloads routes the write to the correct cache entry. Legacy
    // Legacy null-world regions use the bare-key compatibility lookup.
    static World worldOf(Region region) {
        return RegionGuiSupport.worldOf(region);
    }

    @SuppressWarnings("deprecation") // Bukkit#getOfflinePlayer(UUID) is the supported sync path.
    static String lookupName(UUID uuid) {
        return RegionGuiSupport.lookupName(uuid);
    }

    static Component pageSummary(int total, Text singular, Text plural, int currentPage, int totalPages) {
        return RegionGuiSupport.pageSummary(total, singular, plural, currentPage, totalPages);
    }

    static void addPageNavButtons(List<ActionButton> buttons, int currentPage, int totalPages,
                                  Consumer<Player> prevAction, Consumer<Player> nextAction) {
        RegionGuiSupport.addPageNavButtons(buttons, currentPage, totalPages, prevAction, nextAction);
    }

    static ActionButton dialogButton(Component label, Component tooltip, Consumer<Player> action) {
        return RegionGuiSupport.dialogButton(label, tooltip, action);
    }
}
