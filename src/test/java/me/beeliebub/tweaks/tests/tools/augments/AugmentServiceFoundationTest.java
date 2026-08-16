package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.AugmentEntry;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AugmentServiceFoundationTest {

    private ServerMock server;
    private Tweaks plugin;
    private AugmentService augments;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        augments = new AugmentService(plugin, null);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void cursePredicateUsesTheRegistryTag() {
        assertTrue(augments.isCurse(Enchantment.VANISHING_CURSE));
        assertTrue(augments.isCurse(Enchantment.BINDING_CURSE));
        assertFalse(augments.isCurse(Enchantment.EFFICIENCY));
    }

    @Test
    void resyncRestoresActiveEntriesWithoutTouchingForeignEnchantments() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        AugmentEntry active = new AugmentEntry(NamespacedKey.minecraft("efficiency"), 5, true);
        augments.ledger().write(item, 5, List.of(active), true);
        item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 1);
        item.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
        item.removeEnchantment(Enchantment.EFFICIENCY);

        augments.resyncActiveEnchantments(item);

        assertEquals(5, item.getEnchantmentLevel(Enchantment.EFFICIENCY));
        assertEquals(2, item.getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test
    void emptyGemBatchIsEmpty() {
        assertTrue(augments.createGemBatch(Map.of(), new Random(1)).isEmpty());
    }

    @Test
    void foreignLookalikeLoreSurvivesAugmentRefreshes() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(Component.text("Augment: minecraft:fortune 3 ●"), Component.text("Keep this line")));
        item.setItemMeta(meta);
        augments.ledger().write(item, 1, List.of(), true);

        augments.updateLore(item);
        List<Component> withDuplicate = new ArrayList<>(item.getItemMeta().lore());
        String visibleSlot = PlainTextComponentSerializer.plainText().serialize(withDuplicate.getLast())
                .replace("\u2063", "");
        withDuplicate.add(Component.text(visibleSlot));
        meta = item.getItemMeta();
        meta.lore(withDuplicate);
        item.setItemMeta(meta);
        augments.updateLore(item);

        List<Component> lore = item.getItemMeta().lore();
        List<String> plain = lore.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
        assertEquals(1, plain.stream().filter("Augment: minecraft:fortune 3 ●"::equals).count());
        assertTrue(plain.contains("Keep this line"));
        assertEquals(2, plain.stream().filter(line -> line.startsWith("Augment Slots:")).count());
    }
}
