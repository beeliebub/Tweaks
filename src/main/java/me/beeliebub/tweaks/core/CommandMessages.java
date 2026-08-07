package me.beeliebub.tweaks.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

/**
 * Player-feedback factories for command-driven utility features.
 * Callers access this registry through {@link Messages#COMMANDS}.
 */
public final class CommandMessages {

    CommandMessages() {
    }

    // ---------------------------------------------------------------- Condense

    /** Explains that only a player can condense inventory items. */
    public Component condenseRequiresPlayer() {
        return Component.text("Only players can use /condense.", NamedTextColor.RED);
    }

    /** Explains that no eligible inventory items could be condensed. */
    public Component condenseNothingToCondense() {
        return Component.text("Nothing to condense.", NamedTextColor.YELLOW);
    }

    /** Confirms the total number of blocks created by /condense all. */
    public Component condenseAllSuccess(int totalBlocks) {
        return Component.text("Condensed " + totalBlocks + " block" + (totalBlocks == 1 ? "" : "s") + ".",
                NamedTextColor.GREEN);
    }

    /** Explains that the held item cannot be condensed. */
    public Component condenseHeldItemNotEligible() {
        return Component.text("Held item is not condensable. Try /condense all.", NamedTextColor.RED);
    }

    /** Explains that fewer than nine eligible items of the held type are available. */
    public Component condenseNotEnough(String granularName) {
        return Component.text("Not enough " + granularName + " to condense (need 9).", NamedTextColor.YELLOW);
    }

    /** Confirms the number and type of blocks created from the held item. */
    public Component condenseHeldItemSuccess(int blocks, String blockName) {
        return Component.text("Condensed " + blocks + " " + blockName + ".", NamedTextColor.GREEN);
    }

    // ---------------------------------------------------------------- Config

    /** Explains that a requested runtime configuration key is unsupported. */
    public Component configUnknownKey(String key) {
        return Component.text("Unknown config key: " + key, NamedTextColor.RED);
    }

    /** Lists every supported runtime-configuration command form. */
    public List<Component> configUsage(String label) {
        return List.of(
                Component.text("Usage:", NamedTextColor.RED),
                Component.text("  /" + label + " max_homes <int>", NamedTextColor.RED),
                Component.text("  /" + label + " max_chunks <int>", NamedTextColor.RED),
                Component.text("  /" + label + " egg_collector_drop_chance <0.0-100.0>", NamedTextColor.RED),
                Component.text("  /" + label + " eggdrop <disable|enable> <mob>", NamedTextColor.RED),
                Component.text("  /" + label + " spawneregg <disable|enable> <mob>", NamedTextColor.RED),
                Component.text("  /" + label + " resourceitems <add|remove> <item>", NamedTextColor.RED));
    }

    /** Shows the syntax for changing the homes limit. */
    public Component configMaxHomesUsage(String label) {
        return Component.text("Usage: /" + label + " max_homes <int>", NamedTextColor.RED);
    }

    /** Explains that the homes limit must be positive. */
    public Component configMaxHomesMustBePositive() {
        return Component.text("Max homes must be a positive integer.", NamedTextColor.RED);
    }

    /** Confirms the new live homes limit. */
    public Component configMaxHomesUpdated(int newMax) {
        return Component.text("Max homes has been updated live to " + newMax + "!", NamedTextColor.GREEN);
    }

    /** Shows the syntax for changing the protected-chunks limit. */
    public Component configMaxChunksUsage(String label) {
        return Component.text("Usage: /" + label + " max_chunks <int>", NamedTextColor.RED);
    }

    /** Explains that the protected-chunks limit must be positive. */
    public Component configMaxChunksMustBePositive() {
        return Component.text("Max chunks must be a positive integer.", NamedTextColor.RED);
    }

    /** Confirms the new live protected-chunks limit. */
    public Component configMaxChunksUpdated(int newMax) {
        return Component.text("Max chunks has been updated live to " + newMax + "!", NamedTextColor.GREEN);
    }

    /** Shows the syntax for changing the Egg Collector drop chance. */
    public Component configEggDropChanceUsage(String label) {
        return Component.text("Usage: /" + label + " egg_collector_drop_chance <0.0-100.0>", NamedTextColor.RED);
    }

    /** Explains the permitted Egg Collector drop-chance range. */
    public Component configEggDropChanceRange() {
        return Component.text("Drop chance must be between 0.0 and 100.0.", NamedTextColor.RED);
    }

    /** Confirms the new live Egg Collector drop chance. */
    public Component configEggDropChanceUpdated(double chance) {
        return Component.text("Egg Collector drop chance has been updated live to " + chance + "%!", NamedTextColor.GREEN);
    }

