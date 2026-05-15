package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.PDCUtil;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.world.ChunkLoadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

// End-to-end checks against the live plugin loaded into MockBukkit. The
// scope is intentionally narrow: the listener-unit tests already cover
// handler logic in isolation, and the manager tests cover routing math —
// this file proves the wires are connected (lifecycle init, listener
// registration, PDC round-trip via the real chunk implementation).
class ProtectionIntegrationTest {

    private ServerMock server;
    private Tweaks plugin;
    private ProtectionManager protection;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        protection = plugin.getProtectionManager();
        world = server.addSimpleWorld("region-test");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginExposesProtectionManager() {
        assertNotNull(protection);
        assertNotNull(protection.regions());
        assertNotNull(protection.pendingStamps());
        assertNotNull(protection.orphanedRegions());
    }

    @Test
    void chunkLoadEventDrainsPendingStampsAndStampsPdc() {
        Chunk chunk = world.getChunkAt(0, 0);
        long key = chunk.getChunkKey();

        Set<String> pending = ConcurrentHashMap.newKeySet();
        pending.add("home");
        protection.pendingStamps().put(key, pending);

        ChunkLoadEvent event = new ChunkLoadEvent(chunk, false);
        server.getPluginManager().callEvent(event);

        assertFalse(protection.pendingStamps().containsKey(key),
                "ChunkListener must drain the pending entry on load");
        assertTrue(PDCUtil.read(chunk).contains("home"),
                "PDC must carry the stamped region pointer");
    }

    @Test
    void orphanedRegionsPurgedOnChunkLoad() {
        Chunk chunk = world.getChunkAt(5, 5);
        PDCUtil.append(chunk, "dead");
        assertTrue(PDCUtil.read(chunk).contains("dead"));

        protection.orphanedRegions().add("dead");
        server.getPluginManager().callEvent(new ChunkLoadEvent(chunk, false));

        assertFalse(PDCUtil.read(chunk).contains("dead"),
                "Orphaned pointer must be stripped on chunk reload");
    }

    @Test
    void claimPopulatesPendingStampsForLargeBox() {
        Region region = new Region("admin", UUID.randomUUID(), List.of(),
                EnumSet.noneOf(RegionFlag.class));

        // 6x6 = 36 chunks → lazy path.
        protection.claim(region, world, 0, 0, 95, 95);

        assertEquals(36, protection.pendingStamps().size());
        for (Set<String> ids : protection.pendingStamps().values()) {
            assertTrue(ids.contains("admin"));
        }
    }

    @Test
    void unclaimRemovesRegionAndOrphans() {
        Region region = new Region("home", UUID.randomUUID(), List.of(),
                EnumSet.noneOf(RegionFlag.class));
        protection.regions().put("home", region);

        assertTrue(protection.unclaim("home"));
        assertFalse(protection.regions().containsKey("home"));
        assertTrue(protection.orphanedRegions().contains("home"));
    }

    @Test
    void claimSurfacesAsBlockBreakProtectionForOutsider() {
        Region region = new Region("home",
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                List.of(),
                EnumSet.noneOf(RegionFlag.class));
        protection.regions().put("home", region);

        Chunk chunk = world.getChunkAt(10, 10);
        PDCUtil.append(chunk, "home");

        Player outsider = server.addPlayer("stranger");
        assertFalse(protection.isAllowed(
                chunk.getBlock(0, 64, 0).getLocation(),
                outsider.getUniqueId(),
                RegionFlag.BLOCK_BREAK));
    }
}
