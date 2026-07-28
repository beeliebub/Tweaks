package me.beeliebub.tweaks.minigames.resource;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Owns Resource Hunt's counted-item and placed-block taint markers.
 *
 * <p>The item and chunk PDC keys are persistent compatibility contracts: counted stacks must
 * never contribute to progress again, while a tainted placed block must make its later drops
 * counted. Callers deliberately mutate existing item metadata so foreign item metadata remains
 * intact.</p>
 */
final class ResourceHuntPdc {
    private final NamespacedKey countedKey;

    ResourceHuntPdc(Tweaks plugin) {
        this.countedKey = new NamespacedKey(plugin, "resource_hunt_counted");
    }

    NamespacedKey countedKey() {
        return countedKey;
    }

    boolean isCounted(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(countedKey, PersistentDataType.BYTE);
    }

    boolean markCounted(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(countedKey, PersistentDataType.BYTE)) return false;
        pdc.set(countedKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return true;
    }

    void tagInventoryStacks(Player player, Material material, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (remaining <= 0) break;
            if (stack == null || stack.getType() != material || isCounted(stack)) continue;
            markCounted(stack);
            remaining -= stack.getAmount();
        }
    }

    void taintPlacedBlock(Block block, ItemStack placedItem) {
        if (!isCounted(placedItem) || isGrowthExempt(block)) return;
        block.getChunk().getPersistentDataContainer().set(blockTaintKey(block), PersistentDataType.BYTE, (byte) 1);
    }

    boolean consumeBlockTaint(Block block) {
        PersistentDataContainer chunkPdc = block.getChunk().getPersistentDataContainer();
        NamespacedKey key = blockTaintKey(block);
        if (!chunkPdc.has(key, PersistentDataType.BYTE)) return false;
        chunkPdc.remove(key);
        return true;
    }

    void stripCountedTags(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            removeCountedTag(stack);
        }
        for (ItemStack stack : player.getEnderChest().getContents()) {
            removeCountedTag(stack);
        }
    }

    private boolean isGrowthExempt(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable) return true;
        return switch (block.getType()) {
            case SMALL_AMETHYST_BUD, MEDIUM_AMETHYST_BUD, LARGE_AMETHYST_BUD, AMETHYST_CLUSTER -> true;
            default -> false;
        };
    }

    private NamespacedKey blockTaintKey(Block block) {
        int localX = block.getX() & 0xF;
        int localZ = block.getZ() & 0xF;
        int y = block.getY();
        String encodedY = y < 0 ? "n" + -y : "p" + y;
        return new NamespacedKey(countedKey.getNamespace(), "rh_taint_" + localX + "_" + encodedY + "_" + localZ);
    }

    private void removeCountedTag(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(countedKey, PersistentDataType.BYTE)) return;
        pdc.remove(countedKey);
        stack.setItemMeta(meta);
    }
}
