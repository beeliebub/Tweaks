package me.beeliebub.tweaks.tests.minigames.resource;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.RewardManager;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.WorldCreator;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Sheep;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceHuntTest {

    private ServerMock server;
    private Tweaks plugin;
    private ResourceHunt resourceHunt;
    private PlayerMock player;
    private WorldMock resourceWorld;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);

        // Write a minimal resource_hunt.yml containing only the red_sheep entry so the
        // target pool is deterministic — ResourceHunt always picks the sole entry.
        // The file must exist before ResourceHunt is constructed so saveResource() skips it.
        Files.writeString(
                plugin.getDataFolder().toPath().resolve("resource_hunt.yml"),
                """
                overworld:
                  shear:
                    red_sheep: "10:2.0"
                nether:
                """
        );

        // Create a world whose NamespacedKey is "jass:resource". ResourceHunt.isResourceWorld()
        // checks worldKey.contains("jass:resource"), so this world passes the guard.
        NamespacedKey resourceKey = new NamespacedKey("jass", "resource");
        resourceWorld = (WorldMock) server.createWorld(WorldCreator.ofKey(resourceKey));

        server.addSimpleWorld("world");

        resourceHunt = new ResourceHunt(plugin, new RewardManager(plugin));
        server.getPluginManager().registerEvents(resourceHunt, plugin);

        // addPlayer fires a PlayerJoinEvent, which triggers assignTargetIfAbsent — the player
        // receives the red_sheep target from the single-entry pool.
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shearingMatchingColoredSheepIncrementsProgress() {
        Sheep sheep = buildSheep(DyeColor.RED);
        PlayerShearEntityEvent event = new PlayerShearEntityEvent(
                player, sheep, new ItemStack(Material.SHEARS), EquipmentSlot.HAND, List.of());
        server.getPluginManager().callEvent(event);

        assertTrue(getProgress(player.getUniqueId()) > 0,
                "Shearing a RED sheep should increment progress when the target is red_sheep");
    }

    @Test
    void shearingMismatchedColorIgnoresProgress() {
        Sheep sheep = buildSheep(DyeColor.BLUE);
        PlayerShearEntityEvent event = new PlayerShearEntityEvent(
                player, sheep, new ItemStack(Material.SHEARS), EquipmentSlot.HAND, List.of());
        server.getPluginManager().callEvent(event);

        assertEquals(0, getProgress(player.getUniqueId()),
                "Shearing a BLUE sheep must not credit progress when the target is red_sheep");
    }

    /**
     * Creates a Mockito-stubbed Sheep in the jass:resource world with the given dye color.
     * getWorld() must return resourceWorld so ResourceHunt's world-key guard passes.
     */
    private Sheep buildSheep(DyeColor color) {
        Sheep sheep = mock(Sheep.class);
        when(sheep.getType()).thenReturn(EntityType.SHEEP);
        when(sheep.getColor()).thenReturn(color);
        // getWorld() must return the jass:resource-keyed world so the handler's isResourceWorld
        // check passes and the activeWorldKey equality check passes.
        when(sheep.getWorld()).thenReturn(resourceWorld);
        return sheep;
    }

    // getProgress is package-private in ResourceHunt; reflection is the least-invasive way to
    // reach it from a different test package without modifying production code.
    private int getProgress(UUID uuid) {
        try {
            Method m = ResourceHunt.class.getDeclaredMethod("getProgress", UUID.class);
            m.setAccessible(true);
            return (int) m.invoke(resourceHunt, uuid);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke ResourceHunt.getProgress via reflection", e);
        }
    }
}
