package me.beeliebub.tweaks.tools.durability;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Factory and PDC recognizer for repair kits. */
public final class RepairKitItem {

    private final Tweaks plugin;
    private final NamespacedKey markerKey;

    public RepairKitItem(Tweaks plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "repair_kit");
    }

    public ItemStack create(int amount) {
        Material material = configuredMaterial();
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Messages.TOOLS.repairKitName());
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        String model = plugin.getConfig().getString("tools.repair-kit.item-model", "jass:repair_kit");
        if (model != null && Key.key(model) != null) stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(model));
        stack.setData(DataComponentTypes.RARITY, ItemRarity.UNCOMMON);
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 64);
        return stack;
    }

    public boolean isKit(ItemStack stack) {
        if (!enabled()) return false;
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("tools.repair-kit.enabled", true);
    }

    public Material configuredMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("tools.repair-kit.material", "PAPER"));
        return material == null || material.isAir() || !material.isItem() || material.isBlock()
                ? Material.PAPER : material;
    }
}
