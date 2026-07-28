package me.beeliebub.tweaks.ranks;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /rank set <player> <rank> — manually set a player's rank (admin only).
 */
public class RankSetCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economyManager;
    private final RankManager rankManager;

    public RankSetCommand(EconomyManager economyManager, RankManager rankManager) {
        this.economyManager = economyManager;
        this.rankManager = rankManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission(Permissions.ADMIN_RANK_SET)) {
            sender.sendMessage(Messages.noPermission());
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("set")) {
            sender.sendMessage(Messages.rankSetUsage());
            return true;
        }

        Player online = Bukkit.getPlayer(args[1]);
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayer(args[1]);
        UUID uuid = target.getUniqueId();
        economyManager.loadPlayer(uuid);

        String rankInput = args[2];
        int rankId = -1;

        // Try matching by display name first (case-insensitive)
        for (int i = 0; i <= rankManager.getMaxRank(); i++) {
            if (rankManager.getRankDisplayName(i).equalsIgnoreCase(rankInput)) {
                rankId = i;
                break;
            }
        }

        // If not matched by name, try parsing as integer (rank ID)
        if (rankId == -1) {
            try {
                rankId = Integer.parseInt(rankInput);
            } catch (NumberFormatException ignored) {}
        }

        if (rankId < 0 || rankId > rankManager.getMaxRank()) {
            sender.sendMessage(Messages.rankSetInvalidRank(rankInput));
            return true;
        }

        economyManager.setRank(uuid, rankId);
        sender.sendMessage(Messages.rankSetSuccess(target.getName() != null ? target.getName() : args[1],
                rankManager.getRankDisplayName(rankId)));

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_RANK_SET)) {
            return Collections.emptyList();
        }

        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            if ("set".startsWith(partial)) return List.of("set");
        } else if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .collect(java.util.stream.Collectors.toList());
        } else if (args.length == 3) {
            List<String> ranks = new ArrayList<>();
            for (int i = 0; i <= rankManager.getMaxRank(); i++) {
                ranks.add(String.valueOf(i));
                ranks.add(rankManager.getRankDisplayName(i));
            }
            return ranks.stream()
                    .filter(r -> r.toLowerCase(Locale.ROOT).startsWith(partial))
                    .collect(java.util.stream.Collectors.toList());
        }

        return Collections.emptyList();
    }
}
