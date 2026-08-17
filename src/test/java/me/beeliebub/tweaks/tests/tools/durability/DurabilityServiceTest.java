package me.beeliebub.tweaks.tests.tools.durability;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import me.beeliebub.tweaks.tools.durability.DurabilityService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
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
    void projectedPoolsNeverCollapseBelowTheMinimumPool() {
        assertTrue(DurabilityService.maxDamageFor(100, 3.0, 10, 10.0) >= 8);
        assertTrue(DurabilityService.maxDamageFor(100, 3.0, 4, 25.0) >= 8);
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

    @Test
    void spentStateAndCollateralDamageAgreeAtTheOnePointBoundary() {
        DurabilityService durability = new DurabilityService(plugin);
        ItemStack item = augmented(Material.IRON_PICKAXE, durability);
        int max = durability.maxDamage(item);

        setDamage(item, max - 2);
        assertFalse(durability.isSpent(item));
        assertTrue(durability.canTakeDamage(item, 1));

        setDamage(item, max - 1);
        assertTrue(durability.isSpent(item));
        assertFalse(durability.canTakeDamage(item, 1));
        assertEquals(1, max - durability.damage(item));
        assertEquals(1, durability.maxDamage(item) - durability.depletedThreshold(item));
    }

    @Test
    void unchangedStampsPreserveComponentsAndForeignMetadata() {
        DurabilityService durability = new DurabilityService(plugin);
        ItemStack item = new ItemStack(Material.IRON_PICKAXE);
        Damageable meta = (Damageable) item.getItemMeta();
        meta.displayName(Component.text("Renamed"));
        meta.lore(java.util.List.of(Component.text("foreign lore")));
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.setCustomModelData(123);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "foreign_key"),
                PersistentDataType.STRING, "kept");
        item.setItemMeta(meta);
        new AugmentLedger(plugin).write(item, 0, java.util.List.of(), true);

        assertTrue(durability.ensureStamped(item));
        int damage = durability.damage(item);
        int max = durability.maxDamage(item);
        assertTrue(durability.ensureStamped(item));

        assertEquals(damage, item.getData(DataComponentTypes.DAMAGE));
        assertEquals(max, item.getData(DataComponentTypes.MAX_DAMAGE));
        Damageable unchanged = (Damageable) item.getItemMeta();
        assertEquals(Component.text("Renamed"), unchanged.displayName());
        assertEquals(java.util.List.of(Component.text("foreign lore")), unchanged.lore());
        assertEquals(3, unchanged.getEnchantLevel(Enchantment.UNBREAKING));
        assertEquals(123, unchanged.getCustomModelData());
        assertEquals("kept", unchanged.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "foreign_key"), PersistentDataType.STRING));
    }

    @Test
    void damageAboveALoweredMaximumClampsToTheNewFloor() {
        plugin.getConfig().set("tools.repair-kit.max-tier", 1);
        DurabilityService durability = new DurabilityService(plugin);
        ItemStack item = augmented(Material.IRON_PICKAXE, durability);
        Damageable meta = (Damageable) item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "durability_tier"),
                PersistentDataType.INTEGER, 9);
        item.setItemMeta(meta);
        setDamage(item, 800);

        durability.ensureStamped(item);

        assertEquals(675, durability.maxDamage(item));
        assertEquals(674, durability.damage(item));
        assertEquals(9, durability.tier(item), "the stored tier is not rewritten when projection clamps it");
    }

    @Test
    void repairRefusesFullItemsAndClearsTheMarkerAfterRepairingDamage() {
        DurabilityService durability = new DurabilityService(plugin);
        ItemStack item = augmented(Material.IRON_PICKAXE, durability);
        int originalTier = durability.tier(item);

        assertFalse(durability.repair(item));
        assertEquals(originalTier, durability.tier(item));

        setDamage(item, durability.maxDamage(item) - 1);
        durability.ensureStamped(item);
        assertTrue(durability.repair(item));
        assertEquals(1, durability.tier(item));
        assertEquals(0, durability.damage(item));
        assertEquals(java.util.List.of(), item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "durability_owned_lore"), PersistentDataType.LIST.strings()));
    }

    @Test
    void pdcRepairStampChangesStillReassertBothComponents() {
        DurabilityService durability = new DurabilityService(plugin);
        ItemStack item = augmented(Material.IRON_PICKAXE, durability);
        setDamage(item, 4);
        Damageable meta = (Damageable) item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "durability_tier"),
                PersistentDataType.STRING, "malformed");
        meta.setDamage(0);
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.MAX_DAMAGE, 750);
        item.setData(DataComponentTypes.DAMAGE, 4);

        durability.ensureStamped(item);

        assertEquals(750, item.getData(DataComponentTypes.MAX_DAMAGE));
        assertEquals(4, item.getData(DataComponentTypes.DAMAGE));
        assertEquals(4, durability.damage(item));
    }

    private ItemStack augmented(Material material, DurabilityService durability) {
        ItemStack item = new ItemStack(material);
        new AugmentLedger(plugin).write(item, 0, java.util.List.of(), true);
        assertTrue(durability.ensureStamped(item));
        return item;
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

    private static void setDamage(ItemStack item, int damage) {
        Integer max = item.getData(DataComponentTypes.MAX_DAMAGE);
        int resolvedMax = max == null ? item.getType().getMaxDurability() : max;
        setDamage(item, resolvedMax, damage);
    }
}
