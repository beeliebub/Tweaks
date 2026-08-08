package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class CrossOwnerAuditTest {

    private static final UUID OWNER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static Region region(String id, UUID owner, String world, String parent) {
        return new Region(id, owner, List.of(), Map.of(), Map.of(), parent,
                null, world);
    }

    @Test
    void reportsOnlyGenuineSameWorldCrossOwnerPairsWithoutMutation() {
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        Region mixedParent = region("mixed-parent", OWNER_A, "world", null);
        Region mixedChild = region("mixed-child", OWNER_B, "world", "mixed-parent");
        Region sameParent = region("same-parent", OWNER_A, "world", null);
        Region sameChild = region("same-child", OWNER_A, "world", "same-parent");
        Region unrelatedOtherWorld = region("mixed-child", OWNER_B, "world_nether", null);
        for (Region region : List.of(mixedParent, mixedChild, sameParent, sameChild,
                unrelatedOtherWorld)) {
            protection.regions().put(ProtectionManager.keyOf(region), region);
        }
        Map<String, Region> before = Map.copyOf(protection.regions());

        assertEquals(List.of("mixed-child -> mixed-parent"),
                protection.auditCrossOwnerHierarchies());
        assertEquals(before, protection.regions());
    }
}
