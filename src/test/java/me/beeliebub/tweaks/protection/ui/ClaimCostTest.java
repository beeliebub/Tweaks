package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionLoader;
import me.beeliebub.tweaks.protection.region.RegionWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Regression coverage for the chunk-claim pricing formula and the {@code Region.cost} persistence
 * round-trip — moved into the production package (was {@code tests.protection.ClaimCostTest},
 * reflecting into {@code ProtectionCommand.computeClaimCost}) since that method now lives on
 * {@link ClaimSubcommand}, package-private in this same package. The unclaim-refund tests
 * that used to live in this class moved to {@code UnclaimRefundTest}, alongside the extraction
 * of {@code UnclaimSubcommand} into its own class.
 *
 * <p>No MockBukkit needed here — pricing is pure arithmetic and the persistence round-trip only
 * touches {@link RegionWriter#writeNow} / {@link RegionLoader#load}, neither of which requires a
 * live server.
 */
class ClaimCostTest {

    // Shared RegionLoader instance — stateless, reused across tests.
    private final RegionLoader loader = new RegionLoader(Logger.getLogger("test"));

    @Test
    void computeClaimCost_25Chunks_Equals89() {
        // The canonical 5x5 = 89 assertion that pins the pricing formula.
        assertEquals(89, ClaimSubcommand.computeClaimCost(25),
                "A 5x5 (25-chunk) claim must cost exactly 89 Resource Rupees");
    }

    @Test
    void computeClaimCost_SingleChunk_Equals10() {
        // Base price: first chunk costs floor(10 / 1.1^0) = floor(10) = 10.
        assertEquals(10, ClaimSubcommand.computeClaimCost(1),
                "A single-chunk claim must cost exactly 10 Resource Rupees");
    }

    @Test
    void computeClaimCost_NeverDropsBelowOne() {
        // At large N the geometric taper would naturally reach 0; max(1, ...) keeps it at 1.
        // Test the invariant: total cost for 200 chunks is positive, and the marginal cost
        // from chunk 199 to chunk 200 is at least 1 (the floor is held).
        int cost200 = ClaimSubcommand.computeClaimCost(200);
        int cost199 = ClaimSubcommand.computeClaimCost(199);

        assertTrue(cost200 > 0,
                "computeClaimCost(200) must be positive (max(1,...) floor applies)");
        assertTrue(cost200 - cost199 >= 1,
                "The marginal cost of the 200th chunk must be >= 1 (floor invariant)");
    }

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
}
