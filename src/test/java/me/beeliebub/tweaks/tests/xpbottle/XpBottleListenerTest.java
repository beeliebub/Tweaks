package me.beeliebub.tweaks.tests.xpbottle;

import me.beeliebub.tweaks.tests.MessageAssert;
import me.beeliebub.tweaks.xpbottle.ExperienceManager;
import me.beeliebub.tweaks.xpbottle.XpBottleListener;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class XpBottleListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private XpBottleListener listener;
    private NamespacedKey brewerKey;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        listener = new XpBottleListener(plugin);
        brewerKey = new NamespacedKey(plugin, "xp_bottle_brewer");
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private BrewEvent mockBrewEvent(Block block, BrewerInventory inv) {
        BrewEvent event = mock(BrewEvent.class);
        when(event.getBlock()).thenReturn(block);
        when(event.getContents()).thenReturn(inv);
        when(event.getHandlers()).thenReturn(BrewEvent.getHandlerList());
        doAnswer(invoc -> {
            when(event.isCancelled()).thenReturn(invoc.getArgument(0));
            return null;
        }).when(event).setCancelled(anyBoolean());
        return event;
    }

    @Test
    void onConsume_AwardsXp() {
        PlayerMock player = server.addPlayer();
        ItemStack bottle = listener.xpBottle().create(100);
        player.getInventory().setItemInMainHand(bottle);

        PlayerItemConsumeEvent event = new PlayerItemConsumeEvent(player, bottle, null);
        server.getPluginManager().callEvent(event);

        assertEquals(100, new ExperienceManager(player).getCurrentExp());
    }

    @Test
    void trackBrewer_SetsPdcOnInventoryClick() {
        PlayerMock player = server.addPlayer();
        var world = server.addSimpleWorld("test");
        var block = world.getBlockAt(0, 0, 0);
        block.setType(Material.BREWING_STAND);
        BrewingStand stand = (BrewingStand) block.getState();
        BrewerInventory inv = stand.getInventory();
        inv.setIngredient(new ItemStack(Material.EMERALD));
        inv.setItem(0, new ItemStack(Material.GLASS_BOTTLE));

        InventoryView view = player.openInventory(inv);
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.FUEL, 3, org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
        
        server.getPluginManager().callEvent(event);
        server.getScheduler().performOneTick();

        // Check PDC on the block state
        BrewingStand standAfter = (BrewingStand) block.getState();
        assertEquals(player.getUniqueId().toString(), standAfter.getPersistentDataContainer().get(brewerKey, PersistentDataType.STRING));
    }

    @Test
    void onBrew_Success() {
        PlayerMock player = server.addPlayer();
        new ExperienceManager(player).changeExp(5000);

        Block mockBlock = mock(Block.class);
        BrewingStand mockStand = mock(BrewingStand.class);
        when(mockBlock.getState()).thenReturn(mockStand);
        var world = server.addSimpleWorld("test1");
        when(mockBlock.getWorld()).thenReturn(world);
        when(mockBlock.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 0, 0));
        
        org.bukkit.persistence.PersistentDataContainer pdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(pdc.get(brewerKey, PersistentDataType.STRING)).thenReturn(player.getUniqueId().toString());
        when(mockStand.getPersistentDataContainer()).thenReturn(pdc);
        
        BrewerInventory inv = mock(BrewerInventory.class);
        when(mockStand.getInventory()).thenReturn(inv);
        
        ItemStack emerald = new ItemStack(Material.EMERALD);
        when(inv.getIngredient()).thenReturn(emerald);
        when(inv.getItem(0)).thenReturn(new ItemStack(Material.GLASS_BOTTLE));
        when(inv.getItem(1)).thenReturn(new ItemStack(Material.GLASS_BOTTLE));
        when(inv.getItem(2)).thenReturn(null);

        BrewEvent event = mockBrewEvent(mockBlock, inv);
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        server.getScheduler().performOneTick();

        // Check XP consumed
        assertEquals(5000 - (2 * XpBottleListener.ORBS_PER_EMERALD), new ExperienceManager(player).getCurrentExp());
        
        // Check bottles created
        verify(inv).setItem(eq(0), argThat(item -> item != null && listener.xpBottle().isXpBottle(item)));
        verify(inv).setItem(eq(1), argThat(item -> item != null && listener.xpBottle().isXpBottle(item)));
        
        // Check PDC cleared
        verify(pdc).remove(brewerKey);
    }

    @Test
    void onBrew_PartialSuccess() {
        PlayerMock player = server.addPlayer();
        // Enough for 1 but not 2 (1395 * 2 = 2790)
        new ExperienceManager(player).changeExp(2000); 

        Block mockBlock = mock(Block.class);
        BrewingStand mockStand = mock(BrewingStand.class);
        when(mockBlock.getState()).thenReturn(mockStand);
        var world = server.addSimpleWorld("test2");
        when(mockBlock.getWorld()).thenReturn(world);
        when(mockBlock.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 0, 0));
        
        org.bukkit.persistence.PersistentDataContainer pdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(pdc.get(brewerKey, PersistentDataType.STRING)).thenReturn(player.getUniqueId().toString());
        when(mockStand.getPersistentDataContainer()).thenReturn(pdc);

        BrewerInventory inv = mock(BrewerInventory.class);
        when(mockStand.getInventory()).thenReturn(inv);
        
        ItemStack emeralds = new ItemStack(Material.EMERALD, 2);
        when(inv.getIngredient()).thenReturn(emeralds);
        when(inv.getItem(0)).thenReturn(new ItemStack(Material.GLASS_BOTTLE));
        when(inv.getItem(1)).thenReturn(new ItemStack(Material.GLASS_BOTTLE));
        when(inv.getItem(2)).thenReturn(null);

        BrewEvent event = mockBrewEvent(mockBlock, inv);
        server.getPluginManager().callEvent(event);

        server.getScheduler().performOneTick();

        // Check XP consumed for only 1 bottle
        assertEquals(2000 - XpBottleListener.ORBS_PER_EMERALD, new ExperienceManager(player).getCurrentExp());
        
        // One XP bottle, one glass bottle
        verify(inv).setItem(eq(0), argThat(item -> item != null && listener.xpBottle().isXpBottle(item)));
        verify(inv).setItem(eq(1), argThat(item -> item != null && item.getType() == Material.GLASS_BOTTLE));
        
        // Message sent
        MessageAssert.assertMessageSent(player, "Not enough XP for all bottles");
    }

    @Test
    void onBrew_NoXp_ReturnsIngredients() {
        PlayerMock player = server.addPlayer();
        new ExperienceManager(player).changeExp(0);

        Block mockBlock = mock(Block.class);
        BrewingStand mockStand = mock(BrewingStand.class);
        when(mockBlock.getState()).thenReturn(mockStand);
        var world = server.addSimpleWorld("test3");
        when(mockBlock.getWorld()).thenReturn(world);
        when(mockBlock.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 0, 0));
        
        org.bukkit.persistence.PersistentDataContainer pdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(pdc.get(brewerKey, PersistentDataType.STRING)).thenReturn(player.getUniqueId().toString());
        when(mockStand.getPersistentDataContainer()).thenReturn(pdc);

        BrewerInventory inv = mock(BrewerInventory.class);
        when(mockStand.getInventory()).thenReturn(inv);
        
        ItemStack emeralds = new ItemStack(Material.EMERALD, 5);
        when(inv.getIngredient()).thenReturn(emeralds);
        when(inv.getItem(0)).thenReturn(new ItemStack(Material.GLASS_BOTTLE));
        when(inv.getItem(1)).thenReturn(null);
        when(inv.getItem(2)).thenReturn(null);

        BrewEvent event = mockBrewEvent(mockBlock, inv);
        server.getPluginManager().callEvent(event);

        server.getScheduler().performOneTick();

        // Ingredient slot should be empty (dropped)
        verify(inv).setIngredient(null);
        
        // Check if item was dropped
        assertTrue(world.getEntities().stream().anyMatch(e -> e instanceof org.bukkit.entity.Item && ((org.bukkit.entity.Item) e).getItemStack().getType() == Material.EMERALD));

        MessageAssert.assertMessageSent(player, "Not enough XP to brew XP bottles");
    }
}