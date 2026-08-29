package me.beeliebub.tweaks.tests.worldmanagement;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.worldmanagement.WorldRuleListener;
import io.papermc.paper.event.player.PlayerTradeEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillagerTradeListenerTest {

    private ServerMock server;
    private Tweaks plugin;
    private WorldRuleListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        listener = new WorldRuleListener(plugin, mock(ProtectionManager.class));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ItemStack loreItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(Component.text("...the Wanderer's Path...")));
        item.setItemMeta(meta);
        return item;
    }

    // A joining PlayerMock is sent login tips by HelpSystem; clear them so message
    // assertions only see what onVillagerInteract produced.
    private void drainMessages(PlayerMock player) {
        while (player.nextMessage() != null) {
            // discard
        }
    }

    private PlayerInteractEntityEvent interactEvent(PlayerMock player, Entity target, EquipmentSlot hand) {
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(event.getRightClicked()).thenReturn(target);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(hand);
        return event;
    }

    @Test
    void blocksVillagerInteractWhenCarryingLoreEmerald() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(loreItem(Material.EMERALD));
        drainMessages(player);

        PlayerInteractEntityEvent event = interactEvent(player, mock(Villager.class), EquipmentSlot.HAND);
        listener.onVillagerInteract(event);

        verify(event).setCancelled(true);
        assertNotNull(player.nextMessage());
    }

    @Test
    void blocksWhenCarryingLoreEmeraldBlock() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(loreItem(Material.EMERALD_BLOCK));

        PlayerInteractEntityEvent event = interactEvent(player, mock(Villager.class), EquipmentSlot.HAND);
        listener.onVillagerInteract(event);

        verify(event).setCancelled(true);
    }

    @Test
    void blocksWhenLoreEmeraldInOffHand() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInOffHand(loreItem(Material.EMERALD));

        PlayerInteractEntityEvent event = interactEvent(player, mock(Villager.class), EquipmentSlot.HAND);
        listener.onVillagerInteract(event);

        verify(event).setCancelled(true);
    }

    @Test
    void offHandInteractIsCancelledButNotMessaged() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(loreItem(Material.EMERALD));
        drainMessages(player);

        PlayerInteractEntityEvent event = interactEvent(player, mock(Villager.class), EquipmentSlot.OFF_HAND);
        listener.onVillagerInteract(event);

        verify(event).setCancelled(true);
        assertNull(player.nextMessage());
    }

    @Test
    void allowsVillagerInteractWithPlainEmerald() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.EMERALD));

        PlayerInteractEntityEvent event = interactEvent(player, mock(Villager.class), EquipmentSlot.HAND);
        listener.onVillagerInteract(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void allowsVillagerInteractWithEmptyInventory() {
        PlayerMock player = server.addPlayer();

        PlayerInteractEntityEvent event = interactEvent(player, mock(Villager.class), EquipmentSlot.HAND);
        listener.onVillagerInteract(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void allowsWanderingTraderInteractWhileCarryingLoreEmerald() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(loreItem(Material.EMERALD));

        PlayerInteractEntityEvent event = interactEvent(player, mock(WanderingTrader.class), EquipmentSlot.HAND);
        listener.onVillagerInteract(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void allowsNameTagInteractWhileCarryingLoreEmerald() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.NAME_TAG));
        player.getInventory().setItem(1, loreItem(Material.EMERALD));

        PlayerInteractEntityEvent event = interactEvent(player, mock(Villager.class), EquipmentSlot.HAND);
        listener.onVillagerInteract(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void onVillagerTradeSuppressesXpByDefault() {
        PlayerTradeEvent event = mock(PlayerTradeEvent.class);

        listener.onVillagerTrade(event);

        verify(event).setRewardExp(false);
    }

    @Test
    void onVillagerTradeLeavesXpAloneWhenDisabled() {
        plugin.getConfig().set("worldmanagement.villager-trade-xp-disabled", false);
        PlayerTradeEvent event = mock(PlayerTradeEvent.class);

        listener.onVillagerTrade(event);

        verify(event, never()).setRewardExp(anyBoolean());
    }
}
