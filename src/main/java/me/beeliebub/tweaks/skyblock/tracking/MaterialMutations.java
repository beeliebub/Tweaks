package me.beeliebub.tweaks.skyblock.tracking;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Explicit vanilla drop preimage table used to bound chunk-taint writes. */
public final class MaterialMutations {
    private static final Map<Material, Set<Material>> PREIMAGES = new EnumMap<>(Material.class);

    static {
        add(Material.COBBLESTONE, Material.STONE);
        add(Material.COBBLED_DEEPSLATE, Material.DEEPSLATE);
        add(Material.DIRT, Material.GRASS_BLOCK, Material.MYCELIUM, Material.PODZOL);
        add(Material.CRACKED_STONE_BRICKS, Material.STONE_BRICKS);
        add(Material.RAW_IRON, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE);
        add(Material.RAW_COPPER, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE);
        add(Material.RAW_GOLD, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE);
        add(Material.REDSTONE, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE);
        add(Material.LAPIS_LAZULI, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);
        add(Material.DIAMOND, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);
        add(Material.EMERALD, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);
        add(Material.NETHERITE_SCRAP, Material.ANCIENT_DEBRIS);
    }

    private MaterialMutations() {
    }

    private static void add(Material result, Material... sources) {
        PREIMAGES.computeIfAbsent(result, ignored -> EnumSet.noneOf(Material.class)).addAll(Set.of(sources));
    }

    public static Set<Material> preimage(Material result) {
        if (result == null) return Set.of();
        EnumSet<Material> values = EnumSet.of(result);
        values.addAll(PREIMAGES.getOrDefault(result, Set.of()));
        return Set.copyOf(values);
    }

    public static Set<Material> taintRelevant(Set<Material> challengeMaterials) {
        EnumSet<Material> result = EnumSet.noneOf(Material.class);
        if (challengeMaterials != null) {
            for (Material material : challengeMaterials) result.addAll(preimage(material));
        }
        return Set.copyOf(result);
    }

    public static Map<Material, Set<Material>> table() {
        return Map.copyOf(PREIMAGES);
    }
}
