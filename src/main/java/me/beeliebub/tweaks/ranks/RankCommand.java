package me.beeliebub.tweaks.ranks;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.economy.BalanceCommand;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /ranks — list all configured ranks and their costs.
 * Structured as a subcommand switch so future branches (e.g. "edit") can be added
 * without restructuring this class. No permission required.
 */
public class RankCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economyManager;
    private final RankManager rankManager;

    public RankCommand(EconomyManager economyManager, RankManager rankManager) {
        this.economyManager = economyManager;
        this.rankManager = rankManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "edit" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Messages.ranksEditRequiresPlayer());
                    return true;
                }
                if (!sender.hasPermission(Permissions.ADMIN_RANKS)) {
                    sender.sendMessage(Messages.noPermission());
                    return true;
                }
                RankEditGUI.openRankList(player, rankManager);
            }
            default -> listRanks(sender);
        }
        return true;
    }

    // ---- TabCompleter -------------------------------------------------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (sender.hasPermission(Permissions.ADMIN_RANKS)) {
                String partial = args[0].toLowerCase(Locale.ROOT);
                if ("edit".startsWith(partial)) {
                    return List.of("edit");
                }
            }
        }
        return Collections.emptyList();
    }

    // ---- Subcommand implementations -----------------------------------------

    private void listRanks(CommandSender sender) {
        int playerRank = -1;
        UUID uuid = null;
        if (sender instanceof Player player) {
            uuid = player.getUniqueId();
            playerRank = economyManager.getRank(uuid);
        }

        sender.sendMessage(Messages.ranksListHeader());

        int max = rankManager.getMaxRank();
        for (int rank = 1; rank <= max; rank++) {
            String name = rankManager.getRankDisplayName(rank);
            double cost = rankManager.getRankCost(rank);
            double multiplier = rankManager.getRankMultiplierBonus(rank);
            double rakeback = rankManager.getRankRakeback(rank);

            boolean isCurrent = (rank == playerRank);
            String marker = isCurrent ? "» " : "  ";

            NamedTextColor lineColor = isCurrent ? NamedTextColor.YELLOW : NamedTextColor.WHITE;

            Component parsedName = parseDisplayName(name);

            sender.sendMessage(Messages.ranksListEntry(marker, parsedName, BalanceCommand.formatBalance(cost),
                    (int) Math.round(multiplier * 100), (int) Math.round(rakeback * 100), isCurrent, lineColor));
        }

        if (uuid != null && playerRank == 0) {
            sender.sendMessage(Messages.ranksUnrankedNotice());
        } else if (uuid != null && playerRank >= max) {
            sender.sendMessage(Messages.ranksMaximumRankNotice());
        }
    }

    // ---- Helpers ------------------------------------------------------------

    /**
     * Parses a rank display name into an Adventure Component.
     * If the name contains legacy color codes ({@code &} or {@code §}), the
     * ampersand-based {@link LegacyComponentSerializer} is used so both forms
     * are handled. Otherwise the name is treated as MiniMessage markup.
     */
    public static Component parseDisplayName(String name) {
        if (name == null || name.isEmpty()) {
            return Component.empty();
        }
        if (name.indexOf('§') >= 0) {
            // Contains a section-sign (§) — use the section-sign serializer.
            return LegacyComponentSerializer.legacySection().deserialize(name);
        }
        if (name.indexOf('&') >= 0) {
            // Contains an ampersand — use the ampersand serializer.
            return LegacyComponentSerializer.legacyAmpersand().deserialize(name);
        }
        return Messages.MM.deserialize(name);
    }
}
