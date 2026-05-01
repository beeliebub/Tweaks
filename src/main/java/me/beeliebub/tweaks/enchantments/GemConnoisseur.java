package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

// Adds bonus gem/material drops when mining stone, deepslate, or netherrack.
// Drop rates are configurable per enchantment level and block type in config.yml.
public class GemConnoisseur implements Listener {

    private static final int MAX_LEVEL = 3;

    private final Enchantment enchantment;
    // Nested map: enchant level -> block kind -> (bonus material -> 1-in-N chance)
    private final Map<Integer, Map<String, Map<Material, Integer>>> rates;
    private final Telekinesis telekinesis;
    private final ResourceHunt resourceHunt;

    public GemConnoisseur(Tweaks plugin, Telekinesis telekinesis, ResourceHunt resourceHunt) {
        String raw = plugin.getConfig().getString("gem-connoisseur");
        this.enchantment = resolveEnchantment(plugin, raw);
        this.rates = loadRates(plugin);
        this.telekinesis = telekinesis;
        this.resourceHunt = resourceHunt;
    }

    private Enchantment resolveEnchantment(Tweaks plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            plugin.getLogger().warning("No 'gem-connoisseur' key configured; gem-connoisseur enchant disabled.");
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw);
        if (key == null) {
            plugin.getLogger().log(Level.WARNING, "Invalid gem-connoisseur key '" + raw + "'; gem-connoisseur enchant disabled.");
            return null;
        }
        Enchantment resolved = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
        if (resolved == null) {
            plugin.getLogger().warning("Gem-connoisseur enchantment '" + raw + "' not found in registry; is the data pack loaded?");
        }
        return resolved;
    }

    private Map<Integer, Map<String, Map<Material, Integer>>> loadRates(Tweaks plugin) {
        Map<Integer, Map<String, Map<Material, Integer>>> result = new HashMap<>();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("gem-connoisseur-rates");
        if (root == null) {
            plugin.getLogger().warning("No 'gem-connoisseur-rates' section configured; gem-connoisseur will have no drops.");
            return result;
        }
        for (String levelKey : root.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(levelKey);
            } catch (NumberFormatException e) {
                continue;
            }
            ConfigurationSection levelSection = root.getConfigurationSection(levelKey);
            if (levelSection == null) continue;

            Map<String, Map<Material, Integer>> byBlock = new HashMap<>();
            for (String blockKey : levelSection.getKeys(false)) {
                ConfigurationSection blockSection = levelSection.getConfigurationSection(blockKey);
                if (blockSection == null) continue;

                Map<Material, Integer> materialRates = new LinkedHashMap<>();
                for (String matKey : blockSection.getKeys(false)) {
                    Material mat = Material.matchMaterial(matKey);
                    if (mat == null) {
                        plugin.getLogger().warning("Unknown material '" + matKey + "' in gem-connoisseur-rates." + levelKey + "." + blockKey);
                        continue;
                    }
                    int rate = blockSection.getInt(matKey);
                    if (rate > 0) materialRates.put(mat, rate);
                }
                byBlock.put(blockKey.toLowerCase(), materialRates);
            }
            result.put(level, byBlock);
        }
        return result;
    }

    public boolean hasEnchant(ItemStack tool) {
        return enchantment != null && !tool.isEmpty() && tool.containsEnchantment(enchantment);
    }

    // Roll for bonus gem drops based on block type, enchant level, and fortune
    public List<ItemStack> rollDrops(Material blockType, ItemStack tool) {
        List<ItemStack> result = new ArrayList<>();
        if (enchantment == null) return result;

        String blockKind = blockKindOf(blockType);
        if (blockKind == null) return result;
        if (!tool.containsEnchantment(enchantment)) return result;

        int level = Math.min(tool.getEnchantmentLevel(enchantment), MAX_LEVEL);
        if (level <= 0) return result;

        Map<String, Map<Material, Integer>> byBlock = rates.get(level);
        if (byBlock == null) return result;
        Map<Material, Integer> materialRates = byBlock.get(blockKind);
        if (materialRates == null || materialRates.isEmpty()) return result;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);

        for (Map.Entry<Material, Integer> entry : materialRates.entrySet()) {
            int oneIn = entry.getValue();
            if (oneIn <= 0) continue;
            if (random.nextInt(oneIn) != 0) continue;

            int amount = 1;
            if (fortuneLevel > 0) {
                int bonus = random.nextInt(fortuneLevel + 2) - 1;
                if (bonus > 0) amount += bonus;
            }

            result.add(new ItemStack(entry.getKey(), amount));
        }
        return result;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (enchantment == null) return;

        Block block = event.getBlock();
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.isEmpty() || !hasEnchant(tool)) return;
        if (block.getDrops(tool, player).isEmpty()) return;

        List<ItemStack> gemDrops = rollDrops(block.getType(), tool);
        if (gemDrops.isEmpty()) return;

        if (resourceHunt != null) {
            resourceHunt.recordExternalDrops(player, block, gemDrops);
        }

        boolean routeToInventory = telekinesis != null && telekinesis.hasEnchant(tool);
        for (ItemStack drop : gemDrops) {
            if (routeToInventory) {
                telekinesis.giveOrDrop(player, block, drop);
            } else {
                block.getWorld().dropItemNaturally(block.getLocation(), drop);
            }
        }
    }

    private String blockKindOf(Material material) {
        return switch (material) {
            case STONE -> "stone";
            case DEEPSLATE -> "deepslate";
            case NETHERRACK -> "netherrack";
            default -> null;
        };
    }
}