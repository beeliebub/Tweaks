package me.beeliebub.tweaks.economy;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.utils.OfflinePlayerResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Handles /balance (and /bal alias).
 *
 * Sub-commands:
 *   /balance              — show own balance (player only)
 *   /balance hide         — toggle own hidden flag (player only)
 *   /balance <player>     — show another player's balance (requires ADMIN_BALANCE)
 *   /balance set   <player> <amount>  — set balance   (requires ADMIN_BALANCE)
 *   /balance add   <player> <amount>  — add balance   (requires ADMIN_BALANCE)
 *   /balance remove <player> <amount> — remove balance (requires ADMIN_BALANCE)
 */
public class BalanceCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economyManager;

    public BalanceCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    // ---- CommandExecutor ----------------------------------------------------

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        // No args: show own balance (player only)
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.balanceConsoleMustSpecifyPlayer());
                return true;
            }
            sendOwnBalance(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // /balance hide — toggle own hidden flag (player only)
        if (sub.equals("hide")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.balanceHideConsoleCannotUse());
                return true;
            }
            UUID uuid = player.getUniqueId();
            boolean nowHidden = !economyManager.isBalanceHidden(uuid);
            economyManager.setBalanceHidden(uuid, nowHidden);
            if (nowHidden) {
                player.sendMessage(Messages.balanceHidden());
            } else {
                player.sendMessage(Messages.balanceVisible());
            }
            return true;
        }

        // /balance set|add|remove <player> <amount> — admin mutations
        if (sub.equals("set") || sub.equals("add") || sub.equals("remove")) {
            if (!sender.hasPermission(Permissions.ADMIN_BALANCE)) {
                sender.sendMessage(Messages.balanceModifyNoPermission());
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Messages.balanceMutationUsage(sub));
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Messages.balanceInvalidAmount(args[2]));
                return true;
            }
            if (!Double.isFinite(amount)) {
                sender.sendMessage(Messages.balanceInvalidAmount(args[2]));
                return true;
            }
            resolveTarget(sender, args[1], target -> {
                UUID targetId = target.getUniqueId();
                // Ensure the player's data is in cache even if they are offline,
                // so the write is not a blind first-touch with stale defaults.
                economyManager.loadPlayer(targetId);
                BalanceMutationResult mutation = switch (sub) {
                    case "set" -> economyManager.setBalance(targetId, amount);
                    case "add" -> economyManager.addBalance(targetId, amount);
                    case "remove" -> economyManager.removeBalance(targetId, amount);
                    default -> throw new IllegalStateException("Unexpected balance operation: " + sub);
                };
                String targetName = target.getName() != null ? target.getName() : args[1];
                if (mutation == BalanceMutationResult.REJECTED_UNREPRESENTABLE) {
                    sender.sendMessage(Messages.balanceMutationRejected(targetName, sub, formatBalance(amount)));
                    return;
                }
                sender.sendMessage(Messages.balanceMutationSuccess(
                        targetName,
                        sub,
                        formatBalance(amount),
                        formatBalance(economyManager.getBalance(targetId))));
            });
            return true;
        }

        // /balance <player> — view another player's balance (admin)
        if (!sender.hasPermission(Permissions.ADMIN_BALANCE)) {
            sender.sendMessage(Messages.balanceViewOtherNoPermission());
            return true;
        }
        resolveTarget(sender, args[0], target -> {
            UUID targetId = target.getUniqueId();
            economyManager.loadPlayer(targetId);
            double balance = economyManager.getBalance(targetId);
            String targetName = target.getName() != null ? target.getName() : args[0];
            sender.sendMessage(Messages.balanceOtherPlayer(targetName, formatBalance(balance)));
        });
        return true;
    }

    private void resolveTarget(CommandSender sender, String input,
                               java.util.function.Consumer<OfflinePlayer> action) {
        JavaPlugin plugin = economyManager.plugin();
        var future = OfflinePlayerResolver.resolve(plugin, sender, input);
        boolean asynchronous = !future.isDone();
        future.whenComplete((target, error) -> {
            Runnable continuation = () -> {
                if (asynchronous && !OfflinePlayerResolver.isSenderOnline(sender)) return;
                if (error != null) {
                    if (OfflinePlayerResolver.isLookupInProgress(error)) {
                        sender.sendMessage(Messages.COMMANDS.offlinePlayerLookupBusy());
                    } else {
                        plugin.getLogger().log(java.util.logging.Level.WARNING,
                                "Balance player lookup failed for " + input, error);
                    }
                    return;
                }
                if (target == null) {
                    sender.sendMessage(Messages.balanceUnknownPlayer(input));
                    return;
                }
                action.accept(target);
            };
            if (future.isDone()) continuation.run();
            else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, continuation);
        });
    }

    // ---- TabCompleter -------------------------------------------------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            java.util.List<String> options = new java.util.ArrayList<>();
            options.add("hide");
            if (sender.hasPermission(Permissions.ADMIN_BALANCE)) {
                options.add("set");
                options.add("add");
                options.add("remove");
            }

            // Also suggest online player names (for /balance <player>)
            if (sender.hasPermission(Permissions.ADMIN_BALANCE)) {
                Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            }

            return options.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("set") || sub.equals("add") || sub.equals("remove")) {
                if (sender.hasPermission(Permissions.ADMIN_BALANCE)) {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                            .sorted()
                            .collect(java.util.stream.Collectors.toList());
                }
            }
        }

        return List.of();
    }

    // ---- Helpers ------------------------------------------------------------

    private void sendOwnBalance(Player player) {
        UUID uuid = player.getUniqueId();
        double balance = economyManager.getBalance(uuid);
        player.sendMessage(Messages.balanceOwn(formatBalance(balance)));
    }

    /** Formats a double as a dollar-style currency string, e.g. $1,234. */
    public static String formatBalance(double amount) {
        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.US);
        fmt.setMaximumFractionDigits(0);
        return "$" + fmt.format(amount);
    }
}
