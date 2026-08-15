package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Applies supported-item, quality, and configurable vanilla exclusivity rules. */
public final class AugmentCompatibility {

    private final Tweaks plugin;
    private final QualityRegistry qualityRegistry;

    public AugmentCompatibility(Tweaks plugin, QualityRegistry qualityRegistry) {
        this.plugin = plugin;
        this.qualityRegistry = qualityRegistry;
    }

    public boolean canAttach(ItemStack item, Enchantment candidate, List<AugmentEntry> entries) {
        if (item == null || item.isEmpty() || candidate == null || !candidate.canEnchantItem(item)) return false;
        for (AugmentEntry entry : entries) {
            var existing = registry().get(entry.enchantmentKey());
            if (existing == null) continue;
            if (existing.getKey().equals(candidate.getKey())) return false;
            if (!entry.active()) continue;
            String existingQuality = qualityRegistry == null ? null : qualityRegistry.matchEnchantName(existing);
            String candidateQuality = qualityRegistry == null ? null : qualityRegistry.matchEnchantName(candidate);
            if (existingQuality != null && existingQuality.equals(candidateQuality)) {
                // Multiple tiers may occupy slots, but only one quality tier is active at a time.
                continue;
            }
            if (plugin.getConfig().getBoolean("tools.augments.enforce-vanilla-exclusivity", true)
                    && (existing.conflictsWith(candidate) || candidate.conflictsWith(existing))) return false;
        }
        return true;
    }

    public boolean sameQualityBase(Enchantment first, Enchantment second) {
        if (qualityRegistry == null) return false;
        String a = qualityRegistry.matchEnchantName(first);
        return a != null && a.equals(qualityRegistry.matchEnchantName(second));
    }

    private org.bukkit.Registry<Enchantment> registry() {
        return io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT);
    }
}
