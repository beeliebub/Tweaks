package me.beeliebub.tweaks.tests.lottery;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.LotteryMessages;
import me.beeliebub.tweaks.discord.DiscordAnnouncer;
import me.beeliebub.tweaks.discord.SettlementLine;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.economy.HousePaymentService;
import me.beeliebub.tweaks.lottery.LotteryCommand;
import me.beeliebub.tweaks.lottery.LotteryManager;
import me.beeliebub.tweaks.lottery.LotteryMath;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.tests.MessageAssert;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LotteryCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private LotteryManager manager;
    private HouseAccount house;
    private HousePaymentService payment;
    private LotteryCommand command;
    private RecordingAnnouncer discord;
    private final Command bukkitCommand = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        manager = mock(LotteryManager.class);
        house = mock(HouseAccount.class);
        payment = mock(HousePaymentService.class);
        when(manager.isLoaded()).thenReturn(true);
        when(house.isLoaded()).thenReturn(true);
        when(manager.entrantCount()).thenReturn(0);
        when(manager.baseline()).thenReturn(10_000L);
        when(manager.fallback()).thenReturn(10_000L);
        when(manager.configuredFallbackBase()).thenReturn(10_000L);
        when(manager.currentPot()).thenReturn(new LotteryMath.PotOutcome.Refused(
                LotteryMath.RefusalReason.NOT_ENOUGH_ENTRANTS));
        when(manager.entrantSnapshot(50)).thenReturn(new LotteryManager.EntrantPage(List.of(), 0));
        when(payment.isReady()).thenReturn(true);
        discord = new RecordingAnnouncer();
        command = new LotteryCommand(plugin, manager, house, payment, discord);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void nonAdminCanViewInfoAndEntriesButNotAdminSubcommands() {
        PlayerMock player = nonAdmin();

        command.onCommand(player, bukkitCommand, "lottery", new String[]{"info"});
        MessageAssert.assertMessageSent(player, "[Lottery]");
        command.onCommand(player, bukkitCommand, "lottery", new String[]{"entries"});
        MessageAssert.assertMessageSent(player, "Entrants (0)");
        command.onCommand(player, bukkitCommand, "lottery", new String[]{"draw"});
        MessageAssert.assertMessageSent(player, "permission");
        command.onCommand(player, bukkitCommand, "lottery", new String[]{"baseline", "10"});
        MessageAssert.assertMessageSent(player, "permission");
        command.onCommand(player, bukkitCommand, "lottery", new String[]{"fallback"});
        MessageAssert.assertMessageSent(player, "permission");
    }

    @Test
    void tabCompletionMatchesPublicAndAdminPermissions() {
        PlayerMock player = nonAdmin();
        assertEquals(List.of("info", "entries"), command.onTabComplete(player, bukkitCommand,
                "lottery", new String[]{""}));

        player.addAttachment(plugin, Permissions.LOTTERY_ADMIN, true);
        assertEquals(List.of("info", "entries", "draw", "baseline", "fallback"),
                command.onTabComplete(player, bukkitCommand, "lottery", new String[]{""}));
    }

    @Test
    void entriesAreBoundedAndRateLimitedForNonAdmins() {
        PlayerMock player = nonAdmin();
        when(manager.entrantSnapshot(50)).thenReturn(new LotteryManager.EntrantPage(
                List.of(UUID.randomUUID()), 51));

        command.onCommand(player, bukkitCommand, "lottery", new String[]{"entries"});
        MessageAssert.assertMessageSent(player, "Entrants (51)");
        MessageAssert.assertMessageSent(player, "50 more");
        command.onCommand(player, bukkitCommand, "lottery", new String[]{"entries"});
        MessageAssert.assertMessageSent(player, "Please wait");
        verify(manager).entrantSnapshot(50);
    }

    @Test
    void adminsAreExemptFromEntriesCooldown() {
        PlayerMock player = admin();

        command.onCommand(player, bukkitCommand, "lottery", new String[]{"entries"});
        command.onCommand(player, bukkitCommand, "lottery", new String[]{"entries"});

        verify(manager, org.mockito.Mockito.times(2)).entrantSnapshot(50);
    }

    @Test
    void fallbackCommandReportsUpdatesAndRejectsInvalidValues() {
        PlayerMock player = admin();
        when(manager.setFallback(12_345L)).thenReturn(CompletableFuture.completedFuture(true));

        command.onCommand(player, bukkitCommand, "lottery", new String[]{"fallback"});
        MessageAssert.assertMessageSent(player, "configured base $10,000");
        command.onCommand(player, bukkitCommand, "lottery", new String[]{"fallback", "12,345"});
        MessageAssert.assertMessageSent(player, "non-negative whole number");
        command.onCommand(player, bukkitCommand, "lottery", new String[]{"fallback", "-1"});
        MessageAssert.assertMessageSent(player, "non-negative whole number");
        command.onCommand(player, bukkitCommand, "lottery", new String[]{"fallback", "12345"});
        server.getScheduler().performOneTick();
        MessageAssert.assertMessageSent(player, "Lottery fallback set to $12,345");
        verify(manager).setFallback(12_345L);
    }

    @Test
    void notEnoughEntriesBroadcastReachesAnotherPlayer() {
        PlayerMock admin = admin();
        PlayerMock other = server.addPlayer();
        clearMessages(other);
        when(manager.draw()).thenReturn(CompletableFuture.completedFuture(
                new LotteryManager.DrawResult.NotEnoughEntrants(1, 100L, 10_100L, 20_000L)));

        command.onCommand(admin, bukkitCommand, "lottery", new String[]{"draw"});
        server.getScheduler().performOneTick();

        MessageAssert.assertMessageSent(other, "Not enough entries to draw");
        MessageAssert.assertMessageSent(admin, "Fallback raised by $100 to $10,100");
    }

    @Test
    void zeroEntriesUseTheSameBroadcastWithoutRollInDetail() {
        PlayerMock admin = admin();
        PlayerMock other = server.addPlayer();
        clearMessages(other);
        when(manager.draw()).thenReturn(CompletableFuture.completedFuture(
                new LotteryManager.DrawResult.NotEnoughEntrants(0, 0L, 10_000L, 20_000L)));

        command.onCommand(admin, bukkitCommand, "lottery", new String[]{"draw"});
        server.getScheduler().performOneTick();

        MessageAssert.assertMessageSent(other, "Not enough entries to draw");
        MessageAssert.assertMessageSent(admin, "Not enough entries to draw");
        org.junit.jupiter.api.Assertions.assertNull(admin.nextComponentMessage());
    }

    @Test
    void noGrowthBroadcastReachesAnotherPlayer() {
        PlayerMock admin = admin();
        PlayerMock other = server.addPlayer();
        clearMessages(other);
        when(manager.draw()).thenReturn(CompletableFuture.completedFuture(
                new LotteryManager.DrawResult.Refused(LotteryMath.RefusalReason.NO_GROWTH)));

        command.onCommand(admin, bukkitCommand, "lottery", new String[]{"draw"});
        server.getScheduler().performOneTick();

        MessageAssert.assertMessageSent(other, "No House growth since the last draw");
    }

    @Test
    void broadcastOutcomesMirrorExactlyOnceAndPrivateOutcomesStayPrivate() {
        PlayerMock admin = admin();
        PlayerMock other = server.addPlayer();
        UUID winner = UUID.randomUUID();
        List<LotteryManager.DrawResult> broadcastOutcomes = List.of(
                new LotteryManager.DrawResult.NotEnoughEntrants(0, 0L, 10L, 10L),
                new LotteryManager.DrawResult.Refused(LotteryMath.RefusalReason.NO_GROWTH),
                new LotteryManager.DrawResult.Refused(LotteryMath.RefusalReason.NOT_ENOUGH_ENTRANTS),
                new LotteryManager.DrawResult.PaymentAbandoned(winner, 100L,
                        me.beeliebub.tweaks.economy.HousePayOutcome.INSUFFICIENT_FUNDS, 2),
                new LotteryManager.DrawResult.Awarded(winner, 100L));
        for (LotteryManager.DrawResult outcome : broadcastOutcomes) {
            discord.cards.clear();
            clearMessages(other);
            when(manager.draw()).thenReturn(CompletableFuture.completedFuture(outcome));
            command.onCommand(admin, bukkitCommand, "lottery", new String[]{"draw"});
            server.getScheduler().performOneTick();
            int expectedCards = outcome instanceof LotteryManager.DrawResult.Awarded ? 1 : 0;
            assertEquals(expectedCards, discord.cards.size(), outcome.getClass().getSimpleName());
        }

        List<LotteryManager.DrawResult> privateOutcomes = List.of(
                new LotteryManager.DrawResult.NotReady(),
                new LotteryManager.DrawResult.InFlight(),
                new LotteryManager.DrawResult.Refused(LotteryMath.RefusalReason.HOUSE_AT_FLOOR),
                new LotteryManager.DrawResult.PaymentPending("payment", winner, 100L,
                        me.beeliebub.tweaks.economy.HousePayOutcome.NOT_READY),
                new LotteryManager.DrawResult.PaymentStuck("payment", winner, 100L));
        for (LotteryManager.DrawResult outcome : privateOutcomes) {
            discord.cards.clear();
            when(manager.draw()).thenReturn(CompletableFuture.completedFuture(outcome));
            command.onCommand(admin, bukkitCommand, "lottery", new String[]{"draw"});
            server.getScheduler().performOneTick();
            assertEquals(0, discord.cards.size(), outcome.getClass().getSimpleName());
        }
    }

    @Test
    void announcerErrorDoesNotSuppressInGameBroadcast() {
        PlayerMock admin = admin();
        PlayerMock other = server.addPlayer();
        discord.throwOnCard = true;
        clearMessages(other);
        when(manager.draw()).thenReturn(CompletableFuture.completedFuture(
                new LotteryManager.DrawResult.NotEnoughEntrants(0, 0L, 10L, 10L)));

        command.onCommand(admin, bukkitCommand, "lottery", new String[]{"draw"});
        server.getScheduler().performOneTick();

        MessageAssert.assertMessageSent(other, "Not enough entries to draw");
    }

    @Test
    void HouseAtFloorRefusalStaysWithTheCommandSender() {
        PlayerMock admin = admin();
        PlayerMock other = server.addPlayer();
        clearMessages(other);
        when(manager.draw()).thenReturn(CompletableFuture.completedFuture(
                new LotteryManager.DrawResult.Refused(LotteryMath.RefusalReason.HOUSE_AT_FLOOR)));

        command.onCommand(admin, bukkitCommand, "lottery", new String[]{"draw"});
        server.getScheduler().performOneTick();

        MessageAssert.assertMessageSent(admin, "fallback floor");
        org.junit.jupiter.api.Assertions.assertNull(other.nextComponentMessage());
    }

    private PlayerMock nonAdmin() {
        PlayerMock player = server.addPlayer();
        clearMessages(player);
        player.setOp(false);
        player.addAttachment(plugin, Permissions.LOTTERY_ADMIN, false);
        return player;
    }

    private PlayerMock admin() {
        PlayerMock player = server.addPlayer();
        clearMessages(player);
        player.addAttachment(plugin, Permissions.LOTTERY_ADMIN, true);
        return player;
    }

    private static void clearMessages(PlayerMock player) {
        while (player.nextComponentMessage() != null) {
            // Drain join and setup feedback before asserting command output.
        }
    }

    private static final class RecordingAnnouncer implements DiscordAnnouncer {
        private final List<String> cards = new ArrayList<>();
        private boolean throwOnCard;

        @Override
        public void announceCard(String message, int color, org.bukkit.OfflinePlayer subject) {
            if (throwOnCard) throw new AssertionError("Discord unavailable");
            cards.add(message);
        }

        @Override
        public void announceSettlement(SettlementLine line) {
        }

        @Override
        public void renameChannel(String channelId, String name) {
        }
    }
}
