package me.beeliebub.tweaks.tests.logging;

import me.beeliebub.tweaks.logging.HotPathEventBuffer;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotPathEventBufferTest {

    @Test
    void identicalEventsCollapseIntoOneSummary() {
        HotPathEventBuffer buffer = new HotPathEventBuffer();
        HotPathEventBuffer.HotKey key = new HotPathEventBuffer.HotKey(
                UUID.randomUUID(), "hub-1", RegionFlag.BLOCK_BREAK);

        buffer.record("logging.protection.block-break-denied", key, "Videowiz92");
        buffer.record("logging.protection.block-break-denied", key, "Videowiz92");
        buffer.record("logging.protection.block-break-denied", key, "Videowiz92");

        HotPathEventBuffer.Drain drain = buffer.drain();
        assertEquals(1, drain.summaries().size());
        assertEquals(3, drain.summaries().getFirst().count());
        assertEquals(0, drain.droppedAdmissions());
        assertTrue(buffer.drain().summaries().isEmpty());
    }

    @Test
    void distinctKeyAdmissionIsBoundedAndReported() {
        HotPathEventBuffer buffer = new HotPathEventBuffer();
        for (int i = 0; i < HotPathEventBuffer.MAX_DISTINCT_KEYS + 1; i++) {
            buffer.record("logging.protection.entry-denied",
                    new HotPathEventBuffer.HotKey(new UUID(0L, i), "region-" + i, RegionFlag.ENTRY),
                    "player-" + i);
        }

        HotPathEventBuffer.Drain drain = buffer.drain();
        assertEquals(HotPathEventBuffer.MAX_DISTINCT_KEYS, drain.summaries().size());
        assertEquals(1, drain.droppedAdmissions());
    }
}
