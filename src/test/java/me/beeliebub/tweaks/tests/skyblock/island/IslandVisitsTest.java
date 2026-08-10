package me.beeliebub.tweaks.tests.skyblock.island;

import me.beeliebub.tweaks.skyblock.island.IslandVisits;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandVisitsTest {

    @Test
    void changingVisitRemovesThePreviousReverseIndex() {
        IslandVisits visits = new IslandVisits();
        UUID visitor = UUID.randomUUID();

        assertTrue(visits.visit(visitor, "a"));
        assertTrue(visits.visit(visitor, "b"));
        assertEquals(Set.of(), visits.visitorsOf("a"));
        assertEquals(Set.of(visitor), visits.visitorsOf("b"));
        assertEquals("b", visits.visitedIsland(visitor).orElseThrow());
    }

    @Test
    void repeatingVisitIsIdempotentAndReturnedVisitorsAreReadOnlySnapshots() {
        IslandVisits visits = new IslandVisits();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(visits.visit(first, "island"));
        assertFalse(visits.visit(first, "island"));
        Set<UUID> snapshot = visits.visitorsOf("island");
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(second));

        visits.visit(second, "island");
        assertEquals(Set.of(first), snapshot);
        assertEquals(Set.of(first, second), visits.visitorsOf("island"));
        assertEquals(2, visits.size());
    }

    @Test
    void quitAndIslandRemovalClearBothDirections() {
        IslandVisits visits = new IslandVisits();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        visits.visit(first, "island");
        visits.visit(second, "island");

        Set<UUID> removed = visits.clearIsland("island");
        assertEquals(Set.of(first, second), removed);
        assertFalse(visits.visitedIsland(first).isPresent());
        assertEquals(Set.of(), visits.visitorsOf("island"));
    }

    @Test
    void clearQuitAndWorldChangeRemoveForwardAndReverseEntries() {
        IslandVisits visits = new IslandVisits();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();

        visits.visit(first, "first");
        assertEquals("first", visits.clear(first).orElseThrow());
        assertTrue(visits.visitedIsland(first).isEmpty());
        assertTrue(visits.visitorsOf("first").isEmpty());
        assertTrue(visits.clear(first).isEmpty());

        visits.visit(second, "second");
        visits.onQuit(second);
        assertTrue(visits.visitedIsland(second).isEmpty());
        assertTrue(visits.visitorsOf("second").isEmpty());

        visits.visit(third, "third");
        visits.onWorldChange(third);
        assertTrue(visits.visitedIsland(third).isEmpty());
        assertTrue(visits.visitorsOf("third").isEmpty());
        assertEquals(0, visits.size());
    }
}
