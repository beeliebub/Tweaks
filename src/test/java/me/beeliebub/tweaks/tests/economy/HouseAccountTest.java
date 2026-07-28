package me.beeliebub.tweaks.tests.economy;

import me.beeliebub.tweaks.economy.HouseAccount;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.logging.Logger;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HouseAccountTest {

    @TempDir
    File dataFolder;

    private JavaPlugin plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("HouseAccountTest-" + System.nanoTime()));
        return plugin;
    }

    @AfterEach
    void drainAsyncWrites() {
        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);
    }

    @Test
    void creditRejectsNegativeAmountsAndPersistsWholeDollarBalance() throws Exception {
        HouseAccount house = new HouseAccount(plugin());
        house.flush().get();

        assertThrows(IllegalArgumentException.class, () -> house.credit(-1));
        assertTrue(house.credit(250));
        house.flush().get();

        File file = new File(dataFolder, "house/house.yml");
        assertEquals(250L, YamlConfiguration.loadConfiguration(file).getLong("balance"));

        HouseAccount reloaded = new HouseAccount(plugin());
        reloaded.flush().get();
        assertEquals(250L, reloaded.balance(), "a persisted house balance must round-trip exactly");
    }

    @Test
    void debitRefusesToOverdrawButExplicitSetMayMakeBalanceNegative() throws Exception {
        HouseAccount house = new HouseAccount(plugin());
        house.flush().get();
        house.credit(100);

        assertFalse(house.debit(101));
        assertEquals(100L, house.balance());
        assertTrue(house.debit(100));
        assertEquals(0L, house.balance());

        house.set(-20L);
        assertEquals(-20L, house.balance());
    }

    @Test
    void creditLogsAndRejectsOverflowWithoutWrapping() throws Exception {
        HouseAccount house = new HouseAccount(plugin());
        house.flush().get();
        house.set(Long.MAX_VALUE);

        assertFalse(house.credit(1));
        assertEquals(Long.MAX_VALUE, house.balance());
    }
}
