package me.beeliebub.tweaks.blocklog;

import me.beeliebub.tweaks.Tweaks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Lives in the production package (not the tests.* mirror) because it exercises
// BlockLogSystem#retentionMillis, a package-private accessor - see the root CLAUDE.md's
// "Test Layout" section for this project's documented exception.
class BlockLogRetentionConfigTest {

    private Tweaks plugin;
    private BlockLogSystem system;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        system = new BlockLogSystem(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void defaultsToBundledThirtyDays() {
        assertEquals(TimeUnit.DAYS.toMillis(30), system.retentionMillis());
    }

    @Test
    void oneDayRetentionLiveEditIsShorterThanDefault() {
        plugin.getConfig().set("blocklog.retention-days", 1);
        assertEquals(TimeUnit.DAYS.toMillis(1), system.retentionMillis());
    }

    @Test
    void zeroOrNegativeClampsToOneDay() {
        plugin.getConfig().set("blocklog.retention-days", 0);
        assertEquals(TimeUnit.DAYS.toMillis(1), system.retentionMillis());

        plugin.getConfig().set("blocklog.retention-days", -5);
        assertEquals(TimeUnit.DAYS.toMillis(1), system.retentionMillis());
    }
}
