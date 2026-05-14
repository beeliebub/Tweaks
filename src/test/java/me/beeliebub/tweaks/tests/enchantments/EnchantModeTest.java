package me.beeliebub.tweaks.tests.enchantments;

import me.beeliebub.tweaks.enchantments.modes.EnchantMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnchantModeTest {

    private JavaPlugin plugin;
    private EnchantMode mode;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("tweaks");
        mode = new EnchantMode(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void getModeReturnsMaxWhenPdcUnset() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        assertEquals(5, mode.getMode(tool, 5));
        assertEquals(2, mode.getMode(tool, 2));
        assertEquals(1, mode.getMode(tool, 1));
    }

    @Test
    void getModeClampsPdcValueAboveMax() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        mode.setMode(tool, 5);
        // Now the player upgrades to a smaller-radius tool (max = 3); mode should clamp.
        assertEquals(3, mode.getMode(tool, 3));
    }

    @Test
    void getModeClampsPdcValueBelowOne() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        // Force a 0 directly into PDC (setMode would not normally write a non-positive value).
        tool.editMeta(meta -> meta.getPersistentDataContainer().set(
                mode.modeKey(), PersistentDataType.INTEGER, 0));
        assertEquals(1, mode.getMode(tool, 5));
    }

    @Test
    void setModeWritesPdcAndAppendsLoreLine() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        mode.setMode(tool, 3);
        ItemMeta meta = tool.getItemMeta();
        assertEquals(3, meta.getPersistentDataContainer().get(mode.modeKey(), PersistentDataType.INTEGER));

        List<Component> lore = meta.lore();
        assertNotNull(lore);
        assertEquals(1, lore.size());
        assertEquals("Mode: 7x7", PlainTextComponentSerializer.plainText().serialize(lore.get(0)));
    }

    @Test
    void setModeReplacesExistingModeLoreInPlace() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        mode.setMode(tool, 5); // "Mode: 11x11"
        mode.setMode(tool, 2); // "Mode: 5x5"

        List<Component> lore = tool.getItemMeta().lore();
        assertNotNull(lore);
        assertEquals(1, lore.size());
        assertEquals("Mode: 5x5", PlainTextComponentSerializer.plainText().serialize(lore.get(0)));
    }

    @Test
    void setModePreservesExistingNonModeLoreLines() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        tool.editMeta(meta -> meta.lore(List.of(
                Component.text("Existing line A", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Existing line B", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        )));

        mode.setMode(tool, 4);

        List<Component> lore = tool.getItemMeta().lore();
        assertNotNull(lore);
        assertEquals(3, lore.size());
        assertEquals("Existing line A", PlainTextComponentSerializer.plainText().serialize(lore.get(0)));
        assertEquals("Existing line B", PlainTextComponentSerializer.plainText().serialize(lore.get(1)));
        assertEquals("Mode: 9x9", PlainTextComponentSerializer.plainText().serialize(lore.get(2)));
    }

    @Test
    void clearModeRemovesPdcAndLore() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        mode.setMode(tool, 3);
        mode.clearMode(tool);

        ItemMeta meta = tool.getItemMeta();
        assertFalse(meta.getPersistentDataContainer().has(mode.modeKey(), PersistentDataType.INTEGER));
        // Lore should be null or empty after the single mode line was removed.
        assertTrue(meta.lore() == null || meta.lore().isEmpty());
    }

    @Test
    void clearModeKeepsUnrelatedLore() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        tool.editMeta(meta -> meta.lore(List.of(
                Component.text("Keeper", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        )));
        mode.setMode(tool, 5);
        mode.clearMode(tool);

        List<Component> lore = tool.getItemMeta().lore();
        assertNotNull(lore);
        assertEquals(1, lore.size());
        assertEquals("Keeper", PlainTextComponentSerializer.plainText().serialize(lore.get(0)));
    }

    @Test
    void getModeHandlesNullTool() {
        assertEquals(0, mode.getMode(null, 0));
        assertEquals(5, mode.getMode(null, 5));
    }

    @Test
    void getModeReturnsZeroWhenMaxIsZero() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        mode.setMode(tool, 3);
        assertEquals(0, mode.getMode(tool, 0));
    }

    @Test
    void cycleSemanticsMatchListenerBehavior() {
        // Simulate the listener's decrement-with-wrap behavior for a max=5 tool.
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        int max = 5;
        int[] expected = {4, 3, 2, 1, 5, 4, 3, 2, 1, 5};
        for (int cycle : expected) {
            int current = mode.getMode(tool, max);
            int next = current - 1;
            if (next < 1) next = max;
            mode.setMode(tool, next);
            assertEquals(cycle, mode.getMode(tool, max));
        }
    }

    @Test
    void modeLorePatternStrictlyMatchesNxN() {
        // Ensures the "is our mode lore" check doesn't eat user lore that just happens to start
        // with "Mode: " — only the NxN suffix is recognized.
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        tool.editMeta(meta -> meta.lore(List.of(
                Component.text("Mode: ultraflex", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        )));
        mode.setMode(tool, 2);

        List<Component> lore = tool.getItemMeta().lore();
        assertNotNull(lore);
        // Existing user line preserved, plus our managed line appended.
        assertEquals(2, lore.size());
        assertEquals("Mode: ultraflex", PlainTextComponentSerializer.plainText().serialize(lore.get(0)));
        assertEquals("Mode: 5x5", PlainTextComponentSerializer.plainText().serialize(lore.get(1)));
    }
}
