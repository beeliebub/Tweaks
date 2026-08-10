package me.beeliebub.tweaks.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Centralized player-facing message registry, mirroring the {@code Permissions.java} pattern:
 * one entry per message, grouped by feature area, each documented with its exact player-visible
 * effect.
 *
 * <p><b>Dialect.</b> Component-builder (this class's own factories) is used for command/event
 * feedback — it is compile-time checked and already the dominant style (~1000+ call sites) across
 * the codebase. {@link #MM}, a single shared {@link MiniMessage} instance, is provided for callers
 * that need richer inline formatting (GUI/help display text, or messages built from templated
 * strings) so no class declares its own {@code MiniMessage.miniMessage()} instance.
 *
 * <p><b>Prefix.</b> Messages stay bare (no {@code [Tweaks]} prefix) — this is a deliberate decision,
 * not an oversight, matching the plugin's existing tone.
 *
 * <p>This class is populated incrementally: it covers the highest-duplication message families
 * (permission-denied, player-not-found, invalid-number) plus complete feature-specific families as
 * their owning packages migrate off local string literals. Expand it rather than reintroducing
 * local literals.
 */
public final class Messages {

    private Messages() {
        // Prevent instantiation
    }

    /** Shared MiniMessage instance. Reference this instead of declaring a local one. */
    public static final MiniMessage MM = MiniMessage.miniMessage();

    /** Central feedback family for the protection command, listener, and Paper Dialog UI. */
    public static final ProtectionMessages PROTECTION = new ProtectionMessages();

    /** Player-feedback factories for standalone administrative and utility commands. */
    public static final CommandMessages COMMANDS = new CommandMessages();

    /** Player-feedback factories for administrative item tools. */
    public static final ItemAdminMessages ITEM_ADMIN = new ItemAdminMessages();

    /** Player-feedback factories for homes, warps, and player teleportation. */
    public static final TeleportMessages TELEPORT = new TeleportMessages();

    /** Player-feedback factories for minigame systems and their presentation. */
    public static final MinigameMessages MINIGAMES = new MinigameMessages();

    /** Player-feedback factories for the server-wide lottery. */
    public static final LotteryMessages LOTTERY = new LotteryMessages();

    /** Player-feedback factories and dialog copy for permission administration. */
    public static final PermissionMessages PERMISSIONS = new PermissionMessages();

    /** Player-feedback factories for world-profile inventory separation. */
    public static final ProfileMessages PROFILES = new ProfileMessages();

    /** Player-feedback factories and dialog copy for the {@code /tconfig} generic settings engine and GUI. */
    public static final ConfigMessages CONFIG = new ConfigMessages();

    /** Player-feedback factories for Skyblock islands, progression, and economy. */
    public static final SkyblockMessages SKYBLOCK = new SkyblockMessages();

    // ---------------------------------------------------------------- Permissions

    /**
     * Sent when a sender lacks the permission required for the command or action they attempted.
     * The canonical wording for what was previously ~7 distinct phrasings across 20+ files.
     */
    public static Component noPermission() {
        return Component.text("You do not have permission to use this command.", NamedTextColor.RED);
    }

    // ---------------------------------------------------------------- Player lookup

    /** Sent when a named player is not currently online. */
    public static Component playerNotOnline(String name) {
        return Component.text("Player '" + name + "' is not online.", NamedTextColor.RED);
    }

    // ---------------------------------------------------------------- Input validation

    /** Sent when a required argument could not be parsed as an integer. */
    public static Component invalidNumber() {
        return Component.text("Invalid number format. Please provide a valid integer.", NamedTextColor.RED);
    }

    /** Sent when a required argument could not be parsed as a decimal number. */
    public static Component invalidDecimal() {
        return Component.text("Invalid number format. Please provide a valid decimal number.", NamedTextColor.RED);
    }

    // ---------------------------------------------------------------- Economy

    /** Explains that the console must name a player when running the balance command. */
    public static Component balanceConsoleMustSpecifyPlayer() {
        return Component.text("Console must specify a player: /balance <player>", NamedTextColor.RED);
    }

    /** Explains that the console cannot toggle a player's hidden-balance preference. */
    public static Component balanceHideConsoleCannotUse() {
        return Component.text("Console cannot use /balance hide.", NamedTextColor.RED);
    }

    /** Confirms that the player's balance is no longer shown in the tab list. */
    public static Component balanceHidden() {
        return Component.text("Your balance is now ", NamedTextColor.YELLOW)
                .append(Component.text("hidden", NamedTextColor.RED))
                .append(Component.text(" from the tab list.", NamedTextColor.YELLOW));
    }

    /** Confirms that the player's balance is shown in the tab list. */
    public static Component balanceVisible() {
        return Component.text("Your balance is now ", NamedTextColor.YELLOW)
                .append(Component.text("visible", NamedTextColor.GREEN))
                .append(Component.text(" in the tab list.", NamedTextColor.YELLOW));
    }

    /** Explains that a sender cannot change player balances. */
    public static Component balanceModifyNoPermission() {
        return Component.text("You don't have permission to modify balances.", NamedTextColor.RED);
    }

    /** Shows the syntax for one administrative balance mutation. */
    public static Component balanceMutationUsage(String operation) {
        return Component.text("Usage: /balance " + operation + " <player> <amount>", NamedTextColor.RED);
    }

    /** Explains that a supplied balance amount was not a valid decimal. */
    public static Component balanceInvalidAmount(String input) {
        return Component.text("Invalid amount: " + input, NamedTextColor.RED);
    }

    /** Explains that a balance target is not an online or known player. */
    public static Component balanceUnknownPlayer(String input) {
        return Component.text("Unknown player: " + input, NamedTextColor.RED);
    }

    /** Confirms an administrative balance mutation and the target's resulting balance. */
    public static Component balanceMutationSuccess(String targetName, String operation, String formattedAmount,
                                                   String formattedNewBalance) {
        return Component.text("Balance of ", NamedTextColor.GREEN)
                .append(Component.text(targetName, NamedTextColor.GOLD))
                .append(Component.text(" " + operation + " by ", NamedTextColor.GREEN))
                .append(Component.text(formattedAmount, NamedTextColor.YELLOW))
                .append(Component.text(". New balance: ", NamedTextColor.GREEN))
                .append(Component.text(formattedNewBalance, NamedTextColor.YELLOW));
    }

    /** Explains that a balance mutation was rejected because the stored value cannot represent it exactly. */
    public static Component balanceMutationRejected(String targetName, String operation, String formattedAmount) {
        return Component.text("Could not " + operation + " " + formattedAmount + " for ", NamedTextColor.RED)
                .append(Component.text(targetName, NamedTextColor.GOLD))
                .append(Component.text(" because that balance cannot represent the change exactly.", NamedTextColor.RED));
    }

    /** Explains that a sender cannot view another player's balance. */
    public static Component balanceViewOtherNoPermission() {
        return Component.text("You don't have permission to view other players' balances.", NamedTextColor.RED);
    }

    /** Shows a named player's formatted balance. */
    public static Component balanceOtherPlayer(String targetName, String formattedBalance) {
        return Component.text(targetName + "'s balance: ", NamedTextColor.GOLD)
                .append(Component.text(formattedBalance, NamedTextColor.YELLOW));
    }

    /** Shows the viewing player's formatted balance. */
    public static Component balanceOwn(String formattedBalance) {
        return Component.text("Your balance: ", NamedTextColor.GOLD)
                .append(Component.text(formattedBalance, NamedTextColor.YELLOW));
    }

    /** Explains that the house account's asynchronous startup read has not completed yet. */
    public static Component houseStillLoading() {
        return Component.text("The house account is still loading. Please try again shortly.", NamedTextColor.YELLOW);
    }

    /** Shows the supported administrative house-account command forms. */
    public static Component houseUsage() {
        return Component.text("Usage: /house balance|add <amount>|remove <amount>|set <amount>|pay <player> <amount>",
                NamedTextColor.RED);
    }

    /** Shows the current whole-dollar casino house balance. */
    public static Component houseBalance(long balance) {
        return Component.text("House balance: $" + balance, NamedTextColor.GOLD);
    }

    /** Explains that an account amount must be a positive whole number. */
    public static Component houseInvalidAmount(String input) {
        return Component.text("Invalid positive whole-dollar amount: " + input, NamedTextColor.RED);
    }

    /** Confirms an administrative house-account mutation. */
    public static Component houseMutation(String operation, long amount, long balance) {
        return Component.text("House account " + operation + " $" + amount + ". New balance: $" + balance,
                NamedTextColor.GREEN);
    }

    /** Explains that an account debit or payment would overdraw the house. */
    public static Component houseInsufficientFunds() {
        return Component.text("The house account does not have enough funds for that.", NamedTextColor.RED);
    }

    /** Explains that a credit would exceed the range of the persisted house balance. */
    public static Component houseOverflow() {
        return Component.text("That amount would overflow the house account.", NamedTextColor.RED);
    }

    /** Explains that a payment target is neither online nor known to this server. */
    public static Component houseUnknownPlayer(String name) {
        return Component.text("Unknown player: " + name, NamedTextColor.RED);
    }

    /** Explains that a house payment needs administrator reconciliation after an unexpected failure. */
    public static Component housePaymentFailed() {
        return Component.text("House payment failed; contact an administrator for reconciliation.", NamedTextColor.RED);
    }

    /** Confirms a completed house-to-player transfer. */
    public static Component housePaid(String target, long amount, long remainingBalance) {
        return Component.text("Paid $" + amount + " to " + target + ". House balance: $" + remainingBalance,
                NamedTextColor.GREEN);
    }

    /** Explains that the server is shutting down and new house payments are rejected. */
    public static Component houseShuttingDown() {
        return Component.text("The server is shutting down; house payments are temporarily unavailable.", NamedTextColor.RED);
    }

    /** Explains that the recipient's balance could not exactly represent the payment; the house was not charged. */
    public static Component housePaymentUnrepresentable(String target) {
        return Component.text(target + "'s balance can't exactly represent that amount; nothing was charged.",
                NamedTextColor.RED);
    }

    /** Explains that a payment's recovery record disagreed with what was already applied and needs a human. */
    public static Component housePaymentNeedsReconciliation() {
        return Component.text("That payment's recovery record didn't match; it needs manual reconciliation. Contact an administrator.",
                NamedTextColor.RED);
    }

    /** Explains that a debit succeeded but the credit could not be confirmed durable. */
    public static Component housePaymentRetrying() {
        return Component.text("The house was charged but the credit could not be confirmed; the payment is retained for replay on the next restart.",
                NamedTextColor.RED);
    }

    /** Announces one granted daily-login reward and its resulting streak day. */
    public static Component dailyReward(String formattedReward, int streak) {
        return Component.text("Daily reward: ", NamedTextColor.GOLD)
                .append(Component.text("+" + formattedReward, NamedTextColor.YELLOW))
                .append(Component.text(" (Day " + streak + " streak)", NamedTextColor.GREEN));
    }

    // ---------------------------------------------------------------- ToolProtect

    /** Explains that ToolProtect may only be configured by a player. */
    public static Component toolProtectRequiresPlayer() {
        return Component.text("Only players can use /toolprotect.", NamedTextColor.RED);
    }

    /** Shows the supported ToolProtect command forms. */
    public static Component toolProtectUsage() {
        return Component.text("Usage: /toolprotect <on|off> | /toolprotect durability <n>", NamedTextColor.YELLOW);
    }

    /** Explains that ToolProtect was already in the requested state. */
    public static Component toolProtectAlready(boolean enabled) {
        return Component.text("ToolProtect is already " + (enabled ? "ON." : "OFF."), NamedTextColor.YELLOW);
    }

    /** Confirms that ToolProtect was enabled or disabled. */
    public static Component toolProtectStateChanged(boolean enabled) {
        return Component.text("ToolProtect ", NamedTextColor.GRAY)
                .append(Component.text(enabled ? "ON" : "OFF", enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text(".", NamedTextColor.GRAY));
    }

    /** Shows the player's configured ToolProtect durability threshold. */
    public static Component toolProtectCurrentThreshold(int threshold) {
        return Component.text("Current threshold: ", NamedTextColor.GRAY)
                .append(Component.text(threshold, NamedTextColor.AQUA))
                .append(Component.text(" remaining durability.", NamedTextColor.GRAY));
    }

    /** Shows the syntax for changing ToolProtect's durability threshold. */
    public static Component toolProtectThresholdUsage() {
        return Component.text("Usage: /toolprotect durability <n>", NamedTextColor.YELLOW);
    }

    /** Explains that a ToolProtect threshold must be an integer. */
    public static Component toolProtectThresholdMustBeWholeNumber() {
        return Component.text("Threshold must be a whole number.", NamedTextColor.RED);
    }

    /** Explains the minimum accepted ToolProtect threshold and how to disable protection. */
    public static Component toolProtectThresholdMinimum() {
        return Component.text("Threshold must be at least 1. Use /toolprotect off to disable.", NamedTextColor.RED);
    }

    /** Confirms a new ToolProtect durability threshold. */
    public static Component toolProtectThresholdSet(int threshold) {
        return Component.text("ToolProtect threshold set to ", NamedTextColor.GREEN)
                .append(Component.text(threshold, NamedTextColor.AQUA))
                .append(Component.text(" remaining durability.", NamedTextColor.GREEN));
    }

    /** Shows the current ToolProtect state and durability threshold. */
    public static Component toolProtectStatus(boolean enabled, int threshold) {
        return Component.text("ToolProtect: ", NamedTextColor.GRAY)
                .append(Component.text(enabled ? "ON" : "OFF", enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text(" • Threshold: ", NamedTextColor.GRAY))
                .append(Component.text(threshold, NamedTextColor.AQUA))
                .append(Component.text(" remaining", NamedTextColor.GRAY));
    }

    /** Explains how to change the ToolProtect state or durability threshold. */
    public static Component toolProtectStatusUsage() {
        return Component.text("Use /toolprotect <on|off> or /toolprotect durability <n>.", NamedTextColor.DARK_GRAY);
    }

    /** Warns that a protected tool is below its configured remaining-durability threshold. */
    public static Component toolProtectDurabilityWarning(int remaining, int threshold) {
        return Component.text("ToolProtect: " + remaining + " durability remaining (threshold "
                + threshold + "). Repair or /toolprotect off.", NamedTextColor.RED);
    }

    // ---------------------------------------------------------------- ItemFilter

    /** Explains that ItemFilter may only be configured by a player. */
    public static Component itemFilterRequiresPlayer() {
        return Component.text("Only players can use /itemfilter.", NamedTextColor.RED);
    }

    /** Shows the supported ItemFilter command forms. */
    public static Component itemFilterUsage() {
        return Component.text("Usage: /if [toggle | mode | add <item...> | remove <item...> | list | clear [whitelist|blacklist|both]]",
                NamedTextColor.YELLOW);
    }

    /** Shows the current ItemFilter state, mode, and entry count. */
    public static Component itemFilterStatus(boolean enabled, String mode, int itemCount) {
        return Component.text("ItemFilter: ", NamedTextColor.GRAY)
                .append(Component.text(enabled ? "ENABLED" : "DISABLED",
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text(" • Mode: ", NamedTextColor.GRAY))
                .append(Component.text(mode, NamedTextColor.AQUA))
                .append(Component.text(" • Items: ", NamedTextColor.GRAY))
                .append(Component.text(Integer.toString(itemCount), NamedTextColor.WHITE));
    }

    /** Explains how to view the active ItemFilter list. */
    public static Component itemFilterStatusUsage(String mode) {
        return Component.text("Use /if list to see your " + mode + ".", NamedTextColor.DARK_GRAY);
    }

    /** Confirms that ItemFilter was enabled or disabled. */
    public static Component itemFilterToggled(boolean enabled) {
        return Component.text("ItemFilter " + (enabled ? "enabled." : "disabled."),
                enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
    }

    /** Confirms the newly selected ItemFilter list mode. */
    public static Component itemFilterModeSet(String mode) {
        return Component.text("ItemFilter mode set to ", NamedTextColor.GREEN)
                .append(Component.text(mode, NamedTextColor.AQUA))
                .append(Component.text(".", NamedTextColor.GREEN));
    }

    /** Shows the syntax for adding materials to an ItemFilter list. */
    public static Component itemFilterAddUsage() {
        return Component.text("Usage: /if add <item...>", NamedTextColor.YELLOW);
    }

    /** Explains that an ItemFilter material argument was not recognized. */
    public static Component itemFilterUnknownItem(String input) {
        return Component.text("Unknown item: " + input, NamedTextColor.RED);
    }

    /** Explains that a material is already in the active ItemFilter list. */
    public static Component itemFilterAlreadyListed(String material, String mode) {
        return Component.text(material + " is already in your " + mode + ".", NamedTextColor.YELLOW);
    }

    /** Confirms that a material was added to the active ItemFilter list. */
    public static Component itemFilterAdded(String material, String mode) {
        return Component.text("Added ", NamedTextColor.GREEN)
                .append(Component.text(material, NamedTextColor.AQUA))
                .append(Component.text(" to your " + mode + ".", NamedTextColor.GREEN));
    }

    /** Shows the syntax for removing materials from an ItemFilter list. */
    public static Component itemFilterRemoveUsage() {
        return Component.text("Usage: /if remove <item...>", NamedTextColor.YELLOW);
    }

    /** Explains that a material is absent from the active ItemFilter list. */
    public static Component itemFilterNotListed(String material, String mode) {
        return Component.text(material + " isn't in your " + mode + ".", NamedTextColor.YELLOW);
    }

    /** Confirms that a material was removed from the active ItemFilter list. */
    public static Component itemFilterRemoved(String material, String mode) {
        return Component.text("Removed ", NamedTextColor.GREEN)
                .append(Component.text(material, NamedTextColor.AQUA))
                .append(Component.text(" from your " + mode + ".", NamedTextColor.GREEN));
    }

    /** Confirms that the active ItemFilter list was cleared. */
    public static Component itemFilterActiveListCleared(String mode) {
        return Component.text("Your " + mode + " has been cleared.", NamedTextColor.GREEN);
    }

    /** Confirms that the whitelist ItemFilter list was cleared. */
    public static Component itemFilterWhitelistCleared() {
        return Component.text("Your whitelist has been cleared.", NamedTextColor.GREEN);
    }

    /** Confirms that the blacklist ItemFilter list was cleared. */
    public static Component itemFilterBlacklistCleared() {
        return Component.text("Your blacklist has been cleared.", NamedTextColor.GREEN);
    }

    /** Confirms that both ItemFilter lists were cleared. */
    public static Component itemFilterBothListsCleared() {
        return Component.text("Both your whitelist and blacklist have been cleared.", NamedTextColor.GREEN);
    }

    /** Shows the supported ItemFilter clear targets. */
    public static Component itemFilterClearUsage() {
        return Component.text("Usage: /if clear [whitelist|blacklist|both]", NamedTextColor.YELLOW);
    }

    /** Explains that the active ItemFilter list has no entries. */
    public static Component itemFilterListEmpty(String mode) {
        return Component.text("Your " + mode + " is empty.", NamedTextColor.GRAY);
    }

    /** Heads the list of materials in the active ItemFilter list. */
    public static Component itemFilterListHeader(String mode, int itemCount) {
        return Component.text("Your " + mode + " (" + itemCount + "):", NamedTextColor.GRAY);
    }

    /** Shows one shortened material name from an ItemFilter list. */
    public static Component itemFilterListEntry(String material) {
        return Component.text(" • ", NamedTextColor.DARK_GRAY)
                .append(Component.text(material, NamedTextColor.AQUA));
    }

    // ---------------------------------------------------------------- InvSee

    /** Explains that only a player can inspect another player's inventory. */
    public static Component invSeeRequiresPlayer() {
        return Component.text("Only players can use /invsee.", NamedTextColor.RED);
    }

    /** Shows the syntax for the inventory-inspection command. */
    public static Component invSeeUsage() {
        return Component.text("Usage: /invsee <player>", NamedTextColor.RED);
    }

    /** Explains that a player cannot inspect their own inventory through InvSee. */
    public static Component invSeeCannotInspectSelf() {
        return Component.text("You can't /invsee yourself.", NamedTextColor.RED);
    }

    /** Renders the title of an InvSee inventory mirror. */
    public static Component invSeeTitle(String targetName) {
        return Component.text(targetName + "'s Inventory", NamedTextColor.GOLD);
    }

    /** Explains that an InvSee target went offline during an interaction. */
    public static Component invSeeTargetNoLongerOnline() {
        return Component.text("Target is no longer online. Closing.", NamedTextColor.RED);
    }

    /** Explains that an InvSee mirror closed because its target quit. */
    public static Component invSeeTargetQuit() {
        return Component.text("InvSee closed: target quit.", NamedTextColor.YELLOW);
    }

    /** Explains that an InvSee mirror closed because its target changed profile. */
    public static Component invSeeTargetSwitchedProfile() {
        return Component.text("InvSee closed: target switched profile.", NamedTextColor.YELLOW);
    }

    /** Explains that an InvSee mirror closed because its target died. */
    public static Component invSeeTargetDied() {
        return Component.text("InvSee closed: target died.", NamedTextColor.YELLOW);
    }

    // ---------------------------------------------------------------- Death inventory

    /** Confirms that a clicked death-inventory GUI item was copied to the viewer. */
    public static Component deathInventoryItemGiven() {
        return Component.text("Item given.", NamedTextColor.GREEN);
    }

    /** Explains that the requested named player has no prior server history. */
    public static Component deathInventoryPlayerHasNotPlayed(String name) {
        return Component.text("Player '" + name + "' has not played before.", NamedTextColor.RED);
    }

    /** Explains that a player has no saved death-inventory snapshots. */
    public static Component deathInventoryNoneFound(String name) {
        return Component.text("No death inventories found for " + name + ".", NamedTextColor.YELLOW);
    }

    /** Heads the list of a player's saved death-inventory snapshots. */
    public static Component deathInventoryListHeader(String name) {
        return Component.text("Death inventories for " + name + ":", NamedTextColor.GOLD);
    }

    /** Displays one saved death-inventory identifier and its formatted date. */
    public static Component deathInventoryListEntry(String id, String date) {
        return Component.text("  " + id + "  (" + date + ")", NamedTextColor.GRAY);
    }

    /** Explains that a requested death-inventory identifier does not exist for the player. */
    public static Component deathInventoryNotFound(String id, String name) {
        return Component.text("No death inventory with ID '" + id + "' for " + name + ".", NamedTextColor.RED);
    }

    /** Explains that a player must be online before their inventory can be restored. */
    public static Component deathInventoryRestoreTargetOffline(String name) {
        return Component.text(name + " must be online to restore their inventory.", NamedTextColor.RED);
    }

    /** Confirms an administrator restored a player's saved inventory. */
    public static Component deathInventoryRestored(String name) {
        return Component.text("Restored inventory for " + name + ".", NamedTextColor.GREEN);
    }

    /** Notifies a player that an administrator restored their prior-death inventory. */
    public static Component deathInventoryRestoreNotice() {
        return Component.text("An admin restored your inventory from a previous death.", NamedTextColor.YELLOW);
    }

    /** Explains that opening the death-inventory GUI requires a player sender. */
    public static Component deathInventoryGuiRequiresPlayer() {
        return Component.text("Only players can open the death inventory GUI. Use 'restore' to restore.", NamedTextColor.RED);
    }

    /** Renders the title of a death-inventory inspection GUI. */
    public static Component deathInventoryGuiTitle(String name, String date) {
        return Component.text(name + " @ " + date, NamedTextColor.DARK_AQUA);
    }

    /** Shows the syntax for the death-inventory command. */
    public static Component deathInventoryUsage(String label) {
        return Component.text("Usage: /" + label + " <player> list | /" + label + " <player> <id> [restore]",
                NamedTextColor.YELLOW);
    }

    // ---------------------------------------------------------------- Ranks

    /** Explains that opening the rank editor requires a player sender. */
    public static Component ranksEditRequiresPlayer() {
        return Component.text("Only players can edit ranks.", NamedTextColor.RED);
    }

    /** Heads the configured-rank list shown by the ranks command. */
    public static Component ranksListHeader() {
        return Component.text("--- Ranks ---", NamedTextColor.GOLD);
    }

    /** Displays one configured rank and its cost, bonus, rakeback, and current-rank marker. */
    public static Component ranksListEntry(String marker, Component displayName, String formattedCost,
                                           int bonusPercent, int rakebackPercent, boolean current,
                                           NamedTextColor color) {
        return Component.text(marker + "Rank ", color)
                .append(displayName)
                .append(Component.text(" — Cost: " + formattedCost
                        + " | Bonus: " + bonusPercent + "%"
                        + " | Rakeback: " + rakebackPercent + "%"
                        + (current ? " (current)" : ""), color));
    }

    /** Advises an unranked player how to purchase their first rank. */
    public static Component ranksUnrankedNotice() {
        return Component.text("You are currently Unranked. Use /rankup to purchase Rank I.", NamedTextColor.GRAY);
    }

    /** Informs a player that they have reached the final configured rank. */
    public static Component ranksMaximumRankNotice() {
        return Component.text("You have reached the maximum rank!", NamedTextColor.GOLD);
    }

    /** Explains that purchasing a rank requires a player sender. */
    public static Component rankupRequiresPlayer() {
        return Component.text("Only players can use this command.", NamedTextColor.RED);
    }

    /** Informs a player that they cannot purchase another rank. */
    public static Component rankupAlreadyMaximumRank() {
        return Component.text("You are already the maximum rank.", NamedTextColor.RED);
    }

    /** Explains the next rank's cost and the player's insufficient balance. */
    public static Component rankupInsufficientFunds(Component rankName, String formattedCost, String formattedBalance) {
        return Component.text("Insufficient funds. Rank ", NamedTextColor.RED)
                .append(rankName)
                .append(Component.text(" costs " + formattedCost
                        + " but you only have " + formattedBalance + ".", NamedTextColor.RED));
    }

    /** Confirms a successful rank purchase and shows the remaining balance. */
    public static Component rankupSuccess(Component rankName, String formattedRemainingBalance) {
        return Component.text("Congratulations! You are now Rank ", NamedTextColor.GREEN)
                .append(rankName)
                .append(Component.text(". Remaining balance: " + formattedRemainingBalance + ".", NamedTextColor.GREEN));
    }

    /** Shows the syntax for manually assigning a player's rank. */
    public static Component rankSetUsage() {
        return Component.text("Usage: /rank set <player> <rank_id/name>", NamedTextColor.RED);
    }

    /** Explains that a requested rank name or identifier is invalid. */
    public static Component rankSetInvalidRank(String rankInput) {
        return Component.text("Invalid rank: " + rankInput, NamedTextColor.RED);
    }

    /** Confirms that an administrator assigned a rank to a player. */
    public static Component rankSetSuccess(String targetName, Component rankName) {
        return Component.text("Successfully set ", NamedTextColor.GREEN)
                .append(Component.text(targetName, NamedTextColor.GOLD))
                .append(Component.text("'s rank to ", NamedTextColor.GREEN))
                .append(rankName)
                .append(Component.text(".", NamedTextColor.GREEN));
    }

    /** Explains that a rank name submitted through the editor cannot be empty. */
    public static Component rankEditNameCannotBeEmpty() {
        return Component.text("Name cannot be empty.", NamedTextColor.RED);
    }

    /** Confirms that an administrator updated a configured rank's display name. */
    public static Component rankEditNameUpdated(int rank, Component displayName) {
        return Component.text("Set name of Rank " + rank + " to ", NamedTextColor.GREEN)
                .append(displayName)
                .append(Component.text(".", NamedTextColor.GREEN));
    }

    /** Explains that the rank editor received an unsupported attribute name. */
    public static Component rankEditUnknownAttribute(String attribute) {
        return Component.text("Unknown attribute: " + attribute, NamedTextColor.RED);
    }

    /** Confirms that an administrator updated one numeric attribute of a configured rank. */
    public static Component rankEditAttributeUpdated(String attribute, Component rankName, double value) {
        return Component.text("Set " + attribute + " of Rank ", NamedTextColor.GREEN)
                .append(rankName)
                .append(Component.text(" to " + value + ".", NamedTextColor.GREEN));
    }

    /** Labels one rank-selection button with its configured display name and cost. */
    public static Component rankEditListButtonLabel(Component displayName, String formattedCost) {
        return Component.text("Rank ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false)
                .append(displayName)
                .append(Component.text(" — " + formattedCost, NamedTextColor.YELLOW, TextDecoration.BOLD));
    }

    /** Shows one rank's daily-reward multiplier in the rank-selection tooltip. */
    public static Component rankEditListMultiplierTooltip(int multiplierPercent) {
        return Component.text("Multiplier: " + multiplierPercent + "%", NamedTextColor.GRAY);
    }

    /** Shows one rank's casino rakeback rate in the rank-selection tooltip. */
    public static Component rankEditListRakebackTooltip(int rakebackPercent) {
        return Component.text("Rakeback: " + rakebackPercent + "%", NamedTextColor.GRAY);
    }

    /** Instructs an administrator that a rank-selection button opens that rank's editor. */
    public static Component rankEditListInstructionTooltip() {
        return Component.text("Click to edit this rank.", NamedTextColor.GREEN);
    }

    /** Titles the top-level rank editor dialog. */
    public static Component rankEditListTitle() {
        return MM.deserialize("<!italic><green><bold>Edit Ranks");
    }

    /** Explains the purpose of the top-level rank editor dialog. */
    public static Component rankEditListBody() {
        return Component.text("Select a rank to edit its attributes.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Labels the button that opens a rank's display-name editor. */
    public static Component rankEditNameButtonLabel() {
        return Component.text("Edit Name", NamedTextColor.AQUA, TextDecoration.BOLD);
    }

    /** Shows the currently configured display name in the name-editor tooltip. */
    public static Component rankEditNameTooltip(Component displayName) {
        return Component.text("Current: ", NamedTextColor.GRAY).append(displayName);
    }

    /** Labels the button that opens a rank's purchase-cost editor. */
    public static Component rankEditCostButtonLabel() {
        return Component.text("Edit Cost", NamedTextColor.GOLD, TextDecoration.BOLD);
    }

    /** Shows the currently configured purchase cost in the cost-editor tooltip. */
    public static Component rankEditCostTooltip(String formattedCost) {
        return Component.text("Current: " + formattedCost, NamedTextColor.GRAY);
    }

    /** Labels the button that opens a rank's daily-reward multiplier editor. */
    public static Component rankEditMultiplierButtonLabel() {
        return Component.text("Edit Multiplier", NamedTextColor.GREEN, TextDecoration.BOLD);
    }

    /** Shows the current multiplier and its accepted fraction format. */
    public static Component rankEditMultiplierTooltip(double multiplier) {
        return Component.text("Current: " + multiplier + " (e.g. 0.01 = 1%)", NamedTextColor.GRAY);
    }

    /** Labels the button that opens a rank's casino-rakeback editor. */
    public static Component rankEditRakebackButtonLabel() {
        return Component.text("Edit Rakeback", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD);
    }

    /** Shows the current rakeback value and its accepted fraction format. */
    public static Component rankEditRakebackTooltip(double rakeback) {
        return Component.text("Current: " + rakeback + " (e.g. 0.05 = 5%)", NamedTextColor.GRAY);
    }

    /** Labels the dialog action that returns to the top-level rank list. */
    public static Component rankEditBackButtonLabel() {
        return Component.text("← Back to Ranks", NamedTextColor.RED, TextDecoration.BOLD);
    }

    /** Explains that the dialog action returns to the top-level rank list. */
    public static Component rankEditBackTooltip() {
        return Component.text("Return to the rank list.", NamedTextColor.GRAY);
    }

    /** Titles the attribute-selection dialog for a configured rank. */
    public static Component rankEditMenuTitle(Component displayName) {
        return Component.text("Rank: ", NamedTextColor.GREEN)
                .append(displayName)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Explains the purpose of the rank attribute-selection dialog. */
    public static Component rankEditMenuBody() {
        return Component.text("Select an attribute to edit.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Labels the confirmation action that applies an entered rank value. */
    public static Component rankEditSaveButtonLabel() {
        return Component.text("Save", NamedTextColor.GREEN, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Explains that the confirmation action applies the submitted rank value. */
    public static Component rankEditSaveTooltip() {
        return Component.text("Apply the entered value.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Labels the confirmation-dialog action that cancels rank editing. */
    public static Component rankEditCancelButtonLabel() {
        return Component.text("Cancel", NamedTextColor.RED, TextDecoration.BOLD);
    }

    /** Explains that the cancel action returns to the selected rank's editor. */
    public static Component rankEditCancelTooltip() {
        return Component.text("Return to the rank menu.", NamedTextColor.GRAY);
    }

    /** Titles an editor dialog for one mutable rank attribute. */
    public static Component rankEditPromptTitle(String capitalizedAttribute, Component displayName) {
        return Component.text("Edit " + capitalizedAttribute + ": Rank ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(displayName)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Shows the current attribute value above a rank-editor text input. */
    public static Component rankEditPromptBody(String currentValue) {
        return Component.text("Current value: " + currentValue + ". Enter a new value below.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Labels the text input used to submit an edited rank value. */
    public static Component rankEditValueInputLabel() {
        return Component.text("Value", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false);
    }
}
