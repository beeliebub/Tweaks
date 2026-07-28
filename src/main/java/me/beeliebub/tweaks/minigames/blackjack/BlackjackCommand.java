package me.beeliebub.tweaks.minigames.blackjack;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.economy.EconomyManager;
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
 * Admin commands for managing in-world Blackjack tables via physical buttons.
 *
 * <ul>
 *   <li>{@code /blackjack createtable <bet> [hexColor]} — begins the server-table setup flow;
 *       the admin right-clicks the middle control button to finalize the table. The optional
 *       {@code hexColor} sets the card-back tint for every card rendered at this table. Accepted
 *       forms: {@code RRGGBB}, {@code #RRGGBB}, or a plain decimal integer (e.g. {@code 16744448}).</li>
 *   <li>{@code /blackjack removetable} — begins the removal flow; the admin right-clicks the
 *       middle button of the target table to remove it.</li>
 * </ul>
 *
 * <p>Geometry detection, PDC persistence, and post-click feedback are owned by
 * {@link BlackjackListener}; this command is intentionally thin.
 */
public final class BlackjackCommand implements CommandExecutor, TabCompleter {

    private static final List<String> BET_SUGGESTIONS = List.of("10", "50", "100", "500", "1000");

    // Economy is kept in the constructor to satisfy the unchanged Tweaks.java wiring.
    private final EconomyManager economyManager;
    private final BlackjackListener listener;

    public BlackjackCommand(EconomyManager economyManager, BlackjackListener listener) {
        this.economyManager = economyManager;
        this.listener = listener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.MINIGAMES.blackjackManageRequiresPlayer());
            return true;
        }

        if (args.length == 0) {
            sendUsage(player, label);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "createtable" -> handleCreateTable(player, label, args);
            case "removetable" -> handleRemoveTable(player);
            default -> {
                sendUsage(player, label);
                yield true;
            }
        };
    }

    // ---- Subcommand handlers ------------------------------------------------

    private boolean handleCreateTable(Player player, String label, String[] args) {
        if (!player.hasPermission(Permissions.BLACKJACK_CREATETABLE)) {
            player.sendMessage(Messages.noPermission());
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(Messages.MINIGAMES.blackjackCreateUsage(label));
            return true;
        }

        int bet;
        if (args[1].equalsIgnoreCase("free") || args[1].equals("0")) {
            bet = 0;
        } else {
            try {
                bet = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Messages.MINIGAMES.blackjackBetInvalid());
                return true;
            }
            if (bet < 0) {
                player.sendMessage(Messages.MINIGAMES.blackjackBetNegative());
                return true;
            }
        }

        // Optional third argument: card-back hex color. Accepts #RRGGBB, RRGGBB, or decimal int.
        Integer backColor = null;
        if (args.length >= 3) {
            String colorArg = args[2].startsWith("#") ? args[2].substring(1) : args[2];
            try {
                // Try hex parse first (6 hex digits). Fall through to decimal if not exactly 6 hex chars.
                if (colorArg.matches("[0-9A-Fa-f]{1,6}")) {
                    backColor = Integer.parseUnsignedInt(colorArg, 16) & 0xFFFFFF;
                } else {
                    backColor = Integer.parseUnsignedInt(colorArg) & 0xFFFFFF;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(Messages.MINIGAMES.blackjackColorInvalid());
                return true;
            }
        }

        listener.beginTableSetup(player, bet, backColor);
        String betDisplay = bet == 0 ? "FREE" : String.valueOf(bet);
        player.sendMessage(Messages.MINIGAMES.blackjackTableSetupStarted(betDisplay, backColor));
        return true;
    }

    private boolean handleRemoveTable(Player player) {
        if (!player.hasPermission(Permissions.BLACKJACK_REMOVETABLE)) {
            player.sendMessage(Messages.noPermission());
            return true;
        }

        listener.beginTableRemoval(player);
        player.sendMessage(Messages.MINIGAMES.blackjackTableRemovalStarted());
        return true;
    }

    // ---- Tab completion -----------------------------------------------------

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            if (sender.hasPermission(Permissions.BLACKJACK_CREATETABLE)
                    && "createtable".startsWith(args[0].toLowerCase())) {
                suggestions.add("createtable");
            }
            if (sender.hasPermission(Permissions.BLACKJACK_REMOVETABLE)
                    && "removetable".startsWith(args[0].toLowerCase())) {
                suggestions.add("removetable");
            }
            return suggestions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("createtable")
                && sender.hasPermission(Permissions.BLACKJACK_CREATETABLE)) {
            return BET_SUGGESTIONS;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("createtable")
                && sender.hasPermission(Permissions.BLACKJACK_CREATETABLE)) {
            // Offer a few example hex colors as hints; player can type any RRGGBB value.
            return List.of("FF0000", "00FF00", "0000FF", "FF8800", "8800FF");
        }

        return Collections.emptyList();
    }

    // ---- Helpers ------------------------------------------------------------

    private static void sendUsage(Player player, String label) {
        player.sendMessage(Messages.MINIGAMES.blackjackUsage(label));
    }
}
