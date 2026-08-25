package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.UUID;

/** Establishes augment state on newly delivered damageable crafting, smithing, and trade results. */
public final class AugmentCraftListener implements Listener {

    private final Tweaks plugin;
    private final AugmentService augments;
    private final NamespacedKey deliveryKey;
    private final Map<UUID, TradeSnapshot> tradeSnapshots = new HashMap<>();

    public AugmentCraftListener(Tweaks plugin, AugmentService augments) {
        this.plugin = plugin;
        this.augments = augments;
        this.deliveryKey = new NamespacedKey(plugin, "augment_result_delivery");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockAugmentGemPreview(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        if (containsAugmentGem(inventory.getMatrix())) inventory.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockAugmentGemCraft(CraftItemEvent event) {
        if (containsAugmentGem(event.getInventory().getMatrix())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void captureTradeState(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() != 2) return;
        InventoryView view = event.getView();
        if (view == null) return;
        Inventory top = view.getTopInventory();
        if (top == null || top.getType() != InventoryType.MERCHANT) return;
        TradeSnapshot snapshot = new TradeSnapshot(
                copyContents(player.getInventory().getStorageContents()), copy(player.getItemOnCursor()));
        UUID playerId = player.getUniqueId();
        tradeSnapshots.put(playerId, snapshot);
        plugin.getServer().getScheduler().runTask(plugin,
                () -> tradeSnapshots.remove(playerId, snapshot));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !augments.enabled()) return;
        ItemStack result = event.getInventory().getResult();
        if (!isDamageableResult(result)) return;
        String deliveryToken = markDelivery(result);
        if (deliveryToken == null) return;
        deferDelta(player, result.getType(), deliveryToken);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmith(SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !augments.enabled()) return;
        if (!(event.getInventory() instanceof SmithingInventory inventory)) return;
        ItemStack result = inventory.getResult();
        if (!isDamageableResult(result)) return;
        deferDelta(player, result.getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        if (!augments.enabled()) return;
        ItemStack result = event.getTrade().getResult();
        if (!isDamageableResult(result)) return;
        Player player = event.getPlayer();
        TradeSnapshot before = tradeSnapshots.remove(player.getUniqueId());
        if (before == null) {
            deferDelta(player, result.getType());
        } else {
            deferDelta(player, result.getType(), before);
        }
    }

    private void deferDelta(Player player, Material resultMaterial) {
        deferDelta(player, resultMaterial,
                new TradeSnapshot(copyContents(player.getInventory().getStorageContents()),
                        copy(player.getItemOnCursor())));
    }

    private void deferDelta(Player player, Material resultMaterial, TradeSnapshot before) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (!plugin.isEnabled() || !player.isOnline() || !augments.enabled()) return;
                stampStorageDelta(player, resultMaterial, before.storage());
                stampCursorDelta(player, resultMaterial, before.cursor());
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to establish augment state on a crafted item", failure);
            }
        });
    }

    private void deferDelta(Player player, Material resultMaterial, String deliveryToken) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (!plugin.isEnabled() || !player.isOnline() || !augments.enabled()) return;
                stampCraftStorage(player, resultMaterial, deliveryToken);
                stampCraftCursor(player, resultMaterial, deliveryToken);
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

    private void stampCraftStorage(Player player, Material resultMaterial, String deliveryToken) {
        ItemStack[] current = player.getInventory().getStorageContents();
        for (int slot = 0; slot < current.length; slot++) {
            ItemStack candidate = current[slot];
            if (!isResult(candidate, resultMaterial) || !hasDelivery(candidate, deliveryToken)) continue;
            augments.initializeCraftedItem(candidate);
            clearDelivery(candidate, deliveryToken);
            player.getInventory().setItem(slot, candidate);
        }
    }

    private void stampCraftCursor(Player player, Material resultMaterial, String deliveryToken) {
        ItemStack candidate = player.getItemOnCursor();
        if (isResult(candidate, resultMaterial) && hasDelivery(candidate, deliveryToken)) {
            augments.initializeCraftedItem(candidate);
            clearDelivery(candidate, deliveryToken);
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

    private boolean containsAugmentGem(ItemStack[] matrix) {
        if (matrix == null) return false;
        for (ItemStack item : matrix) {
            if (augments.gemItem().isGem(item)) return true;
        }
        return false;
    }

    private static ItemStack[] copyContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = copy(contents[i]);
        return copy;
    }

    private static ItemStack copy(ItemStack item) {
        return item == null || item.isEmpty() || item.getType().isAir() ? null : item.clone();
    }

    private record TradeSnapshot(ItemStack[] storage, ItemStack cursor) {
    }

    private String markDelivery(ItemStack item) {
        String token = UUID.randomUUID().toString();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        meta.getPersistentDataContainer().set(deliveryKey, PersistentDataType.STRING, token);
        item.setItemMeta(meta);
        return token;
    }

    private boolean hasDelivery(ItemStack item, String token) {
        if (item == null || item.isEmpty() || token == null || !item.hasItemMeta()) return false;
        try {
            return token.equals(item.getItemMeta().getPersistentDataContainer()
                    .get(deliveryKey, PersistentDataType.STRING));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private void clearDelivery(ItemStack item, String token) {
        if (!hasDelivery(item, token)) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(deliveryKey);
        item.setItemMeta(meta);
    }
}
