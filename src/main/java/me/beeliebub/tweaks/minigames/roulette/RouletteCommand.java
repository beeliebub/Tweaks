package me.beeliebub.tweaks.minigames.roulette;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Admin and player commands for physical Roulette boards.
 *
 * <ul>
 *   <li>{@code /roulette createboard <min> <max>} — begins the board setup flow; the admin
 *       right-clicks a wall-mounted button or lever to finalize the board as that spin control's
 *       location.</li>
 *   <li>{@code /roulette removeboard} — begins the removal flow; the admin right-clicks the
 *       target board's spin control to remove it.</li>
 *   <li>{@code /roulette stake <amount>} — sets a sticky per-player stake; every segment click at
 *       any board wagers this amount. Ungated (like a physical button press), unlike the two admin
 *       subcommands above.</li>
 * </ul>
 *
 * <p>Geometry detection, PDC persistence, and post-click feedback are owned by
 * {@link RouletteListener}; this command is intentionally thin.
 */
public final class RouletteCommand implements CommandExecutor, TabCompleter {

    private final RouletteListener listener;

    public RouletteCommand(RouletteListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.MINIGAMES.rouletteManageRequiresPlayer());
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Messages.MINIGAMES.rouletteUsage(label));
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "createboard" -> handleCreateBoard(player, label, args);
            case "removeboard" -> handleRemoveBoard(player);
            case "stake" -> handleStake(player, label, args);
            default -> {
                player.sendMessage(Messages.MINIGAMES.rouletteUsage(label));
                yield true;
            }
        };
    }

    // ---- Subcommand handlers ------------------------------------------------

    private boolean handleCreateBoard(Player player, String label, String[] args) {
        if (!player.hasPermission(Permissions.ROULETTE_CREATEBOARD)) {
            player.sendMessage(Messages.noPermission());
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(Messages.MINIGAMES.rouletteCreateUsage(label));
            return true;
        }

        int minBet;
        int maxBet;
        try {
            minBet = Integer.parseInt(args[1]);
            maxBet = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Messages.MINIGAMES.rouletteInvalidBounds());
            return true;
        }
        if (minBet < 1 || minBet > maxBet) {
            player.sendMessage(Messages.MINIGAMES.rouletteInvalidBounds());
            return true;
        }

        listener.beginBoardSetup(player, minBet, maxBet);
        player.sendMessage(Messages.MINIGAMES.rouletteCreateStarted(minBet, maxBet));
        return true;
    }

    private boolean handleRemoveBoard(Player player) {
        if (!player.hasPermission(Permissions.ROULETTE_REMOVEBOARD)) {
            player.sendMessage(Messages.noPermission());
            return true;
        }

        listener.beginBoardRemoval(player);
        player.sendMessage(Messages.MINIGAMES.rouletteRemovalStarted());
        return true;
    }

    /** Ungated, like a physical button press — every player may set their own sticky stake. */
    private boolean handleStake(Player player, String label, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Messages.MINIGAMES.rouletteStakeUsage(label));
            return true;
        }

        RouletteSessionManager.StakeParse parsed = RouletteSessionManager.parseStake(args[1]);
        switch (parsed.error()) {
            case NOT_A_WHOLE_NUMBER -> player.sendMessage(Messages.MINIGAMES.rouletteStakeNotWholeNumber());
            case BELOW_MINIMUM -> player.sendMessage(Messages.MINIGAMES.rouletteStakeBelowMinimum());
            case ABOVE_CEILING -> player.sendMessage(
                    Messages.MINIGAMES.rouletteStakeAboveCeiling(RouletteRound.MAX_CUMULATIVE_WAGER));
            case null -> {
                listener.setStickyStake(player, parsed.amount());
                player.sendMessage(Messages.MINIGAMES.rouletteStakeSet(parsed.amount()));
            }
        }
        return true;
    }

    // ---- Tab completion -----------------------------------------------------

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            if (sender.hasPermission(Permissions.ROULETTE_CREATEBOARD)
                    && "createboard".startsWith(args[0].toLowerCase())) {
                suggestions.add("createboard");
            }
            if (sender.hasPermission(Permissions.ROULETTE_REMOVEBOARD)
                    && "removeboard".startsWith(args[0].toLowerCase())) {
                suggestions.add("removeboard");
            }
            if ("stake".startsWith(args[0].toLowerCase())) {
                suggestions.add("stake");
            }
            return suggestions;
        }

        return Collections.emptyList();
    }
}
