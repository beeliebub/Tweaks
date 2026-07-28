package me.beeliebub.tweaks.tests.economy;

import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.economy.HousePayOutcome;
import me.beeliebub.tweaks.economy.HousePaymentService;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;
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
        assertEquals(125.0D, economyManager.getBalance(recipient));
        assertTrue(houseAccount.pendingPayments().isEmpty());
    }

    @Test
    void insufficientFundsMutatesNeitherAccount() throws Exception {
        makeReady();
        houseAccount.credit(50);
        UUID recipient = UUID.randomUUID();

        assertEquals(HousePayOutcome.INSUFFICIENT_FUNDS, service.pay(recipient, 51).get());

        assertEquals(50L, houseAccount.balance());
        assertEquals(0.0D, economyManager.getBalance(recipient));
    }

    @Test
    void beginShutdownRejectsNewPaymentsButLeavesTheAccountsUntouched() throws Exception {
        makeReady();
        houseAccount.credit(500);
        UUID recipient = UUID.randomUUID();

        service.beginShutdown();
        assertEquals(HousePayOutcome.SHUTTING_DOWN, service.pay(recipient, 100).get());

        assertEquals(500L, houseAccount.balance());
        assertEquals(0.0D, economyManager.getBalance(recipient));
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
        assertEquals(100.0D, economyManager.getBalance(recipient), "replay must finish the interrupted credit");
        assertTrue(houseAccount.pendingPayments().isEmpty(), "replay must compact the journal once resolved");

        // A second replay (e.g. two crashes before recovery ever completed) must not double-credit —
        // there is nothing left pending, so this is a no-op by construction.
        service.replayPendingPayments().get();
        assertEquals(100.0D, economyManager.getBalance(recipient));
    }
}
