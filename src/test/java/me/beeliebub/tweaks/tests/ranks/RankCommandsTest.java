package me.beeliebub.tweaks.tests.ranks;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.ranks.RankCommand;
import me.beeliebub.tweaks.ranks.RankManager;
import me.beeliebub.tweaks.ranks.RankupCommand;
import me.beeliebub.tweaks.tests.MessageAssert;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RankCommandsTest {

    private ServerMock server;
    private Tweaks plugin;
    private EconomyManager em;
    private RankManager rm;
    private RankupCommand rankupCmd;
    private RankCommand ranksCmd;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        em = plugin.getEconomyManager();
        rm = plugin.getRankManager();
        rankupCmd = new RankupCommand(em, rm);
        ranksCmd = new RankCommand(em, rm);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ---- RankManager loading ------------------------------------------------

    @Test
    void rankManagerLoadsDisplayName() {
        assertEquals("I", rm.getRankDisplayName(1));
    }

    @Test
    void rankManagerLoadsCost() {
        assertEquals(1000.0, rm.getRankCost(1), 0.001);
    }

    @Test
    void rankManagerLoadsMaxRank() {
        assertEquals(10, rm.getMaxRank());
    }

    @Test
    void rankManagerLoadsMultiplierBonus() {
        assertEquals(0.01, rm.getRankMultiplierBonus(1), 0.0001);
    }

    @Test
    void rankManagerLoadsRakebackForMaxRank() {
        assertEquals(0.10, rm.getRankRakeback(10), 0.0001);
    }

    @Test
    void rankZeroIsUnranked() {
        assertEquals("Unranked", rm.getRankDisplayName(0));
    }

    @Test
    void unknownRankReturnsDefaults() {
        assertEquals("Unranked", rm.getRankDisplayName(99));
        assertEquals(0.0, rm.getRankCost(99), 0.0001);
        assertEquals(0.0, rm.getRankMultiplierBonus(99), 0.0001);
        assertEquals(0.0, rm.getRankRakeback(99), 0.0001);
    }

    @Test
    void getCasinoRakebackRateReflectsPlayerRank() {
        // addPlayer() fires PlayerJoinEvent which grants the daily reward; set rank after.
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();

        // Unranked → 0% rakeback.
        assertEquals(0.0, rm.getCasinoRakebackRate(uuid), 0.0001);

        em.setRank(uuid, 5);
        assertEquals(rm.getRankRakeback(5), rm.getCasinoRakebackRate(uuid), 0.0001);
    }

    // ---- /rankup success ----------------------------------------------------

    @Test
    void rankupSuccessDeductsBalanceAndSetsRank() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        drainMessages(player);

        // Set balance explicitly after addPlayer to control state (join event grants 100).
        em.setBalance(uuid, 5000.0);
        em.setRank(uuid, 0);

        rankupCmd.onCommand(player, bukkitCmd, "rankup", new String[0]);

        assertEquals(1, em.getRank(uuid), "rank should advance to 1");
        assertEquals(5000.0 - rm.getRankCost(1), em.getBalance(uuid), 0.001,
                "balance should be reduced by cost of rank 1");
        MessageAssert.assertMessageSent(player, "Congratulations");
    }

    @Test
    void rankupSuccessMessageContainsNewRankName() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        drainMessages(player);

        em.setBalance(uuid, 10000.0);
        em.setRank(uuid, 0);

        rankupCmd.onCommand(player, bukkitCmd, "rankup", new String[0]);

        MessageAssert.assertMessageSent(player, "I");
    }

    // ---- /rankup insufficient funds -----------------------------------------

    @Test
    void rankupInsufficientFundsLeavesRankUnchanged() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        drainMessages(player);

        em.setBalance(uuid, 10.0);   // far below rank-1 cost of 1000
        em.setRank(uuid, 0);

        rankupCmd.onCommand(player, bukkitCmd, "rankup", new String[0]);

        assertEquals(0, em.getRank(uuid), "rank must remain unchanged on insufficient funds");
        assertEquals(10.0, em.getBalance(uuid), 0.001,
                "balance must not change on failed rankup");
        MessageAssert.assertMessageSent(player, "Insufficient");
    }

    // ---- /rankup at max rank ------------------------------------------------

    @Test
    void rankupAtMaxRankDoesNothing() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        drainMessages(player);

        em.setRank(uuid, 10);
        em.setBalance(uuid, 999999.0);

        rankupCmd.onCommand(player, bukkitCmd, "rankup", new String[0]);

        assertEquals(10, em.getRank(uuid), "rank must stay at max");
        assertEquals(999999.0, em.getBalance(uuid), 0.001,
                "balance must not change at max rank");
        MessageAssert.assertMessageSent(player, "maximum rank");
    }

    // ---- RankManager setters ------------------------------------------------

    @Test
    void setRankCostUpdatesGetRankCost() {
        rm.setRankCost(1, 1234.0);
        assertEquals(1234.0, rm.getRankCost(1), 0.001);
    }

    @Test
    void setRankNameUpdatesGetRankDisplayName() {
        rm.setRankName(1, "Alpha");
        assertEquals("Alpha", rm.getRankDisplayName(1));
    }

    @Test
    void setRankMultiplierUpdatesGetRankMultiplierBonus() {
        rm.setRankMultiplier(1, 0.05);
        assertEquals(0.05, rm.getRankMultiplierBonus(1), 0.0001);
    }

    @Test
    void setRankRakebackUpdatesGetRankRakeback() {
        rm.setRankRakeback(1, 0.07);
        assertEquals(0.07, rm.getRankRakeback(1), 0.0001);
    }

    @Test
    void setRankCostOutOfRangeIsIgnored() {
        double before = rm.getRankCost(1);
        rm.setRankCost(99, 9999.0);
        assertEquals(before, rm.getRankCost(1), 0.001,
                "out-of-range setter must not affect valid ranks");
        assertEquals(0.0, rm.getRankCost(99), 0.001,
                "out-of-range rank must still return default");
    }

    // ---- /ranks listing -----------------------------------------------------

    @Test
    void ranksListSendsMessageContainingRankNames() {
        PlayerMock player = server.addPlayer();
        drainMessages(player);

        ranksCmd.onCommand(player, bukkitCmd, "ranks", new String[0]);

        MessageAssert.assertMessageSent(player, "Rank I");
    }

    @Test
    void ranksListMarkersCurrentRank() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        drainMessages(player);

        em.setRank(uuid, 3);

        ranksCmd.onCommand(player, bukkitCmd, "ranks", new String[0]);

        MessageAssert.assertMessageSent(player, "(current)");
    }

    @Test
    void rankupConsecutiveRanksWork() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        drainMessages(player);

        em.setRank(uuid, 4);
        em.setBalance(uuid, 1_000_000.0);

        rankupCmd.onCommand(player, bukkitCmd, "rankup", new String[0]);

        assertEquals(5, em.getRank(uuid), "should advance from rank 4 to rank 5");
    }

    // ---- Helpers ------------------------------------------------------------

    private void drainMessages(PlayerMock player) {
        //noinspection StatementWithEmptyBody
        while (player.nextComponentMessage() != null) { /* discard */ }
    }
}
