package me.beeliebub.tweaks.tests.blocklog;

import me.beeliebub.tweaks.blocklog.ChestLogEntry;
import me.beeliebub.tweaks.blocklog.LogAction;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChestLogCodecTest {

    private static Method encode;
    private static Method decode;

    static {
        try {
            Class<?> codec = Class.forName("me.beeliebub.tweaks.blocklog.ChestLogCodec");
            encode = codec.getDeclaredMethod("encode", List.class);
            decode = codec.getDeclaredMethod("decode", byte[].class);
            encode.setAccessible(true);
            decode.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static byte[] encode(List<ChestLogEntry> entries) throws Exception {
        return (byte[]) encode.invoke(null, entries);
    }

    @SuppressWarnings("unchecked")
    private static List<ChestLogEntry> decode(byte[] bytes) throws Exception {
        return (List<ChestLogEntry>) decode.invoke(null, (Object) bytes);
    }

    @Test
    void emptyListEncodesToVersionPlusZeroCount() throws Exception {
        byte[] out = encode(new ArrayList<>());
        // 1 byte version + 4 bytes count
        assertEquals(5, out.length);
        assertEquals(1, out[0], "format version byte");
        assertEquals(0, out[4], "count low byte");
    }

    @Test
    void decodeNullReturnsEmptyList() throws Exception {
        assertTrue(decode(null).isEmpty());
    }

    @Test
    void decodeEmptyArrayReturnsEmptyList() throws Exception {
        assertTrue(decode(new byte[0]).isEmpty());
    }

    @Test
    void decodeUnknownVersionReturnsEmptyList() throws Exception {
        byte[] bogus = new byte[]{99, 0, 0, 0, 0};
        assertTrue(decode(bogus).isEmpty());
    }

    @Test
    void roundTripsSingleEntry() throws Exception {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        ItemStack item = mock(ItemStack.class);
        byte[] payload = {7, 8, 9};

        try (MockedStatic<ItemStack> mocked = mockStatic(ItemStack.class)) {
            when(item.serializeAsBytes()).thenReturn(payload);
            mocked.when(() -> ItemStack.deserializeBytes(any(byte[].class))).thenReturn(item);

            List<ChestLogEntry> input = List.of(
                    new ChestLogEntry(987654321L, LogAction.REMOVE, uuid, "Bee", item)
            );
            byte[] encoded = encode(input);
            List<ChestLogEntry> decoded = decode(encoded);

            assertEquals(1, decoded.size());
            ChestLogEntry e = decoded.get(0);
            assertEquals(987654321L, e.timestamp());
            assertEquals(LogAction.REMOVE, e.action());
            assertEquals(uuid, e.playerUuid());
            assertEquals("Bee", e.playerName());
        }
    }

    @Test
    void roundTripsMultipleEntriesPreservesOrder() throws Exception {
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ItemStack item = mock(ItemStack.class);
        when(item.serializeAsBytes()).thenReturn(new byte[]{1});

        try (MockedStatic<ItemStack> mocked = mockStatic(ItemStack.class)) {
            mocked.when(() -> ItemStack.deserializeBytes(any(byte[].class))).thenReturn(item);

            List<ChestLogEntry> input = List.of(
                    new ChestLogEntry(1L, LogAction.ADD, u1, "Alpha", item),
                    new ChestLogEntry(2L, LogAction.REMOVE, u2, "Beta", item)
            );
            List<ChestLogEntry> decoded = decode(encode(input));

            assertEquals(2, decoded.size());
            assertEquals(1L, decoded.get(0).timestamp());
            assertEquals("Alpha", decoded.get(0).playerName());
            assertEquals(LogAction.ADD, decoded.get(0).action());
            assertEquals(u1, decoded.get(0).playerUuid());

            assertEquals(2L, decoded.get(1).timestamp());
            assertEquals("Beta", decoded.get(1).playerName());
            assertEquals(LogAction.REMOVE, decoded.get(1).action());
            assertEquals(u2, decoded.get(1).playerUuid());
        }
    }

    @Test
    void roundTripPreservesUtf8PlayerName() throws Exception {
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ItemStack item = mock(ItemStack.class);
        when(item.serializeAsBytes()).thenReturn(new byte[]{0});

        try (MockedStatic<ItemStack> mocked = mockStatic(ItemStack.class)) {
            mocked.when(() -> ItemStack.deserializeBytes(any(byte[].class))).thenReturn(item);

            String name = "蜂🐝Bee";
            ChestLogEntry input = new ChestLogEntry(0L, LogAction.ADD, u1, name, item);
            List<ChestLogEntry> decoded = decode(encode(List.of(input)));
            assertEquals(name, decoded.get(0).playerName());
        }
    }

    @Test
    void truncatedDataReturnsEmptyListWithoutThrowing() throws Exception {
        // Version byte + start of count, but truncated mid-stream.
        byte[] truncated = new byte[]{1, 0, 0, 0, 5, 0, 0};
        List<ChestLogEntry> result = decode(truncated);
        assertNotNull(result);
        // The codec swallows IOException and returns whatever it managed to read (here, none).
        assertTrue(result.isEmpty());
    }
}
