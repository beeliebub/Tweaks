package me.beeliebub.tweaks.minigames.resource;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads and validates the resource_hunt.yml target pools without owning runtime session state. */
final class ResourceHuntConfigLoader {
    private static final double DEFAULT_MULTIPLIER = 2.0;
    private static final String OVERWORLD_SECTION = "overworld";
    private static final String NETHER_SECTION = "nether";
    private static final Set<ResourceHunt.Category> ENTITY_CATEGORIES =
            EnumSet.of(ResourceHunt.Category.KILL, ResourceHunt.Category.SHEAR, ResourceHunt.Category.BREED);
    private static final Set<ResourceHunt.Category> OVERWORLD_ONLY =
            EnumSet.of(ResourceHunt.Category.ENCHANT, ResourceHunt.Category.SHEAR);
    private static final Set<ResourceHunt.Category> NETHER_ONLY =
            EnumSet.of(ResourceHunt.Category.BARTER);

    private final Tweaks plugin;

    ResourceHuntConfigLoader(Tweaks plugin) {
        this.plugin = plugin;
    }

    Map<String, List<ResourceHuntTarget.Definition>> loadAllEntries() {
        File file = new File(plugin.getDataFolder(), "resource_hunt.yml");
        if (!file.exists()) {
            plugin.saveResource("resource_hunt.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<ResourceHuntTarget.Definition> overworldEntries = new ArrayList<>();
        loadTargets(config, OVERWORLD_SECTION, ResourceHunt.TARGET_WORLD_KEY, overworldEntries);
        List<ResourceHuntTarget.Definition> netherEntries = new ArrayList<>();
        loadTargets(config, NETHER_SECTION, ResourceHunt.TARGET_WORLD_NETHER_KEY, netherEntries);

        Map<String, List<ResourceHuntTarget.Definition>> result = new LinkedHashMap<>();
        if (!overworldEntries.isEmpty()) result.put(ResourceHunt.TARGET_WORLD_KEY, overworldEntries);
        if (!netherEntries.isEmpty()) result.put(ResourceHunt.TARGET_WORLD_NETHER_KEY, netherEntries);
        return result;
    }

    private void loadTargets(YamlConfiguration config, String section, String worldKey,
                             List<ResourceHuntTarget.Definition> entries) {
        ConfigurationSection sectionConfig = config.getConfigurationSection(section);
        if (sectionConfig == null) return;
        for (String categoryKey : sectionConfig.getKeys(false)) {
            ResourceHunt.Category category;
            try {
                category = ResourceHunt.Category.valueOf(categoryKey.toUpperCase());
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("resource_hunt.yml (" + section + "): unknown category '"
                        + categoryKey + "', skipped.");
                continue;
            }
            if (!isCategoryAllowedIn(category, section)) {
                plugin.getLogger().warning("resource_hunt.yml: category '" + categoryKey
                        + "' is not allowed in section '" + section + "', skipped.");
                continue;
            }
            ConfigurationSection categorySection = sectionConfig.getConfigurationSection(categoryKey);
            if (categorySection == null) continue;
            for (Map.Entry<String, Object> entry : categorySection.getValues(false).entrySet()) {
                parseAndAddTarget(entry, category, section, categoryKey, worldKey, entries);
            }
        }
    }

    private void parseAndAddTarget(Map.Entry<String, Object> entry, ResourceHunt.Category category,
                                   String section, String categoryKey, String worldKey,
                                   List<ResourceHuntTarget.Definition> entries) {
        String targetKey = entry.getKey();
        String location = section + "/" + categoryKey;
        int amount;
        double multiplier;
        Object raw = entry.getValue();
        if (raw instanceof Number number) {
            amount = number.intValue();
            multiplier = DEFAULT_MULTIPLIER;
        } else if (raw instanceof String string) {
            String[] parts = string.split(":", 2);
            try {
                amount = Integer.parseInt(parts[0].trim());
                multiplier = parts.length >= 2 ? Double.parseDouble(parts[1].trim()) : DEFAULT_MULTIPLIER;
            } catch (NumberFormatException exception) {
                plugin.getLogger().warning("resource_hunt.yml (" + location + "): '" + targetKey
                        + "' has invalid value '" + string + "', skipped.");
                return;
            }
        } else {
            plugin.getLogger().warning("resource_hunt.yml (" + location + "): '" + targetKey
                    + "' has invalid amount, skipped.");
            return;
        }
        if (amount <= 0) {
            plugin.getLogger().warning("resource_hunt.yml (" + location + "): '" + targetKey
                    + "' has non-positive amount, skipped.");
            return;
        }
        if (multiplier < 1.0) {
            plugin.getLogger().warning("resource_hunt.yml (" + location + "): '" + targetKey
                    + "' has multiplier < 1.0; clamping to 1.0.");
            multiplier = 1.0;
        }

        Material material = null;
        EntityType entityType = null;
        DyeColor sheepColor = null;
        if (ENTITY_CATEGORIES.contains(category)) {
            boolean resolved = false;
            if (category == ResourceHunt.Category.SHEAR && targetKey.toLowerCase().endsWith("_sheep")) {
                String prefix = targetKey.substring(0, targetKey.length() - 6);
                try {
                    sheepColor = DyeColor.valueOf(prefix.toUpperCase());
                    entityType = EntityType.SHEEP;
                    resolved = true;
                } catch (IllegalArgumentException ignored) {
                    // Fall through to the established unknown-entity warning.
                }
            }
            if (!resolved) {
                try {
                    entityType = EntityType.valueOf(targetKey.toUpperCase());
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("resource_hunt.yml (" + location + "): unknown entity '"
                            + targetKey + "', skipped.");
                    return;
                }
            }
        } else {
            material = Material.matchMaterial(targetKey);
            if (material == null) {
                plugin.getLogger().warning("resource_hunt.yml (" + location + "): unknown material '"
                        + targetKey + "', skipped.");
                return;
            }
        }
        entries.add(new ResourceHuntTarget.Definition(category, material, entityType, amount,
                multiplier, worldKey, sheepColor));
    }

    private static boolean isCategoryAllowedIn(ResourceHunt.Category category, String section) {
        if (OVERWORLD_ONLY.contains(category)) return OVERWORLD_SECTION.equals(section);
        if (NETHER_ONLY.contains(category)) return NETHER_SECTION.equals(section);
        return true;
    }
}
