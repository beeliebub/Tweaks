package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.FlagTarget;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class EntryFlagDefaultTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static Location location() {
        return new Location(mock(World.class), 0, 64, 0);
    }

    @Test
    void protectedRegionWithoutEntryRuleAllowsEntry() {
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        Region protectedRegion = new Region("plot", OWNER, List.of(), Map.of());

        assertTrue(protection.isAllowed(List.of(protectedRegion), location(), PLAYER, RegionFlag.ENTRY));
    }

    @Test
    void unrelatedConfiguredFlagDoesNotRestrictEntry() {
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        Region protectedRegion = new Region(
                "plot", OWNER, List.of(), Map.of(RegionFlag.PVP, Map.of(FlagTarget.DEFAULT, false)));

        assertTrue(protection.isAllowed(List.of(protectedRegion), location(), PLAYER, RegionFlag.ENTRY));
    }

    @Test
    void explicitEntryRuleStillRestrictsEntry() {
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        Region restricted = new Region(
                "plot", OWNER, List.of(), Map.of(RegionFlag.ENTRY, Map.of(FlagTarget.DEFAULT, false)));

        assertFalse(protection.isAllowed(List.of(restricted), location(), PLAYER, RegionFlag.ENTRY));
    }

    @Test
    void explicitEntryAllowRulePermitsEntry() {
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        Region permitted = new Region(
                "plot", OWNER, List.of(), Map.of(RegionFlag.ENTRY, Map.of(FlagTarget.DEFAULT, true)));

        assertTrue(protection.isAllowed(List.of(permitted), location(), PLAYER, RegionFlag.ENTRY));
    }

    @Test
    void nonmatchingTargetedEntryRuleDoesNotRestrictEntry() {
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        Region ownerOnly = new Region(
                "plot", OWNER, List.of(), Map.of(RegionFlag.ENTRY, Map.of(FlagTarget.OWNER, false)));

        assertTrue(protection.isAllowed(List.of(ownerOnly), location(), PLAYER, RegionFlag.ENTRY));
    }

    @Test
    void parentEntryDenyIsInheritedBySilentChild() {
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        Region parent = new Region(
                "parent", OWNER, List.of(), Map.of(RegionFlag.ENTRY, Map.of(FlagTarget.DEFAULT, false)));
        Region child = new Region("child", OWNER, List.of(), Map.of(), "parent");
        protection.regions().put("parent", parent);
        protection.regions().put("child", child);

        assertFalse(protection.isAllowed(List.of(parent, child), location(), PLAYER, RegionFlag.ENTRY));
    }

    @Test
    void childEntryAllowOverridesParentDeny() {
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        Region parent = new Region(
                "parent", OWNER, List.of(), Map.of(RegionFlag.ENTRY, Map.of(FlagTarget.DEFAULT, false)));
        Region child = new Region(
                "child", OWNER, List.of(), Map.of(RegionFlag.ENTRY, Map.of(FlagTarget.DEFAULT, true)), "parent");
        protection.regions().put("parent", parent);
        protection.regions().put("child", child);

        assertTrue(protection.isAllowed(List.of(parent, child), location(), PLAYER, RegionFlag.ENTRY));
    }

    @Test
    void nonmatchingChildEntryRuleFallsThroughToParent() {
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        Region parent = new Region(
                "parent", OWNER, List.of(), Map.of(RegionFlag.ENTRY, Map.of(FlagTarget.DEFAULT, false)));
        Region child = new Region(
                "child", OWNER, List.of(), Map.of(RegionFlag.ENTRY, Map.of(FlagTarget.OWNER, true)), "parent");
        protection.regions().put("parent", parent);
        protection.regions().put("child", child);

        assertFalse(protection.isAllowed(List.of(parent, child), location(), PLAYER, RegionFlag.ENTRY));
    }
}
