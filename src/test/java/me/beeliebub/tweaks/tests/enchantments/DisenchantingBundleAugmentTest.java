package me.beeliebub.tweaks.tests.enchantments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.DisenchantingBundle;
import me.beeliebub.tweaks.tools.augments.AugmentEntry;
import me.beeliebub.tweaks.tools.augments.AugmentGemItem;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisenchantingBundleAugmentTest {

    private ServerMock server;
    private Tweaks plugin;
    private AugmentService augments;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        augments = new AugmentService(plugin, null);
        player = server.addPlayer("BundleAugmentTester");
        while (player.nextComponentMessage() != null) {}
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void legacyCurseOnlyItemRefusesBeforeWritingLedgerOrConsumingBundle() {
        DisenchantingBundle bundleListener = new DisenchantingBundle(plugin, null, augments);
        ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
        item.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);
        ItemStack bundle = bundle();
        InventoryClickEvent event = event(item, bundle);

        bundleListener.onInventoryClick(event);

        verify(event).setCancelled(true);
        assertTrue(item.containsEnchantment(Enchantment.BINDING_CURSE));
        assertFalse(AugmentLedger.hasLedger(item));
        assertEquals(1, bundle.getAmount());
    }

    @Test
    void recoveryDestroysBoundCursesAndReturnsRiderFreeGems() {
        DisenchantingBundle bundleListener = new DisenchantingBundle(plugin, null, augments);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 1,
                List.of(new AugmentEntry(NamespacedKey.minecraft("efficiency"), 5, true)), true);
        augments.ledger().appendCurses(item,
                List.of(new AugmentGemItem.CurseRider(Enchantment.VANISHING_CURSE, 1)));
        item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        item.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
        ItemStack bundle = bundle();

        bundleListener.onInventoryClick(event(item, bundle));

        assertEquals(0, item.getAmount());
        assertEquals(0, bundle.getAmount());
        AugmentGemItem.GemData recovered = augments.inventoryGems(player).stream()
                .map(location -> augments.gemItem().read(location.item()))
                .filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow();
        assertEquals(Enchantment.EFFICIENCY, recovered.enchantment());
        assertTrue(recovered.curses().isEmpty());
    }

    @Test
    void unresolvedLedgerRefusesBeforeLegacyMigrationOrConsumption() {
        DisenchantingBundle bundleListener = new DisenchantingBundle(plugin, null, augments);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "augment_attached"),
                PersistentDataType.LIST.strings(), List.of("v1|minecraft:not_registered|1|true"));
        item.setItemMeta(meta);
        ItemStack bundle = bundle();

        bundleListener.onInventoryClick(event(item, bundle));

        assertTrue(item.containsEnchantment(Enchantment.EFFICIENCY));
        assertEquals(1, bundle.getAmount());
        assertTrue(augments.inventoryGems(player).isEmpty());
    }

    @Test
    void migratedRecoveryRefusesAnUnrepresentedRealEnchantment() {
        DisenchantingBundle bundleListener = new DisenchantingBundle(plugin, null, augments);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 1,
                List.of(new AugmentEntry(NamespacedKey.minecraft("efficiency"), 5, true)), true);
        item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        item.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
        ItemStack bundle = bundle();

        bundleListener.onInventoryClick(event(item, bundle));

        assertEquals(1, item.getAmount());
        assertEquals(1, bundle.getAmount());
        assertTrue(augments.inventoryGems(player).isEmpty());
    }

    @Test
    void failedRecoveryRollDoesNotLowerTheNextChance() {
        DisenchantingBundle bundleListener = new DisenchantingBundle(plugin, null, augments,
                new ScriptedRandom(0, 0.99, 0.75));
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 3, List.of(
                new AugmentEntry(NamespacedKey.minecraft("efficiency"), 5, true),
                new AugmentEntry(NamespacedKey.minecraft("unbreaking"), 3, true),
                new AugmentEntry(NamespacedKey.minecraft("fortune"), 3, true)), true);
        ItemStack bundle = bundle();

        bundleListener.onInventoryClick(event(item, bundle));

        assertEquals(2, augments.inventoryGems(player).size());
        assertEquals(0, item.getAmount());
        assertEquals(0, bundle.getAmount());
    }

    private InventoryClickEvent event(ItemStack item, ItemStack bundle) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getClick()).thenReturn(ClickType.RIGHT);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getCurrentItem()).thenReturn(item);
        when(event.getCursor()).thenReturn(bundle);
        return event;
    }

    private static ItemStack bundle() {
        ItemStack item = new ItemStack(Material.BUNDLE);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(Component.text("Disenchanting Bundle")));
        item.setItemMeta(meta);
        return item;
    }

    private static final class ScriptedRandom extends Random {
        private final int tieChoice;
        private final double[] rolls;
        private int rollIndex;

        private ScriptedRandom(int tieChoice, double... rolls) {
            this.tieChoice = tieChoice;
            this.rolls = rolls;
        }

        @Override
        public int nextInt(int bound) {
            return Math.min(tieChoice, bound - 1);
        }

        @Override
        public double nextDouble() {
            return rolls[rollIndex++];
        }
    }
}
