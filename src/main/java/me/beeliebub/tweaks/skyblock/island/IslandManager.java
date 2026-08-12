package me.beeliebub.tweaks.skyblock.island;

import me.beeliebub.tweaks.skyblock.SkyblockConfig;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread mutator and concurrent read cache for Skyblock islands. */
public final class IslandManager {

    private final SkyblockConfig config;
    private final IslandGrid grid;
    private final IslandStore store;
    private final Map<String, Island> byId = new ConcurrentHashMap<>();
    private final Map<UUID, String> byMember = new ConcurrentHashMap<>();
    private final Map<Integer, String> bySlot = new ConcurrentHashMap<>();
    private final Set<Integer> blockedSlots = ConcurrentHashMap.newKeySet();
    private final Set<Integer> pendingSlots = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingOwners = ConcurrentHashMap.newKeySet();

    public IslandManager(SkyblockConfig config, IslandGrid grid, IslandStore store) {
        this.config = config;
        this.grid = grid;
        this.store = store;
        for (Island island : store.loadAll()) {
            byId.put(island.id(), island);
            try {
                index(island);
            } catch (RuntimeException failure) {
                byId.remove(island.id(), island);
                throw failure;
            }
        }
    }

    public List<Island> all() {
        return byId.values().stream()
                .sorted(Comparator.comparing(Island::id))
                .toList();
    }

    public Optional<Island> byId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public Optional<Island> forPlayer(UUID player) {
        String id = byMember.get(player);
        return id == null ? Optional.empty() : byId(id);
    }

