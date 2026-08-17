package me.beeliebub.tweaks.tests.tools.augments;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.tools.augments.AugmentGemItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
        String plain = lore.stream().map(PlainTextComponentSerializer.plainText()::serialize).reduce("", (a, b) -> a + "\n" + b);

        assertEquals(2, lore.size());
        assertTrue(plain.contains("Efficiency 5"), plain);
        assertTrue(plain.contains("Vanishing Curse 1"), plain);
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

        AugmentGemItem.GemData data = gems.read(gem);

        assertNotNull(data);
        assertEquals(1, data.curses().size());
        assertEquals(Enchantment.VANISHING_CURSE, data.curses().getFirst().enchantment());
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
