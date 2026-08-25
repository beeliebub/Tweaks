package me.beeliebub.tweaks.tools.cash;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.ItemStack;

/** Converts cash at player-inventory ingress points without sweeping unrelated slots. */
public final class CashItemListener implements Listener {

    private final Tweaks plugin;
    private final CashItemService service;

    public CashItemListener(Tweaks plugin, CashItemService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        long total = service.totalValue(stack).orElse(0L);
        CashItemService.Result result = service.convert(stack, player.getUniqueId());
        if (result == CashItemService.Result.APPLIED) {
            event.setCancelled(true);
            event.getItem().remove();
            player.sendMessage(Messages.TOOLS.cashConverted(total));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int rawSlot = event.getRawSlot();
        ItemStack before = event.getCurrentItem() == null ? null : event.getCurrentItem().clone();
        if (rawSlot >= 0 && rawSlot < player.getOpenInventory().getTopInventory().getSize()
                && player.getOpenInventory().getTopInventory().getType() == InventoryType.MERCHANT
                && rawSlot == 2) {
            ItemStack result = event.getCurrentItem();
            long total = service.totalValue(result).orElse(0L);
            if (service.convert(result, player.getUniqueId()) == CashItemService.Result.APPLIED) {
                event.setCancelled(true);
                player.sendMessage(Messages.TOOLS.cashConverted(total));
            }
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (rawSlot >= player.getOpenInventory().getTopInventory().getSize()) {
                int playerSlot = rawSlot - player.getOpenInventory().getTopInventory().getSize();
                if (playerSlot >= 0 && playerSlot < player.getInventory().getSize()) {
                    convertSlot(player, player.getInventory().getItem(playerSlot));
                }
            } else if (rawSlot >= 0 && before != null) {
                convertMatching(player, before);
            }
            convertCursor(player);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Integer rawSlot : event.getRawSlots()) {
                if (rawSlot < player.getOpenInventory().getTopInventory().getSize()) {
                    convertSlot(player, player.getOpenInventory().getItem(rawSlot));
                } else {
                    int slot = rawSlot - player.getOpenInventory().getTopInventory().getSize();
                    if (slot >= 0 && slot < player.getInventory().getSize()) convertSlot(player, player.getInventory().getItem(slot));
                }
            }
            convertCursor(player);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!(event.getDestination().getHolder() instanceof Player player)) return;
        ItemStack moving = event.getItem();
        long total = service.totalValue(moving).orElse(0L);
        if (service.convert(moving, player.getUniqueId()) == CashItemService.Result.APPLIED) {
            event.setCancelled(true);
            player.sendMessage(Messages.TOOLS.cashConverted(total));
        }
    }

    private void convertSlot(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        long total = service.totalValue(stack).orElse(0L);
        CashItemService.Result result = service.convert(stack, player.getUniqueId());
        if (result == CashItemService.Result.APPLIED) player.sendMessage(Messages.TOOLS.cashConverted(total));
    }

    private void convertCursor(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor == null || cursor.isEmpty()) return;
        long total = service.totalValue(cursor).orElse(0L);
        CashItemService.Result result = service.convert(cursor, player.getUniqueId());
        if (result == CashItemService.Result.APPLIED) {
            player.setItemOnCursor(null);
            player.sendMessage(Messages.TOOLS.cashConverted(total));
        }
    }

    private void convertMatching(Player player, ItemStack expected) {
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.isSimilar(expected)) convertSlot(player, stack);
        }
    }
}
