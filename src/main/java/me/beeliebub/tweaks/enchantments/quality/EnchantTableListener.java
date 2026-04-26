package me.beeliebub.tweaks.enchantments.quality;

import me.beeliebub.tweaks.managers.BloodMoonManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// Intercepts enchanting table results and rolls a per-enchantment chance to upgrade
// to a quality variant (uncommon/rare/epic/legendary). The roll chance is boosted
// while a Blood Moon event is active.
public class EnchantTableListener implements Listener {

    private static final double QUALITY_CHANCE = 0.10;
    private static final double BLOOD_MOON_QUALITY_CHANCE = 0.50;

    private final QualityRegistry registry;
    private final BloodMoonManager bloodMoon;

    public EnchantTableListener(QualityRegistry registry, BloodMoonManager bloodMoon) {
        this.registry = registry;
        this.bloodMoon = bloodMoon;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        Map<Enchantment, Integer> enchantsToAdd = event.getEnchantsToAdd();
        List<Enchantment> toRemove = new ArrayList<>();
        Map<Enchantment, Integer> toAdd = new HashMap<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double chance = bloodMoon.isActive() ? BLOOD_MOON_QUALITY_CHANCE : QUALITY_CHANCE;

        for (var entry : enchantsToAdd.entrySet()) {
            Enchantment enchant = entry.getKey();
            int level = entry.getValue();

            String name = registry.matchEnchantName(enchant);
            if (name == null) continue;

            if (random.nextDouble() >= chance) continue;

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