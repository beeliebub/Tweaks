package me.beeliebub.tweaks.listeners;

import me.beeliebub.tweaks.Point;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.managers.StorageManager;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
 * Consolidates gameplay restrictions specific to the resource worlds.
 */
public class ResourceWorldListener implements Listener {

    private static final String RESOURCE_NETHER_WORLD_KEY = "jass:resource_nether";
    private static final String DESTINATION_WARP = "newspawn";

    private final Tweaks plugin;
    private final StorageManager storageManager;

    public ResourceWorldListener(Tweaks plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
    }

    /**
     * Ejects any player whose login location is inside a resource world to the "newspawn" warp.
     * Uses a 1-tick delay to ensure the teleport succeeds after login processing.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!ResourceHunt.isResourceWorld(player.getWorld().getKey().asString())) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (!ResourceHunt.isResourceWorld(player.getWorld().getKey().asString())) return;

            Optional<Point> warp = storageManager.getWarp(DESTINATION_WARP);
            if (warp.isEmpty()) {
                plugin.getLogger().warning("Warp '" + DESTINATION_WARP + "' is not set; cannot eject "
                        + player.getName() + " from resource world.");
                return;
            }

            Optional<Location> destination = warp.get().toLocation();
            if (destination.isEmpty()) {
                plugin.getLogger().warning("Warp '" + DESTINATION_WARP + "' references an unloaded world; cannot eject "
                        + player.getName() + " from resource world.");
                return;
            }

            player.teleportAsync(destination.get());
            player.sendMessage(Component.text("For your safety, returning you to the main survival world!",
                    NamedTextColor.YELLOW));
        });
    }

    /**
     * Prevents teleporting to the Nether roof in the resource world by redirecting to a safe bedrock platform.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (!RESOURCE_NETHER_WORLD_KEY.equals(to.getWorld().getKey().asString())) return;

        if (to.getY() >= 127) {
            Location safe = ResourceHunt.createBedrockPlatform(to.getWorld(), to.getBlockX(), to.getBlockZ());
            safe.setYaw(to.getYaw());
            safe.setPitch(to.getPitch());
            event.setTo(safe);
            event.getPlayer().sendMessage(Component.text("The Nether roof is off-limits; redirecting you to a safe platform.", NamedTextColor.GOLD));
        }
    }

    /**
     * Prevents opening ender chests in resource worlds.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEnderChestOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() != InventoryType.ENDER_CHEST) return;

        Player player = (Player) event.getPlayer();
        if (ResourceHunt.isResourceWorld(player.getWorld().getKey().asString())) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Ender chests are disabled in resource worlds!", NamedTextColor.RED));
        }
    }
}
