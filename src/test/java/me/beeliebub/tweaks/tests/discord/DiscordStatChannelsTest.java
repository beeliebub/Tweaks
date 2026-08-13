package me.beeliebub.tweaks.tests.discord;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.discord.DiscordAnnouncer;
import me.beeliebub.tweaks.discord.DiscordStatChannels;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.lottery.LotteryManager;
import me.beeliebub.tweaks.lottery.LotteryMath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscordStatChannelsTest {

    private ServerMock server;
    private Tweaks plugin;
    private HouseAccount house;
    private LotteryManager lottery;
    private RecordingAnnouncer announcer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        house = mock(HouseAccount.class);
        lottery = mock(LotteryManager.class);
        announcer = new RecordingAnnouncer();
        when(house.isLoaded()).thenReturn(true);
        when(house.balance()).thenReturn(113_053L);
        when(lottery.isLoaded()).thenReturn(true);
        when(lottery.currentPot()).thenReturn(new LotteryMath.PotOutcome.Payable(
                25_000L, 0L, 0L));
        plugin.getConfig().set("discord.house-channel-id", "house-id");
        plugin.getConfig().set("discord.lottery-channel-id", "lottery-id");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void unchangedNamesDoNotPushAgain() {
        DiscordStatChannels stats = new DiscordStatChannels(plugin, house, lottery, announcer);

        stats.refresh();
        stats.refresh();

        assertEquals(List.of("house-id=House Bal: $113,053", "lottery-id=Lottery Pot: $25,000"),
                announcer.renames);
    }

    @Test
    void houseLoadingDoesNotBlockLottery() {
        when(house.isLoaded()).thenReturn(false);
        DiscordStatChannels stats = new DiscordStatChannels(plugin, house, lottery, announcer);

        stats.refresh();

        assertEquals(List.of("lottery-id=Lottery Pot: $25,000"), announcer.renames);
    }

    @Test
    void refusedPotUsesWaitingStateAndBlankIdsAreSkipped() {
        when(lottery.currentPot()).thenReturn(new LotteryMath.PotOutcome.Refused(
                LotteryMath.RefusalReason.NO_GROWTH));
        plugin.getConfig().set("discord.house-channel-id", "");
        DiscordStatChannels stats = new DiscordStatChannels(plugin, house, lottery, announcer);

        stats.refresh();

        assertEquals(List.of("lottery-id=Lottery Pot: waiting"), announcer.renames);
    }

    @Test
    void statRefreshFloorIsFiveMinutes() {
        plugin.getConfig().set("discord.stat-refresh-seconds", 60);

        assertEquals(300, DiscordStatChannels.refreshSeconds(plugin));
    }

    @Test
    void failedRenameIsRetriedOnTheNextRefresh() {
        plugin.getConfig().set("discord.lottery-channel-id", "");
        FailingAnnouncer failing = new FailingAnnouncer();
        DiscordStatChannels stats = new DiscordStatChannels(plugin, house, lottery, failing);

        stats.refresh();
        stats.refresh();

        assertEquals(2, failing.attempts);
    }

    private static final class RecordingAnnouncer implements DiscordAnnouncer {
        private final List<String> renames = new ArrayList<>();

        @Override
        public void announceCard(String message, int color, org.bukkit.OfflinePlayer subject) {
        }

        @Override
        public void announceSettlement(me.beeliebub.tweaks.discord.SettlementLine line) {
        }

        @Override
        public void renameChannel(String channelId, String name) {
            renames.add(channelId + "=" + name);
        }
    }

    private static final class FailingAnnouncer implements DiscordAnnouncer {
        private int attempts;

        @Override
        public void announceCard(String message, int color, org.bukkit.OfflinePlayer subject) {
        }

        @Override
        public void announceSettlement(me.beeliebub.tweaks.discord.SettlementLine line) {
        }

        @Override
        public void renameChannel(String channelId, String name) {
        }

        @Override
        public void renameChannel(String channelId, String name, Runnable onSuccess, Runnable onFailure) {
            attempts++;
            onFailure.run();
        }
    }
}
