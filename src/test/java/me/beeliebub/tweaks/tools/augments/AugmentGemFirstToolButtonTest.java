package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each gem-first tool button's tooltip must list whatever augments and curses that tool already
 * carries, so a player can tell two stacks of the same material apart before committing the attach.
 */
class AugmentGemFirstToolButtonTest {

    private ServerMock server;
    private Tweaks plugin;
    private AugmentService augments;
    private AugmentDialog dialog;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        augments = new AugmentService(plugin, new QualityRegistry(plugin));
        dialog = new AugmentDialog(augments);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void tooltipListsAttachedAugmentsAndBoundCurses() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 2, List.of(
                new AugmentEntry(Enchantment.EFFICIENCY.getKey(), 4, true),
                new AugmentEntry(Enchantment.UNBREAKING.getKey(), 3, false)), true);
        augments.ledger().appendCurses(item, List.of(
                new AugmentGemItem.CurseRider(Enchantment.BINDING_CURSE, 1)));

        ItemStack gem = augments.gemItem().create(Enchantment.FORTUNE, 3);
        String tooltip = plain(dialog.toolButtonTooltip(item, augments.gemItem().read(gem)));

        assertTrue(tooltip.contains("Currently attached:"), tooltip);
        assertTrue(tooltip.contains("Efficiency IV"), tooltip);
        assertTrue(tooltip.contains("Unbreaking III"), tooltip);
        assertTrue(tooltip.contains("Binding"), tooltip);
        assertFalse(tooltip.contains("no augments attached"), tooltip);
    }

    @Test
    void tooltipForABareToolSaysNothingIsAttached() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 1, List.of(), true);

        ItemStack gem = augments.gemItem().create(Enchantment.EFFICIENCY, 5);
        String tooltip = plain(dialog.toolButtonTooltip(item, augments.gemItem().read(gem)));

        assertTrue(tooltip.contains("no augments attached"), tooltip);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
