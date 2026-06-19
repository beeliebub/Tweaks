package me.beeliebub.tweaks.tests;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.recipes.ResourceRupee;
import me.beeliebub.tweaks.utils.InventoryUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

// Tests for the Resource Rupee currency helpers in InventoryUtil:
// getResourceRupeeBalance, deductResourceRupees, addResourceRupees.
class ResourceRupeeInventoryTest {

    private ServerMock server;
    private PlayerMock player;
    private final ResourceRupee rupee = new ResourceRupee();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(Tweaks.class);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ---------------------------------------------------------------------------
    // getResourceRupeeBalance
    // ---------------------------------------------------------------------------

    @Test
    void balanceIsZeroWithEmptyInventory() {
        assertEquals(0, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void balanceCountsLooseRupees() {
        player.getInventory().addItem(rupee.createRupee(7));
        assertEquals(7, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void balanceCountsRupeeBlocksAsNine() {
        player.getInventory().addItem(rupee.createRupeeBlock(3));
        assertEquals(27, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void balanceSumsLooseAndBlocks() {
        player.getInventory().addItem(rupee.createRupeeBlock(2));
        player.getInventory().addItem(rupee.createRupee(5));
        // 2 blocks = 18, plus 5 loose = 23
        assertEquals(23, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void balanceIgnoresPlainEmeraldAndEmeraldBlock() {
        player.getInventory().addItem(new ItemStack(Material.EMERALD, 10));
        player.getInventory().addItem(new ItemStack(Material.EMERALD_BLOCK, 2));
        assertEquals(0, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void balanceIgnoresNullSlotsGracefully() {
        // Give a rupee then clear most slots, leaving nulls around it.
        player.getInventory().clear();
        player.getInventory().setItem(15, rupee.createRupee(3));
        assertEquals(3, InventoryUtil.getResourceRupeeBalance(player));
    }

    // ---------------------------------------------------------------------------
    // deductResourceRupees
    // ---------------------------------------------------------------------------

    @Test
    void deductZeroIsNoOpReturnsTrue() {
        player.getInventory().addItem(rupee.createRupee(5));
        assertTrue(InventoryUtil.deductResourceRupees(player, 0));
        assertEquals(5, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void deductNegativeReturnsFalseWithoutMutation() {
        player.getInventory().addItem(rupee.createRupee(5));
        assertFalse(InventoryUtil.deductResourceRupees(player, -1));
        assertEquals(5, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void deductReturnsFalseWhenInsufficientFunds() {
        player.getInventory().addItem(rupee.createRupee(3));
        assertFalse(InventoryUtil.deductResourceRupees(player, 5));
        // Inventory must not be mutated.
        assertEquals(3, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void deductExactLooseRupeesLeavesZero() {
        player.getInventory().addItem(rupee.createRupee(8));
        assertTrue(InventoryUtil.deductResourceRupees(player, 8));
        assertEquals(0, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void deductPartialLooseRupeesLeavesRemainder() {
        player.getInventory().addItem(rupee.createRupee(10));
        assertTrue(InventoryUtil.deductResourceRupees(player, 6));
        assertEquals(4, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void deductConsumesLooseBeforeBlocks() {
        // 1 block (9) + 3 loose = 12. Deduct 3 — should consume the loose stack, not touch block.
        player.getInventory().addItem(rupee.createRupeeBlock(1));
        player.getInventory().addItem(rupee.createRupee(3));
        assertTrue(InventoryUtil.deductResourceRupees(player, 3));
        assertEquals(9, InventoryUtil.getResourceRupeeBalance(player));
        // The block should still be present.
        assertEquals(1, countMaterial(Material.EMERALD_BLOCK));
        // No loose rupees should remain.
        assertEquals(0, countMaterial(Material.EMERALD));
    }

    @Test
    void deductBreaksBlockWhenLooseIsInsufficient() {
        // 1 block (9) + 2 loose = 11. Deduct 5: consume 2 loose, then break 1 block to cover 3
        // more, minting 6 change.
        player.getInventory().addItem(rupee.createRupeeBlock(1));
        player.getInventory().addItem(rupee.createRupee(2));
        assertTrue(InventoryUtil.deductResourceRupees(player, 5));
        // Paid 2 loose + 1 block (9) - 6 change = net 5 deducted; should have 6 remaining.
        assertEquals(6, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void deductBreaksBlockAlignedExactly() {
        // 2 blocks = 18. Deduct 9 — should consume exactly one block, leaving one.
        player.getInventory().addItem(rupee.createRupeeBlock(2));
        assertTrue(InventoryUtil.deductResourceRupees(player, 9));
        assertEquals(9, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void deductEntireBalanceLeavesZero() {
        player.getInventory().addItem(rupee.createRupeeBlock(1));
        player.getInventory().addItem(rupee.createRupee(4));
        // Balance = 13.
        assertTrue(InventoryUtil.deductResourceRupees(player, 13));
        assertEquals(0, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void deductOnBlockOnlyWithNoLoose() {
        // 1 block only (9 units). Deduct 4: break block, mint 5 change.
        player.getInventory().addItem(rupee.createRupeeBlock(1));
        assertTrue(InventoryUtil.deductResourceRupees(player, 4));
        assertEquals(5, InventoryUtil.getResourceRupeeBalance(player));
    }

    // ---------------------------------------------------------------------------
    // addResourceRupees
    // ---------------------------------------------------------------------------

    @Test
    void addZeroIsNoOp() {
        InventoryUtil.addResourceRupees(player, 0);
        assertEquals(0, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void addNegativeIsNoOp() {
        InventoryUtil.addResourceRupees(player, -5);
        assertEquals(0, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void addLooseRupeesOnly() {
        // 7 < 9, so no blocks — only loose rupees.
        InventoryUtil.addResourceRupees(player, 7);
        assertEquals(7, InventoryUtil.getResourceRupeeBalance(player));
        assertEquals(0, countMaterial(Material.EMERALD_BLOCK));
    }

    @Test
    void addExactBlockMultiple() {
        // 18 = 2 blocks, no loose.
        InventoryUtil.addResourceRupees(player, 18);
        assertEquals(18, InventoryUtil.getResourceRupeeBalance(player));
        assertEquals(0, countMaterial(Material.EMERALD));
    }

    @Test
    void addMixedBlocksAndLoose() {
        // 20 = 2 blocks + 2 loose.
        InventoryUtil.addResourceRupees(player, 20);
        assertEquals(20, InventoryUtil.getResourceRupeeBalance(player));
    }

    @Test
    void addSingleUnit() {
        InventoryUtil.addResourceRupees(player, 1);
        assertEquals(1, InventoryUtil.getResourceRupeeBalance(player));
        assertEquals(0, countMaterial(Material.EMERALD_BLOCK));
    }

    @Test
    void addNineProducesOneBlock() {
        InventoryUtil.addResourceRupees(player, 9);
        assertEquals(9, InventoryUtil.getResourceRupeeBalance(player));
        assertEquals(1, countMaterial(Material.EMERALD_BLOCK));
        assertEquals(0, countMaterial(Material.EMERALD));
    }

    // ---------------------------------------------------------------------------
    // Round-trip: add then deduct
    // ---------------------------------------------------------------------------

    @Test
    void addThenDeductRoundTrips() {
        InventoryUtil.addResourceRupees(player, 25);
        assertEquals(25, InventoryUtil.getResourceRupeeBalance(player));
        assertTrue(InventoryUtil.deductResourceRupees(player, 10));
        assertEquals(15, InventoryUtil.getResourceRupeeBalance(player));
        assertTrue(InventoryUtil.deductResourceRupees(player, 15));
        assertEquals(0, InventoryUtil.getResourceRupeeBalance(player));
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private int countMaterial(Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) total += stack.getAmount();
        }
        return total;
    }
}
