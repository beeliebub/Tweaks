package me.beeliebub.tweaks.minigames.roulette;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.ranks.RankManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Placed in the production package (no {@code tests.} prefix) so package-private access to
 * {@link RouletteSessionManager#endRound}/{@link RouletteSessionManager#shutdown} is legal without
 * widening production visibility — mirrors this package's other test-location exceptions.
 *
 * <p>Exercises the teardown funnel entirely through {@link RouletteSessionManager#handleSegmentClick}
 * and {@link RouletteSessionManager#endRound}/{@code shutdown} — never through {@code RouletteRenderer}
 * spin/holo entity spawning, which MockBukkit does not support (same boundary
 * {@code BlackjackInactivityTimeoutTest} documents for {@code ItemDisplay}). Every scenario below
 * stays in {@code BETTING} (no watcher path is ever entered, so no ball/hologram entity is spawned)
 * — {@code endRoundVisuals}' renderer calls are still exercised, but wrapped in {@code safeRender},
 * so a MockBukkit gap there (e.g. scoreboard teams) fails safe rather than failing the test.
 */
class RouletteEndRoundTest {

    private static final int STAKE = 100;

    private ServerMock server;
    private Tweaks plugin;
    private EconomyManager economyManager;

    private RouletteBoardStore.BoardEntry board;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        economyManager = plugin.getEconomyManager();

        World world = server.addSimpleWorld("casino");
        Location center = new Location(world, 0.5, 65.0, 0.5);
        Location control = new Location(world, 2.0, 65.0, 0.5);
        board = new RouletteBoardStore.BoardEntry(center, 1, 1000, 16, control, BlockFace.NORTH, false);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private RouletteSessionManager freshSessionManager() {
        HouseAccount houseAccount = new HouseAccount(plugin);
        RankManager rankManager = plugin.getRankManager();
        RouletteBoardStore boardStore = new RouletteBoardStore(plugin);
        RouletteRestPoseStore restPoseStore = new RouletteRestPoseStore(plugin);
        RouletteRenderer renderer = new RouletteRenderer(plugin, houseAccount, restPoseStore);
        return new RouletteSessionManager(
                plugin, economyManager, houseAccount, rankManager, boardStore, renderer, restPoseStore);
    }

    private RouletteBoardStore.SegmentRef straightUpRef(int pocket) {
        return new RouletteBoardStore.SegmentRef(board, BetType.STRAIGHT, pocket);
    }

    // ---- shutdown during BETTING: the only refund path -----------------------------------

    @Test
    void shutdownDuringBettingRefundsExactlyOnce() {
        RouletteSessionManager sessions = freshSessionManager();
        PlayerMock player = server.addPlayer();
        UUID playerId = player.getUniqueId();
        economyManager.setBalance(playerId, 1000L);

        sessions.setStake(playerId, STAKE);
        sessions.handleSegmentClick(player, straightUpRef(7));
        long balanceAfterBet = economyManager.getBalance(playerId);
        assertEquals(900L, balanceAfterBet, "the stake must be debited on placement");

        sessions.shutdown();

        assertEquals(1000L, economyManager.getBalance(playerId),
                "shutdown mid-BETTING must refund the full wager");
    }

    @Test
    void rejectedShutdownRefundIsRoutedToTheHouse() throws Exception {
        HouseAccount house = new HouseAccount(plugin);
        house.whenLoaded().get(5, TimeUnit.SECONDS);
        RouletteBoardStore boardStore = new RouletteBoardStore(plugin);
        RouletteRestPoseStore restPoseStore = new RouletteRestPoseStore(plugin);
        RouletteRenderer renderer = new RouletteRenderer(plugin, house, restPoseStore);
        RouletteSessionManager sessions = new RouletteSessionManager(
                plugin, economyManager, house, plugin.getRankManager(), boardStore, renderer, restPoseStore);
        PlayerMock player = server.addPlayer();
        UUID playerId = player.getUniqueId();
        economyManager.setBalance(playerId, 1000L);

        sessions.setStake(playerId, STAKE);
        sessions.handleSegmentClick(player, straightUpRef(7));
        seedBalance(playerId, EconomyManager.MAX_BALANCE);

        sessions.shutdown();

        assertEquals(100L, house.balance(), "an unrepresentable refund must remain recoverable in House");
    }

    private void seedBalance(UUID playerId, double balance) throws Exception {
        economyManager.unloadAndSavePlayer(playerId);
        economyManager.saveAll().get(5, TimeUnit.SECONDS);
        File file = new File(plugin.getDataFolder(), "players/" + playerId + ".yml");
        org.bukkit.configuration.file.YamlConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        config.set("balance", balance);
        config.save(file);
        economyManager.loadPlayer(playerId);
    }

    @Test
    void secondEndRoundCallForTheSameBoardIsANoOp() {
        RouletteSessionManager sessions = freshSessionManager();
        PlayerMock player = server.addPlayer();
        UUID playerId = player.getUniqueId();
        economyManager.setBalance(playerId, 1000L);

        sessions.setStake(playerId, STAKE);
        sessions.handleSegmentClick(player, straightUpRef(7));

        sessions.endRound(board, RouletteTeardownPolicy.EndReason.SHUTDOWN);
        long balanceAfterFirstTeardown = economyManager.getBalance(playerId);
        assertEquals(1000L, balanceAfterFirstTeardown, "the wager must be refunded once");

        // The context was already removed by the first call — a second call for the same board
        // must be a silent no-op, never a second refund.
        sessions.endRound(board, RouletteTeardownPolicy.EndReason.SHUTDOWN);
        assertEquals(balanceAfterFirstTeardown, economyManager.getBalance(playerId),
                "a repeated endRound call for an already-torn-down board must not refund twice");
    }

    // ---- chunk unload on a BETTING round: draw-then-settle, never a refund ---------------

    @Test
    void chunkUnloadOnABettingRoundSettlesInsteadOfRefunding() {
        RouletteSessionManager sessions = freshSessionManager();
        PlayerMock player = server.addPlayer();
        UUID playerId = player.getUniqueId();
        economyManager.setBalance(playerId, 1000L);

        sessions.setStake(playerId, STAKE);
        sessions.handleSegmentClick(player, straightUpRef(7));
        long balanceBeforeBet = 1000L;
        long balanceAfterBet = economyManager.getBalance(playerId);
        assertEquals(900L, balanceAfterBet);
        assertTrue(sessions.hasRoundInFlight(board), "the round must be BETTING before teardown");

        sessions.endRound(board, RouletteTeardownPolicy.EndReason.CHUNK_UNLOAD);

        // A plain refund would return the balance to exactly balanceBeforeBet (net 0). A real
        // settlement instead either loses the stake (balance stays at balanceAfterBet) or wins
        // 36:1 (balance jumps far above balanceBeforeBet) — either way, never exactly the
        // pre-bet balance, which is what distinguishes "settled" from "refunded" without needing
        // to control the drawn pocket's RNG.
        long finalBalance = economyManager.getBalance(playerId);
        assertNotEquals(balanceBeforeBet, finalBalance,
                "a chunk-unloaded BETTING round must settle (win or lose), never refund");
        assertTrue(finalBalance == balanceAfterBet || finalBalance > balanceBeforeBet,
                "the settled balance must reflect either a loss (unchanged from post-bet) or a "
                        + "36:1 win (well above the pre-bet balance)");
        assertFalse(sessions.hasRoundInFlight(board), "the round must be fully torn down after settling");
    }
}
