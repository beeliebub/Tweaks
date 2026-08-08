package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionLoader;
import me.beeliebub.tweaks.protection.region.RegionWriter;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegionWriterOrderingTest {

    @Test
    void rapidWritesForOneRegionFinishInSubmissionOrder(@TempDir Path tmp) throws Exception {
        Tweaks plugin = mock(Tweaks.class, RETURNS_DEEP_STUBS);
        when(plugin.getServer().getScheduler().runTaskAsynchronously(any(Plugin.class), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(1).run();
                    return mock(BukkitTask.class);
                });
        Region first = region(1);
        Region second = region(2);
        RegionWriter writer = new RegionWriter(plugin, tmp.toFile());
        writer.queue(first);
        writer.queue(second);

        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();
        new RegionLoader(Logger.getLogger("ordering")).load(tmp.toFile(), cache);
        assertEquals(2, cache.get("world:home").bounds().maxChunkX());
    }

    private static Region region(int max) {
        return new Region("home", UUID.randomUUID(), List.of(), Map.of(), Map.of(), null,
                new Region.RegionBounds(0, 0, max, max), "world");
    }
}
