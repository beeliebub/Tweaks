package me.beeliebub.tweaks.enchantments;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

// Prevents tools with SpawnerPickup or EggCollector enchantments from being used in anvils
public class AnvilListener implements Listener {

    private final SpawnerPickup spawnerPickup;
    private final EggCollector eggCollector;

    public AnvilListener(SpawnerPickup spawnerPickup, EggCollector eggCollector) {
        this.spawnerPickup = spawnerPickup;
        this.eggCollector = eggCollector;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        if (isBlockedTool(inv.getItem(0)) || isBlockedTool(inv.getItem(1))) {
            event.setResult(null);
        }
    }

    private boolean isBlockedTool(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType() == Material.ENCHANTED_BOOK) return false;
        Enchantment sp = spawnerPickup.getEnchantment();
        if (sp != null && item.containsEnchantment(sp)) return true;
        Enchantment ec = eggCollector.getEnchantment();
        if (ec != null && item.containsEnchantment(ec)) return true;
        return false;
    }
}