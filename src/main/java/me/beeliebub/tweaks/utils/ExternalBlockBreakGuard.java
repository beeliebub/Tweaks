package me.beeliebub.tweaks.utils;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Gate consulted before an enchantment mutates a collateral block without a Bukkit break event. */
@FunctionalInterface
public interface ExternalBlockBreakGuard {

    boolean canBreak(Player player, Block block, Material material);
}
