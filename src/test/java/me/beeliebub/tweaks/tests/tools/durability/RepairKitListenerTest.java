package me.beeliebub.tweaks.tests.tools.durability;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tests.MessageAssert;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import me.beeliebub.tweaks.tools.durability.DurabilityService;
import me.beeliebub.tweaks.tools.durability.RepairKitItem;
import me.beeliebub.tweaks.tools.durability.RepairKitListener;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

class RepairKitListenerTest {

    private ServerMock server;
    private Tweaks plugin;
    private RepairKitListener listener;
    private DurabilityService durability;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        durability = new DurabilityService(plugin);
        listener = new RepairKitListener(new RepairKitItem(plugin), durability);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void targetsHidePlainDamageableItems() {
        PlayerMock player = server.addPlayer("KitTargets");
        player.getInventory().setItem(1, new ItemStack(Material.IRON_PICKAXE));

        assertTrue(listener.targets(player).isEmpty());
    }

    @Test
    void applyingAKitReportsRepairCountAndConsumesOnlyAfterSuccess() {
        PlayerMock player = server.addPlayer("KitApply");
        ItemStack target = new ItemStack(Material.IRON_PICKAXE);
        new AugmentLedger(plugin).write(target, 0, java.util.List.of(), true);
        durability.ensureStamped(target);
        setDamage(target, durability.maxDamage(target) - 2);
        player.getInventory().setItem(1, target);
        player.getInventory().setItemInMainHand(new RepairKitItem(plugin).create(1));

        assertTrue(listener.apply(player, 1));
        assertTrue(player.getInventory().getItemInMainHand() == null
                || player.getInventory().getItemInMainHand().isEmpty());
        MessageAssert.assertMessageSent(player, "Repair kit applied! Repair 1/9");
    }

    @Test
    void staleDialogTargetIsRejectedWhenItBecomesPlain() {
        PlayerMock player = server.addPlayer("KitStale");
        ItemStack augmented = new ItemStack(Material.IRON_PICKAXE);
        new AugmentLedger(plugin).write(augmented, 0, java.util.List.of(), true);
        player.getInventory().setItem(1, augmented);
        ItemStack expected = augmented.clone();
        player.getInventory().setItemInMainHand(new RepairKitItem(plugin).create(1));
        player.getInventory().setItem(1, new ItemStack(Material.IRON_PICKAXE));

        assertFalse(listener.apply(player, 1, expected));
        assertTrue(player.getInventory().getItemInMainHand().getAmount() == 1);
        MessageAssert.assertMessageSent(player, "Repair kits only work on augmented items.");
    }

    @Test
    void staleDialogTargetReportsTheChangeWithoutConsumingAKit() {
        PlayerMock player = server.addPlayer("KitChanged");
        ItemStack target = new ItemStack(Material.IRON_PICKAXE);
        new AugmentLedger(plugin).write(target, 0, java.util.List.of(), true);
        durability.ensureStamped(target);
        setDamage(target, durability.maxDamage(target) - 2);
        ItemStack expected = target.clone();
        player.getInventory().setItem(1, target);
        player.getInventory().setItemInMainHand(new RepairKitItem(plugin).create(1));
        ItemStack liveTarget = player.getInventory().getItem(1);
        setDamage(liveTarget, durability.maxDamage(liveTarget) - 3);

        assertFalse(listener.apply(player, 1, expected));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        MessageAssert.assertMessageSent(player, "That repair target changed");
    }

    @Test
    void fullDurabilityTargetsAreHiddenAndRejectedWithoutConsumingAKit() {
        PlayerMock player = server.addPlayer("KitFull");
        ItemStack target = new ItemStack(Material.IRON_PICKAXE);
        new AugmentLedger(plugin).write(target, 0, java.util.List.of(), true);
        durability.ensureStamped(target);
        player.getInventory().setItem(1, target);

        assertTrue(listener.targets(player).isEmpty());
        player.getInventory().setItemInMainHand(new RepairKitItem(plugin).create(1));
        assertFalse(listener.apply(player, 1, target.clone()));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        MessageAssert.assertMessageSent(player, "already at full durability");
    }

    @Test
    void targetThatBecomesFullAfterDialogBuildReportsTheFullMessageFirst() {
        PlayerMock player = server.addPlayer("KitRace");
        ItemStack target = new ItemStack(Material.IRON_PICKAXE);
        new AugmentLedger(plugin).write(target, 0, java.util.List.of(), true);
        durability.ensureStamped(target);
        setDamage(target, durability.maxDamage(target) - 2);
        ItemStack expected = target.clone();
        player.getInventory().setItem(1, target);
        player.getInventory().setItemInMainHand(new RepairKitItem(plugin).create(1));
        setDamage(player.getInventory().getItem(1), 0);

        assertFalse(listener.apply(player, 1, expected));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        MessageAssert.assertMessageSent(player, "already at full durability");
    }

    @Test
    void refusedRepairReportsAReasonAndDoesNotConsumeAKit() {
        PlayerMock player = server.addPlayer("KitRefused");
        ItemStack target = new ItemStack(Material.IRON_PICKAXE);
        new AugmentLedger(plugin).write(target, 0, java.util.List.of(), true);
        DurabilityService spy = Mockito.spy(new DurabilityService(plugin));
        spy.ensureStamped(target);
        setDamage(target, spy.maxDamage(target) - 2);
        doReturn(false).when(spy).repair(any(ItemStack.class));
        RepairKitListener refused = new RepairKitListener(new RepairKitItem(plugin), spy);
        player.getInventory().setItem(1, target);
        player.getInventory().setItemInMainHand(new RepairKitItem(plugin).create(1));

        assertFalse(refused.apply(player, 1));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        MessageAssert.assertMessageSent(player, "could not be repaired");
    }

    private static void setDamage(ItemStack item, int damage) {
        int max = item.getData(io.papermc.paper.datacomponent.DataComponentTypes.MAX_DAMAGE) == null
                ? item.getType().getMaxDurability()
                : item.getData(io.papermc.paper.datacomponent.DataComponentTypes.MAX_DAMAGE);
        item.setData(io.papermc.paper.datacomponent.DataComponentTypes.MAX_DAMAGE, max);
        org.bukkit.inventory.meta.Damageable meta = (org.bukkit.inventory.meta.Damageable) item.getItemMeta();
        meta.setDamage(damage);
        item.setItemMeta(meta);
        item.setData(io.papermc.paper.datacomponent.DataComponentTypes.MAX_DAMAGE, max);
        item.setData(io.papermc.paper.datacomponent.DataComponentTypes.DAMAGE, damage);
    }
}
