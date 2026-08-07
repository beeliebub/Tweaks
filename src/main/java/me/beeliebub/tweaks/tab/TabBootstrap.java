package me.beeliebub.tweaks.tab;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

/**
 * Tier:       3 - player-facing infra
 * Reads:      economyManager, rankManager, worldProfileTable (tier 1), playerAdminManager (same
 *             tier, published by playeradmin.PlayerAdminBootstrap immediately before this call)
 * Wire-backs: TabManager is constructed from all three managers, then wired back into each via
 *             setTabManager so they can push tab-list/scoreboard updates without a
 *             circular constructor dependency.
 */
public final class TabBootstrap {

    private TabBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        TabManager tabManager = new TabManager(plugin, services.economyManager(), services.rankManager(),
                services.playerAdminManager(), services.worldProfileTable());
        plugin.getServer().getPluginManager().registerEvents(tabManager, plugin);
        services.playerAdminManager().setTabManager(tabManager);
        services.economyManager().setTabManager(tabManager);
        services.rankManager().setTabManager(tabManager);
    }
}
