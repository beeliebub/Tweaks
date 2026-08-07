package me.beeliebub.tweaks.ranks;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

/**
 * Tier:      1 - foundation state
 * Reads:     economyManager (published by economy.EconomyBootstrap.register, same tier)
 * Publishes: rankManager (read by tab, minigames' blackjack, and economy's rank-aware
 *            listener straddle call later in this tier)
 */
public final class RanksBootstrap {

    private RanksBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        RankManager rankManager = new RankManager(plugin, services.economyManager());
        services.setRankManager(rankManager);

        RankCommand rankCommand = new RankCommand(services.economyManager(), rankManager);
        plugin.getCommand("ranks").setExecutor(rankCommand);
        plugin.getCommand("ranks").setTabCompleter(rankCommand);

        plugin.getCommand("rankup").setExecutor(new RankupCommand(services.economyManager(), rankManager));

        RankSetCommand rankSetCommand = new RankSetCommand(plugin, services.economyManager(), rankManager);
        plugin.getCommand("rank").setExecutor(rankSetCommand);
        plugin.getCommand("rank").setTabCompleter(rankSetCommand);
    }
}
