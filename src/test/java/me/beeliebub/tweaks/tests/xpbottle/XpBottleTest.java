package me.beeliebub.tweaks.tests.xpbottle;

import me.beeliebub.tweaks.xpbottle.XpBottle;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class XpBottleTest {

    private final NamespacedKey orbsKey = mock(NamespacedKey.class);
    private final XpBottle bottle = new XpBottle(orbsKey);

    @Test
    void orbsKeyAccessorReturnsConstructorArg() {
        assertSame(orbsKey, bottle.orbsKey());
    }

    @Test
    void isXpBottleRejectsNull() {
        assertFalse(bottle.isXpBottle(null));
    }

    @Test
    void isXpBottleRejectsWrongMaterial() {
        ItemStack wrong = mock(ItemStack.class);
        when(wrong.getType()).thenReturn(Material.GOLDEN_APPLE);
        assertFalse(bottle.isXpBottle(wrong));
    }

    @Test
    void isXpBottleRejectsPotionWithoutPdcMarker() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.getType()).thenReturn(Material.POTION);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(eq(orbsKey), eq(PersistentDataType.INTEGER))).thenReturn(false);

        assertFalse(bottle.isXpBottle(stack));
    }

    @Test
    void isXpBottleAcceptsPotionWithPdcMarker() {
        ItemStack stack = stackWithOrbs(0);
        assertTrue(bottle.isXpBottle(stack));
    }

    @Test
    void getStoredOrbsReturnsZeroWhenNotAnXpBottle() {
        ItemStack wrong = mock(ItemStack.class);
        when(wrong.getType()).thenReturn(Material.STONE);
        assertEquals(0, bottle.getStoredOrbs(wrong));
    }

    @Test
    void getStoredOrbsReturnsPdcInteger() {
        ItemStack stack = stackWithOrbs(1395);
        assertEquals(1395, bottle.getStoredOrbs(stack));
    }

    @Test
    void getStoredOrbsReturnsZeroWhenPdcValueIsNull() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.getType()).thenReturn(Material.POTION);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(eq(orbsKey), eq(PersistentDataType.INTEGER))).thenReturn(true);
        when(pdc.get(eq(orbsKey), eq(PersistentDataType.INTEGER))).thenReturn(null);

        assertEquals(0, bottle.getStoredOrbs(stack));
    }

    private ItemStack stackWithOrbs(int orbs) {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.getType()).thenReturn(Material.POTION);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(eq(orbsKey), eq(PersistentDataType.INTEGER))).thenReturn(true);
        when(pdc.get(eq(orbsKey), eq(PersistentDataType.INTEGER))).thenReturn(orbs);
        return stack;
    }
}
