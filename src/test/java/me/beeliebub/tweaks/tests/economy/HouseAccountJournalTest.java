package me.beeliebub.tweaks.tests.economy;

import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.economy.HouseBeginPaymentOutcome;
import me.beeliebub.tweaks.economy.HouseJournalEntry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers {@link HouseAccount}'s durable payment journal: it round-trips through a reload, survives
 * an unrelated balance mutation (both mutate the same {@code house.yml} snapshot), fails the load
 * closed on corruption rather than silently dropping the recovery record, and the compound
 * begin/complete/refund/reconcile operations behave as the crash-recovery state machine requires.
 */
class HouseAccountJournalTest {

    @TempDir
    File dataFolder;

    @AfterEach
    void drainAsyncWrites() {
        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);
    }

    private JavaPlugin plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("HouseAccountJournalTest-" + System.nanoTime()));
        return plugin;
    }

    @Test
    void journalRoundTripsThroughAReload() throws Exception {
        HouseAccount house = new HouseAccount(plugin());
        house.flush().get();
        house.credit(500);
        UUID recipient = UUID.randomUUID();

        assertEquals(HouseBeginPaymentOutcome.ACCEPTED, house.beginPayment("pay-1", recipient, 100).get());

        HouseAccount reloaded = new HouseAccount(plugin());
        reloaded.flush().get();

        Map<String, HouseJournalEntry> pending = reloaded.pendingPayments();
        assertEquals(1, pending.size());
        HouseJournalEntry entry = pending.get("pay-1");
        assertNotNull(entry);
        assertEquals(recipient, entry.recipient());
        assertEquals(100L, entry.amount());
        assertEquals(400L, reloaded.balance());
    }

    @Test
    void journalEntrySurvivesAnUnrelatedCredit() throws Exception {
        HouseAccount house = new HouseAccount(plugin());
        house.flush().get();
        house.credit(500);
        UUID recipient = UUID.randomUUID();
        house.beginPayment("pay-1", recipient, 100).get();

        // credit() re-emits the whole snapshot; without the journal being a first-class field of
        // that writer, this would silently erase the in-flight entry.
        house.credit(50);

        assertEquals(1, house.pendingPayments().size(), "an unrelated credit must not erase the journal");
        assertEquals(450L, house.balance());
    }

    @Test
    void corruptJournalFailsTheLoadClosedRatherThanDroppingIt() throws Exception {
        File file = new File(dataFolder, "house/house.yml");
        file.getParentFile().mkdirs();
        YamlConfiguration seed = new YamlConfiguration();
        seed.set("balance", 500L);
        seed.set("payment_journal.pay-1.recipient", "not-a-uuid");
        seed.set("payment_journal.pay-1.amount", 100L);
        seed.set("payment_journal.pay-1.state", "DEBIT_DURABLE");
        seed.save(file);

        HouseAccount house = new HouseAccount(plugin());
        house.whenLoaded().get();

        assertFalse(house.isLoaded(), "a malformed journal entry must fail the load rather than being silently dropped");
    }

    @Test
    void beginPaymentRefusesToOverdraw() throws Exception {
        HouseAccount house = new HouseAccount(plugin());
        house.flush().get();
        house.credit(50);
        UUID recipient = UUID.randomUUID();

        assertEquals(HouseBeginPaymentOutcome.INSUFFICIENT_FUNDS, house.beginPayment("pay-1", recipient, 51).get());

        assertEquals(50L, house.balance());
        assertTrue(house.pendingPayments().isEmpty());
    }

    @Test
    void completePaymentOnAnUnknownIdIsANoOp() throws Exception {
        HouseAccount house = new HouseAccount(plugin());
        house.flush().get();

        assertDoesNotThrow(() -> house.completePayment("never-existed").get());
    }

    @Test
    void refundPaymentCreditsTheHouseBackAndCompactsTheEntry() throws Exception {
        HouseAccount house = new HouseAccount(plugin());
        house.flush().get();
        house.credit(500);
        UUID recipient = UUID.randomUUID();
        house.beginPayment("pay-1", recipient, 100).get();

        house.refundPayment("pay-1").get();

        assertEquals(500L, house.balance(), "a refund must restore the exact debited amount");
        assertTrue(house.pendingPayments().isEmpty());
    }

    @Test
    void markNeedsReconciliationExcludesTheEntryFromPendingPayments() throws Exception {
        HouseAccount house = new HouseAccount(plugin());
        house.flush().get();
        house.credit(500);
        UUID recipient = UUID.randomUUID();
        house.beginPayment("pay-1", recipient, 100).get();

        house.markNeedsReconciliation("pay-1").get();

        assertTrue(house.pendingPayments().isEmpty(), "a NEEDS_RECONCILIATION entry must never be auto-replayed");
        assertEquals("NEEDS_RECONCILIATION", house.journalEntries().get("pay-1").state().name(),
                "the full journal snapshot must retain terminal entries for receipt cleanup");
        assertEquals(400L, house.balance(), "reconciliation must not move money");
    }
}
