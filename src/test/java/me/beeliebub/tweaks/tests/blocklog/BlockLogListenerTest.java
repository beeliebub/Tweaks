package me.beeliebub.tweaks.tests.blocklog;

import me.beeliebub.tweaks.blocklog.BlockLogListener;
import me.beeliebub.tweaks.blocklog.ChestLogEntry;
import me.beeliebub.tweaks.blocklog.ChestLogManager;
import me.beeliebub.tweaks.blocklog.LogAction;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.mockito.Mockito.*;

class BlockLogListenerTest {

    private ServerMock server;
    private ChestLogManager manager;
    private BlockLogListener listener;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        manager = mock(ChestLogManager.class);
        listener = new BlockLogListener(manager);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testLoggingOnInventoryChange() {
        Block block = server.addSimpleWorld("test").getBlockAt(0, 0, 0);
        block.setType(Material.CHEST);
        
        Inventory inv = server.createInventory(null, 27);
        ItemStack itemBefore = new ItemStack(Material.STONE, 64);
        inv.setItem(0, itemBefore);
        
        when(manager.anchorOf(inv)).thenReturn(block);
        
        org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTopInventory()).thenReturn(inv);
        
        // Open
        InventoryOpenEvent openEvent = new InventoryOpenEvent(view);
        listener.onOpen(openEvent);
        
        // Change inventory
        inv.setItem(0, null);
        
        // Close
        InventoryCloseEvent closeEvent = new InventoryCloseEvent(view);
        listener.onClose(closeEvent);
        
        // Verify
        verify(manager).appendAll(eq(block), argThat(entries -> {
            if (entries.size() != 1) return false;
            ChestLogEntry entry = entries.get(0);
            return entry.action() == LogAction.REMOVE && entry.item().getType() == Material.STONE && entry.item().getAmount() == 64;
        }));
    }

    @Test
    void testLoggingAddItems() {
        Block block = server.addSimpleWorld("test").getBlockAt(0, 0, 0);
        block.setType(Material.CHEST);
        
        Inventory inv = server.createInventory(null, 27);
        
        when(manager.anchorOf(inv)).thenReturn(block);
        
        org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTopInventory()).thenReturn(inv);
        
        // Open
        InventoryOpenEvent openEvent = new InventoryOpenEvent(view);
        listener.onOpen(openEvent);
        
        // Add items
        ItemStack itemAfter = new ItemStack(Material.DIAMOND, 10);
        inv.setItem(0, itemAfter);
        
        // Close
        InventoryCloseEvent closeEvent = new InventoryCloseEvent(view);
        listener.onClose(closeEvent);
        
        // Verify
        verify(manager).appendAll(eq(block), argThat(entries -> {
            if (entries.size() != 1) return false;
            ChestLogEntry entry = entries.get(0);
            return entry.action() == LogAction.ADD && entry.item().getType() == Material.DIAMOND && entry.item().getAmount() == 10;
        }));
    }
}
