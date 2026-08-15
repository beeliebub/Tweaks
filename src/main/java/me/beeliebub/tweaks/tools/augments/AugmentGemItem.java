package me.beeliebub.tweaks.tools.augments;

import io.papermc.paper.datacomponent.DataComponentTypes;
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

/** Factory and PDC recognizer for one-enchantment augment gems. */
public final class AugmentGemItem {

    private final Tweaks plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey enchantmentKey;
    private final NamespacedKey levelKey;

    public AugmentGemItem(Tweaks plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "augment_gem");
        this.enchantmentKey = new NamespacedKey(plugin, "augment_gem_enchantment");
        this.levelKey = new NamespacedKey(plugin, "augment_gem_level");
    }

    public ItemStack create(Enchantment enchantment, int level) {
        Material material = configuredMaterial();
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Messages.TOOLS.augmentGemName());
        var pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(enchantmentKey, PersistentDataType.STRING, enchantment.getKey().toString());
        pdc.set(levelKey, PersistentDataType.INTEGER, Math.max(1, level));
        stack.setItemMeta(meta);
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
            String raw = pdc.get(enchantmentKey, PersistentDataType.STRING);
            Integer level = pdc.get(levelKey, PersistentDataType.INTEGER);
            Enchantment enchantment = null;
            if (raw != null) {
                var key = NamespacedKey.fromString(raw);
                if (key != null) enchantment = io.papermc.paper.registry.RegistryAccess.registryAccess()
                        .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(key);
            }
            return enchantment == null || level == null || level <= 0 ? null : new GemData(enchantment, level);
        } catch (IllegalArgumentException malformed) {
            plugin.getLogger().warning("Malformed augment gem metadata was ignored.");
            return null;
        }
    }

    public boolean isGem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) return false;
        try {
            return stack.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
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

    public record GemData(Enchantment enchantment, int level) {}
}
