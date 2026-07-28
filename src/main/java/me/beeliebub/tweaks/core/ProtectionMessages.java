package me.beeliebub.tweaks.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Player-facing feedback for the protection command, listener, and Paper Dialog surfaces.
 *
 * <p>The templates intentionally preserve the established wording and Adventure styling. The
 * protection package supplies only dynamic values; it must not create player-visible components
 * or MiniMessage strings itself.
 */
public final class ProtectionMessages {

    ProtectionMessages() {
        // Owned by Messages.PROTECTION.
    }

    /** Creates one root component from a centralized protection template. */
    public Component text(Text template, Object... arguments) {
        Component component = Component.text(template.format(arguments), template.color());
        return template.bold() ? component.decorate(TextDecoration.BOLD) : component;
    }

    /** Creates a dialog title, explicitly disabling inherited italic styling. */
    public Component title(Text template, Object... arguments) {
        return text(template, arguments).decoration(TextDecoration.ITALIC, false);
    }

    /** Renders a bold flag name using the existing active/inactive color convention. */
    public Component flagLabel(String flag, boolean active) {
        return Component.text(flag, active ? NamedTextColor.YELLOW : NamedTextColor.GRAY)
                .decorate(TextDecoration.BOLD);
    }

    /** Resolves a centralized template for APIs that require a plain string. */
    public String value(Text template, Object... arguments) {
        return template.format(arguments);
    }

    /** Renders the shared two-color usage row used by /region help output. */
    public Component usageLine(String syntax, String description) {
        return Component.text("  " + syntax, NamedTextColor.GRAY)
                .append(Component.text(" — " + description, NamedTextColor.DARK_GRAY));
    }

