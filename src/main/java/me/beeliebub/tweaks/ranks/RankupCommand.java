package me.beeliebub.tweaks.ranks;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.economy.BalanceCommand;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingPaths;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * /rankup — purchase the next rank using the player's balance.
 * No permission required; available to all players.
 */
public class RankupCommand implements CommandExecutor {

    private final EconomyManager economyManager;
    private final RankManager rankManager;

    public RankupCommand(EconomyManager economyManager, RankManager rankManager) {
        this.economyManager = economyManager;
        this.rankManager = rankManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.rankupRequiresPlayer());
            return true;
        }

        UUID uuid = player.getUniqueId();
        int current = economyManager.getRank(uuid);

        if (current >= rankManager.getMaxRank()) {
            player.sendMessage(Messages.rankupAlreadyMaximumRank());
            return true;
        }

        int next = current + 1;
        double cost = rankManager.getRankCost(next);
        double balance = economyManager.getBalance(uuid);

        if (balance < cost) {
            player.sendMessage(Messages.rankupInsufficientFunds(rankManager.getRankDisplayComponent(next),
                    BalanceCommand.formatBalance(cost), BalanceCommand.formatBalance(balance)));
            return true;
        }

        economyManager.removeBalance(uuid, cost);
        economyManager.setRank(uuid, next);

        double remaining = economyManager.getBalance(uuid);
        player.sendMessage(Messages.rankupSuccess(rankManager.getRankDisplayComponent(next),
                BalanceCommand.formatBalance(remaining)));
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(rankManager.plugin());
        if (eventLog != null) eventLog.log(LoggingPaths.RANKS_PURCHASED, () ->
                "[Ranks] " + ConsoleEventLog.actorLabel(player.getName(), uuid)
                        + " purchased rank " + next + " for " + BalanceCommand.formatBalance(cost));
        return true;
    }
}
