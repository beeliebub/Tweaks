package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;

/** Converts enchanted books entering player inventory into individual augment gems. */
public final class BookConversionListener implements Listener {

    private final Tweaks plugin;
    private final AugmentService augments;

    public BookConversionListener(Tweaks plugin, AugmentService augments) {
        this.plugin = plugin;
        this.augments = augments;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (convert(player, event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int rawSlot = event.getRawSlot();
        ItemStack before = event.getCurrentItem() == null ? null : event.getCurrentItem().clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack stack = rawSlot >= 0 && rawSlot < player.getOpenInventory().getTopInventory().getSize()
                    ? player.getOpenInventory().getItem(rawSlot)
                    : rawSlot >= player.getOpenInventory().getTopInventory().getSize()
                    ? player.getInventory().getItem(rawSlot - player.getOpenInventory().getTopInventory().getSize()) : null;
            if (stack != null) convert(player, stack);
            if (before != null && rawSlot < player.getOpenInventory().getTopInventory().getSize()) {
                convertMatching(player, before);
            }
            convertCursor(player);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int topSize = player.getOpenInventory().getTopInventory().getSize();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Integer rawSlot : event.getRawSlots()) {
                if (rawSlot >= topSize) {
                    int playerSlot = rawSlot - topSize;
                    if (playerSlot >= 0 && playerSlot < player.getInventory().getSize()) {
                        convert(player, player.getInventory().getItem(playerSlot));
                    }
                }
            }
            convertCursor(player);
        });
    }

    private void convertMatching(Player player, ItemStack expected) {
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.isSimilar(expected)) {
                convert(player, stack);
            }
        }
    }

    private void convertCursor(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.isEmpty() && convert(player, cursor)) {
            player.setItemOnCursor(null);
        }
    }

    public boolean convert(Player player, ItemStack book) {
        if (!plugin.getConfig().getBoolean("tools.augments.enabled", true)
                || !plugin.getConfig().getBoolean("tools.augments.convert-enchanted-books", true)) return false;
        if (book == null || book.getType() != org.bukkit.Material.ENCHANTED_BOOK
                || !(book.getItemMeta() instanceof EnchantmentStorageMeta storage)) return false;
        List<ItemStack> gems = new ArrayList<>();
        storage.getStoredEnchants().forEach((enchantment, level) -> gems.add(augments.gemItem().create(enchantment, level)));
        int sourceAmount = Math.max(1, book.getAmount());
        for (ItemStack gem : gems) gem.setAmount(sourceAmount);
        if (gems.isEmpty()) return false;
        if (!augments.canFit(player, gems)) {
            player.sendMessage(Messages.TOOLS.inventoryFull());
            return false;
        }
        book.setAmount(0);
        augments.addGems(player, gems);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!(event.getDestination().getHolder() instanceof Player player)) return;
        if (convert(player, event.getItem())) event.setCancelled(true);
    }
}
