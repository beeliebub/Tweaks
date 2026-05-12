package me.beeliebub.tweaks.tests.minigames;

import me.beeliebub.tweaks.minigames.RewardManager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RewardManagerTest {

    @TempDir
    File dataFolder;

    private RewardManager manager;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        manager = new RewardManager(plugin);
    }

    @AfterEach
    void drainAsyncWrites() throws InterruptedException {
        // RewardManager fires off CompletableFuture.runAsync on every mutation; on Windows
        // those background writers can still hold the temp file open when JUnit tries to
        // clean up @TempDir, producing a misleading "Failed to delete temp directory" error.
        // Give the common-pool a brief moment to drain before the directory deletion runs.
        Thread.sleep(150);
    }

    @Test
    void newManagerHasNoRewards() {
        assertTrue(manager.getRewardNames().isEmpty());
    }

    @Test
    void createRewardLowercasesNameAndAddsEmptyItemArray() {
        manager.createReward("Gold");
        assertTrue(manager.rewardExists("gold"));
        assertTrue(manager.rewardExists("GOLD"));
        assertEquals(0, manager.getRewardItems("Gold").length);
    }

    @Test
    void rewardExistsReturnsFalseForUnknownName() {
        assertFalse(manager.rewardExists("nope"));
    }

    @Test
    void setRewardItemsFiltersOutNullSlots() {
        manager.createReward("loot");
        ItemStack a = mock(ItemStack.class);
        ItemStack b = mock(ItemStack.class);
        manager.setRewardItems("loot", new ItemStack[]{a, null, b, null});
        ItemStack[] items = manager.getRewardItems("loot");
        assertEquals(2, items.length);
        assertSame(a, items[0]);
        assertSame(b, items[1]);
    }

    @Test
    void getRewardItemsReturnsEmptyArrayForUnknownReward() {
        ItemStack[] items = manager.getRewardItems("none");
        assertNotNull(items);
        assertEquals(0, items.length);
    }

    @Test
    void getRewardNamesIsUnmodifiable() {
        manager.createReward("a");
        assertThrows(UnsupportedOperationException.class,
                () -> manager.getRewardNames().clear());
    }

    @Test
    void grantRewardLowercasesAndAppendsToPendingList() {
        UUID uuid = UUID.randomUUID();
        manager.grantReward(uuid, "Loot");
        manager.grantReward(uuid, "LOOT");
        manager.grantReward(uuid, "other");
        assertEquals(3, manager.getPendingRewards(uuid).size());
        assertTrue(manager.getPendingRewards(uuid).contains("loot"));
        assertTrue(manager.getPendingRewards(uuid).contains("other"));
    }

    @Test
    void getPendingRewardsReturnsEmptyListForUnknownUuid() {
        assertTrue(manager.getPendingRewards(UUID.randomUUID()).isEmpty());
    }

    @Test
    void clearPendingRewardsRemovesAllForUuid() {
        UUID uuid = UUID.randomUUID();
        manager.grantReward(uuid, "loot");
        manager.clearPendingRewards(uuid);
        assertTrue(manager.getPendingRewards(uuid).isEmpty());
    }

    @Test
    void clearPendingRewardsForUnknownUuidIsNoOp() {
        assertDoesNotThrow(() -> manager.clearPendingRewards(UUID.randomUUID()));
    }
}
