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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairKitListenerTest {

    private ServerMock server;
    private Tweaks plugin;
    private RepairKitListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        DurabilityService durability = new DurabilityService(plugin);
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
}
