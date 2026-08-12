package me.beeliebub.tweaks.tests.skyblock.island;

import me.beeliebub.tweaks.skyblock.island.IslandGrid;
import me.beeliebub.tweaks.skyblock.island.SkyblockSpawn;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkyblockSpawnTest {
    @Test
    void enforcementUsesLiveRegionBoundsWhileKeepingRecoveryRecord() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("skyblock");
        when(world.getKey()).thenReturn(org.bukkit.NamespacedKey.fromString("minecraft:skyblock"));
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0.5, 64, 0.5));
        IslandGrid.ChunkBounds recovery = new IslandGrid.ChunkBounds(-1, -1, 1, 1);
        IslandGrid.ChunkBounds live = new IslandGrid.ChunkBounds(4, 5, 6, 7);
        UUID owner = UUID.randomUUID();
        SkyblockSpawn.Store store = new MemoryStore();
        SkyblockSpawn spawn = new SkyblockSpawn(store, ignored -> live);
        SkyblockSpawn.SpawnData data = new SkyblockSpawn.SpawnData("minecraft:skyblock", recovery, owner,
                72.0, 80.0, 88.0, 12.0f, 3.0f);

        spawn.record(data).join();

        assertEquals(recovery, spawn.data().orElseThrow().bounds());
        assertEquals(live, spawn.permittedBounds(world));
        assertTrue(spawn.permits(new Location(world, 4 * 16 + 1.0, 70, 5 * 16 + 1.0)));
        assertFalse(spawn.permits(new Location(world, 0.5, 64, 0.5)));
        assertEquals(72.0, spawn.destination(world).getX());
    }

    @Test
    void legacyOwnerlessDataLoadsButCannotSupplyARecordedDestination() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("skyblock");
        when(world.getKey()).thenReturn(org.bukkit.NamespacedKey.fromString("minecraft:skyblock"));
        Location fallback = new Location(world, 0.5, 64, 0.5);
        when(world.getSpawnLocation()).thenReturn(fallback);
        IslandGrid.ChunkBounds bounds = new IslandGrid.ChunkBounds(-1, -1, 1, 1);
        SkyblockSpawn spawn = new SkyblockSpawn(new MemoryStore(), ignored -> bounds);
        spawn.record(new SkyblockSpawn.SpawnData("minecraft:skyblock", bounds,
                8.0, 70.0, 8.0, 0.0f, 0.0f));

        assertTrue(spawn.data().orElseThrow().owner() == null);
        assertEquals(fallback, spawn.destination(world));
    }

    @Test
    void yamlRoundTripPersistsOwnerAndKeepsLegacyOwnerOptional(@TempDir Path directory) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        IslandGrid.ChunkBounds bounds = new IslandGrid.ChunkBounds(-1, -1, 1, 1);
        UUID owner = UUID.randomUUID();
        SkyblockSpawn.SpawnData data = new SkyblockSpawn.SpawnData(
                "minecraft:skyblock", bounds, owner, 8.0, 70.0, 8.0, 0.0f, 0.0f);

        new SkyblockSpawn(plugin, ignored -> bounds).record(data).join();
        SkyblockSpawn loaded = new SkyblockSpawn(plugin, ignored -> bounds);

        assertEquals(owner, loaded.data().orElseThrow().owner());
        new SkyblockSpawn(plugin, ignored -> bounds).record(new SkyblockSpawn.SpawnData(
                "minecraft:skyblock", bounds, 8.0, 70.0, 8.0, 0.0f, 0.0f)).join();
        assertTrue(new SkyblockSpawn(plugin, ignored -> bounds).data().orElseThrow().owner() == null);
    }

    private static final class MemoryStore implements SkyblockSpawn.Store {
        private final AtomicReference<SkyblockSpawn.SpawnData> value = new AtomicReference<>();

        @Override
        public Optional<SkyblockSpawn.SpawnData> load() {
            return Optional.ofNullable(value.get());
        }

        @Override
        public CompletableFuture<Void> save(SkyblockSpawn.SpawnData data) {
            value.set(data);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> clear() {
            value.set(null);
            return CompletableFuture.completedFuture(null);
        }
    }
}
