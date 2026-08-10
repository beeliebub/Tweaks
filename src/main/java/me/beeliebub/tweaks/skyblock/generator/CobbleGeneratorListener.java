package me.beeliebub.tweaks.skyblock.generator;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

import java.util.Objects;
import java.util.Random;

/** Applies weighted cobblestone output only for non-default island generators. */
public final class CobbleGeneratorListener implements Listener {
    private final IslandManager islands;
    private final GeneratorRegistry registry;
    private final String worldKey;
    private final Random random;

    public CobbleGeneratorListener(IslandManager islands, GeneratorRegistry registry, String worldKey) {
        this(islands, registry, worldKey, new Random());
    }

    public CobbleGeneratorListener(IslandManager islands, GeneratorRegistry registry, String worldKey, Random random) {
        this.islands = Objects.requireNonNull(islands, "islands");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.worldKey = Objects.requireNonNull(worldKey, "worldKey");
        this.random = Objects.requireNonNull(random, "random");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        if (!worldKey.equals(world.getKey().asString())) return;
        if (block.getType() != Material.COBBLESTONE && event.getNewState().getType() != Material.COBBLESTONE) return;
        Island island = islands.islandAt(block.getLocation()).orElse(null);
        if (island == null || "default".equals(island.generatorTierId())) return;
        GeneratorTier tier = registry.tier(island.generatorTierId()).orElse(null);
        if (tier == null || tier.totalWeight() <= 0) return;
        double roll = random.nextDouble() * tier.totalWeight();
        for (var entry : tier.outputs().entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                event.getNewState().setType(entry.getKey());
                return;
            }
        }
    }
}
