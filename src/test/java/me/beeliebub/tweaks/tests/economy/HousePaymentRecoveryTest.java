package me.beeliebub.tweaks.tests.economy;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyListener;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.economy.HousePayOutcome;
import me.beeliebub.tweaks.economy.HousePaymentService;
import me.beeliebub.tweaks.ranks.RankManager;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end crash/restart simulations, distinct from {@link HousePaymentServiceTest}'s
 * single-instance coverage: these construct a fresh {@link HouseAccount}/{@link EconomyManager}
 * pair pointed at the same on-disk directory, simulating an actual process restart rather than
 * reusing in-memory state.
 */
class HousePaymentRecoveryTest {

    @TempDir
    File dataFolder;

    @AfterEach
    void drainAsyncWrites() {
        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);
    }

    private JavaPlugin plugin(String name) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(name + "-" + System.nanoTime()));
        return plugin;
    }

    private Tweaks listenerPlugin(String name) {
        Tweaks plugin = mock(Tweaks.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(name + "-" + System.nanoTime()));
        return plugin;
    }

    @Test
    void debitDurableEntrySurvivesARestartAndReplaysExactlyOnce() throws Exception {
        UUID recipient = UUID.randomUUID();

        // "Before the crash": debit lands durably, credit never happens.
        JavaPlugin firstBoot = plugin("before-crash");
        HouseAccount houseBeforeCrash = new HouseAccount(firstBoot);
        houseBeforeCrash.flush().get();
        houseBeforeCrash.credit(500);
        houseBeforeCrash.beginPayment("crash-pay", recipient, 100).get();

        // "After the restart": brand new instances reading the same on-disk state.
        JavaPlugin secondBoot = plugin("after-restart");
        HouseAccount houseAfterRestart = new HouseAccount(secondBoot);
        houseAfterRestart.whenLoaded().get();
        assertTrue(houseAfterRestart.isLoaded());
        assertEquals(400L, houseAfterRestart.balance(), "the debit must have survived the restart");
        assertEquals(1, houseAfterRestart.pendingPayments().size());

        EconomyManager economyAfterRestart = new EconomyManager(secondBoot);
        HousePaymentService serviceAfterRestart = new HousePaymentService(secondBoot, houseAfterRestart, economyAfterRestart);
        serviceAfterRestart.replayPendingPayments().get();

        assertEquals(100L, economyAfterRestart.getBalance(recipient));
        assertTrue(houseAfterRestart.pendingPayments().isEmpty());

        // A second startup replaying the same (now-compacted) state must not double-credit.
        serviceAfterRestart.replayPendingPayments().get();
        assertEquals(100L, economyAfterRestart.getBalance(recipient));
    }

    @Test
    void receiptSurvivesACrashBeforeJournalCompactionAndIsPrunedAfterReplay() throws Exception {
        UUID recipient = UUID.randomUUID();

        JavaPlugin firstBoot = plugin("credit-before-compaction");
        HouseAccount houseBeforeCrash = new HouseAccount(firstBoot);
        houseBeforeCrash.flush().get();
        houseBeforeCrash.credit(500);
        houseBeforeCrash.beginPayment("credit-pay", recipient, 100).get();
        EconomyManager economyBeforeCrash = new EconomyManager(firstBoot);
        assertEquals(me.beeliebub.tweaks.economy.HousePaymentResult.APPLIED,
                economyBeforeCrash.applyHousePayment(recipient, "credit-pay", 100).get());

        JavaPlugin secondBoot = plugin("after-credit-before-compaction");
        HouseAccount houseAfterRestart = new HouseAccount(secondBoot);
        houseAfterRestart.whenLoaded().get();
        EconomyManager economyAfterRestart = new EconomyManager(secondBoot);
        HousePaymentService serviceAfterRestart = new HousePaymentService(
                secondBoot, houseAfterRestart, economyAfterRestart);

        serviceAfterRestart.replayPendingPayments().get();

        assertEquals(100L, economyAfterRestart.getBalance(recipient));
        assertTrue(houseAfterRestart.pendingPayments().isEmpty());
        assertTrue(houseAfterRestart.journalEntries().isEmpty());
        assertTrue(YamlConfiguration.loadConfiguration(
                new File(dataFolder, "players/" + recipient + ".yml"))
                .getConfigurationSection("house_payment_receipts") == null,
                "replay must prune an orphaned receipt after the journal is resolved");
    }

    @Test
    void creditFailureRetainsTheJournalEntryForNextReplayRatherThanRefunding() throws Exception {
        UUID recipient = UUID.randomUUID();

        // Block the recipient's player file path with a directory, so applyHousePayment can
        // neither read nor write it — EconomyManager.applyHousePayment uses YamlStore's *strict*
        // ordered read specifically so a present-but-unreadable file fails the payment instead of
        // silently parsing as an empty (phantom zero-balance) document; that failure propagates
        // through HousePaymentService as HousePayOutcome.FAILED, same as any other unexpected
        // failure mid-transfer.
        File blocked = new File(dataFolder, "players/" + recipient + ".yml");
        assertTrue(blocked.mkdirs());

        JavaPlugin testPlugin = plugin("credit-failure");
        HouseAccount house = new HouseAccount(testPlugin);
        house.flush().get();
        house.credit(500);
        EconomyManager economy = new EconomyManager(testPlugin);
        HousePaymentService service = new HousePaymentService(testPlugin, house, economy);
        service.replayPendingPayments().get();

        HousePayOutcome outcome = service.pay(recipient, 100).get();

        assertEquals(HousePayOutcome.FAILED, outcome);
        assertEquals(400L, house.balance(), "the house was still debited; only the credit failed");
        assertEquals(1, house.pendingPayments().size(), "the entry must be retained, not refunded — the credit may or may not have landed");
    }

    @Test
    void legacyReceiptSweepWaitsUntilPaymentReplayIsReady() throws Exception {
        ServerMock server = MockBukkit.mock();
        try {
            Tweaks listenerPlugin = listenerPlugin("sweep-not-ready");
            HouseAccount house = new HouseAccount(listenerPlugin);
            house.flush().get();
            EconomyManager economy = new EconomyManager(listenerPlugin);
            PlayerMock player = server.addPlayer();
            UUID playerId = player.getUniqueId();
            economy.loadPlayer(playerId);
            economy.setLastLogin(playerId, System.currentTimeMillis());
            economy.applyHousePayment(playerId, "orphan-pay", 25).get();

            HousePaymentService service = new HousePaymentService(listenerPlugin, house, economy);
            EconomyListener listener = new EconomyListener(listenerPlugin, economy,
                    mock(RankManager.class), service, house);
            listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

            assertTrue(economy.hasHousePaymentReceipt(playerId, "orphan-pay").get(),
                    "a sweep must not run before startup replay marks the service ready");
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void legacyReceiptSweepRemovesOrphansButKeepsPendingAndTerminalJournalReceipts() throws Exception {
        ServerMock server = MockBukkit.mock();
        try {
            Tweaks listenerPlugin = listenerPlugin("sweep-ready");
            HouseAccount house = new HouseAccount(listenerPlugin);
            house.flush().get();
            EconomyManager economy = new EconomyManager(listenerPlugin);
            HousePaymentService service = new HousePaymentService(listenerPlugin, house, economy);
            service.replayPendingPayments().get();

            PlayerMock player = server.addPlayer();
            UUID playerId = player.getUniqueId();
            economy.loadPlayer(playerId);
            economy.setLastLogin(playerId, System.currentTimeMillis());

            house.credit(100);
            house.beginPayment("pending-pay", playerId, 10).get();
            economy.applyHousePayment(playerId, "pending-pay", 10).get();
            house.beginPayment("terminal-pay", playerId, 10).get();
            economy.applyHousePayment(playerId, "terminal-pay", 10).get();
            house.markNeedsReconciliation("terminal-pay").get();
            economy.applyHousePayment(playerId, "orphan-pay", 5).get();

            EconomyListener listener = new EconomyListener(listenerPlugin, economy,
                    mock(RankManager.class), service, house);
            listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

            assertTrue(economy.hasHousePaymentReceipt(playerId, "pending-pay").get());
            assertTrue(economy.hasHousePaymentReceipt(playerId, "terminal-pay").get());
            assertTrue(!economy.hasHousePaymentReceipt(playerId, "orphan-pay").get(),
                    "only receipts absent from the full journal may be removed");
            assertTrue(house.journalEntries().containsKey("pending-pay"));
            assertTrue(house.journalEntries().containsKey("terminal-pay"));
        } finally {
            MockBukkit.unmock();
        }
    }
}
