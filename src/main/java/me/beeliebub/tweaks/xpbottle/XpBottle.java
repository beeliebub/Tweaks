package me.beeliebub.tweaks.xpbottle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

// Custom-item factory + identifier for the XP-storage Bottle of Enchanting.
//
// We render this as Material.POTION with PotionType.AWKWARD (no effects) so that vanilla's
// drinking animation, stack decrement, and glass-bottle remainder all "just work". Material
// EXPERIENCE_BOTTLE was rejected because its throw behaviour is hardcoded server-side and is not
// gated on the consumable data component, so right-click would still throw the bottle.
public final class XpBottle {

    // Yellow-green, evokes the vanilla Bottle of Enchanting hue while staying clearly distinct
    // from common potion colours.
    private static final Color BOTTLE_COLOR = Color.fromRGB(184, 224, 88);

    private final NamespacedKey orbsKey;

    public XpBottle(NamespacedKey orbsKey) {
        this.orbsKey = orbsKey;
    }

    public NamespacedKey orbsKey() {
        return orbsKey;
    }

    @SuppressWarnings("deprecation") // ItemFlag.HIDE_ADDITIONAL_TOOLTIP — Paper 26.1.x marks the
    // enum value deprecated but the data-component replacement isn't exposed in this build.
    public ItemStack create(int orbs) {
        ItemStack stack = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        meta.setBasePotionType(PotionType.AWKWARD);
        meta.setColor(BOTTLE_COLOR);

        meta.itemName(Component.text("Bottle of Enchanting")
                .color(NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));

        String formatted = NumberFormat.getNumberInstance(Locale.US).format(orbs);
        meta.lore(List.of(
                Component.text("Stored XP: ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(formatted + " orbs", NamedTextColor.GREEN)
                                .decoration(TextDecoration.ITALIC, false))
        ));

        // Hide the empty potion-effect tooltip ("(No Effects)") that the AWKWARD base would
        // otherwise show.
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        meta.getPersistentDataContainer().set(orbsKey, PersistentDataType.INTEGER, orbs);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isXpBottle(ItemStack stack) {
        if (stack == null || stack.getType() != Material.POTION) return false;
        if (!stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(orbsKey, PersistentDataType.INTEGER);
    }

    public int getStoredOrbs(ItemStack stack) {
        if (!isXpBottle(stack)) return 0;
        Integer val = stack.getItemMeta().getPersistentDataContainer().get(orbsKey, PersistentDataType.INTEGER);
        return val == null ? 0 : val;
    }
}