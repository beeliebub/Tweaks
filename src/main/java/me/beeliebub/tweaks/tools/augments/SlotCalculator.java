package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import org.bukkit.Material;

import java.util.List;

/** Bukkit-light slot capacity, purchase-price, and quality-weight math. */
public final class SlotCalculator {

    private final Tweaks plugin;
    private final QualityRegistry qualityRegistry;

    public SlotCalculator(Tweaks plugin, QualityRegistry qualityRegistry) {
        this.plugin = plugin;
        this.qualityRegistry = qualityRegistry;
    }

    public int capacity(Material material) {
        String exact = material.name().toLowerCase(java.util.Locale.ROOT);
        int configured = mapInt("tools.augments.slot-capacity." + exact, -1);
        if (configured >= 0) return configured;
        String family = family(material);
        configured = family == null ? -1 : mapInt("tools.augments.slot-capacity." + family, -1);
        if (configured >= 0) return configured;
        return Math.max(0, Math.min(64, plugin.getConfig().getInt("tools.augments.slot-capacity-default", 5)));
    }

    public int price(int slot) {
        return Math.max(0, mapInt("tools.augments.slot-prices." + slot, 0));
    }

    public int qualityWeight(org.bukkit.enchantments.Enchantment enchantment) {
        QualityTier tier = qualityRegistry == null ? null : qualityRegistry.getTier(enchantment);
        return qualityWeight(tier);
    }

    public int qualityWeight(QualityTier tier) {
        String key = tier == null ? "none" : tier.name().toLowerCase(java.util.Locale.ROOT);
        return Math.max(1, mapInt("tools.augments.quality-slot-cost." + key, tier == null ? 1 : tier.ordinal() + 2));
    }

    public int used(List<AugmentEntry> entries) {
        int total = 0;
        for (AugmentEntry entry : entries) {
            if (entry == null) continue;
            var enchantment = io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(entry.enchantmentKey());
            total += qualityWeight(enchantment);
        }
        return total;
    }

    public String slotDots(int purchased, int used, int capacity) {
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < capacity; i++) {
            if (i < used) dots.append('●');
            else if (i < purchased) dots.append('○');
            else dots.append('◌');
        }
        return dots.toString();
    }

    private int mapInt(String path, int fallback) {
        if (!plugin.getConfig().contains(path)) return fallback;
        int value = plugin.getConfig().getInt(path, fallback);
        return value;
    }

    private static String family(Material material) {
        String name = material.name().toLowerCase(java.util.Locale.ROOT);
        if (name.startsWith("wooden_")) return "wooden";
        if (name.startsWith("stone_") || name.equals("stone")) return "stone";
        if (name.startsWith("iron_")) return "iron";
        if (name.startsWith("copper_")) return "copper";
        if (name.startsWith("golden_") || name.startsWith("gold_")) return "gold";
        if (name.startsWith("diamond_")) return "diamond";
        if (name.startsWith("netherite_")) return "netherite";
        if (name.equals("shears")) return "shears";
        if (name.equals("fishing_rod")) return "fishing_rod";
        if (name.equals("mace")) return "mace";
        if (name.equals("elytra")) return "elytra";
        return null;
    }
}
