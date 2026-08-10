package me.beeliebub.tweaks.skyblock.command;

import org.bukkit.entity.Player;

/** Small command-handler seam kept for command and UI integration tests. */
@FunctionalInterface
public interface IslandSubcommand {
    boolean execute(Player player, String label, String[] args);
}
