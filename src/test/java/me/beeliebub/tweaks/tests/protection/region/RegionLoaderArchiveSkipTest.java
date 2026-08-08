package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionLoaderArchiveSkipTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void skipsDeletedPathSegmentButLoadsWorldContainingDeletedSubstring(@TempDir Path tmp)
            throws Exception {
        Path archived = tmp.resolve("_deleted/world/removed.yml");
        Path live = tmp.resolve("my_deleted_world/kept.yml");
        Files.createDirectories(archived.getParent());
        Files.createDirectories(live.getParent());
        String archivedYaml = "id: removed\nowner: " + OWNER + "\nworld: world\n";
        String liveYaml = "id: kept\nowner: " + OWNER + "\nworld: my_deleted_world\n";
        Files.writeString(archived, archivedYaml);
        Files.writeString(live, liveYaml);

        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();
        int loaded = new RegionLoader(Logger.getLogger("test")).load(tmp.toFile(), cache);

        assertEquals(1, loaded);
        assertFalse(cache.containsKey("world:removed"));
        assertTrue(cache.containsKey("my_deleted_world:kept"));
    }
}
