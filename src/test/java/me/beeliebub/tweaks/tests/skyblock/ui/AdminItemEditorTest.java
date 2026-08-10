package me.beeliebub.tweaks.tests.skyblock.ui;

import me.beeliebub.tweaks.skyblock.ui.admin.AdminItemEditor;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminItemEditorTest {

    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private PlayerMock player;
    private NamespacedKey customKey;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("tweaks");
        player = server.addPlayer("Admin");
        customKey = new NamespacedKey(plugin, "editor-test");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void closeSavesCopyOfCustomItemAndRestoresPlayerInventory() {
        ItemStack original = customItem();
        player.getInventory().setItem(0, original);
        AtomicReference<List<ItemStack>> saved = new AtomicReference<>();
        AdminItemEditor editor = new AdminItemEditor(plugin);

        Inventory inventory = editor.open(player, Component.text("Items"), List.of(), 9,
                ignored -> true, saved::set);
        assertNotNull(inventory);
        assertNull(inventory.getItem(0), "editor starts empty instead of borrowing player inventory");
        assertEquals(original, player.getInventory().getItem(0));

        inventory.setItem(0, original.clone());
        InventoryCloseEvent close = mock(InventoryCloseEvent.class);
        when(close.getPlayer()).thenReturn(player);
        when(close.getInventory()).thenReturn(inventory);
        editor.onClose(close);

        ItemStack savedItem = saved.get().getFirst();
        assertNotSame(original, savedItem);
        assertCustomItem(savedItem);
        assertCustomItem(player.getInventory().getItem(0));
    }

    @Test
    void disconnectDiscardsEditsAndRestoresSnapshot() {
        discardAndAssertRestored(false);
    }

    @Test
    void shutdownDiscardsEditsAndRestoresSnapshot() {
        discardAndAssertRestored(true);
    }

    private void discardAndAssertRestored(boolean shutdown) {
        ItemStack original = customItem();
        player.getInventory().setItem(0, original);
        AtomicReference<List<ItemStack>> saved = new AtomicReference<>();
        AdminItemEditor editor = new AdminItemEditor(plugin);
        Inventory inventory = editor.open(player, Component.text("Items"), List.of(), 9,
                ignored -> true, saved::set);
        inventory.setItem(0, new ItemStack(Material.DIRT));

        if (shutdown) {
            editor.shutdown();
        } else {
            PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(player);
            editor.onQuit(quit);
        }

        assertNull(saved.get(), "discarded sessions must never invoke the save callback");
        assertCustomItem(player.getInventory().getItem(0));
    }

    private ItemStack customItem() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Copied blade"));
        meta.addEnchant(Enchantment.SHARPNESS, 4, true);
        meta.getPersistentDataContainer().set(customKey, PersistentDataType.STRING, "keep-me");
        item.setItemMeta(meta);
        return item;
    }

    private void assertCustomItem(ItemStack item) {
        assertNotNull(item);
        assertEquals(Material.DIAMOND_SWORD, item.getType());
        ItemMeta meta = item.getItemMeta();
        assertEquals(Component.text("Copied blade"), meta.displayName());
        assertEquals(4, meta.getEnchantLevel(Enchantment.SHARPNESS));
        assertEquals("keep-me", meta.getPersistentDataContainer().get(customKey, PersistentDataType.STRING));
    }
}
