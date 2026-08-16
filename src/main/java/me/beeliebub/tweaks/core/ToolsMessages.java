package me.beeliebub.tweaks.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Player-facing feedback for the tools, XP, cash, durability, and augment features. */
public final class ToolsMessages {

    ToolsMessages() {}

    public Component featureDisabled(String feature) {
        return Component.text(feature + " is currently disabled.", NamedTextColor.YELLOW);
    }

    public Component cashConverted(long amount) {
        return Component.text("Converted cash for $" + amount + ".", NamedTextColor.GREEN);
    }

    public Component cashRejected() {
        return Component.text("That cash item could not be converted; it was left unchanged.", NamedTextColor.RED);
    }

    public Component cashMalformed() {
        return Component.text("That cash item is invalid and was left unchanged.", NamedTextColor.RED);
    }

    public Component cashOverflow() {
        return Component.text("That cash stack is too large to convert safely.", NamedTextColor.RED);
    }

    public Component mendingPaid(long xp, long dollars) {
        return Component.text("Mending converted " + xp + " XP into $" + dollars + ".", NamedTextColor.GREEN);
    }

    public Component repairKitGiven() {
        return Component.text("Repair kit given.", NamedTextColor.GREEN);
    }

    public Component repairKitName() {
        return Component.text("Repair Kit", NamedTextColor.LIGHT_PURPLE);
    }

    public Component repairKitUsage() {
        return Component.text("Usage: /repairkit give [player] | /repairkit debug", NamedTextColor.YELLOW);
    }

    public Component repairKitRequiresPlayer() {
        return Component.text("Only a player can use that repair-kit action.", NamedTextColor.RED);
    }

    public Component augmentRequiresPlayerSender() {
        return Component.text("Only a player can open the augment menu.", NamedTextColor.RED);
    }

    public Component repairKitNoTarget() {
        return Component.text("Hold a damageable item and use a repair kit.", NamedTextColor.RED);
    }

    public Component repairKitApplied(int tier) {
        return Component.text("Repair kit applied. Durability tier is now " + tier + ".", NamedTextColor.GREEN);
    }

    public Component repairKitMaxTier() {
        return Component.text("That item is already at the maximum repair tier.", NamedTextColor.YELLOW);
    }

    public Component repairKitNoDamageableItem() {
        return Component.text("Repair kits only work on damageable items.", NamedTextColor.RED);
    }

    public Component renameUsage(String label) {
        return Component.text("Usage: /" + label + " [name] (omit the name to reset it)", NamedTextColor.YELLOW);
    }

    public Component renameRequiresPlayer() {
        return Component.text("Only a player can rename an item.", NamedTextColor.RED);
    }

    public Component renameRequiresItem() {
        return Component.text("Hold an item to rename it.", NamedTextColor.RED);
    }

    public Component renameTooLong(int max) {
        return Component.text("That name is too long; the maximum is " + max + " characters.", NamedTextColor.RED);
    }

    public Component renamed() {
        return Component.text("Item renamed.", NamedTextColor.GREEN);
    }

    public Component renameReset() {
        return Component.text("Item name reset.", NamedTextColor.GREEN);
    }

    public Component anvilLocked() {
        return Component.text("Anvils are disabled for this item while the tools lockout is enabled.", NamedTextColor.RED);
    }

    public Component grindstoneLocked() {
        return Component.text("Grindstones are disabled for augmented or quality items.", NamedTextColor.RED);
    }

    public Component augmentUsage() {
        return Component.text("Usage: /augment | /augment debug | /augment give <player> <enchantment-key> [level] [curse-key[:level] ...]",
                NamedTextColor.YELLOW);
    }

    public Component augmentRequiresItem() {
        return Component.text("Hold an item or an augment gem.", NamedTextColor.RED);
    }

    public Component augmentAttached(String enchantment, int level) {
        return Component.text("Attached " + enchantment + " " + level + ".", NamedTextColor.GREEN);
    }

