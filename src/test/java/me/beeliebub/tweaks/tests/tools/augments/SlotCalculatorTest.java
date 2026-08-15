package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import me.beeliebub.tweaks.tools.augments.SlotCalculator;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotCalculatorTest {

    private ServerMock server;
    private Tweaks plugin;
    private SlotCalculator calculator;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        calculator = new SlotCalculator(plugin, null);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void capacityUsesConfiguredMaterialFamiliesAndFallback() {
        assertEquals(3, calculator.capacity(Material.WOODEN_PICKAXE));
        assertEquals(11, calculator.capacity(Material.NETHERITE_PICKAXE));
        assertEquals(5, calculator.capacity(Material.BOW));
    }

    @Test
    void pricesAndQualityWeightsUseTheConfiguredLadders() {
        assertEquals(30, calculator.price(1));
        assertEquals(35, calculator.price(2));
        assertEquals(4, calculator.qualityWeight(QualityTier.EPIC));
        assertEquals(1, calculator.qualityWeight((QualityTier) null));
    }
}
