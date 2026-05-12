package me.beeliebub.tweaks.tests.blocklog;

import me.beeliebub.tweaks.blocklog.ChestLogEntry;
import me.beeliebub.tweaks.blocklog.LogAction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the package-private ChunkLogStore. We reach into the class via reflection because
 * it is package-private, but everything else (chunks, PDC, NamespacedKey) comes from a real
 * MockBukkit ServerMock so the production code paths are exercised end-to-end.
 */
class ChunkLogStoreTest {

    private static final Class<?> CLASS;
    private static final Constructor<?> CTOR;
    private static final Method KEY_FOR;
    private static final Method READ;
    private static final Method APPEND;
    private static final Method APPEND_ALL;
    private static final Method PRUNE_CHUNK;
    private static final int MAX_ENTRIES;

    static {
        try {
            CLASS = Class.forName("me.beeliebub.tweaks.blocklog.ChunkLogStore");
            CTOR = CLASS.getDeclaredConstructor(Plugin.class);
            CTOR.setAccessible(true);
            KEY_FOR = CLASS.getDeclaredMethod("keyFor", Block.class);
            KEY_FOR.setAccessible(true);
            READ = CLASS.getDeclaredMethod("read", Block.class);
            READ.setAccessible(true);
            APPEND = CLASS.getDeclaredMethod("append", Block.class, ChestLogEntry.class);
            APPEND.setAccessible(true);
            APPEND_ALL = CLASS.getDeclaredMethod("appendAll", Block.class, List.class);
            APPEND_ALL.setAccessible(true);
            PRUNE_CHUNK = CLASS.getDeclaredMethod("pruneChunk", org.bukkit.Chunk.class, long.class);
            PRUNE_CHUNK.setAccessible(true);
            Field maxField = CLASS.getDeclaredField("MAX_ENTRIES_PER_CHEST");
            maxField.setAccessible(true);
            MAX_ENTRIES = (int) maxField.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ServerMock server;
    private Plugin plugin;
    private World world;
    private Object store;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("tweaks");
        world = new WorldMock(Material.STONE, 0);
        server.addWorld((WorldMock) world);
        store = CTOR.newInstance(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Block blockAt(int x, int y, int z) {
        return world.getBlockAt(x, y, z);
    }

    private ChestLogEntry entry(long timestamp) {
        return new ChestLogEntry(timestamp, LogAction.ADD,
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "Bee",
                new ItemStack(Material.STONE));
    }

    @SuppressWarnings("unchecked")
    private List<ChestLogEntry> read(Block b) throws Exception {
        return (List<ChestLogEntry>) READ.invoke(store, b);
    }

    @Test
    void keyForEncodesLocalCoordsAndAbsoluteY() throws Exception {
        Block b = blockAt(35, 64, -7); // 35 & 15 = 3, -7 & 15 = 9
        NamespacedKey key = (NamespacedKey) KEY_FOR.invoke(store, b);
        assertEquals("tweaks", key.getNamespace());
        assertEquals("blocklog_3_64_9", key.getKey());
    }

    @Test
    void keyForUsesUnsignedLocalCoordsForNegativeBlockPositions() throws Exception {
        // Verifies the bitwise & 15 behaviour normalises into [0,15] for any signed input.
        Block b = blockAt(-1, 0, -16);
        NamespacedKey key = (NamespacedKey) KEY_FOR.invoke(store, b);
        assertEquals("blocklog_15_0_0", key.getKey());
    }

    @Test
    void readReturnsEmptyListWhenNothingStored() throws Exception {
        assertTrue(read(blockAt(0, 0, 0)).isEmpty());
    }

    @Test
    void appendStoresASingleEntryUnderTheChestKey() throws Exception {
        Block b = blockAt(0, 0, 0);
        APPEND.invoke(store, b, entry(1L));
        List<ChestLogEntry> result = read(b);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).timestamp());
        assertEquals(LogAction.ADD, result.get(0).action());
    }

    @Test
    void appendAllOnEmptyListIsNoOpAndDoesNotCreateAKey() throws Exception {
        Block b = blockAt(0, 0, 0);
        APPEND_ALL.invoke(store, b, List.of());
        // No key was written — read should still return empty
        assertTrue(read(b).isEmpty());
    }

    @Test
    void appendAllAppendsMultipleEntriesPreservingOrder() throws Exception {
        Block b = blockAt(0, 0, 0);
        APPEND_ALL.invoke(store, b, List.of(entry(1L), entry(2L), entry(3L)));
        List<ChestLogEntry> result = read(b);
        assertEquals(3, result.size());
        assertEquals(1L, result.get(0).timestamp());
        assertEquals(2L, result.get(1).timestamp());
        assertEquals(3L, result.get(2).timestamp());
    }

    @Test
    void singleAppendCapsListAtMaxEntriesAndKeepsTheNewest() throws Exception {
        Block b = blockAt(0, 0, 0);
        for (int i = 0; i < MAX_ENTRIES + 5; i++) {
            APPEND.invoke(store, b, entry(i));
        }
        List<ChestLogEntry> result = read(b);
        assertEquals(MAX_ENTRIES, result.size());
        // Newest 500 are kept; lowest timestamp must be 5 (we dropped 0..4).
        assertEquals(5L, result.get(0).timestamp());
        assertEquals(MAX_ENTRIES + 4L, result.get(result.size() - 1).timestamp());
    }

    @Test
    void pruneChunkRemovesExpiredEntriesAndCountsThem() throws Exception {
        Block b = blockAt(0, 0, 0);
        APPEND_ALL.invoke(store, b, List.of(entry(100L), entry(200L), entry(300L)));

        int pruned = (int) PRUNE_CHUNK.invoke(store, b.getChunk(), 250L);
        assertEquals(2, pruned, "two entries below cutoff should be pruned");

        List<ChestLogEntry> remaining = read(b);
        assertEquals(1, remaining.size());
        assertEquals(300L, remaining.get(0).timestamp());
    }

    @Test
    void pruneChunkRemovesEntirelyEmptyKeys() throws Exception {
        Block b = blockAt(0, 0, 0);
        APPEND_ALL.invoke(store, b, List.of(entry(100L), entry(200L)));

        PRUNE_CHUNK.invoke(store, b.getChunk(), 1_000_000L);
        // After pruning everything, the key should be gone — read() returns empty.
        assertTrue(read(b).isEmpty());
        // And the chunk PDC must no longer hold our key.
        NamespacedKey key = (NamespacedKey) KEY_FOR.invoke(store, b);
        assertFalse(b.getChunk().getPersistentDataContainer().getKeys().contains(key));
    }

    @Test
    void pruneChunkSkipsKeysFromOtherNamespaces() throws Exception {
        Block b = blockAt(0, 0, 0);
        APPEND_ALL.invoke(store, b, List.of(entry(100L)));

        // Inject an unrelated key under a different plugin's namespace.
        Plugin foreign = MockBukkit.createMockPlugin("other");
        NamespacedKey foreignKey = new NamespacedKey(foreign, "blocklog_irrelevant");
        b.getChunk().getPersistentDataContainer()
                .set(foreignKey, org.bukkit.persistence.PersistentDataType.BYTE_ARRAY, new byte[]{0xA, 0xB});

        PRUNE_CHUNK.invoke(store, b.getChunk(), 1_000_000L);
        assertTrue(b.getChunk().getPersistentDataContainer().getKeys().contains(foreignKey),
                "foreign-namespace keys must never be touched by pruneChunk");
    }

    @Test
    void appendAndReadRoundTripsForMultipleDistinctChests() throws Exception {
        // Two chests in the same chunk at different y values must store independently.
        Block a = blockAt(0, 60, 0);
        Block c = blockAt(0, 70, 0);
        APPEND.invoke(store, a, entry(1L));
        APPEND.invoke(store, c, entry(2L));

        assertEquals(1, read(a).size());
        assertEquals(1L, read(a).get(0).timestamp());
        assertEquals(1, read(c).size());
        assertEquals(2L, read(c).get(0).timestamp());
    }
}
