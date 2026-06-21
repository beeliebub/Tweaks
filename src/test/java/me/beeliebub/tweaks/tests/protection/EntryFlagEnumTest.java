package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.protection.RegionFlag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests that the ENTRY flag exists in the RegionFlag enum and is correctly
// classified as neither a material flag nor an entity flag.
//
// These tests are pure-Java enum inspections and require no Bukkit server or
// MockBukkit, so they run in any environment.
class EntryFlagEnumTest {

    @Test
    void entryFlagExistsInEnum() {
        RegionFlag entry = RegionFlag.ENTRY;
        assertNotNull(entry, "ENTRY must be a declared constant in RegionFlag");
    }

    @Test
    void entryFlagIsNotMaterialFlag() {
        assertFalse(RegionFlag.ENTRY.isMaterialFlag(),
                "ENTRY is a boolean flag and must NOT be classified as a material flag");
    }

    @Test
    void entryFlagIsNotEntityFlag() {
        assertFalse(RegionFlag.ENTRY.isEntityFlag(),
                "ENTRY is a boolean flag and must NOT be classified as an entity flag");
    }

    @Test
    void entryFlagNameMatchesConvention() {
        assertEquals("ENTRY", RegionFlag.ENTRY.name());
    }

    // Sanity check: INVINCIBILITY (the flag before ENTRY in the enum) should also
    // not be classified as material or entity, so our test logic is coherent.
    @Test
    void invincibilityFlagIsAlsoBoolean() {
        assertFalse(RegionFlag.INVINCIBILITY.isMaterialFlag());
        assertFalse(RegionFlag.INVINCIBILITY.isEntityFlag());
    }
}
