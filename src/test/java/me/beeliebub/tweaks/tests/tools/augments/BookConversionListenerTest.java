package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tests.MessageAssert;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import me.beeliebub.tweaks.tools.augments.BookConversionListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookConversionListenerTest {

    private ServerMock server;
    private Tweaks plugin;
    private AugmentService augments;
    private BookConversionListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        augments = new AugmentService(plugin, null);
        listener = new BookConversionListener(plugin, augments);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void inventoryMoveConsumesTheVerifiedSourceStackBeforeGrantingGems() {
        Player player = server.addPlayer("BookMover");
        Inventory source = Bukkit.createInventory(null, 9);
        ItemStack sourceStack = enchantedBook(3);
        source.setItem(0, sourceStack);
        ItemStack moved = sourceStack.clone();
        moved.setAmount(1);

        InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
        when(event.getDestination()).thenReturn(player.getInventory());
        when(event.getSource()).thenReturn(source);
        when(event.getItem()).thenReturn(moved);

        listener.onInventoryMove(event);

        verify(event).setCancelled(true);
        assertEquals(2, source.getItem(0).getAmount());
        assertEquals(1, augments.inventoryGems(player).getFirst().item().getAmount());
    }

    @Test
    void shiftClickConvertsOnlyTheAmountThatEnteredAnExistingSimilarStack() {
        Player player = server.addPlayer("ClickMover");
        Inventory top = Bukkit.createInventory(null, 9);
        player.openInventory(top);
        ItemStack source = enchantedBook(2);
        ItemStack existing = enchantedBook(5);
        top.setItem(0, source);
        player.getInventory().setItem(1, existing);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(event.getRawSlot()).thenReturn(0);
        when(event.getCurrentItem()).thenReturn(source.clone());
        when(event.getCursor()).thenReturn(null);
        when(event.getHotbarButton()).thenReturn(-1);

        listener.onClick(event);
        top.setItem(0, null);
        ItemStack merged = existing.clone();
        merged.setAmount(7);
        player.getInventory().setItem(1, merged);
        server.getScheduler().performOneTick();

        assertEquals(5, player.getInventory().getItem(1).getAmount());
        assertEquals(2, augments.inventoryGems(player).getFirst().item().getAmount());
    }

    @Test
    void dragReplacementIsIgnoredWhenTheCapturedPostEventStackIsGone() {
        Player player = server.addPlayer("DragMover");
        Inventory top = Bukkit.createInventory(null, 9);
        player.openInventory(top);
        ItemStack expected = enchantedBook(1);
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getOldCursor()).thenReturn(expected.clone());
        when(event.getNewItems()).thenReturn(Map.of(10, expected.clone()));

        listener.onDrag(event);
        player.getInventory().setItem(1, new ItemStack(Material.STONE));
        server.getScheduler().performOneTick();

        assertTrue(augments.inventoryGems(player).isEmpty());
        assertEquals(Material.STONE, player.getInventory().getItem(1).getType());
    }

    @Test
    void clickConversionIsIgnoredWhenTheInventoryViewIsReopened() {
        Player player = server.addPlayer("ReopenedClickMover");
        Inventory top = Bukkit.createInventory(null, 9);
        player.openInventory(top);
        ItemStack source = enchantedBook(1);
        top.setItem(0, source);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(event.getRawSlot()).thenReturn(0);
        when(event.getCurrentItem()).thenReturn(source.clone());
        when(event.getCursor()).thenReturn(null);
        when(event.getHotbarButton()).thenReturn(-1);

        listener.onClick(event);
        player.closeInventory();
        player.openInventory(top);
        server.getScheduler().performOneTick();

        assertTrue(augments.inventoryGems(player).isEmpty());
    }

    @Test
    void deferredOverflowRefusalReportsAfterTheMovedBookIsVerified() {
        PlayerMock player = server.addPlayer("OverflowBookMover");
        Inventory top = Bukkit.createInventory(null, 9);
        player.openInventory(top);
        ItemStack source = enchantedBook(1);
        top.setItem(0, source);

        AugmentService refusalService = mock(AugmentService.class);
        when(refusalService.createGemBatchResult(anyMap(), any(Random.class)))
                .thenReturn(AugmentService.GemBatchResult.refused(
                        AugmentService.GemBatchFailure.TOO_MANY_CURSE_RIDERS));
        BookConversionListener refusalListener = new BookConversionListener(plugin, refusalService);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(event.getRawSlot()).thenReturn(0);
        when(event.getCurrentItem()).thenReturn(source.clone());
        when(event.getCursor()).thenReturn(null);
        when(event.getHotbarButton()).thenReturn(-1);

        refusalListener.onClick(event);
        top.setItem(0, null);
        player.getInventory().setItem(0, source.clone());
        server.getScheduler().performOneTick();

        MessageAssert.assertMessageSent(player, "could not be converted safely");
        assertEquals(1, player.getInventory().getItem(0).getAmount());
    }

    @Test
    void fullInventoryPickupLeavesTheEntityAndEventUnchanged() {
        PlayerMock player = server.addPlayer("FullBookPicker");
        while (player.nextComponentMessage() != null) {}
        fillStorage(player);
        ItemStack book = enchantedBook(1);
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(book);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getItem()).thenReturn(entity);

        listener.onPickup(event);

        verify(event, never()).setCancelled(anyBoolean());
        verify(entity, never()).remove();
        assertEquals(1, book.getAmount());
        assertTrue(augments.inventoryGems(player).isEmpty());
        MessageAssert.assertMessageSent(player, "Your inventory does not have enough room");
    }

    @Test
    void fullInventoryMoveLeavesTheSourceAndEventUnchanged() {
        PlayerMock player = server.addPlayer("FullBookMover");
        fillStorage(player);
        Inventory source = Bukkit.createInventory(null, 9);
        ItemStack sourceStack = enchantedBook(2);
        source.setItem(0, sourceStack);
        InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
        when(event.getDestination()).thenReturn(player.getInventory());
        when(event.getSource()).thenReturn(source);
        when(event.getItem()).thenReturn(sourceStack.clone());

        listener.onInventoryMove(event);

        verify(event, never()).setCancelled(anyBoolean());
        assertEquals(2, source.getItem(0).getAmount());
        assertTrue(augments.inventoryGems(player).isEmpty());
    }

    private static void fillStorage(PlayerMock player) {
        for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        }
    }

    private static ItemStack enchantedBook(int amount) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, amount);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(Enchantment.EFFICIENCY, 5, true);
        book.setItemMeta(meta);
        return book;
    }
}
