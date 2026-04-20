package me.beeliebub.tweaks.listeners;

import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

// Prevents creeper explosions from destroying blocks and endermen from picking up blocks
public class MobGriefListener implements Listener {

    // Allow creeper explosion damage but prevent block destruction
    @EventHandler
    public void onCreeperExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof Creeper) {
            event.blockList().clear();
        }
    }

    // Stop endermen from picking up or placing blocks
    @EventHandler
    public void onEndermanGrief(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Enderman) {
            event.setCancelled(true);
        }
    }

}
