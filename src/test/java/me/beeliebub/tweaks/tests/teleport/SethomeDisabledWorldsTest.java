package me.beeliebub.tweaks.tests.teleport;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.profiles.StorageManager;
import me.beeliebub.tweaks.teleport.TeleportCommandManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
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
 * Regression pinning the sethome-disabled-worlds config migration and its fix: before this change,
 * {@code TeleportCommandManager} hardcoded {@code DISABLED_HOME_WORLD_KEY = "jass:resource"} and
 * never checked {@code jass:resource_nether}, despite {@code teleport/CLAUDE.md} claiming both
 * resource world keys were blocked. {@code teleport.sethome-disabled-worlds} is now the live-read
 * source of truth for both.
 */
class SethomeDisabledWorldsTest {

    private Tweaks plugin;
    private StorageManager storage;
    private TeleportCommandManager mgr;
    private final Command bukkit = mock(Command.class);

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        storage = mock(StorageManager.class);
        when(storage.getHome(any(), anyString())).thenReturn(Optional.empty());
        when(storage.getHomeCount(any())).thenReturn(0);
        mgr = new TeleportCommandManager(plugin, storage, mock(ResourceHuntItems.class));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Player playerInWorld(UUID uuid, String worldKey) {
        Player player = mock(Player.class);
        World world = mock(World.class);
        NamespacedKey key = mock(NamespacedKey.class);
        when(key.asString()).thenReturn(worldKey);
        when(world.getKey()).thenReturn(key);
        Location loc = new Location(world, 0, 64, 0);
        when(player.getLocation()).thenReturn(loc);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }

    @Test
    void sethomeIsRefusedInResourceNetherRegressionForThePreviouslyMissedKey() {
        plugin.getConfig().set("teleport.sethome-disabled-worlds",
                List.of("jass:resource", "jass:resource_nether"));
        Player player = playerInWorld(UUID.randomUUID(), "jass:resource_nether");

        mgr.onCommand(player, bukkit, "sethome", new String[]{});

        verify(storage, never()).setHome(any(), anyString(), any());
    }

    @Test
    void sethomeIsRefusedInResourceWorld() {
        plugin.getConfig().set("teleport.sethome-disabled-worlds",
                List.of("jass:resource", "jass:resource_nether"));
        Player player = playerInWorld(UUID.randomUUID(), "jass:resource");

        mgr.onCommand(player, bukkit, "sethome", new String[]{});

        verify(storage, never()).setHome(any(), anyString(), any());
    }

    @Test
    void sethomeIsAllowedInAnUnlistedWorld() {
        plugin.getConfig().set("teleport.sethome-disabled-worlds",
                List.of("jass:resource", "jass:resource_nether"));
        Player player = playerInWorld(UUID.randomUUID(), "minecraft:overworld");

        mgr.onCommand(player, bukkit, "sethome", new String[]{});

        verify(storage).setHome(any(), anyString(), any());
    }

    @Test
    void removingEveryEntryReEnablesSethomeEverywhere() {
        plugin.getConfig().set("teleport.sethome-disabled-worlds", List.of());
        Player player = playerInWorld(UUID.randomUUID(), "jass:resource_nether");

        mgr.onCommand(player, bukkit, "sethome", new String[]{});

        verify(storage).setHome(any(), anyString(), any());
    }

    @Test
    void editingTheListAtRuntimeTakesEffectWithoutReconstruction() {
        plugin.getConfig().set("teleport.sethome-disabled-worlds", List.of());
        Player player = playerInWorld(UUID.randomUUID(), "jass:resource_nether");
        mgr.onCommand(player, bukkit, "sethome", new String[]{});
        verify(storage).setHome(any(), anyString(), any());

        plugin.getConfig().set("teleport.sethome-disabled-worlds", List.of("jass:resource_nether"));
        mgr.onCommand(player, bukkit, "sethome", new String[]{"second"});
        verify(storage, never()).setHome(any(), eq("second"), any());
    }
}
