package me.beeliebub.tweaks.tests.core;

import me.beeliebub.tweaks.core.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Messages}, focused on the factory methods that take parameters —
 * the bare constant-style methods ({@code noPermission}, {@code invalidNumber}, etc.) carry no
 * logic to verify beyond "it returns a non-null Component," which these tests also cover.
 *
 * <p>Deliberately avoids {@code PlainTextComponentSerializer}: MockBukkit registers a
 * {@code PlainTextComponentSerializer$Provider} service (discovered via the JVM ServiceLoader
 * regardless of whether MockBukkit is actually started in this test) that is currently broken
 * under this project's pinned Paper/Adventure versions. Flat component contracts use
 * {@link TextComponent#content()}; composite contracts are recursively inspected with
 * {@link #fullContent(Component)}.
 */
class MessagesTest {

    private static String content(Component component) {
        assertInstanceOf(TextComponent.class, component, "message factories return a plain TextComponent");
        return ((TextComponent) component).content();
    }

    private static void assertMessage(Component component, String expectedContent, NamedTextColor expectedColor) {
        assertEquals(expectedContent, content(component));
        assertEquals(expectedColor, component.color());
    }

    private static String fullContent(Component component) {
        StringBuilder content = new StringBuilder(content(component));
        for (Component child : component.children()) {
            content.append(fullContent(child));
        }
        return content.toString();
    }

    @Test
    void noPermissionMentionsPermission() {
        assertTrue(content(Messages.noPermission()).toLowerCase().contains("permission"));
    }

    @Test
    void playerNotOnlineIncludesTheGivenName() {
        String text = content(Messages.playerNotOnline("Steve"));
        assertTrue(text.contains("Steve"), "message must include the player name");
        assertTrue(text.contains("not online"));
    }

    @Test
    void playerNotOnlineDiffersPerName() {
        String a = content(Messages.playerNotOnline("Alice"));
        String b = content(Messages.playerNotOnline("Bob"));
        assertNotEquals(a, b, "different names must produce different messages");
    }

    @Test
    void invalidNumberMentionsInteger() {
        assertTrue(content(Messages.invalidNumber()).toLowerCase().contains("integer"));
    }

    @Test
    void invalidDecimalMentionsDecimal() {
        assertTrue(content(Messages.invalidDecimal()).toLowerCase().contains("decimal"));
    }

    @Test
    void sharedMiniMessageInstanceIsUsableAndSingleton() {
        assertSame(Messages.MM, Messages.MM, "MM must be a single shared instance");
        assertEquals("hi", content(Messages.MM.deserialize("hi")));
    }

    @Test
    void economyMessagesPreserveTextParametersAndColors() {
        assertMessage(Messages.balanceConsoleMustSpecifyPlayer(),
                "Console must specify a player: /balance <player>", NamedTextColor.RED);
        assertMessage(Messages.balanceHideConsoleCannotUse(), "Console cannot use /balance hide.", NamedTextColor.RED);
        assertEquals("Your balance is now hidden from the tab list.", fullContent(Messages.balanceHidden()));
        assertEquals("Your balance is now visible in the tab list.", fullContent(Messages.balanceVisible()));
        assertMessage(Messages.balanceModifyNoPermission(),
                "You don't have permission to modify balances.", NamedTextColor.RED);
        assertMessage(Messages.balanceMutationUsage("add"),
                "Usage: /balance add <player> <amount>", NamedTextColor.RED);
        assertMessage(Messages.balanceInvalidAmount("oops"), "Invalid amount: oops", NamedTextColor.RED);
        assertEquals("Balance of Alex add by $50. New balance: $125", fullContent(
                Messages.balanceMutationSuccess("Alex", "add", "$50", "$125")));
        assertMessage(Messages.balanceViewOtherNoPermission(),
                "You don't have permission to view other players' balances.", NamedTextColor.RED);
        assertEquals("Alex's balance: $125", fullContent(Messages.balanceOtherPlayer("Alex", "$125")));
        assertEquals("Your balance: $125", fullContent(Messages.balanceOwn("$125")));
        assertEquals("Daily reward: +$100 (Day 1 streak)", fullContent(Messages.dailyReward("$100", 1)));
    }

    @Test
    void b2NamespaceMessagesPreserveRepresentativePresentationContracts() {
        assertMessage(Messages.COMMANDS.condenseNothingToCondense(), "Nothing to condense.", NamedTextColor.YELLOW);
        assertEquals("You received 2x 'starter'. Use /reward claim to collect.",
                fullContent(Messages.MINIGAMES.rewardReceived(2, "starter")));
        assertEquals("BLACKJACK! You won $150!", fullContent(
                Messages.MINIGAMES.blackjackSettlementSummary("PLAYER_BLACKJACK", 100, 250, 0)));

        Component groups = Messages.PERMISSIONS.groupsLabel();
        assertMessage(groups, "Groups", NamedTextColor.GREEN);
        assertEquals(TextDecoration.State.TRUE, groups.decoration(TextDecoration.BOLD));
        assertEquals("Permissions", fullContent(Messages.PERMISSIONS.mainTitle()));
        assertEquals(TextDecoration.State.FALSE,
                Messages.PERMISSIONS.mainTitle().decoration(TextDecoration.ITALIC));
        assertEquals("2 permissions — Page 1 of 3", fullContent(Messages.PERMISSIONS.pageSummary(
                2, me.beeliebub.tweaks.core.PermissionMessages.PageNoun.PERMISSION, 0, 3)));
        assertEquals("✓ tweaks.admin", fullContent(Messages.PERMISSIONS.permissionToggleLabel("tweaks.admin", true)));
    }

    @Test
    void e1ResourceWorldAndProfileMessagesPreservePresentationContracts() {
        assertMessage(Messages.MINIGAMES.resourceWorldLoginEjected(),
                "For your safety, returning you to the main survival world!", NamedTextColor.YELLOW);
        assertMessage(Messages.MINIGAMES.resourceWorldNetherRoofRedirected(),
                "The Nether roof is off-limits; redirecting you to a safe platform.", NamedTextColor.GOLD);
        assertMessage(Messages.MINIGAMES.resourceWorldEnderChestDisabled(),
                "Ender chests are disabled in resource worlds!", NamedTextColor.RED);
        assertMessage(Messages.PROFILES.profileSwapDataCorrupt(),
                "Profile swap aborted: destination data is corrupt. Please report this.", NamedTextColor.RED);
        assertMessage(Messages.PROFILES.profileSwitched("lobby"),
                "Inventory profile switched to: lobby", NamedTextColor.YELLOW);
    }

    @Test
    void deathInventoryMessagesPreserveTextParametersAndColors() {
        assertMessage(Messages.deathInventoryItemGiven(), "Item given.", NamedTextColor.GREEN);
        assertMessage(Messages.deathInventoryPlayerHasNotPlayed("Alex"),
                "Player 'Alex' has not played before.", NamedTextColor.RED);
        assertMessage(Messages.deathInventoryNoneFound("Alex"),
                "No death inventories found for Alex.", NamedTextColor.YELLOW);
        assertMessage(Messages.deathInventoryListHeader("Alex"), "Death inventories for Alex:", NamedTextColor.GOLD);
        assertMessage(Messages.deathInventoryListEntry("123", "2026-07-25 10:00:00"),
                "  123  (2026-07-25 10:00:00)", NamedTextColor.GRAY);
        assertMessage(Messages.deathInventoryNotFound("123", "Alex"),
                "No death inventory with ID '123' for Alex.", NamedTextColor.RED);
        assertMessage(Messages.deathInventoryRestoreTargetOffline("Alex"),
                "Alex must be online to restore their inventory.", NamedTextColor.RED);
        assertMessage(Messages.deathInventoryRestored("Alex"), "Restored inventory for Alex.", NamedTextColor.GREEN);
        assertMessage(Messages.deathInventoryRestoreNotice(),
                "An admin restored your inventory from a previous death.", NamedTextColor.YELLOW);
        assertMessage(Messages.deathInventoryGuiRequiresPlayer(),
                "Only players can open the death inventory GUI. Use 'restore' to restore.", NamedTextColor.RED);
        assertMessage(Messages.deathInventoryGuiTitle("Alex", "2026-07-25 10:00:00"),
                "Alex @ 2026-07-25 10:00:00", NamedTextColor.DARK_AQUA);
        assertMessage(Messages.deathInventoryUsage("deathinventory"),
                "Usage: /deathinventory <player> list | /deathinventory <player> <id> [restore]",
                NamedTextColor.YELLOW);
    }

    @Test
    void rankMessagesPreserveTextParametersAndColors() {
        assertMessage(Messages.ranksEditRequiresPlayer(), "Only players can edit ranks.", NamedTextColor.RED);
        assertMessage(Messages.ranksListHeader(), "--- Ranks ---", NamedTextColor.GOLD);
        assertEquals("» Rank I — Cost: $1,000 | Bonus: 1% | Rakeback: 0% (current)",
                fullContent(Messages.ranksListEntry("» ", Component.text("I", NamedTextColor.YELLOW),
                        "$1,000", 1, 0, true, NamedTextColor.YELLOW)));
        assertMessage(Messages.ranksUnrankedNotice(),
                "You are currently Unranked. Use /rankup to purchase Rank I.", NamedTextColor.GRAY);
        assertMessage(Messages.ranksMaximumRankNotice(), "You have reached the maximum rank!", NamedTextColor.GOLD);
        assertMessage(Messages.rankupRequiresPlayer(), "Only players can use this command.", NamedTextColor.RED);
        assertMessage(Messages.rankupAlreadyMaximumRank(), "You are already the maximum rank.", NamedTextColor.RED);
        Component insufficient = Messages.rankupInsufficientFunds(Component.text("I", NamedTextColor.YELLOW),
                "$1,000", "$25");
        assertEquals("Insufficient funds. Rank I costs $1,000 but you only have $25.", fullContent(insufficient));
        assertEquals(NamedTextColor.RED, insufficient.color());
        Component success = Messages.rankupSuccess(Component.text("I", NamedTextColor.YELLOW), "$4,000");
        assertEquals("Congratulations! You are now Rank I. Remaining balance: $4,000.", fullContent(success));
        assertEquals(NamedTextColor.GREEN, success.color());
        assertMessage(Messages.rankSetUsage(), "Usage: /rank set <player> <rank_id/name>", NamedTextColor.RED);
        assertMessage(Messages.rankSetInvalidRank("bogus"), "Invalid rank: bogus", NamedTextColor.RED);
        assertEquals("Successfully set Alex's rank to I.",
                fullContent(Messages.rankSetSuccess("Alex", Component.text("I", NamedTextColor.YELLOW))));
        assertMessage(Messages.rankEditNameCannotBeEmpty(), "Name cannot be empty.", NamedTextColor.RED);
        assertEquals("Set name of Rank 3 to III.",
                fullContent(Messages.rankEditNameUpdated(3, Component.text("III", NamedTextColor.GOLD))));
        assertMessage(Messages.rankEditUnknownAttribute("level"), "Unknown attribute: level", NamedTextColor.RED);
        assertEquals("Set Multiplier of Rank III to 0.05.", fullContent(
                Messages.rankEditAttributeUpdated("Multiplier", Component.text("III", NamedTextColor.GOLD), 0.05)));
    }

    @Test
    void rankEditorMessagesPreserveTextParametersAndColors() {
        assertEquals("Rank III — $5,000", fullContent(
                Messages.rankEditListButtonLabel(Component.text("III", NamedTextColor.GOLD), "$5,000")));
        assertMessage(Messages.rankEditListMultiplierTooltip(3), "Multiplier: 3%", NamedTextColor.GRAY);
        assertMessage(Messages.rankEditListRakebackTooltip(2), "Rakeback: 2%", NamedTextColor.GRAY);
        assertMessage(Messages.rankEditListInstructionTooltip(), "Click to edit this rank.", NamedTextColor.GREEN);
        assertEquals("Edit Ranks", fullContent(Messages.rankEditListTitle()));
        assertMessage(Messages.rankEditListBody(), "Select a rank to edit its attributes.", NamedTextColor.GRAY);

        assertMessage(Messages.rankEditNameButtonLabel(), "Edit Name", NamedTextColor.AQUA);
        assertEquals("Current: III", fullContent(Messages.rankEditNameTooltip(Component.text("III", NamedTextColor.GOLD))));
        assertMessage(Messages.rankEditCostButtonLabel(), "Edit Cost", NamedTextColor.GOLD);
        assertMessage(Messages.rankEditCostTooltip("$5,000"), "Current: $5,000", NamedTextColor.GRAY);
        assertMessage(Messages.rankEditMultiplierButtonLabel(), "Edit Multiplier", NamedTextColor.GREEN);
        assertMessage(Messages.rankEditMultiplierTooltip(0.03), "Current: 0.03 (e.g. 0.01 = 1%)", NamedTextColor.GRAY);
        assertMessage(Messages.rankEditRakebackButtonLabel(), "Edit Rakeback", NamedTextColor.LIGHT_PURPLE);
        assertMessage(Messages.rankEditRakebackTooltip(0.02), "Current: 0.02 (e.g. 0.05 = 5%)", NamedTextColor.GRAY);
        assertMessage(Messages.rankEditBackButtonLabel(), "← Back to Ranks", NamedTextColor.RED);
        assertMessage(Messages.rankEditBackTooltip(), "Return to the rank list.", NamedTextColor.GRAY);
        assertEquals("Rank: III", fullContent(Messages.rankEditMenuTitle(Component.text("III", NamedTextColor.GOLD))));
        assertMessage(Messages.rankEditMenuBody(), "Select an attribute to edit.", NamedTextColor.GRAY);

        assertMessage(Messages.rankEditSaveButtonLabel(), "Save", NamedTextColor.GREEN);
        assertMessage(Messages.rankEditSaveTooltip(), "Apply the entered value.", NamedTextColor.GRAY);
        assertMessage(Messages.rankEditCancelButtonLabel(), "Cancel", NamedTextColor.RED);
        assertMessage(Messages.rankEditCancelTooltip(), "Return to the rank menu.", NamedTextColor.GRAY);
        assertEquals("Edit Cost: Rank III", fullContent(
                Messages.rankEditPromptTitle("Cost", Component.text("III", NamedTextColor.GOLD))));
        assertMessage(Messages.rankEditPromptBody("$5,000"),
                "Current value: $5,000. Enter a new value below.", NamedTextColor.GRAY);
        assertMessage(Messages.rankEditValueInputLabel(), "Value", NamedTextColor.YELLOW);
    }
}
