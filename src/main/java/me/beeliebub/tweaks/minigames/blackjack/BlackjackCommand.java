package me.beeliebub.tweaks.minigames.blackjack;

import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
 *   <li>{@code /blackjack createpvptable [hexColor]} — begins the PvP-table setup flow;
 *       the admin right-clicks the MIDDLE button on ONE side of the table. The listener
 *       auto-detects the opposing side's buttons and optional betting shelves.</li>
 *   <li>{@code /blackjack removetable} — begins the removal flow; the admin right-clicks the
 *       middle button of the target table to remove it.</li>
 * </ul>
 *
 * <p>Geometry detection, PDC persistence, and post-click feedback are owned by
 * {@link BlackjackListener}; this command is intentionally thin.
 */
public final class BlackjackCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

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
            sender.sendMessage(MM.deserialize("<red>Only players can manage Blackjack tables.</red>"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player, label);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "createtable"    -> handleCreateTable(player, label, args);
            case "createpvptable" -> handleCreatePvpTable(player, label, args);
            case "removetable"    -> handleRemoveTable(player);
            default -> {
                sendUsage(player, label);
                yield true;
            }
        };
    }

    // ---- Subcommand handlers ------------------------------------------------

    private boolean handleCreateTable(Player player, String label, String[] args) {
        if (!player.hasPermission(Permissions.BLACKJACK_CREATETABLE)) {
            player.sendMessage(MM.deserialize("<red>You do not have permission to create Blackjack tables.</red>"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MM.deserialize(
                    "<gray>Usage:</gray> <yellow>/" + label + " createtable <bet> [hexColor]</yellow>"));
            return true;
        }

        int bet;
        try {
            bet = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(MM.deserialize("<red>Bet must be a whole number.</red>"));
            return true;
        }

        if (bet <= 0) {
            player.sendMessage(MM.deserialize("<red>Bet must be greater than 0.</red>"));
            return true;
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
                player.sendMessage(MM.deserialize(
                        "<red>Invalid color. Use a hex value like <yellow>FF8800</yellow>"
                                + " or <yellow>#FF8800</yellow>.</red>"));
                return true;
            }
        }

        listener.beginTableSetup(player, bet, backColor);
        String colorSuffix = backColor != null
                ? " <gray>| Back color:</gray> <yellow>#" + String.format("%06X", backColor) + "</yellow>"
                : "";
        player.sendMessage(MM.deserialize(
                "<green>Table setup started!</green> "
                        + "<gray>Right-click the</gray> <yellow>middle control button</yellow> "
                        + "<gray>to finalize the table (bet:</gray> <yellow>" + bet + "</yellow><gray>).</gray>"
                        + colorSuffix));
        return true;
    }

    private boolean handleCreatePvpTable(Player player, String label, String[] args) {
        if (!player.hasPermission(Permissions.BLACKJACK_CREATETABLE)) {
            player.sendMessage(MM.deserialize("<red>You do not have permission to create Blackjack tables.</red>"));
            return true;
        }

        // Optional second argument: card-back hex color. Same parsing as createtable.
        Integer backColor = null;
        if (args.length >= 2) {
            String colorArg = args[1].startsWith("#") ? args[1].substring(1) : args[1];
            try {
                if (colorArg.matches("[0-9A-Fa-f]{1,6}")) {
                    backColor = Integer.parseUnsignedInt(colorArg, 16) & 0xFFFFFF;
                } else {
                    backColor = Integer.parseUnsignedInt(colorArg) & 0xFFFFFF;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(MM.deserialize(
                        "<red>Invalid color. Use a hex value like <yellow>FF8800</yellow>"
                                + " or <yellow>#FF8800</yellow>.</red>"));
                return true;
            }
        }

        listener.beginPvpTableSetup(player, backColor);
        String colorSuffix = backColor != null
                ? " <gray>| Back color:</gray> <yellow>#" + String.format("%06X", backColor) + "</yellow>"
                : "";
        player.sendMessage(MM.deserialize(
                "<green>PvP table setup started!</green> "
                        + "<gray>Right-click the</gray> <yellow>middle control button</yellow> "
                        + "<gray>on ONE side of the table to finalize.</gray>"
                        + colorSuffix));
        return true;
    }

    private boolean handleRemoveTable(Player player) {
        if (!player.hasPermission(Permissions.BLACKJACK_REMOVETABLE)) {
            player.sendMessage(MM.deserialize("<red>You do not have permission to remove Blackjack tables.</red>"));
            return true;
        }

        listener.beginTableRemoval(player);
        player.sendMessage(MM.deserialize(
                "<green>Table removal started!</green> "
                        + "<gray>Right-click the</gray> <yellow>middle button</yellow> "
                        + "<gray>of the table you want to remove.</gray>"));
        return true;
    }

    // ---- Tab completion -----------------------------------------------------

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            if (sender.hasPermission(Permissions.BLACKJACK_CREATETABLE)) {
                if ("createtable".startsWith(args[0].toLowerCase())) {
                    suggestions.add("createtable");
                }
                if ("createpvptable".startsWith(args[0].toLowerCase())) {
                    suggestions.add("createpvptable");
                }
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

        if (args.length == 2 && args[0].equalsIgnoreCase("createpvptable")
                && sender.hasPermission(Permissions.BLACKJACK_CREATETABLE)) {
            // Only a color arg for PvP tables — no bet.
            return List.of("FF0000", "00FF00", "0000FF", "FF8800", "8800FF");
        }

        return Collections.emptyList();
    }

    // ---- Helpers ------------------------------------------------------------

    private static void sendUsage(Player player, String label) {
        player.sendMessage(MM.deserialize(
                "<gray>Usage:</gray> <yellow>/" + label
                        + " <createtable <bet> [hexColor]|createpvptable [hexColor]|removetable></yellow>"));
    }
}
