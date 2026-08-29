package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tests.MessageAssert;
import io.papermc.paper.datacomponent.DataComponentTypes;
import me.beeliebub.tweaks.tools.augments.AugmentDialog;
import me.beeliebub.tweaks.tools.augments.AugmentGemItem;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import me.beeliebub.tweaks.tools.augments.AugmentPendingConfirmations;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import me.beeliebub.tweaks.tools.durability.DurabilityService;
import me.beeliebub.tweaks.xpbottle.ExperienceManager;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AugmentConfirmationTest {

    private ServerMock server;
    private Tweaks plugin;
    private AugmentService augments;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        augments = new AugmentService(plugin, null);
        player = server.addPlayer("ConfirmationTester");
        while (player.nextComponentMessage() != null) {}
        player.setLevel(30);
        plugin.getConfig().set("tools.augments.slot-prices", Map.of(1, 1, 2, 2));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void confirmSlotOneUnlockChargesAndMigratesLegacyEnchantments() {
        ItemStack item = legacyItem();
        player.getInventory().setItemInMainHand(item);
        augments.pendingConfirmations().create(player, item, 1);
        int before = new ExperienceManager(player).getCurrentExp();

        ItemStack confirmed = augments.confirmSlotOneUnlock(player);
        assertTrue(confirmed != null);

        assertEquals(before - (new ExperienceManager(player).getXpForLevel(30)
                - new ExperienceManager(player).getXpForLevel(29)),
                new ExperienceManager(player).getCurrentExp());
        assertTrue(augments.ledger().migrated(confirmed));
        assertEquals(1, augments.ledger().slots(confirmed));
        assertFalse(confirmed.containsEnchantment(Enchantment.EFFICIENCY));
        assertEquals(Enchantment.EFFICIENCY, augments.inventoryGems(player).getFirst().item()
                .getData(io.papermc.paper.datacomponent.DataComponentTypes.STORED_ENCHANTMENTS)
                .enchantments().keySet().stream().findFirst().orElseThrow());
    }

    @Test
    void confirmSlotOneUnlockFullyRepairsTheConvertedTool() {
        DurabilityService durability = new DurabilityService(plugin);
        AugmentPendingConfirmations pending = new AugmentPendingConfirmations(plugin);
        AugmentService wired = new AugmentService(plugin, null,
                durability::ensureStamped, durability::refreshLoreTail,
                durability::restoreFullDurability, pending);

        ItemStack item = legacyItem();
        item.setData(DataComponentTypes.DAMAGE, 900);
        player.getInventory().setItemInMainHand(item);
        pending.create(player, item, 1);

        ItemStack confirmed = wired.confirmSlotOneUnlock(player);
        assertTrue(confirmed != null);
        assertTrue(wired.ledger().migrated(confirmed));
        assertEquals(0, ((Damageable) confirmed.getItemMeta()).getDamage());
        assertEquals(0, (int) confirmed.getData(DataComponentTypes.DAMAGE));
    }

    @Test
    void confirmSlotOneUnlockPreservesCursesAsRealEnchantments() {
        ItemStack item = legacyItem();
        item.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
        player.getInventory().setItemInMainHand(item);
        augments.pendingConfirmations().create(player, item, 1);

        ItemStack confirmed = augments.confirmSlotOneUnlock(player);
        assertTrue(confirmed != null);

        assertTrue(confirmed.containsEnchantment(Enchantment.VANISHING_CURSE));
        assertFalse(confirmed.containsEnchantment(Enchantment.EFFICIENCY));
        assertTrue(augments.ledger().migrated(confirmed));
    }

    @Test
    void confirmSlotOneUnlockRefusesWhenInventoryCannotFitMigrationGems() {
        ItemStack item = legacyItem();
        player.getInventory().setItemInMainHand(item);
        for (int slot = 1; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        }
        augments.pendingConfirmations().create(player, item, 1);
        int before = new ExperienceManager(player).getCurrentExp();

        assertNull(augments.confirmSlotOneUnlock(player));
        assertEquals(before, new ExperienceManager(player).getCurrentExp());
        assertFalse(AugmentLedger.hasLedger(item));
        assertTrue(item.containsEnchantment(Enchantment.EFFICIENCY));
    }

    @Test
    void confirmSlotOneUnlockRefusesWithNoPendingConfirmation() {
        ItemStack item = legacyItem();
        player.getInventory().setItemInMainHand(item);

        assertNull(augments.confirmSlotOneUnlock(player));
        assertFalse(AugmentLedger.hasLedger(item));
        assertTrue(item.containsEnchantment(Enchantment.EFFICIENCY));
    }

    @Test
    void confirmSlotOneUnlockRefusesWhenHeldItemChangedSincePrompt() {
        ItemStack item = legacyItem();
        player.getInventory().setItemInMainHand(item);
        augments.pendingConfirmations().create(player, item, 1);
        player.getInventory().getItemInMainHand().addUnsafeEnchantment(Enchantment.UNBREAKING, 3);

        assertNull(augments.confirmSlotOneUnlock(player));
        assertFalse(AugmentLedger.hasLedger(item));
        assertTrue(item.containsEnchantment(Enchantment.EFFICIENCY));
    }

    @Test
    void confirmSlotOneUnlockChargesTheQuotedPriceAfterAConfigChange() {
        ItemStack item = legacyItem();
        player.getInventory().setItemInMainHand(item);
        augments.pendingConfirmations().create(player, item, 1);
        plugin.getConfig().set("tools.augments.slot-prices", Map.of(1, 100));

        assertTrue(augments.confirmSlotOneUnlock(player) != null);
        assertEquals(29, player.getLevel());
    }

    @Test
    void confirmSlotOneUnlockIsNotRedeemableTwice() {
        ItemStack item = legacyItem();
        player.getInventory().setItemInMainHand(item);
        augments.pendingConfirmations().create(player, item, 1);
        assertTrue(augments.confirmSlotOneUnlock(player) != null);
        int after = new ExperienceManager(player).getCurrentExp();

        assertNull(augments.confirmSlotOneUnlock(player));
        assertEquals(after, new ExperienceManager(player).getCurrentExp());
    }

    @Test
    void purchaseSlotDoesNotMarkMigratedWhileLegacyEnchantmentsRemain() {
        ItemStack item = legacyItem();
        player.setLevel(30);

        assertTrue(augments.purchaseSlot(player, item));
        assertTrue(AugmentLedger.hasLedger(item));
        assertFalse(augments.ledger().migrated(item));
        assertTrue(item.containsEnchantment(Enchantment.EFFICIENCY));
    }

    @Test
    void curseOnlyGemOnLedgerlessEnchantedItemIsRefused() {
        ItemStack item = legacyItem();
        ItemStack gem = augments.gemItem().create(Enchantment.VANISHING_CURSE, 1);
        int amount = gem.getAmount();

        assertFalse(augments.attach(player, item, gem));
        assertFalse(AugmentLedger.hasLedger(item));
        assertTrue(item.containsEnchantment(Enchantment.EFFICIENCY));
        assertEquals(amount, gem.getAmount());
        MessageAssert.assertMessageSent(player, "Use /augment while holding it to unlock one.");
    }

    @Test
    void openHeldOnLedgerlessEnchantedItemSendsPromptInsteadOfOpeningHub() {
        ItemStack item = legacyItem();
        player.getInventory().setItemInMainHand(item);
        new AugmentDialog(augments).openHeld(player);

        assertTrue(augments.pendingConfirmations().contains(player.getUniqueId()));
        assertFalse(AugmentLedger.hasLedger(item));
        MessageAssert.assertMessageSent(player, "Unlock augment slot 1 for 1 levels?");
    }

    @Test
    void openHeldOnLedgerlessPlainItemOpensHubFreely() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        player.getInventory().setItemInMainHand(item);

        new AugmentDialog(augments).openHeld(player);

        assertFalse(AugmentLedger.hasLedger(item));
        assertFalse(augments.pendingConfirmations().contains(player.getUniqueId()));
    }

    @Test
    void openHeldOnAlreadyMigratedLedgerItemOpensHubDirectly() {
        ItemStack item = legacyItem();
        augments.ledger().write(item, 1,
                List.of(new me.beeliebub.tweaks.tools.augments.AugmentEntry(
                        Enchantment.EFFICIENCY.getKey(), 5, true)), true);
        player.getInventory().setItemInMainHand(item);

        new AugmentDialog(augments).openHeld(player);

        assertFalse(augments.pendingConfirmations().contains(player.getUniqueId()));
        assertTrue(augments.ledger().migrated(player.getInventory().getItemInMainHand()));
    }

    @Test
    void openHeldOnUnpricedLegacyItemRefusesWithoutMutation() {
        plugin.getConfig().set("tools.augments.slot-prices", Map.of());
        ItemStack item = legacyItem();
        player.getInventory().setItemInMainHand(item);

        new AugmentDialog(augments).openHeld(player);

        assertFalse(AugmentLedger.hasLedger(item));
        assertTrue(item.containsEnchantment(Enchantment.EFFICIENCY));
        MessageAssert.assertMessageSent(player, "not currently priced");
    }

    private ItemStack legacyItem() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        return item;
    }
}
