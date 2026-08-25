package me.beeliebub.tweaks.tests.tools.cash;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.cash.CashItemService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CashItemServiceTest {

    private ServerMock server;
    private Tweaks plugin;
    private CashItemService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        service = new CashItemService(plugin, plugin.getEconomyManager());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void zeroValueIsRecognizedButNeverRemoved() {
        ItemStack cash = integerTagged(0);
        assertEquals(CashItemService.Result.INVALID, service.convert(cash, java.util.UUID.randomUUID()));
        assertEquals(1, cash.getAmount());
    }

    @Test
    void multiplicationOverflowLeavesTheStackUntouched() {
        ItemStack cash = longTagged(Long.MAX_VALUE);
        cash.setAmount(2);

        assertEquals(CashItemService.Result.OVERFLOW, service.convert(cash, java.util.UUID.randomUUID()));
        assertEquals(2, cash.getAmount());
    }

    private ItemStack integerTagged(int value) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "cash"), PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack longTagged(long value) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "cash"), PersistentDataType.LONG, value);
        item.setItemMeta(meta);
        return item;
    }
}
