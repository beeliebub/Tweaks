package me.beeliebub.tweaks.tests.tools.augments;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.tools.augments.AugmentGemItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AugmentGemItemTest {

    private ServerMock server;
    private Tweaks plugin;
    private AugmentGemItem gems;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        gems = new AugmentGemItem(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void loreNamesPrimaryAndRiderWithoutChangingDisplayName() {
        ItemStack gem = gems.create(Enchantment.EFFICIENCY, 5,
                List.of(new AugmentGemItem.CurseRider(Enchantment.VANISHING_CURSE, 1)));
        List<Component> lore = gem.getItemMeta().lore();

        assertTrue(lore == null || lore.isEmpty());
        assertEquals(Map.of(Enchantment.EFFICIENCY, 5, Enchantment.VANISHING_CURSE, 1),
                gem.getData(DataComponentTypes.STORED_ENCHANTMENTS).enchantments());
        assertEquals(1, gems.read(gem).curses().size());
        assertEquals(Messages.TOOLS.augmentGemName(), gem.getData(DataComponentTypes.ITEM_NAME));
        assertNull(gem.getData(DataComponentTypes.CUSTOM_NAME));
    }

    @Test
    void gemsWithDifferentRidersDoNotStackAndRecognitionIgnoresLore() {
        ItemStack first = gems.create(Enchantment.EFFICIENCY, 5,
                List.of(new AugmentGemItem.CurseRider(Enchantment.VANISHING_CURSE, 1)));
        ItemStack same = gems.create(Enchantment.EFFICIENCY, 5,
                List.of(new AugmentGemItem.CurseRider(Enchantment.VANISHING_CURSE, 1)));
        ItemStack different = gems.create(Enchantment.EFFICIENCY, 5,
                List.of(new AugmentGemItem.CurseRider(Enchantment.BINDING_CURSE, 1)));

        assertTrue(first.isSimilar(same));
        assertFalse(first.isSimilar(different));
        ItemMeta meta = first.getItemMeta();
        meta.lore(List.of(Component.text("changed by another system")));
        first.setItemMeta(meta);
        assertTrue(gems.isGem(first));
        assertNotNull(gems.read(first));
    }

    @Test
    void malformedRiderEntriesDoNotDiscardValidEntries() {
        ItemStack gem = gems.create(Enchantment.EFFICIENCY, 5);
        ItemMeta meta = gem.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "augment_gem_curses"),
                PersistentDataType.LIST.strings(), List.of(
                        "v9|minecraft:vanishing_curse|1",
                        "v1|minecraft:vanishing_curse|invalid",
                        "v1|minecraft:vanishing_curse|1"));
        gem.setItemMeta(meta);
        gem.setData(DataComponentTypes.STORED_ENCHANTMENTS,
                ItemEnchantments.itemEnchantments(Map.of(Enchantment.EFFICIENCY, 5,
                        Enchantment.VANISHING_CURSE, 1)));

        AugmentGemItem.GemData data = gems.read(gem);

        assertNotNull(data);
        assertEquals(1, data.curses().size());
        assertEquals(Enchantment.VANISHING_CURSE, data.curses().getFirst().enchantment());
    }

    @Test
    void readFailsClosedWhenStoredEnchantmentsAreMissing() {
        ItemStack gem = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = gem.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "augment_gem"),
                PersistentDataType.BYTE, (byte) 1);
        gem.setItemMeta(meta);

        assertNull(gems.read(gem));
    }

    @Test
    void readFailsClosedOnMultipleNonRiderPrimaries() {
        ItemStack gem = gems.create(Enchantment.EFFICIENCY, 5);
        gem.setData(DataComponentTypes.STORED_ENCHANTMENTS,
                ItemEnchantments.itemEnchantments(Map.of(Enchantment.EFFICIENCY, 5, Enchantment.SHARPNESS, 2)));

        assertNull(gems.read(gem));
    }

    @Test
    void readFailsClosedOnZeroNonRiderPrimaries() {
        ItemStack gem = gems.create(Enchantment.EFFICIENCY, 5,
                List.of(new AugmentGemItem.CurseRider(Enchantment.VANISHING_CURSE, 1)));
        gem.setData(DataComponentTypes.STORED_ENCHANTMENTS,
                ItemEnchantments.itemEnchantments(Map.of(Enchantment.VANISHING_CURSE, 1)));

        assertNull(gems.read(gem));
    }

    @Test
    void curseOnlyGemRoundTrips() {
        ItemStack gem = gems.create(Enchantment.VANISHING_CURSE, 1);

        assertEquals(Enchantment.VANISHING_CURSE, gems.read(gem).enchantment());
        assertEquals(1, gems.read(gem).level());
    }

    @Test
    void createRejectsRiderCollidingWithPrimaryKey() {
        assertNull(gems.create(Enchantment.VANISHING_CURSE, 1,
                List.of(new AugmentGemItem.CurseRider(Enchantment.VANISHING_CURSE, 1))));
    }

    @Test
    void gemNameHasNoExplicitColor() {
        assertEquals(Component.text("Augment Gem"), Messages.TOOLS.augmentGemName());
        assertNull(Messages.TOOLS.augmentGemName().color());
    }

    @Test
    void newlyCreatedGemsKeepOnlyTheMarkerAndRiderPdcFields() {
        ItemStack gem = gems.create(Enchantment.EFFICIENCY, 5);
        ItemMeta meta = gem.getItemMeta();
        NamespacedKey enchantmentKey = new NamespacedKey(plugin, "augment_gem_enchantment");
        NamespacedKey levelKey = new NamespacedKey(plugin, "augment_gem_level");

        assertTrue(meta.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "augment_gem"), PersistentDataType.BYTE));
        assertFalse(meta.getPersistentDataContainer().has(enchantmentKey, PersistentDataType.STRING));
        assertFalse(meta.getPersistentDataContainer().has(levelKey, PersistentDataType.INTEGER));
    }

    @Test
    void readFailsClosedWhenRiderMetadataDoesNotMatchStoredEnchantments() {
        ItemStack gem = gems.create(Enchantment.EFFICIENCY, 5);
        ItemMeta meta = gem.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "augment_gem_curses"),
                PersistentDataType.LIST.strings(), List.of("v1|minecraft:vanishing_curse|2"));
        gem.setItemMeta(meta);
        gem.setData(DataComponentTypes.STORED_ENCHANTMENTS,
                ItemEnchantments.itemEnchantments(Map.of(Enchantment.EFFICIENCY, 5,
                        Enchantment.VANISHING_CURSE, 1)));

        assertNull(gems.read(gem));
    }

    @Test
    void duplicateRiderInputIsRejected() {
        assertNull(gems.create(Enchantment.EFFICIENCY, 5, List.of(
                new AugmentGemItem.CurseRider(Enchantment.VANISHING_CURSE, 1),
                new AugmentGemItem.CurseRider(Enchantment.VANISHING_CURSE, 1))));
    }

    @Test
    void oversizedRiderListIsRejectedBeforeSynchronousDecode() {
        ItemStack gem = gems.create(Enchantment.EFFICIENCY, 5);
        ItemMeta meta = gem.getItemMeta();
        List<String> oversized = new ArrayList<>();
        for (int i = 0; i <= AugmentGemItem.MAX_CURSE_RIDERS; i++) {
            oversized.add("v1|minecraft:vanishing_curse|1");
        }
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "augment_gem_curses"),
                PersistentDataType.LIST.strings(), oversized);
        gem.setItemMeta(meta);

        assertNull(gems.read(gem));
    }

    @Test
    void oversizedRiderInputIsRejectedInsteadOfTruncated() {
        List<AugmentGemItem.CurseRider> oversized = new ArrayList<>();
        for (int i = 0; i <= AugmentGemItem.MAX_CURSE_RIDERS; i++) {
            oversized.add(new AugmentGemItem.CurseRider(Enchantment.VANISHING_CURSE, 1));
        }

        assertNull(gems.create(Enchantment.EFFICIENCY, 5, oversized));
    }
}
