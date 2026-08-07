package me.beeliebub.tweaks.enchantments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

// Lives in the production package (not the tests.* mirror) because it exercises SpawnerPickup/
// EggCollector/Lumberjack's package-private config-knob accessors - see the root CLAUDE.md's
// "Test Layout" section for this project's documented exception.
class EnchantmentConfigKnobsTest {

    private Tweaks plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ─── SpawnerPickup ─────────────────────────────────────────────────────

    @Test
    void spawnerPickupDropChanceDefaultsToBundledTwentyPercent() {
        SpawnerPickup spawnerPickup = new SpawnerPickup(plugin);
        assertEquals(0.20, spawnerPickup.dropChance(), 1e-9);
    }

    @Test
    void spawnerPickupDropChancePercentConvertsToFraction() {
        SpawnerPickup spawnerPickup = new SpawnerPickup(plugin);
        plugin.getConfig().set("enchantments.spawner-pickup.drop-chance-percent", 50.0);
        assertEquals(0.50, spawnerPickup.dropChance(), 1e-9,
                "A 50% config value must convert to a 0.5 fraction, not roll as 5000%");
    }

    @Test
    void spawnerPickupDropChanceClampsAboveOneHundredPercentToOneHundred() {
        SpawnerPickup spawnerPickup = new SpawnerPickup(plugin);
        plugin.getConfig().set("enchantments.spawner-pickup.drop-chance-percent", 500.0);
        assertEquals(1.0, spawnerPickup.dropChance(), 1e-9,
                "A direct config.yml hand-edit above 100% must clamp, not silently always-fire beyond 1.0");
    }

    @Test
    void spawnerPickupDropChanceClampsNegativeToZero() {
        SpawnerPickup spawnerPickup = new SpawnerPickup(plugin);
        plugin.getConfig().set("enchantments.spawner-pickup.drop-chance-percent", -20.0);
        assertEquals(0.0, spawnerPickup.dropChance(), 1e-9);
    }

    @Test
    void spawnerPickupBreakAtDefaultsToBundledFive() {
        SpawnerPickup spawnerPickup = new SpawnerPickup(plugin);
        assertEquals(5, spawnerPickup.breakAt());
    }

    @Test
    void spawnerPickupBreakAtClampsBelowOneToOne() {
        SpawnerPickup spawnerPickup = new SpawnerPickup(plugin);
        plugin.getConfig().set("enchantments.spawner-pickup.uses", 0);
        assertEquals(1, spawnerPickup.breakAt());
        plugin.getConfig().set("enchantments.spawner-pickup.uses", -3);
        assertEquals(1, spawnerPickup.breakAt());
    }

    // ─── EggCollector ──────────────────────────────────────────────────────

    @Test
    void eggCollectorBreakAtDefaultsToBundledFive() {
        EggCollector eggCollector = new EggCollector(plugin, mock(QualityRegistry.class));
        assertEquals(5, eggCollector.breakAt());
    }

    @Test
    void eggCollectorBreakAtLiveEditTakesEffectWithoutReconstruction() {
        EggCollector eggCollector = new EggCollector(plugin, mock(QualityRegistry.class));
        plugin.getConfig().set("enchantments.egg-collector.uses", 2);
        assertEquals(2, eggCollector.breakAt());
    }

    @Test
    void eggCollectorBreakAtClampsBelowOneToOne() {
        EggCollector eggCollector = new EggCollector(plugin, mock(QualityRegistry.class));
        plugin.getConfig().set("enchantments.egg-collector.uses", 0);
        assertEquals(1, eggCollector.breakAt());
    }

    // ─── Lumberjack ────────────────────────────────────────────────────────

    @Test
    void lumberjackMaxLogsDefaultsToBundledTwoHundredFiftySix() {
        Lumberjack lumberjack = new Lumberjack(plugin, null, null, null);
        assertEquals(256, lumberjack.maxLogs());
    }

    @Test
    void lumberjackMaxLogsLiveEditTakesEffectWithoutReconstruction() {
        Lumberjack lumberjack = new Lumberjack(plugin, null, null, null);
        plugin.getConfig().set("enchantments.lumberjack.max-logs", 10);
        assertEquals(10, lumberjack.maxLogs());
    }

    @Test
    void lumberjackMaxLogsClampsBelowOneToOne() {
        Lumberjack lumberjack = new Lumberjack(plugin, null, null, null);
        plugin.getConfig().set("enchantments.lumberjack.max-logs", -1);
        assertEquals(1, lumberjack.maxLogs());
    }
}
