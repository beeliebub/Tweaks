package me.beeliebub.tweaks.tests.lottery;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseJournalEntry;
import me.beeliebub.tweaks.economy.HousePayOutcome;
import me.beeliebub.tweaks.economy.HousePaymentService;
import me.beeliebub.tweaks.economy.HousePaymentState;
import me.beeliebub.tweaks.lottery.LotteryManager;
import me.beeliebub.tweaks.lottery.LotteryMath;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LotteryManagerTest {

    private static final long HOUSE_BALANCE = 114_130L;
    private static final long BASELINE = 69_420L;

    @TempDir
    File dataFolder;

    @Test
    void moneyNeutralPaymentLeavesEntrantsBaselineAndDiskUnchanged() throws Exception {
        Fixture fixture = fixture();
        LotteryManager manager = fixture.manager();
        manager.setBaseline(BASELINE).get();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(manager.enter(first));
        assertTrue(manager.enter(second));
        List<UUID> before = manager.entrantSnapshot(50).shown();

        when(fixture.payment.pay(anyString(), any(UUID.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(HousePayOutcome.UNREPRESENTABLE_REFUNDED));

        LotteryManager.DrawResult result = manager.draw().get();

        assertInstanceOf(LotteryManager.DrawResult.PaymentAbandoned.class, result);
        assertEquals(before, manager.entrantSnapshot(50).shown());
        assertEquals(BASELINE, manager.baseline());
        YamlConfiguration state = stateFile();
        assertEquals(before.stream().map(UUID::toString).sorted().toList(),
                state.getStringList("entrants").stream().sorted().toList());
        assertEquals(BASELINE, state.getLong("baseline"));
        assertFalse(state.contains("pending_award"));
    }

    @Test
    void successfulDrawRemovesOnlyTheRecordedEntrants() throws Exception {
        Fixture fixture = fixture();
        LotteryManager manager = fixture.manager();
        manager.setBaseline(BASELINE).get();
        UUID drawn = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID enteredDuringPayment = UUID.randomUUID();
        manager.enter(drawn);
        manager.enter(other);

        CompletableFuture<HousePayOutcome> payment = new CompletableFuture<>();
        when(fixture.payment.pay(anyString(), any(UUID.class), anyLong())).thenReturn(payment);
        CompletableFuture<LotteryManager.DrawResult> draw = manager.draw();
        verify(fixture.payment, timeout(2_000)).pay(anyString(), any(UUID.class), anyLong());
        assertEquals(10_000L, stateFile().getLong("pending_award.new_fallback"));

        assertTrue(manager.enter(enteredDuringPayment));
        payment.complete(HousePayOutcome.SUCCESS);

        LotteryManager.DrawResult result = draw.get();

        assertInstanceOf(LotteryManager.DrawResult.Awarded.class, result);
        assertEquals(List.of(enteredDuringPayment), manager.entrantSnapshot(50).shown());
        assertEquals(86_768L, manager.baseline());
        YamlConfiguration state = stateFile();
        assertEquals(List.of(enteredDuringPayment.toString()), state.getStringList("entrants"));
        assertEquals(86_768L, state.getLong("baseline"));
        assertFalse(state.contains("pending_award"));
    }

    @Test
    void retainedDebitKeepsPendingAwardAndLeavesTheDrawStateUnchanged() throws Exception {
        Fixture fixture = fixture();
        LotteryManager manager = fixture.manager();
        manager.setBaseline(BASELINE).get();
        UUID entrant = UUID.randomUUID();
        manager.enter(UUID.randomUUID());
        manager.enter(entrant);
        when(fixture.payment.pay(anyString(), any(UUID.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(HousePayOutcome.WRITE_FAILED_RETAINED));

        LotteryManager.DrawResult result = manager.draw().get();

        assertInstanceOf(LotteryManager.DrawResult.PaymentPending.class, result);
        assertEquals(2, manager.entrantCount());
        assertEquals(BASELINE, manager.baseline());
        assertTrue(stateFile().contains("pending_award"));
    }

    @Test
    void reconciliationOutcomeCommitsDrawAndClearsIntent() throws Exception {
        Fixture fixture = fixture();
        List<LogRecord> records = new ArrayList<>();
        fixture.logger().addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        LotteryManager manager = fixture.manager();
        manager.setBaseline(BASELINE).get();
        manager.enter(UUID.randomUUID());
        manager.enter(UUID.randomUUID());
        when(fixture.payment.pay(anyString(), any(UUID.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(HousePayOutcome.NEEDS_RECONCILIATION));

        LotteryManager.DrawResult result = manager.draw().get();

        assertInstanceOf(LotteryManager.DrawResult.PaymentStuck.class, result);
        assertEquals(0, manager.entrantCount());
        assertEquals(86_768L, manager.baseline());
        assertFalse(stateFile().contains("pending_award"));
        assertEquals(1, records.stream().filter(record -> record.getLevel() == Level.SEVERE).count());
    }

    @Test
    void legacyPendingAwardWithoutJournalDoesNotBrickLottery() throws Exception {
        UUID winner = UUID.randomUUID();
        writeLegacyState(69_420L, winner, "legacy-pay", 73_505L);
        Fixture fixture = fixture();

        LotteryManager manager = fixture.manager();

        assertTrue(manager.isLoaded());
        assertFalse(stateFile().contains("pending_award"));
        assertEquals(10_000L, stateFile().getLong("fallback"));
    }

    @Test
    void legacyStateWithoutFallbackDefaultsAndPersistsTheConfiguredBase() throws Exception {
        writePlainState(BASELINE, List.of(UUID.randomUUID()));

        Fixture fixture = fixture();
        LotteryManager manager = fixture.manager();

        assertTrue(manager.isLoaded());
        assertEquals(10_000L, manager.fallback());
        assertEquals(10_000L, stateFile().getLong("fallback"));
    }

    @Test
    void legacyReseedToIsIgnoredAndStrippedWithoutValidation() throws Exception {
        UUID winner = UUID.randomUUID();
        writeLegacyState(69_420L, winner, "legacy-reseed", 73_505L, "garbage");
        Fixture fixture = fixture();
        List<LogRecord> records = new ArrayList<>();
        fixture.logger().addHandler(recordingHandler(records));

        LotteryManager manager = fixture.manager();

        assertTrue(manager.isLoaded());
        assertFalse(stateFile().contains("reseed_to"));
        assertEquals(1, records.stream().filter(record -> record.getLevel() == Level.WARNING
                && record.getMessage().contains("superseded payout rule")).count());
    }

    @Test
    void legacyReseedToAwardReplaysWithoutReseedingTheHouse() throws Exception {
        UUID winner = UUID.randomUUID();
        writeLegacyState(69_420L, winner, "legacy-reseed-replay", 73_505L, 88_888L);
        Fixture fixture = fixture();
        when(fixture.economy.hasHousePaymentReceipt(winner, "legacy-reseed-replay"))
                .thenReturn(CompletableFuture.completedFuture(true));

        LotteryManager manager = fixture.manager();

        assertTrue(manager.isLoaded());
        assertFalse(stateFile().contains("pending_award"));
        verify(fixture.house, never()).set(anyLong());
    }

    @Test
    void corruptLegacyReseedToDoesNotFailClosed() throws Exception {
        UUID winner = UUID.randomUUID();
        writeLegacyState(69_420L, winner, "legacy-negative-reseed", 73_505L, -1L);
        Fixture fixture = fixture();

        LotteryManager manager = fixture.manager();

        assertTrue(manager.isLoaded());
        assertFalse(stateFile().contains("reseed_to"));
    }

    @Test
    void retainedJournalReplayUsesTheExistingDebitAndClearsUnrepresentableAward() throws Exception {
        UUID winner = UUID.randomUUID();
        writeState(BASELINE, List.of(UUID.randomUUID()), winner, "unrep-pay", 73_505L, null, null, 0);
        Fixture fixture = fixture();
        when(fixture.house.pendingPayments()).thenReturn(Map.of("unrep-pay",
                new HouseJournalEntry(winner, 73_505L, HousePaymentState.DEBIT_DURABLE, 1L)));
        when(fixture.payment.resumePendingPayment("unrep-pay", winner, 73_505L))
                .thenReturn(CompletableFuture.completedFuture(HousePayOutcome.UNREPRESENTABLE_REFUNDED));

        LotteryManager manager = fixture.manager();

        assertTrue(manager.isLoaded());
        assertFalse(stateFile().contains("pending_award"));
        assertEquals(HOUSE_BALANCE, fixture.house.balance());
        assertEquals(10_000L, stateFile().getLong("fallback"));
    }

    @Test
    void oneEntrantRollsHouseGrowthIntoFallbackAndKeepsTheTicket() throws Exception {
        Fixture fixture = fixture();
        LotteryManager manager = fixture.manager();
        manager.setBaseline(80_000L).get();
        UUID entrant = UUID.randomUUID();
        manager.enter(entrant);

        LotteryManager.DrawResult first = manager.draw().get();

        LotteryManager.DrawResult.NotEnoughEntrants firstOutcome =
                assertInstanceOf(LotteryManager.DrawResult.NotEnoughEntrants.class, first);
        assertEquals(34_130L, firstOutcome.rolledIn());
        assertEquals(44_130L, firstOutcome.fallback());
        assertEquals(HOUSE_BALANCE, firstOutcome.baseline());
        assertEquals(List.of(entrant), manager.entrantSnapshot(50).shown());
        assertEquals(44_130L, manager.fallback());
        assertEquals(HOUSE_BALANCE, manager.baseline());
        assertEquals(44_130L, stateFile().getLong("fallback"));
        assertEquals(HOUSE_BALANCE, stateFile().getLong("baseline"));

        LotteryManager.DrawResult second = manager.draw().get();

        LotteryManager.DrawResult.NotEnoughEntrants secondOutcome =
                assertInstanceOf(LotteryManager.DrawResult.NotEnoughEntrants.class, second);
        assertEquals(0L, secondOutcome.rolledIn());
        assertEquals(44_130L, manager.fallback());
        assertEquals(HOUSE_BALANCE, manager.baseline());
        assertEquals(44_130L, stateFile().getLong("fallback"));
        assertEquals(HOUSE_BALANCE, stateFile().getLong("baseline"));
        verify(fixture.payment, never()).pay(anyString(), any(UUID.class), anyLong());
    }

    @Test
    void entrantSnapshotHonorsItsBoundAndRetainsTheTotal() throws Exception {
        Fixture fixture = fixture();
        LotteryManager manager = fixture.manager();
        for (int i = 0; i < 60; i++) {
            assertTrue(manager.enter(UUID.randomUUID()));
        }

        LotteryManager.EntrantPage page = manager.entrantSnapshot(50);

        assertEquals(60, page.total());
        assertEquals(50, page.shown().size());
        assertEquals(50, page.shown().stream().distinct().count());
    }

    @Test
    void zeroEntrantsDoNotAdvanceBaselineOrFallback() throws Exception {
        Fixture fixture = fixture();
        LotteryManager manager = fixture.manager();
        manager.setBaseline(80_000L).get();
        manager.setFallback(50_000L).get();
        YamlConfiguration before = stateFile();

        LotteryManager.DrawResult result = manager.draw().get();

        assertInstanceOf(LotteryManager.DrawResult.NotEnoughEntrants.class, result);
        assertEquals(80_000L, manager.baseline());
        assertEquals(50_000L, manager.fallback());
        YamlConfiguration after = stateFile();
        assertEquals(before.getLong("baseline"), after.getLong("baseline"));
        assertEquals(before.getLong("fallback"), after.getLong("fallback"));
        assertEquals(before.getStringList("entrants"), after.getStringList("entrants"));
        verify(fixture.payment, never()).pay(anyString(), any(UUID.class), anyLong());
    }

    @Test
    void fallbackOverflowLeavesTheFallbackUnchanged() throws Exception {
        Fixture fixture = fixture();
        LotteryManager manager = fixture.manager();
        manager.setBaseline(80_000L).get();
        manager.setFallback(Long.MAX_VALUE).get();
        manager.enter(UUID.randomUUID());

        LotteryManager.DrawResult.NotEnoughEntrants result =
                assertInstanceOf(LotteryManager.DrawResult.NotEnoughEntrants.class, manager.draw().get());

        assertEquals(0L, result.rolledIn());
        assertEquals(Long.MAX_VALUE, manager.fallback());
        assertEquals(HOUSE_BALANCE, manager.baseline());
    }

    @Test
    void successfulDrawResetsFallbackToConfiguredBase() throws Exception {
        Fixture fixture = fixture();
        LotteryManager manager = fixture.manager();
        manager.setBaseline(BASELINE).get();
        manager.setFallback(50_000L).get();
        manager.enter(UUID.randomUUID());
        manager.enter(UUID.randomUUID());
        when(fixture.payment.pay(anyString(), any(UUID.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(HousePayOutcome.SUCCESS));

        assertInstanceOf(LotteryManager.DrawResult.Awarded.class, manager.draw().get());

        assertEquals(10_000L, manager.fallback());
        assertEquals(10_000L, stateFile().getLong("fallback"));
    }

    @Test
    void replayedAwardAppliesItsPersistedFallbackReset() throws Exception {
        UUID winner = UUID.randomUUID();
        writeState(BASELINE, List.of(winner), winner, "fallback-replay", 10_000L,
                null, List.of(winner), 0, 10_000L);
        YamlConfiguration state = stateFile();
        state.set("fallback", 50_000L);
        state.save(new File(dataFolder, "lottery/lottery.yml"));
        Fixture fixture = fixture();
        when(fixture.house.pendingPayments()).thenReturn(Map.of("fallback-replay",
                new HouseJournalEntry(winner, 10_000L, HousePaymentState.DEBIT_DURABLE, 1L)));
        when(fixture.payment.resumePendingPayment("fallback-replay", winner, 10_000L))
                .thenReturn(CompletableFuture.completedFuture(HousePayOutcome.SUCCESS));

        LotteryManager manager = fixture.manager();

        assertTrue(manager.isLoaded());
        assertEquals(10_000L, manager.fallback());
        assertEquals(10_000L, stateFile().getLong("fallback"));
        assertFalse(stateFile().contains("pending_award"));
    }

    @Test
    void commitPersistFailureRestoresFallback() throws Exception {
        Fixture fixture = fixture();
        LotteryManager manager = fixture.manager();
        manager.setBaseline(BASELINE).get();
        manager.setFallback(50_000L).get();
        manager.enter(UUID.randomUUID());
        manager.enter(UUID.randomUUID());
        CompletableFuture<HousePayOutcome> payment = new CompletableFuture<>();
        when(fixture.payment.pay(anyString(), any(UUID.class), anyLong())).thenReturn(payment);

        CompletableFuture<LotteryManager.DrawResult> draw = manager.draw();
        verify(fixture.payment, timeout(2_000)).pay(anyString(), any(UUID.class), anyLong());
        replaceStateFileWithDirectory();
        payment.complete(HousePayOutcome.SUCCESS);

        assertThrows(java.util.concurrent.ExecutionException.class, draw::get);
        assertEquals(50_000L, manager.fallback());
    }

    @Test
    void commitPersistFailureDoesNotClobberAConcurrentFallbackEdit() throws Exception {
        Fixture fixture = fixture();
        LotteryManager manager = fixture.manager();
        manager.setBaseline(BASELINE).get();
        manager.setFallback(50_000L).get();
        manager.enter(UUID.randomUUID());
        manager.enter(UUID.randomUUID());
        CompletableFuture<HousePayOutcome> payment = new CompletableFuture<>();
        when(fixture.payment.pay(anyString(), any(UUID.class), anyLong())).thenReturn(payment);

        CompletableFuture<LotteryManager.DrawResult> draw = manager.draw();
        verify(fixture.payment, timeout(2_000)).pay(anyString(), any(UUID.class), anyLong());
        manager.setFallback(60_000L).get();
        replaceStateFileWithDirectory();
        payment.complete(HousePayOutcome.SUCCESS);

        assertThrows(java.util.concurrent.ExecutionException.class, draw::get);
        assertEquals(60_000L, manager.fallback());
    }

    @Test
    void floorIsRecheckedBeforeTheHousePaymentBegins() throws Exception {
        Fixture fixture = fixture();
        LotteryManager manager = fixture.manager();
        manager.setBaseline(BASELINE).get();
        manager.enter(UUID.randomUUID());
        manager.enter(UUID.randomUUID());
        when(fixture.house.balance()).thenReturn(HOUSE_BALANCE, 20_000L);

        LotteryManager.DrawResult.Refused result =
                assertInstanceOf(LotteryManager.DrawResult.Refused.class, manager.draw().get());

        assertEquals(LotteryMath.RefusalReason.HOUSE_AT_FLOOR, result.reason());
        verify(fixture.payment, never()).pay(anyString(), any(UUID.class), anyLong());
        assertFalse(stateFile().contains("pending_award"));
    }

    @Test
    void corruptLotteryStateFailsClosed() throws Exception {
        File file = new File(dataFolder, "lottery/lottery.yml");
        file.getParentFile().mkdirs();
        YamlConfiguration corrupt = new YamlConfiguration();
        corrupt.set("baseline", "not-a-number");
        corrupt.save(file);

        Fixture fixture = fixture();
        LotteryManager manager = fixture.manager();

        assertFalse(manager.isLoaded());
    }

    private Fixture fixture() {
        Tweaks plugin = mock(Tweaks.class);
        HousePaymentService payment = mock(HousePaymentService.class);
        me.beeliebub.tweaks.economy.HouseAccount house = mock(me.beeliebub.tweaks.economy.HouseAccount.class);
        EconomyManager economy = mock(EconomyManager.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("lottery.reseed-amount", 10_000L);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("LotteryManagerTest-" + System.nanoTime()));
        when(plugin.getConfig()).thenReturn(config);
        when(house.whenLoaded()).thenReturn(CompletableFuture.completedFuture(null));
        when(house.isLoaded()).thenReturn(true);
        when(house.balance()).thenReturn(HOUSE_BALANCE);
        when(house.pendingPayments()).thenReturn(Map.of());
        when(payment.whenReady()).thenReturn(CompletableFuture.completedFuture(null));
        when(economy.hasHousePaymentReceipt(any(UUID.class), anyString()))
                .thenReturn(CompletableFuture.completedFuture(false));
        Logger logger = plugin.getLogger();
        return new Fixture(plugin, payment, house, economy, logger);
    }

    private void writeLegacyState(long baseline, UUID winner, String paymentId, long amount) throws Exception {
        writeLegacyState(baseline, winner, paymentId, amount, null);
    }

    private void writeLegacyState(long baseline, UUID winner, String paymentId, long amount,
                                  Object reseedTo) throws Exception {
        File file = new File(dataFolder, "lottery/lottery.yml");
        file.getParentFile().mkdirs();
        YamlConfiguration state = new YamlConfiguration();
        state.set("baseline", baseline);
        state.set("entrants", List.of());
        state.set("pending_award.payment_id", paymentId);
        state.set("pending_award.winner", winner.toString());
        state.set("pending_award.amount", amount);
        state.set("pending_award.created_at", 1L);
        if (reseedTo != null) state.set("pending_award.reseed_to", reseedTo);
        state.save(file);
    }

    private void writePlainState(long baseline, List<UUID> entrants) throws Exception {
        File file = new File(dataFolder, "lottery/lottery.yml");
        file.getParentFile().mkdirs();
        YamlConfiguration state = new YamlConfiguration();
        state.set("baseline", baseline);
        state.set("entrants", entrants.stream().map(UUID::toString).toList());
        state.save(file);
    }

    private void writeState(long baseline, List<UUID> entrants, UUID winner, String paymentId,
                            long amount, Long newBaseline, List<UUID> awardEntrants, int attempts) throws Exception {
        writeState(baseline, entrants, winner, paymentId, amount, newBaseline, awardEntrants, attempts, null);
    }

    private void writeState(long baseline, List<UUID> entrants, UUID winner, String paymentId,
                            long amount, Long newBaseline, List<UUID> awardEntrants, int attempts,
                            Long newFallback) throws Exception {
        File file = new File(dataFolder, "lottery/lottery.yml");
        file.getParentFile().mkdirs();
        YamlConfiguration state = new YamlConfiguration();
        state.set("baseline", baseline);
        state.set("entrants", entrants.stream().map(UUID::toString).toList());
        state.set("pending_award.payment_id", paymentId);
        state.set("pending_award.winner", winner.toString());
        state.set("pending_award.amount", amount);
        state.set("pending_award.created_at", 1L);
        if (newBaseline != null) state.set("pending_award.new_baseline", newBaseline);
        if (awardEntrants != null) state.set("pending_award.entrants",
                awardEntrants.stream().map(UUID::toString).toList());
        if (newFallback != null) state.set("pending_award.new_fallback", newFallback);
        state.set("pending_award.attempts", attempts);
        state.save(file);
    }

    private Handler recordingHandler(List<LogRecord> records) {
        return new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
    }

    private void replaceStateFileWithDirectory() {
        File stateFile = new File(dataFolder, "lottery/lottery.yml");
        assertTrue(stateFile.delete(), "the lottery state file should be replaceable in the test fixture");
        assertTrue(stateFile.mkdir(), "the replacement lottery state directory should be creatable");
    }

    private YamlConfiguration stateFile() {
        return YamlConfiguration.loadConfiguration(new File(dataFolder, "lottery/lottery.yml"));
    }

    private record Fixture(Tweaks plugin, HousePaymentService payment,
                           me.beeliebub.tweaks.economy.HouseAccount house, EconomyManager economy,
                           Logger logger) {
        LotteryManager manager() throws Exception {
            LotteryManager manager = new LotteryManager(plugin, house, payment, economy);
            manager.whenLoaded().get();
            return manager;
        }
    }
}
