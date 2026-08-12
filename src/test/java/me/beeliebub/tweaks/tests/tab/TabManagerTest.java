package me.beeliebub.tweaks.tests.tab;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.BalanceCommand;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.ranks.RankManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the unified tab-list renderer (TabManager) and its wiring
 * into EconomyManager and RankManager.
 *
 * All assertions go through the public seams: plugin.getEconomyManager() and
 * plugin.getRankManager(). The tab name is read back via player.playerListName()
 * and serialized to plain text for comparison.
 *
 * Note on scoreboard teams: MockBukkit's scoreboard implementation supports
 * registerNewTeam / getTeam / addPlayer fully. No workarounds are required.
 * The TabManager.assignTeam path (called inside onJoin) exercises that path
 * as a side effect of server.addPlayer(); we do not assert on team membership
 * in these tests — only on playerListName.
 */
class TabManagerTest {

    private ServerMock server;
    private Tweaks plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ---- helper ----------------------------------------------------------------

    /** Serializes a nullable Adventure component to plain text. */
    private static String plain(Component component) {
        if (component == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    // ============================================================
    // Test 1: Balance shows in tab after setBalance
    // ============================================================

    /**
     * Asserts that after setBalance the player's tab name is non-null and its
     * plain text contains the dollar-formatted balance produced by
     * BalanceCommand.formatBalance.
     *
     * Covers: EconomyManager.setBalance -> refreshTabFor -> TabManager.refreshTab
     */
    @Test
    void balanceShowsInTabAfterSetBalance() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        EconomyManager em = plugin.getEconomyManager();

        em.setBalance(uuid, 500L);

        Component listName = player.playerListName();
        assertNotNull(listName, "playerListName must be set after setBalance");

        String plain = plain(listName);
        String expected = BalanceCommand.formatBalance(500L); // "$500"
        assertTrue(plain.contains(expected),
                "Tab plain text '" + plain + "' should contain '" + expected + "'");
    }

    // ============================================================
    // Test 2: Hidden balance omits the amount from tab
    // ============================================================

    /**
     * Asserts that after marking the balance hidden, the formatted balance string
     * no longer appears in the player's tab entry, even though the balance is set.
     *
     * Covers: EconomyManager.setBalanceHidden -> refreshTabFor -> TabManager.refreshTab
     * (the !isBalanceHidden guard inside refreshTab)
     */
    @Test
    void hiddenBalanceOmitsAmountFromTab() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        EconomyManager em = plugin.getEconomyManager();

        em.setBalance(uuid, 500L);
        em.setBalanceHidden(uuid, true);

        String plain = plain(player.playerListName());
        String formatted = BalanceCommand.formatBalance(500L); // "$500"
        assertFalse(plain.contains(formatted),
                "Tab plain text '" + plain + "' should NOT contain '" + formatted
                        + "' when balance is hidden");
    }

    @Test
    void rankChangesRefreshTabImmediatelyWithoutRejoin() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        EconomyManager em = plugin.getEconomyManager();

        String before = plain(player.playerListName());
        em.setRank(uuid, 1);
        String after = plain(player.playerListName());

        assertNotEquals(before, after, "setRank must refresh the online player's tab entry immediately");
        assertTrue(after.contains("[I]"), "Tab plain text '" + after + "' should contain the new rank bracket");
    }

    // ============================================================
    // Test 3: Rank name (with color codes) renders in tab after setRankName
    // ============================================================

    /**
     * Asserts that:
     *  (a) after setting a player's rank to 1 and calling setRankName(1, "&aVIP"),
     *      the plain tab text contains "VIP";
     *  (b) the literal "&a" color code is NOT in the plain text (ColorUtil.parse stripped it).
     *
     * Covers: RankManager.setRankName -> TabManager.refreshTab (for all online players);
     * also verifies ColorUtil.parse correctly processes '&'-prefixed codes.
     *
     * Strategy: setRank sets the player's stored rank. Then setRankName triggers the
     * all-online-players refresh inside RankManager, so no manual balance call is needed.
     */
    @Test
    void rankNameWithColorCodesRendersInTabAfterSetRankName() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        EconomyManager em = plugin.getEconomyManager();
        RankManager rm = plugin.getRankManager();

        // Assign rank 1 to the player so the tab renderer picks up that rank's name.
        em.setRank(uuid, 1);

        // setRankName refreshes all online players — the player above is online.
        rm.setRankName(1, "&aVIP");

        String plain = plain(player.playerListName());

        assertTrue(plain.contains("VIP"),
                "Tab plain text '" + plain + "' should contain 'VIP' after setRankName(1, \"&aVIP\")");
        assertFalse(plain.contains("&a"),
                "Tab plain text '" + plain + "' should NOT contain the literal '&a' — ColorUtil must strip color codes");
    }

    // ============================================================
    // Test 4: World prefix is present in tab text
    // ============================================================

    /**
     * Asserts that a newly joined player's tab entry contains a bracketed world tag.
     * MockBukkit provides a single default world; whatever world key it uses, TabManager
     * maps it to a tag (falling back to "[Survival] " if not in WORLD_TAGS). Either way
     * a "[" character must appear and one of the known tag strings must be present.
     *
     * The assertion is intentionally loose to avoid brittleness from MockBukkit's
     * world-key assignment (the exact key may change between MockBukkit versions).
     */
    @Test
    void worldPrefixPresentInTab() {
        PlayerMock player = server.addPlayer();

        String plain = plain(player.playerListName());

        // A '[' bracket is always present: every tag is formatted as "[Xxx] ".
        assertTrue(plain.contains("["),
                "Tab plain text '" + plain + "' should contain '[' from the world-prefix tag");

        // At least one of the known world tag labels must appear.
        boolean hasKnownTag =
                plain.contains("Survival") ||
                plain.contains("Lobby")    ||
                plain.contains("Archive")  ||
                plain.contains("Pi")       ||
                plain.contains("Nether")   ||
                plain.contains("End")      ||
                plain.contains("Resource");
        assertTrue(hasKnownTag,
                "Tab plain text '" + plain + "' should contain a known world tag label");
    }
}
