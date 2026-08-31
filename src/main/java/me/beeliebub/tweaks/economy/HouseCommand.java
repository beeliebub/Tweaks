package me.beeliebub.tweaks.economy;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingPaths;
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

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** Administrative command surface for the server-wide casino house account. */
public final class HouseCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final HouseAccount houseAccount;
    private final HousePaymentService housePaymentService;

    public HouseCommand(JavaPlugin plugin, HouseAccount houseAccount, HousePaymentService housePaymentService) {
        this.plugin = plugin;
        this.houseAccount = houseAccount;
        this.housePaymentService = housePaymentService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.HOUSE_ADMIN)) {
            sender.sendMessage(Messages.noPermission());
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Messages.houseUsage());
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("balance")) {
            if (!houseAccount.isLoaded()) {
                sender.sendMessage(Messages.houseStillLoading());
                return true;
            }
            return showBalance(sender, args);
        }

        // add/remove/set/pay all mutate the account; pay additionally routes through the durable
        // payment journal, so none of them may run until both the account and the payment service
        // (which replays any surviving journal entry from a prior crash) are ready. balance stays
        // readable above without waiting on the payment service.
        if (!houseAccount.isLoaded() || !housePaymentService.isReady()) {
            sender.sendMessage(Messages.houseStillLoading());
            return true;
        }

        return switch (sub) {
            case "add" -> add(sender, args);
            case "remove" -> remove(sender, args);
            case "set" -> set(sender, args);
            case "pay" -> pay(sender, args);
            default -> {
                sender.sendMessage(Messages.houseUsage());
                yield true;
            }
        };
    }

    private boolean showBalance(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Messages.houseUsage());
        } else {
            sender.sendMessage(Messages.houseBalance(houseAccount.balance()));
        }
        return true;
    }

    private boolean add(CommandSender sender, String[] args) {
        Long amount = positiveAmount(sender, args, "add <amount>");
        if (amount == null) return true;
        java.util.concurrent.CompletableFuture<Boolean> durable = houseAccount.creditDurably(amount);
        if (durable == null) durable = java.util.concurrent.CompletableFuture.completedFuture(houseAccount.credit(amount));
        durable.whenComplete((success, error) -> runOnMain(() -> {
            if (error != null || !Boolean.TRUE.equals(success)) {
                sender.sendMessage(error == null ? Messages.houseOverflow() : Messages.housePersistenceFailed());
                return;
            }
            sender.sendMessage(Messages.houseMutation("added", amount, houseAccount.balance()));
            logHouseMutation(sender, "added", amount);
        }));
        return true;
    }

    private void runOnMain(Runnable action) {
        if (!plugin.isEnabled() || Bukkit.isPrimaryThread()) {
            if (plugin.isEnabled()) action.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, action);
    }

    private boolean remove(CommandSender sender, String[] args) {
        Long amount = positiveAmount(sender, args, "remove <amount>");
        if (amount == null) return true;
        if (!houseAccount.debit(amount)) {
            sender.sendMessage(Messages.houseInsufficientFunds());
            return true;
        }
        sender.sendMessage(Messages.houseMutation("removed", amount, houseAccount.balance()));
        logHouseMutation(sender, "removed", amount);
        return true;
    }

    private boolean set(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Messages.houseUsage());
            return true;
        }
        try {
            long amount = Long.parseLong(args[1]);
            houseAccount.set(amount);
            sender.sendMessage(Messages.houseMutation("set to", amount, houseAccount.balance()));
            logHouseMutation(sender, "set to", amount);
        } catch (NumberFormatException exception) {
            sender.sendMessage(Messages.houseInvalidAmount(args[1]));
        }
        return true;
    }

    // Debit/credit are sequenced and journalled by HousePaymentService, so this only needs to
    // validate input, resolve the target, kick the transfer off, and report back whatever
    // eventually happens. The transfer itself is durable even if the ack below never gets seen.
    private boolean pay(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(Messages.houseUsage());
            return true;
        }
        Long amount = parsePositiveAmount(sender, args[2]);
        if (amount == null) return true;

        String targetInput = args[1];
        long resolvedAmount = amount;
        var future = OfflinePlayerResolver.resolve(plugin, sender, targetInput);
        boolean asynchronous = !future.isDone();
        future.whenComplete((target, error) -> {
            Runnable continuation = () -> {
                if (asynchronous && !OfflinePlayerResolver.isSenderOnline(sender)) return;
                if (error != null) {
                    if (OfflinePlayerResolver.isLookupInProgress(error)) {
                        sender.sendMessage(Messages.COMMANDS.offlinePlayerLookupBusy());
                    } else {
                        plugin.getLogger().log(Level.WARNING, "House player lookup failed for " + targetInput, error);
                    }
                    return;
                }
                if (target == null) {
                    deliverUnknownPlayer(sender, targetInput);
                    return;
                }
                String targetName = target.getName() != null ? target.getName() : targetInput;
                housePaymentService.pay(target.getUniqueId(), resolvedAmount)
                        .thenAccept(outcome -> deliverPayOutcome(sender, targetName, resolvedAmount, outcome));
            };
            if (future.isDone()) continuation.run();
            else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, continuation);
        });
        return true;
    }

    // Bukkit.getOfflinePlayer(String) "may involve a blocking web request" per its own Javadoc for
    // a name outside the local user cache — resolve it off the main thread so an admin paying a
    // rarely-seen name can't stall the whole server, consistent with the rest of this path already
    // being asynchronous. Returns null when the name matches neither an online nor a previously-seen
    // player, folding that check into the same off-thread hop.
    // Mirrors deliverPayOutcome's main-thread hop and disconnected-sender fallback, for the
    // "no such player" rejection that now also resolves off the main thread.
    private void deliverUnknownPlayer(CommandSender sender, String targetInput) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sender instanceof Player player && !player.isOnline()) return;
            sender.sendMessage(Messages.houseUnknownPlayer(targetInput));
        });
    }

    // housePaymentService.pay's future always resolves off the main thread, so this hops back
    // before touching sender/player API — never call this directly from the async continuation.
    // Falls back to console logging if the plugin has since disabled or the sender disconnected,
    // so the outcome (the transfer itself already landed durably either way) is never dropped silently.
    private void deliverPayOutcome(CommandSender sender, String targetName, long amount, HousePayOutcome outcome) {
        if (!plugin.isEnabled()) {
            logPayOutcome(targetName, amount, outcome);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sender instanceof Player player && !player.isOnline()) {
                logPayOutcome(targetName, amount, outcome);
                return;
            }
            sender.sendMessage(switch (outcome) {
                case SUCCESS -> Messages.housePaid(targetName, amount, houseAccount.balance());
                case INSUFFICIENT_FUNDS -> Messages.houseInsufficientFunds();
                case UNREPRESENTABLE_REFUNDED -> Messages.housePaymentUnrepresentable(targetName);
                case NEEDS_RECONCILIATION -> Messages.housePaymentNeedsReconciliation();
                case WRITE_FAILED_RETAINED -> Messages.housePaymentRetrying();
                case SHUTTING_DOWN -> Messages.houseShuttingDown();
                case NOT_READY -> Messages.houseStillLoading();
                case FAILED -> Messages.housePaymentFailed();
            });
            if (outcome == HousePayOutcome.SUCCESS) {
                String actorName = sender instanceof Player player ? player.getName() : null;
                java.util.UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
                ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
                if (eventLog != null) eventLog.log(LoggingPaths.ECONOMY_HOUSE_PAYMENT, () ->
                        "[Economy] " + ConsoleEventLog.actorLabel(actorName, actorId)
                                + " completed house payment of $" + amount + " to " + targetName);
            }
        });
    }

    private void logPayOutcome(String targetName, long amount, HousePayOutcome outcome) {
        Level level = switch (outcome) {
            case NEEDS_RECONCILIATION, FAILED -> Level.SEVERE;
            case WRITE_FAILED_RETAINED -> Level.WARNING;
            default -> Level.INFO;
        };
        plugin.getLogger().log(level, "House payment to " + targetName + " for $" + amount
                + " resolved as " + outcome + " (sender no longer reachable).");
        if (outcome == HousePayOutcome.SUCCESS) {
            ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
            if (eventLog != null) eventLog.log(LoggingPaths.ECONOMY_HOUSE_PAYMENT, () ->
                    "[Economy] (console) completed house payment of $" + amount + " to " + targetName);
        }
    }

    private void logHouseMutation(CommandSender sender, String operation, long amount) {
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog == null) return;
        String actorName = sender instanceof Player player ? player.getName() : null;
        java.util.UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
        eventLog.log(LoggingPaths.ECONOMY_HOUSE_MUTATED, () ->
                "[Economy] " + ConsoleEventLog.actorLabel(actorName, actorId)
                        + " " + operation + " house account by $" + amount);
    }

    private Long positiveAmount(CommandSender sender, String[] args, String usage) {
        if (args.length != 2) {
            sender.sendMessage(Messages.houseUsage());
            return null;
        }
        return parsePositiveAmount(sender, args[1]);
    }

    private Long parsePositiveAmount(CommandSender sender, String input) {
        try {
            long amount = Long.parseLong(input);
            if (amount <= 0 || amount > EconomyManager.MAX_BALANCE) {
                sender.sendMessage(Messages.houseInvalidAmount(input));
                return null;
            }
            return amount;
        } catch (NumberFormatException exception) {
            sender.sendMessage(Messages.houseInvalidAmount(input));
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.HOUSE_ADMIN) || args.length != 1) {
            return List.of();
        }
        String partial = args[0].toLowerCase(Locale.ROOT);
        return List.of("balance", "add", "remove", "set", "pay").stream()
                .filter(option -> option.startsWith(partial))
                .toList();
    }
}
