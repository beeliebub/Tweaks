package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Diverts enchanting-table results after vanilla has charged the player. */
public final class AugmentTableListener implements Listener {

    private final Tweaks plugin;
    private final AugmentService augments;

    public AugmentTableListener(Tweaks plugin, AugmentService augments) {
        this.plugin = plugin;
        this.augments = augments;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (!augments.enabled() || event.getEnchantsToAdd() == null || event.getEnchantsToAdd().isEmpty()) return;
        if (!(event.getInventory() instanceof EnchantingInventory enchantingInventory)) return;
        Map<Enchantment, Integer> ordered = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : event.getEnchantsToAdd().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                ordered.put(entry.getKey(), entry.getValue());
            }
        }
        Map<Enchantment, Integer> snapshot = Collections.unmodifiableMap(ordered);
        if (snapshot.isEmpty()) return;
        Player player = event.getEnchanter();
        AugmentService.GemBatchResult prepared = augments.prepareTableGems(
                snapshot, java.util.concurrent.ThreadLocalRandom.current());
        if (prepared.refused() || prepared.gems().isEmpty() || !augments.canFit(player, prepared.gems())) {
            event.setCancelled(true);
            player.sendMessage(me.beeliebub.tweaks.core.Messages.TOOLS.augmentTableInventoryFull());
            return;
        }
        List<ItemStack> preparedGems = prepared.gems();
        InventoryView view = player.getOpenInventory();
        ItemStack sourceSnapshot = event.getItem() == null ? null : event.getItem().clone();
        if (sourceSnapshot == null || sourceSnapshot.isEmpty()) {
            return;
        }
        Map<Enchantment, Integer> preExisting = Collections.unmodifiableMap(
                new LinkedHashMap<>(sourceSnapshot.getEnchantments()));
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (!plugin.isEnabled() || !player.isOnline() || !augments.enabled()) return;
                if (player.getOpenInventory() != view
                        || player.getOpenInventory().getTopInventory() != enchantingInventory) return;
                ItemStack stack = enchantingInventory.getItem();
                boolean fromEnchantingInventory = stack != null && !stack.isEmpty()
                        && matchesSource(stack, sourceSnapshot, snapshot);
                if (!fromEnchantingInventory) {
                    stack = player.getItemOnCursor();
                    fromEnchantingInventory = false;
                }
                if (stack == null || stack.isEmpty()
                        || !matchesSource(stack, sourceSnapshot, snapshot)) return;
                boolean normalizePlainBook = sourceSnapshot.getType() == Material.BOOK
                        && stack.getType() == Material.ENCHANTED_BOOK;
                ItemStack converted = augments.stripTableEnchantmentsResult(player, stack, snapshot,
                        preExisting, normalizePlainBook, preparedGems);
                if (converted == null) {
                    player.sendMessage(me.beeliebub.tweaks.core.Messages.TOOLS.augmentTableConversionFailed());
                    return;
                }
                if (fromEnchantingInventory) enchantingInventory.setItem(converted);
                else player.setItemOnCursor(converted);
                player.updateInventory();
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to convert an enchanting-table result into augment gems", failure);
                player.sendMessage(me.beeliebub.tweaks.core.Messages.TOOLS.augmentTableConversionFailed());
            }
        });
    }

    public boolean strip(Player player, ItemStack stack, Map<Enchantment, Integer> snapshot) {
        return augments.stripTableEnchantments(player, stack, snapshot);
    }

    private static boolean matchesSnapshot(ItemStack stack, Map<Enchantment, Integer> snapshot) {
        if (stack == null || stack.isEmpty() || snapshot == null || snapshot.isEmpty()) return false;
        for (Map.Entry<Enchantment, Integer> entry : snapshot.entrySet()) {
            if (stack.getEnchantmentLevel(entry.getKey()) != entry.getValue()) return false;
        }
        return true;
    }

    private static boolean matchesSource(ItemStack candidate, ItemStack source,
                                         Map<Enchantment, Integer> snapshot) {
        if (candidate == null || source == null || candidate.getAmount() != source.getAmount()) return false;
        if (source.getType() == Material.BOOK && candidate.getType() == Material.ENCHANTED_BOOK) {
            return matchesStoredBookSource(candidate, source, snapshot);
        }
        if (!matchesSnapshot(candidate, snapshot)) return false;
        ItemStack expected = source.clone();
        for (Map.Entry<Enchantment, Integer> entry : snapshot.entrySet()) {
            expected.addUnsafeEnchantment(entry.getKey(), entry.getValue());
        }
        return candidate.isSimilar(expected);
    }

    private static boolean matchesStoredBookSource(ItemStack candidate, ItemStack source,
                                                    Map<Enchantment, Integer> snapshot) {
        if (!(candidate.getItemMeta() instanceof EnchantmentStorageMeta storage)
                || !storage.getStoredEnchants().equals(snapshot)) return false;
        ItemStack comparable = candidate.clone();
        EnchantmentStorageMeta clean = (EnchantmentStorageMeta) comparable.getItemMeta();
        for (Enchantment enchantment : new java.util.HashSet<>(clean.getStoredEnchants().keySet())) {
            clean.removeStoredEnchant(enchantment);
        }
        comparable.setItemMeta(clean);
        comparable = comparable.withType(Material.BOOK);
        return comparable.isSimilar(source);
    }
}
