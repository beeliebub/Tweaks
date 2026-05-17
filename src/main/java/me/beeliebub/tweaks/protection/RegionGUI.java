package me.beeliebub.tweaks.protection;

import me.beeliebub.tweaks.permissions.Permissions;
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
@SuppressWarnings("UnstableApiUsage") // Paper Dialog API is @ApiStatus.Experimental in 26.1.2.
public final class RegionGUI {

    private RegionGUI() {}

    private static final MiniMessage MM = MiniMessage.miniMessage();
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
    public static void openRegionHub(Player player, Region region, ProtectionManager pm) {
        List<ActionButton> buttons = new ArrayList<>();

        buttons.add(dialogButton(
                Component.text("Edit Flags", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("Manage boolean, material, and entity flags.", NamedTextColor.GRAY),
                p -> openFlagsMenu(p, region, pm)));

        buttons.add(dialogButton(
                Component.text("Edit Members", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text("Add or remove region members.", NamedTextColor.GRAY),
                p -> openMembersMenu(p, region, pm)));

        buttons.add(dialogButton(
                Component.text("Edit Managers", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text("Promote or demote managers (owner only).", NamedTextColor.GRAY),
                p -> openManagersMenu(p, region, pm)));

        // Sub-region nesting and unclaim are owner-only on purpose: delegating a
        // manager the ability to unclaim or reparent would let a promoted role
        // destroy the very region they were promoted to maintain. Hide both
        // buttons from non-owners so the UI matches the underlying auth model.
        if (isOwnerOrAdmin(player, region)) {
            buttons.add(dialogButton(
                    Component.text("Manage Sub-regions", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    Component.text("Set or clear this region's parent.", NamedTextColor.GRAY),
                    p -> openSubRegionsMenu(p, region, pm)));

            buttons.add(dialogButton(
                    Component.text("Unclaim", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Delete this region. Cannot be undone.", NamedTextColor.RED),
                    p -> openUnclaimConfirmation(p, region, pm)));
        }

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>Region: " + region.id()))
                .body(List.of(DialogBody.plainMessage(
                        Component.text("Manage settings for this region.", NamedTextColor.GRAY)
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

    static void openFlagsMenu(Player player, Region region, ProtectionManager pm) {
        openFlagsMenu(player, region, pm, 0);
    }

    private static void openFlagsMenu(Player player, Region region, ProtectionManager pm, int page) {
        Region fresh = refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Component.text("Region no longer exists.", NamedTextColor.RED));
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
            buttons.add(flagListEntryButton(fresh, pm, flag));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                p -> openFlagsMenu(p, fresh, pm, currentPage - 1),
                p -> openFlagsMenu(p, fresh, pm, currentPage + 1));

        ActionButton back = dialogButton(
                Component.text("← Back to Region", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the region dashboard.", NamedTextColor.GRAY),
                p -> openRegionHub(p, fresh, pm));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>Flags: " + fresh.id()))
                .body(List.of(DialogBody.plainMessage(pageSummary(all.length, "flag", currentPage, totalPages))))
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
    private static ActionButton flagListEntryButton(Region region, ProtectionManager pm, RegionFlag flag) {
        String summary;
        NamedTextColor color;
        if (flag.isMaterialFlag()) {
            int n = region.materialsFor(flag).size();
            summary = n + " material" + (n == 1 ? "" : "s");
            color = n == 0 ? NamedTextColor.GRAY : NamedTextColor.YELLOW;
        } else if (flag.isEntityFlag()) {
            int n = region.entitiesFor(flag).size();
            summary = n + " entity type" + (n == 1 ? "" : "s");
            color = n == 0 ? NamedTextColor.GRAY : NamedTextColor.YELLOW;
        } else {
            int n = region.rulesFor(flag).size();
            summary = n + " rule" + (n == 1 ? "" : "s");
            color = n == 0 ? NamedTextColor.GRAY : NamedTextColor.YELLOW;
        }
        Component label = Component.text(flag.name(), color, TextDecoration.BOLD);
        Component tip = Component.text(summary + " — click to edit.", NamedTextColor.GRAY);
        return dialogButton(label, tip, p -> openFlagDetail(p, region, pm, flag));
    }

    // Route by flag type to the appropriate detail screen.
    private static void openFlagDetail(Player player, Region region, ProtectionManager pm, RegionFlag flag) {
        if (flag.isMaterialFlag()) {
            openMaterialListMenu(player, region, pm, flag, 0);
        } else if (flag.isEntityFlag()) {
            openEntityListMenu(player, region, pm, flag, 0);
        } else {
            openBooleanTargetsMenu(player, region, pm, flag);
        }
    }

    // --- Boolean flag: target-by-target tri-state (Unset / True / False) ---

    private static void openBooleanTargetsMenu(Player player, Region region, ProtectionManager pm, RegionFlag flag) {
        Region fresh = refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Component.text("Region no longer exists.", NamedTextColor.RED));
            return;
        }
        Map<FlagTarget, Boolean> rules = fresh.rulesFor(flag);

        List<ActionButton> buttons = new ArrayList<>();
        for (FlagTarget t : List.of(FlagTarget.OWNER, FlagTarget.MANAGER, FlagTarget.MEMBER, FlagTarget.DEFAULT)) {
            buttons.add(booleanTargetButton(fresh, pm, flag, t, rules.get(t)));
        }
        for (Map.Entry<FlagTarget, Boolean> e : rules.entrySet()) {
            if (e.getKey().type() == FlagTarget.Type.GROUP) {
                buttons.add(booleanTargetButton(fresh, pm, flag, e.getKey(), e.getValue()));
            }
        }

        ActionButton back = dialogButton(
                Component.text("← Back to Flags", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the flag list.", NamedTextColor.GRAY),
                p -> openFlagsMenu(p, fresh, pm));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>" + flag.name() + ": " + fresh.id()))
                .body(List.of(DialogBody.plainMessage(
                        Component.text("Pick an audience to set or cycle this flag.", NamedTextColor.GRAY)
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
    private static ActionButton booleanTargetButton(Region region, ProtectionManager pm,
                                                    RegionFlag flag, FlagTarget target, Boolean current) {
        NamedTextColor color;
        String glyph;
        String state;
        if (current == null) {
            color = NamedTextColor.GRAY;
            glyph = "○";
            state = "unset";
        } else if (current) {
            color = NamedTextColor.GREEN;
            glyph = "✓";
            state = "true";
        } else {
            color = NamedTextColor.RED;
            glyph = "✗";
            state = "false";
        }
        Component label = Component.text(glyph + " " + target.toKey() + " — " + state, color, TextDecoration.BOLD);
        Component tip = Component.text("Click to cycle Unset → True → False → Unset.", NamedTextColor.GRAY);
        return dialogButton(label, tip, p -> cycleBooleanRule(p, region, pm, flag, target, current));
    }

    private static void cycleBooleanRule(Player player, Region region, ProtectionManager pm,
                                         RegionFlag flag, FlagTarget target, Boolean current) {
        boolean ok;
        if (current == null) {
            ok = pm.setFlag(region.id(), flag, target, true);
        } else if (current) {
            ok = pm.setFlag(region.id(), flag, target, false);
        } else {
            ok = pm.removeFlag(region.id(), flag, target);
        }
        if (!ok) {
            player.sendMessage(Component.text(
                    "No change — rule already at requested value.", NamedTextColor.YELLOW));
        }
        openBooleanTargetsMenu(player, region, pm, flag);
    }

    // --- Material list flag (ALLOW_BLOCK_BREAK etc.) ---

    private static void openMaterialListMenu(Player player, Region region, ProtectionManager pm,
                                             RegionFlag flag, int page) {
        Region fresh = refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Component.text("Region no longer exists.", NamedTextColor.RED));
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
                    Component.text("✗ " + m.name(), NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Click to remove from the list.", NamedTextColor.GRAY),
                    p -> handleRemoveMaterial(p, fresh, pm, flag, m, currentPage)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                p -> openMaterialListMenu(p, fresh, pm, flag, currentPage - 1),
                p -> openMaterialListMenu(p, fresh, pm, flag, currentPage + 1));

        buttons.add(dialogButton(
                Component.text("+ Add Material", NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("Enter a block material name.", NamedTextColor.GRAY),
                p -> openAddMaterialDialog(p, fresh, pm, flag)));

        ActionButton back = dialogButton(
                Component.text("← Back to Flags", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the flag list.", NamedTextColor.GRAY),
                p -> openFlagsMenu(p, fresh, pm));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>" + flag.name() + ": " + fresh.id()))
                .body(List.of(DialogBody.plainMessage(pageSummary(materials.size(), "material", currentPage, totalPages))))
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
                                             RegionFlag flag, Material material, int returnPage) {
        EnumSet<Material> current = EnumSet.noneOf(Material.class);
        current.addAll(region.materialsFor(flag));
        if (!current.remove(material)) {
            player.sendMessage(Component.text(
                    "No change — material not in list.", NamedTextColor.YELLOW));
            openMaterialListMenu(player, region, pm, flag, returnPage);
            return;
        }
        pm.setMaterials(region.id(), flag, current);
        player.sendMessage(Component.text(
                "Removed " + material.name() + " from " + flag.name() + ".",
                NamedTextColor.GREEN));
        openMaterialListMenu(player, region, pm, flag, returnPage);
    }

    private static void openAddMaterialDialog(Player player, Region region, ProtectionManager pm, RegionFlag flag) {
        openSingleTokenDialog(player,
                "Add Material",
                "Enter a block material name (e.g. STONE, OAK_LOG).",
                "material_name", 32,
                (p, raw) -> handleAddMaterialSubmission(p, region, pm, flag, raw),
                p -> openMaterialListMenu(p, region, pm, flag, 0));
    }

    private static void handleAddMaterialSubmission(Player player, Region region, ProtectionManager pm,
                                                    RegionFlag flag, String rawValue) {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.isEmpty()) {
            player.sendMessage(Component.text("Invalid material name.", NamedTextColor.RED));
            openMaterialListMenu(player, region, pm, flag, 0);
            return;
        }
        Material m = Material.matchMaterial(trimmed);
        if (m == null || !m.isBlock()) {
            player.sendMessage(Component.text(
                    "Unknown or non-block material '" + trimmed + "'.", NamedTextColor.RED));
            openMaterialListMenu(player, region, pm, flag, 0);
            return;
        }
        EnumSet<Material> current = EnumSet.noneOf(Material.class);
        current.addAll(region.materialsFor(flag));
        if (!current.add(m)) {
            player.sendMessage(Component.text(
                    "Material already in list.", NamedTextColor.YELLOW));
            openMaterialListMenu(player, region, pm, flag, 0);
            return;
        }
        pm.setMaterials(region.id(), flag, current);
        player.sendMessage(Component.text(
                "Added " + m.name() + " to " + flag.name() + ".", NamedTextColor.GREEN));
        openMaterialListMenu(player, region, pm, flag, 0);
    }

    // --- Entity list flag (ALLOW_MOB_SPAWN / DENY_MOB_SPAWN) ---

    private static void openEntityListMenu(Player player, Region region, ProtectionManager pm,
                                           RegionFlag flag, int page) {
        Region fresh = refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Component.text("Region no longer exists.", NamedTextColor.RED));
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
                    Component.text("✗ " + t.name(), NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Click to remove from the list.", NamedTextColor.GRAY),
                    p -> handleRemoveEntity(p, fresh, pm, flag, t, currentPage)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                p -> openEntityListMenu(p, fresh, pm, flag, currentPage - 1),
                p -> openEntityListMenu(p, fresh, pm, flag, currentPage + 1));

        buttons.add(dialogButton(
                Component.text("+ Add Entity", NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("Enter an entity type name.", NamedTextColor.GRAY),
                p -> openAddEntityDialog(p, fresh, pm, flag)));

        ActionButton back = dialogButton(
                Component.text("← Back to Flags", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the flag list.", NamedTextColor.GRAY),
                p -> openFlagsMenu(p, fresh, pm));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>" + flag.name() + ": " + fresh.id()))
                .body(List.of(DialogBody.plainMessage(pageSummary(entities.size(), "entity type", currentPage, totalPages))))
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
                                           RegionFlag flag, EntityType type, int returnPage) {
        EnumSet<EntityType> current = EnumSet.noneOf(EntityType.class);
        current.addAll(region.entitiesFor(flag));
        if (!current.remove(type)) {
            player.sendMessage(Component.text(
                    "No change — entity not in list.", NamedTextColor.YELLOW));
            openEntityListMenu(player, region, pm, flag, returnPage);
            return;
        }
        pm.setEntities(region.id(), flag, current);
        player.sendMessage(Component.text(
                "Removed " + type.name() + " from " + flag.name() + ".", NamedTextColor.GREEN));
        openEntityListMenu(player, region, pm, flag, returnPage);
    }

    private static void openAddEntityDialog(Player player, Region region, ProtectionManager pm, RegionFlag flag) {
        openSingleTokenDialog(player,
                "Add Entity",
                "Enter an entity type name (e.g. ZOMBIE, ENDERMAN).",
                "entity_name", 48,
                (p, raw) -> handleAddEntitySubmission(p, region, pm, flag, raw),
                p -> openEntityListMenu(p, region, pm, flag, 0));
    }

    private static void handleAddEntitySubmission(Player player, Region region, ProtectionManager pm,
                                                  RegionFlag flag, String rawValue) {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.isEmpty()) {
            player.sendMessage(Component.text("Invalid entity type.", NamedTextColor.RED));
            openEntityListMenu(player, region, pm, flag, 0);
            return;
        }
        EntityType t;
        try {
            t = EntityType.valueOf(trimmed.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                    "Unknown entity type '" + trimmed + "'.", NamedTextColor.RED));
            openEntityListMenu(player, region, pm, flag, 0);
            return;
        }
        EnumSet<EntityType> current = EnumSet.noneOf(EntityType.class);
        current.addAll(region.entitiesFor(flag));
        if (!current.add(t)) {
            player.sendMessage(Component.text("Entity already in list.", NamedTextColor.YELLOW));
            openEntityListMenu(player, region, pm, flag, 0);
            return;
        }
        pm.setEntities(region.id(), flag, current);
        player.sendMessage(Component.text(
                "Added " + t.name() + " to " + flag.name() + ".", NamedTextColor.GREEN));
        openEntityListMenu(player, region, pm, flag, 0);
    }

    // Single-input confirmation dialog: one text field, Add submits, Cancel
    // routes back. Shared by Add Material and Add Entity.
    private static void openSingleTokenDialog(Player player, String title, String prompt,
                                              String inputKey, int maxLength,
                                              java.util.function.BiConsumer<Player, String> onSubmit,
                                              Consumer<Player> onCancel) {
        ActionButton submit = ActionButton.builder(
                        Component.text("Add", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .decoration(TextDecoration.ITALIC, false))
                .tooltip(Component.text("Apply with the entered value.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                onSubmit.accept(p, view.getText(inputKey));
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Component.text("Cancel", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return without changes.", NamedTextColor.GRAY),
                onCancel);

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green><bold>" + title))
                .body(List.of(DialogBody.plainMessage(
                        Component.text(prompt, NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false))))
                .inputs(List.of(
                        DialogInput.text(inputKey,
                                        Component.text("Value", NamedTextColor.YELLOW)
                                                .decoration(TextDecoration.ITALIC, false))
                                .maxLength(maxLength)
                                .build()
                ))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(submit, cancel)));
        player.showDialog(dialog);
    }

    // ---------------------------------------------------------- Members menu

    static void openMembersMenu(Player player, Region region, ProtectionManager pm) {
        openMembersMenu(player, region, pm, 0);
    }

    private static void openMembersMenu(Player player, Region region, ProtectionManager pm, int page) {
        Region fresh = refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Component.text("Region no longer exists.", NamedTextColor.RED));
            return;
        }
        List<UUID> members = new ArrayList<>(fresh.members());
        members.sort(java.util.Comparator.comparing(RegionGUI::lookupName, String.CASE_INSENSITIVE_ORDER));

        int totalPages = Math.max(1, (members.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, members.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            UUID uuid = members.get(i);
            String name = lookupName(uuid);
            buttons.add(dialogButton(
                    Component.text("✗ " + name, NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Click to remove from this region.", NamedTextColor.GRAY),
                    p -> handleRemoveMember(p, fresh, pm, uuid, currentPage)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                p -> openMembersMenu(p, fresh, pm, currentPage - 1),
                p -> openMembersMenu(p, fresh, pm, currentPage + 1));

        buttons.add(dialogButton(
                Component.text("+ Add Member", NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("Open the player-name entry dialog.", NamedTextColor.GRAY),
                p -> openAddMemberDialog(p, fresh, pm)));

        ActionButton back = dialogButton(
                Component.text("← Back to Region", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the region dashboard.", NamedTextColor.GRAY),
                p -> openRegionHub(p, fresh, pm));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>Members: " + fresh.id()))
                .body(List.of(DialogBody.plainMessage(pageSummary(members.size(), "member", currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(LIST_COLUMNS)
                        .exitAction(back)
                        .build()));
        player.showDialog(dialog);
    }

    private static void handleRemoveMember(Player player, Region region, ProtectionManager pm, UUID target, int returnPage) {
        if (!pm.removeMember(region.id(), target)) {
            player.sendMessage(Component.text(
                    "Could not remove — player is no longer a member.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text(
                    "Removed " + lookupName(target) + " from '" + region.id() + "'.",
                    NamedTextColor.GREEN));
        }
        openMembersMenu(player, region, pm, returnPage);
    }

    private static void openAddMemberDialog(Player player, Region region, ProtectionManager pm) {
        openAddPlayerDialog(player, region, pm,
                "Add Member",
                "Enter a player name to add to '" + region.id() + "'. The player must have joined before.",
                (p, name) -> handleAddMemberSubmission(p, region, pm, name),
                p -> openMembersMenu(p, region, pm));
    }

    private static void handleAddMemberSubmission(Player player, Region region, ProtectionManager pm, String rawName) {
        OfflinePlayer target = validatePlayerName(player, rawName);
        if (target == null) {
            openMembersMenu(player, region, pm);
            return;
        }
        if (region.owner().equals(target.getUniqueId())) {
            player.sendMessage(Component.text(
                    "The owner is implicitly a member.", NamedTextColor.YELLOW));
            openMembersMenu(player, region, pm);
            return;
        }
        if (!pm.addMember(region.id(), target.getUniqueId())) {
            player.sendMessage(Component.text(
                    "Could not add — player is already a member.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text(
                    "Added " + target.getName() + " to '" + region.id() + "'.",
                    NamedTextColor.GREEN));
        }
        openMembersMenu(player, region, pm);
    }

    // ---------------------------------------------------------- Managers menu

    static void openManagersMenu(Player player, Region region, ProtectionManager pm) {
        openManagersMenu(player, region, pm, 0);
    }

    private static void openManagersMenu(Player player, Region region, ProtectionManager pm, int page) {
        Region fresh = refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Component.text("Region no longer exists.", NamedTextColor.RED));
            return;
        }
        List<UUID> managers = new ArrayList<>(fresh.managers());
        managers.sort(java.util.Comparator.comparing(RegionGUI::lookupName, String.CASE_INSENSITIVE_ORDER));

        int totalPages = Math.max(1, (managers.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, managers.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            UUID uuid = managers.get(i);
            String name = lookupName(uuid);
            buttons.add(dialogButton(
                    Component.text("✗ " + name, NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Click to demote from manager.", NamedTextColor.GRAY),
                    p -> handleRemoveManager(p, fresh, pm, uuid, currentPage)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                p -> openManagersMenu(p, fresh, pm, currentPage - 1),
                p -> openManagersMenu(p, fresh, pm, currentPage + 1));

        buttons.add(dialogButton(
                Component.text("+ Add Manager", NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("Open the player-name entry dialog.", NamedTextColor.GRAY),
                p -> openAddManagerDialog(p, fresh, pm)));

        ActionButton back = dialogButton(
                Component.text("← Back to Region", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the region dashboard.", NamedTextColor.GRAY),
                p -> openRegionHub(p, fresh, pm));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>Managers: " + fresh.id()))
                .body(List.of(DialogBody.plainMessage(pageSummary(managers.size(), "manager", currentPage, totalPages))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(LIST_COLUMNS)
                        .exitAction(back)
                        .build()));
        player.showDialog(dialog);
    }

    private static void handleRemoveManager(Player player, Region region, ProtectionManager pm, UUID target, int returnPage) {
        if (!pm.removeManager(region.id(), target)) {
            player.sendMessage(Component.text(
                    "Could not demote — player is no longer a manager.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text(
                    "Demoted " + lookupName(target) + " on '" + region.id() + "'.",
                    NamedTextColor.GREEN));
        }
        openManagersMenu(player, region, pm, returnPage);
    }

    private static void openAddManagerDialog(Player player, Region region, ProtectionManager pm) {
        openAddPlayerDialog(player, region, pm,
                "Add Manager",
                "Enter a player name to promote to manager on '" + region.id() + "'.",
                (p, name) -> handleAddManagerSubmission(p, region, pm, name),
                p -> openManagersMenu(p, region, pm));
    }

    private static void handleAddManagerSubmission(Player player, Region region, ProtectionManager pm, String rawName) {
        OfflinePlayer target = validatePlayerName(player, rawName);
        if (target == null) {
            openManagersMenu(player, region, pm);
            return;
        }
        if (region.owner().equals(target.getUniqueId())) {
            player.sendMessage(Component.text(
                    "The owner is implicitly a manager — promotion is unnecessary.",
                    NamedTextColor.YELLOW));
            openManagersMenu(player, region, pm);
            return;
        }
        if (!pm.addManager(region.id(), target.getUniqueId())) {
            player.sendMessage(Component.text(
                    "Could not promote — player is already a manager.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text(
                    "Promoted " + target.getName() + " to manager on '" + region.id() + "'.",
                    NamedTextColor.GREEN));
        }
        openManagersMenu(player, region, pm);
    }

    // ----------------------------------------------------------- Add dialog

    // Shared player-name confirmation dialog used by both Add Member and Add
    // Manager. Submit fires `onSubmit` with the trimmed text; Cancel fires
    // `onCancel`. Callers handle validation and any "already exists" messaging.
    private static void openAddPlayerDialog(Player player, Region region, ProtectionManager pm,
                                            String title, String prompt,
                                            java.util.function.BiConsumer<Player, String> onSubmit,
                                            Consumer<Player> onCancel) {
        ActionButton submit = ActionButton.builder(
                        Component.text("Add", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .decoration(TextDecoration.ITALIC, false))
                .tooltip(Component.text("Apply with the entered name.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                onSubmit.accept(p, view.getText("player_name"));
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Component.text("Cancel", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return without changes.", NamedTextColor.GRAY),
                onCancel);

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green><bold>" + title))
                .body(List.of(DialogBody.plainMessage(
                        Component.text(prompt, NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false))))
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

    // Resolve a raw name from the input field to an OfflinePlayer with a real
    // UUID. Returns null and notifies the player on any validation failure.
    @SuppressWarnings("deprecation") // getOfflinePlayer(String) is the only sync path from a literal name.
    private static OfflinePlayer validatePlayerName(Player actor, String rawName) {
        String trimmed = rawName == null ? "" : rawName.trim();
        if (trimmed.isEmpty()) {
            actor.sendMessage(Component.text("Invalid player name.", NamedTextColor.RED));
            return null;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(trimmed);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            actor.sendMessage(Component.text(
                    "Player '" + trimmed + "' has never played before.", NamedTextColor.RED));
            return null;
        }
        return target;
    }

    // -------------------------------------------------------- Sub-regions menu

    static void openSubRegionsMenu(Player player, Region region, ProtectionManager pm) {
        if (!isOwnerOrAdmin(player, region)) {
            player.sendMessage(Component.text(
                    "Only the region owner can manage sub-region hierarchy.", NamedTextColor.RED));
            return;
        }
        Region fresh = refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Component.text("Region no longer exists.", NamedTextColor.RED));
            return;
        }

        List<ActionButton> buttons = new ArrayList<>();
        if (fresh.hasParent()) {
            buttons.add(dialogButton(
                    Component.text("✗ Unset Parent", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Promote '" + fresh.id() + "' back to a top-level region.", NamedTextColor.GRAY),
                    p -> handleUnsetParent(p, fresh, pm)));
        }
        buttons.add(dialogButton(
                Component.text(fresh.hasParent() ? "Change Parent" : "Set Parent",
                        NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("Enter the id of the parent region.", NamedTextColor.GRAY),
                p -> openSetParentDialog(p, fresh, pm)));

        ActionButton back = dialogButton(
                Component.text("← Back to Region", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the region dashboard.", NamedTextColor.GRAY),
                p -> openRegionHub(p, fresh, pm));

        String parentLabel = fresh.hasParent() ? fresh.parentId() : "(none)";
        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green>Sub-regions: " + fresh.id()))
                .body(List.of(DialogBody.plainMessage(
                        Component.text("Current parent: " + parentLabel, NamedTextColor.GRAY)
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

    private static void handleUnsetParent(Player player, Region region, ProtectionManager pm) {
        reportSetParentResult(player, region, pm, null, pm.setParent(region.id(), null));
    }

    private static void openSetParentDialog(Player player, Region region, ProtectionManager pm) {
        openSingleTokenDialog(player,
                "Set Parent",
                "Enter the id of the region to nest '" + region.id() + "' under.",
                "parent_id", 32,
                (p, raw) -> handleSetParentSubmission(p, region, pm, raw),
                p -> openSubRegionsMenu(p, region, pm));
    }

    private static void handleSetParentSubmission(Player player, Region region, ProtectionManager pm, String rawValue) {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.isEmpty()) {
            player.sendMessage(Component.text("Parent id cannot be empty.", NamedTextColor.RED));
            openSubRegionsMenu(player, region, pm);
            return;
        }
        reportSetParentResult(player, region, pm, trimmed, pm.setParent(region.id(), trimmed));
    }

    private static void reportSetParentResult(Player player, Region region, ProtectionManager pm,
                                              String parentId, ProtectionManager.SetParentResult result) {
        switch (result) {
            case OK -> {
                if (parentId == null) {
                    player.sendMessage(Component.text(
                            "Region '" + region.id() + "' is now top-level.", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text(
                            "Region '" + region.id() + "' is now a sub-region of '" + parentId + "'.",
                            NamedTextColor.GREEN));
                }
            }
            case NO_CHANGE -> player.sendMessage(Component.text(
                    "No change — region already has that parent.", NamedTextColor.YELLOW));
            case UNKNOWN_CHILD -> player.sendMessage(Component.text(
                    "No region named '" + region.id() + "'.", NamedTextColor.RED));
            case UNKNOWN_PARENT -> player.sendMessage(Component.text(
                    "Unknown parent region '" + parentId + "'.", NamedTextColor.RED));
            case SELF_REFERENCE -> player.sendMessage(Component.text(
                    "A region cannot be its own parent.", NamedTextColor.RED));
            case CYCLE -> player.sendMessage(Component.text(
                    "Refused — that would create a parent cycle.", NamedTextColor.RED));
            case DIFFERENT_WORLDS -> player.sendMessage(Component.text(
                    "Refused — child and parent are in different worlds.", NamedTextColor.RED));
            case NOT_CONTAINED_IN_PARENT -> player.sendMessage(Component.text(
                    "Refused — child region is not fully contained inside its parent's bounds.",
                    NamedTextColor.RED));
            case OVERLAPS_SIBLING -> player.sendMessage(Component.text(
                    "Refused — child would overlap another sub-region of the same parent.",
                    NamedTextColor.RED));
        }
        openSubRegionsMenu(player, region, pm);
    }

    // ----------------------------------------------------- Unclaim confirmation

    static void openUnclaimConfirmation(Player player, Region region, ProtectionManager pm) {
        if (!isOwnerOrAdmin(player, region)) {
            player.sendMessage(Component.text(
                    "Only the region owner can unclaim this region.", NamedTextColor.RED));
            return;
        }
        Region fresh = refreshRegion(pm, region);
        if (fresh == null) {
            player.sendMessage(Component.text("Region no longer exists.", NamedTextColor.RED));
            return;
        }

        ActionButton yes = dialogButton(
                Component.text("Yes, Unclaim", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Delete this region permanently.", NamedTextColor.RED),
                p -> handleUnclaim(p, fresh, pm));

        ActionButton no = dialogButton(
                Component.text("No, Keep", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("Return without changes.", NamedTextColor.GRAY),
                p -> openRegionHub(p, fresh, pm));

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><red><bold>Unclaim '" + fresh.id() + "'?"))
                .body(List.of(DialogBody.plainMessage(
                        Component.text("Are you absolutely sure you want to unclaim this region? "
                                        + "This cannot be undone.", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(yes, no)));
        player.showDialog(dialog);
    }

    private static void handleUnclaim(Player player, Region region, ProtectionManager pm) {
        if (!pm.unclaim(region.id())) {
            player.sendMessage(Component.text(
                    "Could not unclaim — region no longer exists.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text(
                "Unclaimed region '" + region.id() + "'. Pointer cleanup will run lazily.",
                NamedTextColor.GREEN));
    }

    // ---------------------------------------------------------------- Auth

    static boolean isOwnerOrAdmin(Player player, Region region) {
        if (player.hasPermission(Permissions.PROTECTION_ADMIN)) return true;
        return region.isOwner(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Dialog helpers — kept private to this file so each GUI screen can
    // build its own buttons without leaking widget plumbing.
    // ------------------------------------------------------------------

    static ClickCallback.Options unlimitedClicks() {
        return ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build();
    }

    // Re-resolve `stale` against the live cache. Region records are immutable;
    // each mutation builds a new record and swaps the cache entry, so any
    // long-held reference goes stale immediately on add/remove. Prefer the
    // world-aware lookup when possible to disambiguate same-id regions across
    // worlds.
    static Region refreshRegion(ProtectionManager pm, Region stale) {
        if (stale == null) return null;
        if (stale.worldName() != null) {
            World w = Bukkit.getWorld(stale.worldName());
            if (w != null) {
                Region fresh = pm.byName(w, stale.id());
                if (fresh != null) return fresh;
            }
        }
        return pm.byNameAnyWorld(stale.id());
    }

    @SuppressWarnings("deprecation") // Bukkit#getOfflinePlayer(UUID) is the supported sync path.
    static String lookupName(UUID uuid) {
        if (uuid == null) return "<unknown>";
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        String name = p.getName();
        return (name == null || name.isEmpty()) ? uuid.toString() : name;
    }

    static Component pageSummary(int total, String noun, int currentPage, int totalPages) {
        String pluralized = total == 1 ? noun : noun + "s";
        return Component.text(total + " " + pluralized + " — Page " + (currentPage + 1) + " of " + totalPages,
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    static void addPageNavButtons(List<ActionButton> buttons, int currentPage, int totalPages,
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

    static ActionButton dialogButton(Component label, Component tooltip, Consumer<Player> action) {
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
}
