package me.beeliebub.tweaks.tests.recipes;

import me.beeliebub.tweaks.recipes.ResourceRupee;
import me.beeliebub.tweaks.recipes.ResourceRupeeListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResourceRupeeListenerTest {

    private ResourceRupee rupee;
    private ResourceRupeeListener listener;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        rupee = new ResourceRupee();
        listener = new ResourceRupeeListener(rupee);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void mintedRupeeCarriesExactComponents() {
        ItemStack stack = rupee.createRupee(46);
        assertEquals(Material.EMERALD, stack.getType());
        assertEquals(46, stack.getAmount());

        ItemMeta meta = stack.getItemMeta();
        assertNotNull(meta);

        // item_name: "Resource Rupee" green
        var itemName = meta.itemName();
        assertNotNull(itemName);
        assertEquals(ResourceRupee.RUPEE_NAME, PlainTextComponentSerializer.plainText().serialize(itemName));
        assertEquals(NamedTextColor.GREEN, itemName.color());

        // lore: one line "...the Wanderer's Path..." dark_green italic:false
        assertTrue(meta.hasLore());
        var lore = meta.lore();
        assertNotNull(lore);
        assertEquals(1, lore.size());
        var loreLine = lore.get(0);
        assertEquals(ResourceRupee.LORE_LINE, PlainTextComponentSerializer.plainText().serialize(loreLine));
        assertEquals(NamedTextColor.DARK_GREEN, loreLine.color());
        assertEquals(net.kyori.adventure.text.format.TextDecoration.State.FALSE,
                loreLine.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC));

        // enchantment_glint_override: true
        assertTrue(meta.hasEnchantmentGlintOverride());
        assertEquals(Boolean.TRUE, meta.getEnchantmentGlintOverride());

        // No leftover PDC marker — only the three components listed above should be present.
        assertTrue(meta.getPersistentDataContainer().isEmpty());
    }

    @Test
    void mintedRupeeBlockCarriesExactComponents() {
        ItemStack stack = rupee.createRupeeBlock(1);
        assertEquals(Material.EMERALD_BLOCK, stack.getType());

        ItemMeta meta = stack.getItemMeta();
        assertNotNull(meta);

        var itemName = meta.itemName();
        assertNotNull(itemName);
        assertEquals(ResourceRupee.RUPEE_BLOCK_NAME, PlainTextComponentSerializer.plainText().serialize(itemName));
        assertEquals(NamedTextColor.GREEN, itemName.color());

        assertTrue(meta.hasEnchantmentGlintOverride());
        assertEquals(Boolean.TRUE, meta.getEnchantmentGlintOverride());

        assertTrue(meta.getPersistentDataContainer().isEmpty());
    }

    @Test
    void nineRupeesProduceRupeeBlock() {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) matrix[i] = namedRupee(Material.EMERALD, ResourceRupee.RUPEE_NAME);

        CraftingInventory inv = mock(CraftingInventory.class);
        when(inv.getMatrix()).thenReturn(matrix);

        PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
        when(event.getInventory()).thenReturn(inv);

        listener.onPrepareCraft(event);

        ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
        verify(inv).setResult(captor.capture());
        ItemStack result = captor.getValue();
        assertEquals(Material.EMERALD_BLOCK, result.getType());
        assertEquals(1, result.getAmount());
        assertTrue(rupee.isRupeeBlock(result));
    }

    @Test
    void nineExternallySourcedRupeesProduceRupeeBlock() {
        // Externally-sourced rupees carry name + lore via displayName (not itemName) and have no
        // PDC marker. They must still convert correctly.
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) matrix[i] = externalRupee(Material.EMERALD, ResourceRupee.RUPEE_NAME);

        CraftingInventory inv = mock(CraftingInventory.class);
        when(inv.getMatrix()).thenReturn(matrix);

        PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
        when(event.getInventory()).thenReturn(inv);

        listener.onPrepareCraft(event);

        ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
        verify(inv).setResult(captor.capture());
        ItemStack result = captor.getValue();
        assertEquals(Material.EMERALD_BLOCK, result.getType());
        assertEquals(1, result.getAmount());
    }

    @Test
    void singleRupeeBlockProducesNineRupees() {
        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = namedRupee(Material.EMERALD_BLOCK, ResourceRupee.RUPEE_BLOCK_NAME);

        CraftingInventory inv = mock(CraftingInventory.class);
        when(inv.getMatrix()).thenReturn(matrix);

        PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
        when(event.getInventory()).thenReturn(inv);

        listener.onPrepareCraft(event);

        ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
        verify(inv).setResult(captor.capture());
        ItemStack result = captor.getValue();
        assertEquals(Material.EMERALD, result.getType());
        assertEquals(9, result.getAmount());
        assertTrue(rupee.isRupee(result));
    }

    @Test
    void singleExternallySourcedRupeeBlockProducesNineRupees() {
        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = externalRupee(Material.EMERALD_BLOCK, ResourceRupee.RUPEE_BLOCK_NAME);

        CraftingInventory inv = mock(CraftingInventory.class);
        when(inv.getMatrix()).thenReturn(matrix);

        PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
        when(event.getInventory()).thenReturn(inv);

        listener.onPrepareCraft(event);

        ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
        verify(inv).setResult(captor.capture());
        ItemStack result = captor.getValue();
        assertEquals(Material.EMERALD, result.getType());
        assertEquals(9, result.getAmount());
    }

    @Test
    void plainEmeraldsAreNotOverridden() {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) matrix[i] = plainItem(Material.EMERALD);

        CraftingInventory inv = mock(CraftingInventory.class);
        when(inv.getMatrix()).thenReturn(matrix);

        PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
        when(event.getInventory()).thenReturn(inv);

        listener.onPrepareCraft(event);

        verify(inv, never()).setResult(any());
    }

    @Test
    void mixedGridIsNotOverridden() {
        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = plainItem(Material.EMERALD);
        for (int i = 1; i < 9; i++) matrix[i] = namedRupee(Material.EMERALD, ResourceRupee.RUPEE_NAME);

        CraftingInventory inv = mock(CraftingInventory.class);
        when(inv.getMatrix()).thenReturn(matrix);

        PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
        when(event.getInventory()).thenReturn(inv);

        listener.onPrepareCraft(event);

        verify(inv, never()).setResult(any());
    }

    @Test
    void plainEmeraldBlockIsNotOverridden() {
        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = plainItem(Material.EMERALD_BLOCK);

        CraftingInventory inv = mock(CraftingInventory.class);
        when(inv.getMatrix()).thenReturn(matrix);

        PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
        when(event.getInventory()).thenReturn(inv);

        listener.onPrepareCraft(event);

        verify(inv, never()).setResult(any());
    }

    @Test
    void emptyMatrixIsIgnored() {
        ItemStack[] matrix = new ItemStack[9];

        CraftingInventory inv = mock(CraftingInventory.class);
        when(inv.getMatrix()).thenReturn(matrix);

        PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
        when(event.getInventory()).thenReturn(inv);

        listener.onPrepareCraft(event);

        verify(inv, never()).setResult(any());
    }

    @Test
    void nullMatrixIsIgnored() {
        CraftingInventory inv = mock(CraftingInventory.class);
        when(inv.getMatrix()).thenReturn(null);

        PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
        when(event.getInventory()).thenReturn(inv);

        listener.onPrepareCraft(event);

        verify(inv, never()).setResult(any());
    }

    // Rupee produced by this plugin's factory: itemName component, matching lore.
    private static ItemStack namedRupee(Material material, String name) {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(stack.getType()).thenReturn(material);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.itemName()).thenReturn(Component.text(name, NamedTextColor.GREEN));
        when(meta.hasDisplayName()).thenReturn(false);
        when(meta.hasLore()).thenReturn(true);
        when(meta.lore()).thenReturn(List.of(Component.text(ResourceRupee.LORE_LINE, NamedTextColor.DARK_GREEN)));
        return stack;
    }

    // Externally-sourced rupee: displayName component, matching lore. No PDC marker.
    private static ItemStack externalRupee(Material material, String name) {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(stack.getType()).thenReturn(material);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.itemName()).thenReturn(null);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.displayName()).thenReturn(Component.text(name, NamedTextColor.GREEN));
        when(meta.hasLore()).thenReturn(true);
        when(meta.lore()).thenReturn(List.of(Component.text(ResourceRupee.LORE_LINE, NamedTextColor.DARK_GREEN)));
        return stack;
    }

    private static ItemStack plainItem(Material material) {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(stack.getType()).thenReturn(material);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.itemName()).thenReturn(null);
        when(meta.hasDisplayName()).thenReturn(false);
        when(meta.hasLore()).thenReturn(false);
        return stack;
    }
}