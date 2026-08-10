package me.beeliebub.tweaks.tests.skyblock.economy;

import me.beeliebub.tweaks.skyblock.economy.SkyblockEconomy;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkyblockEconomyTest {
    @Test
    void rejectsNonFiniteAndInexactMutations(@TempDir Path directory) {
        JavaPlugin plugin = plugin(directory);
        IslandManager islands = mock(IslandManager.class);
        SkyblockEconomy economy = new SkyblockEconomy(plugin, islands);
        Island island = Island.create(UUID.randomUUID(), 0, me.beeliebub.tweaks.skyblock.island.IslandSize.SMALL);

        assertEquals(SkyblockEconomy.Mutation.REJECTED, economy.credit(island, Double.NaN));
        assertEquals(SkyblockEconomy.Mutation.REJECTED, economy.credit(island, Double.POSITIVE_INFINITY));
        assertEquals(SkyblockEconomy.Mutation.APPLIED, economy.credit(island, 1.25d));
        assertEquals(1.25d, economy.balance(island));
        economy.flush().join();
    }

    @Test
    void walletsAreKeyedByIslandAndAnyMemberCanUseThem(@TempDir Path directory) {
        JavaPlugin plugin = plugin(directory);
        IslandManager islands = mock(IslandManager.class);
        SkyblockEconomy economy = new SkyblockEconomy(plugin, islands);
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Island island = Island.create(owner, 0, me.beeliebub.tweaks.skyblock.island.IslandSize.SMALL)
                .withMembers(java.util.List.of(member));
        when(islands.forPlayer(member)).thenReturn(Optional.of(island));

        assertEquals(SkyblockEconomy.Mutation.APPLIED, economy.credit(island, 10));
        assertEquals(SkyblockEconomy.Mutation.APPLIED, economy.debit(member, 4));
        assertEquals(6d, economy.balance(island));
        assertEquals(0d, economy.balance("00000000000000000000000000000000"));
        economy.flush().join();
    }

    @Test
    void transferRefusesSameIslandAndMissingFunds(@TempDir Path directory) {
        JavaPlugin plugin = plugin(directory);
        SkyblockEconomy economy = new SkyblockEconomy(plugin, mock(IslandManager.class));
        Island first = Island.create(UUID.randomUUID(), 0, me.beeliebub.tweaks.skyblock.island.IslandSize.SMALL);
        Island second = Island.create(UUID.randomUUID(), 1, me.beeliebub.tweaks.skyblock.island.IslandSize.SMALL);

        assertEquals(SkyblockEconomy.Transfer.SAME_ISLAND, economy.transfer(first, first, 1));
        assertEquals(SkyblockEconomy.Transfer.INSUFFICIENT, economy.transfer(first, second, 1));
        assertEquals(SkyblockEconomy.Transfer.NO_ISLAND, economy.transfer(null, second, 1));
        assertTrue(economy.flush().isDone());
    }

    private static JavaPlugin plugin(Path directory) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SkyblockEconomyTest"));
        return plugin;
    }
}
