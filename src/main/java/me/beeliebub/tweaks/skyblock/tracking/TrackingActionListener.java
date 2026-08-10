package me.beeliebub.tweaks.skyblock.tracking;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/** Player action counters, placement taint, and purchased-item propagation. */
public final class TrackingActionListener implements Listener {
    private static final Set<TrackCategory> RECORDED_CATEGORIES = Set.of(
            TrackCategory.PLACE, TrackCategory.CRAFT, TrackCategory.ENCHANT);
    private final JavaPlugin plugin;
    private final IslandManager islands;
    private final IslandTracker tracker;
    private final TrackingScope scope;
    private final SkyblockPdc pdc;

    public TrackingActionListener(JavaPlugin plugin, IslandManager islands, IslandTracker tracker,
                                  TrackingScope scope, SkyblockPdc pdc) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.islands = Objects.requireNonNull(islands, "islands");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.pdc = Objects.requireNonNull(pdc, "pdc");
    }

    public static Set<TrackCategory> recordedCategories() {
        return RECORDED_CATEGORIES;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Island island = islands.islandAt(block.getLocation()).orElse(null);
        if (island == null) return;
        ItemStack item = event.getItemInHand();
        tracker.record(island, new TrackKey(TrackCategory.PLACE, block.getType().name()), 1, item);
        if (pdc.isPurchased(item)) {
            pdc.markPurchased(block);
        } else if (scope.taintMaterials(island).contains(block.getType()) && !pdc.growthExempt(block)) {
            pdc.add(block.getChunk(), block.getType(), block.getX() & 15, block.getY(), block.getZ() & 15);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Island island = islands.islandAt(player.getLocation()).orElse(null);
        if (island == null || event.getRecipe() == null) return;
        ItemStack result = event.getRecipe().getResult().clone();
        ItemStack[] matrix = event.getInventory().getMatrix();
        pdc.propagatePurchased(result, Arrays.asList(matrix));
        if (pdc.isPurchased(result) && countUnmarked(player, result.getType()) > 0) {
            event.setCancelled(true);
            player.sendMessage(me.beeliebub.tweaks.core.Messages.SKYBLOCK.invalidInput(
                    "move ordinary " + result.getType().name() + " stacks before crafting purchased items"));
            return;
        }
        ItemStack[] beforeContents = cloneContents(player.getInventory().getContents());
        ItemStack beforeCursor = cloneOrNull(player.getOpenInventory().getCursor());
        int before = countUnmarked(player, result.getType());
        Bukkit.getScheduler().runTask(plugin, () -> {
            int crafted = Math.max(0, countUnmarked(player, result.getType()) - before);
            if (crafted > 0 && !pdc.isPurchased(result)) tracker.record(island,
                    new TrackKey(TrackCategory.CRAFT, result.getType().name()), crafted);
            if (pdc.isPurchased(result)) tagInserted(player, result.getType(), beforeContents, beforeCursor);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        ItemStack result = event.getResult();
        if (pdc.isPurchased(event.getSource())) pdc.markPurchased(result);
        event.setResult(result);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Island island = islands.islandAt(event.getEnchanter().getLocation()).orElse(null);
        if (island == null) return;
        tracker.record(island, new TrackKey(TrackCategory.ENCHANT, event.getItem().getType().name()), 1,
                event.getItem());
    }

    public void propagateCraftResult(ItemStack result, ItemStack[] matrix) {
        pdc.propagatePurchased(result, Arrays.asList(matrix));
    }

    private int countUnmarked(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material && !pdc.isCounted(stack)) count += stack.getAmount();
        }
        ItemStack cursor = player.getOpenInventory().getCursor();
        if (cursor != null && cursor.getType() == material && !pdc.isCounted(cursor)) count += cursor.getAmount();
        return count;
    }

    private void tagInserted(Player player, Material material, ItemStack[] beforeContents,
                             ItemStack beforeCursor) {
        ItemStack[] afterContents = player.getInventory().getContents();
        for (int slot = 0; slot < afterContents.length; slot++) {
            ItemStack after = afterContents[slot];
            ItemStack before = slot < beforeContents.length ? beforeContents[slot] : null;
            if (after == null || after.getType() != material) continue;
            int beforeAmount = before != null && before.getType() == material ? before.getAmount() : 0;
            if (after.getAmount() > beforeAmount) pdc.markPurchased(after);
        }
        ItemStack cursor = player.getOpenInventory().getCursor();
        if (cursor != null && cursor.getType() == material
                && (beforeCursor == null || beforeCursor.getType() != material
                || cursor.getAmount() > beforeCursor.getAmount())) pdc.markPurchased(cursor);
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = cloneOrNull(contents[i]);
        return copy;
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }
}
