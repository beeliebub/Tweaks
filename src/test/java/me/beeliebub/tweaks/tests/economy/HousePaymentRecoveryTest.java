package me.beeliebub.tweaks.tests.economy;

import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.economy.HousePayOutcome;
import me.beeliebub.tweaks.economy.HousePaymentService;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

        assertEquals(100.0D, economyAfterRestart.getBalance(recipient));
        assertTrue(houseAfterRestart.pendingPayments().isEmpty());

        // A second startup replaying the same (now-compacted) state must not double-credit.
        serviceAfterRestart.replayPendingPayments().get();
        assertEquals(100.0D, economyAfterRestart.getBalance(recipient));
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
}
