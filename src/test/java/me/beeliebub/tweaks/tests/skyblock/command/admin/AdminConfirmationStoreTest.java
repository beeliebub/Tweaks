package me.beeliebub.tweaks.tests.skyblock.command.admin;

import me.beeliebub.tweaks.skyblock.command.admin.AdminConfirmationStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminConfirmationStoreTest {
    @Test
    void confirmationsAreOneShotAndExpireAfterSixtySeconds() {
        MutableClock clock = new MutableClock();
        AdminConfirmationStore store = new AdminConfirmationStore(clock);
        UUID actor = UUID.randomUUID();

        store.put(actor, "delete", "type starter", 3, Path.of("backups"));
        assertEquals(3, store.peek(actor, "delete", "type starter").orElseThrow().references());
        assertTrue(store.consume(actor, "delete", "type starter").isPresent());
        assertTrue(store.consume(actor, "delete", "type starter").isEmpty());

        store.put(actor, "delete", "type starter", 3, Path.of("backups"));
        clock.advance(AdminConfirmationStore.TTL.plusNanos(1));
        assertTrue(store.consume(actor, "delete", "type starter").isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.EPOCH;

        @Override
        public ZoneId getZone() { return ZoneId.of("UTC"); }

        @Override
        public Clock withZone(ZoneId zone) { return this; }

        @Override
        public Instant instant() { return instant; }

        void advance(Duration duration) { instant = instant.plus(duration); }
    }
}
