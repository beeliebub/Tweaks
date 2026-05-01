package me.beeliebub.tweaks.minigames.resource;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the whitelist of items allowed to be brought into the resource world.
 */
public class ResourceHuntItems {

    private final Tweaks plugin;
    private final File file;
    private final Set<Material> allowedItems = new HashSet<>();

    public ResourceHuntItems(Tweaks plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "resource_hunt_items.yml");
        loadConfig();
    }

    private void loadConfig() {
        if (!file.exists()) {
            createDefaultConfig();
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<String> list = cfg.getStringList("allowed-items");
        allowedItems.clear();
        for (String s : list) {
            Material mat = Material.matchMaterial(s);
            if (mat != null) {
                allowedItems.add(mat);
            } else {
                plugin.getLogger().warning("resource_hunt_items.yml: unknown material '" + s + "'");
            }
        }
    }

    private void createDefaultConfig() {
        List<String> defaults = new ArrayList<>();
        
        // Armor
        String[] materials = {"LEATHER", "CHAINMAIL", "IRON", "GOLDEN", "DIAMOND", "NETHERITE"};
        String[] slots = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
        for (String m : materials) {
            for (String s : slots) {
                defaults.add(m + "_" + s);
            }
        }
        defaults.add("TURTLE_HELMET");

        // Tools
        String[] toolMaterials = {"WOODEN", "STONE", "IRON", "GOLDEN", "DIAMOND", "NETHERITE"};
        String[] toolTypes = {"PICKAXE", "AXE", "SHOVEL", "SWORD", "HOE"};
        for (String m : toolMaterials) {
            for (String t : toolTypes) {
                defaults.add(m + "_" + t);
            }
        }
        // Additional tools
        defaults.add("TRIDENT");
        defaults.add("BOW");
        defaults.add("CROSSBOW");
        defaults.add("FISHING_ROD");
        defaults.add("MACE");
        defaults.add("SHEARS");
        // Copper tools (not standard vanilla but requested)
        defaults.add("COPPER_PICKAXE");
        defaults.add("COPPER_AXE");
        defaults.add("COPPER_SHOVEL");
        defaults.add("COPPER_SWORD");
        defaults.add("COPPER_HOE");
        // Spear (requested, may be a mod or custom item, but added to list as material)
        defaults.add("SPEAR");

        // Food
        String[] food = {
            "SUSPICIOUS_STEW", "RABBIT_STEW", "PORKCHOP", "COOKED_PORKCHOP", "PUMPKIN_PIE", 
            "BEEF", "COOKED_BEEF", "BEETROOT_SOUP", "CHICKEN", "COOKED_CHICKEN", 
            "MUTTON", "COOKED_MUTTON", "SALMON", "COOKED_SALMON", "GOLDEN_CARROT", 
            "HONEY_BOTTLE", "MUSHROOM_STEW", "BAKED_POTATO", "BREAD", "COD", "COOKED_COD", 
            "RABBIT", "COOKED_RABBIT", "APPLE", "CHORUS_FRUIT", "COOKIE"
        };
        Collections.addAll(defaults, food);

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("allowed-items", defaults);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save resource_hunt_items.yml!");
        }
    }

    /**
     * Checks a player's inventory for disallowed items.
     * @return A list of Materials that are not allowed.
     */
    public List<Material> getDisallowedItems(Player player) {
        List<Material> disallowed = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                if (!allowedItems.contains(item.getType())) {
                    disallowed.add(item.getType());
                }
            }
        }
        return disallowed;
    }

    public boolean isAllowed(Material material) {
        return allowedItems.contains(material);
    }

    public void addAllowedItem(Material material) {
        if (allowedItems.add(material)) {
            saveList();
        }
    }

    public void removeAllowedItem(Material material) {
        if (allowedItems.remove(material)) {
            saveList();
        }
    }

    private void saveList() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("allowed-items", allowedItems.stream().map(Material::name).collect(Collectors.toList()));
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save resource_hunt_items.yml!");
        }
    }
}
