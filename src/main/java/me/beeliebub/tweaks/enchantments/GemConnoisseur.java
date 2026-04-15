package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class GemConnoisseur implements Listener {

    private static final int MAX_LEVEL = 3;

    private final Enchantment enchantment;
    private final Map<Integer, Map<String, Map<Material, Integer>>> rates;
    private final Telekinesis telekinesis;

    public GemConnoisseur(Tweaks plugin, Telekinesis telekinesis) {
        String raw = plugin.getConfig().getString("gem-connoisseur");
        this.enchantment = resolveEnchantment(plugin, raw);
        this.rates = loadRates(plugin);
        this.telekinesis = telekinesis;
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

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (enchantment == null) return;

        Block block = event.getBlock();
        String blockKind = blockKindOf(block.getType());
        if (blockKind == null) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.isEmpty() || !tool.containsEnchantment(enchantment)) return;
        if (block.getDrops(tool, player).isEmpty()) return;

        int level = Math.min(tool.getEnchantmentLevel(enchantment), MAX_LEVEL);
        if (level <= 0) return;

        Map<String, Map<Material, Integer>> byBlock = rates.get(level);
        if (byBlock == null) return;
        Map<Material, Integer> materialRates = byBlock.get(blockKind);
        if (materialRates == null || materialRates.isEmpty()) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        boolean routeToInventory = telekinesis != null && telekinesis.hasEnchant(tool);

        for (Map.Entry<Material, Integer> entry : materialRates.entrySet()) {
            int oneIn = entry.getValue();
            if (oneIn <= 0) continue;
            if (random.nextInt(oneIn) != 0) continue;

            int amount = 1;
            if (fortuneLevel > 0) {
                int bonus = random.nextInt(fortuneLevel + 2) - 1;
                if (bonus > 0) amount += bonus;
            }

            ItemStack drop = new ItemStack(entry.getKey(), amount);
            if (routeToInventory) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
                for (ItemStack remaining : leftover.values()) {
                    block.getWorld().dropItemNaturally(block.getLocation(), remaining);
                }
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