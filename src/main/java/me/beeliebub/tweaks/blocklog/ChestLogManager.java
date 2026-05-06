package me.beeliebub.tweaks.blocklog;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// High-level API for the block-log system. Owns anchor logic (turns any clicked half of a
// double chest into a deterministic primary block) and delegates persistence to ChunkLogStore.
//
// Loggable container types: chests, trapped chests, barrels. Shulker boxes and ender chests
// are intentionally excluded (portable / per-player).
public final class ChestLogManager {

    private final ChunkLogStore store;

    public ChestLogManager(Plugin plugin) {
        this.store = new ChunkLogStore(plugin);
    }

    public boolean isLoggable(Material material) {
        return switch (material) {
            case CHEST, TRAPPED_CHEST, BARREL -> true;
            default -> false;
        };
    }

    public boolean isLoggable(Block block) {
        return block != null && isLoggable(block.getType());
    }

    // For a double chest, return the lower-coord half so both halves point to the same log.
    // For everything else, return the input block unchanged.
    public Block anchor(Block block) {
        if (block == null) return null;
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) return block;

        Inventory inv = chest.getInventory();
        if (inv instanceof DoubleChestInventory doubleInv) {
            DoubleChest doubleChest = (DoubleChest) doubleInv.getHolder();
            if (doubleChest != null) {
                InventoryHolder left = doubleChest.getLeftSide();
                if (left instanceof Chest leftChest) {
                    return leftChest.getBlock();
                }
            }
        }
        return block;
    }

    // Resolve the anchor block for a given inventory (open/close events go through this).
    // Returns null if the inventory is not a loggable container.
    public @Nullable Block anchorOf(Inventory inv) {
        if (inv == null) return null;
        InventoryHolder holder = inv.getHolder();
        if (holder == null) return null;

        if (holder instanceof DoubleChest doubleChest) {
            InventoryHolder left = doubleChest.getLeftSide();
            if (left instanceof Chest leftChest) {
                Block b = leftChest.getBlock();
                return isLoggable(b) ? b : null;
            }
            return null;
        }
        if (holder instanceof BlockInventoryHolder blockHolder) {
            Block b = blockHolder.getBlock();
            return isLoggable(b) ? b : null;
        }
        return null;
    }

    public List<ChestLogEntry> read(Block anchor) {
        return store.read(anchor);
    }

    public void appendAll(Block anchor, List<ChestLogEntry> entries) {
        if (entries.isEmpty()) return;
        store.appendAll(anchor, entries);
    }

    public int pruneChunk(org.bukkit.Chunk chunk, long cutoffMillis) {
        return store.pruneChunk(chunk, cutoffMillis);
    }
}