package me.beeliebub.tweaks.minigames.blackjack;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.BalanceMutationResult;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.ranks.RankManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlackjackSessionManagerEconomyTest {

    private ServerMock server;
    private BlackjackSessionManager sessions;
    private EconomyManager economy;
    private HouseAccount house;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Tweaks plugin = MockBukkit.load(Tweaks.class);
        economy = mock(EconomyManager.class);
        house = mock(HouseAccount.class);
        sessions = new BlackjackSessionManager(plugin, economy, house,
                mock(RankManager.class), mock(BlackjackRenderer.class));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rejectedCreditRoutesTheAmountToTheHouse() {
        UUID player = UUID.randomUUID();
        when(economy.addBalance(player, 250L)).thenReturn(BalanceMutationResult.REJECTED_UNREPRESENTABLE);
        when(house.credit(250L)).thenReturn(true);

        sessions.creditOrRouteToHouse(player, "Player", 250L, "payout");

        verify(economy).addBalance(player, 250L);
        verify(house).credit(250L);
    }
}
