package me.beeliebub.tweaks.ranks;

import me.beeliebub.tweaks.economy.BalanceCommand;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
                    sender.sendMessage(Component.text("Only players can edit ranks.", NamedTextColor.RED));
                    return true;
                }
                if (!sender.hasPermission(Permissions.ADMIN_RANKS)) {
                    sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
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

        sender.sendMessage(Component.text("--- Ranks ---", NamedTextColor.GOLD));

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

            Component line = Component.text(marker + "Rank ", lineColor)
                    .append(parsedName)
                    .append(Component.text(
                            " — Cost: " + BalanceCommand.formatBalance(cost)
                            + " | Bonus: " + (int) Math.round(multiplier * 100) + "%"
                            + " | Rakeback: " + (int) Math.round(rakeback * 100) + "%"
                            + (isCurrent ? " (current)" : ""),
                            lineColor));
            sender.sendMessage(line);
        }

        if (uuid != null && playerRank == 0) {
            sender.sendMessage(Component.text(
                    "You are currently Unranked. Use /rankup to purchase Rank I.",
                    NamedTextColor.GRAY));
        } else if (uuid != null && playerRank >= max) {
            sender.sendMessage(Component.text(
                    "You have reached the maximum rank!", NamedTextColor.GOLD));
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
        return MiniMessage.miniMessage().deserialize(name);
    }
}
