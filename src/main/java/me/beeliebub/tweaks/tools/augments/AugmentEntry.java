package me.beeliebub.tweaks.tools.augments;

import org.bukkit.NamespacedKey;

/** One versioned augment ledger entry. */
public record AugmentEntry(NamespacedKey enchantmentKey, int level, boolean active) {}
