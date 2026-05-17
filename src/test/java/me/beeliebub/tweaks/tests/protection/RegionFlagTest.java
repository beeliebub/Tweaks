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
            RegionFlag.MOB_GRIEFING,
            RegionFlag.MOB_SPAWNING,
            RegionFlag.INVINCIBILITY
    );

    private static final Set<RegionFlag> MATERIAL_FLAGS = EnumSet.of(
            RegionFlag.ALLOW_BLOCK_BREAK,
            RegionFlag.DENY_BLOCK_BREAK,
            RegionFlag.ALLOW_BLOCK_PLACE,
            RegionFlag.DENY_BLOCK_PLACE
    );

    private static final Set<RegionFlag> ENTITY_FLAGS = EnumSet.of(
            RegionFlag.ALLOW_MOB_SPAWN,
            RegionFlag.DENY_MOB_SPAWN
    );

    @Test
    void everyFlagIsClassifiedExactlyOnce() {
        for (RegionFlag f : RegionFlag.values()) {
            int hits = (BOOLEAN_FLAGS.contains(f) ? 1 : 0)
                    + (MATERIAL_FLAGS.contains(f) ? 1 : 0)
                    + (ENTITY_FLAGS.contains(f) ? 1 : 0);
            assertEquals(1, hits,
                    "Flag " + f + " must be in exactly one classifier set — "
                            + "did a new flag get added without updating RegionFlagTest?");
        }
    }

    @Test
    void enumValuesEqualsClassifierUnion() {
        Set<RegionFlag> union = EnumSet.copyOf(BOOLEAN_FLAGS);
        union.addAll(MATERIAL_FLAGS);
        union.addAll(ENTITY_FLAGS);
        assertEquals(union, EnumSet.allOf(RegionFlag.class),
                "RegionFlag enum has values not categorized by this test. Add them to one of BOOLEAN_FLAGS, MATERIAL_FLAGS, or ENTITY_FLAGS.");
    }

    @Test
    void isMaterialFlagAgreesWithExpectedClassification() {
        for (RegionFlag f : BOOLEAN_FLAGS) {
            assertFalse(f.isMaterialFlag(), f + " should not be a material-list flag");
        }
        for (RegionFlag f : MATERIAL_FLAGS) {
            assertTrue(f.isMaterialFlag(), f + " should be a material-list flag");
        }
        for (RegionFlag f : ENTITY_FLAGS) {
            assertFalse(f.isMaterialFlag(), f + " should not be a material-list flag");
        }
    }

    @Test
    void isEntityFlagAgreesWithExpectedClassification() {
        for (RegionFlag f : BOOLEAN_FLAGS) {
            assertFalse(f.isEntityFlag(), f + " should not be an entity-list flag");
        }
        for (RegionFlag f : MATERIAL_FLAGS) {
            assertFalse(f.isEntityFlag(), f + " should not be an entity-list flag");
        }
        for (RegionFlag f : ENTITY_FLAGS) {
            assertTrue(f.isEntityFlag(), f + " should be an entity-list flag");
        }
    }

    @Test
    void valueOfRoundTripsEveryName() {
        for (RegionFlag f : RegionFlag.values()) {
            assertSame(f, RegionFlag.valueOf(f.name()));
        }
    }
}
