package me.beeliebub.tweaks.tests.enchantments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.DisenchantingBundle;
import me.beeliebub.tweaks.enchantments.EggCollector;
import me.beeliebub.tweaks.enchantments.SpawnerPickup;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DisenchantingBundleTest {

    private Tweaks plugin;
    private QualityRegistry qualityRegistry;
    private SpawnerPickup spawnerPickup;
    private EggCollector eggCollector;
    private Enchantment spawnerPickupEnch;
    private Enchantment eggCollectorEnch;
    private DisenchantingBundle bundle;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = mock(Tweaks.class);
        qualityRegistry = mock(QualityRegistry.class);
        spawnerPickup = mock(SpawnerPickup.class);
        eggCollector = mock(EggCollector.class);
        spawnerPickupEnch = mock(Enchantment.class);
        eggCollectorEnch = mock(Enchantment.class);
        when(spawnerPickup.getEnchantment()).thenReturn(spawnerPickupEnch);
        when(eggCollector.getEnchantment()).thenReturn(eggCollectorEnch);
        bundle = new DisenchantingBundle(plugin, qualityRegistry, spawnerPickup, eggCollector);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void nonRightClickIsIgnored() {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        bundle.onInventoryClick(event);
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void nonPlayerClickerIsIgnored() {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getClick()).thenReturn(ClickType.RIGHT);
        when(event.getWhoClicked()).thenReturn(mock(org.bukkit.entity.HumanEntity.class));
        bundle.onInventoryClick(event);
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void spawnerPickupToolIsRefused_cursorBundleClickedItem() {
        Player player = mock(Player.class);
        ItemStack bundleStack = bundleWithLore();
        ItemStack tool = enchantedTool(spawnerPickupEnch);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getClick()).thenReturn(ClickType.RIGHT);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getCurrentItem()).thenReturn(tool);
        when(event.getCursor()).thenReturn(bundleStack);

        bundle.onInventoryClick(event);

        verify(event).setCancelled(true);
        // Tool keeps its enchantment — bundle did not strip
        assertTrue(tool.containsEnchantment(spawnerPickupEnch));
        // Player got the rejection feedback
        verify(player).sendMessage(any(Component.class));
        verify(player).playSound((org.bukkit.Location) any(), eq(Sound.ENTITY_VILLAGER_NO), anyFloat(), anyFloat());
    }

    @Test
    void eggCollectorToolIsRefused_cursorToolClickedBundle() {
        Player player = mock(Player.class);
        ItemStack bundleStack = bundleWithLore();
        ItemStack tool = enchantedTool(eggCollectorEnch);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getClick()).thenReturn(ClickType.RIGHT);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getCurrentItem()).thenReturn(bundleStack);
        when(event.getCursor()).thenReturn(tool);

        bundle.onInventoryClick(event);

        verify(event).setCancelled(true);
        assertTrue(tool.containsEnchantment(eggCollectorEnch));
        verify(player).sendMessage(any(Component.class));
        verify(player).playSound((org.bukkit.Location) any(), eq(Sound.ENTITY_VILLAGER_NO), anyFloat(), anyFloat());
    }

    @Test
    void unrelatedEnchantedToolIsNotRefused() {
        Player player = mock(Player.class);
        org.bukkit.inventory.PlayerInventory inv = mock(org.bukkit.inventory.PlayerInventory.class);
        org.bukkit.World world = mock(org.bukkit.World.class);
        when(player.getInventory()).thenReturn(inv);
        when(player.getWorld()).thenReturn(world);
        when(inv.addItem(any())).thenReturn(new java.util.HashMap<>());

        ItemStack bundleStack = bundleWithLore();
        when(bundleStack.getAmount()).thenReturn(1);
        Enchantment unrelated = mock(Enchantment.class);
        ItemStack tool = enchantedTool(unrelated);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getClick()).thenReturn(ClickType.RIGHT);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getCurrentItem()).thenReturn(tool);
        when(event.getCursor()).thenReturn(bundleStack);

        bundle.onInventoryClick(event);

        // Refusal sound should NOT have fired (operation proceeded into processDisenchant).
        verify(player, never()).playSound((org.bukkit.Location) any(), eq(Sound.ENTITY_VILLAGER_NO), anyFloat(), anyFloat());
    }

    private static ItemStack bundleWithLore() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(stack.getType()).thenReturn(Material.BUNDLE);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.hasLore()).thenReturn(true);
        return stack;
    }

    private static ItemStack enchantedTool(Enchantment enchantment) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.DIAMOND_PICKAXE);
        when(stack.getEnchantments()).thenReturn(Map.of(enchantment, 1));
        when(stack.containsEnchantment(enchantment)).thenReturn(true);
        return stack;
    }
}