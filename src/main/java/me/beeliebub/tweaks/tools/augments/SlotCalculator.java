package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bukkit-light slot capacity, purchase-price, and quality-weight math. */
public final class SlotCalculator {

    public static final int MAX_RENDERED_SLOTS = 64;
    public static final Set<String> FAMILY_KEYS = Set.of(
            "wooden", "stone", "iron", "copper", "gold", "diamond", "netherite",
            "shears", "fishing_rod", "mace", "elytra");

    public static boolean isValidCapacityKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return false;
        String normalized = rawKey.toLowerCase(java.util.Locale.ROOT);
        if (FAMILY_KEYS.contains(normalized)) return true;
        Material material = Material.matchMaterial(normalized);
        return material != null && !material.isAir() && material.isItem();
    }

    private final Tweaks plugin;
    private final QualityRegistry qualityRegistry;

    public SlotCalculator(Tweaks plugin, QualityRegistry qualityRegistry) {
        this.plugin = plugin;
        this.qualityRegistry = qualityRegistry;
    }

    public int capacity(Material material) {
        return capacityResolution(material).value();
    }

    public int price(int slot) {
        return priceResolution(slot).value();
    }

    public CapacityResolution capacityResolution(Material material) {
        Material resolvedMaterial = material == null ? Material.AIR : material;
        String exact = resolvedMaterial.name().toLowerCase(java.util.Locale.ROOT);
        int configured = mapInt("tools.augments.slot-capacity." + exact, -1);
        if (configured >= 0) return new CapacityResolution("exact:" + exact, configured);
        String family = family(resolvedMaterial);
        configured = family == null ? -1 : mapInt("tools.augments.slot-capacity." + family, -1);
        if (configured >= 0) return new CapacityResolution("family:" + family, configured);
        return new CapacityResolution("default",
                Math.max(0, Math.min(64, plugin.getConfig().getInt("tools.augments.slot-capacity-default", 5))));
    }

    public PriceResolution priceResolution(int slot) {
        Object raw = plugin.getConfig().get("tools.augments.slot-prices");
        if (raw instanceof Map<?, ?> values) {
            String exactKey = Integer.toString(slot);
            if (values.containsKey(slot) || values.containsKey(exactKey)) {
                Object value = values.containsKey(slot) ? values.get(slot) : values.get(exactKey);
                return new PriceResolution("exact:" + slot,
                        value instanceof Number number ? number.intValue() : -1);
            }
            int highest = values.values().stream()
                    .filter(Number.class::isInstance)
                    .mapToInt(value -> ((Number) value).intValue())
                    .max().orElse(-1);
            return highest < 0 ? new PriceResolution("unpriced", -1)
                    : new PriceResolution("highest-configured", highest);
        }
        String exactPath = "tools.augments.slot-prices." + slot;
        if (plugin.getConfig().contains(exactPath)) {
            return new PriceResolution("exact:" + slot, mapInt(exactPath, -1));
        }
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("tools.augments.slot-prices");
        int highest = section == null ? -1 : section.getValues(false).values().stream()
                .filter(Number.class::isInstance)
                .mapToInt(value -> ((Number) value).intValue())
                .max().orElse(-1);
        return highest < 0 ? new PriceResolution("unpriced", -1)
                : new PriceResolution("highest-configured", highest);
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
        long total = 0;
        for (AugmentEntry entry : entries) {
            if (entry == null) continue;
            var enchantment = io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(entry.enchantmentKey());
            total = Math.min(Integer.MAX_VALUE, total + qualityWeight(enchantment));
        }
        return (int) total;
    }

    public String slotDots(int purchased, int used, int capacity) {
        int visibleCapacity = Math.min(Math.max(0, capacity), MAX_RENDERED_SLOTS);
        StringBuilder dots = new StringBuilder(visibleCapacity + (capacity > visibleCapacity ? 1 : 0));
        for (int i = 0; i < visibleCapacity; i++) {
            if (i < used) dots.append('●');
            else if (i < purchased) dots.append('○');
            else dots.append('◌');
        }
        if (capacity > visibleCapacity) dots.append('…');
        return dots.toString();
    }

    private int mapInt(String path, int fallback) {
        if (!plugin.getConfig().contains(path)) return fallback;
        int value = plugin.getConfig().getInt(path, fallback);
        return value;
    }

    private static String family(Material material) {
        String name = material.name().toLowerCase(java.util.Locale.ROOT);
        if (name.startsWith("leather_")) return familyKey("wooden");
        if (name.startsWith("chainmail_")) return familyKey("stone");
        if (name.equals("turtle_helmet")) return familyKey("iron");
        if (name.startsWith("wooden_")) return familyKey("wooden");
        if (name.startsWith("stone_") || name.equals("stone")) return familyKey("stone");
        if (name.startsWith("iron_")) return familyKey("iron");
        if (name.startsWith("copper_")) return familyKey("copper");
        if (name.startsWith("golden_") || name.startsWith("gold_")) return familyKey("gold");
        if (name.startsWith("diamond_")) return familyKey("diamond");
        if (name.startsWith("netherite_")) return familyKey("netherite");
        if (name.equals("shears")) return familyKey("shears");
        if (name.equals("fishing_rod")) return familyKey("fishing_rod");
        if (name.equals("mace")) return familyKey("mace");
        if (name.equals("elytra")) return familyKey("elytra");
        return null;
    }

    private static String familyKey(String family) {
        return FAMILY_KEYS.contains(family) ? family : null;
    }

    public record CapacityResolution(String key, int value) {}

    public record PriceResolution(String key, int value) {}
}
