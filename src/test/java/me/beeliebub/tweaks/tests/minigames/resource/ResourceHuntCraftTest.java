package me.beeliebub.tweaks.tests.minigames.resource;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.RewardManager;
import me.beeliebub.tweaks.minigames.resource.ResourceCraftListener;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

class ResourceHuntCraftTest {

    private ServerMock server;
    private Tweaks plugin;
    private ResourceHunt resourceHunt;
    private ResourceCraftListener listener;
    private PlayerMock player;
    private NamespacedKey countedKey;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        resourceHunt = new ResourceHunt(plugin, new RewardManager(plugin));
        listener = new ResourceCraftListener(plugin, resourceHunt);
        server.addSimpleWorld("world");
        player = server.addPlayer();
        countedKey = new NamespacedKey(plugin, "resource_hunt_counted");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void tagsInventoryItemsOfCraftedMaterial() {
        player.getInventory().setItem(0, new ItemStack(Material.IRON_BLOCK, 3));
        player.getInventory().setItem(1, new ItemStack(Material.STICK, 1));

        listener.tagCraftedItems(player, Material.IRON_BLOCK);

        assertTrue(hasCountedTag(player.getInventory().getItem(0)),
                "Crafted-material stack should carry resource_hunt_counted");
        assertFalse(hasCountedTag(player.getInventory().getItem(1)),
                "Unrelated material must not be tagged");
    }

    @Test
    void doesNotTagOtherMaterials() {
        player.getInventory().setItem(0, new ItemStack(Material.IRON_INGOT, 9));

        listener.tagCraftedItems(player, Material.DIAMOND_BLOCK);

        assertFalse(hasCountedTag(player.getInventory().getItem(0)));
    }

    @Test
    void doesNotDoubleTagAlreadyCountedItems() {
        ItemStack pre = new ItemStack(Material.IRON_BLOCK, 1);
        resourceHunt.markItemAsCounted(pre);
        player.getInventory().setItem(0, pre);

        listener.tagCraftedItems(player, Material.IRON_BLOCK);

        assertTrue(hasCountedTag(player.getInventory().getItem(0)),
                "Already-counted items remain tagged");
    }

    @Test
    void tagsCursorItemWhenMaterialMatches() {
        ItemStack cursor = new ItemStack(Material.IRON_BLOCK, 1);
        player.getOpenInventory().setCursor(cursor);

        listener.tagCraftedItems(player, Material.IRON_BLOCK);

        assertTrue(hasCountedTag(player.getOpenInventory().getCursor()));
    }

    @Test
    void resourceHuntRecognisesTaggedItem() {
        player.getInventory().setItem(0, new ItemStack(Material.IRON_BLOCK, 1));
        listener.tagCraftedItems(player, Material.IRON_BLOCK);

        // The same NamespacedKey wired into ResourceHunt should now match the slot's stack.
        ItemStack inSlot = player.getInventory().getItem(0);
        assertNotNull(inSlot);
        ItemMeta meta = inSlot.getItemMeta();
        assertNotNull(meta);
        assertTrue(meta.getPersistentDataContainer().has(countedKey, PersistentDataType.BYTE));
    }

    private boolean hasCountedTag(ItemStack stack) {
        if (stack == null) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(countedKey, PersistentDataType.BYTE);
    }
}
