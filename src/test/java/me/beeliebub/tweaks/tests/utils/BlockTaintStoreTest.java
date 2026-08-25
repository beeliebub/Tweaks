package me.beeliebub.tweaks.tests.utils;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.utils.BlockTaintStore;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockTaintStoreTest {

    private ServerMock server;
    private Tweaks plugin;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        world = server.addSimpleWorld("taint-test");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void markAndConsumeAreScopedToTheExactBlock() {
        BlockTaintStore store = new BlockTaintStore(plugin, "tools_taint_");
        Block block = world.getBlockAt(1, 64, 2);
        Block nearby = world.getBlockAt(2, 64, 2);

        store.mark(block);

        assertTrue(store.isTainted(block));
        assertFalse(store.isTainted(nearby));
        assertTrue(store.consume(block));
        assertFalse(store.isTainted(block));
    }

    @Test
    void pruneRemovesExpiredMarkersAndKeepsTheEmptyFastPathCheap() {
        BlockTaintStore store = new BlockTaintStore(plugin, "tools_taint_");
        assertEquals(0, store.pruneExpired(world.getChunkAt(0, 0), 1));
        world.getChunkAt(0, 0).getPersistentDataContainer().set(
                new NamespacedKey("tweaks", "tools_taint_1_p64_2"), PersistentDataType.LONG,
                System.currentTimeMillis() - 10_000);

        assertEquals(1, store.pruneExpired(world.getChunkAt(0, 0), 1_000));
    }
}
