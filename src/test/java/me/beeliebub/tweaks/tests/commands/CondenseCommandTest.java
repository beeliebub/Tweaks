package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.commands.CondenseCommand;
import me.beeliebub.tweaks.minigames.RewardManager;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CondenseCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private ResourceHunt resourceHunt;
    private CondenseCommand cmd;
    private PlayerMock player;
    private NamespacedKey countedKey;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        resourceHunt = new ResourceHunt(plugin, new RewardManager(plugin));
        cmd = new CondenseCommand(resourceHunt);
        player = server.addPlayer();
        countedKey = new NamespacedKey(plugin, "resource_hunt_counted");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void condensesHeldMaterialOnly() {
        player.getInventory().setItem(0, new ItemStack(Material.RAW_IRON, 9));
        player.getInventory().setItem(1, new ItemStack(Material.IRON_INGOT, 18));
        player.getInventory().setItemInMainHand(new ItemStack(Material.RAW_IRON, 9));

        cmd.onCommand(player, bukkitCmd, "condense", new String[0]);

        assertEquals(1, countMaterial(Material.RAW_IRON_BLOCK));
        assertEquals(0, countMaterial(Material.RAW_IRON));
        assertEquals(18, countMaterial(Material.IRON_INGOT));
    }

    @Test
    void allCondensesEveryEligibleMaterial() {
        player.getInventory().setItem(0, new ItemStack(Material.RAW_IRON, 9));
        player.getInventory().setItem(1, new ItemStack(Material.IRON_INGOT, 18));
        player.getInventory().setItem(2, new ItemStack(Material.DIAMOND, 9));
        player.getInventory().setItem(3, new ItemStack(Material.COBBLESTONE, 64));

        cmd.onCommand(player, bukkitCmd, "condense", new String[]{"all"});

        assertEquals(1, countMaterial(Material.RAW_IRON_BLOCK));
        assertEquals(2, countMaterial(Material.IRON_BLOCK));
        assertEquals(1, countMaterial(Material.DIAMOND_BLOCK));
        assertEquals(64, countMaterial(Material.COBBLESTONE));
    }

    @Test
    void condensingLeavesRemainderInPlace() {
        player.getInventory().setItem(0, new ItemStack(Material.IRON_INGOT, 14));
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_INGOT, 14));

        cmd.onCommand(player, bukkitCmd, "condense", new String[0]);

        assertEquals(1, countMaterial(Material.IRON_BLOCK));
        assertEquals(5, countMaterial(Material.IRON_INGOT));
    }

    @Test
    void condensesResourceHuntTaggedItemsAndPropagatesTag() {
        ItemStack tagged = tagged(Material.RAW_IRON, 9);
        player.getInventory().setItemInMainHand(tagged);

        cmd.onCommand(player, bukkitCmd, "condense", new String[]{"all"});

        assertEquals(0, countMaterial(Material.RAW_IRON));
        assertEquals(1, countMaterial(Material.RAW_IRON_BLOCK));

        // The output block must carry the resource_hunt_counted tag.
        ItemStack block = findFirst(Material.RAW_IRON_BLOCK);
        assertNotNull(block);
        ItemMeta meta = block.getItemMeta();
        assertNotNull(meta);
        assertTrue(meta.getPersistentDataContainer().has(countedKey, PersistentDataType.BYTE),
                "Condensed block must inherit the resource_hunt_counted tag from its source");
    }

    @Test
    void taggedAndUntaggedPoolsStaySeparate() {
        // 9 tagged + 9 untagged ingots -> 1 tagged block + 1 untagged block. The two pools
        // must NOT merge or the tag information for counted items would be lost.
        // Note: setItemInMainHand drops into hotbar slot 0, so place the tagged stash
        // outside the hotbar to avoid clobbering.
        player.getInventory().setItem(10, tagged(Material.IRON_INGOT, 9));
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_INGOT, 9));

        cmd.onCommand(player, bukkitCmd, "condense", new String[0]);

        assertEquals(2, countMaterial(Material.IRON_BLOCK));
        assertEquals(0, countMaterial(Material.IRON_INGOT));

        int taggedBlocks = 0;
        int untaggedBlocks = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() != Material.IRON_BLOCK) continue;
            int amount = stack.getAmount();
            ItemMeta meta = stack.getItemMeta();
            boolean hasTag = meta != null && meta.getPersistentDataContainer().has(countedKey, PersistentDataType.BYTE);
            if (hasTag) taggedBlocks += amount;
            else untaggedBlocks += amount;
        }
        assertEquals(1, taggedBlocks, "One block should carry the tag");
        assertEquals(1, untaggedBlocks, "One block should be plain");
    }

    @Test
    void poolBelowThresholdLeavesItemsUntouched() {
        // 5 tagged + 5 untagged: neither pool reaches 9, so nothing condenses.
        player.getInventory().setItem(10, tagged(Material.IRON_INGOT, 5));
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_INGOT, 5));

        cmd.onCommand(player, bukkitCmd, "condense", new String[0]);

        assertEquals(0, countMaterial(Material.IRON_BLOCK));
        assertEquals(10, countMaterial(Material.IRON_INGOT));
    }

    @Test
    void skipsItemsWithForeignPdcTags() {
        NamespacedKey other = new NamespacedKey(plugin, "some_other_tag");
        ItemStack stack = new ItemStack(Material.IRON_INGOT, 9);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(other, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        player.getInventory().setItemInMainHand(stack);

        cmd.onCommand(player, bukkitCmd, "condense", new String[0]);

        assertEquals(0, countMaterial(Material.IRON_BLOCK));
        assertEquals(9, countMaterial(Material.IRON_INGOT));
    }

    @Test
    void skipsItemsWithCustomMetaEvenIfAlsoTagged() {
        // A renamed counted ingot is still custom-meta; protect it.
        ItemStack stack = tagged(Material.IRON_INGOT, 9);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Custom"));
        stack.setItemMeta(meta);
        player.getInventory().setItemInMainHand(stack);

        cmd.onCommand(player, bukkitCmd, "condense", new String[0]);

        assertEquals(0, countMaterial(Material.IRON_BLOCK));
        assertEquals(9, countMaterial(Material.IRON_INGOT));
    }

    @Test
    void condenseWithoutHeldEligibleMaterialIsNoOp() {
        player.getInventory().setItem(10, new ItemStack(Material.IRON_INGOT, 18));
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK, 1));

        cmd.onCommand(player, bukkitCmd, "condense", new String[0]);

        assertEquals(0, countMaterial(Material.IRON_BLOCK));
        assertEquals(18, countMaterial(Material.IRON_INGOT));
    }

    @Test
    void tabCompleteSuggestsAll() {
        var suggestions = cmd.onTabComplete(player, bukkitCmd, "condense", new String[]{""});
        assertTrue(suggestions.contains("all"));
    }

    private ItemStack tagged(Material material, int amount) {
        ItemStack stack = new ItemStack(material, amount);
        resourceHunt.markItemAsCounted(stack);
        return stack;
    }

    private int countMaterial(Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) total += stack.getAmount();
        }
        return total;
    }

    private ItemStack findFirst(Material material) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) return stack;
        }
        return null;
    }
}
