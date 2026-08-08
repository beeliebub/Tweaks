package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RegionLoaderNonConformingNameTest {

    @Test
    void legacyBadNameLoadsUnchangedAndIsReported(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("legacy.yml"), """
                id: Bad Name
                owner: 11111111-1111-1111-1111-111111111111
                world: world
                """);
        Logger logger = mock(Logger.class);
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        RegionLoader.LoadResult result = new RegionLoader(logger).loadWithReport(tmp.toFile(), cache);

        assertEquals(1, result.loaded());
        assertEquals(1, result.nonConformingNames());
        assertNotNull(cache.get("world:Bad Name"));
        verify(logger).warning(contains("Bad Name"));
    }
}
