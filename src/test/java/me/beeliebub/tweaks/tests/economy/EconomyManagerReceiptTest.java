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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers {@link EconomyManager#applyHousePayment}'s durability contract: a receipt that survives
 * an unrelated write, offline recipients credited from their real on-disk balance rather than the
 * documented {@code getBalance} -> {@code 0} trap, idempotent replay, fail-closed mismatch, and
 * the balance-range guards. Constructed via a bare Mockito {@link JavaPlugin} (no MockBukkit),
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
        seed.set("balance", 500L);
        seed.save(file);

        // Never call loadPlayer(id) — this recipient must remain "offline" from the cache's
        // perspective, which is exactly the getBalance() -> 0.0 trap documented in economy/CLAUDE.md.
        assertEquals(HousePaymentResult.APPLIED, economy.applyHousePayment(id, "pay-1", 100).get());

        assertEquals(600L, economy.getBalance(id),
                "applyHousePayment must credit the real on-disk balance, not a hardcoded 0");
    }

    @Test
    void duplicateApplyWithMatchingReceiptIsANoOp() throws Exception {
        UUID id = UUID.randomUUID();
        assertEquals(HousePaymentResult.APPLIED, economy.applyHousePayment(id, "pay-1", 100).get());
        assertEquals(HousePaymentResult.ALREADY_APPLIED, economy.applyHousePayment(id, "pay-1", 100).get());

        assertEquals(100L, economy.getBalance(id), "a replayed payment must not credit twice");
    }

    @Test
    void mismatchedReceiptRejectsWithoutMutatingBalance() throws Exception {
        UUID id = UUID.randomUUID();
        assertEquals(HousePaymentResult.APPLIED, economy.applyHousePayment(id, "pay-1", 100).get());
        assertEquals(HousePaymentResult.REJECTED_MISMATCH, economy.applyHousePayment(id, "pay-1", 200).get());

        assertEquals(100L, economy.getBalance(id), "a mismatched receipt must not overwrite the earlier credit");
    }

    @Test
    void balanceAtTheCeilingRejectsAnOverflowingPayment() throws Exception {
        UUID id = UUID.randomUUID();
        writeBalance(id, EconomyManager.MAX_BALANCE);

        assertEquals(HousePaymentResult.REJECTED_UNREPRESENTABLE, economy.applyHousePayment(id, "pay-1", 100).get());
    }

    @Test
    void balanceMutatorsRejectValuesOutsideTheSupportedRange() throws Exception {
        UUID id = UUID.randomUUID();
        writeBalance(id, EconomyManager.MAX_BALANCE);

        assertEquals(HousePaymentResult.REJECTED_UNREPRESENTABLE, economy.applyHousePayment(id, "pay-1", 1).get());
        assertEquals(BalanceMutationResult.REJECTED_UNREPRESENTABLE, economy.addBalance(id, 1));
        assertEquals(BalanceMutationResult.APPLIED, economy.removeBalance(id, 1));
        assertEquals(BalanceMutationResult.REJECTED_UNREPRESENTABLE,
                economy.setBalance(id, Long.MAX_VALUE));
        assertEquals(EconomyManager.MAX_BALANCE - 1, economy.getBalance(id));

        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);
        assertEquals(EconomyManager.MAX_BALANCE - 1, YamlConfiguration.loadConfiguration(
                new File(dataFolder, "players/" + id + ".yml")).getLong("balance"));
    }

    @Test
    void clearHousePaymentReceiptsRemovesOnlyNamedIdsAndOmitsAnEmptySection() throws Exception {
        UUID id = UUID.randomUUID();
        assertEquals(HousePaymentResult.APPLIED, economy.applyHousePayment(id, "pay-1", 100).get());
        assertEquals(HousePaymentResult.APPLIED, economy.applyHousePayment(id, "pay-2", 200).get());

        economy.clearHousePaymentReceipts(id, Set.of("pay-1")).get();
        File file = new File(dataFolder, "players/" + id + ".yml");
        YamlConfiguration oneRemaining = YamlConfiguration.loadConfiguration(file);
        assertFalse(oneRemaining.getConfigurationSection("house_payment_receipts")
                .getKeys(false).contains("pay-1"));
        assertEquals(200L, oneRemaining.getLong("house_payment_receipts.pay-2"));

        economy.clearHousePaymentReceipts(id, Set.of("missing")).get();
        economy.clearHousePaymentReceipts(id, Set.of("pay-2")).get();
        YamlConfiguration empty = YamlConfiguration.loadConfiguration(file);
        assertTrue(empty.getConfigurationSection("house_payment_receipts") == null,
                "an emptied receipt map must remove the section entirely");
    }

    @Test
    void rejectedAndAlreadyAppliedPaymentsFlushADeferredLegacyNormalization() throws Exception {
        UUID already = UUID.randomUUID();
        writeBalanceAndReceipt(already, 110.00000000000001D, "already", 10L);
        assertEquals(HousePaymentResult.ALREADY_APPLIED,
                economy.applyHousePayment(already, "already", 10L).get());
        assertEquals(110L, readBalance(already));

        UUID mismatch = UUID.randomUUID();
        writeBalanceAndReceipt(mismatch, 110.00000000000001D, "mismatch", 10L);
        assertEquals(HousePaymentResult.REJECTED_MISMATCH,
                economy.applyHousePayment(mismatch, "mismatch", 11L).get());
        assertEquals(110L, readBalance(mismatch));

        UUID overflow = UUID.randomUUID();
        writeBalance(overflow, 110.00000000000001D);
        assertEquals(HousePaymentResult.REJECTED_UNREPRESENTABLE,
                economy.applyHousePayment(overflow, "overflow", EconomyManager.MAX_BALANCE).get());
        assertEquals(110L, readBalance(overflow));
    }

    private long readBalance(UUID id) {
        return YamlConfiguration.loadConfiguration(new File(dataFolder, "players/" + id + ".yml"))
                .getLong("balance");
    }

    private void writeBalanceAndReceipt(UUID id, double balance, String paymentId, long amount)
            throws Exception {
        File file = new File(dataFolder, "players/" + id + ".yml");
        file.getParentFile().mkdirs();
        YamlConfiguration seed = new YamlConfiguration();
        seed.set("balance", balance);
        seed.set("house_payment_receipts." + paymentId, amount);
        seed.save(file);
    }

    private void writeBalance(UUID id, double balance) throws Exception {
        File file = new File(dataFolder, "players/" + id + ".yml");
        file.getParentFile().mkdirs();
        YamlConfiguration seed = new YamlConfiguration();
        seed.set("balance", balance);
        seed.save(file);
    }
}
