package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RegionUnclaimCascadeTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static Region region(String id, String world, String parent) {
        return new Region(id, OWNER, List.of(), Map.of(), Map.of(), parent,
                new Region.RegionBounds(0, 0, 4, 4), world);
    }

    @Test
    void unclaimDestroysDescendantsDeepestFirstAndPurgesState(@TempDir Path tmp) throws Exception {
        Tweaks plugin = mock(Tweaks.class);
        RegionWriter writer = new RegionWriter(plugin, tmp.toFile());
        ProtectionManager protection = new ProtectionManager(plugin);
        Region parent = region("parent", "world", null);
        Region child = region("child", "world", "parent");
        Region grandchild = region("grandchild", "world", "child");

        for (Region r : List.of(parent, child, grandchild)) {
            writer.writeNow(r);
            protection.regions().put(ProtectionManager.keyOf(r), r);
        }
        protection.setWriter(writer);
        for (long key : new long[] {1L, 2L, 3L}) {
            protection.pendingStamps().put(key,
                    ConcurrentHashMap.newKeySet());
        }
        protection.pendingStamps().get(1L).add("parent");
        protection.pendingStamps().get(2L).add("child");
        protection.pendingStamps().get(3L).add("grandchild");

        ProtectionManager.UnclaimOutcome outcome = protection.unclaim("parent");

        assertEquals(ProtectionManager.UnclaimResult.OK, outcome.result());
        assertEquals(List.of("grandchild", "child", "parent"),
                outcome.destroyed().stream().map(Region::id).toList());
        for (String id : List.of("parent", "child", "grandchild")) {
            assertTrue(protection.orphanedRegions().contains(id));
            assertFalse(protection.regions().containsKey("world:" + id));
            assertFalse(Files.exists(tmp.resolve("world/" + id + ".yml")));
        }
        assertTrue(protection.pendingStamps().isEmpty());
        try (var archived = Files.list(tmp.resolve("_deleted/world"))) {
            assertEquals(3, archived.filter(path -> path.getFileName().toString().endsWith(".yml")).count());
        }
    }

    @Test
    void sameNamedRegionInAnotherWorldSurvives(@TempDir Path tmp) throws Exception {
        Tweaks plugin = mock(Tweaks.class);
        RegionWriter writer = new RegionWriter(plugin, tmp.toFile());
        ProtectionManager protection = new ProtectionManager(plugin);
        Region target = region("parent", "world", null);
        Region otherWorld = region("child", "world_nether", null);
        Region descendant = region("child", "world", "parent");

        for (Region r : List.of(target, otherWorld, descendant)) {
            writer.writeNow(r);
            protection.regions().put(ProtectionManager.keyOf(r), r);
        }
        protection.setWriter(writer);

        ProtectionManager.UnclaimOutcome outcome = protection.unclaim("parent");

        assertEquals(List.of("child", "parent"), outcome.destroyed().stream().map(Region::id).toList());
        assertTrue(protection.regions().containsKey("world_nether:child"));
        assertTrue(Files.exists(tmp.resolve("world_nether/child.yml")));
        assertEquals(otherWorld, protection.regions().get("world_nether:child"),
                "the unrelated same-name region must remain the original cached object");
    }
}
