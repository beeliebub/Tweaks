package me.beeliebub.tweaks.tests.skyblock.tracking;

import me.beeliebub.tweaks.skyblock.tracking.TrackCategory;
import me.beeliebub.tweaks.skyblock.tracking.TrackKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrackKeyTest {
    @Test
    void formatAndParseAreStable() {
        TrackKey key = new TrackKey(TrackCategory.SMELT, "iron_ingot");
        assertEquals("smelt:IRON_INGOT", key.format());
        assertEquals(key, TrackKey.parse(key.format()));
    }

    @Test
    void malformedKeysFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> TrackKey.parse("not-a-key"));
        assertThrows(IllegalArgumentException.class, () -> new TrackKey(TrackCategory.COLLECT, " "));
    }
}
