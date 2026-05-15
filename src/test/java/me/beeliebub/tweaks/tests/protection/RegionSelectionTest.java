package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.protection.RegionSelection;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RegionSelectionTest {

    @Test
    void freshSelectionHasNeitherPos() {
        RegionSelection sel = new RegionSelection(mock(World.class));
        assertFalse(sel.hasPos1());
        assertFalse(sel.hasPos2());
        assertFalse(sel.isComplete());
        assertNull(sel.pos1());
        assertNull(sel.pos2());
    }

    @Test
    void pos1AloneIsPartial() {
        RegionSelection sel = new RegionSelection(mock(World.class));
        sel.setPos1(42L);
        assertTrue(sel.hasPos1());
        assertFalse(sel.hasPos2());
        assertFalse(sel.isComplete());
        assertEquals(42L, sel.pos1());
    }

    @Test
    void bothPositionsMakesItComplete() {
        RegionSelection sel = new RegionSelection(mock(World.class));
        sel.setPos1(1L);
        sel.setPos2(2L);
        assertTrue(sel.isComplete());
        assertEquals(1L, sel.pos1());
        assertEquals(2L, sel.pos2());
    }

    @Test
    void positionsCanBeOverwritten() {
        RegionSelection sel = new RegionSelection(mock(World.class));
        sel.setPos1(1L);
        sel.setPos1(99L);
        assertEquals(99L, sel.pos1());
    }

    @Test
    void worldIsImmutable() {
        World world = mock(World.class);
        RegionSelection sel = new RegionSelection(world);
        assertSame(world, sel.world());
    }
}
