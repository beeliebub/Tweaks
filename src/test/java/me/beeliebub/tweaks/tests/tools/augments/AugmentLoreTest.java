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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AugmentLoreTest {

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
    void renderedLoreUsesVisibleArabicLevelsWithoutAnInvisibleMarker() {
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 2,
                List.of(new AugmentEntry(NamespacedKey.minecraft("efficiency"), 5, true)), true);

        augments.updateLore(item);

        List<Component> lore = item.getItemMeta().lore();
        String plain = lore.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(plain.contains("Efficiency 5"));
        assertFalse(lore.stream().anyMatch(line -> line.toString().contains("\u2063")));
    }

    @Test
    void ownedBlockReplacementLeavesForeignLoreWithAnIdenticalLine() {
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(Component.text("Keep this line")));
        item.setItemMeta(meta);
        augments.ledger().write(item, 1, List.of(), true);

        augments.updateLore(item);
        List<Component> existing = item.getItemMeta().lore();
        meta = item.getItemMeta();
        meta.lore(List.of(existing.getFirst(), existing.getLast(), existing.getLast()));
        item.setItemMeta(meta);
        augments.updateLore(item);

        long slots = item.getItemMeta().lore().stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .filter(line -> line.startsWith("Augment Slots:"))
                .count();
        assertTrue(slots >= 2);
        assertTrue(item.getItemMeta().lore().stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .anyMatch("Keep this line"::equals));
    }
}
