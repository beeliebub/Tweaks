package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.AugmentCraftListener;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AugmentCraftListenerTest {

    private ServerMock server;
    private Tweaks plugin;
    private AugmentService augments;
    private AugmentCraftListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        augments = new AugmentService(plugin, null);
        listener = new AugmentCraftListener(plugin, augments);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void craftedPlainDamageableGetsAnEmptyMigratedLedger() {
        PlayerMock player = server.addPlayer("CraftedTool");
        Recipe recipe = recipe(Material.DIAMOND_PICKAXE);
        CraftItemEvent event = craftEvent(player, recipe);

        listener.onCraft(event);
        ItemStack crafted = new ItemStack(Material.DIAMOND_PICKAXE);
        player.setItemOnCursor(crafted);
        server.getScheduler().performOneTick();

        ItemStack delivered = player.getItemOnCursor();
        assertTrue(AugmentLedger.hasLedger(delivered), "delivered=" + delivered + " meta="
                + (delivered == null ? null : delivered.getItemMeta()));
        assertTrue(augments.ledger().migrated(delivered));
        assertTrue(augments.entries(delivered).isEmpty());
        assertEquals(1, augments.ledger().slots(delivered));
    }

    @Test
    void craftedLiveEnchantmentsArePreservedAndRecorded() {
        PlayerMock player = server.addPlayer("CraftedEnchantment");
        ItemStack result = enchanted(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 5);
        Recipe recipe = recipe(result);
        CraftItemEvent event = craftEvent(player, recipe);

        listener.onCraft(event);
        player.setItemOnCursor(result);
        server.getScheduler().performOneTick();

        ItemStack delivered = player.getItemOnCursor();
        assertEquals(5, delivered.getEnchantmentLevel(Enchantment.EFFICIENCY));
        assertEquals(1, augments.entries(delivered).size(), "delivered=" + delivered + " meta="
                + delivered.getItemMeta());
        assertEquals(Enchantment.EFFICIENCY.getKey(), augments.entries(delivered).getFirst().enchantmentKey());
        assertEquals(0, augments.ledger().slots(delivered));
    }

    @Test
    void preExistingSameMaterialIsNotStampedWhenASeparateResultArrives() {
        PlayerMock player = server.addPlayer("CraftedExisting");
        ItemStack preExisting = new ItemStack(Material.IRON_PICKAXE);
        player.getInventory().setItem(0, preExisting);
        CraftItemEvent event = craftEvent(player, recipe(Material.IRON_PICKAXE));

        listener.onCraft(event);
        ItemStack crafted = new ItemStack(Material.IRON_PICKAXE);
        player.getInventory().setItem(1, crafted);
        server.getScheduler().performOneTick();

        assertFalse(AugmentLedger.hasLedger(preExisting));
        assertTrue(AugmentLedger.hasLedger(player.getInventory().getItem(1)));
    }

    @Test
    void existingLedgerIsIdempotentAndDoesNotRestamp() {
        AtomicInteger stamps = new AtomicInteger();
        augments = new AugmentService(plugin, null, item -> stamps.incrementAndGet());
        listener = new AugmentCraftListener(plugin, augments);
        PlayerMock player = server.addPlayer("CraftedIdempotent");
        ItemStack crafted = new ItemStack(Material.DIAMOND_SWORD);
        augments.ledger().write(crafted, 1, java.util.List.of(), true);
        CraftItemEvent event = craftEvent(player, recipe(Material.DIAMOND_SWORD));

        listener.onCraft(event);
        player.setItemOnCursor(crafted);
        server.getScheduler().performOneTick();

        assertEquals(0, stamps.get());
        assertTrue(AugmentLedger.hasLedger(player.getItemOnCursor()));
    }

    @Test
    void smithingResultUsesTheSameDeferredInitialization() {
        PlayerMock player = server.addPlayer("SmithingTool");
        SmithingInventory inventory = (SmithingInventory) Bukkit.createInventory(null, InventoryType.SMITHING);
        ItemStack result = enchanted(Material.NETHERITE_PICKAXE, Enchantment.UNBREAKING, 3);
        inventory.setResult(result);
        SmithItemEvent event = mock(SmithItemEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);

        listener.onSmith(event);
        player.setItemOnCursor(result);
        server.getScheduler().performOneTick();

        ItemStack delivered = player.getItemOnCursor();
        assertTrue(AugmentLedger.hasLedger(delivered), "delivered=" + delivered + " meta="
                + (delivered == null ? null : delivered.getItemMeta()));
        assertEquals(3, delivered.getEnchantmentLevel(Enchantment.UNBREAKING));
    }

    @Test
    void tradeResultGetsAFreeSlotWhenUnenchanted() {
        PlayerMock player = server.addPlayer("TradePlainTool");
        ItemStack result = new ItemStack(Material.DIAMOND_PICKAXE);
        listener.onTrade(tradeEvent(player, result));
        player.setItemOnCursor(result);
        server.getScheduler().performOneTick();

        assertEquals(1, augments.ledger().slots(player.getItemOnCursor()));
    }

    @Test
    void tradeResultKeepsZeroSlotsWhenEnchanted() {
        PlayerMock player = server.addPlayer("TradeEnchantedTool");
        ItemStack result = enchanted(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 5);
        listener.onTrade(tradeEvent(player, result));
        player.setItemOnCursor(result);
        server.getScheduler().performOneTick();

        assertEquals(0, augments.ledger().slots(player.getItemOnCursor()));
        assertEquals(5, player.getItemOnCursor().getEnchantmentLevel(Enchantment.EFFICIENCY));
    }

    @Test
    void netheriteUpgradeCarriesOverLedgerWithoutGrantingAFreeSlot() {
        PlayerMock player = server.addPlayer("SmithingLedger");
        SmithingInventory inventory = (SmithingInventory) Bukkit.createInventory(null, InventoryType.SMITHING);
        ItemStack result = new ItemStack(Material.NETHERITE_PICKAXE);
        augments.ledger().write(result, 4, java.util.List.of(), true);
        inventory.setResult(result);
        SmithItemEvent event = mock(SmithItemEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);

        listener.onSmith(event);
        player.setItemOnCursor(result);
        server.getScheduler().performOneTick();

        assertEquals(4, augments.ledger().slots(player.getItemOnCursor()));
    }

    private static CraftItemEvent craftEvent(PlayerMock player, Recipe recipe) {
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRecipe()).thenReturn(recipe);
        return event;
    }

    private static Recipe recipe(Material material) {
        return recipe(new ItemStack(material));
    }

    private static Recipe recipe(ItemStack result) {
        Recipe recipe = mock(Recipe.class);
        when(recipe.getResult()).thenReturn(result);
        return recipe;
    }

    private static PlayerTradeEvent tradeEvent(PlayerMock player, ItemStack result) {
        return new PlayerTradeEvent(player, mock(AbstractVillager.class),
                new MerchantRecipe(result, 1), false, false);
    }

    private static ItemStack enchanted(Material material, Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(material);
        item.addUnsafeEnchantment(enchantment, level);
        return item;
    }
}
