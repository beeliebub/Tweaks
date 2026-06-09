package me.beeliebub.tweaks.ranks;

import me.beeliebub.tweaks.economy.BalanceCommand;
import me.beeliebub.tweaks.economy.EconomyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        UUID uuid = player.getUniqueId();
        int current = economyManager.getRank(uuid);

        if (current >= rankManager.getMaxRank()) {
            player.sendMessage(Component.text("You are already the maximum rank.", NamedTextColor.RED));
            return true;
        }

        int next = current + 1;
        double cost = rankManager.getRankCost(next);
        double balance = economyManager.getBalance(uuid);

        if (balance < cost) {
            player.sendMessage(Component.text(
                    "Insufficient funds. Rank " + rankManager.getRankDisplayName(next)
                    + " costs " + BalanceCommand.formatBalance(cost)
                    + " but you only have " + BalanceCommand.formatBalance(balance) + ".",
                    NamedTextColor.RED));
            return true;
        }

        economyManager.removeBalance(uuid, cost);
        economyManager.setRank(uuid, next);

        double remaining = economyManager.getBalance(uuid);
        player.sendMessage(Component.text(
                "Congratulations! You are now Rank " + rankManager.getRankDisplayName(next)
                + ". Remaining balance: " + BalanceCommand.formatBalance(remaining) + ".",
                NamedTextColor.GREEN));
        return true;
    }
}
