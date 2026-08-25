package me.beeliebub.tweaks.utils;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Bridge for area enchantments that apply item damage outside a normal item-damage event. */
public interface ExternalDurabilityHook {

    boolean canTakeDamage(ItemStack item, int amount);

    void applyDamage(Player player, EquipmentSlot slot, int amount);
}
