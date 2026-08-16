package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import me.beeliebub.tweaks.tools.augments.AugmentGemItem;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AugmentCurseRiderTest {

    private ServerMock server;
    private Tweaks plugin;
    private AugmentService augments;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        augments = new AugmentService(plugin, null);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void batchBindsEachCurseToOneNonCurseGem() {
        List<ItemStack> result = augments.createGemBatch(Map.of(
                Enchantment.EFFICIENCY, 5,
                Enchantment.UNBREAKING, 3,
                Enchantment.VANISHING_CURSE, 1), new Random(7));

        assertEquals(2, result.size());
        assertEquals(1, result.stream().map(augments.gemItem()::read)
                .mapToInt(data -> data.curses().size()).sum());
    }

    @Test
    void curseOnlyBatchHasOneCursePrimaryGem() {
        AugmentGemItem.GemData data = augments.gemItem()
                .read(augments.createGemBatch(Map.of(Enchantment.VANISHING_CURSE, 1), new Random(1)).getFirst());

        assertEquals(Enchantment.VANISHING_CURSE, data.enchantment());
        assertTrue(data.curses().isEmpty());
    }

    @Test
    void attachingRiderCostsOneSlotAndToggleLeavesCurseActive() {
        PlayerMock player = server.addPlayer("CurseTester");
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 1, List.of(), true);
        ItemStack gem = augments.gemItem().create(Enchantment.EFFICIENCY, 5,
                List.of(new AugmentGemItem.CurseRider(Enchantment.VANISHING_CURSE, 1)));

        assertTrue(augments.attach(player, item, gem));
        assertEquals(1, augments.slotCalculator().used(augments.entries(item)));
        assertEquals(1, augments.ledger().curses(item).size());
        assertTrue(item.containsEnchantment(Enchantment.VANISHING_CURSE));

        assertFalse(augments.attach(player, item,
                augments.gemItem().create(Enchantment.UNBREAKING, 3,
                        List.of(new AugmentGemItem.CurseRider(Enchantment.VANISHING_CURSE, 1)))));
        assertTrue(augments.toggle(player, item, 0));
        assertFalse(item.containsEnchantment(Enchantment.EFFICIENCY));
        assertTrue(item.containsEnchantment(Enchantment.VANISHING_CURSE));
    }

    @Test
    void curseOnlyGemAttachesToAnyItemWithoutApatchedEntry() {
        PlayerMock player = server.addPlayer("CurseOnlyTester");
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemStack gem = augments.gemItem().create(Enchantment.BINDING_CURSE, 1);

        assertTrue(augments.compatibleForDisplay(item, augments.gemItem().read(gem), List.of()));
        assertTrue(augments.attach(player, item, gem));
        assertTrue(augments.entries(item).isEmpty());
        assertEquals(1, augments.ledger().curses(item).size());
        assertTrue(augments.ledger().migrated(item));
        assertTrue(item.containsEnchantment(Enchantment.BINDING_CURSE));
    }

    @Test
    void directVanillaCurseCannotBeReboundAsASecondRider() {
        PlayerMock player = server.addPlayer("DirectCurseTester");
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 1, List.of(), true);
        item.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);
        ItemStack gem = augments.gemItem().create(Enchantment.EFFICIENCY, 5,
                List.of(new AugmentGemItem.CurseRider(Enchantment.BINDING_CURSE, 1)));

        assertFalse(augments.attach(player, item, gem));
        assertTrue(augments.entries(item).isEmpty());
    }
}