    /** Shows the syntax for a mob-toggle runtime-configuration command. */
    public Component configMobToggleUsage(String label, boolean spawnerEggFeature) {
        String subcommand = spawnerEggFeature ? "spawneregg" : "eggdrop";
        return Component.text("Usage: /" + label + " " + subcommand + " <disable|enable> <mob>",
                NamedTextColor.RED);
    }

    /** Explains the accepted actions for a mob-toggle command. */
    public Component configMobToggleActionInvalid() {
        return Component.text("Action must be 'disable' or 'enable'.", NamedTextColor.RED);
    }

    /** Explains that a supplied mob has no corresponding spawn egg. */
    public Component configMobUnknown(String input) {
        return Component.text("Unknown mob (no spawn egg exists): " + input, NamedTextColor.RED);
    }

    /** Confirms a mob-toggle request, including an unchanged request. */
    public Component configMobToggleResult(boolean spawnerEggFeature, String mob, boolean disabling, boolean changed) {
        String featureLabel = spawnerEggFeature ? "Spawn-egg use on spawners" : "Egg Collector drops";
        String outcome;
        if (disabling) {
            outcome = changed ? "are now DISABLED." : "were already disabled.";
        } else {
            outcome = changed ? "are now ENABLED." : "were already enabled.";
        }
        return Component.text(featureLabel + " for '" + mob + "' " + outcome, NamedTextColor.GREEN);
    }

    /** Shows the syntax for editing the resource-world allowed-material list. */
    public Component configResourceItemsUsage(String label) {
        return Component.text("Usage: /" + label + " resourceitems <add|remove> <item>", NamedTextColor.RED);
    }

    /** Explains that a supplied resource-world material was not recognized. */
    public Component configUnknownMaterial(String input) {
        return Component.text("Unknown material: " + input, NamedTextColor.RED);
    }

    /** Confirms a material was added to the resource-world allowed list. */
    public Component configResourceItemAdded(String material) {
        return Component.text("Added '" + material + "' to resource world allowed items.", NamedTextColor.GREEN);
    }

    /** Confirms a material was removed from the resource-world allowed list. */
    public Component configResourceItemRemoved(String material) {
        return Component.text("Removed '" + material + "' from resource world allowed items.", NamedTextColor.GREEN);
    }

    /** Explains that the resource-world allowed-material file could not be persisted. */
    public Component configResourceItemsSaveFailed() {
        return Component.text("The resource world allowed-items file could not be saved; no change was applied.", NamedTextColor.RED);
    }

    /** Explains the accepted actions for the resource-world allowed-material list. */
    public Component configResourceItemActionInvalid() {
        return Component.text("Action must be 'add' or 'remove'.", NamedTextColor.RED);
    }

    /** Explains that another asynchronous player-name lookup is already active for this sender. */
    public Component offlinePlayerLookupBusy() {
        return Component.text("Please wait for the previous player lookup to finish.", NamedTextColor.YELLOW);
    }

    // ---------------------------------------------------------------- GuiCopy

    /** Explains that GuiCopy can only copy a chest targeted by a player. */
    public Component guiCopyRequiresPlayer() {
        return Component.text("Only players can use this command.", NamedTextColor.RED);
    }

    /** Explains that GuiCopy requires a nearby chest target. */
    public Component guiCopyRequiresChestTarget(int maxDistance) {
        return Component.text("Look at a chest within " + maxDistance + " blocks.", NamedTextColor.RED);
    }

    /** Explains the permitted characters in a GuiCopy file name. */
    public Component guiCopyInvalidName() {
        return Component.text("Name may only contain letters, numbers, dots, dashes, and underscores.",
                NamedTextColor.RED);
    }

    /** Confirms a chest inventory was saved or overwritten by GuiCopy. */
    public Component guiCopySaved(boolean overwriting, String name, int size, String worldKey, int x, int y, int z) {
        return Component.text(overwriting ? "Overwrote " : "Saved ", NamedTextColor.GREEN)
                .append(Component.text("guicopies/" + name + ".yml", NamedTextColor.YELLOW))
                .append(Component.text(" (" + size + " slots from " + worldKey + " "
                        + x + "," + y + "," + z + ").", NamedTextColor.GREEN));
    }

    // ---------------------------------------------------------------- Resource Hunt reroll

    /** Explains that only a player can reroll a Resource Hunt target. */
    public Component rerollRequiresPlayer() {
        return Component.text("Only players can use this command.", NamedTextColor.RED);
    }

    /** Explains that Resource Hunt is inactive. */
    public Component rerollResourceHuntInactive() {
        return Component.text("Resource Hunt is not active this session.", NamedTextColor.RED);
    }

    /** Explains that rerolling requires the resource world. */
    public Component rerollRequiresResourceWorld() {
        return Component.text("You must be in the resource world to reroll your target.", NamedTextColor.RED);
    }

