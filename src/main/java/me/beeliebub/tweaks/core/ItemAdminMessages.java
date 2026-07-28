package me.beeliebub.tweaks.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Player-feedback factories for {@code me.beeliebub.tweaks.itemadmin}.
 * Callers access this registry through {@link Messages#ITEM_ADMIN}.
 */
public final class ItemAdminMessages {

    ItemAdminMessages() {
    }

    // ---------------------------------------------------------------- Item editing

    /** Explains that a lore or name edit requires a player sender. */
    public Component itemEditRequiresPlayer() {
        return Component.text("Only players can use this command.", NamedTextColor.RED);
    }

    /** Explains that an item edit requires a main-hand item. */
    public Component itemEditRequiresHeldItem() {
        return Component.text("You must be holding an item.", NamedTextColor.RED);
    }

    /** Explains that a lore line number must be an integer. */
    public Component itemEditLoreLineMustBeInteger() {
        return Component.text("Line number must be an integer.", NamedTextColor.RED);
    }

    /** Explains that lore line numbering starts at one. */
    public Component itemEditLoreLineMustBePositive() {
        return Component.text("Line number must be 1 or greater.", NamedTextColor.RED);
    }

    /** Shows the syntax for adding a lore line. */
    public Component itemEditLoreAddUsage(String label) {
        return Component.text("Usage: /" + label + " add <line#> <text>", NamedTextColor.RED);
    }

    /** Confirms that a styled lore line was added. */
    public Component itemEditLoreAdded(int lineNumber, Component lore) {
        return Component.text("Added lore line " + lineNumber + ":", NamedTextColor.GREEN)
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(lore);
    }

    /** Explains that an item has no lore line to remove. */
    public Component itemEditLoreEmpty() {
        return Component.text("This item has no lore to remove.", NamedTextColor.RED);
    }

    /** Explains that the requested lore line does not exist. */
    public Component itemEditLoreLineMissing(int lineNumber, int loreSize) {
        return Component.text("Line " + lineNumber + " does not exist (item has "
                + loreSize + " lore line" + (loreSize == 1 ? "" : "s") + ").", NamedTextColor.RED);
    }

    /** Confirms that a styled lore line was removed. */
    public Component itemEditLoreRemoved(int lineNumber, Component lore) {
        return Component.text("Removed lore line " + lineNumber + ":", NamedTextColor.GREEN)
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(lore);
    }

    /** Heads the lore-edit usage block. */
    public Component itemEditUsageHeader() {
        return Component.text("Usage:", NamedTextColor.RED);
    }

    /** Shows the lore-add syntax inside the lore-edit usage block. */
    public Component itemEditLoreAddUsageLine(String label) {
        return Component.text("  /" + label + " add <line#> <text>", NamedTextColor.RED);
    }

    /** Shows the lore-remove syntax inside the lore-edit usage block. */
    public Component itemEditLoreRemoveUsageLine(String label) {
        return Component.text("  /" + label + " remove <line#>", NamedTextColor.RED);
    }

    /** Shows the supported custom-name command forms. */
    public Component itemEditNameUsage(String label) {
        return Component.text("Usage: /" + label + " <name> | /" + label + " off | /" + label + " blank",
                NamedTextColor.RED);
    }

    /** Confirms that an item's custom name was removed. */
    public Component itemEditNameRemoved() {
        return Component.text("Custom name removed.", NamedTextColor.GREEN);
    }

    /** Confirms that an item was made blank with hover information removed. */
    public Component itemEditNameBlanked() {
        return Component.text("Item name set to blank and all hover info removed.", NamedTextColor.GREEN);
    }

    /** Confirms an item's new styled display name. */
    public Component itemEditNameSet(Component name) {
        return Component.text("Display name set to ", NamedTextColor.GREEN).append(name);
    }

    /** Explains that /more requires a player sender. */
    public Component itemMoreRequiresPlayer() {
        return Component.text("Only players can use this command!", NamedTextColor.RED);
    }

    /** Explains that /more requires a main-hand item. */
    public Component itemMoreRequiresHeldItem() {
        return Component.text("You must be holding an item!", NamedTextColor.RED);
    }

    /** Confirms the new maximum stack amount of the main-hand item. */
    public Component itemMoreSuccess(int amount) {
        return Component.text("Stack maximized to " + amount + "!", NamedTextColor.GREEN);
    }

    // ---------------------------------------------------------------- Display chest

    /** Explains that Display Chest tools require a player sender. */
    public Component displayChestRequiresPlayer() {
        return Component.text("Only players can use this command.", NamedTextColor.RED);
    }

    /** Announces that Display Chest removal mode was enabled. */
    public Component displayChestRemovalEnabled() {
        return Component.text("Display Chest removal mode ENABLED. Click a chest to remove its item display.",
                NamedTextColor.GREEN);
    }

    /** Announces that Display Chest removal mode was disabled. */
    public Component displayChestRemovalDisabled() {
        return Component.text("Display Chest removal mode DISABLED.", NamedTextColor.RED);
    }

    /** Announces one enabled Display Chest setup-mode variant. */
    public Component displayChestSetupEnabled(boolean useHand, boolean embedSide) {
        String message;
        if (useHand && embedSide) {
            message = "Display Chest setup mode ENABLED (hand + side). Currently-held item is used on each click. Item embeds on the clicked face.";
        } else if (useHand) {
            message = "Display Chest setup mode ENABLED (hand). Currently-held item is used on each click. Item floats above the chest.";
        } else if (embedSide) {
            message = "Display Chest setup mode ENABLED (side). Click a chest to display its top-left item. Item embeds on the clicked face.";
        } else {
            message = "Display Chest setup mode ENABLED. Click a chest to display its top-left item. Item floats above the chest.";
        }
        return Component.text(message, NamedTextColor.GREEN);
    }

    /** Announces that Display Chest setup mode was disabled. */
    public Component displayChestSetupDisabled() {
        return Component.text("Display Chest setup mode DISABLED.", NamedTextColor.RED);
    }

    /** Confirms that a display chest was generated or updated. */
    public Component displayChestGeneratedOrUpdated() {
        return Component.text("Display chest generated/updated!", NamedTextColor.GREEN);
    }

    /** Confirms that a display chest was removed. */
    public Component displayChestRemoved() {
        return Component.text("Display chest removed!", NamedTextColor.RED);
    }
}
