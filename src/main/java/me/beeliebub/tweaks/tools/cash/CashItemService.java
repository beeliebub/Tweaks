package me.beeliebub.tweaks.tools.cash;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.BalanceMutationResult;
import me.beeliebub.tweaks.economy.EconomyManager;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.OptionalLong;
import java.util.logging.Level;

/** Decodes and converts datapack-issued cash items. */
public final class CashItemService {

    public enum Result { NOT_CASH, APPLIED, INVALID, OVERFLOW, REJECTED }

    private final Tweaks plugin;
    private final EconomyManager economy;

    public CashItemService(Tweaks plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public OptionalLong readValue(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) return OptionalLong.empty();
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = configuredKey();
        if (key == null) return OptionalLong.empty();
        Long value = firstIntegral(pdc, key);
        return value == null || value < 0 ? OptionalLong.empty() : OptionalLong.of(value);
    }

    public OptionalLong totalValue(ItemStack stack) {
        OptionalLong unitValue = readValue(stack);
        if (unitValue.isEmpty() || stack == null || stack.isEmpty()) return OptionalLong.empty();
        try {
            return OptionalLong.of(Math.multiplyExact(unitValue.getAsLong(), (long) stack.getAmount()));
        } catch (ArithmeticException e) {
            return OptionalLong.empty();
        }
    }

    public Result convert(ItemStack stack, java.util.UUID playerId) {
        if (stack == null || stack.isEmpty()) return Result.NOT_CASH;
        if (!plugin.getConfig().getBoolean("tools.cash-item.enabled", true)) return Result.NOT_CASH;
        if (!hasMarker(stack)) return Result.NOT_CASH;
        OptionalLong value = readValue(stack);
        if (value.isEmpty() || value.getAsLong() <= 0) {
            plugin.getLogger().warning("Cash item had a missing, non-integral, or negative configured value; item was left unchanged.");
            return Result.INVALID;
        }
        long total;
        try {
            total = Math.multiplyExact(value.getAsLong(), (long) stack.getAmount());
        } catch (ArithmeticException e) {
            plugin.getLogger().log(Level.SEVERE, "Cash stack value overflow; item was left unchanged.", e);
            return Result.OVERFLOW;
        }
        if (economy.addBalance(playerId, total) != BalanceMutationResult.APPLIED) {
            plugin.getLogger().severe("Cash conversion was rejected for player " + playerId
                    + " for amount " + total + "; item was left unchanged.");
            return Result.REJECTED;
        }
        stack.setAmount(0);
        return Result.APPLIED;
    }

    private boolean hasMarker(ItemStack stack) {
        if (!stack.hasItemMeta()) return false;
        NamespacedKey key = configuredKey();
        return key != null && stack.getItemMeta().getPersistentDataContainer().getKeys().stream()
                .anyMatch(candidate -> candidate.equals(key));
    }

    private NamespacedKey configuredKey() {
        String raw = plugin.getConfig().getString("tools.cash-item.pdc-key", "cash");
        if (raw == null || raw.isBlank()) return null;
        try {
            return new NamespacedKey(plugin, raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Long firstIntegral(PersistentDataContainer pdc, NamespacedKey key) {
        try {
            Byte byteValue = pdc.get(key, PersistentDataType.BYTE);
            if (byteValue != null) return byteValue.longValue();
            Short shortValue = pdc.get(key, PersistentDataType.SHORT);
            if (shortValue != null) return shortValue.longValue();
            Integer integerValue = pdc.get(key, PersistentDataType.INTEGER);
            if (integerValue != null) return integerValue.longValue();
            return pdc.get(key, PersistentDataType.LONG);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
