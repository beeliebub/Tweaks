package me.beeliebub.tweaks.tools.durability;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import me.beeliebub.tweaks.utils.ExternalDurabilityHook;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Owns the anchored durability-tier projection for augmented or already-stamped items. */
public final class DurabilityService implements ExternalDurabilityHook {

    private static final int MIN_POOL = 8;
    private static final int MAX_OWNED_LORE_LINES = 512;

    private final Tweaks plugin;
    private final NamespacedKey tierKey;
    private final NamespacedKey multiplierKey;
    private final NamespacedKey ownedLoreKey;

    public DurabilityService(Tweaks plugin) {
        this.plugin = plugin;
        this.tierKey = new NamespacedKey(plugin, "durability_tier");
        this.multiplierKey = new NamespacedKey(plugin, "durability_multiplier");
        this.ownedLoreKey = new NamespacedKey(plugin, "durability_owned_lore");
    }

    public boolean ensureStamped(ItemStack item) {
        return ensureStamped(item, false);
    }

    /** Refreshes the owned marker after another renderer has appended its own lore block. */
    public void refreshLoreTail(ItemStack item) {
        ensureStamped(item, true);
    }

    private boolean ensureStamped(ItemStack item, boolean refreshLoreTail) {
        if (!isDamageable(item)) return false;
        if (!AugmentLedger.hasLedger(item) && !alreadyStamped(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
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
        boolean pdcChanged = firstStamp;
        if (firstStamp) {
            tier = 0;
            anchored = liveMultiplier();
            pdc.set(tierKey, PersistentDataType.INTEGER, tier);
            pdc.set(multiplierKey, PersistentDataType.DOUBLE, anchored);
        }

        int vanillaMax = item.getType().getMaxDurability();
        int projectedTier = Math.min(Math.max(0, tier), configuredMaxTier());
        int targetMax = maxDamageFor(vanillaMax, anchored, projectedTier, effectiveTierStep());
        Integer currentMaxComponent = maxDamageComponent(item);
        Integer currentDamageComponent = damageComponent(item);
        int currentMax = currentMaxComponent == null || currentMaxComponent <= 0
                ? vanillaMax : currentMaxComponent;
        int currentDamage = currentDamageComponent == null
                ? damage(item, damageable) : Math.max(0, currentDamageComponent);
        int targetDamage = currentDamage;
        if (firstStamp && currentMax > 0 && currentMax != targetMax) {
            double ratio = Math.max(0.0, Math.min(1.0,
                    (double) currentDamage / (double) currentMax));
            targetDamage = (int) Math.round(ratio * targetMax);
        }
        boolean neverBreak = plugin.getConfig().getBoolean("tools.never-break.enabled", true);
        int maximumAllowed = Math.max(0, targetMax - (neverBreak ? 1 : 0));
        targetDamage = Math.max(0, Math.min(maximumAllowed, targetDamage));

        MarkerState markerState = markerState(targetDamage, targetMax, projectedTier);
        if (updateMarker(meta, markerState, refreshLoreTail)) pdcChanged = true;

        if (pdcChanged) {
            damageable.setDamage(targetDamage);
            item.setItemMeta(meta);
            item.setData(DataComponentTypes.MAX_DAMAGE, targetMax);
            item.setData(DataComponentTypes.DAMAGE, targetDamage);
        } else {
            if (!Integer.valueOf(targetMax).equals(currentMaxComponent)) {
                item.setData(DataComponentTypes.MAX_DAMAGE, targetMax);
            }
            if (!Integer.valueOf(targetDamage).equals(currentDamageComponent)) {
                item.setData(DataComponentTypes.DAMAGE, targetDamage);
            }
        }
        return true;
    }

    /**
     * Stamps the item if needed, then clears all accumulated damage without advancing the repair
     * tier. Applied when a plain tool first becomes augmented so it arrives at the full projected
     * durability pool instead of carrying the wear it built up before it had a ledger. Any depleted
     * marker line is removed in the same write. Returns {@code false} when there was nothing to do
     * (undamaged, no marker) or the item cannot carry the projection.
     */
    public boolean restoreFullDurability(ItemStack item) {
        if (!ensureStamped(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;
        boolean markerCleared = updateMarker(meta, MarkerState.NONE, false);
        if (!markerCleared && damage(item, damageable) <= 0) return false;

        int targetMax = maxDamage(item);
        damageable.setDamage(0);
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.MAX_DAMAGE, targetMax);
        item.setData(DataComponentTypes.DAMAGE, 0);
        return true;
    }

    public boolean repair(ItemStack item) {
        if (!ensureStamped(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;
        if (damage(item, damageable) <= 0) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int tier;
        try {
            Integer storedTier = pdc.getOrDefault(tierKey, PersistentDataType.INTEGER, 0);
            tier = storedTier == null ? 0 : Math.max(0, storedTier);
        } catch (IllegalArgumentException malformed) {
            tier = 0;
            plugin.getLogger().warning("Malformed durability tier metadata was reset to tier 0.");
        }
        if (tier >= configuredMaxTier()) return false;

        tier++;
        pdc.set(tierKey, PersistentDataType.INTEGER, tier);
        Double multiplier;
        try {
            multiplier = pdc.get(multiplierKey, PersistentDataType.DOUBLE);
        } catch (IllegalArgumentException malformed) {
            multiplier = liveMultiplier();
            plugin.getLogger().warning("Malformed durability multiplier metadata was ignored during repair.");
        }
        if (multiplier == null || !Double.isFinite(multiplier) || multiplier < 1.0) {
            multiplier = liveMultiplier();
        }

        int projectedTier = Math.min(Math.max(0, tier), configuredMaxTier());
        int targetMax = maxDamageFor(item.getType().getMaxDurability(), multiplier,
                projectedTier, effectiveTierStep());
        updateMarker(meta, MarkerState.NONE, false);
        damageable.setDamage(0);
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.MAX_DAMAGE, targetMax);
        item.setData(DataComponentTypes.DAMAGE, 0);
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

    public boolean hasStamp(ItemStack item) {
        return alreadyStamped(item);
    }

    public int maxTier() {
        return configuredMaxTier();
    }

    public boolean isSpent(ItemStack item) {
        if (!isDamageable(item)) return false;
        // The never-break floor belongs to augmented or already-participating items; a raw
        // damage comparison must not freeze a plain item at its vanilla break point.
        if (!AugmentLedger.hasLedger(item) && !alreadyStamped(item)) return false;
        ItemMeta meta = item.getItemMeta();
        return meta instanceof Damageable damageable
                && damage(item, damageable) >= depletedThreshold(item);
    }

    public int depletedThreshold(ItemStack item) {
        return maxDamage(item) - 1;
    }

    @Override
    public boolean canTakeDamage(ItemStack item, int amount) {
        if (!isDamageable(item) || amount < 0) return false;
        // Unstamped, unaugmented items use vanilla collateral durability and must not be
        // converted into custom-durability items merely because an area effect touched them.
        if (!AugmentLedger.hasLedger(item) && !alreadyStamped(item)) return true;
        ensureStamped(item);
        if (!plugin.getConfig().getBoolean("tools.never-break.enabled", true)) return true;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;
        long next = (long) damage(item, damageable) + amount;
        return next <= depletedThreshold(item);
    }

    /** Reads the data-component damage value, falling back to the item-meta view. */
    public int damage(ItemStack item) {
        if (!isDamageable(item)) return 0;
        ItemMeta meta = item.getItemMeta();
        return meta instanceof Damageable damageable ? damage(item, damageable) : 0;
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
        return (int) Math.max(MIN_POOL, Math.min(Integer.MAX_VALUE, value));
    }

    /** Returns the clamped tier-step percentage used by the projection. */
    double effectiveTierStep() {
        return Math.min(liveTierStep(), 90.0 / configuredMaxTier());
    }

    /** Logs a configuration warning outside the durability hot path when the step is clamped. */
    public void warnIfTierStepClamped() {
        double live = liveTierStep();
        double effective = effectiveTierStep();
        if (Double.compare(live, effective) != 0) {
            plugin.getLogger().warning("Repair-kit tier step is clamped from " + live + "% to "
                    + effective + "% for max tier " + configuredMaxTier() + ".");
        }
    }

    private boolean updateMarker(ItemMeta meta, MarkerState state, boolean refreshLoreTail) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        List<String> owned = readOwnedLore(pdc);
        if (owned == null) return false;
        List<String> desired = state == MarkerState.NONE
                ? List.of() : List.of(markerLine(state).toString());
        boolean changed = !owned.equals(desired);
        if (!changed && refreshLoreTail && !desired.isEmpty()) {
            List<Component> lore = meta.hasLore() && meta.lore() != null
                    ? meta.lore() : List.of();
            changed = lore.isEmpty() || !desired.get(0).equals(lore.get(lore.size() - 1).toString());
        }
        if (!changed) return false;

        List<Component> existing = meta.hasLore() && meta.lore() != null
                ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        removeOwnedBlock(existing, owned);
        if (state != MarkerState.NONE) existing.add(markerLine(state));
        meta.lore(existing);
        pdc.set(ownedLoreKey, PersistentDataType.LIST.strings(), desired);
        return true;
    }

    private MarkerState markerState(int damage, int maxDamage, int tier) {
        if (damage < maxDamage - 1) return MarkerState.NONE;
        return tier >= configuredMaxTier() ? MarkerState.DEPLETED_TERMINAL
                : MarkerState.DEPLETED_REPAIRABLE;
    }

    private Component markerLine(MarkerState state) {
        return state == MarkerState.DEPLETED_TERMINAL
                ? Messages.TOOLS.durabilityDepletedTerminal()
                : Messages.TOOLS.durabilityDepletedRepairable();
    }

    private List<String> readOwnedLore(PersistentDataContainer pdc) {
        if (!pdc.getKeys().contains(ownedLoreKey)) return List.of();
        try {
            if (!pdc.has(ownedLoreKey, PersistentDataType.LIST.strings())) {
                plugin.getLogger().warning("Invalid durability lore ownership metadata; existing lore was preserved.");
                return null;
            }
            List<String> owned = pdc.get(ownedLoreKey, PersistentDataType.LIST.strings());
            if (owned == null || owned.size() > MAX_OWNED_LORE_LINES
                    || owned.stream().anyMatch(value -> value == null)) {
                plugin.getLogger().warning("Oversized or malformed durability lore ownership metadata; existing lore was preserved.");
                return null;
            }
            return List.copyOf(owned);
        } catch (IllegalArgumentException malformed) {
            plugin.getLogger().warning("Invalid durability lore ownership metadata; existing lore was preserved.");
            return null;
        }
    }

    private static void removeOwnedBlock(List<Component> existing, List<String> owned) {
        if (owned == null || owned.isEmpty() || owned.size() > existing.size()) return;
        for (int start = existing.size() - owned.size(); start >= 0; start--) {
            boolean matches = true;
            for (int offset = 0; offset < owned.size(); offset++) {
                if (!owned.get(offset).equals(existing.get(start + offset).toString())) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            for (int offset = 0; offset < owned.size(); offset++) existing.remove(start);
            return;
        }
    }

    private int maxDamage(ItemStack item, int fallback) {
        Integer component = maxDamageComponent(item);
        return component == null || component <= 0 ? fallback : component;
    }

    private Integer maxDamageComponent(ItemStack item) {
        try {
            return item == null ? null : item.getData(DataComponentTypes.MAX_DAMAGE);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private int damage(ItemStack item, Damageable fallback) {
        Integer component = damageComponent(item);
        return component == null ? Math.max(0, fallback.getDamage()) : Math.max(0, component);
    }

    private Integer damageComponent(ItemStack item) {
        try {
            return item == null ? null : item.getData(DataComponentTypes.DAMAGE);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private boolean alreadyStamped(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return false;
        var keys = item.getItemMeta().getPersistentDataContainer().getKeys();
        return keys.contains(tierKey) || keys.contains(multiplierKey);
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

    private enum MarkerState {
        NONE,
        DEPLETED_REPAIRABLE,
        DEPLETED_TERMINAL
    }
}
