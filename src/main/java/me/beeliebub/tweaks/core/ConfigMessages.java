package me.beeliebub.tweaks.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;

/**
 * Player-facing command and dialog copy for the {@code /tconfig} generic settings engine and its
 * Dialog GUI. Callers access this registry through {@link Messages#CONFIG}.
 *
 * <p>The six pre-existing legacy {@code /tconfig} forms keep their responses in
 * {@link CommandMessages} untouched - this class covers the generic settings engine and the GUI's
 * own copy.
 */
public final class ConfigMessages {

    ConfigMessages() {}

    // ---------------------------------------------------------------- Commands

    public Component guiRequiresPlayer() { return red("Only players can use the GUI."); }

    /** Appended after {@code CommandMessages#configUsage} - the new gui/list/generic-path forms. */
    public List<Component> additionalUsageLines(String label) {
        return List.of(
                Component.text("  /" + label + " gui", NamedTextColor.RED),
                Component.text("  /" + label + " list [category]", NamedTextColor.RED),
                Component.text("  /" + label + " <path> <value>", NamedTextColor.RED));
    }

    public Component categoryHeader(String displayName) {
        return Component.text("--- " + displayName + " ---", NamedTextColor.GOLD);
    }

    public Component settingListEntry(String displayName, String currentValue) {
        return Component.text("  " + displayName + ": ", NamedTextColor.YELLOW)
                .append(Component.text(currentValue, NamedTextColor.WHITE));
    }

    public Component unknownCategory(String key) {
        return Component.text("Unknown config category: " + key, NamedTextColor.RED);
    }

    public Component unknownSetting(String path) {
        return Component.text("Unknown config setting: " + path, NamedTextColor.RED);
    }

    /** Sent when a mutation throws an unexpected exception - see ConfigValueEditor#guarded. */
    public Component unexpectedError() {
        return Component.text("Something went wrong applying that change; see the server console.", NamedTextColor.RED);
    }

    public Component saveFailed(String displayName) {
        return Component.text(displayName + " could not be saved; the change was not applied.", NamedTextColor.RED);
    }

    public Component invalidMapKey(String input) {
        return Component.text("Invalid key '" + input + "' - keys cannot be empty or contain '.'.", NamedTextColor.RED);
    }

    public Component scalarUsage(String label, String path) {
        return Component.text("Usage: /" + label + " " + path + " <value>", NamedTextColor.RED);
    }

    public Component listUsage(String label, String path) {
        return Component.text("Usage: /" + label + " " + path + " <add|remove> <value>", NamedTextColor.RED);
    }

    public Component mapUsage(String label, String path) {
        return Component.text("Usage: /" + label + " " + path + " <key> <value>", NamedTextColor.RED);
    }

    public Component gridUsage(String label, String path) {
        return Component.text("Usage: /" + label + " " + path + " <cell 0-8> <material|air>", NamedTextColor.RED);
    }

    public Component notAScalarSetting(String displayName) {
        return Component.text(displayName + " is not a single-value setting.", NamedTextColor.RED);
    }

    public Component notAMapSetting(String displayName) {
        return Component.text(displayName + " is not a map setting.", NamedTextColor.RED);
    }

    public Component notAListSetting(String displayName) {
        return Component.text(displayName + " is not a list setting.", NamedTextColor.RED);
    }

    public Component gridCellRange() {
        return Component.text("Recipe cell must be an index from 0 through 8.", NamedTextColor.RED);
    }

    public Component recipeRejected() {
        return Component.text("That recipe is invalid or collides with an existing recipe; the old recipe was kept.",
                NamedTextColor.RED);
    }

    public Component gridCellUpdated(int index, String material) {
        return Component.text("Recipe cell " + index + " updated to " + material + ".", NamedTextColor.GREEN);
    }

    public Component outOfRange(String displayName, Double min, Double max) {
        String range = (min != null ? formatBound(min) : "-∞") + " to " + (max != null ? formatBound(max) : "∞");
        return Component.text(displayName + " must be between " + range + ".", NamedTextColor.RED);
    }

    private static String formatBound(double bound) {
        return bound == Math.floor(bound) && !Double.isInfinite(bound) ? String.valueOf((long) bound) : String.valueOf(bound);
    }

    public Component invalidBoolean() {
        return Component.text("Value must be 'true' or 'false'.", NamedTextColor.RED);
    }

    public Component invalidNamespacedKey(String input) {
        return Component.text("Invalid namespaced key: " + input, NamedTextColor.RED);
    }

    public Component updated(String displayName, String newValue) {
        return Component.text(displayName + " updated to " + newValue + ".", NamedTextColor.GREEN);
    }

