package me.beeliebub.tweaks.tests.tools.augments;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.tools.augments.AugmentEntry;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void renderedLoreLinesAreNotItalic() {
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = augmentedItem(augments, true);

        assertTrue(item.getItemMeta().lore().stream().noneMatch(this::hasItalic));
    }

    @Test
    void entryLoreHasNoAugmentPrefix() {
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = augmentedItem(augments, true);

        String entry = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().lore().get(1));
        assertTrue(entry.startsWith("Efficiency 5"));
        assertFalse(entry.startsWith("Augment:"));
    }

    @Test
    void entryLoreIsFullyGrayWhenActiveAndDarkGrayWhenInactive() {
        Component active = Messages.TOOLS.augmentEntryLore(
                Component.text("Efficiency", NamedTextColor.AQUA), true, "●●");
        Component inactive = Messages.TOOLS.augmentEntryLore(
                Component.text("Efficiency", NamedTextColor.AQUA), false, "○○");

        assertEquals(NamedTextColor.GRAY, active.color());
        assertEquals(NamedTextColor.DARK_GRAY, inactive.color());
        assertTrue(allColors(active, NamedTextColor.GRAY));
        assertTrue(allColors(inactive, NamedTextColor.DARK_GRAY));
    }

    @Test
    void entryLoreDotCountMatchesQualityWeight() {
        plugin.getConfig().set("tools.augments.quality-slot-cost.none", 4);
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = augmentedItem(augments, true);
        String entry = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().lore().get(1));

        assertTrue(entry.endsWith("●●●●"));
    }

    @Test
    void unaccountedRealEnchantmentIsStillRenderedInLore() {
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = augmentedItem(augments, true);
        item.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
        augments.updateLore(item);

        assertTrue(item.getItemMeta().lore().stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .anyMatch(line -> line.contains("Vanishing")));
    }

    @Test
    void tooltipDisplayHidesEnchantmentsAndMergesExistingHiddenComponents() {
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = augmentedItem(augments, true);
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.DAMAGE).build());

        augments.updateLore(item);

        TooltipDisplay tooltip = item.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        assertNotNull(tooltip);
        assertFalse(tooltip.hideTooltip());
        assertTrue(tooltip.hiddenComponents().contains(DataComponentTypes.DAMAGE));
        assertTrue(tooltip.hiddenComponents().contains(DataComponentTypes.ENCHANTMENTS));
    }

    @Test
    void tooltipDisplayIsIdempotentAcrossRepeatedUpdates() {
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = augmentedItem(augments, true);

        augments.updateLore(item);
        TooltipDisplay first = item.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        augments.updateLore(item);
        TooltipDisplay second = item.getData(DataComponentTypes.TOOLTIP_DISPLAY);

        assertEquals(first.hiddenComponents(), second.hiddenComponents());
        assertEquals(first.hideTooltip(), second.hideTooltip());
    }

    @Test
    void ledgerlessItemsDoNotReceiveAugmentLoreOrTooltipChanges() {
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(Component.text("Foreign lore")));
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.DAMAGE).build());

        augments.updateLore(item);

        assertEquals(List.of(Component.text("Foreign lore")), item.getItemMeta().lore());
        assertFalse(item.getData(DataComponentTypes.TOOLTIP_DISPLAY)
                .hiddenComponents().contains(DataComponentTypes.ENCHANTMENTS));
        assertNull(item.getData(DataComponentTypes.TOOLTIP_DISPLAY).hiddenComponents().stream()
                .filter(type -> type.equals(DataComponentTypes.ENCHANTMENTS)).findFirst().orElse(null));
    }

    private ItemStack augmentedItem(AugmentService augments, boolean active) {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 2,
                List.of(new AugmentEntry(NamespacedKey.minecraft("efficiency"), 5, active)), true);
        augments.updateLore(item);
        return item;
    }

    private boolean hasItalic(Component component) {
        if (component.style().decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE) return true;
        return component.children().stream().anyMatch(this::hasItalic);
    }

    private boolean allColors(Component component, NamedTextColor expected) {
        if (component.color() != null && !expected.equals(component.color())) return false;
        return component.children().stream().allMatch(child -> allColors(child, expected));
    }
}
