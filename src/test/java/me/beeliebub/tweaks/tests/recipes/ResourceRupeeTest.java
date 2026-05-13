package me.beeliebub.tweaks.tests.recipes;

import me.beeliebub.tweaks.recipes.ResourceRupee;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceRupeeTest {

    private final ResourceRupee rupee = new ResourceRupee();

    @Test
    void isRupeeRejectsNull() {
        assertFalse(rupee.isRupee(null));
        assertFalse(rupee.isRupeeBlock(null));
    }

    @Test
    void isRupeeRejectsWrongMaterial() {
        ItemStack stack = stackWith(Material.DIAMOND, ResourceRupee.RUPEE_NAME, ResourceRupee.LORE_LINE, false);
        assertFalse(rupee.isRupee(stack));
    }

    @Test
    void isRupeeBlockRejectsWrongMaterial() {
        ItemStack stack = stackWith(Material.DIAMOND_BLOCK, ResourceRupee.RUPEE_BLOCK_NAME, ResourceRupee.LORE_LINE, false);
        assertFalse(rupee.isRupeeBlock(stack));
    }

    @Test
    void isRupeeRejectsEmeraldWithoutNameOrLore() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(stack.getType()).thenReturn(Material.EMERALD);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.itemName()).thenReturn(null);
        when(meta.hasDisplayName()).thenReturn(false);
        assertFalse(rupee.isRupee(stack));
    }

    @Test
    void isRupeeRejectsEmeraldWithRightNameButWrongLore() {
        ItemStack stack = stackWith(Material.EMERALD, ResourceRupee.RUPEE_NAME, "wrong lore", false);
        assertFalse(rupee.isRupee(stack));
    }

    @Test
    void isRupeeRejectsEmeraldWithRightLoreButWrongName() {
        ItemStack stack = stackWith(Material.EMERALD, "Some Other Coin", ResourceRupee.LORE_LINE, false);
        assertFalse(rupee.isRupee(stack));
    }

    @Test
    void isRupeeRejectsEmeraldWithRupeeBlockNameOnly() {
        ItemStack stack = stackWith(Material.EMERALD, ResourceRupee.RUPEE_BLOCK_NAME, ResourceRupee.LORE_LINE, false);
        assertFalse(rupee.isRupee(stack));
    }

    @Test
    void isRupeeBlockRejectsEmeraldBlockWithRupeeNameOnly() {
        ItemStack stack = stackWith(Material.EMERALD_BLOCK, ResourceRupee.RUPEE_NAME, ResourceRupee.LORE_LINE, false);
        assertFalse(rupee.isRupeeBlock(stack));
    }

    @Test
    void isRupeeAcceptsEmeraldWithItemNameAndLore() {
        // Items minted by this plugin's factory route through itemName() (data component item_name).
        ItemStack stack = stackWith(Material.EMERALD, ResourceRupee.RUPEE_NAME, ResourceRupee.LORE_LINE, false);
        assertTrue(rupee.isRupee(stack));
    }

    @Test
    void isRupeeAcceptsEmeraldWithDisplayNameAndLore() {
        // Items minted externally (e.g. /give with custom_name component, anvil rename, /name
        // command) route through displayName() instead. Both fields must be recognized.
        ItemStack stack = stackWith(Material.EMERALD, ResourceRupee.RUPEE_NAME, ResourceRupee.LORE_LINE, true);
        assertTrue(rupee.isRupee(stack));
    }

    @Test
    void isRupeeBlockAcceptsEmeraldBlockWithItemNameAndLore() {
        ItemStack stack = stackWith(Material.EMERALD_BLOCK, ResourceRupee.RUPEE_BLOCK_NAME, ResourceRupee.LORE_LINE, false);
        assertTrue(rupee.isRupeeBlock(stack));
    }

    @Test
    void isRupeeBlockAcceptsEmeraldBlockWithDisplayNameAndLore() {
        ItemStack stack = stackWith(Material.EMERALD_BLOCK, ResourceRupee.RUPEE_BLOCK_NAME, ResourceRupee.LORE_LINE, true);
        assertTrue(rupee.isRupeeBlock(stack));
    }

    // Build a mocked ItemStack whose ItemMeta carries a name (via either itemName or displayName)
    // and exactly one lore line. PDC interactions are intentionally absent — detection must rely
    // on name + lore alone.
    private static ItemStack stackWith(Material material, String name, String loreText, boolean viaDisplayName) {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(stack.getType()).thenReturn(material);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);

        if (viaDisplayName) {
            when(meta.itemName()).thenReturn(null);
            when(meta.hasDisplayName()).thenReturn(true);
            when(meta.displayName()).thenReturn(Component.text(name, NamedTextColor.GREEN));
        } else {
            when(meta.itemName()).thenReturn(Component.text(name, NamedTextColor.GREEN));
            when(meta.hasDisplayName()).thenReturn(false);
        }

        when(meta.hasLore()).thenReturn(true);
        when(meta.lore()).thenReturn(List.of(Component.text(loreText, NamedTextColor.DARK_GREEN)));
        return stack;
    }
}