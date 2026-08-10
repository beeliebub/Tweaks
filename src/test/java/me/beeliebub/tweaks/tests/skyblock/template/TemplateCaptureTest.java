package me.beeliebub.tweaks.tests.skyblock.template;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandGrid;
import me.beeliebub.tweaks.skyblock.template.IslandTemplate;
import me.beeliebub.tweaks.skyblock.template.TemplateCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateCaptureTest {

    private static final IslandGrid.ChunkBounds ONE_CHUNK = new IslandGrid.ChunkBounds(0, 0, 0, 0);

    @Test
    void rejectsCaptureBeforeReadingBlocksWhenEstimatedVolumeExceedsLimit() {
        CountingSource source = new CountingSource();

        TemplateCapture.VolumeLimitException error = assertThrows(
                TemplateCapture.VolumeLimitException.class,
                () -> new TemplateCapture().capture(source, ONE_CHUNK, 0, 2, 2, 511,
                        "too-large", new Island.SpawnOffset(0, 0, 0)));

        assertEquals(new TemplateCapture.CaptureEstimate(16, 2, 16, 512), error.estimate());
        assertEquals(511, error.maximum());
        assertEquals(0, source.calls.get(), "volume rejection must happen before block reads");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveVolumeLimit(long maximum) {
        CountingSource source = new CountingSource();

        TemplateCapture.VolumeLimitException error = assertThrows(
                TemplateCapture.VolumeLimitException.class,
                () -> new TemplateCapture().capture(source, ONE_CHUNK, 0, 1, 1, maximum,
                        "invalid-limit", new Island.SpawnOffset(0, 0, 0)));

        assertEquals(maximum, error.maximum());
        assertEquals(0, source.calls.get());
    }

    @Test
    void allowsCaptureAtTheEstimatedVolumeLimit() {
        IslandTemplate template = new TemplateCapture().capture(new CountingSource(), ONE_CHUNK,
                0, 2, 2, 512, "at-limit", new Island.SpawnOffset(0, 0, 0));

        assertEquals("at-limit", template.id());
        assertEquals(16, template.width());
        assertEquals(2, template.height());
        assertEquals(16, template.depth());
    }

    private static final class CountingSource implements TemplateCapture.BlockSource {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public String blockData(int x, int y, int z) {
            calls.incrementAndGet();
            return x == 0 && y == 0 && z == 0 ? "minecraft:stone" : "minecraft:air";
        }
    }
}
