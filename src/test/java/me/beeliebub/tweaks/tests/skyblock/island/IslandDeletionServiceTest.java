package me.beeliebub.tweaks.tests.skyblock.island;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandDeletionService;
import me.beeliebub.tweaks.skyblock.island.IslandGrid;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class IslandDeletionServiceTest {

    @Test
    void ordersEjectionUnclaimSweepCompletionDeleteAndSlotRelease() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        Location spawn = new Location(world, 0.5, 65, 0.5);
        List<String> events = new ArrayList<>();
        InMemoryProgressStore progress = new InMemoryProgressStore(events);
        RecordingSlots slots = new RecordingSlots(events);
        IslandDeletionService service = new IslandDeletionService(
                world, new IslandGrid(), 2, progress,
                new IslandDeletionService.PlayerEjector() {
                    @Override
                    public Collection<Player> playersInside(World ignored, IslandGrid.ChunkBounds bounds) {
                        return List.of(player);
                    }

                    @Override
                    public boolean eject(Player ignored, Location destination) {
                        events.add("eject");
                        return true;
                    }
                },
                ignored -> spawn,
                ignored -> {
                    events.add("unclaim");
                    return true;
                },
                (ignored, chunkX, chunkZ) -> {
                    events.add("sweep");
                    return true;
                },
                slots,
                ignored -> events.add("complete"));

        Island island = Island.create(UUID.randomUUID(), 1, IslandSize.SMALL);
        assertEquals(IslandDeletionService.BeginResult.STARTED, service.begin(island));
        assertEquals(List.of("reserve", "eject", "unclaim"), events);
        assertTrue(service.isDeleting(island.id()));

        assertEquals(2, service.tick());
        assertFalse(events.contains("release"));
        assertEquals(2, progress.current(island.id()).nextChunkIndex());

        int ticks = 0;
        while (service.isDeleting(island.id())) {
            assertTrue(ticks++ < 200, "deletion should finish within the expected sweep budget");
            service.tick();
        }

        assertEquals(361, events.stream().filter(value -> value.startsWith("sweep")).count());
        assertEquals(List.of("reserve", "eject", "unclaim"), events.subList(0, 3));
        assertTrue(indexOfEvent(events, "eject") < indexOfEvent(events, "unclaim"));
        assertTrue(indexOfEvent(events, "unclaim") < indexOfEvent(events, "sweep"));
        assertTrue(indexOfEvent(events, "sweep") < indexOfEvent(events, "complete"));
        assertTrue(indexOfEvent(events, "complete") < indexOfEvent(events, "delete"));
        assertTrue(indexOfEvent(events, "delete") < indexOfEvent(events, "release"));
        assertFalse(progress.records.containsKey(island.id()));
        assertFalse(service.isSlotDeleting(island.slotIndex()));
        assertTrue(slots.reserved.isEmpty());
    }

    @Test
    void resumesPersistedCursorWithoutRepeatingPreparationAndReleasesSlot() {
        World world = mock(World.class);
        IslandGrid grid = new IslandGrid(35, 10);
        List<String> events = new ArrayList<>();
        InMemoryProgressStore progress = new InMemoryProgressStore(events);
        RecordingSlots slots = new RecordingSlots(events);
        Island island = Island.create(UUID.randomUUID(), 1, IslandSize.SMALL);
        IslandGrid.ChunkBounds bounds = grid.chunkBoundsFor(island.slotIndex(), IslandSize.LARGE);
        IslandDeletionService.DeletionProgress persisted = new IslandDeletionService.DeletionProgress(
                island.id(), island.slotIndex(), bounds, bounds.chunkCount() - 1,
                IslandDeletionService.Stage.SWEEPING);
        progress.seed(persisted);

        IslandDeletionService service = new IslandDeletionService(
                world, grid, 1, progress,
                new IslandDeletionService.PlayerEjector() {
                    @Override
                    public Collection<Player> playersInside(World ignored, IslandGrid.ChunkBounds value) {
                        throw new AssertionError("resuming a sweep must not eject players");
                    }

                    @Override
                    public boolean eject(Player player, Location destination) {
                        throw new AssertionError("resuming a sweep must not eject players");
                    }
                },
                ignored -> {
                    throw new AssertionError("resuming a sweep must not resolve spawn");
                },
                ignored -> {
                    throw new AssertionError("resuming a sweep must not unclaim again");
                },
                (ignored, chunkX, chunkZ) -> {
                    events.add("sweep:" + chunkX + "," + chunkZ);
                    return true;
                },
                slots,
                completed -> {
                    assertEquals(IslandDeletionService.Stage.COMPLETE, completed.stage());
                    events.add("complete");
                });

        service.resumePending();

        assertEquals(IslandDeletionService.BeginResult.SLOT_BUSY,
                service.begin(Island.create(UUID.randomUUID(), island.slotIndex(), IslandSize.SMALL)));
        assertEquals(persisted, service.progressFor(island.id()).orElseThrow());
        assertEquals(1, service.tick(1));

        assertEquals(List.of("reserve", "sweep:44,9", "complete", "delete", "release"), events);
        assertFalse(service.isDeleting(island.id()));
        assertFalse(service.isSlotDeleting(island.slotIndex()));
        assertTrue(progress.records.isEmpty());
        assertTrue(slots.reserved.isEmpty());
    }

    private static final class InMemoryProgressStore implements IslandDeletionService.ProgressStore {
        private final Map<String, IslandDeletionService.DeletionProgress> records = new LinkedHashMap<>();
        private final List<String> events;

        private InMemoryProgressStore(List<String> events) {
            this.events = events;
        }

        @Override
        public Collection<IslandDeletionService.DeletionProgress> load() {
            return List.copyOf(records.values());
        }

        @Override
        public boolean save(IslandDeletionService.DeletionProgress value) {
            records.put(value.islandId(), value);
            return true;
        }

        @Override
        public boolean delete(String islandId) {
            events.add("delete");
            return records.remove(islandId) != null;
        }

        private IslandDeletionService.DeletionProgress current(String islandId) {
            return records.get(islandId);
        }

        private void seed(IslandDeletionService.DeletionProgress progress) {
            records.put(progress.islandId(), progress);
        }
    }

    private static final class RecordingSlots implements IslandDeletionService.SlotRegistry {
        private final List<String> events;
        private final Set<Integer> reserved = new HashSet<>();

        private RecordingSlots(List<String> events) {
            this.events = events;
        }

        @Override
        public void reserve(int slotIndex) {
            if (!reserved.add(slotIndex)) {
                throw new AssertionError("slot was reserved twice: " + slotIndex);
            }
            events.add("reserve");
        }

        @Override
        public void release(int slotIndex) {
            if (!reserved.remove(slotIndex)) {
                throw new AssertionError("slot was released without a reservation: " + slotIndex);
            }
            events.add("release");
        }
    }

    private static int indexOfEvent(List<String> events, String prefix) {
        for (int index = 0; index < events.size(); index++) {
            if (events.get(index).startsWith(prefix)) {
                return index;
            }
        }
        throw new AssertionError("Missing event: " + prefix);
    }
}
