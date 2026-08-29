package me.beeliebub.tweaks.tests.tools.augments;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.tools.augments.AugmentEntry;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
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
    void renderedLoreUsesVisibleRomanLevelsWithoutAnInvisibleMarker() {
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 2,
                List.of(new AugmentEntry(NamespacedKey.minecraft("efficiency"), 5, true)), true);

        augments.updateLore(item);

        List<Component> lore = item.getItemMeta().lore();
        String plain = lore.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(plain.contains("Efficiency V"));
        assertFalse(plain.contains("Efficiency 5"));
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
        assertTrue(entry.startsWith("Efficiency V"));
        assertFalse(entry.startsWith("Augment:"));
    }

    @Test
    void entryLorePreservesQualityColorAndUsesWhiteActiveDots() {
        Component qualityName = Component.text("✤", NamedTextColor.WHITE)
                .append(Component.text("Efficiency", NamedTextColor.AQUA));
        Component active = Messages.TOOLS.augmentEntryLore(
                qualityName, true, true, "●●");
        Component inactive = Messages.TOOLS.augmentEntryLore(
                qualityName, false, true, "○○");

        // The line root is the name component itself; the dots ride as its last child.
        assertEquals(NamedTextColor.WHITE, active.color());
        assertEquals(NamedTextColor.AQUA, active.children().getFirst().color());
        assertEquals(NamedTextColor.WHITE, active.children().getLast().color());
        assertTrue(allColors(active.children().getLast(), NamedTextColor.WHITE));
        assertEquals(TextDecoration.State.FALSE, active.decoration(TextDecoration.ITALIC));
        assertEquals(NamedTextColor.DARK_GRAY, inactive.color());
        assertTrue(allColors(inactive, NamedTextColor.DARK_GRAY));
    }

    @Test
    void plainVanillaSilkTouchAugmentRendersGrayWithoutANumeral() {
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 1,
                List.of(new AugmentEntry(NamespacedKey.minecraft("silk_touch"), 1, true)), true);
        item.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);

        augments.updateLore(item);

        Component entryLine = item.getItemMeta().lore().get(1);
        String plain = PlainTextComponentSerializer.plainText().serialize(entryLine);
        assertTrue(plain.startsWith("Silk Touch"), plain);
        assertFalse(plain.contains("Silk Touch I"), plain);
        // Line root is the gray name; only the trailing dots child is white.
        assertEquals(NamedTextColor.GRAY, entryLine.color(), entryLine.toString());
        assertEquals(NamedTextColor.WHITE, entryLine.children().getLast().color());
    }

    @Test
    void mendingAugmentRendersAsNumismaticLiteralText() {
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 1,
                List.of(new AugmentEntry(NamespacedKey.minecraft("mending"), 1, true)), true);
        item.addUnsafeEnchantment(Enchantment.MENDING, 1);

        augments.updateLore(item);

        String line = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().lore().get(1));
        assertTrue(line.startsWith("Numismatic"), line);
        assertFalse(line.contains("Mending"), line);
    }

    @Test
    void generatedLoreCarriesNoEmptyFillerComponents() {
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 3, List.of(
                new AugmentEntry(NamespacedKey.minecraft("silk_touch"), 1, true),
                new AugmentEntry(NamespacedKey.minecraft("efficiency"), 5, false)), true);
        item.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        item.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);

        augments.updateLore(item);

        List<Component> lore = item.getItemMeta().lore();
        for (Component line : lore) {
            assertFalse(hasEmptyLeaf(line), line.toString());
        }
        // The active entry line matches the screenshot standard: italic is set once, at the
        // line root, and every descendant inherits it rather than restating it.
        Component entryLine = lore.get(1);
        assertEquals(TextDecoration.State.FALSE, entryLine.decoration(TextDecoration.ITALIC));
        assertFalse(entryLine.children().stream().anyMatch(this::carriesExplicitItalic), entryLine.toString());
    }

    @Test
    void entryLoreSuppressesAnItalicSpanFromADatapackEnchantName() {
        Component datapackName = Component.text("⚔ ", NamedTextColor.GOLD)
                .append(Component.text("Bloodthirst", NamedTextColor.DARK_RED)
                        .decoration(TextDecoration.ITALIC, true));

        Component activeLine = Messages.TOOLS.augmentEntryLore(datapackName, true, true, "●●");
        Component curseLine = Messages.TOOLS.augmentCurseLore(datapackName);
        Component foreignLine = Messages.TOOLS.augmentForeignEnchantLore(datapackName);

        assertFalse(hasItalic(activeLine), activeLine.toString());
        assertFalse(hasItalic(curseLine), curseLine.toString());
        assertFalse(hasItalic(foreignLine), foreignLine.toString());
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
    void curseLoreOmitsTheRedundantCursePrefix() {
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = augmentedItem(augments, true);
        augments.ledger().appendCurses(item, List.of(
                new me.beeliebub.tweaks.tools.augments.AugmentGemItem.CurseRider(
                        Enchantment.VANISHING_CURSE, 1)));
        augments.updateLore(item);

        List<String> lines = item.getItemMeta().lore().stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
        assertTrue(lines.stream().anyMatch(line -> line.endsWith("Curse of Vanishing")
                || line.endsWith("Vanishing Curse")), lines.toString());
        assertFalse(lines.stream().anyMatch(line -> line.startsWith("Curse: ")));

        Component rendered = Messages.TOOLS.augmentCurseLore(
                Component.text("Curse of Vanishing", NamedTextColor.RED));
        assertEquals("Curse of Vanishing", PlainTextComponentSerializer.plainText().serialize(rendered));
        assertEquals(NamedTextColor.RED, rendered.color());
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

    private boolean hasEmptyLeaf(Component component) {
        if (component instanceof TextComponent text
                && text.content().isEmpty() && component.children().isEmpty()) {
            return true;
        }
        return component.children().stream().anyMatch(this::hasEmptyLeaf);
    }

    private boolean carriesExplicitItalic(Component component) {
        if (component.decoration(TextDecoration.ITALIC) != TextDecoration.State.NOT_SET) return true;
        return component.children().stream().anyMatch(this::carriesExplicitItalic);
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
