package me.beeliebub.tweaks.tools.durability;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import me.beeliebub.tweaks.utils.ExternalDurabilityHook;

/** Owns the anchored durability-tier projection for every damageable item. */
public final class DurabilityService implements ExternalDurabilityHook {

    private final Tweaks plugin;
    private final NamespacedKey tierKey;
    private final NamespacedKey multiplierKey;

    public DurabilityService(Tweaks plugin) {
        this.plugin = plugin;
        this.tierKey = new NamespacedKey(plugin, "durability_tier");
        this.multiplierKey = new NamespacedKey(plugin, "durability_multiplier");
    }

    public boolean ensureStamped(ItemStack item) {
        if (!isDamageable(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;
        var pdc = meta.getPersistentDataContainer();
        Integer tier;
        Double anchored;
        try {
            tier = pdc.get(tierKey, PersistentDataType.INTEGER);
            anchored = pdc.get(multiplierKey, PersistentDataType.DOUBLE);
        } catch (IllegalArgumentException malformed) {
            tier = null;
            anchored = null;
            plugin.getLogger().warning("Malformed durability metadata was replaced with a fresh tier stamp.");
        }
        boolean firstStamp = tier == null || anchored == null || !Double.isFinite(anchored) || anchored < 1.0;
        if (firstStamp) {
            tier = 0;
            anchored = liveMultiplier();
            pdc.set(tierKey, PersistentDataType.INTEGER, tier);
            pdc.set(multiplierKey, PersistentDataType.DOUBLE, anchored);
        }
        int vanillaMax = item.getType().getMaxDurability();
        int targetMax = maxDamageFor(vanillaMax, anchored, tier, liveTierStep());
        int currentMax = maxDamage(item, vanillaMax);
        if (firstStamp && currentMax > 0 && currentMax != targetMax) {
            double ratio = Math.max(0.0, Math.min(1.0,
                    (double) damageable.getDamage() / (double) currentMax));
            damageable.setDamage(Math.min(Math.max(0, targetMax - 1),
                    (int) Math.round(ratio * targetMax)));
        } else if (damageable.getDamage() >= targetMax) {
            damageable.setDamage(Math.max(0, targetMax - 1));
        }
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.MAX_DAMAGE, targetMax);
        return true;
    }

    public boolean repair(ItemStack item) {
        if (!ensureStamped(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;
        var pdc = meta.getPersistentDataContainer();
        int tier;
        try {
            tier = Math.max(0, pdc.getOrDefault(tierKey, PersistentDataType.INTEGER, 0));
        } catch (IllegalArgumentException malformed) {
            tier = 0;
            plugin.getLogger().warning("Malformed durability tier metadata was reset to tier 0.");
        }
        if (tier >= configuredMaxTier()) return false;
        tier++;
        pdc.set(tierKey, PersistentDataType.INTEGER, tier);
        damageable.setDamage(0);
        item.setItemMeta(meta);
        Double multiplier;
        try {
            multiplier = item.getItemMeta().getPersistentDataContainer().get(multiplierKey, PersistentDataType.DOUBLE);
        } catch (IllegalArgumentException malformed) {
            multiplier = liveMultiplier();
            plugin.getLogger().warning("Malformed durability multiplier metadata was ignored during repair.");
        }
        item.setData(DataComponentTypes.MAX_DAMAGE,
                maxDamageFor(item.getType().getMaxDurability(), multiplier == null ? liveMultiplier() : multiplier,
                        tier, liveTierStep()));
        return true;
    }

    public int tier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        try {
            Integer tier = item.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
            return tier == null ? 0 : Math.max(0, tier);
        } catch (IllegalArgumentException malformed) {
            plugin.getLogger().warning("Malformed durability tier metadata was ignored.");
            return 0;
        }
    }

    public int maxTier() {
        return configuredMaxTier();
    }

    public boolean isSpent(ItemStack item) {
        if (!isDamageable(item)) return false;
        ItemMeta meta = item.getItemMeta();
        return meta instanceof Damageable damageable
                && damageable.getDamage() >= Math.max(1, maxDamage(item, item.getType().getMaxDurability()) - 1);
    }

    @Override
    public boolean canTakeDamage(ItemStack item, int amount) {
        if (!isDamageable(item) || amount < 0) return false;
        ensureStamped(item);
        if (!plugin.getConfig().getBoolean("tools.never-break.enabled", true)) return true;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;
        long next = (long) damageable.getDamage() + amount;
        return next < maxDamage(item);
    }

    @Override
    public void applyDamage(Player player, EquipmentSlot slot, int amount) {
        if (player != null && slot != null && amount > 0) player.damageItemStack(slot, amount);
    }

    public boolean isDamageable(ItemStack item) {
        return item != null && !item.isEmpty() && item.getType().getMaxDurability() > 0
                && item.getItemMeta() instanceof Damageable;
    }

    public int maxDamage(ItemStack item) {
        return maxDamage(item, item == null ? 0 : item.getType().getMaxDurability());
    }

    public static int maxDamageFor(int vanillaMax, double multiplier, int tier) {
        return maxDamageFor(vanillaMax, multiplier, tier, 10.0);
    }

    public static int maxDamageFor(int vanillaMax, double multiplier, int tier, double tierStepPercent) {
        if (vanillaMax <= 0 || !Double.isFinite(multiplier) || multiplier <= 0) return 0;
        int safeTier = Math.max(0, tier);
        double factor = Math.max(0.0, 100.0 - tierStepPercent * safeTier) / 100.0;
        long value = Math.round(vanillaMax * multiplier * (safeTier == 0 ? 1.0 : factor));
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, value));
    }

    private int maxDamage(ItemStack item, int fallback) {
        try {
            Integer component = item == null ? null : item.getData(DataComponentTypes.MAX_DAMAGE);
            return component == null || component <= 0 ? fallback : component;
        } catch (RuntimeException malformed) {
            return fallback;
        }
    }

    private double liveMultiplier() {
        double value = plugin.getConfig().getDouble("tools.repair-kit.durability-multiplier", 3.0);
        return Double.isFinite(value) ? Math.max(1.0, Math.min(10.0, value)) : 3.0;
    }

    private int configuredMaxTier() {
        return Math.max(1, Math.min(20, plugin.getConfig().getInt("tools.repair-kit.max-tier", 9)));
    }

    private double liveTierStep() {
        double value = plugin.getConfig().getDouble("tools.repair-kit.tier-step-percent", 10.0);
        return Double.isFinite(value) ? Math.max(1.0, Math.min(50.0, value)) : 10.0;
    }
}
