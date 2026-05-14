package me.beeliebub.tweaks.enchantments.modes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

// PDC-backed area-mode storage shared by Tunneller and Efficacy.
//
// Each quality-enchanted tool has a maximum area radius set by its tier (uncommon = 2 / 5x5,
// rare = 3 / 7x7, epic = 4 / 9x9, legendary = 5 / 11x11). Mode lets the player downshift to a
// smaller area without losing the enchantment — e.g. a legendary tunneller can cycle through
// 3x3, 5x5, 7x7, 9x9, 11x11.
//
// Cycling is sneak + right-click on the held tool; the mode read by Tunneller/Efficacy is the PDC
// value clamped to [1, maxRadius]. Absence of the PDC value means "use the tool's maximum"
// (i.e. the default behavior before this feature shipped).
public final class EnchantMode {

    public static final String MODE_LORE_PREFIX = "Mode: ";

    private final NamespacedKey modeKey;

    public EnchantMode(JavaPlugin plugin) {
        this.modeKey = new NamespacedKey(plugin, "enchant_mode");
    }

    public NamespacedKey modeKey() {
        return modeKey;
    }

    // Returns the effective mode (clamped to [1, maxRadius]). If the PDC value is unset, returns
    // maxRadius so legacy tools (created before this feature) behave exactly as they did before.
    public int getMode(ItemStack tool, int maxRadius) {
        if (maxRadius <= 0) return 0;
        if (tool == null || !tool.hasItemMeta()) return maxRadius;
        Integer raw = tool.getItemMeta().getPersistentDataContainer().get(modeKey, PersistentDataType.INTEGER);
        if (raw == null) return maxRadius;
        if (raw < 1) return 1;
        if (raw > maxRadius) return maxRadius;
        return raw;
    }

    // Writes the new mode and refreshes the lore line.
    public void setMode(ItemStack tool, int mode) {
        if (tool == null || tool.isEmpty()) return;
        tool.editMeta(meta -> {
            meta.getPersistentDataContainer().set(modeKey, PersistentDataType.INTEGER, mode);
            applyModeLore(meta, mode);
        });
    }

    // Drops the mode from PDC and removes the lore line. Useful for tests / admin tooling.
    public void clearMode(ItemStack tool) {
        if (tool == null || tool.isEmpty()) return;
        tool.editMeta(meta -> {
            meta.getPersistentDataContainer().remove(modeKey);
            removeOurLore(meta);
        });
    }

    // Update or add the "Mode: NxN" lore line in place. Existing line is removed first so toggles
    // don't accumulate. Italic is explicitly disabled so the line renders in the same style as the
    // rest of the plugin's lore (matches NickCommand / ResourceRupee / XpBottle conventions).
    public void applyModeLore(ItemMeta meta, int radius) {
        int size = radius * 2 + 1;
        Component line = Component.text(MODE_LORE_PREFIX + size + "x" + size, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.removeIf(EnchantMode::isOurModeLore);
        lore.add(line);
        meta.lore(lore);
    }

    private void removeOurLore(ItemMeta meta) {
        if (meta.lore() == null) return;
        List<Component> lore = new ArrayList<>(meta.lore());
        if (lore.removeIf(EnchantMode::isOurModeLore)) {
            meta.lore(lore.isEmpty() ? null : lore);
        }
    }

    static boolean isOurModeLore(Component c) {
        String plain = PlainTextComponentSerializer.plainText().serialize(c);
        return plain.startsWith(MODE_LORE_PREFIX) && plain.matches("Mode: \\d+x\\d+");
    }
}