    public Optional<Island> islandAt(Location location) {
        if (location == null || location.getWorld() == null
                || !config.worldKey().equalsIgnoreCase(location.getWorld().getKey().asString())) {
            return Optional.empty();
        }
        final int slot;
        try {
            slot = grid.slotContaining(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        if (slot < 0) return Optional.empty();
        return islandAt(location.getWorld(), slot, location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public Optional<Island> islandAt(World world, int chunkX, int chunkZ) {
        if (world == null || !config.worldKey().equalsIgnoreCase(world.getKey().asString())) return Optional.empty();
        final int slot;
        try {
            slot = grid.slotContaining(chunkX, chunkZ);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        if (slot < 0) return Optional.empty();
        return islandAt(world, slot, chunkX, chunkZ);
    }

    private Optional<Island> islandAt(World world, int slot, int chunkX, int chunkZ) {
        if (blockedSlots.contains(slot)) return Optional.empty();
        String id = bySlot.get(slot);
        Island island = id == null ? null : byId.get(id);
        if (island == null || !grid.isWithinFootprint(slot, island.size(), chunkX, chunkZ)) {
            return Optional.empty();
        }
        return Optional.of(island);
    }

    public Optional<Island> createIsland(UUID owner) {
        Optional<Island> pending = createPendingIsland(owner);
        if (pending.isEmpty()) return pending;
        try {
            publishCreated(pending.get());
            return pending;
        } catch (RuntimeException failure) {
            discardPending(pending.get());
            throw failure;
        }
    }

    public Optional<Island> createPendingIsland(UUID owner) {
        if (owner == null || forPlayer(owner).isPresent() || pendingOwners.contains(owner)) return Optional.empty();
        int slot = nextFreeSlot();
        Instant now = Instant.now();
        Island island = new Island(Island.newId(), owner, List.of(), slot, IslandSize.SMALL,
                "default", "normal", false, "Island", new Island.SpawnOffset(0, 0, 0),
                "default", Map.of(), 0, now, now, Map.of(), Set.of(), Set.of());
        pendingSlots.add(slot);
        pendingOwners.add(owner);
        return Optional.of(island);
    }

    public void publishCreated(Island island) {
        if (island == null || !pendingSlots.remove(island.slotIndex()) || !pendingOwners.remove(island.owner())) {
            throw new IllegalStateException("Island is not a pending creation");
        }
        try {
            publish(island);
        } catch (RuntimeException failure) {
            pendingSlots.add(island.slotIndex());
            pendingOwners.add(island.owner());
            throw failure;
        }
    }

    public void discardPending(Island island) {
        if (island == null) return;
        pendingSlots.remove(island.slotIndex());
        pendingOwners.remove(island.owner());
    }

    public int nextFreeSlot() {
        for (int index = 0; index <= grid.maxIndex(); index++) {
            if (!grid.isSpawnExcluded(index) && !bySlot.containsKey(index)
                    && !blockedSlots.contains(index) && !pendingSlots.contains(index)) return index;
        }
        throw new IllegalStateException("No Skyblock island slots remain below skyblock.max-slot-index");
    }

    public Island update(Island island) {
        Island existing = byId.get(island.id());
        if (existing == null) throw new IllegalArgumentException("Unknown island: " + island.id());
        unindex(existing);
        try {
            publish(island);
            return island;
        } catch (RuntimeException failure) {
            byId.remove(island.id(), island);
            unindex(island);
            byId.put(existing.id(), existing);
            index(existing);
            throw failure;
        }
    }

    public boolean remove(String id) {
        Island removed = byId.remove(id);
        if (removed == null) return false;
        unindex(removed);
        store.discardDirty(id);
        store.deleteAsync(id);
        return true;
    }

    public boolean addMember(String id, UUID player) {
        Island island = byId.get(id);
        if (island == null || player == null || island.isMember(player)
                || island.memberCount() >= config.maxMembers() || forPlayer(player).isPresent()) return false;
        Set<UUID> members = ConcurrentHashMap.newKeySet();
        members.addAll(island.members());
        members.add(player);
        update(island.withMembers(members));
        return true;
    }

    public boolean removeMember(String id, UUID player) {
        Island island = byId.get(id);
        if (island == null || player == null || !island.members().contains(player)) return false;
        Set<UUID> members = ConcurrentHashMap.newKeySet();
        members.addAll(island.members());
        members.remove(player);
        update(island.withMembers(members));
        return true;
    }

    public void blockSlot(int slot) {
        blockedSlots.add(slot);
    }

    public void unblockSlot(int slot) {
        blockedSlots.remove(slot);
    }

    public boolean isSlotBlocked(int slot) {
        return blockedSlots.contains(slot);
    }

    public Set<Integer> blockedSlots() {
        return Set.copyOf(blockedSlots);
    }

    public int slotFor(Location location) {
        if (location == null) return -1;
        try {
            return grid.slotContaining(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        } catch (IllegalArgumentException ignored) {
            return -1;
        }
    }

    public Location spawnLocation(Island island, World world) {
        if (island == null || world == null) return null;
        IslandGrid.Slot slot = grid.slotForIndex(island.slotIndex());
        Island.SpawnOffset offset = island.spawnOffset();
        return new Location(world, slot.centerChunkX() * 16 + 8 + offset.x(),
                config.islandY() + offset.y(), slot.centerChunkZ() * 16 + 8 + offset.z());
    }

    public IslandGrid grid() {
        return grid;
    }

    public SkyblockConfig config() {
        return config;
    }

    public void markActive(Island island) {
        if (island != null && byId.containsKey(island.id())) update(island.withLastActive(Instant.now()));
    }

    public void flush() {
        store.flush(all());
    }

    private void publish(Island island) {
        ensureIndexAvailable(island, null);
        byId.put(island.id(), island);
        try {
            index(island);
            store.markDirty(island.id(), island);
        } catch (RuntimeException failure) {
            byId.remove(island.id(), island);
            unindex(island);
            throw failure;
        }
    }

    private void index(Island island) {
        String previousSlot = bySlot.putIfAbsent(island.slotIndex(), island.id());
        if (previousSlot != null && !previousSlot.equals(island.id())) {
            throw new IllegalStateException("Skyblock slot " + island.slotIndex()
                    + " is already indexed by " + previousSlot);
        }
        indexMember(island.owner(), island.id());
        for (UUID member : island.members()) indexMember(member, island.id());
    }

    private void indexMember(UUID member, String islandId) {
        String previous = byMember.putIfAbsent(member, islandId);
        if (previous != null && !previous.equals(islandId)) {
            throw new IllegalStateException("Skyblock member " + member + " is already indexed by " + previous);
        }
    }

    private void ensureIndexAvailable(Island island, Island replacing) {
        String existingSlot = bySlot.get(island.slotIndex());
        if (existingSlot != null && !existingSlot.equals(island.id())
                && (replacing == null || !existingSlot.equals(replacing.id()))) {
            throw new IllegalStateException("Skyblock slot " + island.slotIndex() + " is already occupied");
        }
        for (UUID member : island.memberSet()) {
            String existing = byMember.get(member);
            if (existing != null && !existing.equals(island.id())
                    && (replacing == null || !existing.equals(replacing.id()))) {
                throw new IllegalStateException("Skyblock member " + member + " belongs to " + existing);
            }
        }
    }

    private void unindex(Island island) {
        bySlot.remove(island.slotIndex(), island.id());
        byMember.remove(island.owner(), island.id());
        for (UUID member : island.members()) byMember.remove(member, island.id());
    }
}
