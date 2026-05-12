package me.beeliebub.tweaks.tests;

import me.beeliebub.tweaks.InventoryUtil;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryUtilTest {

    @Test
    void toBase64NullReturnsEmptyString() {
        assertEquals("", InventoryUtil.toBase64(null));
    }

    @Test
    void toBase64EmptyArrayReturnsEmptyString() {
        assertEquals("", InventoryUtil.toBase64(new ItemStack[0]));
    }

    @Test
    void fromBase64NullReturnsEmptyArray() {
        ItemStack[] result = InventoryUtil.fromBase64(null);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    void fromBase64EmptyStringReturnsEmptyArray() {
        ItemStack[] result = InventoryUtil.fromBase64("");
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    void toBase64EncodesPaperSerializedBytes() {
        ItemStack stack = mock(ItemStack.class);
        ItemStack[] items = {stack};
        byte[] payload = {1, 2, 3, 4, 5};

        try (MockedStatic<ItemStack> mocked = mockStatic(ItemStack.class)) {
            mocked.when(() -> ItemStack.serializeItemsAsBytes(items)).thenReturn(payload);
            String encoded = InventoryUtil.toBase64(items);
            assertEquals(Base64.getEncoder().encodeToString(payload), encoded);
        }
    }

    @Test
    void fromBase64DecodesAndDelegatesToPaperDeserializer() {
        byte[] payload = {9, 8, 7, 6};
        String encoded = Base64.getEncoder().encodeToString(payload);
        ItemStack[] expected = {mock(ItemStack.class)};

        try (MockedStatic<ItemStack> mocked = mockStatic(ItemStack.class)) {
            mocked.when(() -> ItemStack.deserializeItemsFromBytes(any(byte[].class))).thenReturn(expected);
            ItemStack[] result = InventoryUtil.fromBase64(encoded);
            assertSame(expected, result);
            mocked.verify(() -> ItemStack.deserializeItemsFromBytes(payload));
        }
    }

    @Test
    void toBase64WrapsSerializerExceptionsAsIllegalState() {
        ItemStack[] items = {mock(ItemStack.class)};
        try (MockedStatic<ItemStack> mocked = mockStatic(ItemStack.class)) {
            mocked.when(() -> ItemStack.serializeItemsAsBytes(items))
                    .thenThrow(new RuntimeException("boom"));
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> InventoryUtil.toBase64(items));
            assertTrue(ex.getMessage().contains("serialize"));
        }
    }

    @Test
    void fromBase64WrapsDecoderExceptionsAsIllegalState() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> InventoryUtil.fromBase64("!!!not-base64!!!"));
        assertTrue(ex.getMessage().contains("decode"));
    }
}