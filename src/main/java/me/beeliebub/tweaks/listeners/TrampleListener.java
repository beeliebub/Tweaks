package me.beeliebub.tweaks.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class TrampleListener implements Listener {

    @EventHandler
    public void onPlayerTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            Block block = event.getClickedBlock();

            if (block != null && block.getType() == Material.FARMLAND) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevents all non-player entities (mobs, animals, etc.) from trampling farmland.
     */
    @EventHandler
    public void onEntityTrample(EntityInteractEvent event) {
        Block block = event.getBlock();

        // Check if the block being interacted with is Farmland
        if (block != null && block.getType() == Material.FARMLAND) {
            event.setCancelled(true);
        }
    }
}