package me.beeliebub.tweaks.tests.enchantments;

import me.beeliebub.tweaks.enchantments.AnvilListener;
import me.beeliebub.tweaks.enchantments.EggCollector;
import me.beeliebub.tweaks.enchantments.SpawnerPickup;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.mockito.Mockito.*;

class AnvilListenerTest {

    private SpawnerPickup spawnerPickup;
    private EggCollector eggCollector;
    private AnvilListener listener;

    @BeforeEach
    void setUp() {
        // Real ItemStack handling needs the Material registry.
        MockBukkit.mock();
        spawnerPickup = mock(SpawnerPickup.class);
        eggCollector = mock(EggCollector.class);
        listener = new AnvilListener(spawnerPickup, eggCollector);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PrepareAnvilEvent eventWith(ItemStack first, ItemStack second) {
        AnvilInventory inv = mock(AnvilInventory.class);
        when(inv.getItem(0)).thenReturn(first);
        when(inv.getItem(1)).thenReturn(second);
        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        when(event.getInventory()).thenReturn(inv);
        return event;
    }

    @Test
    void allowsAnvilWhenBothSlotsEmpty() {
        PrepareAnvilEvent event = eventWith(null, null);
        listener.onPrepareAnvil(event);
        verify(event, never()).setResult(null);
    }

    @Test
    void allowsAnvilForToolWithoutCustomEnchants() {
        Enchantment spEnch = mock(Enchantment.class);
        Enchantment ecEnch = mock(Enchantment.class);
        when(spawnerPickup.getEnchantment()).thenReturn(spEnch);
        when(eggCollector.getEnchantment()).thenReturn(ecEnch);

        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        PrepareAnvilEvent event = eventWith(tool, null);
        listener.onPrepareAnvil(event);
        verify(event, never()).setResult(null);
    }

    @Test
    void blocksAnvilWhenToolCarriesSpawnerPickup() {
        // Use Enchantment.UNBREAKING as a stand-in real enchantment — the listener only cares
        // whether `getEnchantment()` returns something the tool actually contains.
        when(spawnerPickup.getEnchantment()).thenReturn(Enchantment.UNBREAKING);
        when(eggCollector.getEnchantment()).thenReturn(null);

        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        tool.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);

        PrepareAnvilEvent event = eventWith(tool, null);
        listener.onPrepareAnvil(event);
        verify(event).setResult(null);
    }

    @Test
    void blocksAnvilWhenToolCarriesEggCollector() {
        when(spawnerPickup.getEnchantment()).thenReturn(null);
        when(eggCollector.getEnchantment()).thenReturn(Enchantment.SHARPNESS);

        ItemStack tool = new ItemStack(Material.DIAMOND_SWORD);
        tool.addUnsafeEnchantment(Enchantment.SHARPNESS, 1);

        PrepareAnvilEvent event = eventWith(null, tool);
        listener.onPrepareAnvil(event);
        verify(event).setResult(null);
    }

    @Test
    void enchantedBookIsExemptEvenWhenItHoldsTheEnchant() {
        when(spawnerPickup.getEnchantment()).thenReturn(Enchantment.UNBREAKING);
        when(eggCollector.getEnchantment()).thenReturn(null);

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        book.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);

        PrepareAnvilEvent event = eventWith(book, null);
        listener.onPrepareAnvil(event);
        verify(event, never()).setResult(null);
    }

    @Test
    void airSlotsAreIgnored() {
        // Default mock returns null/AIR for both slots; second call exercises the branch where
        // Material.AIR slips through getItem() (which technically isn't supposed to happen but
        // is defended against in production).
        ItemStack airStack = new ItemStack(Material.AIR);
        PrepareAnvilEvent event = eventWith(airStack, airStack);
        listener.onPrepareAnvil(event);
        verify(event, never()).setResult(null);
    }
}
