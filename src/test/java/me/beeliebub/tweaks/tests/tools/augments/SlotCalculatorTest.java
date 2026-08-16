package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import me.beeliebub.tweaks.tools.augments.AugmentEntry;
import me.beeliebub.tweaks.tools.augments.SlotCalculator;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(3, calculator.capacity(Material.LEATHER_CHESTPLATE));
        assertEquals(4, calculator.capacity(Material.CHAINMAIL_CHESTPLATE));
        assertEquals(5, calculator.capacity(Material.TURTLE_HELMET));
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

    @Test
    void largeExactCapacityKeepsPurchaseValueButBoundsVisualDots() {
        plugin.getConfig().set("tools.augments.slot-capacity.diamond_pickaxe", 1_000_000);

        assertEquals(1_000_000, calculator.capacity(Material.DIAMOND_PICKAXE));
        assertEquals(64, calculator.slotDots(0, 0, 64).length());
        assertEquals(65, calculator.slotDots(0, 0, 65).length());
        assertTrue(calculator.slotDots(0, 0, 65).endsWith("…"));
        assertEquals(65, calculator.slotDots(0, 0, Integer.MAX_VALUE).length());
    }

    @Test
    void usedSlotMathSaturatesInsteadOfWrappingWhenConfiguredWeightsAreHuge() {
        plugin.getConfig().set("tools.augments.quality-slot-cost.none", Integer.MAX_VALUE);
        List<AugmentEntry> entries = List.of(
                new AugmentEntry(org.bukkit.NamespacedKey.minecraft("fortune"), 3, true),
                new AugmentEntry(org.bukkit.NamespacedKey.minecraft("unbreaking"), 3, true));

        assertEquals(Integer.MAX_VALUE, calculator.used(entries));
    }
}
