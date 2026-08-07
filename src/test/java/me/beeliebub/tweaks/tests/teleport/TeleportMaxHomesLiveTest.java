package me.beeliebub.tweaks.tests.teleport;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.profiles.StorageManager;
import me.beeliebub.tweaks.teleport.TeleportCommandManager;
import me.beeliebub.tweaks.utils.Point;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression for the max-homes live-read fix: before this fix, TeleportCommandManager cached
 * max-homes once at construction time, so a /tconfig edit at
 * runtime silently had no effect until the server restarted. This test edits config.yml between
 * two /sethome calls against the SAME manager instance - no reconstruction.
 */
class TeleportMaxHomesLiveTest {

    private Tweaks plugin;
    private StorageManager storage;
    private TeleportCommandManager mgr;
    private final Command bukkit = mock(Command.class);

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        storage = mock(StorageManager.class);
        mgr = new TeleportCommandManager(plugin, storage, mock(ResourceHuntItems.class));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Player playerInOverworld(UUID uuid) {
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        NamespacedKey key = mock(NamespacedKey.class);
        when(key.asString()).thenReturn("minecraft:overworld");
        when(world.getKey()).thenReturn(key);
        Location loc = new Location(world, 0, 64, 0);
        when(player.getLocation()).thenReturn(loc);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }

    @Test
    void editingMaxHomesAtRuntimeTakesEffectWithoutReconstruction() {
        UUID uuid = UUID.randomUUID();
        Player player = playerInOverworld(uuid);
        when(storage.getHomeCount(uuid)).thenReturn(2);
        when(storage.getHome(eq(uuid), anyString())).thenReturn(Optional.empty());

        plugin.getConfig().set("max-homes", 2);
        mgr.onCommand(player, bukkit, "sethome", new String[]{"another"});
        verify(storage, never()).setHome(any(), anyString(), any());

        plugin.getConfig().set("max-homes", 5);
        mgr.onCommand(player, bukkit, "sethome", new String[]{"another"});
        verify(storage).setHome(eq(uuid), eq("another"), any(Point.class));
    }
}
