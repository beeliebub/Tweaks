package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.AugmentEntry;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import me.beeliebub.tweaks.tools.augments.AugmentTableListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AugmentTableListenerTest {

    private ServerMock server;
    private Tweaks plugin;
    private AugmentService augments;
    private AugmentTableListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        augments = new AugmentService(plugin, null);
        listener = new AugmentTableListener(plugin, augments);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void deferredHandlerLeavesVanillaMapAndConvertsAfterOneTick() {
        PlayerMock player = server.addPlayer("TableTester");
        EnchantingInventory inventory = enchantingInventory(player);
        ItemStack item = enchanted(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 5);
        inventory.setItem(item);
        Map<Enchantment, Integer> rolled = new LinkedHashMap<>();
        rolled.put(Enchantment.EFFICIENCY, 5);
        EnchantItemEvent event = event(player, inventory, rolled);

        listener.onEnchant(event);

        assertEquals(Map.of(Enchantment.EFFICIENCY, 5), rolled);
        assertTrue(item.containsEnchantment(Enchantment.EFFICIENCY));
        server.getScheduler().performOneTick();

        assertTrue(inventory.getItem().getEnchantments().isEmpty());
        assertEquals(1, augments.inventoryGems(player).size());
    }

    @Test
    void plainBookResultAcceptsVanillaStoredEnchantmentRepresentation() {
        PlayerMock player = server.addPlayer("PlainBookTester");
        EnchantingInventory inventory = enchantingInventory(player);
        ItemStack plainBook = new ItemStack(Material.BOOK);
        inventory.setItem(plainBook);
        Map<Enchantment, Integer> rolled = Map.of(Enchantment.EFFICIENCY, 5);
        listener.onEnchant(event(player, inventory, rolled));

        ItemStack vanillaResult = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta storage = (EnchantmentStorageMeta) vanillaResult.getItemMeta();
        storage.addStoredEnchant(Enchantment.EFFICIENCY, 5, true);
        vanillaResult.setItemMeta(storage);
        inventory.setItem(vanillaResult);
        server.getScheduler().performOneTick();

        assertEquals(Material.BOOK, inventory.getItem().getType());
        assertTrue(augments.inventoryGems(player).size() == 1);
        assertTrue(((EnchantmentStorageMeta) vanillaResult.getItemMeta()).hasStoredEnchants());
    }

    @Test
    void cursorSourceIsWrittenBackAndFullInventoryRefusesBeforeStripping() {
        PlayerMock player = server.addPlayer("CursorTester");
        EnchantingInventory inventory = enchantingInventory(player);
        ItemStack cursor = enchanted(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 5);
        player.setItemOnCursor(cursor);
        Map<Enchantment, Integer> rolled = Map.of(Enchantment.EFFICIENCY, 5);
        listener.onEnchant(event(player, inventory, rolled));
        server.getScheduler().performOneTick();

        assertTrue(player.getItemOnCursor().getEnchantments().isEmpty());
        assertEquals(1, augments.inventoryGems(player).size());

        for (int i = 0; i < player.getInventory().getStorageContents().length; i++) {
            player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        }
        ItemStack full = enchanted(Material.DIAMOND_PICKAXE, Enchantment.UNBREAKING, 3);
        assertTrue(!listener.strip(player, full, Map.of(Enchantment.UNBREAKING, 3)));
        assertEquals(3, full.getEnchantmentLevel(Enchantment.UNBREAKING));
    }

    @Test
    void activeLedgerEnchantmentsAreRestoredAfterTheTableStrip() {
        PlayerMock player = server.addPlayer("ResyncTester");
        ItemStack item = enchanted(Material.DIAMOND_PICKAXE, Enchantment.UNBREAKING, 3);
        augments.ledger().write(item, 2,
                List.of(new AugmentEntry(NamespacedKey.minecraft("efficiency"), 5, true)), true);

        assertTrue(listener.strip(player, item, Map.of(Enchantment.UNBREAKING, 3)));
        assertEquals(5, item.getEnchantmentLevel(Enchantment.EFFICIENCY));
        assertTrue(item.getEnchantments().containsKey(Enchantment.EFFICIENCY));
    }

    @Test
    void deferredConversionIsIgnoredWhenTheTableViewIsReopened() {
        PlayerMock player = server.addPlayer("ReopenedTableTester");
        EnchantingInventory inventory = enchantingInventory(player);
        ItemStack item = enchanted(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 5);
        inventory.setItem(item);
        Map<Enchantment, Integer> rolled = Map.of(Enchantment.EFFICIENCY, 5);

        listener.onEnchant(event(player, inventory, rolled));
        player.closeInventory();
        player.openInventory(inventory);
        server.getScheduler().performOneTick();

        assertTrue(inventory.getItem().containsEnchantment(Enchantment.EFFICIENCY));
        assertTrue(augments.inventoryGems(player).isEmpty());
    }

    @Test
    void deferredFullInventoryRefusalReportsThatTheVanillaResultWasKept() {
        PlayerMock player = server.addPlayer("FullTableTester");
        while (player.nextComponentMessage() != null) {}
        EnchantingInventory inventory = enchantingInventory(player);
        ItemStack item = enchanted(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 5);
        inventory.setItem(item);
        for (int i = 0; i < player.getInventory().getStorageContents().length; i++) {
            player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        }

        EnchantItemEvent event = event(player, inventory, Map.of(Enchantment.EFFICIENCY, 5));
        listener.onEnchant(event);
        verify(event).setCancelled(true);
        server.getScheduler().performOneTick();

        assertTrue(item.containsEnchantment(Enchantment.EFFICIENCY));
        assertTrue(player.nextComponentMessage() != null);
    }

    private EnchantingInventory enchantingInventory(PlayerMock player) {
        EnchantingInventory inventory = (EnchantingInventory) Bukkit.createInventory(null, InventoryType.ENCHANTING);
        player.openInventory(inventory);
        return inventory;
    }

    private EnchantItemEvent event(PlayerMock player, EnchantingInventory inventory,
                                   Map<Enchantment, Integer> rolled) {
        EnchantItemEvent event = mock(EnchantItemEvent.class);
        when(event.getEnchanter()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getEnchantsToAdd()).thenReturn(rolled);
        ItemStack eventItem = (inventory.getItem() != null ? inventory.getItem() : player.getItemOnCursor()).clone();
        rolled.keySet().forEach(eventItem::removeEnchantment);
        when(event.getItem()).thenReturn(eventItem);
        return event;
    }

    private static ItemStack enchanted(Material material, Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(material);
        item.addUnsafeEnchantment(enchantment, level);
        return item;
    }
}
