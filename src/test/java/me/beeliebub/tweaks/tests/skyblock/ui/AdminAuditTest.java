package me.beeliebub.tweaks.tests.skyblock.ui;

import me.beeliebub.tweaks.skyblock.ui.admin.AdminAudit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class AdminAuditTest {
    @Test
    void recordsAnInfoLevelBeforeAndAfterSummary() {
        Logger logger = Logger.getLogger("tweaks-admin-audit-test-" + System.nanoTime());
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) { records.add(record); }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            AdminAudit.recorded(logger, "Ada", "edit", "type starter", "old value", "new value");
        } finally {
            logger.removeHandler(handler);
        }

        assertEquals(1, records.size());
        assertEquals(Level.INFO, records.getFirst().getLevel());
        assertEquals("Skyblock admin {0} {1} {2}: {3} -> {4}",
                records.getFirst().getMessage());
        assertArrayEquals(new Object[]{"Ada", "edit", "type starter", "old value", "new value"},
                records.getFirst().getParameters());
    }
}
