package me.beeliebub.tweaks.playeradmin;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

/**
 * Tier:      3 - player-facing infra
 * Publishes: playerAdminManager (read by tab.TabBootstrap at the same tier, wired back
 *            via setTabManager)
 */
public final class PlayerAdminBootstrap {

    private PlayerAdminBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        PlayerAdminManager playerAdminManager = new PlayerAdminManager(plugin);
        services.setPlayerAdminManager(playerAdminManager);

        PlayerAdminCommand playerAdminCommand = new PlayerAdminCommand(playerAdminManager);
        for (String label : new String[]{"survival", "creative", "nv", "fly", "afk", "nick"}) {
            plugin.getCommand(label).setExecutor(playerAdminCommand);
        }
        plugin.getServer().getPluginManager().registerEvents(playerAdminManager, plugin);
        playerAdminManager.start();
    }
}
