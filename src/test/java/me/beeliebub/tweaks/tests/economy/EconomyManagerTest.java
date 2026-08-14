package me.beeliebub.tweaks.tests.economy;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class EconomyManagerTest {

    private ServerMock server;
    private Tweaks plugin;
    private EconomyManager economy;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        economy = plugin.getEconomyManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void managerIsExposed() {
        assertNotNull(economy);
    }

    @Test
    void defaultBalanceIsZero() {
        UUID id = UUID.randomUUID();
        assertEquals(0L, economy.getBalance(id));
    }

    @Test
    void setBalanceRoundtrips() {
        UUID id = UUID.randomUUID();
        economy.setBalance(id, 123L);
        assertEquals(123L, economy.getBalance(id));
    }

    @Test
    void addBalanceAccumulates() {
        UUID id = UUID.randomUUID();
        economy.setBalance(id, 100L);
        economy.addBalance(id, 50L);
        assertEquals(150L, economy.getBalance(id));
    }

    @Test
    void removeBalanceSubtracts() {
        UUID id = UUID.randomUUID();
        economy.setBalance(id, 100L);
        economy.removeBalance(id, 30L);
        assertEquals(70L, economy.getBalance(id));
    }

    @Test
    void addBalanceFromDefaultStartsAtZero() {
        UUID id = UUID.randomUUID();
        economy.addBalance(id, 25L);
        assertEquals(25L, economy.getBalance(id));
    }

    @Test
    void addAtTheBalanceCeilingLeavesMemoryAndDiskUnchanged() throws Exception {
        UUID id = UUID.randomUUID();
        assertEquals(me.beeliebub.tweaks.economy.BalanceMutationResult.APPLIED,
                economy.setBalance(id, EconomyManager.MAX_BALANCE));
        economy.saveAll().get(5, TimeUnit.SECONDS);

        assertEquals(me.beeliebub.tweaks.economy.BalanceMutationResult.REJECTED_UNREPRESENTABLE,
                economy.addBalance(id, 1L));
        assertEquals(EconomyManager.MAX_BALANCE, economy.getBalance(id));

        economy.saveAll().get(5, TimeUnit.SECONDS);
        File file = new File(plugin.getDataFolder(), "players/" + id + ".yml");
        assertEquals(EconomyManager.MAX_BALANCE, YamlConfiguration.loadConfiguration(file).getLong("balance"));
    }

    @Test
    void rankRoundtripsWithZeroDefault() {
        UUID id = UUID.randomUUID();
        assertEquals(0, economy.getRank(id));
        economy.setRank(id, 4);
        assertEquals(4, economy.getRank(id));
    }

    @Test
    void balanceHiddenRoundtripsWithFalseDefault() {
        UUID id = UUID.randomUUID();
        assertFalse(economy.isBalanceHidden(id));
        economy.setBalanceHidden(id, true);
        assertTrue(economy.isBalanceHidden(id));
    }

    @Test
    void lastLoginRoundtripsWithZeroDefault() {
        UUID id = UUID.randomUUID();
        assertEquals(0L, economy.getLastLogin(id));
        long now = System.currentTimeMillis();
        economy.setLastLogin(id, now);
        assertEquals(now, economy.getLastLogin(id));
    }

    @Test
    void loginStreakRoundtripsWithZeroDefault() {
        UUID id = UUID.randomUUID();
        assertEquals(0, economy.getLoginStreak(id));
        economy.setLoginStreak(id, 7);
        assertEquals(7, economy.getLoginStreak(id));
    }

    @Test
    void worksWithRealPlayerUuid() {
        PlayerMock player = server.addPlayer();
        UUID id = player.getUniqueId();
        economy.setBalance(id, 500L);
        economy.setRank(id, 2);
        assertEquals(500L, economy.getBalance(id));
        assertEquals(2, economy.getRank(id));
    }

    @Test
    void saveAllFlushesBalanceMutatedImmediatelyBeforeDisableToDisk() throws Exception {
        UUID id = UUID.randomUUID();
        economy.setBalance(id, 777L);

        economy.saveAll().get(5, TimeUnit.SECONDS);

        File file = new File(plugin.getDataFolder(), "players/" + id + ".yml");
        assertTrue(file.exists());
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(file);
        assertEquals(777L, onDisk.getLong("balance"));
    }

    @Test
    void saveAllWaitsForEveryCachedPlayerNotJustTheFirst() throws Exception {
        // Each setBalance() call already fires its own independent async write, so a single-player
        // version of this test can't distinguish "saveAll() did the work" from "the mutator's own
        // write happened to land first." Exercising several players proves the returned future is
        // a real CompletableFuture.allOf(...) aggregate that only completes once every cached
        // player's write has finished, not just the first one in iteration order.
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        economy.setBalance(first, 10L);
        economy.setBalance(second, 20L);
        economy.setBalance(third, 30L);

        economy.saveAll().get(5, TimeUnit.SECONDS);

        for (var entry : java.util.Map.of(first, 10L, second, 20L, third, 30L).entrySet()) {
            File file = new File(plugin.getDataFolder(), "players/" + entry.getKey() + ".yml");
            assertTrue(file.exists(), "expected a saved file for " + entry.getKey());
            YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(file);
            assertEquals(entry.getValue(), onDisk.getLong("balance"));
        }
    }
}
