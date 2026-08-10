package me.beeliebub.tweaks.skyblock.tracking;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Set;

/** Lazy exact-key retirement of stale block taint, with a generation fast path. */
public final class TaintRetirement implements Listener {
    private final SkyblockPdc pdc;
    private final TrackingScope scope;
    private final IslandManager islands;

    public TaintRetirement(SkyblockPdc pdc, TrackingScope scope) {
        this(pdc, scope, null);
    }

    public TaintRetirement(SkyblockPdc pdc, TrackingScope scope, IslandManager islands) {
        this.pdc = Objects.requireNonNull(pdc, "pdc");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.islands = islands;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        retire(event.getChunk());
    }

    public void retire(Chunk chunk) {
        if (chunk == null || islands == null
                || !islands.config().worldKey().equalsIgnoreCase(chunk.getWorld().getKey().asString())) return;
        Island island = islands.islandAt(chunk.getWorld(), chunk.getX(), chunk.getZ()).orElse(null);
        if (island == null) return;
        int generation = generationFor(island);
        if (pdc.generation(chunk) == generation) return;
        Set<Material> live = scope.taintMaterials(island);
        for (Material material : Material.values()) {
            if (material.isAir() || live.contains(material)) continue;
            chunk.getPersistentDataContainer().remove(pdc.taintKey(material));
        }
        pdc.setGeneration(chunk, generation);
    }

    public int generationFor(Island island) {
        if (island == null) return scope.generation();
        return java.util.Objects.hash(scope.generation(), island.id(), island.typeId(),
                island.completedChallenges().stream().sorted().toList());
    }

    public boolean stripEncountered(ItemStackAdapter stack) {
        return stack.strip(pdc);
    }

    public boolean stripEncountered(ItemStack stack) {
        return pdc.stripCounted(stack);
    }

    public interface ItemStackAdapter {
        boolean strip(SkyblockPdc pdc);
    }
}
