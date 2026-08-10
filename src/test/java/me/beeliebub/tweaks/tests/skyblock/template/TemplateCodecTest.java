package me.beeliebub.tweaks.tests.skyblock.template;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.template.IslandTemplate;
import me.beeliebub.tweaks.skyblock.template.TemplateCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateCodecTest {
    @Test
    void roundTripPreservesPaletteIndicesOffsetsAndBlockEntities() {
        IslandTemplate source = new IslandTemplate("starter", 2, 2, 1,
                List.of("minecraft:air", "minecraft:chest[facing=north]"),
                new int[]{0, 1, 1, 0},
                Map.of(new IslandTemplate.BlockEntity(1, 0, 0, "minecraft:chest"), "opaque-payload"),
                new Island.SpawnOffset(1, 2, 3));

        IslandTemplate decoded = TemplateCodec.decode(TemplateCodec.encode(source));

        assertEquals(source.id(), decoded.id());
        assertEquals(source.palette(), decoded.palette());
        assertArrayEquals(source.blockIndices(), decoded.blockIndices());
        assertEquals(source.blockEntities(), decoded.blockEntities());
        assertEquals(source.spawnOffset(), decoded.spawnOffset());
    }

    @Test
    void rejectsTrailingBytesAndInvalidIndices() {
        IslandTemplate source = new IslandTemplate("x", 1, 1, 1, List.of("minecraft:stone"),
                new int[]{0}, Map.of(), new Island.SpawnOffset(0, 0, 0));
        byte[] encoded = TemplateCodec.encodeBytes(source);
        byte[] trailing = java.util.Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(IllegalArgumentException.class, () -> TemplateCodec.decodeBytes(trailing));
        assertThrows(IllegalArgumentException.class, () -> new IslandTemplate("x", 1, 1, 1,
                List.of("minecraft:stone"), new int[]{1}, Map.of(), new Island.SpawnOffset(0, 0, 0)));
    }
}
