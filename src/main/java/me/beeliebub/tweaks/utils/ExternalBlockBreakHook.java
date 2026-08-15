package me.beeliebub.tweaks.utils;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Narrow bridge for feature listeners that break blocks without firing BlockBreakEvent. */
@FunctionalInterface
public interface ExternalBlockBreakHook {

    void onExternalBreak(Player player, Block block, Material originalMaterial);
}
