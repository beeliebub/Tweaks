package me.beeliebub.tweaks.blocklog;

import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Snapshots a chest's contents when a player opens it and diffs against the contents
// when the same player closes it. Each per-slot change becomes one ChestLogEntry.
//
// Only player interactions are recorded — hoppers, droppers, and other automation are
// skipped intentionally to keep the log readable and the chunk PDC small.
//
// Concurrency note: snapshots are keyed by (player, anchor). If two players view the
// same chest simultaneously, the second to close may attribute the first's changes to
// themselves. Acceptable trade-off for the simpler implementation.
public final class BlockLogListener implements Listener {

    private final ChestLogManager manager;
    // (player UUID, anchor key) -> snapshot
    private final Map<SnapshotKey, ItemStack[]> snapshots = new HashMap<>();

    public BlockLogListener(ChestLogManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        Block anchor = manager.anchorOf(inv);
        if (anchor == null) return;
        snapshots.put(SnapshotKey.of(player.getUniqueId(), anchor), copy(inv.getContents()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        HumanEntity human = event.getPlayer();
        if (!(human instanceof Player player)) return;
        Inventory inv = event.getInventory();
        Block anchor = manager.anchorOf(inv);
        if (anchor == null) return;

        SnapshotKey key = SnapshotKey.of(player.getUniqueId(), anchor);
        ItemStack[] before = snapshots.remove(key);
        if (before == null) return;

        ItemStack[] after = inv.getContents();
        List<ChestLogEntry> entries = diff(before, after, player);
        if (entries.isEmpty()) return;
        manager.appendAll(anchor, entries);
    }

    // Per-slot diff. Same-type slots produce one delta entry (ADD/REMOVE of the amount changed).
    // Different items produce REMOVE old + ADD new of full stacks.
    private List<ChestLogEntry> diff(ItemStack[] before, ItemStack[] after, Player player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        List<ChestLogEntry> out = new ArrayList<>();

        int slots = Math.max(before.length, after.length);
        for (int i = 0; i < slots; i++) {
            ItemStack a = i < before.length ? before[i] : null;
            ItemStack b = i < after.length ? after[i] : null;

            if (isEmpty(a) && isEmpty(b)) continue;
            if (!isEmpty(a) && !isEmpty(b) && a.equals(b)) continue;

            if (!isEmpty(a) && !isEmpty(b) && a.isSimilar(b)) {
                int delta = b.getAmount() - a.getAmount();
                if (delta == 0) continue;
                ItemStack template = a.clone();
                template.setAmount(Math.abs(delta));
                LogAction action = delta > 0 ? LogAction.ADD : LogAction.REMOVE;
                out.add(new ChestLogEntry(now, action, uuid, name, template));
            } else {
                if (!isEmpty(a)) {
                    out.add(new ChestLogEntry(now, LogAction.REMOVE, uuid, name, a.clone()));
                }
                if (!isEmpty(b)) {
                    out.add(new ChestLogEntry(now, LogAction.ADD, uuid, name, b.clone()));
                }
            }
        }
        return out;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static ItemStack[] copy(ItemStack[] src) {
        ItemStack[] dst = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i] == null ? null : src[i].clone();
        }
        return dst;
    }

    // Compound key wrapping (UUID, world+coords). Block.equals uses world+coords already,
    // but UUID-of-world is a safer identity than the Block reference held over time.
    private record SnapshotKey(UUID player, UUID world, int x, int y, int z) {
        static SnapshotKey of(UUID player, Block anchor) {
            return new SnapshotKey(player, anchor.getWorld().getUID(), anchor.getX(), anchor.getY(), anchor.getZ());
        }
    }
}