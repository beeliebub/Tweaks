package me.beeliebub.tweaks.tools.durability;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;

/** Stamps damageable items lazily and enforces the never-break primary-use floor. */
public final class DurabilityListener implements Listener {

    private final DurabilityService service;
    private final org.bukkit.plugin.java.JavaPlugin plugin;

    public DurabilityListener(org.bukkit.plugin.java.JavaPlugin plugin, DurabilityService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        service.ensureStamped(item);
        if (neverBreak() && service.isSpent(item)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) return;
        ItemStack item = event.getItem();
        service.ensureStamped(item);
        if (neverBreak() && service.isSpent(item)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        service.ensureStamped(item);
        if (neverBreak() && service.isSpent(item)) {
            // A spent weapon loses its weapon damage but still behaves like an empty hand.
            event.setDamage(1.0);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        service.ensureStamped(item);
        if (neverBreak() && service.isSpent(item)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        service.ensureStamped(item);
        if (neverBreak() && service.isSpent(item)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack elytra = player.getInventory().getChestplate();
        service.ensureStamped(elytra);
        if (neverBreak() && service.isSpent(elytra)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (!service.ensureStamped(item) || !neverBreak()) return;
        var meta = item.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.Damageable damageable)) return;
        int max = service.maxDamage(item);
        if (damageable.getDamage() >= max - 1) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(Math.min(event.getDamage(), Math.max(0, max - 1 - damageable.getDamage())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        service.ensureStamped(event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) service.ensureStamped(event.getItem().getItemStack());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        service.ensureStamped(event.getCurrentItem());
        service.ensureStamped(event.getCursor());
        service.ensureStamped(player.getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArmorDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        boolean spent = false;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            service.ensureStamped(armor);
            if (service.isSpent(armor)) spent = true;
        }
        if (neverBreak() && spent && event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0.0);
        }
    }

    private boolean neverBreak() {
        return plugin.getConfig().getBoolean("tools.never-break.enabled", true);
    }
}
