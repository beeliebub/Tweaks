package me.beeliebub.tweaks.tools;

import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/** Owns all plugin-side anvil, grindstone, and augmented repair-grid lockouts. */
public final class AnvilLockoutListener implements Listener {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final QualityRegistry qualityRegistry;

    public AnvilLockoutListener(org.bukkit.plugin.java.JavaPlugin plugin, QualityRegistry qualityRegistry) {
        this.plugin = plugin;
        this.qualityRegistry = qualityRegistry;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || player.hasPermission(Permissions.ANVIL_BYPASS)) return;
        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.ANVIL && plugin.getConfig().getBoolean("tools.anvil.lockout-enabled", true)) {
            event.setCancelled(true);
            player.sendMessage(Messages.TOOLS.anvilLocked());
        } else if (type == InventoryType.GRINDSTONE
                && plugin.getConfig().getBoolean("tools.anvil.grindstone-lockout-enabled", true)) {
            event.setCancelled(true);
            player.sendMessage(Messages.TOOLS.grindstoneLocked());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!craftingGridLockoutEnabled(event.getView().getPlayer())) return;
        if (isAugmentedToolRepair(event.getInventory().getMatrix())) event.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!craftingGridLockoutEnabled(event.getWhoClicked())) return;
        if (isAugmentedToolRepair(event.getInventory().getMatrix())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) player.sendMessage(Messages.TOOLS.anvilLocked());
        }
    }

    private boolean craftingGridLockoutEnabled(org.bukkit.entity.HumanEntity human) {
        return human instanceof Player player
                && !player.hasPermission(Permissions.ANVIL_BYPASS)
                && plugin.getConfig().getBoolean("tools.anvil.lockout-enabled", true);
    }

    private boolean isAugmentedToolRepair(ItemStack[] matrix) {
        if (matrix == null || matrix.length == 0) return false;
        ItemStack first = null;
        ItemStack second = null;
        int count = 0;
        for (ItemStack item : matrix) {
            if (item == null || item.isEmpty()) continue;
            if (++count == 1) first = item;
            else if (count == 2) second = item;
            else return false;
        }
        if (first == null || second == null || first.getType() != second.getType()) return false;
        if (!(first.getItemMeta() instanceof Damageable firstDamage)
                || !(second.getItemMeta() instanceof Damageable secondDamage)) return false;
        if (firstDamage.getDamage() <= 0 && secondDamage.getDamage() <= 0) return false;
        return hasAugmentOrQuality(first) || hasAugmentOrQuality(second);
    }

    private boolean hasAugmentOrQuality(ItemStack item) {
        if (AugmentLedger.hasLedger(item)) return true;
        return qualityRegistry != null && item.getEnchantments().keySet().stream()
                .anyMatch(enchantment -> qualityRegistry.getTier(enchantment) != null);
    }
}
