package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import me.beeliebub.tweaks.tools.augments.AugmentEntry;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import me.beeliebub.tweaks.tools.augments.SlotCalculator;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void bundledCapacityTableAndRepresentativeResolutionStayPinned() {
        assertEquals(3, calculator.capacity(Material.WOODEN_PICKAXE));
        assertEquals(3, calculator.capacity(Material.LEATHER_BOOTS));
        assertEquals(4, calculator.capacity(Material.STONE_SWORD));
        assertEquals(4, calculator.capacity(Material.CHAINMAIL_HELMET));
        assertEquals(5, calculator.capacity(Material.IRON_AXE));
        assertEquals(5, calculator.capacity(Material.TURTLE_HELMET));
        assertEquals(5, calculator.capacity(Material.COPPER_SWORD));
        assertEquals(5, calculator.capacity(Material.GOLDEN_BOOTS));
        assertEquals(7, calculator.capacity(Material.DIAMOND_HOE));
        assertEquals(11, calculator.capacity(Material.NETHERITE_LEGGINGS));
        assertEquals(6, calculator.capacity(Material.SHEARS));
        assertEquals(6, calculator.capacity(Material.FISHING_ROD));
        assertEquals(11, calculator.capacity(Material.MACE));
        assertEquals(7, calculator.capacity(Material.ELYTRA));
        assertEquals(5, calculator.capacity(Material.BOW));
        assertEquals("family:wooden", calculator.capacityResolution(Material.WOODEN_PICKAXE).key());
        assertEquals("default", calculator.capacityResolution(Material.BOW).key());
    }

    @Test
    void pricesAndQualityWeightsUseTheConfiguredLadders() {
        assertEquals(30, calculator.price(1));
        assertEquals(35, calculator.price(2));
        assertEquals(4, calculator.qualityWeight(QualityTier.EPIC));
        assertEquals(1, calculator.qualityWeight((QualityTier) null));
    }

    @Test
    void unlistedPricesUseTheHighestConfiguredValueAndEmptyPricesAreUnpriced() {
        plugin.getConfig().set("tools.augments.slot-prices", java.util.Map.of(1, 30, 2, 80));

        assertEquals(80, calculator.price(12));
        assertEquals("highest-configured", calculator.priceResolution(12).key());

        plugin.getConfig().set("tools.augments.slot-prices", java.util.Map.of());
        assertEquals(-1, calculator.price(1));
        assertEquals("unpriced", calculator.priceResolution(1).key());
    }

    @Test
    void unpricedSlotPurchaseRefusesWithoutWritingTheLedger() {
        plugin.getConfig().set("tools.augments.slot-prices", java.util.Map.of());
        AugmentService service = new AugmentService(plugin, null);
        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer("UnpricedBuyer");
        player.setLevel(30);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);

        assertFalse(service.purchaseSlot(player, item));
        assertFalse(AugmentLedger.hasLedger(item));
        assertEquals(30, player.getLevel());
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

    @Test
    void entryDotsAreBoundedLikeSlotDots() {
        assertEquals("●●●●", calculator.entryDots(4, true));
        assertEquals(SlotCalculator.MAX_RENDERED_ENTRY_DOTS + 1,
                calculator.entryDots(Integer.MAX_VALUE, false).length());
        assertTrue(calculator.entryDots(Integer.MAX_VALUE, false).endsWith("…"));
    }
}
