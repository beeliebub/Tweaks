package me.beeliebub.tweaks.tests.blocklog;

import me.beeliebub.tweaks.blocklog.ChestLogEntry;
import me.beeliebub.tweaks.blocklog.LogAction;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ChestLogEntryTest {

    private final ItemStack stack = mock(ItemStack.class);
    private final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void recordAccessorsReturnConstructorValues() {
        ChestLogEntry entry = new ChestLogEntry(123L, LogAction.ADD, uuid, "Bee", stack);
        assertEquals(123L, entry.timestamp());
        assertEquals(LogAction.ADD, entry.action());
        assertEquals(uuid, entry.playerUuid());
        assertEquals("Bee", entry.playerName());
        assertSame(stack, entry.item());
    }

    @Test
    void nullActionThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChestLogEntry(0L, null, uuid, "Bee", stack));
    }

    @Test
    void nullUuidThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChestLogEntry(0L, LogAction.ADD, null, "Bee", stack));
    }

    @Test
    void nullItemThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChestLogEntry(0L, LogAction.ADD, uuid, "Bee", null));
    }

    @Test
    void nullPlayerNameNormalizesToEmptyString() {
        ChestLogEntry entry = new ChestLogEntry(0L, LogAction.ADD, uuid, null, stack);
        assertEquals("", entry.playerName());
    }

    @Test
    void recordEqualityAndHashCodeBasedOnComponents() {
        ChestLogEntry a = new ChestLogEntry(1L, LogAction.REMOVE, uuid, "Bee", stack);
        ChestLogEntry b = new ChestLogEntry(1L, LogAction.REMOVE, uuid, "Bee", stack);
        ChestLogEntry diff = new ChestLogEntry(2L, LogAction.REMOVE, uuid, "Bee", stack);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, diff);
    }
}
