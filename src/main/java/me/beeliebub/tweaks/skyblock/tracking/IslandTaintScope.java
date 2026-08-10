package me.beeliebub.tweaks.skyblock.tracking;

import me.beeliebub.tweaks.skyblock.island.Island;
import org.bukkit.Material;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Per-island cache of the live taint set and its generation. */
public final class IslandTaintScope {
    private final Function<Island, Set<Material>> calculator;
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    public IslandTaintScope(Function<Island, Set<Material>> calculator) {
        this.calculator = calculator;
    }

    public Snapshot recompute(Island island) {
        Set<Material> materials = calculator.apply(island);
        Snapshot previous = snapshots.get(island.id());
        int generation = previous == null ? 1 : previous.generation() + 1;
        Snapshot next = new Snapshot(Set.copyOf(materials), generation);
        snapshots.put(island.id(), next);
        return next;
    }

    public Snapshot get(Island island) {
        Snapshot existing = snapshots.get(island.id());
        return existing == null ? recompute(island) : existing;
    }

    public Snapshot get(String islandId) {
        return snapshots.getOrDefault(islandId, new Snapshot(Set.of(), 0));
    }

    public void remove(String islandId) {
        snapshots.remove(islandId);
    }

    public record Snapshot(Set<Material> materials, int generation) {
        public Snapshot {
            materials = Set.copyOf(materials);
        }

        public boolean contains(Material material) {
            return materials.contains(material);
        }
    }
}
