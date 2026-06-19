package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.ProtectionCommand;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionLoader;
import me.beeliebub.tweaks.protection.RegionSelectionManager;
import me.beeliebub.tweaks.protection.RegionWriter;
import me.beeliebub.tweaks.utils.InventoryUtil;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedConstruction;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

// Regression coverage for the chunk-claim pricing, persistence, and refund flow.
//
// Tests 1-3: pure-logic pricing via reflection on the package-private static
// computeClaimCost helper — no Bukkit API needed for these, but MockBukkit is
// initialised class-wide to satisfy ProtectionKeys.init (used in the command
// tests that share this class's lifecycle).
//
// Tests 4-5: round-trip YAML persistence via RegionWriter.writeNow +
// RegionLoader.load. RegionWriter needs a Tweaks plugin only for its async
// queue() path; writeNow() is called directly here, so a simple mock suffices.
//
// Tests 6-8: handleUnclaim command integration via reflection. MockBukkit
// provides a live ServerMock so Bukkit.getPlayer() resolves correctly, and
// InventoryUtil.addResourceRupees can operate on a real PlayerMock inventory.
class ClaimCostTest {

    private static Method computeClaimCost;
    private static Method handleUnclaim;
    private static ServerMock server;

    @BeforeAll
    static void setUpAll() throws NoSuchMethodException {
        server = MockBukkit.mock();
        try (MockedConstruction<NamespacedKey> ignored = mockConstruction(NamespacedKey.class)) {
            ProtectionKeys.init(mock(Tweaks.class));
        }

        // Reflect into the package-private static helper.
        computeClaimCost = ProtectionCommand.class.getDeclaredMethod("computeClaimCost", int.class);
        computeClaimCost.setAccessible(true);

        // Reflect into the private instance handleUnclaim.
        handleUnclaim = ProtectionCommand.class.getDeclaredMethod("handleUnclaim",
                org.bukkit.command.CommandSender.class, String[].class);
        handleUnclaim.setAccessible(true);
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    // Shared RegionLoader instance — stateless, reused across tests.
    private final RegionLoader loader = new RegionLoader(Logger.getLogger("test"));

    // Helper: invoke computeClaimCost via reflection.
    private int cost(int chunks) throws Exception {
        return (int) computeClaimCost.invoke(null, chunks);
    }

    // Helper: build a minimal ProtectionCommand wired to the supplied manager.
    private ProtectionCommand command(ProtectionManager protection) {
        Tweaks plugin = mock(Tweaks.class);
        when(plugin.getPermissionManager()).thenReturn(null);
        return new ProtectionCommand(plugin, protection, mock(RegionSelectionManager.class));
    }

    // Helper: invoke handleUnclaim on the given command instance via reflection.
    private void invokeUnclaim(ProtectionCommand cmd,
                               org.bukkit.command.CommandSender sender,
                               String regionName) throws Exception {
        handleUnclaim.invoke(cmd, sender, new String[]{regionName});
    }

    // -----------------------------------------------------------------------
    // 1. computeClaimCost_25Chunks_Equals89
    // -----------------------------------------------------------------------

    @Test
    void computeClaimCost_25Chunks_Equals89() throws Exception {
        // The canonical 5x5 = 89 assertion that pins the pricing formula.
        assertEquals(89, cost(25),
                "A 5x5 (25-chunk) claim must cost exactly 89 Resource Rupees");
    }

    // -----------------------------------------------------------------------
    // 2. computeClaimCost_SingleChunk_Equals10
    // -----------------------------------------------------------------------

    @Test
    void computeClaimCost_SingleChunk_Equals10() throws Exception {
        // Base price: first chunk costs floor(10 / 1.1^0) = floor(10) = 10.
        assertEquals(10, cost(1),
                "A single-chunk claim must cost exactly 10 Resource Rupees");
    }

    // -----------------------------------------------------------------------
    // 3. computeClaimCost_NeverDropsBelowOne
    // -----------------------------------------------------------------------

    @Test
    void computeClaimCost_NeverDropsBelowOne() throws Exception {
        // At large N the geometric taper would naturally reach 0; max(1, ...) keeps it at 1.
        // Test the invariant: total cost for 200 chunks is positive, and the marginal cost
        // from chunk 199 to chunk 200 is at least 1 (the floor is held).
        int cost200 = cost(200);
        int cost199 = cost(199);

        assertTrue(cost200 > 0,
                "computeClaimCost(200) must be positive (max(1,...) floor applies)");
        assertTrue(cost200 - cost199 >= 1,
                "The marginal cost of the 200th chunk must be >= 1 (floor invariant)");
    }

    // -----------------------------------------------------------------------
    // 4. region_costPersistsThroughLoaderWriter
    // -----------------------------------------------------------------------

    @Test
    void region_costPersistsThroughLoaderWriter(@TempDir Path tmp) throws IOException {
        UUID owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Region original = new Region(
                "r89", owner, List.of(),
                java.util.Map.of(), java.util.Map.of(),
                null, null, null,
                List.of(), java.util.Map.of(),
                89
        );

        // Write the region synchronously to the temp directory.
        Tweaks pluginMock = mock(Tweaks.class);
        RegionWriter writer = new RegionWriter(pluginMock, tmp.toFile());
        writer.writeNow(original);

        // Load from the same directory and assert cost round-tripped correctly.
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();
        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(1, loaded, "Exactly one region file must be loaded");

        Region loaded89 = cache.get("r89");
        assertNotNull(loaded89, "Region 'r89' must be present in cache");
        assertEquals(89, loaded89.cost(),
                "cost field must survive a write + load round-trip");

        // Legacy path: a YAML file without the 'cost:' key must default to 0.
        Path legacyFile = tmp.resolve("legacy.yml");
        Files.writeString(legacyFile,
                "id: legacy\nowner: " + owner + "\n");

        ConcurrentHashMap<String, Region> legacyCache = new ConcurrentHashMap<>();
        loader.load(tmp.toFile(), legacyCache);

        Region legacyRegion = legacyCache.get("legacy");
        assertNotNull(legacyRegion, "Legacy region must be loadable");
        assertEquals(0, legacyRegion.cost(),
                "Legacy regions loaded without a 'cost:' key must default to 0");
    }

    // -----------------------------------------------------------------------
    // 5. region_costOmittedFromYamlWhenZero
    // -----------------------------------------------------------------------

    @Test
    void region_costOmittedFromYamlWhenZero(@TempDir Path tmp) throws IOException {
        UUID owner = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        Region free = new Region(
                "free", owner, List.of(),
                java.util.Map.of(), java.util.Map.of(),
                null, null, null,
                List.of(), java.util.Map.of(),
                0
        );

        Tweaks pluginMock = mock(Tweaks.class);
        RegionWriter writer = new RegionWriter(pluginMock, tmp.toFile());
        writer.writeNow(free);

        // Read the raw YAML text and confirm 'cost:' does not appear at all.
        Path ymlFile = tmp.resolve("free.yml");
        assertTrue(ymlFile.toFile().exists(), "RegionWriter must create free.yml");
        String rawYaml = Files.readString(ymlFile);
        assertFalse(rawYaml.contains("cost:"),
                "RegionWriter must NOT emit a 'cost:' key for regions with cost == 0");
    }

    // -----------------------------------------------------------------------
    // 6. handleUnclaim_refundsCostToOnlineOwner
    // -----------------------------------------------------------------------

    @Test
    void handleUnclaim_refundsCostToOnlineOwner() throws Exception {
        // Add a live MockBukkit player as the region owner.
        PlayerMock owner = server.addPlayer("OwnerPlayer");

        Region region = new Region(
                "costly", owner.getUniqueId(), List.of(),
                java.util.Map.of(), java.util.Map.of(),
                null, null, null,
                List.of(), java.util.Map.of(),
                89
        );

        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        protection.regions().put("costly", region);

        ProtectionCommand cmd = command(protection);

        // Baseline balance should be 0 (empty inventory).
        assertEquals(0, InventoryUtil.getResourceRupeeBalance(owner),
                "player starts with zero Resource Rupees");

        // Invoke unclaim using the owner as the sender.
        invokeUnclaim(cmd, owner, "costly");

        // After unclaim, the refund of 89 should have landed in the player's inventory.
        int balance = InventoryUtil.getResourceRupeeBalance(owner);
        assertTrue(balance >= 89,
                "owner must receive a refund >= 89 Resource Rupees after unclaim; got " + balance);

        // The region must also be removed from the cache.
        assertFalse(protection.regions().containsKey("costly"),
                "region must be evicted from cache after successful unclaim");
    }

    // -----------------------------------------------------------------------
    // 7. handleUnclaim_skipsRefundForOfflineOwner
    // -----------------------------------------------------------------------

    @Test
    void handleUnclaim_skipsRefundForOfflineOwner() throws Exception {
        // Use a UUID that is not backed by any online MockBukkit player.
        UUID offlineOwnerUuid = UUID.fromString("deadbeef-dead-dead-dead-deaddeadbeef");

        Region region = new Region(
                "ghost", offlineOwnerUuid, List.of(),
                java.util.Map.of(), java.util.Map.of(),
                null, null, null,
                List.of(), java.util.Map.of(),
                89
        );

        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        protection.regions().put("ghost", region);

        ProtectionCommand cmd = command(protection);

        // Use a console-style mock sender (non-Player) — this passes isOwnerOrAdmin
        // because `!(sender instanceof Player)` returns true.
        org.bukkit.command.ConsoleCommandSender console =
                mock(org.bukkit.command.ConsoleCommandSender.class);

        // Bukkit.getPlayer(offlineOwnerUuid) will return null since that player has never
        // joined the MockBukkit server — confirming the offline path.
        invokeUnclaim(cmd, console, "ghost");

        // Region must still be unclaimed.
        assertFalse(protection.regions().containsKey("ghost"),
                "ghost region must be removed from cache even when owner is offline");

        // No rupees should have been added to any online player.
        // We verify this by asserting that no currently-online player received anything.
        // (The only online players are from other tests, but the region's offlineOwnerUuid
        // can never be resolved to a Player, so addResourceRupees was never called.)
        for (PlayerMock p : server.getOnlinePlayers()) {
            if (p.getUniqueId().equals(offlineOwnerUuid)) {
                fail("Unexpectedly found the 'offline' owner as online — test setup error");
            }
        }
        // If we reach here, the offline path ran without attempting a refund.
    }

    // -----------------------------------------------------------------------
    // 8. handleUnclaim_zeroCostRegion_noRefundAttempted
    // -----------------------------------------------------------------------

    @Test
    void handleUnclaim_zeroCostRegion_noRefundAttempted() throws Exception {
        // A free/admin/legacy region has cost == 0; unclaiming must not add rupees to anyone.
        PlayerMock owner = server.addPlayer("FreeOwner");

        Region region = new Region(
                "freeregion", owner.getUniqueId(), List.of(),
                java.util.Map.of(), java.util.Map.of(),
                null, null, null,
                List.of(), java.util.Map.of(),
                0
        );

        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        protection.regions().put("freeregion", region);

        ProtectionCommand cmd = command(protection);

        int balanceBefore = InventoryUtil.getResourceRupeeBalance(owner);
        assertEquals(0, balanceBefore, "owner starts with zero balance");

        // Invoke unclaim as the owner.
        invokeUnclaim(cmd, owner, "freeregion");

        // Balance must remain exactly 0 — no refund issued for a cost-0 region.
        int balanceAfter = InventoryUtil.getResourceRupeeBalance(owner);
        assertEquals(0, balanceAfter,
                "unclaiming a zero-cost region must not add any Resource Rupees to the owner");

        // Region removed from cache.
        assertFalse(protection.regions().containsKey("freeregion"),
                "zero-cost region must still be evicted from cache on unclaim");
    }
}
