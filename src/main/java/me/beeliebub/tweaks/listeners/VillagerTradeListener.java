package me.beeliebub.tweaks.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;

// Blocks emeralds carrying lore from being placed in the cost slots of a regular Villager's
// merchant GUI. Wandering Traders (also Merchant + AbstractVillager) are intentionally exempt:
// the instanceof Villager check passes only for the regular villager class — WanderingTrader
// is a sibling type, so its trades are unaffected.
//
// Cost slots are raw 0 and 1 of the MerchantInventory; slot 2 is the result.
public class VillagerTradeListener implements Listener {

    private static final int COST_SLOT_A = 0;
    private static final int COST_SLOT_B = 1;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory mi)) return;
        if (!isRegularVillagerMerchant(mi.getMerchant())) return;

        InventoryAction action = event.getAction();
        int rawSlot = event.getRawSlot();
        boolean topSlotClicked = rawSlot == COST_SLOT_A || rawSlot == COST_SLOT_B;

        switch (action) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> {
                if (topSlotClicked && hasLoreEmerald(event.getCursor())) {
                    rejectLoreEmerald(event);
                }
            }
            case MOVE_TO_OTHER_INVENTORY -> {
                // Shift-click from the bottom inventory routes the item into the cost slots.
                Inventory clicked = event.getClickedInventory();
                if (clicked != null && clicked != mi && hasLoreEmerald(event.getCurrentItem())) {
                    rejectLoreEmerald(event);
                }
            }
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                if (topSlotClicked) {
                    HumanEntity who = event.getWhoClicked();
                    int hotbar = event.getHotbarButton();
                    if (hotbar >= 0 && who instanceof Player p) {
                        ItemStack hotbarItem = p.getInventory().getItem(hotbar);
                        if (hasLoreEmerald(hotbarItem)) {
                            rejectLoreEmerald(event);
                        }
                    }
                }
            }
            default -> { /* nothing else can place a new item into the cost slots */ }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory mi)) return;
        if (!isRegularVillagerMerchant(mi.getMerchant())) return;
        if (!hasLoreEmerald(event.getOldCursor())) return;

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot == COST_SLOT_A || rawSlot == COST_SLOT_B) {
                event.setCancelled(true);
                notifyRejection(event.getWhoClicked());
                return;
            }
        }
    }

    private boolean isRegularVillagerMerchant(Merchant merchant) {
        // Villager (regular) and WanderingTrader are sibling AbstractVillager types — the
        // instanceof check below excludes WanderingTrader by design.
        return merchant instanceof Villager;
    }

    private boolean hasLoreEmerald(ItemStack item) {
        if (item == null || item.getType() != Material.EMERALD) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().hasLore();
    }

    private void rejectLoreEmerald(InventoryClickEvent event) {
        event.setCancelled(true);
        notifyRejection(event.getWhoClicked());
    }

    private void notifyRejection(HumanEntity who) {
        if (!(who instanceof Player player)) return;
        player.sendMessage(Component.text(
                "This villager won't accept emeralds with lore. Try a wandering trader.",
                NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }
}