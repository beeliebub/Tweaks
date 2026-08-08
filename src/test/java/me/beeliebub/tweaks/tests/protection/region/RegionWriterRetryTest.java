package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionWriter;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegionWriterRetryTest {

    @Test
    void failedWriteIsRetainedAndFlushDrainsIt(@TempDir Path tmp) throws Exception {
        Tweaks plugin = mock(Tweaks.class, RETURNS_DEEP_STUBS);
        when(plugin.getServer().getScheduler().runTaskAsynchronously(any(Plugin.class), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(1).run();
                    return mock(BukkitTask.class);
                });
        Path regions = tmp.resolve("regions");
        Files.writeString(regions, "blocking path");
        RegionWriter writer = new RegionWriter(plugin, regions.toFile());
        Region region = new Region("home", UUID.randomUUID(), List.of(), Map.of(), Map.of(), null,
                null, "world");

        writer.queue(region);
        Files.delete(regions);
        Files.createDirectory(regions);
        writer.flushNow(5_000);

        assertTrue(Files.exists(regions.resolve("world/home.yml")));
    }
}
