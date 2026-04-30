package me.beeliebub.tweaks.listeners;

import me.beeliebub.tweaks.Point;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.managers.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;

// Ejects any player whose login location is inside the resource world to the "newspawn" warp.
// The teleport runs at MONITOR priority on PlayerJoinEvent so other join handlers (notably
// SeparatorListener, which loads the resource-world profile inventory) finish first; the
// follow-up world change then lets SeparatorListener swap to the destination world's profile
// via PlayerChangedWorldEvent. teleportAsync handles chunk loading at the destination.
public class ResourceWorldJoinListener implements Listener {

    private static final String DISABLED_WORLD_KEY = "jass:resource";
    private static final String DESTINATION_WARP = "newspawn";

    private final Tweaks plugin;
    private final StorageManager storageManager;

    public ResourceWorldJoinListener(Tweaks plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!DISABLED_WORLD_KEY.equals(player.getWorld().getKey().asString())) return;

        Optional<Point> warp = storageManager.getWarp(DESTINATION_WARP);
        if (warp.isEmpty()) {
            plugin.getLogger().warning("Warp '" + DESTINATION_WARP + "' is not set; cannot eject "
                    + player.getName() + " from " + DISABLED_WORLD_KEY + ".");
            return;
        }

        Optional<Location> destination = warp.get().toLocation();
        if (destination.isEmpty()) {
            plugin.getLogger().warning("Warp '" + DESTINATION_WARP + "' references an unloaded world; cannot eject "
                    + player.getName() + " from " + DISABLED_WORLD_KEY + ".");
            return;
        }

        player.teleportAsync(destination.get());
        player.sendMessage(Component.text("For your safety, returning you to the main survival world!",
                NamedTextColor.YELLOW));
    }
}
