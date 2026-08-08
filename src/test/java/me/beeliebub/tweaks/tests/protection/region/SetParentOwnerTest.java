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

class SetParentOwnerTest {

    private static final UUID OWNER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static Region region(String id, UUID owner, Region.RegionBounds bounds) {
        return new Region(id, owner, List.of(), Map.of(), Map.of(), null, bounds, "world");
    }

    private static ProtectionManager manager(Region parent, Region child) {
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        protection.regions().put("world:" + parent.id(), parent);
        protection.regions().put("world:" + child.id(), child);
        return protection;
    }

    @Test
    void differingOwnersAreRejected() {
        ProtectionManager protection = manager(
                region("parent", OWNER_A, new Region.RegionBounds(0, 0, 4, 4)),
                region("child", OWNER_B, new Region.RegionBounds(1, 1, 2, 2)));

        assertEquals(ProtectionManager.SetParentResult.DIFFERENT_OWNER,
                protection.setParent("child", "parent"));
    }

    @Test
    void equalOwnersWithContainedBoundsAreAccepted() {
        ProtectionManager protection = manager(
                region("parent", OWNER_A, new Region.RegionBounds(0, 0, 4, 4)),
                region("child", OWNER_A, new Region.RegionBounds(1, 1, 2, 2)));

        assertEquals(ProtectionManager.SetParentResult.OK,
                protection.setParent("child", "parent"));
    }

    @Test
    void ownerMismatchWinsBeforeContainmentFailure() {
        ProtectionManager protection = manager(
                region("parent", OWNER_A, new Region.RegionBounds(0, 0, 1, 1)),
                region("child", OWNER_B, new Region.RegionBounds(3, 3, 4, 4)));

        assertEquals(ProtectionManager.SetParentResult.DIFFERENT_OWNER,
                protection.setParent("child", "parent"));
    }

    @Test
    void administrativeOverrideAllowsContainedCrossOwnerHierarchy() {
        ProtectionManager protection = manager(
                region("parent", OWNER_A, new Region.RegionBounds(0, 0, 4, 4)),
                region("child", OWNER_B, new Region.RegionBounds(1, 1, 2, 2)));

        assertEquals(ProtectionManager.SetParentResult.OK,
                protection.setParent("child", "parent", true));
    }

    @Test
    void twoArgumentFormRemainsStrict() {
        ProtectionManager protection = manager(
                region("parent", OWNER_A, new Region.RegionBounds(0, 0, 4, 4)),
                region("child", OWNER_B, new Region.RegionBounds(1, 1, 2, 2)));

        assertEquals(ProtectionManager.SetParentResult.DIFFERENT_OWNER,
                protection.setParent("child", "parent"));
    }
}
