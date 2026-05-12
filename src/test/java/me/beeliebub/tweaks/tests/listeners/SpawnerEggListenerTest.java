package me.beeliebub.tweaks.tests.listeners;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.listeners.SpawnerEggListener;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.mockito.Mockito.*;

class SpawnerEggListenerTest {

    private Tweaks plugin;
    private FileConfiguration config;
    private SpawnerEggListener listener;

    @BeforeEach
    void setUp() {
        // Material#getKey() touches the registry, so spin up a real MockBukkit ServerMock.
        MockBukkit.mock();
        plugin = mock(Tweaks.class);
        config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        listener = new SpawnerEggListener(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerInteractEvent rightClickEgg(Material spawnerType, Material eggMaterial) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(spawnerType);

        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(eggMaterial);

        Player player = mock(Player.class);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(block);
        when(event.getItem()).thenReturn(item);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    @Test
    void cancelsRightClickWhenEggIsOnDisabledList() {
        when(config.getStringList("spawner-egg-disabled-mobs"))
                .thenReturn(List.of("zombie", "wither_skeleton"));

        PlayerInteractEvent event = rightClickEgg(Material.SPAWNER, Material.ZOMBIE_SPAWN_EGG);
        listener.onPlayerInteract(event);
        verify(event).setCancelled(true);
    }

    @Test
    void allowsRightClickWhenEggIsNotOnDisabledList() {
        when(config.getStringList("spawner-egg-disabled-mobs"))
                .thenReturn(List.of("zombie"));

        PlayerInteractEvent event = rightClickEgg(Material.SPAWNER, Material.PIG_SPAWN_EGG);
        listener.onPlayerInteract(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void disabledListLookupIsCaseInsensitive() {
        when(config.getStringList("spawner-egg-disabled-mobs"))
                .thenReturn(List.of("ZOMBIE"));

        PlayerInteractEvent event = rightClickEgg(Material.SPAWNER, Material.ZOMBIE_SPAWN_EGG);
        listener.onPlayerInteract(event);
        verify(event).setCancelled(true);
    }

    @Test
    void ignoresLeftClicksAndPhysicalEvents() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.LEFT_CLICK_BLOCK);
        listener.onPlayerInteract(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void ignoresClicksOnNonSpawnerBlocks() {
        when(config.getStringList("spawner-egg-disabled-mobs"))
                .thenReturn(List.of("zombie"));

        PlayerInteractEvent event = rightClickEgg(Material.STONE, Material.ZOMBIE_SPAWN_EGG);
        listener.onPlayerInteract(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void ignoresClicksWithoutHeldItem() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.SPAWNER);

        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(block);
        when(event.getItem()).thenReturn(null);

        listener.onPlayerInteract(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void ignoresHeldItemsThatAreNotSpawnEggs() {
        when(config.getStringList("spawner-egg-disabled-mobs"))
                .thenReturn(List.of("zombie"));

        PlayerInteractEvent event = rightClickEgg(Material.SPAWNER, Material.STONE);
        listener.onPlayerInteract(event);
        verify(event, never()).setCancelled(true);
    }
}
