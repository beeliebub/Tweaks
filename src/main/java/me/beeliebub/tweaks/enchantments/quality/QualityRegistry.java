package me.beeliebub.tweaks.enchantments.quality;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Loads and indexes all quality enchantment variants (uncommon through legendary) from the
// Paper registry. Provides lookup methods for the enchant table listener, fortune/looting
// re-roll listeners, and efficacy/tunneller radius checks.
public class QualityRegistry {

    // All enchantment names that have quality variants in the data pack
    private static final String[] ENCHANT_NAMES = {
            "fortune", "looting", "luck_of_the_sea", "frost_walker", "knockback",
            "lunge", "lure", "multishot", "piercing", "power", "punch",
            "quick_charge", "sharpness", "smite", "bane_of_arthropods",
            "sweeping_edge", "unbreaking", "efficacy", "tunneller", "efficiency"
    };

    private static final Set<String> SUPPORTED_NAMES = Set.of(ENCHANT_NAMES);

    // enchantName -> tier -> Enchantment
    private final Map<String, EnumMap<QualityTier, Enchantment>> variants = new HashMap<>();

    // quality Enchantment -> QualityTier (reverse lookup)
    private final Map<Enchantment, QualityTier> enchantToTier = new HashMap<>();

    // quality Enchantment -> base enchant name (reverse lookup)
    private final Map<Enchantment, String> enchantToName = new HashMap<>();

    public QualityRegistry(Tweaks plugin) {
        Registry<Enchantment> registry = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT);

        for (String name : ENCHANT_NAMES) {
            for (QualityTier tier : QualityTier.values()) {
                NamespacedKey key = new NamespacedKey("jass", tier.getPrefix() + "_" + name);
                Enchantment ench = registry.get(key);
                if (ench == null) continue;

                variants.computeIfAbsent(name, k -> new EnumMap<>(QualityTier.class))
                        .put(tier, ench);
                enchantToTier.put(ench, tier);
                enchantToName.put(ench, name);
            }
        }

        int total = variants.values().stream().mapToInt(Map::size).sum();
        plugin.getLogger().info("Loaded " + total + " quality enchantment variants across "
                + variants.size() + " enchantment types.");
    }

    // Match a base enchantment's key name to a supported enchant name (for enchant table).
    // Works for both vanilla (minecraft:fortune -> "fortune") and custom (jass:efficacy -> "efficacy").
    public String matchEnchantName(Enchantment enchant) {
        String keyValue = enchant.getKey().getKey();
        return SUPPORTED_NAMES.contains(keyValue) ? keyValue : null;
    }

    // Get the quality variant enchantment for a given name and tier (null if not loaded)
    public Enchantment getVariant(String enchantName, QualityTier tier) {
        EnumMap<QualityTier, Enchantment> tierMap = variants.get(enchantName);
        return tierMap != null ? tierMap.get(tier) : null;
    }

    // Get the quality tier of a quality enchantment (null if not a quality enchant)
    public QualityTier getTier(Enchantment enchant) {
        return enchantToTier.get(enchant);
    }

    // Get the base enchant name of a quality enchantment (null if not a quality enchant)
    public String getName(Enchantment enchant) {
        return enchantToName.get(enchant);
    }

    // Check if a tool has any quality variant of the given enchantment and return info.
    // Returns null if the tool has no quality variant of this enchantment.
    public QualityInfo getToolQuality(ItemStack tool, String enchantName) {
        if (tool == null || tool.isEmpty()) return null;
        EnumMap<QualityTier, Enchantment> tierMap = variants.get(enchantName);
        if (tierMap == null) return null;
        for (var entry : tierMap.entrySet()) {
            int level = tool.getEnchantmentLevel(entry.getValue());
            if (level > 0) return new QualityInfo(entry.getKey(), level);
        }
        return null;
    }

    // Get the quality tier on a tool for a specific enchantment (null if none)
    public QualityTier getToolQualityTier(ItemStack tool, String enchantName) {
        QualityInfo info = getToolQuality(tool, enchantName);
        return info != null ? info.tier() : null;
    }

    public record QualityInfo(QualityTier tier, int level) {}
}