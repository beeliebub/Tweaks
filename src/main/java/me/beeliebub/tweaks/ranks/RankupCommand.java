package me.beeliebub.tweaks.ranks;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.economy.BalanceMutationResult;
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
import java.util.logging.Level;

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
        double configuredCost = rankManager.getRankCost(next);
        if (!Double.isFinite(configuredCost)) {
            rankManager.plugin().getLogger().log(Level.WARNING,
                    "Refusing rankup to rank " + next + " because its configured cost is non-finite: "
                            + configuredCost);
            return true;
        }
        long cost = (long) Math.floor(configuredCost);
        if (cost < 0 || cost > EconomyManager.MAX_BALANCE) {
            rankManager.plugin().getLogger().log(Level.WARNING,
                    "Refusing rankup to rank " + next + " because its configured cost is outside the balance range: "
                            + configuredCost);
            return true;
        }
        long balance = economyManager.getBalance(uuid);

        if (balance < cost) {
            player.sendMessage(Messages.rankupInsufficientFunds(rankManager.getRankDisplayComponent(next),
                    BalanceCommand.formatBalance(cost), BalanceCommand.formatBalance(balance)));
            return true;
        }

        if (economyManager.removeBalance(uuid, cost) != BalanceMutationResult.APPLIED) {
            rankManager.plugin().getLogger().log(Level.WARNING,
                    "Refusing rankup to rank " + next + " because its cost could not be debited for " + uuid);
            return true;
        }
        economyManager.setRank(uuid, next);

        long remaining = economyManager.getBalance(uuid);
        player.sendMessage(Messages.rankupSuccess(rankManager.getRankDisplayComponent(next),
                BalanceCommand.formatBalance(remaining)));
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(rankManager.plugin());
        if (eventLog != null) eventLog.log(LoggingPaths.RANKS_PURCHASED, () ->
                "[Ranks] " + ConsoleEventLog.actorLabel(player.getName(), uuid)
                        + " purchased rank " + next + " for " + BalanceCommand.formatBalance(cost));
        return true;
    }
}
