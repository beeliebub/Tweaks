package me.beeliebub.tweaks.enchantments.quality;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * Handles special drops for quality Silk Touch variants.
 * - Legendary: Budding Amethyst
 * - Epic: Reinforced Deepslate
 * - Rare: Farmland
 * - Uncommon: Dirt Path
 */
public class SilkTouchQualityListener implements Listener {

    private final QualityRegistry registry;

    public SilkTouchQualityListener(QualityRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.isEmpty()) return;

        Block block = event.getBlock();
        Material drop = getQualitySilkTouchDrop(block, tool);
        if (drop != null) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(drop));
        }
    }

    /**
     * Determines if a block should drop its own item based on quality Silk Touch.
     * @return The material to drop, or null if no special quality drop applies.
     */
    public Material getQualitySilkTouchDrop(Block block, ItemStack tool) {
        QualityTier tier = registry.getToolQualityTier(tool, "silk_touch");
        if (tier == null) return null;

        Material type = block.getType();
        if (type == Material.BUDDING_AMETHYST && tier == QualityTier.LEGENDARY) {
            return Material.BUDDING_AMETHYST;
        }
        if (type == Material.REINFORCED_DEEPSLATE && tier.ordinal() >= QualityTier.EPIC.ordinal()) {
            return Material.REINFORCED_DEEPSLATE;
        }
        if (type == Material.FARMLAND && tier.ordinal() >= QualityTier.RARE.ordinal()) {
            return Material.FARMLAND;
        }
        if (type == Material.DIRT_PATH && tier.ordinal() >= QualityTier.UNCOMMON.ordinal()) {
            return Material.DIRT_PATH;
        }
        return null;
    }

    /**
     * Applies quality Silk Touch logic to a collection of drops.
     * Used by Tunneller for manual drop handling.
     */
    public Collection<ItemStack> applySilkQuality(Block block, ItemStack tool, Collection<ItemStack> baseDrops) {
        Material drop = getQualitySilkTouchDrop(block, tool);
        if (drop != null) {
            return List.of(new ItemStack(drop));
        }
        return baseDrops;
    }
}
