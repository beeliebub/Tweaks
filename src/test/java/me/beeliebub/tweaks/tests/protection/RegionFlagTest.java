package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.protection.RegionFlag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Pin-down of the RegionFlag enum surface so that adding/removing a flag is
// always a deliberate, observable change. The enum doubles as the wire format
// (YAML keys) and the listener routing discriminator, so silent reorderings
// or renames break user data — these tests force a test failure first.
class RegionFlagTest {

    private static final Set<RegionFlag> BOOLEAN_FLAGS = EnumSet.of(
            RegionFlag.BLOCK_BREAK,
            RegionFlag.BLOCK_PLACE,
            RegionFlag.CONTAINER_ACCESS,
            RegionFlag.INTERACT,
            RegionFlag.REDSTONE,
            RegionFlag.EXPLOSION,
            RegionFlag.PVP,
            RegionFlag.MOB_GRIEFING
    );

    private static final Set<RegionFlag> MATERIAL_FLAGS = EnumSet.of(
            RegionFlag.ALLOW_BLOCK_BREAK,
            RegionFlag.DENY_BLOCK_BREAK,
            RegionFlag.ALLOW_BLOCK_PLACE,
            RegionFlag.DENY_BLOCK_PLACE
    );

    @Test
    void everyFlagIsClassifiedExactlyOnceAsBooleanOrMaterial() {
        for (RegionFlag f : RegionFlag.values()) {
            boolean asBoolean = BOOLEAN_FLAGS.contains(f);
            boolean asMaterial = MATERIAL_FLAGS.contains(f);
            assertNotEquals(asBoolean, asMaterial,
                    "Flag " + f + " must be in exactly one of the two classifier sets — "
                            + "did a new flag get added without updating RegionFlagTest?");
        }
    }

    @Test
    void enumValuesEqualsClassifierUnion() {
        Set<RegionFlag> union = EnumSet.copyOf(BOOLEAN_FLAGS);
        union.addAll(MATERIAL_FLAGS);
        assertEquals(union, EnumSet.allOf(RegionFlag.class),
                "RegionFlag enum has values not categorized by this test. Add them to BOOLEAN_FLAGS or MATERIAL_FLAGS.");
    }

    @Test
    void isMaterialFlagAgreesWithExpectedClassification() {
        for (RegionFlag f : BOOLEAN_FLAGS) {
            assertFalse(f.isMaterialFlag(), f + " should be a boolean flag");
        }
        for (RegionFlag f : MATERIAL_FLAGS) {
            assertTrue(f.isMaterialFlag(), f + " should be a material-list flag");
        }
    }

    @Test
    void valueOfRoundTripsEveryName() {
        for (RegionFlag f : RegionFlag.values()) {
            assertSame(f, RegionFlag.valueOf(f.name()));
        }
    }
}