    /** Renders one boolean flag rule with its value-dependent color. */
    public Component booleanRule(String flag, String target, boolean value) {
        return Component.text("  " + flag + " [" + target + "] = " + value,
                value ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    /** Renders one indented boolean flag rule in /region info. */
    public Component indentedBooleanRule(String flag, String target, boolean value) {
        return Component.text("      " + flag + " [" + target + "] = " + value,
                value ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    /** Renders a target-only boolean rule in the single-flag command view. */
    public Component targetBooleanRule(String target, boolean value) {
        return Component.text("  [" + target + "] = " + value,
                value ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    /** Renders a hoverable material-list summary for region information output. */
    public Component materialListSummary(String flag, int size, String fullList) {
        return Component.text(flag + " (" + size + " entr" + (size == 1 ? "y" : "ies") + ")",
                        NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text(fullList, NamedTextColor.WHITE)));
    }

    /** Renders a hoverable entity-list summary for region information output. */
    public Component entityListSummary(String flag, int size, String fullList) {
        return Component.text(flag + " (" + size + " entr" + (size == 1 ? "y" : "ies") + ")",
                        NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text(fullList, NamedTextColor.WHITE)));
    }

    /** Preserves the red/yellow segmented claim-limit feedback. */
    public Component claimLimit(int owned, int maxChunks, int requested) {
        return Component.text("You cannot claim that many chunks. You currently own ", NamedTextColor.RED)
                .append(Component.text(owned, NamedTextColor.YELLOW))
                .append(Component.text(" of ", NamedTextColor.RED))
                .append(Component.text(maxChunks, NamedTextColor.YELLOW))
                .append(Component.text("; this claim would add ", NamedTextColor.RED))
                .append(Component.text(requested, NamedTextColor.YELLOW))
                .append(Component.text(" more.", NamedTextColor.RED));
    }

    /** Preserves the red/yellow segmented unaffordable-claim feedback. */
    public Component claimUnaffordable(int cost, int balance) {
        return Component.text("You can't afford this claim. Cost: ", NamedTextColor.RED)
                .append(Component.text(cost, NamedTextColor.YELLOW))
                .append(Component.text(" Resource Rupees; you have ", NamedTextColor.RED))
                .append(Component.text(balance, NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.RED));
    }

    /** Preserves the green/yellow segmented admin claim confirmation. */
    public Component claimAdminSuccess(String name, int chunks) {
        return Component.text("Claimed region ", NamedTextColor.GREEN)
                .append(Component.text("'" + name + "'", NamedTextColor.YELLOW))
                .append(Component.text(" (", NamedTextColor.GREEN))
                .append(Component.text(chunks, NamedTextColor.YELLOW))
                .append(Component.text(" chunk" + (chunks == 1 ? "" : "s")
                        + ", free admin claim).", NamedTextColor.GREEN));
    }

    /** Preserves the green/yellow segmented paid claim confirmation. */
    public Component claimSuccess(String name, int chunks, int cost) {
        return Component.text("Claimed region ", NamedTextColor.GREEN)
                .append(Component.text("'" + name + "'", NamedTextColor.YELLOW))
                .append(Component.text(" (", NamedTextColor.GREEN))
                .append(Component.text(chunks, NamedTextColor.YELLOW))
                .append(Component.text(" chunk" + (chunks == 1 ? "" : "s") + ") for ", NamedTextColor.GREEN))
                .append(Component.text(cost, NamedTextColor.YELLOW))
                .append(Component.text(" Resource Rupee" + (cost == 1 ? "" : "s") + ".", NamedTextColor.GREEN));
    }

    /** Preserves the green/yellow segmented successful unclaim refund confirmation. */
    public Component unclaimRefunded(String name, int refund, String recipient) {
        return Component.text("Unclaimed region ", NamedTextColor.GREEN)
                .append(Component.text("'" + name + "'", NamedTextColor.YELLOW))
                .append(Component.text(". Refunded ", NamedTextColor.GREEN))
                .append(Component.text(refund, NamedTextColor.YELLOW))
                .append(Component.text(" Resource Rupee" + (refund == 1 ? "" : "s") + " to ", NamedTextColor.GREEN))
                .append(Component.text(recipient, NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN));
    }

    /** Preserves the offline-refund warning's yellow values and red emphasis. */
    public Component unclaimOfflineRefund(String name, int refund) {
        return Component.text("Unclaimed region ", NamedTextColor.GREEN)
                .append(Component.text("'" + name + "'", NamedTextColor.YELLOW))
                .append(Component.text(". ", NamedTextColor.GREEN))
                .append(Component.text(refund, NamedTextColor.YELLOW))
                .append(Component.text(" Resource Rupee" + (refund == 1 ? "" : "s")
                        + " were owed, but the owner is offline — refund was ", NamedTextColor.GREEN))
                .append(Component.text("NOT", NamedTextColor.RED))
                .append(Component.text(" issued.", NamedTextColor.GREEN));
    }

    /** All literal player-visible protection templates, with their preserved root styling. */
    public enum Text {
        ENTRY_DENIED("You do not have permission to enter this area.", NamedTextColor.RED),
        SELECTION_POS1("Pos1", NamedTextColor.GRAY),
        SELECTION_POS2("Pos2", NamedTextColor.GRAY),
        SELECTION_POINT("%s set at chunk (%s, %s).", NamedTextColor.GREEN),
        SELECTION_COVERAGE("Selection covers %s chunk%s. Run /region claim <name> to commit.", NamedTextColor.GRAY),

        UNKNOWN_SUBCOMMAND("Unknown subcommand: %s", NamedTextColor.RED),
        COMMAND_PERMISSION("You don't have permission for /region %s.", NamedTextColor.RED),
        USAGE_HEADER("Usage:", NamedTextColor.YELLOW),
        REGION_COMMANDS_HEADER("Region commands:", NamedTextColor.YELLOW),
        NO_VISIBLE_REGION_COMMANDS("  (you don't have permission for any region subcommand)", NamedTextColor.DARK_GRAY),
        USAGE_CLAIM_SYNTAX("/region claim <name>", NamedTextColor.GRAY),
        USAGE_CLAIM_DESCRIPTION("Claim the chunks in your current wand selection as a new region.", NamedTextColor.GRAY),
        USAGE_UNCLAIM_SYNTAX("/region unclaim <name>", NamedTextColor.GRAY),
        USAGE_UNCLAIM_DESCRIPTION("Unclaim a region you own, refunding its full claim cost.", NamedTextColor.GRAY),
        USAGE_GUI_SYNTAX("/region gui [name]", NamedTextColor.GRAY),
        USAGE_GUI_DESCRIPTION("Open the management dialog for a region.", NamedTextColor.GRAY),
        USAGE_INFO_SYNTAX("/region info [name]", NamedTextColor.GRAY),
        USAGE_INFO_DESCRIPTION("Show region info here, or by name.", NamedTextColor.GRAY),
        USAGE_FLAGS_SYNTAX("/region flags [name]", NamedTextColor.GRAY),
        USAGE_FLAGS_DESCRIPTION("List flag rules on a region (defaults to your current location).", NamedTextColor.GRAY),
        USAGE_CLEAR_SYNTAX("/region clear", NamedTextColor.GRAY),
        USAGE_CLEAR_DESCRIPTION("Drop your active wand selection.", NamedTextColor.GRAY),
        USAGE_WAND_SYNTAX("/region wand", NamedTextColor.GRAY),
        USAGE_WAND_DESCRIPTION("Receive a region selection wand.", NamedTextColor.GRAY),
        USAGE_SELECT_SYNTAX("/region select <name>", NamedTextColor.GRAY),
        USAGE_SELECT_DESCRIPTION("Restore a region's outline onto your selection.", NamedTextColor.GRAY),
        USAGE_SET_PARENT_SYNTAX("/region setparent <child> <parent>", NamedTextColor.GRAY),
        USAGE_SET_PARENT_DESCRIPTION("Nest one region as a sub-region of another.", NamedTextColor.GRAY),
        USAGE_UNSET_PARENT_SYNTAX("/region unsetparent <child>", NamedTextColor.GRAY),
        USAGE_UNSET_PARENT_DESCRIPTION("Promote a sub-region back to top-level.", NamedTextColor.GRAY),
        USAGE_MEMBER_SYNTAX("/region %s <name> <player|group:<name>>", NamedTextColor.GRAY),
        USAGE_ADD_MEMBER_DESCRIPTION("Add a player or permission group as a member of a region.", NamedTextColor.GRAY),
        USAGE_REMOVE_MEMBER_DESCRIPTION("Remove a player or permission group from a region.", NamedTextColor.GRAY),
        USAGE_MANAGER_SYNTAX("/region %s <name> <player|group:<name>>", NamedTextColor.GRAY),
        USAGE_ADD_MANAGER_DESCRIPTION("Promote a player or permission group to manager on a region (owner-only).", NamedTextColor.GRAY),
        USAGE_REMOVE_MANAGER_DESCRIPTION("Demote a manager player or permission group (owner-only).", NamedTextColor.GRAY),
        USAGE_FLAG_SYNTAX("/region flag <name> <flag> [true|false|materials...]", NamedTextColor.GRAY),
        USAGE_FLAG_DESCRIPTION("Set a flag rule, or list rules when no value is given.", NamedTextColor.GRAY),
        USAGE_UNFLAG_SYNTAX("/region unflag <name> <flag> [target]", NamedTextColor.GRAY),
        USAGE_UNFLAG_DESCRIPTION("Remove a flag rule (clears material list for material flags).", NamedTextColor.GRAY),

        CLAIM_ONLY_PLAYERS("Only players can claim regions.", NamedTextColor.RED),
        CLAIM_EXISTS_WORLD("Region '%s' already exists in this world.", NamedTextColor.RED),
        CLAIM_SELECTION_REQUIRED("Set Pos1 (left-click) and Pos2 (right-click) on chunk corners with your wand first.", NamedTextColor.RED),
        CLAIM_SELECTION_OTHER_WORLD("Your selection is in a different world. Re-select in this world.", NamedTextColor.RED),
        CLAIM_LIMIT("You cannot claim that many chunks. You currently own %s of %s; this claim would add %s more.", NamedTextColor.RED),
        CLAIM_AFFORD("You can't afford this claim. Cost: %s Resource Rupees; you have %s.", NamedTextColor.RED),
        CLAIM_EXISTS("Region '%s' already exists.", NamedTextColor.RED),
        CLAIM_OVERLAPS_FOREIGN("Your selection overlaps a region you don't own. Adjust pos1/pos2 or ask the owner to add you.", NamedTextColor.RED),
        CLAIM_UNEXPECTED("An unexpected error occurred while claiming this region.", NamedTextColor.RED),
        CLAIM_ADMIN_SUCCESS("Claimed region '%s' (%s chunk%s, free admin claim).", NamedTextColor.GREEN),
        CLAIM_SUCCESS("Claimed region '%s' (%s chunk%s) for %s Resource Rupee%s.", NamedTextColor.GREEN),

        GLOBAL_UNCLAIM_DENIED("The wilderness/global region cannot be unclaimed.", NamedTextColor.RED),
        REGION_NOT_FOUND("No region named '%s'.", NamedTextColor.RED),
        UNCLAIM_OWNER_ONLY("Only the region owner can unclaim '%s'.", NamedTextColor.RED),
        UNCLAIM_REFUNDED("Unclaimed region '%s'. Refunded %s Resource Rupee%s to %s.", NamedTextColor.GREEN),
        UNCLAIM_OFFLINE_REFUND("Unclaimed region '%s'. %s Resource Rupee%s were owed, but the owner is offline — refund was NOT issued.", NamedTextColor.GREEN),
        UNCLAIM_SUCCESS("Unclaimed region '%s'. Pointer cleanup will run lazily.", NamedTextColor.GREEN),

        GUI_CONSOLE_NAME_REQUIRED("Console must supply a region name: /region gui <name>.", NamedTextColor.RED),
        GUI_ONLY_PLAYERS("Only players can open the region dialog.", NamedTextColor.RED),
        GUI_WILDERNESS("You are standing in wilderness — stand inside a region, or run /region gui <name>.", NamedTextColor.YELLOW),
        GUI_OPEN_AUTH("Only the region owner, a manager, or an admin can open '%s'.", NamedTextColor.RED),

        CLEAR_ONLY_PLAYERS("Only players have selections to clear.", NamedTextColor.RED),
        CLEAR_NONE("You have no active selection.", NamedTextColor.YELLOW),
        CLEAR_SUCCESS("Cleared your selection.", NamedTextColor.GREEN),
        WAND_ONLY_PLAYERS("Only players can receive a wand.", NamedTextColor.RED),
        WAND_FULL("Inventory full — wand dropped at your feet.", NamedTextColor.YELLOW),
        WAND_SUCCESS("Received a region selection wand. Left-click = Pos1, right-click = Pos2.", NamedTextColor.GREEN),
        WAND_NAME("Region Selection Wand", NamedTextColor.AQUA),
        SELECT_ONLY_PLAYERS("Only players can hold a selection.", NamedTextColor.RED),
        SELECT_AUTH("Only the region owner or admins can select '%s'.", NamedTextColor.RED),
        SELECT_NO_BOUNDS("Region '%s' was claimed before bounds were tracked. Unclaim and re-claim it to refresh.", NamedTextColor.RED),
        SELECT_SUCCESS("Selected '%s' (%s chunk%s). Outline shown in your current world.", NamedTextColor.GREEN),

        PARENT_EDIT_AUTH("Only the region owner or an admin can edit sub-region hierarchy.", NamedTextColor.RED),
        PARENT_TOP_LEVEL("Region '%s' is now top-level.", NamedTextColor.GREEN),
        PARENT_SUCCESS("Region '%s' is now a sub-region of '%s'.", NamedTextColor.GREEN),
        PARENT_NO_CHANGE("No change — region already has that parent.", NamedTextColor.YELLOW),
        PARENT_UNKNOWN("Unknown parent region '%s'.", NamedTextColor.RED),
        PARENT_SELF("A region cannot be its own parent.", NamedTextColor.RED),
        PARENT_CYCLE("Refused — that would create a parent cycle.", NamedTextColor.RED),
        PARENT_OTHER_WORLD("Refused — child and parent are in different worlds.", NamedTextColor.RED),
        PARENT_NOT_CONTAINED("Refused — child region is not fully contained inside its parent's bounds.", NamedTextColor.RED),
        PARENT_SIBLING_OVERLAP("Refused — child would overlap another sub-region of the same parent.", NamedTextColor.RED),

        INFO_CONSOLE_NAME_REQUIRED("Console must supply a region name: /region info <name>.", NamedTextColor.RED),
        INFO_WILDERNESS("You are standing in wilderness — no region claimed here.", NamedTextColor.YELLOW),
        INFO_AT_LOCATION("Region%s at your location:", NamedTextColor.AQUA),
        FLAGS_CONSOLE_NAME_REQUIRED("Console must supply a region name: /region flags <name>.", NamedTextColor.RED),
        FLAGS_WILDERNESS("You are standing in wilderness — no region to list flags for.", NamedTextColor.YELLOW),
        FLAG_RULES_HEADER("Flag rules for '%s':", NamedTextColor.AQUA),
        MATERIAL_LISTS_HEADER("Material lists for '%s':", NamedTextColor.AQUA),
        ENTITY_LISTS_HEADER("Entity lists for '%s':", NamedTextColor.AQUA),
        NO_FLAG_RULES("Region '%s' has no flag rules.", NamedTextColor.YELLOW),
        REGION_BULLET("• %s", NamedTextColor.GREEN),
        REGION_PARENT("    parent: %s", NamedTextColor.GRAY),
        REGION_OWNER("    owner: %s", NamedTextColor.GRAY),
        REGION_MANAGERS("    managers: %s", NamedTextColor.GRAY),
        REGION_MEMBERS_NONE("    members: (none)", NamedTextColor.GRAY),
        REGION_MEMBERS("    members: %s", NamedTextColor.GRAY),
        REGION_FLAGS_NONE("    flags: (none)", NamedTextColor.GRAY),
        REGION_FLAGS("    flags:", NamedTextColor.GRAY),
        REGION_MATERIAL_FLAGS("    material flags:", NamedTextColor.GRAY),
        REGION_ENTITY_FLAGS("    entity flags:", NamedTextColor.GRAY),
        GROUP_LABEL("group:%s", NamedTextColor.GRAY),
        INDENT("  ", NamedTextColor.GRAY),
        DEEP_INDENT("      ", NamedTextColor.GRAY),

        MEMBER_EDIT_AUTH("Only the region owner, a manager, or an admin can edit members.", NamedTextColor.RED),
        MANAGER_EDIT_AUTH("Only the region owner can edit the manager set.", NamedTextColor.RED),
        GROUP_NAME_REQUIRED("Group name cannot be empty. Usage: group:<name>", NamedTextColor.RED),
        GROUP_MISSING_WARNING("Note: permission group '%s' does not exist yet — adding anyway; create the group first if this was unintentional.", NamedTextColor.YELLOW),
        MEMBER_GROUP_DEFAULT_WARNING("Warning: 'default' is the implicit group every player without explicit group assignments belongs to — this grants MEMBER access to '%s' to virtually all players on the server.", NamedTextColor.YELLOW),
        MANAGER_GROUP_DEFAULT_WARNING("Warning: 'default' is the implicit group every player without explicit group assignments belongs to — this grants MANAGER access (flag editing, membership control) on '%s' to virtually all players on the server.", NamedTextColor.YELLOW),
        MEMBER_GROUP_DUPLICATE("Group '%s' is already a member of '%s'.", NamedTextColor.RED),
        MEMBER_GROUP_MISSING("Group '%s' is not a member of '%s'.", NamedTextColor.RED),
        MEMBER_GROUP_ADD("Added group '%s' as a member of '%s'.", NamedTextColor.GREEN),
        MEMBER_GROUP_REMOVE("Removed group '%s' from '%s'.", NamedTextColor.GREEN),
        UNKNOWN_PLAYER("Unknown player '%s'.", NamedTextColor.RED),
        MEMBER_ADD_FAILED("Could not add — region missing or player already a member.", NamedTextColor.RED),
        MEMBER_REMOVE_FAILED("Could not remove — region missing or player not a member.", NamedTextColor.RED),
        MEMBER_SUCCESS("%s %s %s '%s'.", NamedTextColor.GREEN),
        MANAGER_GROUP_DUPLICATE("Group '%s' is already a manager of '%s'.", NamedTextColor.RED),
        MANAGER_GROUP_MISSING("Group '%s' is not a manager of '%s'.", NamedTextColor.RED),
        MANAGER_GROUP_ADD("Added group '%s' as a manager of '%s'.", NamedTextColor.GREEN),
        MANAGER_GROUP_REMOVE("Removed group '%s' from managers of '%s'.", NamedTextColor.GREEN),
        OWNER_MANAGER("The owner is implicitly a manager — promotion is unnecessary.", NamedTextColor.YELLOW),
        MANAGER_ADD_FAILED("Could not add — player is already a manager.", NamedTextColor.RED),
        MANAGER_REMOVE_FAILED("Could not remove — player is not a manager.", NamedTextColor.RED),
        MANAGER_SUCCESS("%s %s %s '%s'.", NamedTextColor.GREEN),

        FLAG_EDIT_AUTH("Only the region owner, a manager, or an admin can edit flags.", NamedTextColor.RED),
        FLAG_MISSING_VALUE("Missing value. For boolean flags use 'true|false [target]'; for material flags supply a space-separated list of block names.", NamedTextColor.RED),
        MATERIAL_TARGET_WARNING("'%s' is a material-list flag; targets do not apply. Use /region unflag %s %s to clear the list.", NamedTextColor.YELLOW),
        ENTITY_TARGET_WARNING("'%s' is an entity-list flag; targets do not apply. Use /region unflag %s %s to clear the list.", NamedTextColor.YELLOW),
        FLAG_LIST_EMPTY("No change — '%s' list is already empty.", NamedTextColor.YELLOW),
        MATERIAL_LIST_CLEARED("Cleared '%s' %s material list.", NamedTextColor.GREEN),
        ENTITY_LIST_CLEARED("Cleared '%s' %s entity list.", NamedTextColor.GREEN),
        FLAG_REMOVE_MISSING("No rule to remove for flag %s [%s].", NamedTextColor.RED),
        FLAG_REMOVED("Removed rule on '%s' for flag %s [%s].", NamedTextColor.GREEN),
        FLAG_NO_MATERIALS("'%s' has no %s materials.", NamedTextColor.YELLOW),
        FLAG_NO_ENTITIES("'%s' has no %s entities.", NamedTextColor.YELLOW),
        FLAG_LIST_HEADER("'%s' %s (%s):", NamedTextColor.AQUA),
        BULLET("  • %s", NamedTextColor.GRAY),
        FLAG_NO_RULES("'%s' has no rules for %s.", NamedTextColor.YELLOW),
        FLAG_HEADER("'%s' %s:", NamedTextColor.AQUA),
        MATERIAL_REQUIRED("Supply at least one block material name.", NamedTextColor.RED),
        MATERIAL_NO_CHANGE("No change — material list already matches the requested set.", NamedTextColor.YELLOW),
        MATERIAL_SET("Set '%s' %s to %s material%s.", NamedTextColor.GREEN),
        ENTITY_REQUIRED("Supply at least one entity type name.", NamedTextColor.RED),
        ENTITY_NO_CHANGE("No change — entity list already matches the requested set.", NamedTextColor.YELLOW),
        ENTITY_SET("Set '%s' %s to %s entity type%s.", NamedTextColor.GREEN),
        BOOLEAN_EXPECTED("'%s' is a boolean flag; expected 'true' or 'false', got '%s'.", NamedTextColor.RED),
        BOOLEAN_TOO_MANY("Too many arguments. Usage: /region flag <name> %s true|false [target]", NamedTextColor.RED),
        BOOLEAN_NO_CHANGE("No change — '%s' %s [%s] is already %s. Run /region flag %s %s (no value) to view all current rules.", NamedTextColor.YELLOW),
        BOOLEAN_SET("Region '%s' flag %s [%s] set to %s.", NamedTextColor.GREEN),
        UNKNOWN_FLAG("Unknown flag '%s'.", NamedTextColor.RED),
        UNKNOWN_MATERIAL("Unknown material '%s'.", NamedTextColor.RED),
        NOT_BLOCK_MATERIAL("'%s' is not a block material.", NamedTextColor.RED),
        UNKNOWN_ENTITY("Unknown entity type '%s'.", NamedTextColor.RED),
        UNKNOWN_GROUP("Unknown permission group '%s'. Use 'owner', 'member', or a registered group name.", NamedTextColor.RED),

        REGION_GONE("Region no longer exists.", NamedTextColor.RED),
        UNKNOWN_NAME("<unknown>", NamedTextColor.GRAY),
        GUI_EDIT_FLAGS("Edit Flags", NamedTextColor.GREEN, true),
        GUI_EDIT_FLAGS_TIP("Manage boolean, material, and entity flags.", NamedTextColor.GRAY),
        GUI_EDIT_MEMBERS("Edit Members", NamedTextColor.AQUA, true),
        GUI_EDIT_MEMBERS_TIP("Add or remove region members.", NamedTextColor.GRAY),
        GUI_EDIT_MANAGERS("Edit Managers", NamedTextColor.AQUA, true),
        GUI_EDIT_MANAGERS_TIP("Promote or demote managers (owner only).", NamedTextColor.GRAY),
        GUI_SUBREGIONS("Manage Sub-regions", NamedTextColor.YELLOW, true),
        GUI_SUBREGIONS_TIP("Set or clear this region's parent.", NamedTextColor.GRAY),
        GUI_UNCLAIM("Unclaim", NamedTextColor.RED, true),
        GUI_UNCLAIM_TIP("Delete this region. Cannot be undone.", NamedTextColor.RED),
        GUI_REGION_TITLE("Region: %s", NamedTextColor.GREEN),
        GUI_REGION_BODY("Manage settings for this region.", NamedTextColor.GRAY),
        GUI_BACK_REGION("← Back to Region", NamedTextColor.RED, true),
        GUI_BACK_REGION_TIP("Return to the region dashboard.", NamedTextColor.GRAY),
        GUI_FLAGS_TITLE("Flags: %s", NamedTextColor.GREEN),
        GUI_FLAG_SUMMARY("%s %s", NamedTextColor.GRAY),
        GUI_FLAG_SUMMARY_ACTIVE("%s %s", NamedTextColor.YELLOW),
        GUI_FLAG_TIP("%s — click to edit.", NamedTextColor.GRAY),
        GUI_BACK_FLAGS("← Back to Flags", NamedTextColor.RED, true),
        GUI_BACK_FLAGS_TIP("Return to the flag list.", NamedTextColor.GRAY),
        GUI_FLAG_DETAIL_TITLE("%s: %s", NamedTextColor.GREEN),
        GUI_BOOLEAN_BODY("Pick an audience to set or cycle this flag.", NamedTextColor.GRAY),
        GUI_BOOLEAN_UNSET("○ %s — unset", NamedTextColor.GRAY, true),
        GUI_BOOLEAN_TRUE("✓ %s — true", NamedTextColor.GREEN, true),
        GUI_BOOLEAN_FALSE("✗ %s — false", NamedTextColor.RED, true),
        GUI_BOOLEAN_TIP("Click to cycle Unset → True → False → Unset.", NamedTextColor.GRAY),
        GUI_RULE_NO_CHANGE("No change — rule already at requested value.", NamedTextColor.YELLOW),
        GUI_REMOVE_ENTRY("✗ %s", NamedTextColor.RED, true),
        GUI_REMOVE_LIST_TIP("Click to remove from the list.", NamedTextColor.GRAY),
        GUI_ADD_MATERIAL("+ Add Material", NamedTextColor.YELLOW, true),
        GUI_ADD_MATERIAL_TIP("Enter a block material name.", NamedTextColor.GRAY),
        GUI_ADD_MATERIAL_TITLE("Add Material", NamedTextColor.GREEN, true),
        GUI_ADD_MATERIAL_PROMPT("Enter a block material name (e.g. STONE, OAK_LOG).", NamedTextColor.GRAY),
        GUI_INPUT_VALUE("Value", NamedTextColor.YELLOW),
        GUI_INPUT_PLAYER_NAME("Player Name", NamedTextColor.YELLOW),
        GUI_APPLY_VALUE_TIP("Apply with the entered value.", NamedTextColor.GRAY),
        GUI_APPLY_NAME_TIP("Apply with the entered name.", NamedTextColor.GRAY),
        GUI_INVALID_MATERIAL("Invalid material name.", NamedTextColor.RED),
        GUI_UNKNOWN_BLOCK("Unknown or non-block material '%s'.", NamedTextColor.RED),
        GUI_MATERIAL_EXISTS("Material already in list.", NamedTextColor.YELLOW),
        GUI_MATERIAL_NOT_PRESENT("No change â€” material not in list.", NamedTextColor.YELLOW),
        GUI_MATERIAL_REMOVED("Removed %s from %s.", NamedTextColor.GREEN),
        GUI_MATERIAL_ADDED("Added %s to %s.", NamedTextColor.GREEN),
        GUI_ADD_ENTITY("+ Add Entity", NamedTextColor.YELLOW, true),
        GUI_ADD_ENTITY_TIP("Enter an entity type name.", NamedTextColor.GRAY),
        GUI_ADD_ENTITY_TITLE("Add Entity", NamedTextColor.GREEN, true),
        GUI_ADD_ENTITY_PROMPT("Enter an entity type name (e.g. ZOMBIE, ENDERMAN).", NamedTextColor.GRAY),
        GUI_INVALID_ENTITY("Invalid entity type.", NamedTextColor.RED),
        GUI_ENTITY_EXISTS("Entity already in list.", NamedTextColor.YELLOW),
        GUI_ENTITY_NOT_PRESENT("No change â€” entity not in list.", NamedTextColor.YELLOW),
        GUI_ENTITY_REMOVED("Removed %s from %s.", NamedTextColor.GREEN),
        GUI_ENTITY_ADDED("Added %s to %s.", NamedTextColor.GREEN),
        GUI_MEMBER_REMOVE("✗ %s", NamedTextColor.RED, true),
        GUI_MEMBER_REMOVE_TIP("Click to remove from this region.", NamedTextColor.GRAY),
        GUI_GROUP_REMOVE("✗ group:%s", NamedTextColor.RED, true),
        GUI_GROUP_REMOVE_TIP("Click to remove this group from this region.", NamedTextColor.GRAY),
        GUI_ADD_MEMBER("+ Add Member", NamedTextColor.YELLOW, true),
        GUI_ADD_MEMBER_TIP("Open the player-name or group entry dialog.", NamedTextColor.GRAY),
        GUI_MEMBERS_TITLE("Members: %s", NamedTextColor.GREEN),
        GUI_MEMBER_NOT_PRESENT("Could not remove — player is no longer a member.", NamedTextColor.YELLOW),
        GUI_MEMBER_GROUP_NOT_PRESENT("Could not remove — group '%s' is no longer a member.", NamedTextColor.YELLOW),
        GUI_MEMBER_REMOVED("Removed %s from '%s'.", NamedTextColor.GREEN),
        GUI_MEMBER_GROUP_REMOVED("Removed group '%s' from '%s'.", NamedTextColor.GREEN),
        GUI_ADD_MEMBER_TITLE("Add Member", NamedTextColor.GREEN, true),
        GUI_ADD_MEMBER_PROMPT("Enter a player name or group:<name> to add to '%s'.", NamedTextColor.GRAY),
        GUI_GROUP_NAME_REQUIRED("Group name cannot be empty. Use group:<name>.", NamedTextColor.RED),
        GUI_MEMBER_GROUP_EXISTS("Could not add — group '%s' is already a member.", NamedTextColor.YELLOW),
        GUI_MEMBER_GROUP_ADDED("Added group '%s' to '%s'.", NamedTextColor.GREEN),
        GUI_OWNER_MEMBER("The owner is implicitly a member.", NamedTextColor.YELLOW),
        GUI_MEMBER_EXISTS("Could not add — player is already a member.", NamedTextColor.YELLOW),
        GUI_MEMBER_ADDED("Added %s to '%s'.", NamedTextColor.GREEN),
        GUI_MANAGER_REMOVE_TIP("Click to demote from manager.", NamedTextColor.GRAY),
        GUI_MANAGER_GROUP_REMOVE_TIP("Click to remove this group from managers.", NamedTextColor.GRAY),
        GUI_ADD_MANAGER("+ Add Manager", NamedTextColor.YELLOW, true),
        GUI_MANAGERS_TITLE("Managers: %s", NamedTextColor.GREEN),
        GUI_MANAGER_NOT_PRESENT("Could not demote — player is no longer a manager.", NamedTextColor.YELLOW),
        GUI_MANAGER_GROUP_NOT_PRESENT("Could not demote — group '%s' is no longer a manager.", NamedTextColor.YELLOW),
        GUI_MANAGER_DEMOTED("Demoted %s on '%s'.", NamedTextColor.GREEN),
        GUI_MANAGER_GROUP_REMOVED("Removed group '%s' from managers of '%s'.", NamedTextColor.GREEN),
        GUI_ADD_MANAGER_TITLE("Add Manager", NamedTextColor.GREEN, true),
        GUI_ADD_MANAGER_PROMPT("Enter a player name or group:<name> to promote to manager on '%s'.", NamedTextColor.GRAY),
        GUI_MANAGER_GROUP_EXISTS("Could not promote — group '%s' is already a manager.", NamedTextColor.YELLOW),
        GUI_MANAGER_GROUP_ADDED("Added group '%s' as a manager of '%s'.", NamedTextColor.GREEN),
        GUI_MANAGER_EXISTS("Could not promote — player is already a manager.", NamedTextColor.YELLOW),
        GUI_MANAGER_PROMOTED("Promoted %s to manager on '%s'.", NamedTextColor.GREEN),
        GUI_INVALID_PLAYER("Invalid player name.", NamedTextColor.RED),
        GUI_PLAYER_NEVER_PLAYED("Player '%s' has never played before.", NamedTextColor.RED),
        GUI_SUBREGION_AUTH("Only the region owner can manage sub-region hierarchy.", NamedTextColor.RED),
        GUI_UNSET_PARENT("✗ Unset Parent", NamedTextColor.RED, true),
        GUI_UNSET_PARENT_TIP("Promote '%s' back to a top-level region.", NamedTextColor.GRAY),
        GUI_CHANGE_PARENT("Change Parent", NamedTextColor.YELLOW, true),
        GUI_SET_PARENT("Set Parent", NamedTextColor.YELLOW, true),
        GUI_SET_PARENT_TIP("Enter the id of the parent region.", NamedTextColor.GRAY),
        GUI_SUBREGIONS_TITLE("Sub-regions: %s", NamedTextColor.GREEN),
        GUI_CURRENT_PARENT("Current parent: %s", NamedTextColor.GRAY),
        GUI_NO_PARENT("(none)", NamedTextColor.GRAY),
        GUI_SET_PARENT_TITLE("Set Parent", NamedTextColor.GREEN, true),
        GUI_SET_PARENT_PROMPT("Enter the id of the region to nest '%s' under.", NamedTextColor.GRAY),
        GUI_PARENT_EMPTY("Parent id cannot be empty.", NamedTextColor.RED),
        GUI_UNCLAIM_AUTH("Only the region owner can unclaim this region.", NamedTextColor.RED),
        GUI_UNCLAIM_YES("Yes, Unclaim", NamedTextColor.RED, true),
        GUI_UNCLAIM_YES_TIP("Delete this region permanently.", NamedTextColor.RED),
        GUI_UNCLAIM_NO("No, Keep", NamedTextColor.GREEN, true),
        GUI_RETURN_NO_CHANGES("Return without changes.", NamedTextColor.GRAY),
        GUI_UNCLAIM_TITLE("Unclaim '%s'?", NamedTextColor.RED, true),
        GUI_UNCLAIM_BODY("Are you absolutely sure you want to unclaim this region? This cannot be undone.", NamedTextColor.GRAY),
        GUI_PREV_PAGE("◀ Prev Page", NamedTextColor.GREEN, true),
        GUI_NEXT_PAGE("Next Page ▶", NamedTextColor.GREEN, true),
        GUI_PAGE("Page %s of %s", NamedTextColor.GRAY),
        GUI_PAGE_SUMMARY("%s %s — Page %s of %s", NamedTextColor.GRAY),
        GUI_WORD_FLAG("flag", NamedTextColor.GRAY),
        GUI_WORD_FLAGS("flags", NamedTextColor.GRAY),
        GUI_WORD_MATERIAL("material", NamedTextColor.GRAY),
        GUI_WORD_MATERIALS("materials", NamedTextColor.GRAY),
        GUI_WORD_ENTITY_TYPE("entity type", NamedTextColor.GRAY),
        GUI_WORD_ENTITY_TYPES("entity types", NamedTextColor.GRAY),
        GUI_WORD_RULE("rule", NamedTextColor.GRAY),
        GUI_WORD_RULES("rules", NamedTextColor.GRAY),
        GUI_WORD_MEMBER("member", NamedTextColor.GRAY),
        GUI_WORD_MEMBERS("members", NamedTextColor.GRAY),
        GUI_WORD_MANAGER("manager", NamedTextColor.GRAY),
        GUI_WORD_MANAGERS("managers", NamedTextColor.GRAY),
        GUI_ADD("Add", NamedTextColor.GREEN, true),
        GUI_CANCEL("Cancel", NamedTextColor.RED, true);

        private final String template;
        private final NamedTextColor color;
        private final boolean bold;

        Text(String template, NamedTextColor color) {
            this(template, color, false);
        }

        Text(String template, NamedTextColor color, boolean bold) {
            this.template = template;
            this.color = color;
            this.bold = bold;
        }

        String format(Object... arguments) {
            return template.formatted(arguments);
        }

        NamedTextColor color() {
            return color;
        }

        boolean bold() {
            return bold;
        }
    }
}
