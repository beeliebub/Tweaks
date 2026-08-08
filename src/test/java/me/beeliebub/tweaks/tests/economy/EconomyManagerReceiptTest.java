package me.beeliebub.tweaks.tests.economy;

import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.BalanceMutationResult;
import me.beeliebub.tweaks.economy.HousePaymentResult;
import org.bukkit.configuration.file.YamlConfiguration;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers {@link EconomyManager#applyHousePayment}'s durability contract: a receipt that survives
 * an unrelated write, offline recipients credited from their real on-disk balance rather than the
 * documented {@code getBalance} -> {@code 0.0} trap, idempotent replay, fail-closed mismatch, and
 * the representability guards. Constructed via a bare Mockito {@link JavaPlugin} (no MockBukkit),
 * mirroring {@code EconomyManagerPersistenceTest} — {@code applyHousePayment}'s tab refresh
 * no-ops when {@code plugin.isEnabled()} is false, so no scheduler is needed.
 */
class EconomyManagerReceiptTest {

    @TempDir
    File dataFolder;

    private EconomyManager economy;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("EconomyManagerReceiptTest-" + System.nanoTime()));
        economy = new EconomyManager(plugin);
    }

    @AfterEach
    void drainAsyncWrites() {
        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);
    }

    @Test
    void receiptSurvivesAnUnrelatedMutatorWrite() throws Exception {
        UUID id = UUID.randomUUID();
        assertEquals(HousePaymentResult.APPLIED, economy.applyHousePayment(id, "pay-1", 100).get());

        // setRank re-emits the whole document; without receipts being a first-class field of that
        // writer, this would silently erase the receipt written above.
        economy.setRank(id, 5);
        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);

        File file = new File(dataFolder, "players/" + id + ".yml");
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(file);
        assertEquals(100L, onDisk.getLong("house_payment_receipts.pay-1"));
    }

    @Test
    void offlineRecipientIsCreditedFromRealOnDiskBalanceNotTheGetterZeroTrap() throws Exception {
        UUID id = UUID.randomUUID();
        File file = new File(dataFolder, "players/" + id + ".yml");
        file.getParentFile().mkdirs();
        YamlConfiguration seed = new YamlConfiguration();
        seed.set("balance", 500.0D);
        seed.save(file);

        // Never call loadPlayer(id) — this recipient must remain "offline" from the cache's
        // perspective, which is exactly the getBalance() -> 0.0 trap documented in economy/CLAUDE.md.
        assertEquals(HousePaymentResult.APPLIED, economy.applyHousePayment(id, "pay-1", 100).get());

        assertEquals(600.0D, economy.getBalance(id),
                "applyHousePayment must credit the real on-disk balance, not a hardcoded 0.0");
    }

    @Test
    void duplicateApplyWithMatchingReceiptIsANoOp() throws Exception {
        UUID id = UUID.randomUUID();
        assertEquals(HousePaymentResult.APPLIED, economy.applyHousePayment(id, "pay-1", 100).get());
        assertEquals(HousePaymentResult.ALREADY_APPLIED, economy.applyHousePayment(id, "pay-1", 100).get());

        assertEquals(100.0D, economy.getBalance(id), "a replayed payment must not credit twice");
    }

    @Test
    void mismatchedReceiptRejectsWithoutMutatingBalance() throws Exception {
        UUID id = UUID.randomUUID();
        assertEquals(HousePaymentResult.APPLIED, economy.applyHousePayment(id, "pay-1", 100).get());
        assertEquals(HousePaymentResult.REJECTED_MISMATCH, economy.applyHousePayment(id, "pay-1", 200).get());

        assertEquals(100.0D, economy.getBalance(id), "a mismatched receipt must not overwrite the earlier credit");
    }

    @Test
    void nonFiniteBalanceRejectsThePayment() throws Exception {
        UUID id = UUID.randomUUID();
        writeBalance(id, Double.NaN);

        assertEquals(HousePaymentResult.REJECTED_UNREPRESENTABLE, economy.applyHousePayment(id, "pay-1", 100).get());
    }

    @Test
    void unrepresentableAmountNearDoublePrecisionLimitRejects() throws Exception {
        UUID id = UUID.randomUUID();
        // 2^53: the largest integral double every whole-dollar amount below it can still exactly
        // add to. Adding 1 here cannot change the value by exactly 1 in double arithmetic.
        writeBalance(id, Math.pow(2, 53));

        assertEquals(HousePaymentResult.REJECTED_UNREPRESENTABLE, economy.applyHousePayment(id, "pay-1", 1).get());
    }

    @Test
    void balanceMutatorsRejectNonFiniteStoredBalanceWithoutWriting() throws Exception {
        UUID id = UUID.randomUUID();
        writeBalance(id, Double.NaN);

        assertEquals(BalanceMutationResult.REJECTED_UNREPRESENTABLE, economy.addBalance(id, 1));
        assertEquals(BalanceMutationResult.REJECTED_UNREPRESENTABLE, economy.setBalance(id, 10));
        assertEquals(BalanceMutationResult.REJECTED_UNREPRESENTABLE, economy.removeBalance(id, 1));
        assertTrue(Double.isNaN(economy.getBalance(id)));

        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);
        assertTrue(Double.isNaN(YamlConfiguration.loadConfiguration(
                new File(dataFolder, "players/" + id + ".yml")).getDouble("balance")));
    }

    @Test
    void balanceMutatorsRejectPrecisionLimitWhenChangeCannotBeRepresented() throws Exception {
        UUID id = UUID.randomUUID();
        double ceiling = Math.pow(2, 53);
        writeBalance(id, ceiling);

        assertEquals(BalanceMutationResult.REJECTED_UNREPRESENTABLE, economy.addBalance(id, 1));
        assertEquals(BalanceMutationResult.APPLIED, economy.removeBalance(id, 1));
        assertEquals(BalanceMutationResult.REJECTED_UNREPRESENTABLE, economy.setBalance(id, 1e300));
        assertEquals(ceiling - 1, economy.getBalance(id));

        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);
        assertEquals(ceiling - 1, YamlConfiguration.loadConfiguration(
                new File(dataFolder, "players/" + id + ".yml")).getDouble("balance"));
    }

    private void writeBalance(UUID id, double balance) throws Exception {
        File file = new File(dataFolder, "players/" + id + ".yml");
        file.getParentFile().mkdirs();
        YamlConfiguration seed = new YamlConfiguration();
        seed.set("balance", balance);
        seed.save(file);
    }
}
