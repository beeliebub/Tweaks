package me.beeliebub.tweaks.tests.logging;

import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.HotPathEventBuffer;
import me.beeliebub.tweaks.logging.LoggingConfigCache;
import me.beeliebub.tweaks.logging.LoggingPaths;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleEventLogTest {

    @Test
    void disabledEventNeverInvokesSupplier() {
        Recording recording = new Recording();
        ConsoleEventLog eventLog = recording.eventLog();
        boolean[] invoked = {false};

        eventLog.log(LoggingPaths.ECONOMY_BALANCE_SET, () -> {
            invoked[0] = true;
            return "should not appear";
        });

        assertFalse(invoked[0]);
        assertTrue(recording.messages().isEmpty());
    }

    @Test
    void enabledEventRunsSynchronouslyAndSupplierFailureIsContained() {
        Recording recording = new Recording();
        recording.cache.update(LoggingPaths.ECONOMY_BALANCE_SET, true);
        ConsoleEventLog eventLog = recording.eventLog();
        Thread caller = Thread.currentThread();
        Thread[] supplierThread = new Thread[1];

        eventLog.log(LoggingPaths.ECONOMY_BALANCE_SET, () -> {
            supplierThread[0] = Thread.currentThread();
            return "[Economy] (console) changed balance";
        });
        eventLog.log(LoggingPaths.ECONOMY_BALANCE_SET, () -> {
            throw new IllegalStateException("bad snapshot");
        });

        assertEquals(caller, supplierThread[0]);
        assertTrue(recording.messages().stream().anyMatch("[Economy] (console) changed balance"::equals));
        assertEquals(Level.WARNING, recording.records().getLast().getLevel());
    }

    @Test
    void hotFlushEmitsSummaryAndOverflowMarker() {
        Recording recording = new Recording();
        recording.cache.update(LoggingPaths.PROTECTION_BLOCK_BREAK, true);
        recording.cache.update(LoggingPaths.PROTECTION_ENTRY, true);
        ConsoleEventLog eventLog = recording.eventLog();
        UUID actor = UUID.randomUUID();
        HotPathEventBuffer.HotKey key = new HotPathEventBuffer.HotKey(actor, "hub-1", RegionFlag.BLOCK_BREAK);

        eventLog.logHot(LoggingPaths.PROTECTION_BLOCK_BREAK, key, "Videowiz92");
        eventLog.logHot(LoggingPaths.PROTECTION_BLOCK_BREAK, key, "Videowiz92");
        for (int i = 0; i < HotPathEventBuffer.MAX_DISTINCT_KEYS; i++) {
            eventLog.logHot(LoggingPaths.PROTECTION_ENTRY,
                    new HotPathEventBuffer.HotKey(new UUID(1L, i), "r-" + i, RegionFlag.ENTRY), "p" + i);
        }
        eventLog.flushHot();

        assertTrue(recording.messages().stream().anyMatch(line -> line.contains("[Protection] Videowiz92 (" + actor
                + ") denied block-break x2 at region hub-1")));
        assertTrue(recording.messages().stream().anyMatch(line -> line.contains("hot-path audit window dropped 1")));
    }

    private static final class Recording {
        private final Logger logger = Logger.getLogger("ConsoleEventLogTest-" + System.nanoTime());
        private final List<LogRecord> records = new ArrayList<>();
        private final LoggingConfigCache cache = new LoggingConfigCache();
        private final HotPathEventBuffer buffer = new HotPathEventBuffer();

        private Recording() {
            logger.setUseParentHandlers(false);
            logger.addHandler(new Handler() {
                @Override public void publish(LogRecord record) { records.add(record); }
                @Override public void flush() {}
                @Override public void close() {}
            });
        }

        private ConsoleEventLog eventLog() { return new ConsoleEventLog(logger, cache, buffer); }
        private List<LogRecord> records() { return records; }
        private List<String> messages() { return records.stream().map(LogRecord::getMessage).toList(); }
    }
}
