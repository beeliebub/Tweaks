package me.beeliebub.tweaks.tests.enchantments.quality;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry.QualityInfo;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QualityRegistryTest {

    private ServerMock server;
    private Tweaks plugin;
    private QualityRegistry registry;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(Tweaks.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        registry = new QualityRegistry(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void constructorBuildsRegistryEvenWithoutDataPackLoaded() {
        // The data pack supplying jass:<tier>_<enchant> variants isn't loaded here, so the
        // registry will end up empty. Constructor must not throw and the public API must
        // gracefully report "no variant found" for every lookup.
        assertNotNull(registry);
    }

    @Test
    void matchEnchantNameReturnsNameForKnownEnchants() {
        Enchantment fortune = mockEnchant("fortune");
        assertEquals("fortune", registry.matchEnchantName(fortune));

        Enchantment looting = mockEnchant("looting");
        assertEquals("looting", registry.matchEnchantName(looting));

        Enchantment efficacy = mockEnchant("efficacy");
        assertEquals("efficacy", registry.matchEnchantName(efficacy));
    }

    @Test
    void matchEnchantNameReturnsNullForUnsupportedEnchants() {
        Enchantment unsupported = mockEnchant("aqua_affinity");
        assertNull(registry.matchEnchantName(unsupported));
    }

    @Test
    void getVariantReturnsNullWhenDataPackVariantsAbsent() {
        for (QualityTier tier : QualityTier.values()) {
            assertNull(registry.getVariant("fortune", tier));
        }
    }

    @Test
    void getTierReturnsNullForVanillaEnchant() {
        assertNull(registry.getTier(mockEnchant("fortune")));
    }

    @Test
    void getNameReturnsNullForUnregisteredEnchant() {
        assertNull(registry.getName(mockEnchant("fortune")));
    }

    @Test
    void getToolQualityReturnsNullForNullOrEmptyTool() {
        assertNull(registry.getToolQuality(null, "fortune"));
        assertNull(registry.getToolQuality(new ItemStack(Material.AIR), "fortune"));
    }

    @Test
    void getToolQualityTierMirrorsGetToolQualityResult() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        // No quality variants are registered without a data pack, so both return null.
        assertNull(registry.getToolQualityTier(tool, "fortune"));
    }

    @Test
    void getEffectiveUnbreakingLevelReturnsZeroForEmptyTool() {
        assertEquals(0, registry.getEffectiveUnbreakingLevel(null));
        assertEquals(0, registry.getEffectiveUnbreakingLevel(new ItemStack(Material.AIR)));
    }

    @Test
    void getEffectiveUnbreakingLevelFallsBackToVanillaLevelWhenNoQualityVariant() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        tool.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        assertEquals(3, registry.getEffectiveUnbreakingLevel(tool));
    }

    @Test
    void qualityInfoRecordAccessorsReturnConstructorValues() {
        QualityInfo info = new QualityInfo(QualityTier.LEGENDARY, 5);
        assertEquals(QualityTier.LEGENDARY, info.tier());
        assertEquals(5, info.level());
    }

    private Enchantment mockEnchant(String keyName) {
        Enchantment e = mock(Enchantment.class);
        NamespacedKey key = NamespacedKey.minecraft(keyName);
        doReturn(key).when(e).getKey();
        return e;
    }
}
