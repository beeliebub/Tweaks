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
    private WorldMock resourceNetherWorld;

    // YAML used by the two existing shear tests — deterministic single-entry overworld pool.
    private static final String OVERWORLD_ONLY_RED_SHEEP_YAML = """
            overworld:
              shear:
                red_sheep: "10:2.0"
            nether:
            """;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * Writes the given YAML body to resource_hunt.yml, creates both resource worlds (so that any
     * YAML combination resolves a registered world), constructs ResourceHunt, and registers it as
     * a listener. Does NOT add a player — tests that need player-join side-effects must call
     * server.addPlayer() themselves after bootstrap.
     */
    private void bootstrap(String yamlBody) throws IOException {
        Files.writeString(
                plugin.getDataFolder().toPath().resolve("resource_hunt.yml"),
                yamlBody
        );

        // Ensure the default "world" is present.
        server.addSimpleWorld("world");

        // Always create both resource worlds so neither overworld-only nor nether-only configs
        // reference a nonexistent world.
        NamespacedKey resourceKey = new NamespacedKey("jass", "resource");
        resourceWorld = (WorldMock) server.createWorld(WorldCreator.ofKey(resourceKey));

        NamespacedKey resourceNetherKey = new NamespacedKey("jass", "resource_nether");
        resourceNetherWorld = (WorldMock) server.createWorld(WorldCreator.ofKey(resourceNetherKey));

        resourceHunt = new ResourceHunt(plugin, new RewardManager(plugin));
        server.getPluginManager().registerEvents(resourceHunt, plugin);
    }

    // -------------------------------------------------------------------------
    // Existing shear tests — unchanged behaviour, now call bootstrap explicitly.
    // -------------------------------------------------------------------------

    @Test
    void shearingMatchingColoredSheepIncrementsProgress() throws IOException {
        bootstrap(OVERWORLD_ONLY_RED_SHEEP_YAML);
        // addPlayer fires PlayerJoinEvent → assignTargetIfAbsent → receives red_sheep target.
        player = server.addPlayer();

        Sheep sheep = buildSheep(DyeColor.RED, resourceWorld);
        PlayerShearEntityEvent event = new PlayerShearEntityEvent(
                player, sheep, new ItemStack(Material.SHEARS), EquipmentSlot.HAND, List.of());
        server.getPluginManager().callEvent(event);

        assertTrue(getProgress(player.getUniqueId()) > 0,
                "Shearing a RED sheep should increment progress when the target is red_sheep");
    }

    @Test
    void shearingMismatchedColorIgnoresProgress() throws IOException {
        bootstrap(OVERWORLD_ONLY_RED_SHEEP_YAML);
        player = server.addPlayer();

        Sheep sheep = buildSheep(DyeColor.BLUE, resourceWorld);
        PlayerShearEntityEvent event = new PlayerShearEntityEvent(
                player, sheep, new ItemStack(Material.SHEARS), EquipmentSlot.HAND, List.of());
        server.getPluginManager().callEvent(event);

        assertEquals(0, getProgress(player.getUniqueId()),
                "Shearing a BLUE sheep must not credit progress when the target is red_sheep");
    }

    // -------------------------------------------------------------------------
    // New world-selection tests.
    // -------------------------------------------------------------------------

    @Test
    void netherOnlyConfigSelectsNetherWorld() throws IOException {
        bootstrap("""
                overworld:
                nether:
                  barter:
                    gold_ingot: "5:2.0"
                """);

        assertTrue(resourceHunt.isActive(),
                "ResourceHunt should be active when nether has entries");
        assertEquals(ResourceHunt.TARGET_WORLD_NETHER_KEY, resourceHunt.getActiveWorldKey(),
                "A nether-only config must always select the nether world key");
    }

    @Test
    void overworldOnlyConfigSelectsOverworldWorld() throws IOException {
        bootstrap(OVERWORLD_ONLY_RED_SHEEP_YAML);

        assertTrue(resourceHunt.isActive(),
                "ResourceHunt should be active when overworld has entries");
        assertEquals(ResourceHunt.TARGET_WORLD_KEY, resourceHunt.getActiveWorldKey(),
                "An overworld-only config must always select the overworld world key");
    }

    @Test
    void emptyConfigDisablesMinigame() throws IOException {
        bootstrap("overworld:\nnether:\n");

        assertFalse(resourceHunt.isActive(),
                "ResourceHunt must be inactive when both sections are empty");
        // The constructor documents that it falls back to TARGET_WORLD_KEY when no entries exist.
        assertEquals(ResourceHunt.TARGET_WORLD_KEY, resourceHunt.getActiveWorldKey(),
                "getActiveWorldKey() must return the overworld fallback key when disabled");
    }

    @Test
    void bothWorldsPopulatedAreEachSelectedOverManyTrials() throws IOException {
        String bothWorldsYaml = """
                overworld:
                  shear:
                    red_sheep: "10:2.0"
                nether:
                  barter:
                    gold_ingot: "5:2.0"
                """;

        // Bootstrap once — creates worlds, writes YAML, constructs the first ResourceHunt and
        // registers it as a listener. The file is already on disk for subsequent constructions.
        bootstrap(bothWorldsYaml);

        // The RewardManager created during bootstrap has already created the "resource" reward
        // shell. Reuse it for the additional instances so "already exists" is handled gracefully.
        RewardManager sharedRewardManager = new RewardManager(plugin);

        int overworldCount = 0;
        int netherCount = 0;
        int trials = 200;

        // Count the initial instance from bootstrap.
        if (ResourceHunt.TARGET_WORLD_KEY.equals(resourceHunt.getActiveWorldKey())) {
            overworldCount++;
        } else {
            netherCount++;
        }

        // Construct the remaining instances directly — no need to register as listeners because
        // we only need getActiveWorldKey().
        for (int i = 1; i < trials; i++) {
            ResourceHunt rh = new ResourceHunt(plugin, sharedRewardManager);
            if (ResourceHunt.TARGET_WORLD_KEY.equals(rh.getActiveWorldKey())) {
                overworldCount++;
            } else {
                netherCount++;
            }
        }

        int lowerBound = 20; // well below the expected ~100 for each world out of 200 trials
        assertTrue(overworldCount >= lowerBound,
                "Overworld was selected only " + overworldCount + "/" + trials
                        + " times — expected at least " + lowerBound);
        assertTrue(netherCount >= lowerBound,
                "Nether was selected only " + netherCount + "/" + trials
                        + " times — expected at least " + lowerBound);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a Mockito-stubbed Sheep in the given world with the specified dye color.
     * getWorld() must return a world whose key passes ResourceHunt.isResourceWorld() and whose
     * key matches the activeWorldKey equality check inside the shear handler.
     */
    private Sheep buildSheep(DyeColor color, WorldMock world) {
        Sheep sheep = mock(Sheep.class);
        when(sheep.getType()).thenReturn(EntityType.SHEEP);
        when(sheep.getColor()).thenReturn(color);
        when(sheep.getWorld()).thenReturn(world);
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
