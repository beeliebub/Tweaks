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

    // ─── xpbottle.orbs-per-emerald (restart-required config knob) ────────────

    @Test
    void orbsPerEmeraldReadsConfiguredValueAtConstruction() {
        plugin.getConfig().set("xpbottle.orbs-per-emerald", 2000);
        XpBottleListener customListener = new XpBottleListener(plugin);
        assertEquals(2000, customListener.orbsPerEmerald());
        assertEquals(2000 * 9, customListener.orbsPerEmeraldBlock());
    }

    @Test
    void orbsPerEmeraldIgnoresLiveEditsAfterConstruction() {
        // Restart-required: this value is baked into a registered PotionMix recipe at boot, so
        // an edit after construction must not affect the already-built listener.
        plugin.getConfig().set("xpbottle.orbs-per-emerald", 999);
        assertEquals(1395, listener.orbsPerEmerald(),
                "Value is read once at construction; live edits require a restart");
    }

    @Test
    void orbsPerEmeraldClampsBelowOneToOne() {
        plugin.getConfig().set("xpbottle.orbs-per-emerald", 0);
        XpBottleListener customListener = new XpBottleListener(plugin);
        assertEquals(1, customListener.orbsPerEmerald());
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

        // XP consumed for both bottles
        assertEquals(5000 - (2 * listener.orbsPerEmerald()), new ExperienceManager(player).getCurrentExp());

        // Bottles created with the exact orb count
        verify(inv).setItem(eq(0), argThat(item -> item != null
                && listener.xpBottle().isXpBottle(item)
                && listener.xpBottle().getStoredOrbs(item) == listener.orbsPerEmerald()));
        verify(inv).setItem(eq(1), argThat(item -> item != null
                && listener.xpBottle().isXpBottle(item)
                && listener.xpBottle().getStoredOrbs(item) == listener.orbsPerEmerald()));

        // Full brew consumes exactly 1 emerald: 1 in slot - 1 = empty slot
        verify(inv).setIngredient(isNull());

        // PDC cleared
        verify(pdc).remove(brewerKey);

        // Nothing dropped on a full brew
        assertEquals(0, world.getEntities().stream()
                .filter(e -> e instanceof org.bukkit.entity.Item)
                .count());
    }

    @Test
    void onBrew_FullBrew_LargeStack_ConsumesOnlyOneEmerald() {
        PlayerMock player = server.addPlayer();
        new ExperienceManager(player).changeExp(10000);

        Block mockBlock = mock(Block.class);
        BrewingStand mockStand = mock(BrewingStand.class);
        when(mockBlock.getState()).thenReturn(mockStand);
        var world = server.addSimpleWorld("test_full_large");
        when(mockBlock.getWorld()).thenReturn(world);
        when(mockBlock.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 0, 0));

        org.bukkit.persistence.PersistentDataContainer pdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(pdc.get(brewerKey, PersistentDataType.STRING)).thenReturn(player.getUniqueId().toString());
        when(mockStand.getPersistentDataContainer()).thenReturn(pdc);

        BrewerInventory inv = mock(BrewerInventory.class);
        when(mockStand.getInventory()).thenReturn(inv);

        // 5 emeralds in slot — vanilla parity says one brewing cycle consumes exactly 1.
        ItemStack emeralds = new ItemStack(Material.EMERALD, 5);
        when(inv.getIngredient()).thenReturn(emeralds);
        when(inv.getItem(0)).thenReturn(new ItemStack(Material.GLASS_BOTTLE));
        when(inv.getItem(1)).thenReturn(new ItemStack(Material.GLASS_BOTTLE));
        when(inv.getItem(2)).thenReturn(new ItemStack(Material.GLASS_BOTTLE));

        BrewEvent event = mockBrewEvent(mockBlock, inv);
        server.getPluginManager().callEvent(event);
        server.getScheduler().performOneTick();

        // 3 bottles brewed -> 3 * orbsPerEmerald() charged
        assertEquals(10000 - (3 * listener.orbsPerEmerald()),
                new ExperienceManager(player).getCurrentExp());

        // Slot left with exactly 4 emeralds — one consumed, four kept.
        verify(inv).setIngredient(argThat(stack -> stack != null
                && stack.getType() == Material.EMERALD
                && stack.getAmount() == 4));

        // No drops on a full brew — ingredient stays in the slot.
        assertEquals(0, world.getEntities().stream()
                .filter(e -> e instanceof org.bukkit.entity.Item)
                .count());
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

        // XP consumed for only the brewed bottle
        assertEquals(2000 - listener.orbsPerEmerald(), new ExperienceManager(player).getCurrentExp());

        // One XP bottle (with correct orbs), one glass bottle refund
        verify(inv).setItem(eq(0), argThat(item -> item != null
                && listener.xpBottle().isXpBottle(item)
                && listener.xpBottle().getStoredOrbs(item) == listener.orbsPerEmerald()));
        verify(inv).setItem(eq(1), argThat(item -> item != null && item.getType() == Material.GLASS_BOTTLE));

        // Partial brew refunds ALL emeralds (no consumption), slot cleared.
        verify(inv).setIngredient(isNull());
        long emeraldsDropped = world.getEntities().stream()
                .filter(e -> e instanceof org.bukkit.entity.Item)
                .map(e -> ((org.bukkit.entity.Item) e).getItemStack())
                .filter(s -> s.getType() == Material.EMERALD)
                .mapToLong(ItemStack::getAmount)
                .sum();
        assertEquals(2, emeraldsDropped, "Partial brew must refund the entire emerald stack");

        // Message sent
        MessageAssert.assertMessageSent(player, "Not enough XP for all bottles");
    }

    @Test
    void onBrew_PartialBrew_LargeStack_RefundsAllEmeralds() {
        PlayerMock player = server.addPlayer();
        // Enough for 1 bottle of 1395 orbs but not for the 3 requested.
        new ExperienceManager(player).changeExp(2000);

        Block mockBlock = mock(Block.class);
        BrewingStand mockStand = mock(BrewingStand.class);
        when(mockBlock.getState()).thenReturn(mockStand);
        var world = server.addSimpleWorld("test_partial_large");
        when(mockBlock.getWorld()).thenReturn(world);
        when(mockBlock.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 0, 0));

        org.bukkit.persistence.PersistentDataContainer pdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(pdc.get(brewerKey, PersistentDataType.STRING)).thenReturn(player.getUniqueId().toString());
        when(mockStand.getPersistentDataContainer()).thenReturn(pdc);

        BrewerInventory inv = mock(BrewerInventory.class);
        when(mockStand.getInventory()).thenReturn(inv);

        // 5 emeralds in slot. Partial brew must refund all 5 (no vanish, no dup).
        ItemStack emeralds = new ItemStack(Material.EMERALD, 5);
        when(inv.getIngredient()).thenReturn(emeralds);
        when(inv.getItem(0)).thenReturn(new ItemStack(Material.GLASS_BOTTLE));
        when(inv.getItem(1)).thenReturn(new ItemStack(Material.GLASS_BOTTLE));
        when(inv.getItem(2)).thenReturn(new ItemStack(Material.GLASS_BOTTLE));

        BrewEvent event = mockBrewEvent(mockBlock, inv);
        server.getPluginManager().callEvent(event);
        server.getScheduler().performOneTick();

        // Exactly 1 bottle brewed -> 1395 XP charged
        assertEquals(2000 - listener.orbsPerEmerald(),
                new ExperienceManager(player).getCurrentExp());

        // One XP bottle, two glass bottle refunds.
        verify(inv).setItem(eq(0), argThat(item -> item != null && listener.xpBottle().isXpBottle(item)));
        verify(inv).setItem(eq(1), argThat(item -> item != null && item.getType() == Material.GLASS_BOTTLE));
        verify(inv).setItem(eq(2), argThat(item -> item != null && item.getType() == Material.GLASS_BOTTLE));

        // Slot cleared and exactly 5 emeralds dropped — no vanishing, no duplication.
        verify(inv).setIngredient(isNull());
        long emeraldsDropped = world.getEntities().stream()
                .filter(e -> e instanceof org.bukkit.entity.Item)
                .map(e -> ((org.bukkit.entity.Item) e).getItemStack())
                .filter(s -> s.getType() == Material.EMERALD)
                .mapToLong(ItemStack::getAmount)
                .sum();
        assertEquals(5, emeraldsDropped);

        MessageAssert.assertMessageSent(player, "Not enough XP for all bottles");
    }

    @Test
    void onBrew_EmeraldBlock_StoresHigherOrbValue() {
        PlayerMock player = server.addPlayer();
        new ExperienceManager(player).changeExp(50000);

        Block mockBlock = mock(Block.class);
        BrewingStand mockStand = mock(BrewingStand.class);
        when(mockBlock.getState()).thenReturn(mockStand);
        var world = server.addSimpleWorld("test_block");
        when(mockBlock.getWorld()).thenReturn(world);
        when(mockBlock.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 0, 0));

        org.bukkit.persistence.PersistentDataContainer pdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(pdc.get(brewerKey, PersistentDataType.STRING)).thenReturn(player.getUniqueId().toString());
        when(mockStand.getPersistentDataContainer()).thenReturn(pdc);

        BrewerInventory inv = mock(BrewerInventory.class);
        when(mockStand.getInventory()).thenReturn(inv);

        when(inv.getIngredient()).thenReturn(new ItemStack(Material.EMERALD_BLOCK));
        when(inv.getItem(0)).thenReturn(new ItemStack(Material.GLASS_BOTTLE));
        when(inv.getItem(1)).thenReturn(null);
        when(inv.getItem(2)).thenReturn(null);

        BrewEvent event = mockBrewEvent(mockBlock, inv);
        server.getPluginManager().callEvent(event);
        server.getScheduler().performOneTick();

        // Bottle stores the emerald-block orb value, not the emerald value.
        verify(inv).setItem(eq(0), argThat(item -> item != null
                && listener.xpBottle().isXpBottle(item)
                && listener.xpBottle().getStoredOrbs(item) == listener.orbsPerEmeraldBlock()));
        assertEquals(50000 - listener.orbsPerEmeraldBlock(),
                new ExperienceManager(player).getCurrentExp());
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