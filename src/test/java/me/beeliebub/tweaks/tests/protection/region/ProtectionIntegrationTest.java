package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.utils.PDCUtil;
import me.beeliebub.tweaks.protection.region.FlagTarget;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
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
        String stampKey = ProtectionManager.stampKey(world.getName(), key);
        protection.pendingStamps().put(stampKey, pending);

        ChunkLoadEvent event = new ChunkLoadEvent(chunk, false);
        server.getPluginManager().callEvent(event);

        assertFalse(protection.pendingStamps().containsKey(stampKey),
                "ChunkListener must drain the pending entry on load");
        assertTrue(PDCUtil.read(chunk, protection.regionPointersKey()).contains("home"),
                "PDC must carry the stamped region pointer");
    }

    @Test
    void orphanedRegionsPurgedOnChunkLoad() {
        Chunk chunk = world.getChunkAt(5, 5);
        PDCUtil.append(chunk, "dead", protection.regionPointersKey());
        assertTrue(PDCUtil.read(chunk, protection.regionPointersKey()).contains("dead"));

        protection.orphanedRegions().add(world.getName() + ":dead");
        server.getPluginManager().callEvent(new ChunkLoadEvent(chunk, false));

        assertFalse(PDCUtil.read(chunk, protection.regionPointersKey()).contains("dead"),
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

        assertEquals(ProtectionManager.UnclaimResult.OK,
                protection.unclaim("home").result());
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
        PDCUtil.append(chunk, "home", protection.regionPointersKey());

        Player outsider = server.addPlayer("stranger");
        assertFalse(protection.isAllowed(
                chunk.getBlock(0, 64, 0).getLocation(),
                outsider.getUniqueId(),
                RegionFlag.BLOCK_BREAK));
    }

    // ------------------------------------------------------------------
    // Global / wilderness region behaviour
    // ------------------------------------------------------------------

    @Test
    void unownedChunkDefaultsToGlobalRegionPermissive() {
        // No PDC stamping, no chunk-level regions — pure wilderness.
        Chunk chunk = world.getChunkAt(20, 20);
        Player player = server.addPlayer("wanderer");

        // Default global region has no flag rules, so wilderness stays permissive.
        assertTrue(protection.isAllowed(
                chunk.getBlock(0, 64, 0).getLocation(),
                player.getUniqueId(),
                RegionFlag.BLOCK_BREAK));
        // isExplicitlyAllowed still defaults to false in wilderness — vanilla gamerules win.
        assertFalse(protection.isExplicitlyAllowed(
                chunk.getBlock(0, 64, 0).getLocation(),
                player.getUniqueId(),
                RegionFlag.MOB_GRIEFING));
    }

    @Test
    void globalRegionFlagBlocksActionInWilderness() {
        Chunk chunk = world.getChunkAt(30, 30);
        Player player = server.addPlayer("wanderer2");

        // Admin restricts BLOCK_BREAK globally for this world.
        assertTrue(protection.setFlag(
                world,
                ProtectionManager.GLOBAL_REGION_ID,
                RegionFlag.BLOCK_BREAK,
                FlagTarget.DEFAULT,
                false));

        assertFalse(protection.isAllowed(
                chunk.getBlock(0, 64, 0).getLocation(),
                player.getUniqueId(),
                RegionFlag.BLOCK_BREAK),
                "Global region rule must propagate to unowned chunks");
    }

    @Test
    void globalRegionExplicitAllowOptsInToGamerule() {
        Chunk chunk = world.getChunkAt(40, 40);
        Player player = server.addPlayer("wanderer3");

        // Admin explicitly opts wilderness in to mob griefing for this world.
        assertTrue(protection.setFlag(
                world,
                ProtectionManager.GLOBAL_REGION_ID,
                RegionFlag.MOB_GRIEFING,
                FlagTarget.DEFAULT,
                true));

        assertTrue(protection.isExplicitlyAllowed(
                chunk.getBlock(0, 64, 0).getLocation(),
                player.getUniqueId(),
                RegionFlag.MOB_GRIEFING));
    }

    @Test
    void claimedChunkStillRespectsLocalRegionOverGlobal() {
        Region region = new Region("home",
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                List.of(),
                EnumSet.noneOf(RegionFlag.class));
        protection.regions().put("home", region);

        Chunk chunk = world.getChunkAt(50, 50);
        PDCUtil.append(chunk, "home", protection.regionPointersKey());

        // Global region is permissive...
        assertTrue(protection.setFlag(
                world,
                ProtectionManager.GLOBAL_REGION_ID,
                RegionFlag.BLOCK_BREAK,
                FlagTarget.DEFAULT,
                true));
        // ...but the local 'home' region's chain wins for the stamped chunk;
        // outsider stays blocked because they aren't a member.
        Player outsider = server.addPlayer("stranger2");
        assertFalse(protection.isAllowed(
                chunk.getBlock(0, 64, 0).getLocation(),
                outsider.getUniqueId(),
                RegionFlag.BLOCK_BREAK));
    }

    @Test
    void globalRegionSurvivesUnclaimAttempt() {
        // Materialise the per-world global, then verify unclaim refuses it.
        Region global = protection.globalRegion(world);
        assertNotNull(global);
        assertEquals(ProtectionManager.UnclaimResult.GLOBAL_DENIED,
                protection.unclaim(ProtectionManager.GLOBAL_REGION_ID).result());
        assertSame(global, protection.globalRegion(world));
        assertTrue(protection.regions().containsKey(world.getName() + ":"
                + ProtectionManager.GLOBAL_REGION_ID));
    }

    // RegionGUI's click handlers call pm.setFlag/setMaterials/setEntities with the
    // (World, id, ...) overload, deriving the world from region.worldName() via
    // Bukkit.getWorld(). This replicates that exact pattern on a per-world global
    // to catch any future regression where the GUI path stops finding the global.
    @Test
    void guiCallPatternMutatesPerWorldGlobalSuccessfully() {
        Region global = protection.globalRegion(world);
        assertNotNull(global);
        assertNotNull(global.worldName());

        // Mirror RegionGUI#worldOf + the cycleBooleanRule "Unset -> True" path.
        org.bukkit.World resolved = org.bukkit.Bukkit.getWorld(global.worldName());
        assertNotNull(resolved, "Bukkit.getWorld must resolve the per-world global's worldName");
        assertTrue(protection.setFlag(resolved, global.id(),
                        RegionFlag.PVP, FlagTarget.DEFAULT, true),
                "GUI's world-aware setFlag call must succeed on a fresh per-world global");

        assertEquals(Boolean.TRUE,
                protection.globalRegion(world).rulesFor(RegionFlag.PVP).get(FlagTarget.DEFAULT));
    }

    // Drives /region flag __global__ ... through the real Bukkit command dispatch
    // (no reflection, no mocks). Confirms a fresh per-world global region accepts
    // the first boolean flag write without the "already at the requested value"
    // short-circuit firing.
    @Test
    void slashRegionFlagOnFreshGlobalAcceptsFirstWrite() {
        Player admin = server.addPlayer("admin");
        admin.setOp(true); // grants tweaks.protection.* via OP
        admin.teleport(world.getSpawnLocation());

        admin.performCommand("region flag __global__ BLOCK_BREAK false");

        Region globalAfter = protection.globalRegion(world);
        assertNotNull(globalAfter);
        assertEquals(Boolean.FALSE,
                globalAfter.rulesFor(RegionFlag.BLOCK_BREAK).get(FlagTarget.DEFAULT),
                "First /region flag on the per-world global must persist the rule");

        // Second invocation with the same value should report 'no change' but
        // crucially the first one must have actually written.
        admin.performCommand("region flag __global__ PVP true");
        assertEquals(Boolean.TRUE,
                protection.globalRegion(world).rulesFor(RegionFlag.PVP).get(FlagTarget.DEFAULT));
    }

    @Test
    void globalRegionFlagIsIsolatedAcrossWorlds() {
        World otherWorld = server.addSimpleWorld("region-test-other");
        Chunk hereChunk = world.getChunkAt(60, 60);
        Chunk thereChunk = otherWorld.getChunkAt(60, 60);
        Player player = server.addPlayer("traveler");

        // Restrict BLOCK_BREAK only in `world`'s wilderness.
        assertTrue(protection.setFlag(
                world,
                ProtectionManager.GLOBAL_REGION_ID,
                RegionFlag.BLOCK_BREAK,
                FlagTarget.DEFAULT,
                false));

        assertFalse(protection.isAllowed(
                hereChunk.getBlock(0, 64, 0).getLocation(),
                player.getUniqueId(),
                RegionFlag.BLOCK_BREAK),
                "Block break must be denied in the restricted world's wilderness");

        assertTrue(protection.isAllowed(
                thereChunk.getBlock(0, 64, 0).getLocation(),
                player.getUniqueId(),
                RegionFlag.BLOCK_BREAK),
                "Other world's wilderness must remain unaffected by the first world's rule");
    }
}
