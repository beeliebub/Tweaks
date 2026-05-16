package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.FlagTarget;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import me.beeliebub.tweaks.protection.RegionLoader;
import me.beeliebub.tweaks.protection.RegionWriter;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RegionWriterTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void writeNowProducesYamlTheLoaderCanRead(@TempDir Path tmp) throws Exception {
        Region original = new Region(
                "plot",
                OWNER,
                List.of(MEMBER),
                Map.of(RegionFlag.PVP, Map.of(FlagTarget.DEFAULT, false, FlagTarget.OWNER, true)),
                Map.of(RegionFlag.ALLOW_BLOCK_BREAK, Set.of(Material.STONE, Material.DIRT)),
                "parent_id",
                new Region.RegionBounds(-3, 4, 2, 7));

        RegionWriter writer = new RegionWriter(mock(Tweaks.class), tmp.toFile());
        writer.writeNow(original);

        File expected = new File(tmp.toFile(), "plot.yml");
        assertTrue(expected.exists(), "writer should have written plot.yml");

        ConcurrentHashMap<String, Region> reloaded = new ConcurrentHashMap<>();
        int loaded = new RegionLoader(Logger.getLogger("test")).load(tmp.toFile(), reloaded);
        assertEquals(1, loaded);

        Region back = reloaded.get("plot");
        assertNotNull(back);
        assertEquals(OWNER, back.owner());
        assertEquals(List.of(MEMBER), back.members());
        assertEquals("parent_id", back.parentId());
        assertNotNull(back.bounds());
        assertEquals(-3, back.bounds().minChunkX());
        assertEquals(2, back.bounds().maxChunkX());
        assertEquals(7, back.bounds().maxChunkZ());
        assertEquals(Map.of(FlagTarget.DEFAULT, false, FlagTarget.OWNER, true),
                back.rulesFor(RegionFlag.PVP));
        assertEquals(Set.of(Material.STONE, Material.DIRT),
                back.materialsFor(RegionFlag.ALLOW_BLOCK_BREAK));
    }

    @Test
    void writeNowOverwritesExistingFileAtomically(@TempDir Path tmp) throws Exception {
        RegionWriter writer = new RegionWriter(mock(Tweaks.class), tmp.toFile());
        Region first = new Region("plot", OWNER, List.of(), Map.of());
        writer.writeNow(first);

        Region second = first.withBounds(new Region.RegionBounds(0, 0, 5, 5));
        writer.writeNow(second);

        ConcurrentHashMap<String, Region> reloaded = new ConcurrentHashMap<>();
        new RegionLoader(Logger.getLogger("test")).load(tmp.toFile(), reloaded);

        Region.RegionBounds b = reloaded.get("plot").bounds();
        assertNotNull(b);
        assertEquals(5, b.maxChunkX());
        assertEquals(5, b.maxChunkZ());
    }
}
