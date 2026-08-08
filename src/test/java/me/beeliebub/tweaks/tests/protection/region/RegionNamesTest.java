package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.protection.region.RegionNames;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegionNamesTest {

    @Test
    void normalizesBeforeValidation() {
        assertEquals("base_name", RegionNames.normalize("  Base_Name "));
        assertTrue(RegionNames.isValid(RegionNames.normalize("Base_Name")));
    }

    @Test
    void acceptsTheDocumentedAlphabetAndBounds() {
        assertFalse(RegionNames.isValid(""));
        assertTrue(RegionNames.isValid("a"));
        assertTrue(RegionNames.isValid("a".repeat(32)));
        assertFalse(RegionNames.isValid("a".repeat(33)));
        assertTrue(RegionNames.isValid("a-1_b"));
        assertFalse(RegionNames.isValid("a/b"));
        assertFalse(RegionNames.isValid("café"));
    }

    @Test
    void reservesGlobalAndArchiveNamespaces() {
        assertTrue(RegionNames.isReserved("__global__"));
        assertTrue(RegionNames.isReserved("_deleted"));
        assertTrue(RegionNames.isReserved("_legacy"));
        assertFalse(RegionNames.isReserved("home"));
    }
}
