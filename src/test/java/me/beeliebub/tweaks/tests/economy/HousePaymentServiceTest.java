package me.beeliebub.tweaks.tests.economy;

import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.economy.HousePayOutcome;
import me.beeliebub.tweaks.economy.HousePaymentResult;
import me.beeliebub.tweaks.economy.HousePaymentService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers {@link HousePaymentService}'s sequencing: the happy path debits the house and credits the
 * recipient exactly once, insufficient funds mutates nothing, the readiness/shutdown gates reject
 * new payments at the right times, and a payment left mid-flight from a prior crash replays cleanly
 * — including a second, redundant replay never double-crediting.
 */
class HousePaymentServiceTest {

    @TempDir
    File dataFolder;

    private JavaPlugin plugin;
    private HouseAccount houseAccount;
    private EconomyManager economyManager;
    private HousePaymentService service;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("HousePaymentServiceTest-" + System.nanoTime()));
        houseAccount = new HouseAccount(plugin);
        houseAccount.flush().get();
        economyManager = new EconomyManager(plugin);
        service = new HousePaymentService(plugin, houseAccount, economyManager);
    }

    @AfterEach
    void drainAsyncWrites() {
        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);
    }

    private void makeReady() throws Exception {
        service.replayPendingPayments().get();
    }

    @Test
    void payRejectsBeforeReplayHasMadeTheServiceReady() throws Exception {
        UUID recipient = UUID.randomUUID();
        houseAccount.credit(500);

        assertEquals(HousePayOutcome.NOT_READY, service.pay(recipient, 100).get());
        assertEquals(500L, houseAccount.balance());
    }

    @Test
    void happyPathDebitsHouseCreditsRecipientAndCompactsTheJournal() throws Exception {
        makeReady();
        houseAccount.credit(500);
        UUID recipient = UUID.randomUUID();

        assertEquals(HousePayOutcome.SUCCESS, service.pay(recipient, 125).get());

        assertEquals(375L, houseAccount.balance());
        assertEquals(125L, economyManager.getBalance(recipient));
        assertTrue(houseAccount.pendingPayments().isEmpty());
        File recipientFile = new File(dataFolder, "players/" + recipient + ".yml");
        assertNull(YamlConfiguration.loadConfiguration(recipientFile)
                .getConfigurationSection("house_payment_receipts"),
                "a resolved payment must not leave a receipt section behind");
    }

    @Test
    void insufficientFundsMutatesNeitherAccount() throws Exception {
        makeReady();
        houseAccount.credit(50);
        UUID recipient = UUID.randomUUID();

        assertEquals(HousePayOutcome.INSUFFICIENT_FUNDS, service.pay(recipient, 51).get());

        assertEquals(50L, houseAccount.balance());
        assertEquals(0L, economyManager.getBalance(recipient));
    }

    @Test
    void beginShutdownRejectsNewPaymentsButLeavesTheAccountsUntouched() throws Exception {
        makeReady();
        houseAccount.credit(500);
        UUID recipient = UUID.randomUUID();

        service.beginShutdown();
        assertEquals(HousePayOutcome.SHUTTING_DOWN, service.pay(recipient, 100).get());

        assertEquals(500L, houseAccount.balance());
        assertEquals(0L, economyManager.getBalance(recipient));
    }

    @Test
    void awaitInFlightCompletesOnceAPaySettles() throws Exception {
        makeReady();
        houseAccount.credit(500);

        service.pay(UUID.randomUUID(), 100).get();

        assertDoesNotThrow(() -> service.awaitInFlight().get(2, TimeUnit.SECONDS));
    }

    @Test
    void replayResumesAPaymentLeftDebitDurableByASimulatedCrashAndIsSafeToRunTwice() throws Exception {
        houseAccount.credit(500);
        UUID recipient = UUID.randomUUID();

        // Simulate a crash between the debit and the credit: beginPayment lands durably, but
        // nothing ever calls applyHousePayment/completePayment for it.
        houseAccount.beginPayment("crash-pay", recipient, 100).get();
        assertEquals(400L, houseAccount.balance());
        assertEquals(1, houseAccount.pendingPayments().size());

        service.replayPendingPayments().get();

        assertTrue(service.isReady());
        assertEquals(100L, economyManager.getBalance(recipient), "replay must finish the interrupted credit");
        assertTrue(houseAccount.pendingPayments().isEmpty(), "replay must compact the journal once resolved");
        assertNull(YamlConfiguration.loadConfiguration(new File(dataFolder, "players/" + recipient + ".yml"))
                .getConfigurationSection("house_payment_receipts"));

        // A second replay (e.g. two crashes before recovery ever completed) must not double-credit —
        // there is nothing left pending, so this is a no-op by construction.
        service.replayPendingPayments().get();
        assertEquals(100L, economyManager.getBalance(recipient));
    }

    @Test
    void resumePendingPaymentDoesNotDebitTheHouseAgain() throws Exception {
        makeReady();
        houseAccount.credit(500);
        UUID recipient = UUID.randomUUID();
        houseAccount.beginPayment("retained-pay", recipient, 100).get();

        assertEquals(HousePayOutcome.SUCCESS,
                service.resumePendingPayment("retained-pay", recipient, 100).get());
        assertEquals(400L, houseAccount.balance());
        assertEquals(100L, economyManager.getBalance(recipient));
    }

    @Test
    void receiptMismatchRetainsTheReceiptAndNeedsReconciliation() throws Exception {
        makeReady();
        houseAccount.credit(500);
        UUID recipient = UUID.randomUUID();
        houseAccount.beginPayment("mismatch-pay", recipient, 100).get();
        assertEquals(HousePaymentResult.APPLIED,
                economyManager.applyHousePayment(recipient, "mismatch-pay", 200).get());

        assertEquals(HousePayOutcome.NEEDS_RECONCILIATION,
                service.resumePendingPayment("mismatch-pay", recipient, 100).get());
        assertTrue(houseAccount.journalEntries().containsKey("mismatch-pay"));
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(
                new File(dataFolder, "players/" + recipient + ".yml"));
        assertEquals(200L, onDisk.getLong("house_payment_receipts.mismatch-pay"),
                "a mismatched receipt must not be pruned");
    }

    @Test
    void unrepresentableRecipientBalanceRefundsTheDebitWithoutCreatingAReceipt() throws Exception {
        makeReady();
        houseAccount.credit(500);
        UUID recipient = UUID.randomUUID();
        assertEquals(me.beeliebub.tweaks.economy.BalanceMutationResult.APPLIED,
                economyManager.setBalance(recipient, EconomyManager.MAX_BALANCE));
        economyManager.saveAll().get();
        houseAccount.beginPayment("overflow-pay", recipient, 1).get();

        assertEquals(HousePayOutcome.UNREPRESENTABLE_REFUNDED,
                service.resumePendingPayment("overflow-pay", recipient, 1).get());
        assertEquals(500L, houseAccount.balance());
        assertTrue(houseAccount.journalEntries().isEmpty());
        assertTrue(YamlConfiguration.loadConfiguration(
                new File(dataFolder, "players/" + recipient + ".yml"))
                .getConfigurationSection("house_payment_receipts") == null);
    }

    @Test
    void receiptPruneFailureStillReportsSuccessfulResolvedPayment() throws Exception {
        makeReady();
        houseAccount.credit(500);
        UUID recipient = UUID.randomUUID();
        EconomyManager economyWithFailedPrune = mock(EconomyManager.class);
        when(economyWithFailedPrune.applyHousePayment(recipient, "prune-fail", 100L))
                .thenReturn(CompletableFuture.completedFuture(HousePaymentResult.APPLIED));
        when(economyWithFailedPrune.clearHousePaymentReceipts(recipient, Set.of("prune-fail")))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("simulated prune failure")));
        HousePaymentService serviceWithFailedPrune = new HousePaymentService(
                plugin, houseAccount, economyWithFailedPrune);
        serviceWithFailedPrune.replayPendingPayments().get();

        assertEquals(HousePayOutcome.SUCCESS,
                serviceWithFailedPrune.pay("prune-fail", recipient, 100L).get());
        assertTrue(houseAccount.pendingPayments().isEmpty(),
                "the journal must already be compact when receipt pruning fails");
    }

    @Test
    void callerOwnedReceiptSurvivesUntilItsStateCommitThenCanBePruned() throws Exception {
        makeReady();
        houseAccount.credit(500);
        UUID recipient = UUID.randomUUID();

        service.retainReceiptForPayment("caller-owned");
        assertEquals(HousePayOutcome.SUCCESS,
                service.pay("caller-owned", recipient, 100L).get());
        assertTrue(houseAccount.journalEntries().isEmpty());
        assertEquals(100L, YamlConfiguration.loadConfiguration(
                new File(dataFolder, "players/" + recipient + ".yml"))
                .getLong("house_payment_receipts.caller-owned"));

        service.pruneResolvedReceipt(recipient, "caller-owned").get();
        assertNull(YamlConfiguration.loadConfiguration(
                new File(dataFolder, "players/" + recipient + ".yml"))
                .getConfigurationSection("house_payment_receipts"));
    }

    @Test
    void terminalJournalIdCannotBeReusedForANewPayment() throws Exception {
        makeReady();
        houseAccount.credit(500);
        UUID recipient = UUID.randomUUID();
        houseAccount.beginPayment("terminal-id", recipient, 100L).get();
        houseAccount.markNeedsReconciliation("terminal-id").get();

        assertEquals(HousePayOutcome.NEEDS_RECONCILIATION,
                service.pay("terminal-id", recipient, 100L).get());
        assertEquals(400L, houseAccount.balance());
        assertEquals("NEEDS_RECONCILIATION",
                houseAccount.journalEntries().get("terminal-id").state().name());
    }
}
