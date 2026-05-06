package me.beeliebub.tweaks.blocklog;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

// One ADD/REMOVE event against a single chest at a specific moment.
// 'item' is the full ItemStack so the viewer can display its meta (enchants, lore, name) on hover.
public record ChestLogEntry(
        long timestamp,
        LogAction action,
        UUID playerUuid,
        String playerName,
        ItemStack item
) {
    public ChestLogEntry {
        if (action == null) throw new IllegalArgumentException("action");
        if (playerUuid == null) throw new IllegalArgumentException("playerUuid");
        if (playerName == null) playerName = "";
        if (item == null) throw new IllegalArgumentException("item");
    }
}