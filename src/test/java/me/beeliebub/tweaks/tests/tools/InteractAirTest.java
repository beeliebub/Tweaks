package me.beeliebub.tweaks.tests.tools;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.AugmentDialog;
import me.beeliebub.tweaks.tools.augments.AugmentGemItem;
import me.beeliebub.tweaks.tools.augments.AugmentGemListener;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import me.beeliebub.tweaks.tools.durability.DurabilityListener;
import me.beeliebub.tweaks.tools.durability.DurabilityService;
import me.beeliebub.tweaks.tools.durability.RepairKitItem;
import me.beeliebub.tweaks.tools.durability.RepairKitListener;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InteractAirTest {

    private ServerMock server;
    private Tweaks plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        player = server.addPlayer("AirTester");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void gemRightClickAirIsHandledEvenWhenTheEventStartsCancelled() {
        AugmentGemItem gems = new AugmentGemItem(plugin);
        ItemStack gem = gems.create(Enchantment.EFFICIENCY, 5);
        AugmentGemListener listener = new AugmentGemListener(gems,
                new AugmentDialog(new AugmentService(plugin, null)));
        PlayerInteractEvent event = airEvent(gem);

        listener.onInteract(event);

        verify(event).setCancelled(true);
    }

    @Test
    void repairKitRightClickAirIsHandled() {
        RepairKitItem kits = new RepairKitItem(plugin);
        RepairKitListener listener = new RepairKitListener(kits, new DurabilityService(plugin));
        PlayerInteractEvent event = airEvent(kits.create(1));

        listener.onInteract(event);

        verify(event).setCancelled(true);
    }

    @Test
    void durabilityRightClickAirStillBlocksASpentAugmentedItem() {
        DurabilityService durability = new DurabilityService(plugin);
        ItemStack item = new ItemStack(Material.IRON_PICKAXE);
        new AugmentLedger(plugin).write(item, 0, List.of(), true);
        durability.ensureStamped(item);
        item.setData(DataComponentTypes.MAX_DAMAGE, durability.maxDamage(item));
        Damageable meta = (Damageable) item.getItemMeta();
        meta.setDamage(durability.maxDamage(item) - 1);
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.MAX_DAMAGE, durability.maxDamage(item));
        item.setData(DataComponentTypes.DAMAGE, durability.maxDamage(item) - 1);
        DurabilityListener listener = new DurabilityListener(plugin, durability);
        PlayerInteractEvent event = airEvent(item);

        listener.onInteract(event);

        verify(event).setCancelled(true);
    }

    @Test
    void explicitItemUseDenyStillLetsTheOwningListenerYield() {
        AugmentGemItem gems = new AugmentGemItem(plugin);
        AugmentGemListener listener = new AugmentGemListener(gems,
                new AugmentDialog(new AugmentService(plugin, null)));
        PlayerInteractEvent event = airEvent(gems.create(Enchantment.EFFICIENCY, 5));
        when(event.useItemInHand()).thenReturn(Event.Result.DENY);

        listener.onInteract(event);

        verify(event, never()).setCancelled(true);
    }

    private PlayerInteractEvent airEvent(ItemStack item) {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getItem()).thenReturn(item);
        return event;
    }
}
