package me.beeliebub.tweaks.tests.skyblock.island;

import me.beeliebub.tweaks.skyblock.SkyblockConfig;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandGrid;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import me.beeliebub.tweaks.skyblock.island.IslandStore;
import org.bukkit.World;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IslandAllocationTest {
    @Test
    void allocationStartsOutsideTheSpawnExclusion() {
        SkyblockConfig config = mock(SkyblockConfig.class);
        when(config.worldKey()).thenReturn("minecraft:skyblock");
        IslandStore store = mock(IslandStore.class);
        when(store.loadAll()).thenReturn(List.of());

        IslandManager manager = new IslandManager(config, new IslandGrid(), store);

        assertEquals(9, manager.nextFreeSlot());
    }

    @Test
    void handEditedOriginIslandRemainsVisibleWhileAllocationSkipsIt() {
        ServerMock server = MockBukkit.mock();
        try {
            UUID owner = UUID.randomUUID();
            Island origin = Island.create(owner, 0, IslandSize.SMALL);
            World world = server.addSimpleWorld("skyblock");
            SkyblockConfig config = mock(SkyblockConfig.class);
            when(config.worldKey()).thenReturn(world.getKey().asString());
            IslandStore store = mock(IslandStore.class);
            when(store.loadAll()).thenReturn(List.of(origin));
            IslandManager manager = new IslandManager(config, new IslandGrid(), store);

            assertTrue(manager.byId(origin.id()).isPresent());
            assertEquals(0, manager.slotFor(new Location(world, 0, 64, 0)));
            assertTrue(manager.islandAt(world, 0, 0).isPresent());
            assertEquals(9, manager.nextFreeSlot());
        } finally {
            MockBukkit.unmock();
        }
    }
}
