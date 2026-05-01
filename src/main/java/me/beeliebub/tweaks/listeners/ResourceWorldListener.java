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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;

/**
 * Consolidates gameplay restrictions specific to the jass:resource world.
 */
public class ResourceWorldListener implements Listener {

    private static final String RESOURCE_WORLD_KEY = "jass:resource";
    private static final String DESTINATION_WARP = "newspawn";

    private final Tweaks plugin;
    private final StorageManager storageManager;

    public ResourceWorldListener(Tweaks plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
    }

    /**
     * Ejects any player whose login location is inside the resource world to the "newspawn" warp.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!RESOURCE_WORLD_KEY.equals(player.getWorld().getKey().asString())) return;

        Optional<Point> warp = storageManager.getWarp(DESTINATION_WARP);
        if (warp.isEmpty()) {
            plugin.getLogger().warning("Warp '" + DESTINATION_WARP + "' is not set; cannot eject "
                    + player.getName() + " from " + RESOURCE_WORLD_KEY + ".");
            return;
        }

        Optional<Location> destination = warp.get().toLocation();
        if (destination.isEmpty()) {
            plugin.getLogger().warning("Warp '" + DESTINATION_WARP + "' references an unloaded world; cannot eject "
                    + player.getName() + " from " + RESOURCE_WORLD_KEY + ".");
            return;
        }

        player.teleportAsync(destination.get());
        player.sendMessage(Component.text("For your safety, returning you to the main survival world!",
                NamedTextColor.YELLOW));
    }

    /**
     * Prevents opening ender chests in the resource world.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEnderChestOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() != InventoryType.ENDER_CHEST) return;
        
        Player player = (Player) event.getPlayer();
        if (RESOURCE_WORLD_KEY.equals(player.getWorld().getKey().asString())) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Ender chests are disabled in the resource world!", NamedTextColor.RED));
        }
    }
}
