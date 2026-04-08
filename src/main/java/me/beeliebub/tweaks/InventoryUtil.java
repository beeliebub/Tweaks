package me.beeliebub.tweaks;

import org.bukkit.inventory.ItemStack;
import java.util.Base64;

public class InventoryUtil {

    public static String toBase64(ItemStack[] items) {
        if (items == null || items.length == 0) return "";

        try {
            byte[] itemBytes = ItemStack.serializeItemsAsBytes(items);
            return Base64.getEncoder().encodeToString(itemBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize inventory to Base64.", e);
        }
    }

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