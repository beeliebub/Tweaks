package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

// Auto-smelts raw ore drops (iron, copper, gold) into ingots when mining with this enchantment
public class Smelter implements Listener {

    private final Enchantment enchantment;
    private final Telekinesis telekinesis;
    private final ResourceHunt resourceHunt;

    public Smelter(Tweaks plugin, Telekinesis telekinesis, ResourceHunt resourceHunt) {
        String raw = plugin.getConfig().getString("smelter");
        this.enchantment = resolveEnchantment(plugin, raw);
        this.telekinesis = telekinesis;
        this.resourceHunt = resourceHunt;
    }

    private Enchantment resolveEnchantment(Tweaks plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            plugin.getLogger().warning("No 'smelter' key configured; smelter enchant disabled.");
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw);
        if (key == null) {
            plugin.getLogger().log(Level.WARNING, "Invalid smelter key '" + raw + "'; smelter enchant disabled.");
            return null;
        }
        Enchantment resolved = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
        if (resolved == null) {
            plugin.getLogger().warning("Smelter enchantment '" + raw + "' not found in registry; is the data pack loaded?");
        }
        return resolved;
    }

    public boolean hasEnchant(ItemStack tool) {
        return enchantment != null && !tool.isEmpty() && tool.containsEnchantment(enchantment);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (enchantment == null) return;
        if (!event.isDropItems()) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!hasEnchant(tool)) return;

        Block block = event.getBlock();
        Collection<ItemStack> drops = block.getDrops(tool, player);
        if (drops.isEmpty()) return;

        List<ItemStack> smelted = smeltDrops(drops);

        event.setDropItems(false);

        // Credit smelted drops toward the active Resource Hunt. setDropItems(false) above
        // suppresses BlockDropItemEvent, which would otherwise be the path that ResourceHunt
        // observes; we have to call the external-drops hook directly so smelted ingots count
        // toward the goal in jass:resource.
        if (resourceHunt != null) {
            resourceHunt.recordExternalDrops(player, block, smelted);
        }

        boolean routeToInventory = telekinesis != null && telekinesis.hasEnchant(tool);
        for (ItemStack drop : smelted) {
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

    // Replace raw ore items with their smelted counterparts
    public static List<ItemStack> smeltDrops(Collection<ItemStack> drops) {
        List<ItemStack> out = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            Material smelted = smelt(drop.getType());
            if (smelted != null) {
                ItemStack replacement = new ItemStack(smelted, drop.getAmount());
                out.add(replacement);
            } else {
                out.add(drop);
            }
        }
        return out;
    }

    private static Material smelt(Material material) {
        return switch (material) {
            case RAW_IRON -> Material.IRON_INGOT;
            case RAW_COPPER -> Material.COPPER_INGOT;
            case RAW_GOLD -> Material.GOLD_INGOT;
            default -> null;
        };
    }
}