    public Component listEntryAdded(String displayName, String value, boolean changed) {
        return Component.text((changed ? "Added " : "Already present: ") + value + " (" + displayName + ").",
                changed ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
    }

    public Component listEntryRemoved(String displayName, String value, boolean changed) {
        return Component.text((changed ? "Removed " : "Not present: ") + value + " (" + displayName + ").",
                changed ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
    }

    public Component mapEntryUpdated(String displayName, String key, double value) {
        return Component.text(displayName + "[" + key + "] set to " + value + ".", NamedTextColor.GREEN);
    }

    // ---------------------------------------------------------------- world-profiles (CLI)

    public Component worldProfileUsage(String label) {
        return Component.text("Usage: /" + label
                + " world-profiles <list|add|remove|edit> ...", NamedTextColor.RED);
    }

    public Component worldProfileAddUsage(String label) {
        return Component.text("Usage: /" + label
                + " world-profiles add <world-key> <profile> <label> <color>", NamedTextColor.RED);
    }

    public Component worldProfileRemoveUsage(String label) {
        return Component.text("Usage: /" + label + " world-profiles remove <world-key>", NamedTextColor.RED);
    }

    public Component worldProfileEditUsage(String label) {
        return Component.text("Usage: /" + label
                + " world-profiles edit <world-key> <label> <color>", NamedTextColor.RED);
    }

    public Component worldProfileListEmpty() {
        return Component.text("No world-profile entries configured - every world resolves to the fallback.", NamedTextColor.YELLOW);
    }

    public Component worldProfileListEntry(String display) {
        return Component.text("  " + display, NamedTextColor.WHITE);
    }

    public Component worldProfileInvalidWorldKey() {
        return Component.text("A world key is required.", NamedTextColor.RED);
    }

    public Component worldProfileDuplicateKey(String worldKey) {
        return Component.text("World key '" + worldKey + "' is already mapped.", NamedTextColor.RED);
    }

    public Component worldProfileCollidesWith(String newKey, String existingKey) {
        return Component.text("World key '" + newKey + "' collides with existing key '" + existingKey
                + "' (one contains the other).", NamedTextColor.RED);
    }

    public Component worldProfileInvalidProfile() {
        return Component.text("Profile must be non-empty and cannot contain '.'.", NamedTextColor.RED);
    }

    public Component worldProfileInvalidLabel() {
        return Component.text("Label must be non-empty and cannot contain '[', ']', or '.'.", NamedTextColor.RED);
    }

    public Component worldProfileInvalidColor(String input) {
        return Component.text("Unknown color '" + input + "'.", NamedTextColor.RED);
    }

    public Component worldProfileAdded(String worldKey, String profile, String label, String color) {
        return Component.text("Added '" + worldKey + "' -> profile '" + profile + "', tag [" + label
                + "] (" + color + ").", NamedTextColor.GREEN);
    }

    public Component worldProfileRemoved(String worldKey) {
        return Component.text("Removed '" + worldKey + "'.", NamedTextColor.GREEN);
    }

    public Component worldProfileUpdated(String worldKey) {
        return Component.text("Updated '" + worldKey + "'.", NamedTextColor.GREEN);
    }

    public Component worldProfileNotFound(String worldKey) {
        return Component.text("World key '" + worldKey + "' is not mapped.", NamedTextColor.RED);
    }

    // ---------------------------------------------------------------- world-profiles (Dialog)

    public Component worldProfileCategoryLabel() { return bold("World Profiles", NamedTextColor.YELLOW); }
    public Component worldProfileCategoryTooltip() { return gray("Manage world-key -> profile/tag/color mappings."); }
    public Component worldProfileListTitle() { return title("<!italic><green><bold>World Profiles"); }
    public Component worldProfileEntryLabel(String display) { return bold(display, NamedTextColor.YELLOW); }
    public Component worldProfileEntryTooltip() { return gray("Click to edit or remove."); }
    public Component worldProfileAddTooltip() { return gray("Open the add-world-key dialog."); }

    // Built via Component.text, NOT title(...)/MM.deserialize - worldKey is admin-supplied and
    // must never be parsed as MiniMessage, or an unsanitized worldKey fed into MM.deserialize could
    // embed a live click/hover event into a Dialog title rendered for every other ADMIN_CONFIG
    // holder who opens this entry.
    public Component worldProfileEntryTitle(String worldKey) {
        return Component.text(worldKey, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false);
    }
    public Component worldProfileEntryBody(String display) { return plainGray(display); }
    public Component worldProfileEditButtonLabel() { return bold("Edit Tag/Color", NamedTextColor.YELLOW); }
    public Component worldProfileEditButtonTooltip() { return gray("Change this entry's tag and color."); }
    public Component worldProfileRemoveButtonLabel() { return bold("✗ Remove", NamedTextColor.RED); }
    public Component worldProfileRemoveButtonTooltip() { return gray("Remove this world-key mapping immediately."); }

    // Same MiniMessage-injection avoidance as worldProfileEntryTitle above.
    public Component worldProfileEditTitle(String worldKey) {
        return Component.text("Edit: " + worldKey, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false);
    }
    public Component worldProfileLabelInputLabel() { return plainYellow("Label (e.g. Lobby)"); }
    public Component worldProfileColorInputLabel() { return plainYellow("Color (e.g. AQUA)"); }

    public Component worldProfileAddTitle() { return title("<!italic><green>Add World Key"); }
    public Component worldProfileAddBody() { return plainGray("Enter the world key, profile, label, and color."); }
    public Component worldProfileWorldKeyInputLabel() { return plainYellow("World Key (e.g. jass:lobby)"); }
    public Component worldProfileProfileInputLabel() { return plainYellow("Profile (e.g. lobby)"); }

    // ------------------------------------------------------------- Dialog copy

    public Component mainTitle() { return title("<!italic><green><bold>Config"); }

    public Component mainBody() {
        return lines(
                plainGray("Select a category to manage."),
                plainGray("Ranks: /ranks edit  ·  Permissions: /tprm  ·  Whack: /whack"));
    }

    public Component loggingCategoryLabel() { return bold("Logging", NamedTextColor.YELLOW); }
    public Component loggingCategoryTooltip() { return gray("Manage console event logging switches."); }
    public Component loggingTitle() { return title("<!italic><green><bold>Logging"); }
    public Component loggingBody() { return plainGray("Select a logging category to edit."); }

    public Component categoryLabel(String displayName) { return bold(displayName, NamedTextColor.YELLOW); }
    public Component categoryTooltip(int settingCount) { return gray(settingCount + " setting(s). Click to view."); }
    public Component backLabel() { return bold("← Back", NamedTextColor.RED); }
    public Component backToMainTooltip() { return gray("Return to the main config menu."); }
    public Component backToLoggingTooltip() { return gray("Return to the logging categories."); }
    public Component categoryTitle(String displayName) { return title("<!italic><green>" + displayName); }
    public Component categoryBody() { return plainGray("Select a setting to edit."); }

    public Component settingLabel(String displayName) { return bold(displayName, NamedTextColor.YELLOW); }
    public Component settingTooltip(String currentValue) { return gray("Current: " + currentValue); }
    public Component backToCategoryLabel() { return bold("← Back", NamedTextColor.RED); }

    public Component editTitle(String displayName) { return title("<!italic><green>Edit: " + displayName); }
    public Component editBody(String currentValue) {
        return plainGray("Current value: " + currentValue + ". Enter a new value below.");
    }
    public Component valueInputLabel() { return plainYellow("Value"); }
    public Component saveLabel() { return bold("Save", NamedTextColor.GREEN); }
    public Component saveScalarTooltip(String displayName) {
        return plainGray("Save the " + displayName + " value.");
    }
    public Component addListEntrySaveTooltip(String displayName) {
        return plainGray("Add this value to the " + displayName + " list.");
    }
    public Component saveGridCellTooltip(int index) {
        return plainGray("Save the material for recipe cell " + index + ".");
    }
    public Component saveMapEntryTooltip(String displayName, String key) {
        return plainGray("Save the " + displayName + " value for key " + key + ".");
    }
    public Component addMapEntrySaveTooltip(String displayName) {
        return plainGray("Add a key and value to " + displayName + ".");
    }
    public Component saveWorldProfileTooltip() {
        return plainGray("Save this world's tag and color.");
    }
    public Component addWorldProfileTooltip() {
        return plainGray("Add this world profile and its display settings.");
    }
    public Component cancelLabel() { return bold("Cancel", NamedTextColor.RED); }
    public Component cancelTooltip() { return gray("Discard and go back."); }

    public Component listEntryLabel(String value) { return bold("✗ " + value, NamedTextColor.RED); }
    public Component listEntryTooltip() { return gray("Click to remove."); }
    public Component addEntryLabel() { return bold("+ Add", NamedTextColor.GREEN); }
    public Component addEntryTooltip() { return gray("Open the add-entry dialog."); }
    public Component addEntryTitle(String displayName) { return title("<!italic><green>Add to " + displayName); }
    public Component addEntryBody() { return plainGray("Enter the value to add."); }
    public Component addEntryInputLabel() { return plainYellow("Value"); }

    public Component mapEntryLabel(String label) { return bold(label, NamedTextColor.YELLOW); }
    public Component mapEntryTooltip() { return gray("Click to edit this entry's value."); }
    public Component editMapEntryTitle(String displayName, String key) {
        return title("<!italic><green>Edit: " + displayName + " [" + key + "]");
    }
    public Component addMapEntryLabel() { return bold("+ Add Entry", NamedTextColor.GREEN); }
    public Component addMapEntryTooltip() { return gray("Open the add-entry dialog."); }
    public Component addMapEntryTitle(String displayName) { return title("<!italic><green>Add to " + displayName); }
    public Component addMapEntryBody() { return plainGray("Enter the key and value to add."); }
    public Component mapKeyInputLabel() { return plainYellow("Key"); }

    public Component previousPageLabel() { return bold("◀ Prev Page", NamedTextColor.GREEN); }
    public Component nextPageLabel() { return bold("Next Page ▶", NamedTextColor.GREEN); }
    public Component pageTooltip(int page, int totalPages) { return gray("Page " + page + " of " + totalPages); }

    private static Component title(String miniMessage) { return Messages.MM.deserialize(miniMessage); }
    private static Component plainGray(String text) { return gray(text).decoration(TextDecoration.ITALIC, false); }
    private static Component plainYellow(String text) {
        return Component.text(text, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
    }
    private static Component bold(String text, NamedTextColor color) { return Component.text(text, color, TextDecoration.BOLD); }
    private static Component red(String text) { return Component.text(text, NamedTextColor.RED); }
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
