package me.beeliebub.tweaks.enchantments.quality;

import org.bukkit.Material;
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

/**
 * Handles drops for quality Silk Touch variants.
 * <p>
 * Quality silk variants (jass:*_silk_touch) are separate registry entries from vanilla
 * silk_touch, so vanilla's silk-touch loot logic does NOT fire for tools that only have a
 * quality variant. This listener restores vanilla silk drops on every silk-touchable block,
 * and on top of that grants the tier-specific bonus drops below.
 * <p>
 * Tier-specific bonuses (block drops itself):
 * - Uncommon+: Dirt Path
 * - Rare+:     Farmland
 * - Epic+:     Reinforced Deepslate
 * - Legendary: Budding Amethyst
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

        QualityTier tier = registry.getToolQualityTier(tool, "silk_touch");
        if (tier == null) return;

        Block block = event.getBlock();

        Material bonus = getQualitySilkTouchDrop(block, tool);
        if (bonus != null) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(bonus));
            return;
        }

        // If vanilla silk_touch is also on the tool, vanilla already handles silk drops
        // correctly; don't double-process.
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;

        Collection<ItemStack> silkDrops = computeSilkDrops(block, tool, player);
        // Only override when the synthetic silk tool would actually produce different drops
        // than the player's real tool — otherwise leave vanilla drops untouched (this avoids
        // re-routing items for non-silk-affected blocks like dirt or wood).
        if (silkDrops == null) return;
        event.setDropItems(false);
        for (ItemStack drop : silkDrops) {
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        }
    }

    /**
     * Determines if a block should drop a tier-specific bonus based on quality Silk Touch.
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
     * Applies quality Silk Touch logic to a manually-collected drop list (used by Tunneller).
     * Returns the tier bonus when applicable, otherwise vanilla silk drops when the block is
     * silk-touchable, otherwise the original drops unchanged.
     */
    public Collection<ItemStack> applySilkQuality(Block block, ItemStack tool, Player player,
                                                  Collection<ItemStack> baseDrops) {
        QualityTier tier = registry.getToolQualityTier(tool, "silk_touch");
        if (tier == null) return baseDrops;

        Material bonus = getQualitySilkTouchDrop(block, tool);
        if (bonus != null) return List.of(new ItemStack(bonus));

        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) return baseDrops;

        Collection<ItemStack> silkDrops = computeSilkDrops(block, tool, player);
        return silkDrops != null ? silkDrops : baseDrops;
    }

    /**
     * Synthesizes the drops the block would produce if the player's tool also had vanilla
     * silk_touch. Returns null if silk produces the same materials as the bare tool — that's
     * how we identify blocks vanilla silk does not affect (dirt, wood, leaves with shears,
     * etc.) and avoid pointless drop replacement.
     */
    private Collection<ItemStack> computeSilkDrops(Block block, ItemStack tool, Player player) {
        ItemStack syntheticSilk = tool.clone();
        syntheticSilk.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        Collection<ItemStack> silkDrops = block.getDrops(syntheticSilk, player);
        Collection<ItemStack> normalDrops = block.getDrops(tool, player);
        if (sameMaterials(silkDrops, normalDrops)) return null;
        return new ArrayList<>(silkDrops);
    }

    private static boolean sameMaterials(Collection<ItemStack> a, Collection<ItemStack> b) {
        if (a.size() != b.size()) return false;
        var ai = a.iterator();
        var bi = b.iterator();
        while (ai.hasNext() && bi.hasNext()) {
            ItemStack ax = ai.next();
            ItemStack bx = bi.next();
            if (ax == null || bx == null) {
                if (ax != bx) return false;
                continue;
            }
            if (ax.getType() != bx.getType()) return false;
            if (ax.getAmount() != bx.getAmount()) return false;
        }
        return true;
    }
}