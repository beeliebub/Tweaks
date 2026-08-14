package me.beeliebub.tweaks.minigames.roulette;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Main-thread gateway coverage for designation-dependent Discord betting outcomes. */
class RouletteDiscordDesignationTest {

    private ServerMock server;
    private Tweaks plugin;
    private EconomyManager economy;
    private RouletteBoardStore store;
    private RouletteSessionManager sessions;
    private RouletteBoardStore.BoardEntry designated;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        economy = plugin.getEconomyManager();
        HouseAccount house = new HouseAccount(plugin);
        store = new RouletteBoardStore(plugin);
        RouletteRestPoseStore restPose = new RouletteRestPoseStore(plugin);
        RouletteRenderer renderer = new RouletteRenderer(plugin, house, restPose);
        sessions = new RouletteSessionManager(plugin, economy, house, plugin.getRankManager(), store,
                renderer, restPose);

        World world = server.addSimpleWorld("discord-casino");
        RouletteBoardStore.BoardEntry base = new RouletteBoardStore.BoardEntry(
                new Location(world, 8.5, 65, 8.5), 1, 1_000, 2,
                new Location(world, 10, 65, 8), BlockFace.NORTH, false);
        store.persistBoard(base);
        designated = store.setDiscordDesignation(base, true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void activateWithoutEntities() {
        store.registerHitboxes(designated, Map.of(UUID.randomUUID(),
                new RouletteBoardStore.SegmentRef(designated, BetType.STRAIGHT, 1)));
    }

    @Test
    void noDesignatedBoardDiffersFromDesignatedInactiveBoard() {
        RouletteBoardStore.BoardEntry inactive = new RouletteBoardStore.BoardEntry(
                designated.center(), designated.minBet(), designated.maxBet(), designated.scanRadius(),
                designated.control(), designated.controlFacing(), false);
        store.setDiscordDesignation(designated, false);
        assertEquals(DiscordBetOutcome.NO_DESIGNATED_BOARD,
                sessions.placeDiscordBet(UUID.randomUUID(), BetType.STRAIGHT, 1, 10).outcome());

        store.setDiscordDesignation(inactive, true);
        assertEquals(DiscordBetOutcome.BOARD_UNAVAILABLE,
                sessions.placeDiscordBet(UUID.randomUUID(), BetType.STRAIGHT, 1, 10).outcome());
    }

    @Test
    void neverJoinedOfflineAccountIsRejectedWithoutBalanceMutation() {
        activateWithoutEntities();
        UUID player = UUID.randomUUID();

        DiscordBetResult result = sessions.placeDiscordBet(player, BetType.STRAIGHT, 1, 10);

        assertEquals(DiscordBetOutcome.INSUFFICIENT_FUNDS, result.outcome());
        assertEquals(0L, economy.getBalance(player));
    }

    @Test
    void offlinePreviouslyPlayedAccountCanBetAndOpensTheRound() {
        activateWithoutEntities();
        UUID player = UUID.randomUUID();
        economy.setBalance(player, 100L);

        DiscordBetResult result = sessions.placeDiscordBet(player, BetType.STRAIGHT, 1, 10);

        assertEquals(DiscordBetOutcome.PLACED, result.outcome());
        assertEquals(90L, economy.getBalance(player));
        assertEquals(1, sessions.discordBetsFor(player).size());
        assertTrue(sessions.discordGateway().discordBoardStatus().state() == RouletteRound.State.BETTING);
        sessions.shutdown();
    }

    @Test
    void cumulativeExposureIsSharedByDiscordBets() {
        activateWithoutEntities();
        UUID player = UUID.randomUUID();
        economy.setBalance(player, Integer.MAX_VALUE);
        RouletteBoardStore.BoardEntry wide = new RouletteBoardStore.BoardEntry(
                designated.center(), 1, RouletteRound.MAX_CUMULATIVE_WAGER, designated.scanRadius(),
                designated.control(), designated.controlFacing(), true);
        assertTrue(store.persistBoard(wide));
        store.setDiscordDesignation(designated, false);
        assertTrue(store.setDiscordDesignation(wide, true) != null);
        store.clearBoardRuntime(designated);
        store.registerHitboxes(wide, Map.of(UUID.randomUUID(),
                new RouletteBoardStore.SegmentRef(wide, BetType.STRAIGHT, 1)));

        assertEquals(DiscordBetOutcome.PLACED,
                sessions.placeDiscordBet(player, BetType.STRAIGHT, 1,
                        RouletteRound.MAX_CUMULATIVE_WAGER).outcome());
        assertEquals(DiscordBetOutcome.EXPOSURE_LIMIT,
                sessions.placeDiscordBet(player, BetType.STRAIGHT, 2, 1).outcome());
        sessions.shutdown();
    }
}
