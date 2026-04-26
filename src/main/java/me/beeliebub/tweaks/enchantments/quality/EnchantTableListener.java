package me.beeliebub.tweaks.enchantments.quality;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// Intercepts enchanting table results and rolls a 5% chance per applicable enchantment
// to upgrade it to a quality variant (uncommon/rare/epic/legendary).
public class EnchantTableListener implements Listener {

    private static final double QUALITY_CHANCE = 0.05;

    private final QualityRegistry registry;

    public EnchantTableListener(QualityRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        Map<Enchantment, Integer> enchantsToAdd = event.getEnchantsToAdd();
        List<Enchantment> toRemove = new ArrayList<>();
        Map<Enchantment, Integer> toAdd = new HashMap<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (var entry : enchantsToAdd.entrySet()) {
            Enchantment enchant = entry.getKey();
            int level = entry.getValue();

            String name = registry.matchEnchantName(enchant);
            if (name == null) continue;

            if (random.nextDouble() >= QUALITY_CHANCE) continue;

            QualityTier tier = QualityTier.rollTier();
            Enchantment qualityEnchant = registry.getVariant(name, tier);
            if (qualityEnchant == null) continue;

            int cappedLevel = Math.min(level, qualityEnchant.getMaxLevel());
            toRemove.add(enchant);
            toAdd.put(qualityEnchant, cappedLevel);
        }

        for (Enchantment e : toRemove) {
            enchantsToAdd.remove(e);
        }
        enchantsToAdd.putAll(toAdd);
    }
}