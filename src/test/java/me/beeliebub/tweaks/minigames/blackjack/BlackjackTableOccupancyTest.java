package me.beeliebub.tweaks.minigames.blackjack;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.ranks.RankManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlackjackTableOccupancyTest {

    private ServerMock server;
    private Tweaks plugin;
    private EconomyManager economy;
    private HouseAccount house;
    private World world;
    private Location tableCenter;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        economy = mock(EconomyManager.class);
        house = mock(HouseAccount.class);
        world = server.addSimpleWorld("casino");
        tableCenter = new Location(world, 0.5, 65.0, 0.5);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anotherPlayerIsRefusedBeforeTheirBetIsChecked() {
        BlackjackRenderer renderer = mock(BlackjackRenderer.class);
        BlackjackSessionManager sessions = newSessionManager(renderer);
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        sessions.registerGameForTesting(firstPlayer, new BlackjackGame(firstPlayer, 100), tableCenter);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(secondPlayer);
        BlackjackTableStore.TableEntry table = table(100);

        sessions.handleMiddleClick(player, table);

        verify(player).sendMessage(me.beeliebub.tweaks.core.Messages.MINIGAMES.blackjackTableOccupied());
        verify(economy, never()).getBalance(secondPlayer);
        verify(economy, never()).removeBalance(secondPlayer, 100);
        assertTrue(sessions.isTableOccupied(BlackjackTableStore.blockKey(tableCenter)));
    }

    @Test
    void teardownReleasesTheTableBeforeRendererFailure() {
        BlackjackRenderer renderer = mock(BlackjackRenderer.class);
        doThrow(new IllegalStateException("display unavailable"))
                .when(renderer).removeDisplays(any(Session.class));
        BlackjackSessionManager sessions = newSessionManager(renderer);
        UUID playerId = UUID.randomUUID();
        sessions.registerGameForTesting(playerId, new BlackjackGame(playerId, 0), tableCenter);

        try {
            sessions.endSession(playerId);
        } catch (IllegalStateException ignored) {
            // The renderer failure is deliberately allowed to propagate from teardown.
        }

        assertFalse(sessions.hasActiveGame(playerId));
        assertFalse(sessions.isTableOccupied(BlackjackTableStore.blockKey(tableCenter)));
    }

    @Test
    void quitReleasesTheTable() {
        BlackjackSessionManager sessions = newSessionManager(mock(BlackjackRenderer.class));
        UUID playerId = UUID.randomUUID();
        sessions.registerGameForTesting(playerId, new BlackjackGame(playerId, 0), tableCenter);

        sessions.onPlayerQuit(playerId);

        assertFalse(sessions.hasActiveGame(playerId));
        assertFalse(sessions.isTableOccupied(BlackjackTableStore.blockKey(tableCenter)));
    }

    private BlackjackSessionManager newSessionManager(BlackjackRenderer renderer) {
        return new BlackjackSessionManager(plugin, economy, house, mock(RankManager.class), renderer);
    }

    private BlackjackTableStore.TableEntry table(int bet) {
        return new BlackjackTableStore.TableEntry(tableCenter, bet, tableCenter, BlockFace.SOUTH, null);
    }
}
