package me.beeliebub.tweaks.utils;

import me.beeliebub.tweaks.recipes.ResourceRupee;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Base64;
import java.util.Map;

// Shared inventory utilities: Base64 serialization and Resource Rupee currency helpers.
public class InventoryUtil {

    // Shared stateless instance — ResourceRupee carries no mutable state; its identification
    // and factory methods delegate entirely to private static helpers and public constants.
    private static final ResourceRupee RUPEE = new ResourceRupee();

    // ---------------------------------------------------------------------------
    // Resource Rupee currency helpers
    // ---------------------------------------------------------------------------

    /**
     * Returns the player's total Resource Rupee balance in loose-rupee units.
     * Each loose rupee counts as 1; each rupee block counts as 9.
     * Null slots in the inventory are skipped.
     */
    public static int getResourceRupeeBalance(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) continue;
            if (RUPEE.isRupee(stack)) {
                total += stack.getAmount();
            } else if (RUPEE.isRupeeBlock(stack)) {
                total += stack.getAmount() * 9;
            }
        }
        return total;
    }

    /**
     * Deducts {@code amount} loose-rupee units from the player's inventory.
     *
     * <p>Returns {@code false} immediately (no inventory mutation) if the player cannot afford
     * the amount, or if {@code amount} is negative. {@code amount == 0} is a no-op that returns
     * {@code true}.
     *
     * <p>Strategy: loose rupees are consumed first; blocks are broken one-at-a-time as needed.
     * When breaking a block would overshoot the remaining debt, change is minted back via
     * {@link #addResourceRupees}.
     */
    public static boolean deductResourceRupees(Player player, int amount) {
        if (amount < 0) return false;
        if (amount == 0) return true;
        if (getResourceRupeeBalance(player) < amount) return false;

        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        int remaining = amount;

        // First pass: consume from loose-rupee stacks.
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !RUPEE.isRupee(stack)) continue;
            int take = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() == 0) {
                contents[i] = null;
            }
            remaining -= take;
        }

        // Second pass: consume rupee blocks one-at-a-time, tracking any change owed.
        // Change is NOT minted inside this loop because addResourceRupees works on the live
        // inventory and a later setContents() call would overwrite the added stacks. Instead we
        // accumulate the change amount and apply it after setContents().
        int changeOwed = 0;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !RUPEE.isRupeeBlock(stack)) continue;

            // Consume one block at a time so change calculation stays precise.
            while (stack.getAmount() > 0 && remaining > 0) {
                stack.setAmount(stack.getAmount() - 1);
                if (remaining >= 9) {
                    remaining -= 9;
                } else {
                    // Block overshoots — record the surplus as change.
                    changeOwed += 9 - remaining;
                    remaining = 0;
                }
            }
            if (stack.getAmount() == 0) {
                contents[i] = null;
            }
        }

        // Commit the consumed state first, then mint change so it lands on the updated inventory.
        inv.setContents(contents);
        if (changeOwed > 0) {
            addResourceRupees(player, changeOwed);
        }
        return true;
    }

    /**
     * Adds {@code amount} loose-rupee units to the player's inventory, distributed optimally
     * as blocks (groups of 9) and loose rupees (remainder).
     *
     * <p>If the inventory is full, leftover stacks are dropped at the player's feet.
     */
    public static void addResourceRupees(Player player, int amount) {
        if (amount <= 0) return;

        int blocks = amount / 9;
        int loose = amount % 9;

        if (blocks > 0) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(RUPEE.createRupeeBlock(blocks));
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
        if (loose > 0) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(RUPEE.createRupee(loose));
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Base64 serialization (YAML storage)
    // ---------------------------------------------------------------------------

    // Serialize an array of items into a Base64 string
    public static String toBase64(ItemStack[] items) {
        if (items == null || items.length == 0) return "";

        try {
            byte[] itemBytes = ItemStack.serializeItemsAsBytes(items);
            return Base64.getEncoder().encodeToString(itemBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize inventory to Base64.", e);
        }
    }

    // Deserialize a Base64 string back into an array of items
    public static ItemStack[] fromBase64(String data) {
        if (data == null || data.isEmpty()) return new ItemStack[0];

        try {
            byte[] itemBytes = Base64.getDecoder().decode(data);
            return ItemStack.deserializeItemsFromBytes(itemBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decode inventory from Base64.", e);
        }
    }
}
