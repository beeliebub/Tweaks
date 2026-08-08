package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionWriter;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReclaimOrphanedIdTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static Tweaks pluginWithInlineScheduler() {
        Tweaks plugin = mock(Tweaks.class, RETURNS_DEEP_STUBS);
        when(plugin.getServer().getScheduler().runTaskAsynchronously(
                any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return mock(BukkitTask.class);
        });
        return plugin;
    }

    @Test
    void reclaimClearsOrphanMarkerAndAllowsWritingAgain(@TempDir Path tmp) throws Exception {
        Tweaks plugin = pluginWithInlineScheduler();
        RegionWriter writer = new RegionWriter(plugin, tmp.toFile());
        ProtectionManager protection = new ProtectionManager(plugin);
        Region original = new Region("home", OWNER, List.of(), Map.of(), Map.of(), null,
                new Region.RegionBounds(0, 0, 0, 0), "world");
        writer.writeNow(original);
        protection.regions().put("world:home", original);
        protection.setWriter(writer);
        protection.unclaim("home");
        assertTrue(protection.orphanedRegions().contains("world:home"));

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Chunk chunk = mock(Chunk.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(chunk.getPersistentDataContainer()).thenReturn(pdc);
        doReturn(List.of()).when(pdc).getOrDefault(any(NamespacedKey.class), any(), any());
        when(world.getChunkAtAsync(0, 0, true))
                .thenReturn(CompletableFuture.completedFuture(chunk));

        protection.claim(new Region("home", OWNER, List.of(), Map.of()), world,
                0, 0, 15, 15).join();

        assertFalse(protection.orphanedRegions().contains("world:home"));
        assertTrue(Files.exists(tmp.resolve("world/home.yml")),
                "a reclaimed id must no longer be blocked by the old tombstone");
    }
}
