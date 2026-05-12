package me.beeliebub.tweaks.tests.listeners;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.listeners.VillagerTradeListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.InventoryViewMock;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillagerTradeListenerTest {

    private ServerMock server;
    private Tweaks plugin;
    private VillagerTradeListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        listener = new VillagerTradeListener();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ItemStack createLoreEmerald() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.lore(Collections.singletonList(Component.text("Special Lore")));
        item.setItemMeta(meta);
        return item;
    }

    @Test
    void onClickRejectsLoreEmeraldInCostSlot() {
        PlayerMock player = server.addPlayer();
        Villager villager = mock(Villager.class);
        MerchantInventory inventory = mock(MerchantInventory.class);
        when(inventory.getMerchant()).thenReturn(villager);

        ItemStack loreEmerald = createLoreEmerald();
        InventoryClickEvent event = new InventoryClickEvent(
                player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 0,
                org.bukkit.event.inventory.ClickType.LEFT, InventoryAction.PLACE_ALL
        );
        // Using reflection or mocking to set the inventory and cursor is hard with real event objects
        // Let's try mocking the event as it's often easier for inventory events in MockBukkit
        InventoryClickEvent mockEvent = mock(InventoryClickEvent.class);
        when(mockEvent.getInventory()).thenReturn(inventory);
        when(mockEvent.getWhoClicked()).thenReturn(player);
        when(mockEvent.getRawSlot()).thenReturn(0);
        when(mockEvent.getAction()).thenReturn(InventoryAction.PLACE_ALL);
        when(mockEvent.getCursor()).thenReturn(loreEmerald);

        listener.onClick(mockEvent);

        verify(mockEvent).setCancelled(true);
        assertNotNull(player.nextMessage());
    }

    @Test
    void onClickAllowsNormalEmerald() {
        PlayerMock player = server.addPlayer();
        Villager villager = mock(Villager.class);
        MerchantInventory inventory = mock(MerchantInventory.class);
        when(inventory.getMerchant()).thenReturn(villager);

        ItemStack normalEmerald = new ItemStack(Material.EMERALD);
        InventoryClickEvent mockEvent = mock(InventoryClickEvent.class);
        when(mockEvent.getInventory()).thenReturn(inventory);
        when(mockEvent.getWhoClicked()).thenReturn(player);
        when(mockEvent.getRawSlot()).thenReturn(0);
        when(mockEvent.getAction()).thenReturn(InventoryAction.PLACE_ALL);
        when(mockEvent.getCursor()).thenReturn(normalEmerald);

        listener.onClick(mockEvent);

        verify(mockEvent, never()).setCancelled(true);
    }

    @Test
    void onClickAllowsWanderingTrader() {
        PlayerMock player = server.addPlayer();
        WanderingTrader trader = mock(WanderingTrader.class);
        MerchantInventory inventory = mock(MerchantInventory.class);
        when(inventory.getMerchant()).thenReturn(trader);

        ItemStack loreEmerald = createLoreEmerald();
        InventoryClickEvent mockEvent = mock(InventoryClickEvent.class);
        when(mockEvent.getInventory()).thenReturn(inventory);
        when(mockEvent.getWhoClicked()).thenReturn(player);
        when(mockEvent.getRawSlot()).thenReturn(0);
        when(mockEvent.getAction()).thenReturn(InventoryAction.PLACE_ALL);
        when(mockEvent.getCursor()).thenReturn(loreEmerald);

        listener.onClick(mockEvent);

        verify(mockEvent, never()).setCancelled(true);
    }

    @Test
    void onDragRejectsLoreEmeraldInCostSlot() {
        PlayerMock player = server.addPlayer();
        Villager villager = mock(Villager.class);
        MerchantInventory inventory = mock(MerchantInventory.class);
        when(inventory.getMerchant()).thenReturn(villager);

        ItemStack loreEmerald = createLoreEmerald();
        InventoryDragEvent mockEvent = mock(InventoryDragEvent.class);
        when(mockEvent.getInventory()).thenReturn(inventory);
        when(mockEvent.getWhoClicked()).thenReturn(player);
        when(mockEvent.getRawSlots()).thenReturn(Collections.singleton(0));
        when(mockEvent.getOldCursor()).thenReturn(loreEmerald);

        listener.onDrag(mockEvent);

        verify(mockEvent).setCancelled(true);
    }
}
