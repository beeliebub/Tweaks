package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.AugmentEntry;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AugmentServiceMigrationTest {

    private ServerMock server;
    private Tweaks plugin;
    private AugmentService augments;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        augments = new AugmentService(plugin, null);
        player = server.addPlayer("MigrationTester");
        while (player.nextComponentMessage() != null) {
            // Clear the join guidance before checking migration feedback.
        }
        player.setLevel(30);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void migrationWipesSlotsAndEntriesAndReturnsOnlyNonCurseGems() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 4);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        item.addUnsafeEnchantment(Enchantment.FORTUNE, 3);

        List<ItemStack> result = augments.migrateToGems(player, item);

        assertEquals(3, result.size());
        assertTrue(item.getEnchantments().isEmpty());
        assertEquals(0, augments.ledger().slots(item));
        assertTrue(augments.entries(item).isEmpty());
        assertTrue(augments.ledger().migrated(item));
    }

    @Test
    void migrationKeepsCursesOnTheItemAndDoesNotGemThem() {
        ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
        item.addUnsafeEnchantment(Enchantment.PROTECTION, 4);
        item.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);

        List<ItemStack> result = augments.migrateToGems(player, item);

        assertEquals(1, result.size());
        assertTrue(item.containsEnchantment(Enchantment.BINDING_CURSE));
        assertFalse(item.containsEnchantment(Enchantment.PROTECTION));
        assertEquals(0, augments.ledger().slots(item));
        assertTrue(augments.entries(item).isEmpty());
    }

    @Test
    void curseOnlyMigrationStillMarksTheItemWithoutCreatingAnEntry() {
        ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
        item.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);

        assertTrue(augments.migrateToGems(player, item).isEmpty());

        assertTrue(item.containsEnchantment(Enchantment.BINDING_CURSE));
        assertTrue(augments.ledger().migrated(item));
        assertEquals(0, augments.ledger().slots(item));
        assertTrue(augments.entries(item).isEmpty());
    }

    @Test
    void fullInventoryRefusesBeforeWritingOrRemovingAnything() {
        for (int i = 0; i < player.getInventory().getStorageContents().length; i++) {
            player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        }
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 4);

        assertNull(augments.migrateToGems(player, item));
        assertTrue(item.containsEnchantment(Enchantment.EFFICIENCY));
        assertFalse(AugmentLedger.hasLedger(item));
    }

    @Test
    void plainItemGetsNoLedgerUntilTheFirstSlotPurchase() {
        ItemStack item = new ItemStack(Material.IRON_PICKAXE);

        assertTrue(augments.migrateToGems(player, item).isEmpty());
        assertFalse(AugmentLedger.hasLedger(item));
        assertTrue(augments.purchaseSlot(player, item));
        assertEquals(1, augments.ledger().slots(item));
        assertTrue(augments.ledger().migrated(item));
    }

    @Test
    void alreadyMigratedItemIsLeftUntouched() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        AugmentEntry entry = new AugmentEntry(NamespacedKey.minecraft("efficiency"), 4, true);
        augments.ledger().write(item, 4, List.of(entry), true);
        item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 4);

        assertTrue(augments.migrateToGems(player, item).isEmpty());
        assertEquals(4, augments.ledger().slots(item));
        assertEquals(4, item.getEnchantmentLevel(Enchantment.EFFICIENCY));
    }

    @Test
    void partialLedgerRefusesLegacyMigrationWithoutChangingTheItem() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 4);
        augments.ledger().write(item, 2, List.of(), false);

        assertNull(augments.migrateToGems(player, item));
        assertEquals(4, item.getEnchantmentLevel(Enchantment.EFFICIENCY));
        assertEquals(2, augments.ledger().slots(item));
        assertFalse(augments.ledger().migrated(item));
    }
}
