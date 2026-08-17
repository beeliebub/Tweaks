package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

import java.util.logging.Level;

/** Establishes augment state on newly delivered damageable crafting and smithing results. */
public final class AugmentCraftListener implements Listener {

    private final Tweaks plugin;
    private final AugmentService augments;

    public AugmentCraftListener(Tweaks plugin, AugmentService augments) {
        this.plugin = plugin;
        this.augments = augments;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !augments.enabled()) return;
        if (event.getRecipe() == null) return;
        ItemStack result = event.getRecipe().getResult();
        if (!isDamageableResult(result)) return;
        deferDelta(player, result.getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmith(SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !augments.enabled()) return;
        if (!(event.getInventory() instanceof SmithingInventory inventory)) return;
        ItemStack result = inventory.getResult();
        if (!isDamageableResult(result)) return;
        deferDelta(player, result.getType());
    }

    private void deferDelta(Player player, Material resultMaterial) {
        ItemStack[] storageBefore = copyContents(player.getInventory().getStorageContents());
        ItemStack cursorBefore = copy(player.getItemOnCursor());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (!plugin.isEnabled() || !player.isOnline() || !augments.enabled()) return;
                stampStorageDelta(player, resultMaterial, storageBefore);
                stampCursorDelta(player, resultMaterial, cursorBefore);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to establish augment state on a crafted item", failure);
            }
        });
    }

    private void stampStorageDelta(Player player, Material resultMaterial, ItemStack[] before) {
        ItemStack[] current = player.getInventory().getStorageContents();
        int count = Math.min(before.length, current.length);
        for (int slot = 0; slot < count; slot++) {
            ItemStack candidate = current[slot];
            if (!isResult(candidate, resultMaterial) || !newlyDelivered(before[slot], candidate)) continue;
            augments.initializeCraftedItem(candidate);
            player.getInventory().setItem(slot, candidate);
        }
    }

    private void stampCursorDelta(Player player, Material resultMaterial, ItemStack before) {
        ItemStack candidate = player.getItemOnCursor();
        if (isResult(candidate, resultMaterial) && newlyDelivered(before, candidate)) {
            augments.initializeCraftedItem(candidate);
            player.setItemOnCursor(candidate);
        }
    }

    private static boolean newlyDelivered(ItemStack before, ItemStack current) {
        if (current == null || current.isEmpty()) return false;
        if (before == null || before.isEmpty()) return true;
        return before.isSimilar(current) && current.getAmount() > before.getAmount();
    }

    private static boolean isDamageableResult(ItemStack result) {
        return result != null && !result.isEmpty() && result.getType().getMaxDurability() > 0;
    }

    private static boolean isResult(ItemStack item, Material material) {
        return item != null && !item.isEmpty() && item.getType() == material
                && item.getType().getMaxDurability() > 0;
    }

    private static ItemStack[] copyContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = copy(contents[i]);
        return copy;
    }

    private static ItemStack copy(ItemStack item) {
        return item == null || item.isEmpty() || item.getType().isAir() ? null : item.clone();
    }
}
