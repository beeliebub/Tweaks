package me.beeliebub.tweaks.tests.skyblock.template;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandGrid;
import me.beeliebub.tweaks.skyblock.template.IslandTemplate;
import me.beeliebub.tweaks.skyblock.template.TemplateCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void capturesABuildAboveTheLegacyScanWindow() {
        IslandTemplate template = new TemplateCapture().capture(
                new AirProbeSource(Set.of(64, 65, 66)), ONE_CHUNK, -64, 320, 128,
                250_000, "above-window", new Island.SpawnOffset(0, 0, 0));

        assertEquals(5, template.height());
    }

    @Test
    void rejectsOnlyWhenTheEntireColumnRangeIsAir() {
        TemplateCapture.AllAirException error = assertThrows(
                TemplateCapture.AllAirException.class,
                () -> new TemplateCapture().capture(new AirProbeSource(Set.of()), ONE_CHUNK,
                        -64, 320, 128, 250_000, "empty", new Island.SpawnOffset(0, 0, 0)));

        assertTrue(error.getMessage().contains("-64"));
        assertTrue(error.getMessage().contains("320"));
    }

    @Test
    void rejectsABuildTallerThanTheHeightLimit() {
        TemplateCapture.HeightLimitException error = assertThrows(
                TemplateCapture.HeightLimitException.class,
                () -> new TemplateCapture().capture(new AirProbeSource(Set.of(0, 200)), ONE_CHUNK,
                        -64, 320, 128, 250_000, "too-tall", new Island.SpawnOffset(0, 0, 0)));

        assertEquals(201, error.height());
        assertEquals(128, error.maximum());
        assertTrue(error.getMessage().contains("201"));
        assertTrue(error.getMessage().contains("128"));
    }

    @Test
    void dropsPaddingBeforeRejectingAnExactlyMaxHeightBuild() {
        IslandTemplate template = new TemplateCapture().capture(
                new AirProbeSource(layers(0, 128)), ONE_CHUNK, -64, 320, 128,
                250_000, "exact-height", new Island.SpawnOffset(0, 0, 0));

        assertEquals(128, template.height());
    }

    @Test
    void boundsDetectionUsesTheCheapAirProbe() {
        AirProbeSource source = new AirProbeSource(Set.of(64));

        IslandTemplate template = new TemplateCapture().capture(source, ONE_CHUNK,
                0, 128, 128, 250_000, "cheap-probe", new Island.SpawnOffset(0, 0, 0));

        assertTrue(source.airAtCalls.get() > 0);
        assertEquals(template.width() * template.height() * template.depth(),
                source.blockDataCalls.get());
    }

    @Test
    void boundsDetectionStopsAtTheFirstAndLastPopulatedLayer() {
        AirProbeSource source = new AirProbeSource(Set.of(-64, 319));

        IslandTemplate template = new TemplateCapture().capture(source, ONE_CHUNK,
                -64, 320, 384, 250_000, "boundary-layers", new Island.SpawnOffset(0, 0, 0));

        assertEquals(384, template.height());
        assertTrue(source.airAtCalls.get() <= 2,
                "each directional scan should stop at the first coordinate in its hit layer");
    }

    @Test
    void classifiesCaveAirAndVoidAirAsAir() {
        TemplateCapture.BlockSource source = (x, y, z) -> "minecraft:air";

        assertTrue(source.isAir(null));
        assertTrue(source.isAir("minecraft:air"));
        assertTrue(source.isAir("minecraft:air[waterlogged=false]"));
        assertTrue(source.isAir("minecraft:cave_air"));
        assertTrue(source.isAir("minecraft:cave_air[foo=true]"));
        assertTrue(source.isAir("minecraft:void_air"));
        assertTrue(source.isAir("minecraft:void_air[foo=true]"));
        assertFalse(source.isAir("minecraft:stone"));
    }

    private static Set<Integer> layers(int first, int lastExclusive) {
        Set<Integer> layers = new HashSet<>();
        for (int y = first; y < lastExclusive; y++) layers.add(y);
        return layers;
    }

    private static final class AirProbeSource implements TemplateCapture.BlockSource {
        private final Set<Integer> populatedLayers;
        private final AtomicInteger blockDataCalls = new AtomicInteger();
        private final AtomicInteger airAtCalls = new AtomicInteger();

        private AirProbeSource(Set<Integer> populatedLayers) {
            this.populatedLayers = Set.copyOf(populatedLayers);
        }

        @Override
        public String blockData(int x, int y, int z) {
            blockDataCalls.incrementAndGet();
            return populatedLayers.contains(y) ? "minecraft:stone" : "minecraft:air";
        }

        @Override
        public boolean isAirAt(int x, int y, int z) {
            airAtCalls.incrementAndGet();
            return !populatedLayers.contains(y);
        }
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
