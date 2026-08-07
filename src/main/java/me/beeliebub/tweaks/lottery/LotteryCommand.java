package me.beeliebub.tweaks.lottery;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.economy.HousePaymentService;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** Legacy command surface for lottery information and administration. */
public final class LotteryCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final LotteryManager manager;
    private final HouseAccount houseAccount;
    private final HousePaymentService housePaymentService;

    public LotteryCommand(JavaPlugin plugin, LotteryManager manager, HouseAccount houseAccount,
                          HousePaymentService housePaymentService) {
        this.plugin = plugin;
        this.manager = manager;
        this.houseAccount = houseAccount;
        this.housePaymentService = housePaymentService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("info")) {
            showInfo(sender, args);
            return true;
        }
        if (!sender.hasPermission(Permissions.LOTTERY_ADMIN)) {
            sender.sendMessage(Messages.LOTTERY.noPermission());
            return true;
        }
        switch (sub) {
            case "draw" -> draw(sender, args);
            case "entries" -> showEntries(sender, args);
            case "baseline" -> setBaseline(sender, args);
            default -> sender.sendMessage(Messages.LOTTERY.usage());
        }
        return true;
    }

    private void showInfo(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Messages.LOTTERY.usage());
            return;
        }
        if (!manager.isLoaded() || !houseAccount.isLoaded()) {
            sender.sendMessage(Messages.LOTTERY.loading());
            return;
        }
        LotteryMath.PotOutcome outcome = manager.currentPot();
        String pot = outcome instanceof LotteryMath.PotOutcome.Payable payable
                ? "$" + payable.pot() : "unavailable";
        sender.sendMessage(Messages.LOTTERY.info(manager.entrantCount(), manager.baseline(), pot));
    }

    private void showEntries(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Messages.LOTTERY.usage());
            return;
        }
        if (!manager.isLoaded()) {
            sender.sendMessage(Messages.LOTTERY.loading());
            return;
        }
        List<java.util.UUID> entrants = manager.entrantSnapshot();
        sender.sendMessage(Messages.LOTTERY.entriesHeader(entrants.size()));
        int limit = Math.min(entrants.size(), 50);
        for (int i = 0; i < limit; i++) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entrants.get(i));
            sender.sendMessage(Messages.LOTTERY.entry(player.getName() == null
                    ? entrants.get(i).toString() : player.getName()));
        }
        if (entrants.size() > limit) sender.sendMessage(Messages.LOTTERY.entriesTruncated(entrants.size() - limit));
    }

    private void setBaseline(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Messages.LOTTERY.usage());
            return;
        }
        long baseline;
        try {
            baseline = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Messages.LOTTERY.baselineInvalid(args[1]));
            return;
        }
        if (baseline < 0) {
            sender.sendMessage(Messages.LOTTERY.baselineInvalid(args[1]));
            return;
        }
        manager.setBaseline(baseline).thenAccept(success -> deliver(sender,
                success ? Messages.LOTTERY.baselineUpdated(baseline) : Messages.LOTTERY.loading()));
    }

    private void draw(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Messages.LOTTERY.usage());
            return;
        }
        if (!manager.isLoaded()) {
            sender.sendMessage(Messages.LOTTERY.drawNotReady());
            return;
        }
        if (!housePaymentService.isReady()) {
            sender.sendMessage(Messages.LOTTERY.paymentLoading());
            return;
        }
        manager.draw().thenAccept(result -> deliverDraw(sender, result));
    }

    private void deliverDraw(CommandSender sender, LotteryManager.DrawResult result) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (result instanceof LotteryManager.DrawResult.NotReady) {
                sender.sendMessage(Messages.LOTTERY.drawNotReady());
            } else if (result instanceof LotteryManager.DrawResult.InFlight) {
                sender.sendMessage(Messages.LOTTERY.drawInFlight());
            } else if (result instanceof LotteryManager.DrawResult.Refused refused) {
                sender.sendMessage(Messages.LOTTERY.drawRefused(refused.reason()));
            } else if (result instanceof LotteryManager.DrawResult.PaymentFailed failed) {
                sender.sendMessage(Messages.LOTTERY.paymentFailed(failed.outcome().name()));
            } else if (result instanceof LotteryManager.DrawResult.Awarded awarded) {
                OfflinePlayer winner = Bukkit.getOfflinePlayer(awarded.winner());
                String name = winner.getName() == null ? awarded.winner().toString() : winner.getName();
                Bukkit.broadcast(Messages.LOTTERY.winner(name, awarded.amount()));
            }
        });
    }

    private void deliver(CommandSender sender, Component message) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        String partial = args[0].toLowerCase(Locale.ROOT);
        List<String> options = sender.hasPermission(Permissions.LOTTERY_ADMIN)
                ? List.of("info", "draw", "entries", "baseline") : List.of("info");
        return options.stream().filter(option -> option.startsWith(partial)).toList();
    }
}
