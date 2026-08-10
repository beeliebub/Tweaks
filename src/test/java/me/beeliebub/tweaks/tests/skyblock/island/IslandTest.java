package me.beeliebub.tweaks.tests.skyblock.island;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandTest {

    @Test
    void generatedIdsAreDashlessLowercaseUuidHex() {
        UUID owner = UUID.randomUUID();
        Island island = Island.create(owner, 0, IslandSize.SMALL);

        assertEquals(32, island.id().length());
        assertTrue(Island.isValidId(island.id()));
        assertFalse(island.id().contains("-"));
    }

    @Test
    void idHelpersAcceptOnlyLowercaseUuidHex() {
        UUID value = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

        assertEquals("aaaaaaaaaaaa4aaa8aaaaaaaaaaaaaaa", Island.idFor(value));
        assertTrue(Island.isValidId("0123456789abcdef0123456789abcdef"));
        assertFalse(Island.isValidId("0123456789ABCDEF0123456789abcdef"));
        assertFalse(Island.isValidId("01234567-89ab-cdef-0123-456789abcdef"));
        assertFalse(Island.isValidId("0123456789abcdef0123456789abcde"));
        assertThrows(NullPointerException.class, () -> Island.idFor(null));
    }

    @Test
    void buildersPreservePreviousStateAndCopyMembers() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        List<UUID> sourceMembers = new ArrayList<>(List.of(owner, member, member));
        Island original = Island.create(owner, 2, IslandSize.SMALL);
        Island updated = original.withSize(IslandSize.MEDIUM).withPublic(true)
                .withMembers(sourceMembers);
        sourceMembers.add(outsider);

        assertEquals(IslandSize.SMALL, original.size());
        assertFalse(original.isPublic());
        assertNotSame(original, updated);
        assertEquals(IslandSize.MEDIUM, updated.size());
        assertTrue(updated.isPublic());
        assertTrue(updated.isMember(member));
        assertEquals(2, updated.memberCount());
        assertEquals(List.of(member), updated.members());
        assertThrows(UnsupportedOperationException.class,
                () -> updated.members().add(UUID.randomUUID()));
    }

    @Test
    void memberSetContainsOwnerAndIsReadOnly() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        Island island = Island.create(owner, 0, IslandSize.SMALL).withMembers(List.of(member));

        assertTrue(island.isMember(owner));
        assertTrue(island.isMember(member));
        assertFalse(island.isMember(outsider));
        assertFalse(island.isMember(null));
        assertEquals(Set.of(owner, member), island.memberSet());
        assertThrows(UnsupportedOperationException.class,
                () -> island.memberSet().add(outsider));

        Island reowned = island.withOwner(outsider);
        assertEquals(outsider, reowned.owner());
        assertTrue(reowned.isMember(outsider));
        assertFalse(reowned.isMember(owner));
        assertEquals(List.of(member), reowned.members());
    }

    @Test
    void buildersCopyNestedHomesCountersAndChallengeSets() {
        UUID owner = UUID.randomUUID();
        Island.Home home = new Island.Home("jass:skyblock", 1.5, 64, -2.5, 90, 15);
        Map<String, Island.Home> sourcePlayerHomes = new LinkedHashMap<>();
        sourcePlayerHomes.put("Home", home);
        Map<UUID, Map<String, Island.Home>> sourceHomes = new LinkedHashMap<>();
        sourceHomes.put(owner, sourcePlayerHomes);
        Map<String, Long> sourceCounters = new LinkedHashMap<>();
        sourceCounters.put("blocks", 4L);
        Set<String> completed = new java.util.LinkedHashSet<>(Set.of("starter"));
        Set<String> notified = new java.util.LinkedHashSet<>(Set.of("starter"));

        Island island = Island.create(owner, 3, IslandSize.SMALL)
                .withHomes(sourceHomes)
                .withCounters(sourceCounters)
                .withCompletedChallenges(completed)
                .withNotifiedChallenges(notified)
                .withPurchasedHomeSlots(2);

        sourcePlayerHomes.put("Other", home);
        sourceHomes.clear();
        sourceCounters.put("broken", 99L);
        completed.add("later");
        notified.add("later");

        assertEquals(home, island.homes().get(owner).get("home"));
        assertFalse(island.homes().get(owner).containsKey("Other"));
        assertEquals(Map.of("blocks", 4L), island.counters());
        assertEquals(Set.of("starter"), island.completedChallenges());
        assertEquals(Set.of("starter"), island.notifiedChallenges());
        assertEquals(5, island.homeLimit(3));

        assertThrows(UnsupportedOperationException.class,
                () -> island.homes().put(owner, Map.of()));
        assertThrows(UnsupportedOperationException.class,
                () -> island.homes().get(owner).put("other", home));
        assertThrows(UnsupportedOperationException.class,
                () -> island.counters().put("other", 1L));
        assertThrows(UnsupportedOperationException.class,
                () -> island.completedChallenges().add("other"));
    }

    @Test
    void homeValidationRejectsBlankWorldAndNames() {
        UUID owner = UUID.randomUUID();
        Island.Home home = new Island.Home("world", 0, 64, 0, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new Island.Home(" ", 0, 64, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Island.create(owner, 0, IslandSize.SMALL)
                        .withHomes(Map.of(owner, Map.of(" ", home))));
    }

    @Test
    void scalarBuildersRetainIdentityAndChangeOnlyTheirValue() {
        UUID owner = UUID.randomUUID();
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant active = Instant.parse("2026-01-02T00:00:00Z");
        Island original = Island.create(owner, 1, IslandSize.SMALL);
        Island updated = original.withTypeId("stone").withDifficultyId("hard")
                .withDisplayName("Builder").withSpawnOffset(new Island.SpawnOffset(1, 2, 3))
                .withGeneratorTierId("tier-2").withCreatedAt(created).withLastActive(active)
                .withSlotIndex(4).withSize(IslandSize.LARGE).withPublic(true);

        assertEquals(original.id(), updated.id());
        assertEquals("stone", updated.typeId());
        assertEquals("hard", updated.difficultyId());
        assertEquals("Builder", updated.displayName());
        assertEquals(new Island.SpawnOffset(1, 2, 3), updated.spawnOffset());
        assertEquals("tier-2", updated.generatorTierId());
        assertEquals(created, updated.createdAt());
        assertEquals(active, updated.lastActive());
        assertEquals(4, updated.slotIndex());
        assertEquals(IslandSize.LARGE, updated.size());
        assertTrue(updated.isPublic());
        assertEquals(1, original.slotIndex());
        assertEquals(IslandSize.SMALL, original.size());
        assertFalse(original.isPublic());
    }
}
