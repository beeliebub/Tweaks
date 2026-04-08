package me.beeliebub.tweaks;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;

public class EndPortalListener implements Listener {

    private final Tweaks plugin;

    public EndPortalListener(Tweaks plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onEnterPortal(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            List<String> disabledWorlds = plugin.getConfig().getStringList("disabled-end-portal-worlds");
            if (disabledWorlds.contains(event.getPlayer().getWorld().getName())) {
                event.setCancelled(true);
            }
        }
    }
}
