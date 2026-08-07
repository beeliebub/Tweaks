package me.beeliebub.tweaks.blocklog;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.blocklog.BlockLogData.ChunkLogStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Lives in the production package (not the tests.* mirror) because it exercises
// ChunkLogStore#maxEntriesPerChest, a package-private accessor - see the root CLAUDE.md's
// "Test Layout" section for this project's documented exception.
//
// NOTE: This only covers the new config knob (live read + clamp), not the append/appendAll
// trimming algorithm itself (unchanged subList logic, already relied on in production) - a
// full round trip through PDC storage needs ItemStack#serializeAsBytes(), which throws under
// MockBukkit for any ItemStack not sourced from a real server delegate (see BlockLogTest's
// CodecRoundTrip nested class for the same documented limitation).
class BlockLogEntryCapConfigTest {

    private Tweaks plugin;
    private ChunkLogStore store;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        store = new ChunkLogStore(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void defaultsToBundledFiveHundred() {
        assertEquals(500, store.maxEntriesPerChest());
    }

    @Test
    void liveEditTakesEffectWithoutReconstruction() {
        plugin.getConfig().set("blocklog.max-entries-per-chest", 3);
        assertEquals(3, store.maxEntriesPerChest());
    }

    @Test
    void zeroOrNegativeClampsToOne() {
        plugin.getConfig().set("blocklog.max-entries-per-chest", 0);
        assertEquals(1, store.maxEntriesPerChest());

        plugin.getConfig().set("blocklog.max-entries-per-chest", -10);
        assertEquals(1, store.maxEntriesPerChest());
    }
}
