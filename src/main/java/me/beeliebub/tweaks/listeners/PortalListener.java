package me.beeliebub.tweaks.listeners;

import me.beeliebub.tweaks.Tweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.Set;

// Cancels portal usage for two cases:
//   - END_PORTAL teleports originating from any world listed under `disabled-end-portal-worlds`
//     in config.yml (admin-configurable; default includes jass:archive and jass:resource).
//   - NETHER_PORTAL teleports originating from the resource world. Hardcoded — the resource
//     world is a single-purpose gathering dimension and should never bridge to the Nether.
public class PortalListener implements Listener {

    private static final String RESOURCE_WORLD_KEY = "jass:resource";
    private static final String RESOURCE_NETHER_WORLD_KEY = "jass:resource_nether";

    private final Set<String> disabledEndWorlds;

    public PortalListener(Tweaks plugin) {
        this.disabledEndWorlds = new HashSet<>();
        for (String world : plugin.getConfig().getStringList("disabled-end-portal-worlds")) {
            disabledEndWorlds.add(world.toLowerCase());
        }
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        String worldKey = event.getFrom().getWorld().getKey().asString().toLowerCase();
        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL && disabledEndWorlds.contains(worldKey)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("The End is disabled in this world!", NamedTextColor.RED));
        } else if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (RESOURCE_WORLD_KEY.equals(worldKey) || RESOURCE_NETHER_WORLD_KEY.equals(worldKey)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text("Nether portals do not work in this world.", NamedTextColor.RED));
            }
        }
    }
}
