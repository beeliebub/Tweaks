package me.beeliebub.tweaks;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

public class EndPortalListener implements Listener {
    
    @EventHandler
    public void onEnterPortal(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            if (event.getPlayer().getWorld().getName().equals("Archive")) {
                event.setCancelled(true);
            }
        }
    }
}
