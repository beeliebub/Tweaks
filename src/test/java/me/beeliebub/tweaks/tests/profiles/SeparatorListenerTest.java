package me.beeliebub.tweaks.tests.profiles;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.profiles.SeparatorListener;
import me.beeliebub.tweaks.profiles.StorageManager;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises SeparatorListener's public event-handler API directly against a WorldProfileTable
 * mapping controlled world keys. MockBukkit's WorldMock.getKey() always returns
 * "minecraft:overworld" regardless of a created world's name (confirmed empirically - MockBukkit
 * has no per-world custom-key support), so the player's real (MockBukkit-managed) world is used for
 * the "to" side of a world change and a Mockito-mocked World with a controlled getKey() is used for
 * the "from" side - the only way to exercise a genuine cross-profile swap under this constraint.
 * Event handlers are invoked directly rather than through Bukkit's dispatcher, matching this
 * project's established pattern for listener unit tests.
 */
class SeparatorListenerTest {

    private static final String TO_KEY = "minecraft:overworld"; // MockBukkit's real default world key
    private static final String FROM_KEY = "jass:mocked_from";

    private ServerMock server;
    private Tweaks plugin;
    private StorageManager storage;
    private WorldProfileTable table;
    private SeparatorListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);

        storage = new StorageManager(plugin);
        table = new WorldProfileTable(plugin);
        table.putEntry(TO_KEY, "profileTo", "To", NamedTextColor.AQUA);
        table.putEntry(FROM_KEY, "profileFrom", "From", NamedTextColor.RED);

        listener = new SeparatorListener(plugin, storage, table);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static World mockWorld(String key) {
        World world = mock(World.class);
        when(world.getKey()).thenReturn(NamespacedKey.fromString(key));
        return world;
    }

    @Test
    void onWorldChangeSwapsProfilesBetweenDifferentProfileWorlds() {
        PlayerMock player = server.addPlayer(); // real world key resolves to "profileTo"
        UUID uuid = player.getUniqueId();

        listener.onWorldChange(new PlayerChangedWorldEvent(player, mockWorld(FROM_KEY)));

        // fromProfile (profileFrom) must now hold the player's live inventory snapshot.
        assertNotNull(storage.getCachedInventory(uuid, "profileFrom"));
    }

    @Test
    void onWorldChangeIgnoresSameProfileWorlds() {
        PlayerMock player = server.addPlayer(); // real world key resolves to "profileTo"
        UUID uuid = player.getUniqueId();

        // A "from" world mapped to the SAME profile as the real destination - must be a no-op,
        // since fromProfile.equals(toProfile) short-circuits before any cache write.
        table.putEntry("jass:also_profile_to", "profileTo", "AlsoTo", NamedTextColor.AQUA);

        listener.onWorldChange(new PlayerChangedWorldEvent(player, mockWorld("jass:also_profile_to")));

        assertNull(storage.getCachedInventory(uuid, "profileTo"));
    }

    @Test
    void onPlayerDeathIgnoresKeepInventory() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();

        PlayerDeathEvent event = new PlayerDeathEvent(player, mock(DamageSource.class),
                List.<ItemStack>of(), 0, Component.text(player.getName() + " died"), false);
        event.setKeepInventory(true);
        listener.onPlayerDeath(event);

        // keepInventory deaths must not pre-emptively overwrite the cache with an empty snapshot.
        assertNull(storage.getCachedInventory(uuid, "profileTo"));
    }

    @Test
    void onPlayerDeathSavesEmptyInventoryWhenNotKeepInventory() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();

        PlayerDeathEvent event = new PlayerDeathEvent(player, mock(DamageSource.class),
                List.<ItemStack>of(), 0, Component.text(player.getName() + " died"), false);
        event.setKeepInventory(false);
        listener.onPlayerDeath(event);

        assertNotNull(storage.getCachedInventory(uuid, "profileTo"));
    }
}
