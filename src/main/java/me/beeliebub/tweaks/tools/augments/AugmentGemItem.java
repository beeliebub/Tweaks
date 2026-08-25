package me.beeliebub.tweaks.tools.augments;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Factory and PDC recognizer for one-enchantment augment gems. */
public final class AugmentGemItem {

    public static final int MAX_CURSE_RIDERS = 64;
    private final Tweaks plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey cursesKey;

    public AugmentGemItem(Tweaks plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "augment_gem");
        this.cursesKey = new NamespacedKey(plugin, "augment_gem_curses");
    }

    public ItemStack create(Enchantment enchantment, int level) {
        return create(enchantment, level, List.of());
    }

    public ItemStack create(Enchantment enchantment, int level, List<CurseRider> curses) {
        if (enchantment == null || level <= 0 || level > 255) {
            plugin.getLogger().warning("Invalid augment gem primary enchantment input was rejected.");
            return null;
        }
        Material material = configuredMaterial();
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        List<CurseRider> safeCurses = curses == null ? List.of() : curses;
        if (safeCurses.size() > MAX_CURSE_RIDERS) {
            plugin.getLogger().warning("Oversized augment curse input was rejected.");
            return null;
        }
        Set<Enchantment> knownCurses = AugmentService.resolveCurseEnchantments();
        Set<Enchantment> seenCurses = new HashSet<>();
        List<CurseRider> validCurses = new ArrayList<>();
        for (CurseRider curse : safeCurses) {
            if (curse == null || curse.enchantment() == null || curse.level() <= 0 || curse.level() > 255
                    || !knownCurses.contains(curse.enchantment())) continue;
            if (!seenCurses.add(curse.enchantment())) {
                plugin.getLogger().warning("Duplicate augment gem curse input was rejected.");
                return null;
            }
            validCurses.add(curse);
        }
        for (CurseRider curse : validCurses) {
            if (curse.enchantment().getKey().equals(enchantment.getKey())) {
                plugin.getLogger().warning("Augment gem creation rejected a primary and rider enchantment collision.");
                return null;
            }
        }
        Map<Enchantment, Integer> storedEnchantments = new HashMap<>();
        storedEnchantments.put(enchantment, level);
        for (CurseRider curse : validCurses) {
            storedEnchantments.put(curse.enchantment(), curse.level());
        }
        var pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        List<String> encodedCurses = new ArrayList<>();
        for (CurseRider curse : validCurses) {
            encodedCurses.add(encodeCurse(curse));
        }
        if (!encodedCurses.isEmpty()) {
            pdc.set(cursesKey, PersistentDataType.LIST.strings(), encodedCurses);
        }
        stack.setItemMeta(meta);
        stack.setData(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantments.itemEnchantments(storedEnchantments));
        stack.setData(DataComponentTypes.ITEM_NAME, Messages.TOOLS.augmentGemName());
        String model = plugin.getConfig().getString("tools.augments.gem-item-model", "jass:augment_gem");
        try { stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(model)); }
        catch (RuntimeException ignored) { plugin.getLogger().warning("Invalid augment gem item model: " + model); }
        stack.setData(DataComponentTypes.RARITY, ItemRarity.RARE);
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 64);
        stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public GemData read(ItemStack stack) {
        if (!isGem(stack)) return null;
        try {
            var pdc = stack.getItemMeta().getPersistentDataContainer();
            List<CurseRider> curses = new ArrayList<>();
            List<String> encodedCurses;
            try {
                encodedCurses = pdc.getOrDefault(cursesKey, PersistentDataType.LIST.strings(), List.of());
            } catch (IllegalArgumentException malformedList) {
                plugin.getLogger().warning("Malformed augment gem curse list was ignored.");
                return null;
            }
            if (encodedCurses.size() > MAX_CURSE_RIDERS) {
                plugin.getLogger().warning("Oversized augment gem curse list was ignored.");
                return null;
            }
            for (String encoded : encodedCurses) {
                CurseRider curse;
                try {
                    curse = decodeCurse(encoded);
                } catch (RuntimeException malformed) {
                    warnMalformedCurse();
                    curse = null;
                }
                if (curse != null) curses.add(curse);
            }
            ItemEnchantments stored = stack.getData(DataComponentTypes.STORED_ENCHANTMENTS);
            if (stored == null || stored.enchantments().isEmpty()) {
                plugin.getLogger().warning("Augment gem stored enchantments were missing or empty.");
                return null;
            }
            Set<Enchantment> seenRiders = new HashSet<>();
            for (CurseRider curse : curses) {
                if (!seenRiders.add(curse.enchantment())) {
                    plugin.getLogger().warning("Duplicate augment gem curse metadata was ignored.");
                    return null;
                }
                Integer storedLevel = stored.enchantments().get(curse.enchantment());
                if (storedLevel == null || storedLevel != curse.level()) {
                    plugin.getLogger().warning("Augment gem curse metadata did not match stored enchantments.");
                    return null;
                }
            }
            var nonRiderEntries = new HashMap<>(stored.enchantments());
            for (CurseRider curse : curses) nonRiderEntries.remove(curse.enchantment());
            if (nonRiderEntries.size() != 1) {
                plugin.getLogger().warning("Augment gem stored enchantments did not contain one primary.");
                return null;
            }
            var primary = nonRiderEntries.entrySet().iterator().next();
            if (primary.getKey() == null || primary.getValue() == null
                    || primary.getValue() <= 0 || primary.getValue() > 255) {
                plugin.getLogger().warning("Augment gem primary enchantment level was invalid.");
                return null;
            }
            return new GemData(primary.getKey(), primary.getValue(), List.copyOf(curses));
        } catch (IllegalArgumentException malformed) {
            plugin.getLogger().warning("Malformed augment gem metadata was ignored.");
            return null;
        }
    }

    public boolean isGem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) return false;
        try {
            Byte marker = stack.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
            return marker != null && marker == (byte) 1;
        } catch (IllegalArgumentException malformed) {
            plugin.getLogger().warning("Malformed augment gem marker was ignored.");
            return false;
        }
    }

    public Material configuredMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("tools.augments.gem-material", "AMETHYST_SHARD"));
        return material == null || material.isAir() || !material.isItem() || material.isBlock()
                ? Material.AMETHYST_SHARD : material;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("tools.augments.enabled", true);
    }

    private String encodeCurse(CurseRider curse) {
        return "v1|" + curse.enchantment().getKey() + "|" + curse.level();
    }

    private CurseRider decodeCurse(String encoded) {
        if (encoded == null) {
            warnMalformedCurse();
            return null;
        }
        String[] fields = encoded.split("\\|", -1);
        if (fields.length != 3 || !fields[0].equals("v1")) {
            warnMalformedCurse();
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(fields[1]);
        if (key == null) {
            warnMalformedCurse();
            return null;
        }
        int level;
        try {
            level = Integer.parseInt(fields[2]);
        } catch (NumberFormatException malformed) {
            warnMalformedCurse();
            return null;
        }
        if (level <= 0) {
            warnMalformedCurse();
            return null;
        }
        Enchantment enchantment = io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(key);
        if (enchantment == null || !AugmentService.resolveCurseEnchantments().contains(enchantment)) {
            warnMalformedCurse();
            return null;
        }
        return new CurseRider(enchantment, level);
    }

    private void warnMalformedCurse() {
        plugin.getLogger().warning("Malformed augment gem curse metadata was ignored.");
    }

    public record CurseRider(Enchantment enchantment, int level) {}

    public record GemData(Enchantment enchantment, int level, List<CurseRider> curses) {}
}
