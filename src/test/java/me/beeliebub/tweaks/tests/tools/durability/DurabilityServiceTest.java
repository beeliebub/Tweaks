package me.beeliebub.tweaks.tests.tools.durability;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import me.beeliebub.tweaks.tools.durability.DurabilityService;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurabilityServiceTest {

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

    @Test
    void defaultMultiplierAndTierStepMatchThePlannedCurve() {
        assertEquals(300, DurabilityService.maxDamageFor(100, 3.0, 0));
        assertEquals(270, DurabilityService.maxDamageFor(100, 3.0, 1));
        assertEquals(30, DurabilityService.maxDamageFor(100, 3.0, 9));
    }

    @Test
    void customTierStepIsAppliedWithoutChangingTheAnchoredMultiplier() {
        assertEquals(240, DurabilityService.maxDamageFor(100, 3.0, 2, 10.0));
        assertEquals(225, DurabilityService.maxDamageFor(100, 3.0, 2, 12.5));
    }

    @Test
    void plainItemsRemainOutsideCustomDurabilityParticipation() {
        DurabilityService durability = new DurabilityService(plugin);
        ItemStack item = damaged(Material.IRON_PICKAXE, Material.IRON_PICKAXE.getMaxDurability() - 1);

        assertFalse(durability.ensureStamped(item));
        assertFalse(AugmentLedger.hasLedger(item));
        assertFalse(durability.isSpent(item));
        assertTrue(durability.canTakeDamage(item, 1));
        assertEquals(null, item.getData(DataComponentTypes.MAX_DAMAGE));
    }

    @Test
    void augmentedItemsReceiveTheFloorAndStampedMaxDamage() {
        DurabilityService durability = new DurabilityService(plugin);
        ItemStack item = new ItemStack(Material.IRON_PICKAXE);
        new AugmentLedger(plugin).write(item, 0, java.util.List.of(), true);

        assertTrue(durability.ensureStamped(item));
        int max = durability.maxDamage(item);
        setDamage(item, max, max - 1);

        assertTrue(durability.isSpent(item), "damageable=" + ((Damageable) item.getItemMeta()).getDamage()
                + " data=" + item.getData(DataComponentTypes.DAMAGE) + " max=" + durability.maxDamage(item));
        assertFalse(durability.canTakeDamage(item, 1));
        assertEquals(750, max);
    }

    @Test
    void neverBreakOnlyClampsAnExistingStampWhenEnabled() {
        DurabilityService durability = new DurabilityService(plugin);
        ItemStack item = new ItemStack(Material.IRON_PICKAXE);
        new AugmentLedger(plugin).write(item, 0, java.util.List.of(), true);
        assertTrue(durability.ensureStamped(item));
        int max = durability.maxDamage(item);

        setDamage(item, max, max);
        plugin.getConfig().set("tools.never-break.enabled", false);
        durability.ensureStamped(item);
        assertEquals(max, item.getData(DataComponentTypes.DAMAGE),
                "data=" + item.getData(DataComponentTypes.DAMAGE) + " max=" + max);

        plugin.getConfig().set("tools.never-break.enabled", true);
        durability.ensureStamped(item);
        assertEquals(max - 1, item.getData(DataComponentTypes.DAMAGE));
    }

    private static ItemStack damaged(Material material, int damage) {
        ItemStack item = new ItemStack(material);
        Damageable meta = (Damageable) item.getItemMeta();
        meta.setDamage(damage);
        item.setItemMeta(meta);
        return item;
    }

    private static void setDamage(ItemStack item, int max, int damage) {
        item.setData(DataComponentTypes.MAX_DAMAGE, max);
        Damageable meta = (Damageable) item.getItemMeta();
        meta.setDamage(damage);
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.MAX_DAMAGE, max);
        item.setData(DataComponentTypes.DAMAGE, damage);
    }
}
