package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import java.util.logging.Level;

/** Resolves datapack-owned enchantments without allowing a missing key to abort plugin boot. */
public final class EnchantmentResolver {

    private EnchantmentResolver() {}

    public static Enchantment resolve(Tweaks plugin, String configPath, String featureName) {
        String raw = plugin.getConfig().getString(configPath);
        return resolve(plugin, raw, configPath, featureName);
    }

    public static Enchantment resolve(Tweaks plugin, String raw, String configPath, String featureName) {
        if (raw == null || raw.isBlank()) {
            plugin.getLogger().log(Level.WARNING, "Missing configured enchantment key for '" + configPath
                    + "'; " + featureName + " disabled.");
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw.toLowerCase(java.util.Locale.ROOT));
        if (key == null) {
            plugin.getLogger().log(Level.WARNING, "Invalid configured enchantment key '" + raw
                    + "' for '" + configPath + "'; " + featureName + " disabled.");
            return null;
        }
        Enchantment resolved = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
        if (resolved == null) {
            plugin.getLogger().log(Level.WARNING, "Configured enchantment key '" + raw
                    + "' for '" + configPath + "' was not found in the registry; "
                    + featureName + " disabled. Is the data pack loaded?");
        }
        return resolved;
    }
}
