package me.beeliebub.tweaks.tests.minigames.resource;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.FortuneQualityListener;
import me.beeliebub.tweaks.minigames.RewardManager;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntBreedListener;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.WorldCreator;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Animals;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    @Test
    void blockDropCountingRunsAfterQualityFortuneRerolls() throws NoSuchMethodException {
        EventHandler resourceHuntHandler = ResourceHunt.class
                .getDeclaredMethod("onBlockDropItem", BlockDropItemEvent.class)
                .getAnnotation(EventHandler.class);
        EventHandler fortuneHandler = FortuneQualityListener.class
                .getDeclaredMethod("onBlockDropItem", BlockDropItemEvent.class)
                .getAnnotation(EventHandler.class);

        assertEquals(EventPriority.NORMAL, resourceHuntHandler.priority());
        assertEquals(EventPriority.LOW, fortuneHandler.priority());
        assertTrue(resourceHuntHandler.priority().getSlot() > fortuneHandler.priority().getSlot(),
                "Resource Hunt must observe drops after quality Fortune has re-rolled them");
    }

    @Test
    void recordBreedProgressCreditsMatchingBreedTarget() throws IOException {
        bootstrap("""
                overworld:
                  breed:
                    turtle: "5:2.0"
                nether:
                """);
        player = server.addPlayer();
        player.teleport(resourceWorld.getSpawnLocation());

        resourceHunt.recordBreedProgress(player, EntityType.TURTLE, 1);

        assertEquals(1, getProgress(player.getUniqueId()));
    }

    @Test
    void recordBreedProgressIgnoresCollectTarget() throws IOException {
        bootstrap("""
                overworld:
                  collect:
                    kelp: "5:2.0"
                nether:
                """);
        player = server.addPlayer();
        player.teleport(resourceWorld.getSpawnLocation());

        resourceHunt.recordBreedProgress(player, EntityType.TURTLE, 1);

        assertEquals(0, getProgress(player.getUniqueId()));
    }

    @Test
    void recordBreedProgressIgnoresInactiveHunt() throws IOException {
        bootstrap("overworld:\nnether:\n");
        player = server.addPlayer();

        resourceHunt.recordBreedProgress(player, EntityType.TURTLE, 1);

        assertEquals(0, getProgress(player.getUniqueId()));
    }

    @Test
    void recordBreedProgressIgnoresPlayerOutsideActiveWorld() throws IOException {
        bootstrap("""
                overworld:
                  breed:
                    turtle: "5:2.0"
                nether:
                """);
        player = server.addPlayer();

        resourceHunt.recordBreedProgress(player, EntityType.TURTLE, 1);

        assertEquals(0, getProgress(player.getUniqueId()));
    }

    @Test
    void recordBreedProgressIgnoresFullyCompletePlayer() throws IOException {
        bootstrap("""
                overworld:
                  breed:
                    turtle: "1:1.0"
                nether:
                """);
        player = server.addPlayer();
        player.teleport(resourceWorld.getSpawnLocation());
        resourceHunt.recordBreedProgress(player, EntityType.TURTLE, 3);

        resourceHunt.recordBreedProgress(player, EntityType.TURTLE, 1);

        assertEquals(3, getProgress(player.getUniqueId()));
    }

    @Test
    void breedListenerCreditsOneNearbyPairing() throws IOException {
        bootstrap("""
                overworld:
                  breed:
                    turtle: "5:2.0"
                nether:
                """);
        player = server.addPlayer();
        player.teleport(resourceWorld.getSpawnLocation());

        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000071");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000072");
        Animals first = buildBredAnimal(firstId, resourceWorld, 0);
        Animals second = buildBredAnimal(secondId, resourceWorld, 2);
        EntityEnterLoveModeEvent firstEvent = loveModeEvent(first, player);
        EntityEnterLoveModeEvent secondEvent = loveModeEvent(second, player);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        AtomicReference<Runnable> sweep = new AtomicReference<>();
        doAnswer(invocation -> {
            sweep.set(invocation.getArgument(1));
            return task;
        }).when(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(10L), eq(10L));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getCurrentTick).thenReturn(0);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getEntity(firstId)).thenReturn(first);
            bukkit.when(() -> Bukkit.getEntity(secondId)).thenReturn(second);
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);

            ResourceHuntBreedListener listener = new ResourceHuntBreedListener(plugin, resourceHunt);
            listener.onEntityEnterLoveMode(firstEvent);
            listener.onEntityEnterLoveMode(secondEvent);
            assertNotNull(sweep.get(), "first tracked animal should start the lazy sweep");

            sweep.get().run();
        }

        assertEquals(1, getProgress(player.getUniqueId()),
                "two nearby successful parents must credit one pairing");
    }

    @ParameterizedTest
    @EnumSource(value = EntityType.class, names = {"TURTLE", "FROG", "SNIFFER"})
    void eggLayingBreedEventsAreIgnored(EntityType type) throws IOException {
        String targetKey = type.name().toLowerCase(Locale.ROOT);
        bootstrap("overworld:\n  breed:\n    " + targetKey
                + ": \"1:2.0\"\nnether:\n");
        player = server.addPlayer();
        player.teleport(resourceWorld.getSpawnLocation());

        LivingEntity child = mock(LivingEntity.class);
        when(child.getType()).thenReturn(type);
        when(child.getWorld()).thenReturn(resourceWorld);
        EntityBreedEvent event = mock(EntityBreedEvent.class);
        when(event.getEntity()).thenReturn(child);
        when(event.getBreeder()).thenReturn(player);

        resourceHunt.onEntityBreed(event);

        assertEquals(0, getProgress(player.getUniqueId()));
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
    // COLLECT-via-entity-drop tests.
    // -------------------------------------------------------------------------

    @Test
    void collectTargetGhastTearCreditsDropsFromGhastKill() throws IOException {
        bootstrap("""
                overworld:
                  collect:
                    ghast_tear: "5:2.0"
                nether:
                """);
        player = server.addPlayer();
        // Player joins and gets the ghast_tear COLLECT target.

        LivingEntity ghast = buildKilledEntity(EntityType.GHAST, resourceWorld, player);
        ItemStack tearDrop = new ItemStack(Material.GHAST_TEAR, 2);
        List<ItemStack> drops = new ArrayList<>(List.of(tearDrop));

        DamageSource damageSource = mock(DamageSource.class);
        EntityDeathEvent event = new EntityDeathEvent(ghast, damageSource, drops);
        server.getPluginManager().callEvent(event);

        assertEquals(2, getProgress(player.getUniqueId()),
                "Killing a ghast that drops 2 ghast_tear must credit 2 progress for a COLLECT:ghast_tear target");
        assertTrue(resourceHunt.isItemCounted(tearDrop),
                "The dropped ghast_tear stack must be marked counted so a later pickup cannot double-credit");
    }

    @Test
    void collectTargetBlazeRodCreditsDropsFromBlazeKill() throws IOException {
        bootstrap("""
                overworld:
                  collect:
                    blaze_rod: "10:2.0"
                nether:
                """);
        player = server.addPlayer();

        LivingEntity blaze = buildKilledEntity(EntityType.BLAZE, resourceWorld, player);
        ItemStack blazeRodDrop = new ItemStack(Material.BLAZE_ROD, 3);
        List<ItemStack> drops = new ArrayList<>(List.of(blazeRodDrop));

        DamageSource damageSource = mock(DamageSource.class);
        EntityDeathEvent event = new EntityDeathEvent(blaze, damageSource, drops);
        server.getPluginManager().callEvent(event);

        assertEquals(3, getProgress(player.getUniqueId()),
                "Killing a blaze that drops 3 blaze_rods must credit 3 progress for a COLLECT:blaze_rod target");
        assertTrue(resourceHunt.isItemCounted(blazeRodDrop),
                "The dropped blaze_rod stack must be marked counted");
    }

    @Test
    void noProgressWhenKillerHasNoMatchingCollectTarget() throws IOException {
        // Player has a COLLECT:iron_ingot target — killing a ghast dropping ghast_tear must not credit.
        bootstrap("""
                overworld:
                  collect:
                    iron_ingot: "10:2.0"
                nether:
                """);
        player = server.addPlayer();

        LivingEntity ghast = buildKilledEntity(EntityType.GHAST, resourceWorld, player);
        ItemStack tearDrop = new ItemStack(Material.GHAST_TEAR, 2);
        List<ItemStack> drops = new ArrayList<>(List.of(tearDrop));

        DamageSource damageSource = mock(DamageSource.class);
        EntityDeathEvent event = new EntityDeathEvent(ghast, damageSource, drops);
        server.getPluginManager().callEvent(event);

        assertEquals(0, getProgress(player.getUniqueId()),
                "Drops of a non-matching material must not credit progress");
    }

    @Test
    void collectTargetMaterialDrivenNotEntityTypeDriven() throws IOException {
        // Player has COLLECT:iron_ingot — a zombie that drops iron_ingot should credit (any mob, any material).
        bootstrap("""
                overworld:
                  collect:
                    iron_ingot: "10:2.0"
                nether:
                """);
        player = server.addPlayer();

        LivingEntity zombie = buildKilledEntity(EntityType.ZOMBIE, resourceWorld, player);
        ItemStack ironDrop = new ItemStack(Material.IRON_INGOT, 1);
        List<ItemStack> drops = new ArrayList<>(List.of(ironDrop));

        DamageSource damageSource = mock(DamageSource.class);
        EntityDeathEvent event = new EntityDeathEvent(zombie, damageSource, drops);
        server.getPluginManager().callEvent(event);

        assertEquals(1, getProgress(player.getUniqueId()),
                "A zombie dropping iron_ingot must credit progress for a COLLECT:iron_ingot target — mechanic is material-driven, not entity-type-driven");
        assertTrue(resourceHunt.isItemCounted(ironDrop),
                "The dropped iron_ingot must be marked counted");
    }

    // -------------------------------------------------------------------------
    // hasCompletedFirstTier tests.
    // -------------------------------------------------------------------------

    @Test
    void hasCompletedFirstTierIsFalseForFreshTarget() throws IOException {
        bootstrap("""
                overworld:
                  collect:
                    ghast_tear: "5:2.0"
                nether:
                """);
        player = server.addPlayer();

        assertFalse(resourceHunt.hasCompletedFirstTier(player.getUniqueId()),
                "A freshly-assigned target must not report first tier complete");
    }

    @Test
    void hasCompletedFirstTierIsFalseForUnknownPlayer() throws IOException {
        bootstrap("""
                overworld:
                  collect:
                    ghast_tear: "5:2.0"
                nether:
                """);
        // No addPlayer call — UUID has never been assigned a target.
        assertFalse(resourceHunt.hasCompletedFirstTier(UUID.randomUUID()),
                "An unknown UUID must report false");
    }

    @Test
    void hasCompletedFirstTierIsTrueAfterCrossingFirstThreshold() throws IOException {
        // Tier 1 threshold is the base amount (5). Drop 5 ghast_tear and the player crosses T1.
        bootstrap("""
                overworld:
                  collect:
                    ghast_tear: "5:2.0"
                nether:
                """);
        player = server.addPlayer();

        LivingEntity ghast = buildKilledEntity(EntityType.GHAST, resourceWorld, player);
        ItemStack tearDrop = new ItemStack(Material.GHAST_TEAR, 5);
        List<ItemStack> drops = new ArrayList<>(List.of(tearDrop));

        DamageSource damageSource = mock(DamageSource.class);
        EntityDeathEvent event = new EntityDeathEvent(ghast, damageSource, drops);
        server.getPluginManager().callEvent(event);

        assertTrue(resourceHunt.hasCompletedFirstTier(player.getUniqueId()),
                "Crossing the tier-1 threshold (5) must flip hasCompletedFirstTier to true");
    }

    @Test
    void hasCompletedFirstTierIsFalseBelowFirstThreshold() throws IOException {
        // Drop only 4 of the required 5 — progress accrues but T1 is not crossed.
        bootstrap("""
                overworld:
                  collect:
                    ghast_tear: "5:2.0"
                nether:
                """);
        player = server.addPlayer();

        LivingEntity ghast = buildKilledEntity(EntityType.GHAST, resourceWorld, player);
        ItemStack tearDrop = new ItemStack(Material.GHAST_TEAR, 4);
        List<ItemStack> drops = new ArrayList<>(List.of(tearDrop));

        DamageSource damageSource = mock(DamageSource.class);
        EntityDeathEvent event = new EntityDeathEvent(ghast, damageSource, drops);
        server.getPluginManager().callEvent(event);

        assertFalse(resourceHunt.hasCompletedFirstTier(player.getUniqueId()),
                "Progress short of the tier-1 threshold must keep hasCompletedFirstTier false");
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

    private Animals buildBredAnimal(UUID uuid, WorldMock world, double x) {
        Animals animal = mock(Animals.class);
        when(animal.getUniqueId()).thenReturn(uuid);
        when(animal.getType()).thenReturn(EntityType.TURTLE);
        when(animal.getWorld()).thenReturn(world);
        when(animal.getLocation()).thenReturn(new Location(world, x, 64, 0));
        when(animal.isDead()).thenReturn(false);
        when(animal.isValid()).thenReturn(true);
        when(animal.isLoveMode()).thenReturn(false);
        when(animal.getAge()).thenReturn(1);
        return animal;
    }

    private EntityEnterLoveModeEvent loveModeEvent(Animals animal, PlayerMock feeder) {
        EntityEnterLoveModeEvent event = mock(EntityEnterLoveModeEvent.class);
        when(event.getEntity()).thenReturn(animal);
        when(event.getHumanEntity()).thenReturn(feeder);
        when(event.getTicksInLove()).thenReturn(600);
        return event;
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

    /**
     * Creates a Mockito-stubbed LivingEntity of the given type in the given world, with the
     * specified player as its killer. getWorld() must return a world whose key passes
     * ResourceHunt.isResourceWorld() and matches the activeWorldKey equality check.
     */
    private LivingEntity buildKilledEntity(EntityType entityType, WorldMock world, PlayerMock killer) {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getType()).thenReturn(entityType);
        when(entity.getWorld()).thenReturn(world);
        when(entity.getKiller()).thenReturn(killer);
        return entity;
    }
}
