package me.beeliebub.tweaks.tests.tools.xp;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.BalanceMutationResult;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.tools.xp.MendingPayoutListener;
import me.beeliebub.tweaks.tools.xp.XpSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MendingPayoutListenerTest {

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
    void mendIsCancelledWhenPayoutIsEnabled() {
        XpSettings settings = new XpSettings(plugin);
        MendingPayoutListener listener = new MendingPayoutListener(plugin,
                Mockito.mock(EconomyManager.class), settings);
        PlayerItemMendEvent event = Mockito.mock(PlayerItemMendEvent.class);

        listener.onItemMend(event);

        verify(event).setCancelled(true);
    }

    @Test
    void mendRemainsVanillaWhenPayoutIsDisabled() {
        plugin.getConfig().set("xp.mending.enabled", false);
        XpSettings settings = new XpSettings(plugin);
        MendingPayoutListener listener = new MendingPayoutListener(plugin,
                Mockito.mock(EconomyManager.class), settings);
        PlayerItemMendEvent event = Mockito.mock(PlayerItemMendEvent.class);

        listener.onItemMend(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void payoutHandlesEmptyArmorSlotsAndAUnilateralMendingHelmet() {
        ItemStack mendingTool = mendingTool();
        ItemStack helmet = new ItemStack(Material.IRON_HELMET);
        helmet.addUnsafeEnchantment(Enchantment.MENDING, 1);

        assertPayout(mendingTool, new ItemStack[]{null, null, null, null});
        assertPayout(null, new ItemStack[]{null, null, null, helmet});
    }

    @Test
    void economyRejectionLeavesTheXpAmountUntouched() {
        EconomyManager economy = Mockito.mock(EconomyManager.class);
        UUID id = UUID.randomUUID();
        Player player = player(id, mendingTool(), new ItemStack[]{null, null, null, null});
        when(economy.addBalance(id, 5L)).thenReturn(BalanceMutationResult.REJECTED_UNREPRESENTABLE);
        MendingPayoutListener listener = new MendingPayoutListener(plugin, economy, new XpSettings(plugin));
        PlayerExpChangeEvent event = new PlayerExpChangeEvent(player, 5);

        listener.onExpChange(event);

        assertEquals(5, event.getAmount());
        verify(economy).addBalance(id, 5L);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void unexpectedPayoutFailureLeavesTheXpAmountUntouched() {
        EconomyManager economy = Mockito.mock(EconomyManager.class);
        UUID id = UUID.randomUUID();
        Player player = player(id, mendingTool(), new ItemStack[]{null, null, null, null});
        when(economy.addBalance(id, 5L)).thenThrow(new IllegalStateException("test failure"));
        MendingPayoutListener listener = new MendingPayoutListener(plugin, economy, new XpSettings(plugin));
        PlayerExpChangeEvent event = new PlayerExpChangeEvent(player, 5);

        assertDoesNotThrow(() -> listener.onExpChange(event));

        assertEquals(5, event.getAmount());
    }

    private void assertPayout(ItemStack mainHand, ItemStack[] armor) {
        EconomyManager economy = Mockito.mock(EconomyManager.class);
        UUID id = UUID.randomUUID();
        Player player = player(id, mainHand, armor);
        when(economy.addBalance(id, 5L)).thenReturn(BalanceMutationResult.APPLIED);
        MendingPayoutListener listener = new MendingPayoutListener(plugin, economy, new XpSettings(plugin));
        PlayerExpChangeEvent event = new PlayerExpChangeEvent(player, 5);

        assertDoesNotThrow(() -> listener.onExpChange(event));

        assertEquals(0, event.getAmount());
        verify(economy).addBalance(id, 5L);
        verify(player).sendMessage(any(Component.class));
    }

    private static Player player(UUID id, ItemStack mainHand, ItemStack[] armor) {
        Player player = Mockito.mock(Player.class);
        PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(mainHand);
        when(inventory.getItemInOffHand()).thenReturn(null);
        when(inventory.getArmorContents()).thenReturn(armor);
        return player;
    }

    private static ItemStack mendingTool() {
        ItemStack tool = new ItemStack(Material.IRON_PICKAXE);
        tool.addUnsafeEnchantment(Enchantment.MENDING, 1);
        return tool;
    }
}
