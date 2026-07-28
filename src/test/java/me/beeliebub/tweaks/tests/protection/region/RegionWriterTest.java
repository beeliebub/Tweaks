package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.FlagTarget;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import me.beeliebub.tweaks.protection.region.RegionLoader;
import me.beeliebub.tweaks.protection.region.RegionWriter;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void writeNowPlacesWorldTaggedRegionsInWorldSubdirectory(@TempDir Path tmp) throws Exception {
        RegionWriter writer = new RegionWriter(mock(Tweaks.class), tmp.toFile());
        Region overworldHome = new Region("home", OWNER, List.of(), Map.of(), Map.of(), null,
                new Region.RegionBounds(0, 0, 1, 1), "world");
        Region netherHome = new Region("home", OWNER, List.of(), Map.of(), Map.of(), null,
                new Region.RegionBounds(0, 0, 1, 1), "world_nether");

        writer.writeNow(overworldHome);
        writer.writeNow(netherHome);

        File overworldPath = new File(new File(tmp.toFile(), "world"), "home.yml");
        File netherPath = new File(new File(tmp.toFile(), "world_nether"), "home.yml");
        assertTrue(overworldPath.exists(), "world/home.yml should exist");
        assertTrue(netherPath.exists(), "world_nether/home.yml should exist");
    }

    // RegionWriter is final, so we can't subclass to make queue() synchronous.
    // Instead, build a Tweaks mock whose BukkitScheduler runs the submitted
    // Runnable inline. This lets tests exercise the real setFlag→queue path
    // without spinning up MockBukkit's full server / async infrastructure.
    private static Tweaks pluginWithInlineScheduler() {
        Tweaks plugin = mock(Tweaks.class, RETURNS_DEEP_STUBS);
        when(plugin.getServer().getScheduler().runTaskAsynchronously(any(Plugin.class), any(Runnable.class)))
                .thenAnswer(inv -> {
                    Runnable r = inv.getArgument(1);
                    r.run();
                    return mock(BukkitTask.class);
                });
        return plugin;
    }

    private static RegionWriter syncWriter(Path tmp) {
        return new RegionWriter(pluginWithInlineScheduler(), tmp.toFile());
    }

    // End-to-end roundtrip for the persistence fix: a flag mutation queued by
    // ProtectionManager#setFlag must land on disk and reload identically.
    @Test
    void flagMutationRoundTripsThroughYaml(@TempDir Path tmp) throws Exception {
        RegionWriter writer = syncWriter(tmp);
        me.beeliebub.tweaks.protection.region.ProtectionManager mgr =
                new me.beeliebub.tweaks.protection.region.ProtectionManager(mock(Tweaks.class));
        mgr.setWriter(writer);

        // Seed a real region under a world (would normally come from tryClaim).
        Region claim = new Region("plot", OWNER, List.of(), Map.of(), Map.of(),
                null, new Region.RegionBounds(0, 0, 1, 1), "world", List.of(), Map.of());
        mgr.regions().put("world:plot", claim);
        writer.writeNow(claim); // initial on-disk state

        org.bukkit.World world = mock(org.bukkit.World.class);
        org.mockito.Mockito.when(world.getName()).thenReturn("world");

        // Mutate via the production path — syncWriter persists immediately.
        assertTrue(mgr.setFlag(world, "plot", RegionFlag.PVP, FlagTarget.DEFAULT, false));
        assertTrue(mgr.setMaterials(world, "plot", RegionFlag.ALLOW_BLOCK_BREAK,
                Set.of(Material.STONE)));

        ConcurrentHashMap<String, Region> reloaded = new ConcurrentHashMap<>();
        new RegionLoader(Logger.getLogger("test")).load(tmp.toFile(), reloaded);
        Region back = reloaded.get("world:plot");
        assertNotNull(back);
        assertEquals(Boolean.FALSE, back.rulesFor(RegionFlag.PVP).get(FlagTarget.DEFAULT));
        assertEquals(Set.of(Material.STONE), back.materialsFor(RegionFlag.ALLOW_BLOCK_BREAK));
    }

    @Test
    void perWorldGlobalFlagRoundTripsThroughYaml(@TempDir Path tmp) throws Exception {
        RegionWriter writer = syncWriter(tmp);
        me.beeliebub.tweaks.protection.region.ProtectionManager mgr =
                new me.beeliebub.tweaks.protection.region.ProtectionManager(mock(Tweaks.class));
        mgr.setWriter(writer);

        org.bukkit.World world = mock(org.bukkit.World.class);
        org.mockito.Mockito.when(world.getName()).thenReturn("alpha");

        assertTrue(mgr.setFlag(world, me.beeliebub.tweaks.protection.region.ProtectionManager.GLOBAL_REGION_ID,
                RegionFlag.BLOCK_BREAK, FlagTarget.DEFAULT, false));

        ConcurrentHashMap<String, Region> reloaded = new ConcurrentHashMap<>();
        new RegionLoader(Logger.getLogger("test")).load(tmp.toFile(), reloaded);
        Region back = reloaded.get("alpha:" + me.beeliebub.tweaks.protection.region.ProtectionManager.GLOBAL_REGION_ID);
        assertNotNull(back, "per-world global must persist under its composite key");
        assertEquals(Boolean.FALSE, back.rulesFor(RegionFlag.BLOCK_BREAK).get(FlagTarget.DEFAULT));
    }

    @Test
    void managerListRoundTripsThroughYaml(@TempDir Path tmp) throws Exception {
        UUID manager = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Region original = new Region("plot", OWNER, List.of(MEMBER), Map.of(), Map.of(), null,
                null, "world").addManager(manager);

        RegionWriter writer = new RegionWriter(mock(Tweaks.class), tmp.toFile());
        writer.writeNow(original);

        ConcurrentHashMap<String, Region> reloaded = new ConcurrentHashMap<>();
        new RegionLoader(Logger.getLogger("test")).load(tmp.toFile(), reloaded);
        Region back = reloaded.get("world:plot");
        assertNotNull(back, "world-tagged region should load under composite key");
        assertTrue(back.isManager(manager));
        assertTrue(back.isMember(manager));
    }
}
