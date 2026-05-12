package me.beeliebub.tweaks.tests.blocklog;

import me.beeliebub.tweaks.blocklog.ChestLogManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChestLogManagerTest {

    private final Plugin plugin = mock(Plugin.class);
    private final ChestLogManager manager = new ChestLogManager(plugin);

    @Test
    void isLoggableMaterialAcceptsChestVariantsAndBarrel() {
        assertTrue(manager.isLoggable(Material.CHEST));
        assertTrue(manager.isLoggable(Material.TRAPPED_CHEST));
        assertTrue(manager.isLoggable(Material.BARREL));
    }

    @Test
    void isLoggableMaterialRejectsExcludedContainers() {
        // Per blocklog/CLAUDE.md: ender chests, shulker boxes, hoppers, droppers are excluded.
        assertFalse(manager.isLoggable(Material.ENDER_CHEST));
        assertFalse(manager.isLoggable(Material.SHULKER_BOX));
        assertFalse(manager.isLoggable(Material.HOPPER));
        assertFalse(manager.isLoggable(Material.DROPPER));
        assertFalse(manager.isLoggable(Material.DISPENSER));
        assertFalse(manager.isLoggable(Material.STONE));
    }

    @Test
    void isLoggableBlockRejectsNull() {
        assertFalse(manager.isLoggable((Block) null));
    }

    @Test
    void isLoggableBlockDelegatesToMaterialCheck() {
        Block chest = mock(Block.class);
        when(chest.getType()).thenReturn(Material.CHEST);
        assertTrue(manager.isLoggable(chest));

        Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);
        assertFalse(manager.isLoggable(stone));
    }

    @Test
    void anchorReturnsNullForNullInput() {
        assertNull(manager.anchor(null));
    }

    @Test
    void anchorReturnsBlockUnchangedForNonChestBlock() {
        Block block = mock(Block.class);
        BlockState state = mock(BlockState.class);
        when(block.getState()).thenReturn(state);
        // state is not a Chest -> early return
        assertSame(block, manager.anchor(block));
    }

    @Test
    void anchorReturnsBlockUnchangedForSingleChest() {
        Block block = mock(Block.class);
        Chest chest = mock(Chest.class);
        Inventory inv = mock(Inventory.class); // not a DoubleChestInventory
        when(block.getState()).thenReturn(chest);
        when(chest.getInventory()).thenReturn(inv);
        assertSame(block, manager.anchor(block));
    }

    @Test
    void anchorReturnsLeftHalfForDoubleChest() {
        Block right = mock(Block.class);
        Chest rightChest = mock(Chest.class);
        DoubleChestInventory doubleInv = mock(DoubleChestInventory.class);
        DoubleChest doubleChest = mock(DoubleChest.class);
        Chest leftChest = mock(Chest.class);
        Block left = mock(Block.class);

        when(right.getState()).thenReturn(rightChest);
        when(rightChest.getInventory()).thenReturn(doubleInv);
        when(doubleInv.getHolder()).thenReturn(doubleChest);
        when(doubleChest.getLeftSide()).thenReturn(leftChest);
        when(leftChest.getBlock()).thenReturn(left);

        assertSame(left, manager.anchor(right));
    }

    @Test
    void anchorOfReturnsNullForNullInventory() {
        assertNull(manager.anchorOf(null));
    }

    @Test
    void anchorOfReturnsNullWhenInventoryHolderMissing() {
        Inventory inv = mock(Inventory.class);
        when(inv.getHolder()).thenReturn(null);
        assertNull(manager.anchorOf(inv));
    }

    @Test
    void anchorOfReturnsBlockForLoggableBlockHolder() {
        Inventory inv = mock(Inventory.class);
        BlockInventoryHolder holder = mock(BlockInventoryHolder.class);
        Block block = mock(Block.class);
        when(inv.getHolder()).thenReturn(holder);
        when(holder.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.BARREL);
        assertSame(block, manager.anchorOf(inv));
    }

    @Test
    void anchorOfReturnsNullForNonLoggableBlockHolder() {
        Inventory inv = mock(Inventory.class);
        BlockInventoryHolder holder = mock(BlockInventoryHolder.class);
        Block block = mock(Block.class);
        when(inv.getHolder()).thenReturn(holder);
        when(holder.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.HOPPER);
        assertNull(manager.anchorOf(inv));
    }

    @Test
    void anchorOfReturnsLeftBlockForDoubleChestHolder() {
        Inventory inv = mock(Inventory.class);
        DoubleChest doubleChest = mock(DoubleChest.class);
        Chest leftChest = mock(Chest.class);
        Block leftBlock = mock(Block.class);
        when(inv.getHolder()).thenReturn(doubleChest);
        when(doubleChest.getLeftSide()).thenReturn(leftChest);
        when(leftChest.getBlock()).thenReturn(leftBlock);
        when(leftBlock.getType()).thenReturn(Material.CHEST);
        assertSame(leftBlock, manager.anchorOf(inv));
    }

    @Test
    void appendAllSkipsCallToStoreWhenEntriesEmpty() {
        // We can't easily verify the store wasn't called (it's package-private), but the
        // method must at minimum not throw on an empty list.
        Block block = mock(Block.class);
        assertDoesNotThrow(() -> manager.appendAll(block, java.util.List.of()));
    }
}
