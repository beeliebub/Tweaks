package me.beeliebub.tweaks.recipes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

// Factory + identifier for the Resource Rupee currency items.
//
// A Resource Rupee is a renamed/lore-tagged Emerald; a Resource Rupee Block is the same treatment
// on an Emerald Block. Both are convertible via the crafting grid (9 Rupees <-> 1 Rupee Block).
// The conversion is implemented in ResourceRupeeListener — vanilla crafting still applies for
// plain emeralds, but a 3x3 grid of Rupees (or a single Rupee Block) is intercepted and routed
// to its currency counterpart.
//
// The minted item carries exactly three data components beyond id/count:
//   minecraft:item_name                 = {color: "green", text: "Resource Rupee[ Block]"}
//   minecraft:lore                      = [{italic: false, text: LORE_LINE, color: "dark_green"}]
//   minecraft:enchantment_glint_override = true
//
// Identification is by **name + lore text alone** (plain-text comparison via Adventure's
// PlainTextComponentSerializer) so that Rupees originating outside this plugin (datapacks,
// /give with components, third-party kits, command blocks) are still recognized.
public final class ResourceRupee {

    public static final String RUPEE_NAME = "Resource Rupee";
    public static final String RUPEE_BLOCK_NAME = "Resource Rupee Block";
    public static final String LORE_LINE = "...the Wanderer's Path...";

    public ItemStack createRupee(int amount) {
        return mint(Material.EMERALD, RUPEE_NAME, amount);
    }

    public ItemStack createRupeeBlock(int amount) {
        return mint(Material.EMERALD_BLOCK, RUPEE_BLOCK_NAME, amount);
    }

    private static ItemStack mint(Material material, String name, int amount) {
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();

        // item_name uses minecraft:item_name (vanilla renders it without italic by default).
        // No explicit italic style is set so the serialized component stays clean:
        // {"color":"green","text":"Resource Rupee[ Block]"}.
        meta.itemName(Component.text(name).color(NamedTextColor.GREEN));

        // lore explicitly disables italic so the serialized form carries italic:false alongside
        // the dark_green color and text.
        meta.lore(List.of(
                Component.text(LORE_LINE, NamedTextColor.DARK_GREEN)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        // Cosmetic shimmer without any actual enchantment.
        meta.setEnchantmentGlintOverride(true);

        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isRupee(ItemStack stack) {
        if (stack == null || stack.getType() != Material.EMERALD) return false;
        return matchesNameAndLore(stack, RUPEE_NAME);
    }

    public boolean isRupeeBlock(ItemStack stack) {
        if (stack == null || stack.getType() != Material.EMERALD_BLOCK) return false;
        return matchesNameAndLore(stack, RUPEE_BLOCK_NAME);
    }

    // Plain-text comparison covers items produced by this plugin's factory AND items minted
    // elsewhere with the same name+lore (datapacks, /give with components, third-party kits).
    // Checks both itemName() (data component minecraft:item_name) and displayName()
    // (data component minecraft:custom_name) because external sources may use either field.
    private static boolean matchesNameAndLore(ItemStack stack, String expectedName) {
        if (!stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (!nameMatches(meta, expectedName)) return false;
        if (!meta.hasLore()) return false;
        List<Component> lore = meta.lore();
        if (lore == null) return false;
        for (Component c : lore) {
            String plain = PlainTextComponentSerializer.plainText().serialize(c);
            if (LORE_LINE.equals(plain)) return true;
        }
        return false;
    }

    private static boolean nameMatches(ItemMeta meta, String expected) {
        Component itemName = meta.itemName();
        if (itemName != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(itemName);
            if (expected.equals(plain)) return true;
        }
        if (meta.hasDisplayName()) {
            Component displayName = meta.displayName();
            if (displayName != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(displayName);
                if (expected.equals(plain)) return true;
            }
        }
        return false;
    }
}
