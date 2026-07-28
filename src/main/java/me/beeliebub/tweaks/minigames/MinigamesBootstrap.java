package me.beeliebub.tweaks.minigames;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.andrew.MannequinAI;
import me.beeliebub.tweaks.minigames.andrew.WhackCommand;
import me.beeliebub.tweaks.minigames.andrew.WhackConfig;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackCommand;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener;
import me.beeliebub.tweaks.minigames.resource.RerollCommand;
import me.beeliebub.tweaks.minigames.resource.ResourceCommand;
import me.beeliebub.tweaks.minigames.resource.ResourceCraftListener;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.minigames.resource.ResourceWorldListener;
import me.beeliebub.tweaks.minigames.roulette.RouletteCommand;
import me.beeliebub.tweaks.minigames.roulette.RouletteListener;
import me.beeliebub.tweaks.minigames.roulette.RouletteScanCommand;

import java.util.logging.Level;

/**
 * Tier:      2 - shared game state
 * Reads:     storageManager (tier 1, ResourceWorldListener), economyManager, houseAccount, rankManager
 *            (tier 1, blackjack + roulette)
 * Publishes: resourceHuntItems (read by teleport and core at tier 3), resourceHunt (read by
 *            itemadmin's CondenseCommand at tier 3 and enchantments at tier 4),
 *            blackjackListener and rouletteListener (both read by Tweaks#onDisable(); the former
 *            also by Tweaks#getBlackjackListener())
 * Ordering:  RewardManager and ResourceHunt are constructed here, ahead of enchantments
 *            (tier 4), specifically so Tunneller can credit its surrounding
 *            (BlockDropItemEvent-bypassing) breaks via ResourceHunt#recordExternalDrops.
 *
 * <p>Roulette's {@code RouletteListener}/{@code RouletteCommand} (board discovery, betting flow,
 * spin animation and settlement, and the teardown funnel) are also constructed here, reading the
 * same three economy services as Blackjack.
 */
public final class MinigamesBootstrap {

    private MinigamesBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        ResourceHuntItems resourceHuntItems = new ResourceHuntItems(plugin);
        services.setResourceHuntItems(resourceHuntItems);

        RewardManager rewardManager = new RewardManager(plugin);
        ResourceHunt resourceHunt = new ResourceHunt(plugin, rewardManager);
        services.setResourceHunt(resourceHunt);

        plugin.getServer().getPluginManager().registerEvents(new ResourceWorldListener(plugin, services.storageManager()), plugin);

        ResourceCommand resourceCommand = new ResourceCommand(resourceHunt, resourceHuntItems);
        plugin.getCommand("resource").setExecutor(resourceCommand);
        plugin.getCommand("resource").setTabCompleter(resourceCommand);

        RerollCommand rerollCommand = new RerollCommand(resourceHunt);
        plugin.getCommand("reroll").setExecutor(rerollCommand);

        MannequinAI mannequinAI = new MannequinAI(plugin);
        plugin.getServer().getPluginManager().registerEvents(mannequinAI, plugin);
        mannequinAI.resumeAll();

        RewardCommand rewardCommand = new RewardCommand(rewardManager);
        plugin.getCommand("reward").setExecutor(rewardCommand);
        plugin.getCommand("reward").setTabCompleter(rewardCommand);
        plugin.getServer().getPluginManager().registerEvents(new RewardListener(rewardManager, rewardCommand), plugin);

        plugin.getServer().getPluginManager().registerEvents(resourceHunt, plugin);
        plugin.getServer().getPluginManager().registerEvents(new ResourceCraftListener(plugin, resourceHunt), plugin);

        WhackConfig whackConfig = new WhackConfig(plugin);
        WhackCommand whackCommand = new WhackCommand(plugin, whackConfig, rewardManager);
        plugin.getCommand("whack").setExecutor(whackCommand);
        plugin.getCommand("whack").setTabCompleter(whackCommand);

        BlackjackListener blackjackListener = new BlackjackListener(
                plugin, services.economyManager(), services.houseAccount(), services.rankManager());
        services.setBlackjackListener(blackjackListener);
        BlackjackCommand blackjackCommand = new BlackjackCommand(services.economyManager(), blackjackListener);
        plugin.getCommand("blackjack").setExecutor(blackjackCommand);
        plugin.getCommand("blackjack").setTabCompleter(blackjackCommand);
        plugin.getServer().getPluginManager().registerEvents(blackjackListener, plugin);

        // Roulette (boards + betting flow + spin/settlement + teardown funnel): reads
        // the tier-1 economyManager/houseAccount/rankManager for stake debits, payouts, rakeback,
        // and house credit. See minigames/roulette/CLAUDE.md.
        RouletteListener rouletteListener = new RouletteListener(
                plugin, services.economyManager(), services.houseAccount(), services.rankManager());
        services.setRouletteListener(rouletteListener);
        plugin.getServer().getPluginManager().registerEvents(rouletteListener, plugin);
        RouletteCommand rouletteCommand = new RouletteCommand(rouletteListener);
        plugin.getCommand("roulette").setExecutor(rouletteCommand);
        plugin.getCommand("roulette").setTabCompleter(rouletteCommand);
        rouletteListener.reactivateAllBoards();

        // Originally scoped as a throwaway diagnostic, kept instead as a standing admin tool. See
        // minigames/roulette/CLAUDE.md.
        RouletteScanCommand rouletteScanCommand = new RouletteScanCommand(plugin);
        plugin.getCommand("roulettescan").setExecutor(rouletteScanCommand);
    }

    /**
     * Takes both listeners directly — see {@code EconomyBootstrap.shutdown}'s Javadoc for why.
     * Each listener's shutdown runs inside its own try/catch: Blackjack's card-cleanup failing
     * must never prevent Roulette's shutdown from running, since the latter's refund-any-round-
     * still-BETTING guarantee is money-critical, not just cosmetic cleanup.
     */
    public static void shutdown(Tweaks plugin, BlackjackListener blackjackListener, RouletteListener rouletteListener) {
        if (blackjackListener != null) {
            try {
                // Guarantee no card ItemDisplays survive a server stop.
                blackjackListener.shutdown();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Minigames: blackjack shutdown failed — "
                        + "continuing so roulette's shutdown still runs.", e);
            }
        }
        if (rouletteListener != null) {
            try {
                // Refunds any round still BETTING, settles one still SPINNING, and guarantees no
                // Interaction hitbox or glowing BlockDisplay survives a server stop — see
                // roulette/CLAUDE.md's teardown-funnel section.
                rouletteListener.shutdown();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Minigames: roulette shutdown failed — its "
                        + "refund/settle guarantees may not have completed for every board.", e);
            }
        }
    }
}
