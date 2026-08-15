package me.beeliebub.tweaks.core;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

/**
 * Tier:  3 - player-facing infra
 * Reads: resourceHuntItems (tier 2), worldProfileTable (tier 1) - both for ConfigCommand
 */
public final class CoreBootstrap {

    private CoreBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        HelpSystem helpSystem = new HelpSystem(plugin.getLogger());
        plugin.getCommand("help").setExecutor(helpSystem);
        plugin.getCommand("help").setTabCompleter(helpSystem);
        plugin.getServer().getPluginManager().registerEvents(helpSystem, plugin);

        ConfigCommand configCommand = new ConfigCommand(plugin, services.resourceHuntItems(),
                services.worldProfileTable(), services.consoleEventLog()::updateCachedBoolean);
        services.setConfigCommand(configCommand);
        plugin.getCommand("tconfig").setExecutor(configCommand);
        plugin.getCommand("tconfig").setTabCompleter(configCommand);
    }
}
