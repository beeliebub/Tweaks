package me.beeliebub.tweaks.tests.listeners;

import me.beeliebub.tweaks.listeners.DisplayChestListener;
import me.beeliebub.tweaks.managers.DisplayChestManager;
import me.beeliebub.tweaks.tests.MessageAssert;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.junit.jupiter.api.Assertions.*;

class DisplayChestListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private DisplayChestManager manager;
    private DisplayChestListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        manager = new DisplayChestManager(plugin);
        listener = new DisplayChestListener(manager);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onInteract_SetupMode_ProcessesChest() {
        PlayerMock player = server.addPlayer();
        manager.toggleSetupMode(player.getUniqueId());
        
        var world = server.addSimpleWorld("test");
        var block = world.getBlockAt(0, 0, 0);
        block.setType(Material.CHEST);
        Chest chest = (Chest) block.getState();
        chest.getInventory().addItem(new ItemStack(Material.EMERALD, 1));

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, null);
        server.getPluginManager().callEvent(event);
        
        assertTrue(event.isCancelled());
        assertEquals(1, world.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class).size());
        MessageAssert.assertMessageSent(player, "Display chest generated/updated!");
    }

    @Test
    void onInteract_RemovalMode_RemovesDisplay() {
        PlayerMock player = server.addPlayer();
        var world = server.addSimpleWorld("test");
        var block = world.getBlockAt(0, 0, 0);
        block.setType(Material.CHEST);
        manager.processChest(block);
        
        manager.toggleRemovalMode(player.getUniqueId());

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, null);
        server.getPluginManager().callEvent(event);
        
        assertTrue(event.isCancelled());
        assertEquals(0, world.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class).size());
        MessageAssert.assertMessageSent(player, "Display chest removed!");
    }
}
