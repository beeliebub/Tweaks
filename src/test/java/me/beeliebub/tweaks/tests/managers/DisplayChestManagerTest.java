package me.beeliebub.tweaks.tests.managers;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.managers.DisplayChestManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisplayChestManagerTest {

    private ServerMock server;
    private Tweaks plugin;
    private DisplayChestManager manager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        manager = new DisplayChestManager(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void toggleSetupMode() {
        UUID id = UUID.randomUUID();
        assertTrue(manager.toggleSetupMode(id));
        assertTrue(manager.isSetupMode(id));
        assertFalse(manager.toggleSetupMode(id));
        assertFalse(manager.isSetupMode(id));
    }

    @Test
    void toggleRemovalMode() {
        UUID id = UUID.randomUUID();
        assertTrue(manager.toggleRemovalMode(id));
        assertTrue(manager.isRemovalMode(id));
        assertFalse(manager.toggleRemovalMode(id));
        assertFalse(manager.isRemovalMode(id));
    }

    @Test
    void toggleSetupModeDisablesRemovalMode() {
        UUID id = UUID.randomUUID();
        manager.toggleRemovalMode(id);
        assertTrue(manager.isRemovalMode(id));
        manager.toggleSetupMode(id);
        assertTrue(manager.isSetupMode(id));
        assertFalse(manager.isRemovalMode(id));
    }

    @Test
    void processChestSpawnsDisplay() {
        Location loc = new Location(server.addSimpleWorld("world"), 0, 64, 0);
        Block block = loc.getBlock();
        block.setType(Material.CHEST);
        Chest chest = (Chest) block.getState();
        chest.getInventory().addItem(new ItemStack(Material.DIAMOND, 64));

        manager.processChest(block);

        // Verify that an ItemDisplay was spawned
        long count = loc.getWorld().getEntitiesByClass(ItemDisplay.class).stream().count();
        assertEquals(1, count);
        
        ItemDisplay display = loc.getWorld().getEntitiesByClass(ItemDisplay.class).stream().findFirst().get();
        assertEquals(Material.DIAMOND, display.getItemStack().getType());
    }

    @Test
    void processChestRemovesOldDisplay() {
        Location loc = new Location(server.addSimpleWorld("world"), 0, 64, 0);
        Block block = loc.getBlock();
        block.setType(Material.CHEST);
        Chest chest = (Chest) block.getState();
        
        chest.getInventory().addItem(new ItemStack(Material.DIAMOND, 64));
        manager.processChest(block);
        assertEquals(1, loc.getWorld().getEntitiesByClass(ItemDisplay.class).stream().count());

        chest.getInventory().clear();
        chest.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 64));
        manager.processChest(block);
        
        assertEquals(1, loc.getWorld().getEntitiesByClass(ItemDisplay.class).stream().count());
        ItemDisplay display = loc.getWorld().getEntitiesByClass(ItemDisplay.class).stream().findFirst().get();
        assertEquals(Material.GOLD_INGOT, display.getItemStack().getType());
    }

    @Test
    void removeDisplay() {
        Location loc = new Location(server.addSimpleWorld("world"), 0, 64, 0);
        Block block = loc.getBlock();
        block.setType(Material.CHEST);
        Chest chest = (Chest) block.getState();
        chest.getInventory().addItem(new ItemStack(Material.DIAMOND, 64));

        manager.processChest(block);
        assertEquals(1, loc.getWorld().getEntitiesByClass(ItemDisplay.class).stream().count());

        manager.removeDisplay(block);
        assertEquals(0, loc.getWorld().getEntitiesByClass(ItemDisplay.class).stream().count());
    }
}
