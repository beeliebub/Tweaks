package me.beeliebub.tweaks.lottery;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.discord.DiscordAnnouncer;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.economy.HousePaymentService;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingPaths;
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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Legacy command surface for lottery information and administration. */
public final class LotteryCommand implements CommandExecutor, TabCompleter {

    private static final long ENTRIES_COOLDOWN_MILLIS = 3_000L;

    private final JavaPlugin plugin;
    private final LotteryManager manager;
    private final HouseAccount houseAccount;
    private final HousePaymentService housePaymentService;
    private final DiscordAnnouncer discordAnnouncer;
    private final Map<UUID, Long> entriesCooldowns = new HashMap<>();

    public LotteryCommand(JavaPlugin plugin, LotteryManager manager, HouseAccount houseAccount,
                          HousePaymentService housePaymentService) {
        this(plugin, manager, houseAccount, housePaymentService, DiscordAnnouncer.NOOP);
    }

    public LotteryCommand(JavaPlugin plugin, LotteryManager manager, HouseAccount houseAccount,
                          HousePaymentService housePaymentService, DiscordAnnouncer discordAnnouncer) {
        this.plugin = plugin;
        this.manager = manager;
        this.houseAccount = houseAccount;
        this.housePaymentService = housePaymentService;
        this.discordAnnouncer = discordAnnouncer == null ? DiscordAnnouncer.NOOP : discordAnnouncer;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("info")) {
            showInfo(sender, args);
            return true;
        }
        if (sub.equals("entries")) {
            showEntries(sender, args);
            return true;
        }
        if (!sender.hasPermission(Permissions.LOTTERY_ADMIN)) {
            sender.sendMessage(Messages.LOTTERY.noPermission());
            return true;
        }
        switch (sub) {
            case "draw" -> draw(sender, args);
            case "baseline" -> setBaseline(sender, args);
            case "fallback" -> setFallback(sender, args);
            default -> sender.sendMessage(Messages.LOTTERY.usage());
        }
        return true;
    }

    private void showInfo(CommandSender sender, String[] args) {
        if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("info"))) {
            sender.sendMessage(Messages.LOTTERY.usage());
            return;
        }
        if (!manager.isLoaded() || !houseAccount.isLoaded()) {
            sender.sendMessage(Messages.LOTTERY.loading());
            return;
        }
        LotteryMath.PotOutcome outcome = manager.currentPot();
        for (Component line : Messages.LOTTERY.info(manager.entrantCount(), manager.baseline(), manager.fallback(),
                manager.configuredFallbackBase(), outcome)) {
            sender.sendMessage(line);
        }
    }

    private void showEntries(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Messages.LOTTERY.usage());
            return;
        }
        if (entriesCooldown(sender)) return;
        if (!manager.isLoaded()) {
            sender.sendMessage(Messages.LOTTERY.loading());
            return;
        }
        LotteryManager.EntrantPage page = manager.entrantSnapshot(50);
        sender.sendMessage(Messages.LOTTERY.entriesHeader(page.total()));
        for (UUID entrant : page.shown()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entrant);
            sender.sendMessage(Messages.LOTTERY.entry(player.getName() == null
                    ? entrant.toString() : player.getName()));
        }
        if (page.total() > page.shown().size()) {
            sender.sendMessage(Messages.LOTTERY.entriesTruncated(page.total() - page.shown().size()));
        }
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
        String actorName = sender instanceof org.bukkit.entity.Player player ? player.getName() : null;
        UUID actorId = sender instanceof org.bukkit.entity.Player player ? player.getUniqueId() : null;
        manager.setBaseline(baseline).whenComplete((success, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Lottery baseline update failed", error);
                deliver(sender, Messages.LOTTERY.baselineFailed());
            } else {
                if (success) {
                    ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
                    if (eventLog != null) eventLog.log(LoggingPaths.LOTTERY_BASELINE, () ->
                            "[Lottery] " + ConsoleEventLog.actorLabel(actorName, actorId)
                                    + " set baseline to " + baseline);
                }
                deliver(sender, success ? Messages.LOTTERY.baselineUpdated(baseline) : Messages.LOTTERY.loading());
            }
        });
    }

    private void setFallback(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (!manager.isLoaded()) {
                sender.sendMessage(Messages.LOTTERY.loading());
                return;
            }
            sender.sendMessage(Messages.LOTTERY.fallbackStatus(manager.fallback(), manager.configuredFallbackBase()));
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(Messages.LOTTERY.usage());
            return;
        }
        long fallback;
        try {
            fallback = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Messages.LOTTERY.fallbackInvalid(args[1]));
            return;
        }
        if (fallback < 0) {
            sender.sendMessage(Messages.LOTTERY.fallbackInvalid(args[1]));
            return;
        }
        String actorName = sender instanceof org.bukkit.entity.Player player ? player.getName() : null;
        UUID actorId = sender instanceof org.bukkit.entity.Player player ? player.getUniqueId() : null;
        manager.setFallback(fallback).whenComplete((success, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Lottery fallback update failed", error);
                deliver(sender, Messages.LOTTERY.fallbackFailed());
            } else {
                if (success) {
                    ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
                    if (eventLog != null) eventLog.log(LoggingPaths.LOTTERY_FALLBACK, () ->
                            "[Lottery] " + ConsoleEventLog.actorLabel(actorName, actorId)
                                    + " set fallback to " + fallback);
                }
                deliver(sender, success ? Messages.LOTTERY.fallbackUpdated(fallback) : Messages.LOTTERY.loading());
            }
        });
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
        manager.draw().whenComplete((result, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Lottery draw failed before its outcome was known", error);
                deliver(sender, Messages.LOTTERY.drawFailed());
                return;
            }
            deliverDraw(sender, result);
        });
    }

    private void deliverDraw(CommandSender sender, LotteryManager.DrawResult result) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
            if (eventLog != null) eventLog.log(LoggingPaths.LOTTERY_DRAW, () ->
                    "[Lottery] (console) draw completed with " + result.getClass().getSimpleName());
            switch (result) {
                case LotteryManager.DrawResult.NotReady ignored -> sender.sendMessage(Messages.LOTTERY.drawNotReady());
                case LotteryManager.DrawResult.InFlight ignored -> sender.sendMessage(Messages.LOTTERY.drawInFlight());
                case LotteryManager.DrawResult.NotEnoughEntrants notEnough -> {
                    Bukkit.broadcast(Messages.LOTTERY.notEnoughEntries());
                    if (notEnough.rolledIn() > 0) {
                        sender.sendMessage(Messages.LOTTERY.rollInDetail(notEnough.rolledIn(), notEnough.fallback(),
                                notEnough.baseline()));
                    }
                }
                case LotteryManager.DrawResult.Refused refused -> {
                    if (refused.reason() == LotteryMath.RefusalReason.NO_GROWTH) {
                        Bukkit.broadcast(Messages.LOTTERY.noGrowthBroadcast());
                    } else if (refused.reason() == LotteryMath.RefusalReason.NOT_ENOUGH_ENTRANTS) {
                        Bukkit.broadcast(Messages.LOTTERY.notEnoughEntries());
                    } else {
                        sender.sendMessage(Messages.LOTTERY.drawRefused(refused.reason()));
                    }
                }
                case LotteryManager.DrawResult.PaymentAbandoned abandoned -> {
                    sender.sendMessage(Messages.LOTTERY.paymentAbandoned(abandoned.outcome().name(),
                            abandoned.entrantCount()));
                    Bukkit.broadcast(Messages.LOTTERY.paymentAbandonedBroadcast(abandoned.entrantCount()));
                }
                case LotteryManager.DrawResult.PaymentPending pending ->
                        sender.sendMessage(Messages.LOTTERY.paymentPending(pending.paymentId(), pending.outcome().name()));
                case LotteryManager.DrawResult.PaymentStuck stuck ->
                        sender.sendMessage(Messages.LOTTERY.paymentStuck(stuck.paymentId()));
                case LotteryManager.DrawResult.Awarded awarded -> {
                    OfflinePlayer winner = Bukkit.getOfflinePlayer(awarded.winner());
                    String name = winner.getName() == null ? awarded.winner().toString() : winner.getName();
                    Bukkit.broadcast(Messages.LOTTERY.winner(name, awarded.amount()));
                    announceCardSafely(Messages.LOTTERY_DISCORD.winner(name, awarded.amount()),
                            Messages.LOTTERY_DISCORD.YELLOW, winner);
                }
            }
        });
    }

    private void announceCardSafely(String message, int color, OfflinePlayer subject) {
        try {
            discordAnnouncer.announceCard(message, color, subject);
        } catch (Throwable throwable) {
            // The in-game broadcast is the primary outcome. Keep optional Discord linkage,
            // formatter, and injected-announcer failures from aborting the draw task.
            plugin.getLogger().log(Level.WARNING, "Lottery Discord announcement failed", throwable);
        }
    }

    private void deliver(CommandSender sender, Component message) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
    }

    private boolean entriesCooldown(CommandSender sender) {
        long now = System.currentTimeMillis();
        entriesCooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= ENTRIES_COOLDOWN_MILLIS);
        if (!(sender instanceof org.bukkit.entity.Player player)
                || sender.hasPermission(Permissions.LOTTERY_ADMIN)) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        Long lastRequest = entriesCooldowns.get(playerId);
        if (lastRequest != null && now - lastRequest < ENTRIES_COOLDOWN_MILLIS) {
            sender.sendMessage(Messages.LOTTERY.entriesCooldown());
            return true;
        }
        entriesCooldowns.put(playerId, now);
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        String partial = args[0].toLowerCase(Locale.ROOT);
        List<String> options = sender.hasPermission(Permissions.LOTTERY_ADMIN)
                ? List.of("info", "entries", "draw", "baseline", "fallback") : List.of("info", "entries");
        return options.stream().filter(option -> option.startsWith(partial)).toList();
    }
}
