package me.beeliebub.tweaks.teleport;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

/**
 * Tier:  3 - player-facing infra
 * Reads: storageManager (tier 1), resourceHuntItems (tier 2)
 */
public final class TeleportBootstrap {

    private TeleportBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        // Teleportation: /home /sethome /delhome /homes /warp /setwarp /delwarp /warps
        // /spawn /back /tpa /tpahere /tpaccept /tpdeny - all routed through one manager.
        // max-homes is read live at the /sethome check site, not cached here.
        TeleportCommandManager teleportManager = new TeleportCommandManager(
                plugin, services.storageManager(), services.resourceHuntItems());
        for (String label : new String[]{"home", "sethome", "delhome", "homes",
                                          "warp", "setwarp", "delwarp", "warps",
                                          "spawn", "back",
                                          "tpa", "tpahere", "tpaccept", "tpdeny"}) {
            plugin.getCommand(label).setExecutor(teleportManager);
            plugin.getCommand(label).setTabCompleter(teleportManager);
        }
        plugin.getServer().getPluginManager().registerEvents(teleportManager, plugin);
    }
}
