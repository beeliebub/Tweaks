package me.beeliebub.tweaks.teleport;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Late-bound access policy for teleport operations involving the Skyblock world. */
@FunctionalInterface
public interface SkyblockTeleportGate {
    boolean allowed(Player player, Location destination);

    default boolean allowedCommand(Player player, String label, String[] args) {
        return true;
    }
}
