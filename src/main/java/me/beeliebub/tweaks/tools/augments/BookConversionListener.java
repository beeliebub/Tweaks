package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

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
        if (!(event.getWhoClicked() instanceof Player player) || !conversionEnabled()) return;
        InventoryView view = player.getOpenInventory();
        Inventory top = view.getTopInventory();
        Inventory bottom = player.getInventory();
        int rawSlot = event.getRawSlot();
        ItemStack clicked = copy(event.getCurrentItem());
        ItemStack cursorBefore = copy(event.getCursor());
        int hotbarButton = event.getHotbarButton();
        ItemStack hotbarBefore = hotbarButton < 0 ? null : copy(bottom.getItem(hotbarButton));
        boolean moveToOther = event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY;
        if (!isPotentialBook(clicked) && !isPotentialBook(cursorBefore) && !isPotentialBook(hotbarBefore)) return;
        ItemStack[] bottomBefore = copyContents(bottom);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                handleClick(player, view, top, bottom, rawSlot, clicked, cursorBefore, hotbarBefore,
                        bottomBefore, moveToOther);
            } catch (RuntimeException failure) {
                reportDeferredFailure(player, "click", failure);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !conversionEnabled()) return;
        InventoryView view = player.getOpenInventory();
        Inventory top = view.getTopInventory();
        Inventory bottom = player.getInventory();
        ItemStack oldCursor = copy(event.getOldCursor());
        Map<Integer, ItemStack> expected = new java.util.HashMap<>();
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            expected.put(entry.getKey(), copy(entry.getValue()));
        }
        if (!isPotentialBook(oldCursor) && expected.values().stream().noneMatch(this::isPotentialBook)) return;
        ItemStack[] bottomBefore = copyContents(bottom);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                handleDrag(player, view, top, bottom, oldCursor, expected, bottomBefore);
            } catch (RuntimeException failure) {
                reportDeferredFailure(player, "drag", failure);
            }
        });
    }

    private void handleClick(Player player, InventoryView view, Inventory top, Inventory bottom, int rawSlot,
                              ItemStack clicked, ItemStack cursorBefore, ItemStack hotbarBefore,
                              ItemStack[] bottomBefore, boolean moveToOther) {
        if (!sameView(player, view, top, bottom)) return;
        int topSize = top.getSize();
        if (moveToOther && rawSlot >= 0 && rawSlot < topSize && isPotentialBook(clicked)) {
            ItemStack sourceAfter = top.getItem(rawSlot);
            int moved = movedFromSource(clicked, sourceAfter);
            if (moved <= 0) return;
            List<InventoryClaim> claims = claimsForDelta(bottomBefore, bottom, clicked);
            if (claims.isEmpty() || totalAmount(claims) != moved) return;
            convertClaims(player, claims, null);
            return;
        }

        List<InventoryClaim> claims = List.of();
        ItemStack source = null;
        if (isPotentialBook(cursorBefore)) source = cursorBefore;
        else if (isPotentialBook(clicked)) source = clicked;
        else if (isPotentialBook(hotbarBefore)) source = hotbarBefore;
        if (source != null) claims = claimsForDelta(bottomBefore, bottom, source);

        CursorClaim cursorClaim = null;
        ItemStack cursorAfter = copy(player.getItemOnCursor());
        if (source != null && isPotentialBook(cursorAfter) && sameStack(cursorAfter, source)) {
            int existing = isPotentialBook(cursorBefore) && sameStack(cursorBefore, source)
                    ? cursorBefore.getAmount() : 0;
            int gained = cursorAfter.getAmount() - existing;
            if (gained > 0) cursorClaim = new CursorClaim(cursorAfter, gained);
            else if (isPotentialBook(cursorBefore) && cursorAfter.getAmount() > 0) {
                cursorClaim = new CursorClaim(cursorAfter, cursorAfter.getAmount());
            }
        }
        if (!claims.isEmpty() || cursorClaim != null) convertClaims(player, claims, cursorClaim);
    }

    private void handleDrag(Player player, InventoryView view, Inventory top, Inventory bottom, ItemStack oldCursor,
                            Map<Integer, ItemStack> expected, ItemStack[] bottomBefore) {
        if (!sameView(player, view, top, bottom)) return;
        int topSize = top.getSize();
        List<InventoryClaim> claims = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> entry : expected.entrySet()) {
            int rawSlot = entry.getKey();
            ItemStack expectedStack = entry.getValue();
            if (rawSlot < topSize || !isPotentialBook(expectedStack)) continue;
            int slot = rawSlot - topSize;
            if (slot < 0 || slot >= bottom.getSize()) return;
            ItemStack current = bottom.getItem(slot);
            if (!sameStack(current, expectedStack) || current.getAmount() != expectedStack.getAmount()) return;
            ItemStack before = bottomBefore[slot];
            int previous = sameStack(before, expectedStack) ? before.getAmount() : 0;
            int added = expectedStack.getAmount() - previous;
            if (added > 0) claims.add(new InventoryClaim(bottom, slot, expectedStack, added));
        }

        CursorClaim cursorClaim = null;
        ItemStack cursorAfter = copy(player.getItemOnCursor());
        if (isPotentialBook(oldCursor) && isPotentialBook(cursorAfter) && sameStack(oldCursor, cursorAfter)) {
            cursorClaim = new CursorClaim(cursorAfter, cursorAfter.getAmount());
        }
        if (cursorClaim != null && totalAmount(claims) + cursorClaim.amount() > oldCursor.getAmount()) return;
        if (!claims.isEmpty() || cursorClaim != null) convertClaims(player, claims, cursorClaim);
    }

    private List<InventoryClaim> claimsForDelta(ItemStack[] before, Inventory inventory, ItemStack source) {
        List<InventoryClaim> claims = new ArrayList<>();
        int count = Math.min(before.length, inventory.getSize());
        for (int slot = 0; slot < count; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (!isPotentialBook(current) || !sameStack(current, source)) continue;
            ItemStack previous = before[slot];
            int previousAmount = sameStack(previous, source) ? previous.getAmount() : 0;
            int added = current.getAmount() - previousAmount;
            if (added > 0) claims.add(new InventoryClaim(inventory, slot, current.clone(), added));
        }
        return claims;
    }

    private void convertClaims(Player player, List<InventoryClaim> claims, CursorClaim cursorClaim) {
        if (!conversionEnabled()) return;
        for (InventoryClaim claim : claims) {
            if (!validClaim(claim)) return;
        }
        List<ItemStack> gems = new ArrayList<>();
        for (InventoryClaim claim : claims) {
            AugmentService.GemBatchResult result = gemsFor(claim.expected(), claim.amount());
            if (result.refused()) {
                reportConversionRefusal(player);
                return;
            }
            gems.addAll(result.gems());
        }
        if (cursorClaim != null) {
            ItemStack cursor = player.getItemOnCursor();
            if (!isPotentialBook(cursor) || !sameStack(cursor, cursorClaim.expected())
                    || cursor.getAmount() < cursorClaim.amount()) return;
            AugmentService.GemBatchResult result = gemsFor(cursorClaim.expected(), cursorClaim.amount());
            if (result.refused()) {
                reportConversionRefusal(player);
                return;
            }
            gems.addAll(result.gems());
        }
        if (gems.isEmpty()) return;
        List<ItemStack> removed = new ArrayList<>();
        for (InventoryClaim claim : claims) {
            ItemStack source = claim.expected().clone();
            source.setAmount(claim.amount());
            removed.add(source);
        }
        if (!augments.canFit(player, gems, removed)) {
            player.sendMessage(Messages.TOOLS.inventoryFull());
            return;
        }
        for (InventoryClaim claim : claims) {
            ItemStack current = claim.inventory().getItem(claim.slot());
            int remaining = current.getAmount() - claim.amount();
            claim.inventory().setItem(claim.slot(), remaining <= 0 ? null : withAmount(current, remaining));
        }
        if (cursorClaim != null) {
            ItemStack current = player.getItemOnCursor();
            int remaining = current.getAmount() - cursorClaim.amount();
            player.setItemOnCursor(remaining <= 0 ? null : withAmount(current, remaining));
        }
        augments.addGems(player, gems);
    }

    private boolean validClaim(InventoryClaim claim) {
        ItemStack current = claim.inventory().getItem(claim.slot());
        return claim.amount() > 0 && isPotentialBook(current)
                && sameStack(current, claim.expected()) && current.getAmount() >= claim.amount();
    }

    private AugmentService.GemBatchResult gemsFor(ItemStack book, int amount) {
        if (!isPotentialBook(book) || amount <= 0) return AugmentService.GemBatchResult.empty();
        EnchantmentStorageMeta storage = (EnchantmentStorageMeta) book.getItemMeta();
        AugmentService.GemBatchResult result = augments.createGemBatchResult(storage.getStoredEnchants(),
                ThreadLocalRandom.current());
        if (result.refused() || result.gems().isEmpty()) return result;
        List<ItemStack> gems = new ArrayList<>(result.gems());
        for (ItemStack gem : gems) gem.setAmount(amount);
        return new AugmentService.GemBatchResult(gems, result.failure());
    }

    private int movedFromSource(ItemStack before, ItemStack after) {
        if (after == null || after.isEmpty()) return before.getAmount();
        if (!sameStack(before, after) || after.getAmount() >= before.getAmount()) return -1;
        return before.getAmount() - after.getAmount();
    }

    private int totalAmount(List<InventoryClaim> claims) {
        return claims.stream().mapToInt(InventoryClaim::amount).sum();
    }

    private boolean sameView(Player player, InventoryView view, Inventory top, Inventory bottom) {
        return player.getOpenInventory() == view
                && player.getOpenInventory().getTopInventory() == top
                && player.getInventory() == bottom;
    }

    private static ItemStack[] copyContents(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) contents[i] = copy(contents[i]);
        return contents;
    }

    private static ItemStack copy(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }

    private static ItemStack withAmount(ItemStack item, int amount) {
        ItemStack replacement = item.clone();
        replacement.setAmount(amount);
        return replacement;
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        return left != null && !left.isEmpty() && right != null && !right.isEmpty()
                && left.isSimilar(right);
    }

    private void reportDeferredFailure(Player player, String operation, RuntimeException failure) {
        plugin.getLogger().log(Level.WARNING,
                "Failed to convert an enchanted book after an inventory " + operation, failure);
        if (player.isOnline()) player.sendMessage(Messages.TOOLS.augmentBookConversionFailed());
    }

    private void reportConversionRefusal(Player player) {
        if (player.isOnline()) player.sendMessage(Messages.TOOLS.augmentBookConversionFailed());
    }

    private boolean conversionEnabled() {
        return plugin.getConfig().getBoolean("tools.augments.enabled", true)
                && plugin.getConfig().getBoolean("tools.augments.convert-enchanted-books", true);
    }

    public boolean convert(Player player, ItemStack book) {
        if (!conversionEnabled()) return false;
        int amount = Math.max(1, book == null ? 1 : book.getAmount());
        AugmentService.GemBatchResult result = gemsFor(book, amount);
        if (result.refused()) {
            reportConversionRefusal(player);
            return false;
        }
        List<ItemStack> gems = result.gems();
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
        if (!conversionEnabled() || !isPotentialBook(event.getItem())) return;
        if (convertMoved(player, event.getSource(), event.getItem())) event.setCancelled(true);
    }

    private boolean convertMoved(Player player, Inventory source, ItemStack moved) {
        int amount = Math.max(1, moved.getAmount());
        int sourceSlot = -1;
        ItemStack sourceStack = null;
        ItemStack[] contents = source.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack candidate = contents[i];
            if (candidate != null && candidate.getAmount() >= amount && candidate.isSimilar(moved)) {
                sourceSlot = i;
                sourceStack = candidate;
                break;
            }
        }
        if (sourceSlot < 0 || sourceStack == null) return false;

        ItemStack converted = moved.clone();
        if (!convert(player, converted)) return false;

        int remaining = sourceStack.getAmount() - amount;
        if (remaining <= 0) source.setItem(sourceSlot, null);
        else source.setItem(sourceSlot, withAmount(sourceStack, remaining));
        return true;
    }

    private boolean isPotentialBook(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getType() == Material.ENCHANTED_BOOK
                && stack.getItemMeta() instanceof EnchantmentStorageMeta;
    }

    private record InventoryClaim(Inventory inventory, int slot, ItemStack expected, int amount) {}

    private record CursorClaim(ItemStack expected, int amount) {}
}
