package me.beeliebub.tweaks.blocklog;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

/**
 * Tier: 5 - leaf features
 */
public final class BlockLogBootstrap {

    private BlockLogBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        // BlockLog - per-chunk PDC chest interaction logging
        BlockLogSystem blockLogSystem = new BlockLogSystem(plugin);
        plugin.getCommand("logs").setExecutor(blockLogSystem);
        plugin.getServer().getPluginManager().registerEvents(blockLogSystem, plugin);
    }
}
