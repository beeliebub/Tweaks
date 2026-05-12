package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.listeners.HelpListener;
import me.beeliebub.tweaks.managers.HelpManager;
import me.beeliebub.tweaks.tests.MessageAssert;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HelpListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private HelpManager helpManager;
    private HelpCommand helpCommand;
    private HelpListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        helpManager = new HelpManager();
        helpCommand = mock(HelpCommand.class);
        listener = new HelpListener(helpCommand, helpManager);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onJoin_SendsHelpMessage() {
        PlayerMock player = server.addPlayer();
        MessageAssert.assertMessageSent(player, "Need help? Type /help");
    }

    @Test
    void onInventoryClick_BackSlot_CallsOpenMainMenu() {
        PlayerMock player = server.addPlayer();
        HelpCommand.HelpHolder holder = new HelpCommand.HelpHolder("teleportation");
        Inventory inv = server.createInventory(holder, 54);
        holder.attach(inv);
        holder.markBackSlot(45);
        inv.setItem(45, new ItemStack(Material.RED_STAINED_GLASS_PANE));

        InventoryClickEvent event = new InventoryClickEvent(player.openInventory(inv), InventoryType.SlotType.CONTAINER, 45, org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        verify(helpCommand).openMainMenu(player);
    }

    @Test
    void onInventoryClick_Category_CallsOpenCategoryMenu() {
        PlayerMock player = server.addPlayer();
        HelpCommand.HelpHolder holder = new HelpCommand.HelpHolder(null);
        Inventory inv = server.createInventory(holder, 54);
        holder.attach(inv);
        holder.mapCategory(20, "teleportation");
        inv.setItem(20, new ItemStack(Material.COMPASS));

        InventoryClickEvent event = new InventoryClickEvent(player.openInventory(inv), InventoryType.SlotType.CONTAINER, 20, org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        verify(helpCommand).openCategoryMenu(eq(player), any(HelpManager.HelpCategory.class));
    }

    @Test
    void onInventoryClick_Article_CallsSendArticle() {
        PlayerMock player = server.addPlayer();
        HelpCommand.HelpHolder holder = new HelpCommand.HelpHolder("teleportation");
        Inventory inv = server.createInventory(holder, 54);
        holder.attach(inv);
        holder.mapArticle(10, "homes");
        inv.setItem(10, new ItemStack(Material.RED_BED));

        InventoryClickEvent event = new InventoryClickEvent(player.openInventory(inv), InventoryType.SlotType.CONTAINER, 10, org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        verify(helpCommand).sendArticle(eq(player), any(HelpManager.HelpArticle.class));
        // Article click should close inventory
        assertNull(player.getOpenInventory().getTopInventory());
    }
}
