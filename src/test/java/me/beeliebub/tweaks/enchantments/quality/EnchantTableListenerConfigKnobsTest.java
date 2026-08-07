package me.beeliebub.tweaks.enchantments.quality;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.worldmanagement.MoonSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

// Lives in the production package (not the tests.* mirror) because it exercises
// EnchantTableListener's package-private config-knob accessors - see the root CLAUDE.md's
// "Test Layout" section for this project's documented exception.
class EnchantTableListenerConfigKnobsTest {

    private Tweaks plugin;
    private EnchantTableListener listener;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        listener = new EnchantTableListener(plugin, mock(QualityRegistry.class), mock(MoonSystem.class));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void qualityChanceDefaultsToBundledTenPercent() {
        assertEquals(0.10, listener.qualityChance(), 1e-9);
    }

    @Test
    void bloodMoonQualityChanceDefaultsToBundledFiftyPercent() {
        assertEquals(0.50, listener.bloodMoonQualityChance(), 1e-9);
    }

    @Test
    void qualityChancePercentConvertsToFractionNotToAlwaysFire() {
        plugin.getConfig().set("enchantments.quality.chance-percent", 25.0);
        assertEquals(0.25, listener.qualityChance(), 1e-9,
                "A 25% config value must convert to a 0.25 fraction, not roll as 2500%");
    }

    @Test
    void bloodMoonQualityChancePercentConvertsToFraction() {
        plugin.getConfig().set("enchantments.quality.blood-moon-chance-percent", 75.0);
        assertEquals(0.75, listener.bloodMoonQualityChance(), 1e-9);
    }

    @Test
    void qualityChanceClampsAboveOneHundredPercentToOneHundred() {
        plugin.getConfig().set("enchantments.quality.chance-percent", 500.0);
        assertEquals(1.0, listener.qualityChance(), 1e-9,
                "A direct config.yml hand-edit above 100% must clamp, not silently always-fire beyond 1.0");
    }

    @Test
    void bloodMoonQualityChanceClampsNegativeToZero() {
        plugin.getConfig().set("enchantments.quality.blood-moon-chance-percent", -30.0);
        assertEquals(0.0, listener.bloodMoonQualityChance(), 1e-9);
    }
}
