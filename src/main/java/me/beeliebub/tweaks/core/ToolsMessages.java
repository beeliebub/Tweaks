package me.beeliebub.tweaks.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

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
        return Component.text("Repair Kit", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false);
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
        return Component.text("You have no augmented items to repair.", NamedTextColor.RED);
    }

    public Component repairKitApplied(int repairs, int maxRepairs) {
        return Component.text("Repair kit applied! Repair " + repairs + "/" + maxRepairs,
                NamedTextColor.GREEN);
    }

    public Component repairKitMaxTier() {
        return Component.text("That item has used all of its repairs and can no longer be restored.",
                NamedTextColor.YELLOW);
    }

    public Component repairKitAlreadyFull() {
        return Component.text("That item is already at full durability.", NamedTextColor.YELLOW);
    }

    public Component repairKitTargetChanged() {
        return Component.text("That repair target changed; no Repair Kit was consumed.", NamedTextColor.RED);
    }

    public Component repairKitRepairFailed() {
        return Component.text("That item could not be repaired; no Repair Kit was consumed.", NamedTextColor.RED);
    }

    public Component repairKitRequiresAugmented() {
        return Component.text("Repair kits only work on augmented items.", NamedTextColor.RED);
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
        return Component.text("Usage: /augment | /augment confirm | /augment cancel | /augment debug | "
                        + "/augment give <player> <enchantment-key> [level] [curse-key[:level] ...]",
                NamedTextColor.YELLOW);
    }

    public Component augmentRequiresItem() {
        return Component.text("Hold an item or an augment gem.", NamedTextColor.RED);
    }

    public Component augmentAttached(Component enchantmentName) {
        return Component.text("Attached ", NamedTextColor.GREEN)
                .append(enchantmentName)
                .append(Component.text(".", NamedTextColor.GREEN));
    }

    public Component augmentAttached(Component enchantmentName, int riders) {
        if (riders <= 0) return augmentAttached(enchantmentName);
        return Component.text("Attached ", NamedTextColor.GREEN)
                .append(enchantmentName)
                .append(Component.text(" with " + riders + " curse rider(s).", NamedTextColor.GREEN));
    }

    public Component augmentCursesAttached(int count) {
        return Component.text("Attached " + count + " permanent curse(s).", NamedTextColor.RED);
    }

    public Component augmentDetached(Component enchantmentName) {
        return Component.text("Disabled ", NamedTextColor.YELLOW)
                .append(enchantmentName)
                .append(Component.text(". The slot remains occupied.", NamedTextColor.YELLOW));
    }

    public Component augmentNoSlots() {
        return Component.text("That item has no available augment slots.\n"
                + "Use /augment while holding it to unlock one.", NamedTextColor.RED);
    }

    public Component augmentIncompatible() {
        return Component.text("That augment is not compatible with the held item.", NamedTextColor.RED);
    }

    public Component augmentCurseAlreadyAttached(Component enchantmentName) {
        return Component.text("The curse ", NamedTextColor.RED)
                .append(enchantmentName)
                .append(Component.text(" is already bound to that item.", NamedTextColor.RED));
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
        return Component.text("Migrated " + count + " enchantment(s) into gems.",
                NamedTextColor.GREEN);
    }

    public Component augmentMigrationPrompt(int cost) {
        Component confirm = Component.text("[Confirm]", NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showText(Component.text("Unlock slot 1 for " + cost + " levels",
                        NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand("/augment confirm"));
        Component cancel = Component.text("[Cancel]", NamedTextColor.RED)
                .hoverEvent(HoverEvent.showText(Component.text("Leave the item unchanged", NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand("/augment cancel"));
        return Component.text("Unlock augment slot 1 for " + cost + " levels? ", NamedTextColor.YELLOW)
                .append(confirm).append(Component.text(" ")).append(cancel);
    }

    public Component augmentConfirmationExpired() {
        return Component.text("That augment confirmation expired or the held item changed.", NamedTextColor.RED);
    }

    public Component augmentConfirmationCancelled() {
        return Component.text("Augment migration cancelled; the item was left unchanged.", NamedTextColor.YELLOW);
    }

    public Component augmentConfirmationUnpriced() {
        return Component.text("Slot 1 is not currently priced, so migration was refused.", NamedTextColor.RED);
    }

    public Component augmentPurchase(int cost, int slots) {
        return Component.text("Purchased augment slot " + slots + " for " + cost + " levels.", NamedTextColor.GREEN);
    }

    public Component augmentPurchaseRejected() {
        return Component.text("You do not have enough XP for that augment slot.", NamedTextColor.RED);
    }

    public Component augmentDebugResolution(String material, String capacityKey, int capacity,
                                            String priceKey, int price) {
        String resolvedPrice = price < 0 ? "unpriced" : Integer.toString(price);
        return Component.text("Material " + material + ": capacity " + capacityKey + " = " + capacity
                + "; next slot price " + priceKey + " = " + resolvedPrice + ".", NamedTextColor.GRAY);
    }

    public Component augmentPurchaseUnsafeXp() {
        return Component.text("That slot purchase cannot be represented safely at your current XP level.",
                NamedTextColor.RED);
    }

    public Component augmentMalformed() {
        return Component.text("This item contains an invalid augment record; it was skipped.", NamedTextColor.RED);
    }

    public Component augmentGemName() {
        return Component.text("Augment Gem");
    }

    public Component enchantmentName(Enchantment enchantment) {
        if (enchantment == null) return Component.text("Unknown enchantment")
                .decoration(TextDecoration.ITALIC, false);
        try {
            return enchantment.description().decoration(TextDecoration.ITALIC, false);
        } catch (RuntimeException unavailable) {
            return fallbackEnchantmentName(enchantment.getKey().getKey());
        }
    }

    public Component enchantmentName(Enchantment enchantment, int level) {
        return enchantmentName(enchantment)
                .append(Component.space())
                .append(Component.text(level))
                .decoration(TextDecoration.ITALIC, false);
    }

    public Component enchantmentName(NamespacedKey key, int level) {
        String raw = key == null ? "unknown enchantment" : key.toString();
        return Component.text(raw + " " + level)
                .decoration(TextDecoration.ITALIC, false);
    }

    public Component augmentEnchantmentName(Enchantment enchantment, int level) {
        return enchantmentName(enchantment)
                .append(Component.space())
                .append(Component.text(romanNumeral(level)))
                .decoration(TextDecoration.ITALIC, false);
    }

    public Component augmentEnchantmentName(NamespacedKey key, int level) {
        String raw = key == null ? "unknown enchantment" : key.toString();
        return Component.text(raw + " " + romanNumeral(level))
                .decoration(TextDecoration.ITALIC, false);
    }

    private Component fallbackEnchantmentName(String raw) {
        StringBuilder result = new StringBuilder();
        for (String word : raw.replace('_', ' ').split(" ")) {
            if (word.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return Component.text(result.toString()).decoration(TextDecoration.ITALIC, false);
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

    public Component augmentTargetItem(String material) {
        return Component.text(material, NamedTextColor.WHITE);
    }

    public Component augmentSlotsBody(String dots, int used, int capacity) {
        return Component.text("Slots: " + dots + " (" + used + "/" + capacity + ")", NamedTextColor.WHITE);
    }

    public Component augmentBuySlot(int slot, int cost) {
        return Component.text("Buy slot " + slot + " (" + cost + " levels)", NamedTextColor.GREEN);
    }

    public Component augmentEntry(Component enchantmentName, boolean active) {
        return enchantmentName.append(Component.text(active ? " ●" : " ○",
                active ? NamedTextColor.GREEN : NamedTextColor.GRAY));
    }

    public Component augmentSlotLore(String dots) {
        return Component.text("Augment Slots: " + dots, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    public Component augmentEntryLore(Component enchantmentName, boolean active, String slotDots) {
        NamedTextColor inactiveColor = NamedTextColor.DARK_GRAY;
        Component renderedName = active ? enchantmentName : colorTree(enchantmentName, inactiveColor);
        NamedTextColor dotsColor = active ? NamedTextColor.WHITE : inactiveColor;
        Component line = Component.empty()
                .append(renderedName)
                .append(Component.text(" " + slotDots, dotsColor));
        return withoutItalic(line);
    }

    public Component augmentCurseLore(Component enchantmentName) {
        return withoutItalic(Component.text("", NamedTextColor.RED).append(enchantmentName));
    }

    public Component augmentForeignEnchantLore(Component enchantmentName) {
        return withoutItalic(Component.text("Enchantment: ", NamedTextColor.RED)
                .append(enchantmentName));
    }

    public Component augmentInventoryGem(Component enchantmentName) {
        return Component.text("Attach ", NamedTextColor.LIGHT_PURPLE).append(enchantmentName);
    }

    public Component augmentInventoryCurseGem(Component curseName) {
        return Component.text("Bind ", NamedTextColor.RED).append(curseName);
    }

    public Component augmentGemRider(Component curseName) {
        return Component.text("Binds: ", NamedTextColor.RED).append(curseName);
    }

    public Component augmentNoGems() {
        return Component.text("No compatible augment gems are in your inventory.", NamedTextColor.YELLOW);
    }

    public Component augmentNoTools() {
        return Component.text("No compatible tools are in your inventory.", NamedTextColor.YELLOW);
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

    private Component colorTree(Component component, NamedTextColor color) {
        return component.color(color).children(component.children().stream()
                .map(child -> colorTree(child, color)).toList());
    }

    private Component withoutItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false).children(component.children().stream()
                .map(this::withoutItalic).toList());
    }

    private String romanNumeral(int level) {
        if (level <= 0 || level > 3999) return Integer.toString(level);
        int remaining = level;
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            while (remaining >= values[index]) {
                result.append(numerals[index]);
                remaining -= values[index];
            }
        }
        return result.toString();
    }

    public Component augmentBundleRefused() {
        return Component.text("There is not enough inventory space for the recovered augment gems.", NamedTextColor.RED);
    }

    public Component augmentTableConversionFailed() {
        return Component.text("The table enchantment could not be converted; the vanilla result was kept.", NamedTextColor.RED);
    }

    public Component augmentTableInventoryFull() {
        return Component.text("Your inventory is too full to receive the augment gems; the enchantment was cancelled.",
                NamedTextColor.RED);
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
        return Component.text("Choose an augmented item to repair.", NamedTextColor.WHITE);
    }

    public Component repairKitTargetName(String material) {
        return Component.text(material, NamedTextColor.WHITE);
    }

    public Component repairKitDebug(boolean damageable, boolean ledger, boolean stamp, int tier, int maxTier,
                                    int damage, int maxDamage, int threshold, boolean depleted,
                                    double effectiveTierStep) {
        boolean participating = damageable && (ledger || stamp);
        return Component.text("Damageable=" + damageable + ", participating=" + participating
                        + " (ledger=" + ledger + ", stamp=" + stamp + "), tier=" + tier + "/" + maxTier
                        + ", damage=" + damage + "/" + maxDamage + ", threshold=" + threshold
                        + ", depleted=" + depleted + ", effective-tier-step=" + effectiveTierStep + "%",
                NamedTextColor.GRAY);
    }

    public Component durabilityDepletedRepairable() {
        return Component.text("Out of durability — use a Repair Kit", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false);
    }

    public Component durabilityDepletedTerminal() {
        return Component.text("Broken — cannot be repaired further", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false);
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