    public Component augmentAttached(String enchantment, int level, int riders) {
        if (riders <= 0) return augmentAttached(enchantment, level);
        return Component.text("Attached " + enchantment + " " + level + " with " + riders
                + " curse rider(s).", NamedTextColor.GREEN);
    }

    public Component augmentCursesAttached(int count) {
        return Component.text("Attached " + count + " permanent curse(s).", NamedTextColor.RED);
    }

    public Component augmentDetached(String enchantment) {
        return Component.text("Disabled " + enchantment + ". The slot remains occupied.", NamedTextColor.YELLOW);
    }

    public Component augmentNoSlots() {
        return Component.text("That item has no available augment slots.", NamedTextColor.RED);
    }

    public Component augmentIncompatible() {
        return Component.text("That augment is not compatible with the held item.", NamedTextColor.RED);
    }

    public Component augmentCurseAlreadyAttached(String enchantment) {
        return Component.text("The curse " + enchantment + " is already bound to that item.", NamedTextColor.RED);
    }

    public Component augmentCurseNotCurse(String enchantment) {
        return Component.text(enchantment + " is not a registered curse.", NamedTextColor.RED);
    }

    public Component augmentCurseInvalid(String token) {
        return Component.text("Invalid curse rider: " + token + ".", NamedTextColor.RED);
    }

    public Component augmentCurseDuplicate(String enchantment) {
        return Component.text("The curse rider " + enchantment + " was listed more than once.", NamedTextColor.RED);
    }

    public Component augmentCursePrimary(String enchantment) {
        return Component.text("The curse rider " + enchantment + " cannot also be the primary enchantment.", NamedTextColor.RED);
    }

    public Component augmentTooManyCurses(int maximum) {
        return Component.text("That gem has too many curse riders; the maximum is " + maximum + ".", NamedTextColor.RED);
    }

    public Component augmentMigrated(int count) {
        return Component.text("Migrated " + count + " enchantment(s) into gems; purchased slots were reset.",
                NamedTextColor.GREEN);
    }

    public Component augmentPurchase(int cost, int slots) {
        return Component.text("Purchased augment slot " + slots + " for " + cost + " XP.", NamedTextColor.GREEN);
    }

    public Component augmentPurchaseRejected() {
        return Component.text("You do not have enough XP for that augment slot.", NamedTextColor.RED);
    }

    public Component augmentPurchaseUnsafeXp() {
        return Component.text("That slot purchase cannot be represented safely at your current XP level.",
                NamedTextColor.RED);
    }

    public Component augmentMalformed() {
        return Component.text("This item contains an invalid augment record; it was skipped.", NamedTextColor.RED);
    }

    public Component augmentGemName() {
        return Component.text("Augment Gem", NamedTextColor.LIGHT_PURPLE);
    }

    public Component augmentGemLore(Component enchantmentName) {
        return enchantmentName.decoration(TextDecoration.ITALIC, false);
    }

    public Component augmentHubTitle() {
        return Component.text("Augments", NamedTextColor.GOLD);
    }

    public Component augmentSlotsLabel() {
        return Component.text("Manage Slots", NamedTextColor.AQUA);
    }

    public Component augmentListLabel() {
        return Component.text("Manage Augments", NamedTextColor.AQUA);
    }

    public Component augmentSlotsBody(String dots, int used, int capacity) {
        return Component.text("Slots: " + dots + " (" + used + "/" + capacity + ")", NamedTextColor.WHITE);
    }

    public Component augmentBuySlot(int slot, int cost) {
        return Component.text("Buy slot " + slot + " (" + cost + " levels)", NamedTextColor.GREEN);
    }

    public Component augmentEntry(String enchantment, int level, boolean active) {
        return Component.text(enchantment + " " + level + (active ? " ●" : " ○"),
                active ? NamedTextColor.GREEN : NamedTextColor.GRAY);
    }

    public Component augmentSlotLore(String dots) {
        return Component.text("Augment Slots: " + dots, NamedTextColor.GRAY);
    }

