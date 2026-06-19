package me.beeliebub.tweaks.ranks;

import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("set")) {
            sender.sendMessage(Component.text("Usage: /rank set <player> <rank_id/name>", NamedTextColor.RED));
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
            sender.sendMessage(Component.text("Invalid rank: " + rankInput, NamedTextColor.RED));
            return true;
        }

        economyManager.setRank(uuid, rankId);
        sender.sendMessage(Component.text("Successfully set ", NamedTextColor.GREEN)
                .append(Component.text(target.getName() != null ? target.getName() : args[1], NamedTextColor.GOLD))
                .append(Component.text("'s rank to ", NamedTextColor.GREEN))
                .append(Component.text(rankManager.getRankDisplayName(rankId), NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN)));

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
