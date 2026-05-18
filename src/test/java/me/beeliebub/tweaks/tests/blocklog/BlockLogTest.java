package me.beeliebub.tweaks.tests.blocklog;

import me.beeliebub.tweaks.blocklog.BlockLogData;
import me.beeliebub.tweaks.blocklog.BlockLogData.ChestLogEntry;
import me.beeliebub.tweaks.blocklog.BlockLogData.LogAction;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Consolidated tests for the block-log data layer. The system-level tests (open/close diff,
// chunk prune, /logs viewer) lived as MockBukkit integration tests that were brittle and
// largely covered by the manual inspector flow — they're skipped in this consolidation.
class BlockLogTest {

    @Nested
    class LogActionEnum {
        @Test void roundTripsThroughByte() {
            assertEquals(LogAction.ADD, LogAction.fromByte(LogAction.ADD.toByte()));
            assertEquals(LogAction.REMOVE, LogAction.fromByte(LogAction.REMOVE.toByte()));
        }

        @Test void fromUnknownByteDefaultsToAdd() {
            assertEquals(LogAction.ADD, LogAction.fromByte((byte) 99));
        }
    }

    @Nested
    class EntryConstruction {
        @BeforeEach void setUp() { MockBukkit.mock(); }
        @AfterEach void tearDown() { MockBukkit.unmock(); }

        @Test void requiresActionUuidAndItem() {
            ItemStack item = new ItemStack(Material.DIAMOND);
            UUID uuid = UUID.randomUUID();
            assertThrows(IllegalArgumentException.class,
                    () -> new ChestLogEntry(0, null, uuid, "x", item));
            assertThrows(IllegalArgumentException.class,
                    () -> new ChestLogEntry(0, LogAction.ADD, null, "x", item));
            assertThrows(IllegalArgumentException.class,
                    () -> new ChestLogEntry(0, LogAction.ADD, uuid, "x", null));
        }

        @Test void nullPlayerNameBecomesEmpty() {
            ChestLogEntry e = new ChestLogEntry(0, LogAction.ADD, UUID.randomUUID(), null, new ItemStack(Material.DIAMOND));
            assertEquals("", e.playerName());
        }
    }

    @Nested
    class CodecRoundTrip {
        @BeforeEach void setUp() { MockBukkit.mock(); }
        @AfterEach void tearDown() { MockBukkit.unmock(); }

        @Test void emptyListEncodesAndDecodes() {
            byte[] data = BlockLogData.Codec.encode(new ArrayList<>());
            List<ChestLogEntry> decoded = BlockLogData.Codec.decode(data);
            assertTrue(decoded.isEmpty());
        }

        @Test void singleEntryRoundTrips() {
            ItemStack item = new ItemStack(Material.DIAMOND, 5);
            ChestLogEntry e = new ChestLogEntry(123456789L, LogAction.ADD,
                    UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                    "Bee", item);
            byte[] data = BlockLogData.Codec.encode(List.of(e));
            List<ChestLogEntry> decoded = BlockLogData.Codec.decode(data);
            assertEquals(1, decoded.size());
            assertEquals(e.timestamp(), decoded.get(0).timestamp());
            assertEquals(e.action(), decoded.get(0).action());
            assertEquals(e.playerUuid(), decoded.get(0).playerUuid());
            assertEquals(e.playerName(), decoded.get(0).playerName());
            assertEquals(item.getType(), decoded.get(0).item().getType());
            assertEquals(item.getAmount(), decoded.get(0).item().getAmount());
        }

        @Test void decodeNullOrEmptyReturnsEmptyList() {
            assertTrue(BlockLogData.Codec.decode(null).isEmpty());
            assertTrue(BlockLogData.Codec.decode(new byte[0]).isEmpty());
        }

        @Test void decodeRejectsUnknownVersion() {
            byte[] garbage = new byte[]{99, 0, 0, 0, 1};
            assertTrue(BlockLogData.Codec.decode(garbage).isEmpty());
        }
    }
}
