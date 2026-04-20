package me.beeliebub.tweaks.listeners;

import me.beeliebub.tweaks.Tweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Blocks End portal usage in configured worlds
public class EndPortalListener implements Listener {

    private final Set<String> disabledWorlds;

    // Load the list of worlds where End portals should be disabled from config
    public EndPortalListener(Tweaks plugin) {
        this.disabledWorlds = new HashSet<>();
        List<String> worlds = plugin.getConfig().getStringList("disabled-end-portal-worlds");
        for (String world : worlds) {
            disabledWorlds.add(world.toLowerCase());
        }
    }

    // Cancel the portal event if the player is in a disabled world
    @EventHandler
    public void onEnterPortal(PlayerPortalEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            String worldKey = event.getFrom().getWorld().getKey().asString().toLowerCase();

            if (disabledWorlds.contains(worldKey)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text("The End is disabled in this world!").color(NamedTextColor.RED));
            }
        }
    }
}