    /** Explains that a completed hunt tier prevents another reroll this session. */
    public Component rerollTierAlreadyCompleted() {
        return Component.text("You have already completed a tier in your current hunt. You cannot reroll until the next session.",
                NamedTextColor.RED);
    }

    /** Explains that no replacement Resource Hunt target is available. */
    public Component rerollNoTargetAvailable() {
        return Component.text("Could not reroll â€” no targets available.", NamedTextColor.RED);
    }

    /** Confirms that the player's free Resource Hunt reroll was used. */
    public Component rerollFreeSuccess() {
        return Component.text()
                .append(Component.text("Your target has been rerolled!", NamedTextColor.GREEN))
                .append(Component.text(" (Free reroll used.)", NamedTextColor.GRAY))
                .build();
    }

    /** Explains the cost of a Resource Hunt reroll when the player lacks a rupee. */
    public Component rerollInsufficientRupees() {
        return Component.text()
                .append(Component.text("You don't have enough Resource Rupees to reroll.", NamedTextColor.RED))
                .append(Component.text(" (Cost: 1 Resource Rupee.)", NamedTextColor.GRAY))
                .build();
    }

    /** Confirms that a paid Resource Hunt reroll deducted one rupee. */
    public Component rerollPaidSuccess() {
        return Component.text()
                .append(Component.text("Your target has been rerolled!", NamedTextColor.GREEN))
                .append(Component.text(" (1 Resource Rupee deducted.)", NamedTextColor.GRAY))
                .build();
    }

    // ---------------------------------------------------------------- Resource command

    /** Explains that only a player can enter or manage the resource world. */
    public Component resourceRequiresPlayer() {
        return Component.text("Only players can use this command.", NamedTextColor.RED);
    }

    /** Lists items that prevent entry into the resource world. */
    public Component resourceEntryDisallowedItems(String itemNames) {
        return Component.text("You cannot enter the resource world with these items: ", NamedTextColor.RED)
                .append(Component.text(itemNames, NamedTextColor.YELLOW));
    }

    /** Explains that the configured resource world is unavailable. */
    public Component resourceWorldUnavailable(String worldKey) {
        return Component.text("The resource world (" + worldKey + ") is currently unavailable.", NamedTextColor.RED);
    }

    /** Warns that a temporary platform was created after a safe resource-world location was not found. */
    public Component resourceTemporaryPlatformCreated() {
        return Component.text("No safe spot found; generated a temporary platform.", NamedTextColor.GOLD);
    }

    /** Announces that resource-world teleportation has started. */
    public Component resourceTeleporting() {
        return Component.text("Teleporting to the resource world...", NamedTextColor.YELLOW);
    }

    /** Confirms successful resource-world teleportation. */
    public Component resourceTeleportSuccess() {
        return Component.text("Successfully teleported!", NamedTextColor.GREEN);
    }

    /** Explains that resource-world teleportation did not complete. */
    public Component resourceTeleportFailed() {
        return Component.text("Teleportation was cancelled or failed.", NamedTextColor.RED);
    }

    /** Shows the syntax for assigning a Resource Hunt target. */
    public Component resourceSetTargetUsage() {
        return Component.text("Usage: /resource settarget [player] <targetKey>", NamedTextColor.RED);
    }

    /** Explains that a Resource Hunt target key is invalid. */
    public Component resourceUnknownTargetKey(String targetKey) {
        return Component.text("Unknown target key: ", NamedTextColor.RED)
                .append(Component.text(targetKey, NamedTextColor.YELLOW))
                .append(Component.text(". Use tab-completion to see valid keys.", NamedTextColor.RED));
    }

    /** Confirms that a player's own Resource Hunt target was changed. */
    public Component resourceOwnTargetSet(String targetKey) {
        return Component.text("Your Resource Hunt target has been set to ", NamedTextColor.GREEN)
                .append(Component.text(targetKey, NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN));
    }

    /** Confirms that an administrator changed another player's Resource Hunt target. */
    public Component resourceOtherTargetSet(String targetName, String targetKey) {
        return Component.text("Set ", NamedTextColor.GREEN)
                .append(Component.text(targetName, NamedTextColor.YELLOW))
                .append(Component.text("'s Resource Hunt target to ", NamedTextColor.GREEN))
                .append(Component.text(targetKey, NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN));
    }

    /** Informs a player that an administrator changed their Resource Hunt target. */
    public Component resourceTargetChangedByAdmin(String targetKey) {
        return Component.text("Your Resource Hunt target has been changed to ", NamedTextColor.GOLD)
                .append(Component.text(targetKey, NamedTextColor.YELLOW))
                .append(Component.text(" by an admin.", NamedTextColor.GOLD));
    }
}
