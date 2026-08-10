package me.beeliebub.tweaks.tests.skyblock.generator;

import me.beeliebub.tweaks.skyblock.generator.GeneratorRegistry;
import me.beeliebub.tweaks.skyblock.generator.GeneratorTier;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratorRegistryTest {
    @Test
    void weightedTierRetainsConfiguredOutputs() {
        GeneratorTier tier = new GeneratorTier("ore", "Ore", Map.of(Material.COBBLESTONE, 3.0, Material.IRON_ORE, 1.0));
        assertEquals(4.0, tier.totalWeight());
    }

    @Test
    void rejectsFiniteWeightsWhoseTotalOverflows() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratorTier("overflow", "Overflow",
                Map.of(Material.STONE, Double.MAX_VALUE, Material.DIRT, Double.MAX_VALUE)));
    }

    @Test
    void malformedEntriesFallBackToDefaultTier() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tiers", List.of(Map.of("id", "broken", "outputs", Map.of("not_a_material", 1))));
        GeneratorRegistry registry = new GeneratorRegistry(Logger.getAnonymousLogger());
        registry.load(yaml);
        assertEquals(Material.COBBLESTONE, registry.tier("default").orElseThrow().outputs().keySet().iterator().next());
    }
}
