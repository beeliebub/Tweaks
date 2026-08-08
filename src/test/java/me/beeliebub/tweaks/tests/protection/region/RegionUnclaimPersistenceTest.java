package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionLoader;
import me.beeliebub.tweaks.protection.region.RegionWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RegionUnclaimPersistenceTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void unclaimArchivesFileAndFreshLoaderDoesNotRestoreIt(@TempDir Path tmp) throws Exception {
        Tweaks plugin = mock(Tweaks.class);
        RegionWriter writer = new RegionWriter(plugin, tmp.toFile());
        ProtectionManager protection = new ProtectionManager(plugin);
        Region region = new Region("home", OWNER, List.of(), java.util.Map.of(),
                java.util.Map.of(), null, new Region.RegionBounds(0, 0, 1, 1), "world");

        writer.writeNow(region);
        protection.regions().put(ProtectionManager.keyOf(region), region);
        protection.setWriter(writer);

        assertTrue(Files.exists(tmp.resolve("world/home.yml")));

        ProtectionManager.UnclaimOutcome outcome = protection.unclaim("home");

        assertEquals(ProtectionManager.UnclaimResult.OK, outcome.result());
        assertFalse(Files.exists(tmp.resolve("world/home.yml")));
        try (var archived = Files.list(tmp.resolve("_deleted/world"))) {
            assertEquals(1, archived.filter(path -> path.getFileName().toString().endsWith(".yml")).count());
        }

        ConcurrentHashMap<String, Region> freshCache = new ConcurrentHashMap<>();
        assertEquals(0, new RegionLoader(Logger.getLogger("test"))
                .load(tmp.toFile(), freshCache));
        assertTrue(freshCache.isEmpty(), "archived regions must stay absent after a fresh load");
    }
}