    public Component augmentEntryLore(String enchantment, int level, boolean active) {
        return Component.text("Augment: " + enchantment + " " + level + " " + (active ? "●" : "○"),
                active ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY);
    }

    public Component augmentCurseLore(String enchantment) {
        return Component.text("Curse: " + enchantment, NamedTextColor.RED);
    }

    public Component augmentInventoryGem(String enchantment, int level) {
        return Component.text("Attach " + enchantment + " " + level, NamedTextColor.LIGHT_PURPLE);
    }

    public Component augmentNoGems() {
        return Component.text("No compatible augment gems are in your inventory.", NamedTextColor.YELLOW);
    }

    public Component augmentPreviousPage() {
        return Component.text("◀ Previous Page", NamedTextColor.GREEN);
    }

    public Component augmentNextPage() {
        return Component.text("Next Page ▶", NamedTextColor.GREEN);
    }

    public Component augmentPageSummary(int page, int totalPages, int totalEntries) {
        return Component.text("Showing " + totalEntries + " augment option(s), page "
                + page + " of " + totalPages + ".", NamedTextColor.GRAY);
    }

    public Component augmentBundleRefused() {
        return Component.text("There is not enough inventory space for the recovered augment gems.", NamedTextColor.RED);
    }

    public Component augmentTableConversionFailed() {
        return Component.text("The table enchantment could not be converted; the vanilla result was kept.", NamedTextColor.RED);
    }

    public Component augmentBookConversionFailed() {
        return Component.text("That enchanted book could not be converted safely; it was left unchanged.", NamedTextColor.RED);
    }

    public Component augmentBundleResult(int recovered) {
        return Component.text("The bundle destroyed the item and recovered " + recovered + " augment gem(s).",
                NamedTextColor.LIGHT_PURPLE);
    }

    public Component xpBottleXpTotalTooLargeIngredientsReturned() {
        return Component.text("Your XP total is too large to store safely; ingredients returned.",
                NamedTextColor.RED);
    }

    public Component xpBottleNotEnoughXpIngredientsReturned() {
        return Component.text("Not enough XP to brew XP bottles. Ingredients returned.", NamedTextColor.RED);
    }

    public Component xpBottleXpChangedReturned() {
        return Component.text("Your XP total changed and the brew was returned without a charge.",
                NamedTextColor.RED);
    }

    public Component xpBottleChargeFailed() {
        return Component.text("Your XP total could not be charged safely; the brew was returned unchanged.",
                NamedTextColor.RED);
    }

    public Component xpBottlePartialBrew(int affordable, int totalBottles) {
        return Component.text("Not enough XP for all bottles — brewed " + affordable + " of " + totalBottles + ".",
                NamedTextColor.YELLOW);
    }

    public Component xpBottleConsumeRejected() {
        return Component.text("Your XP total is too large to receive that bottle safely; the bottle was not consumed.",
                NamedTextColor.RED);
    }

    public Component xpBottleAwardFailed() {
        return Component.text("Your XP total could not be increased safely; the bottle was not consumed.",
                NamedTextColor.RED);
    }

    public Component repairKitDialogBody() {
        return Component.text("Choose a damageable item to repair.", NamedTextColor.WHITE);
    }

    public Component repairKitTargetName(String material) {
        return Component.text(material, NamedTextColor.WHITE);
    }

    public Component repairKitDebug(boolean damageable, int tier, int max) {
        return Component.text("Damageable=" + damageable + ", tier=" + tier + ", max=" + max,
                NamedTextColor.GRAY);
    }

    public Component repairKitPreviousPage() {
        return Component.text("◀ Previous Page", NamedTextColor.GREEN);
    }

    public Component repairKitNextPage() {
        return Component.text("Next Page ▶", NamedTextColor.GREEN);
    }

    public Component inventoryFull() {
        return Component.text("Your inventory does not have enough room for that result.", NamedTextColor.RED);
    }
}
