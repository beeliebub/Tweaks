package me.beeliebub.tweaks.discord;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.commands.PluginSlashCommand;
import github.scarsz.discordsrv.api.commands.SlashCommand;
import github.scarsz.discordsrv.api.commands.SlashCommandProvider;
import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.SlashCommandEvent;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.OptionType;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.build.CommandData;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.build.OptionData;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.InteractionHook;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.minigames.roulette.BetType;
import me.beeliebub.tweaks.minigames.roulette.DiscordBetOutcome;
import me.beeliebub.tweaks.minigames.roulette.DiscordBetResult;
import me.beeliebub.tweaks.minigames.roulette.DiscordBoardStatus;
import me.beeliebub.tweaks.minigames.roulette.RouletteBet;
import me.beeliebub.tweaks.minigames.roulette.RouletteListener;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Optional inbound DiscordSRV slash-command provider for Roulette. */
final class DiscordRouletteBridge implements SlashCommandProvider {

    private static final long BET_COOLDOWN_MILLIS = 2_000L;

    private final Tweaks plugin;
    private final RouletteListener roulette;
    private final EconomyManager economy;
    private final Set<PluginSlashCommand> commands = new HashSet<>();
    private final Map<String, Long> betCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, InteractionHook> pendingBetHooks = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<Void>> followUps = new CopyOnWriteArraySet<>();

    DiscordRouletteBridge(Tweaks plugin, RouletteListener roulette, EconomyManager economy) {
        this.plugin = plugin;
        this.roulette = roulette;
        this.economy = economy;
        commands.add(new PluginSlashCommand(plugin, new CommandData("balance", "Show your balance")));
        commands.add(new PluginSlashCommand(plugin, new CommandData("roulette", "Show Roulette status")));
        commands.add(new PluginSlashCommand(plugin, new CommandData("mybets", "Show your current Roulette bets")));
        OptionData amount = new OptionData(OptionType.INTEGER, "amount", "Wager amount", true)
                .setMinValue(1);
        OptionData pocket = new OptionData(OptionType.INTEGER, "pocket", "Straight-up pocket 0-36", false)
                .setMinValue(0).setMaxValue(36);
        OptionData dozen = new OptionData(OptionType.INTEGER, "dozen", "Dozen 1-3", false)
                .setMinValue(1).setMaxValue(3);
        OptionData color = new OptionData(OptionType.STRING, "color", "red or black", false)
                .addChoice("red", "red").addChoice("black", "black");
        CommandData bet = new CommandData("bet", "Place a Roulette bet")
                .addOptions(amount, pocket, dozen, color);
        commands.add(new PluginSlashCommand(plugin, bet));
    }

    @Override
    public Set<PluginSlashCommand> getSlashCommands() {
        return Set.copyOf(commands);
    }

    @SlashCommand(path = "balance", deferReply = true, deferEphemeral = true,
            ignoreAcknowledged = true)
    public void balance(SlashCommandEvent event) {
        dispatch(event, false, playerId -> {
            economy.loadPlayer(playerId);
            return Messages.ROULETTE_DISCORD_COMMANDS.balance(economy.getBalance(playerId));
        });
    }

    @SlashCommand(path = "roulette", deferReply = true, deferEphemeral = true,
            ignoreAcknowledged = true)
    public void roulette(SlashCommandEvent event) {
        dispatch(event, false, playerId -> {
            return Messages.ROULETTE_DISCORD_COMMANDS.roulette(roulette.discordBoardStatus());
        });
    }

    @SlashCommand(path = "mybets", deferReply = true, deferEphemeral = true,
            ignoreAcknowledged = true)
    public void myBets(SlashCommandEvent event) {
        dispatch(event, false, playerId -> {
            economy.loadPlayer(playerId);
            return Messages.ROULETTE_DISCORD_COMMANDS.myBets(roulette.discordBetsFor(playerId));
        });
    }

    @SlashCommand(path = "bet", deferReply = true, deferEphemeral = true,
            ignoreAcknowledged = true)
    public void bet(SlashCommandEvent event) {
        if (!plugin.getConfig().getBoolean("discord.betting-enabled", true)) {
            reply(event, Messages.ROULETTE_DISCORD_COMMANDS.bettingDisabled());
            return;
        }
        if (!channelAllowed(event)) {
            reply(event, Messages.ROULETTE_DISCORD_COMMANDS.wrongChannel());
            return;
        }
        String discordId = event.getUser().getId();
        long now = System.currentTimeMillis();
        boolean[] allowed = {false};
        betCooldowns.compute(discordId, (key, previous) -> {
            if (previous != null && now - previous < BET_COOLDOWN_MILLIS) {
                return previous;
            }
            allowed[0] = true;
            return now;
        });
        if (!allowed[0]) {
            reply(event, Messages.ROULETTE_DISCORD_COMMANDS.cooldown());
            return;
        }
        BetRequest request = parseBet(event);
        if (request == null) {
            reply(event, Messages.ROULETTE_DISCORD_COMMANDS.invalidBet());
            return;
        }
        dispatch(event, true, playerId -> {
            DiscordBetResult result = roulette.placeDiscordBet(playerId, request.type(), request.selector(),
                    request.amount());
            if (result.outcome() == DiscordBetOutcome.PLACED) {
                registerInteractionHook(playerId, event.getHook());
            }
            return formatBetResult(result, request);
        });
    }

    private void dispatch(SlashCommandEvent event, boolean bet, ReplyOperation operation) {
        if (!bet && !channelAllowed(event)) {
            reply(event, Messages.ROULETTE_DISCORD_COMMANDS.wrongChannel());
            return;
        }
        UUID playerId = linkedPlayer(event);
        if (playerId == null) {
            reply(event, Messages.ROULETTE_DISCORD_COMMANDS.accountNotLinked());
            return;
        }
        if (!plugin.isEnabled() || roulette == null) {
            reply(event, Messages.ROULETTE_DISCORD_COMMANDS.serverRestarting());
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    reply(event, operation.run(playerId));
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.WARNING, "Discord Roulette command failed", throwable);
                    reply(event, Messages.ROULETTE_DISCORD_COMMANDS.unavailable());
                }
            });
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Discord Roulette command could not be scheduled", throwable);
            reply(event, Messages.ROULETTE_DISCORD_COMMANDS.unavailable());
        }
    }

    private UUID linkedPlayer(SlashCommandEvent event) {
        try {
            return DiscordSRV.getPlugin().getAccountLinkManager().getUuidFromCache(event.getUser().getId());
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "DiscordSRV account-link lookup failed", throwable);
            return null;
        }
    }

    private boolean channelAllowed(SlashCommandEvent event) {
        String configured = plugin.getConfig().getString("discord.betting-channel-id", "");
        return configured != null && !configured.isBlank()
                && configured.trim().equals(event.getChannel().getId());
    }

    private BetRequest parseBet(SlashCommandEvent event) {
        Map<String, String> options = new HashMap<>();
        event.getOptions().forEach(option -> options.put(option.getName(), option.getAsString()));
        if (options.size() < 2 || !options.containsKey("amount")) return null;
        int targetCount = (options.containsKey("pocket") ? 1 : 0)
                + (options.containsKey("dozen") ? 1 : 0)
                + (options.containsKey("color") ? 1 : 0);
        if (targetCount != 1) return null;
        try {
            long rawAmount = Long.parseLong(options.get("amount"));
            if (rawAmount < Integer.MIN_VALUE || rawAmount > Integer.MAX_VALUE) return null;
            int amount = (int) rawAmount;
            if (options.containsKey("pocket")) {
                return new BetRequest(amount, BetType.STRAIGHT, Integer.parseInt(options.get("pocket")));
            }
            if (options.containsKey("dozen")) {
                return new BetRequest(amount, BetType.DOZEN, Integer.parseInt(options.get("dozen")));
            }
            String color = options.get("color");
            int selector = switch (color.toLowerCase(java.util.Locale.ROOT)) {
                case "red" -> RouletteBet.COLOR_RED;
                case "black" -> RouletteBet.COLOR_BLACK;
                default -> throw new IllegalArgumentException("unknown color");
            };
            return new BetRequest(amount, BetType.COLOR, selector);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String formatBetResult(DiscordBetResult result, BetRequest request) {
        return switch (result.outcome()) {
            case NO_DESIGNATED_BOARD -> Messages.ROULETTE_DISCORD_COMMANDS.noDesignatedBoard();
            case BOARD_UNAVAILABLE -> Messages.ROULETTE_DISCORD_COMMANDS.boardUnavailable();
            case BETTING_CLOSED -> Messages.ROULETTE_DISCORD_COMMANDS.bettingClosed();
            case INVALID_BET -> Messages.ROULETTE_DISCORD_COMMANDS.invalidBet();
            case EXPOSURE_LIMIT -> Messages.ROULETTE_DISCORD_COMMANDS.exposureLimit();
            case INSUFFICIENT_FUNDS -> Messages.ROULETTE_DISCORD_COMMANDS.insufficientFunds();
            case BALANCE_UNREPRESENTABLE -> Messages.ROULETTE_DISCORD_COMMANDS.balanceUnrepresentable();
            case REJECTED -> Messages.ROULETTE_DISCORD_COMMANDS.betRejected();
            case PLACED -> Messages.ROULETTE_DISCORD_COMMANDS.betPlaced(result.amount(),
                    Messages.ROULETTE_DISCORD_COMMANDS.target(request.type(), request.selector()),
                    result.multiplier());
        };
    }

    void registerInteractionHook(UUID playerId, InteractionHook hook) {
        if (hook == null || hook.isExpired()) return;
        pendingBetHooks.put(playerId, hook);
    }

    void announceOutcome(UUID playerId, String message) {
        InteractionHook hook = pendingBetHooks.remove(playerId);
        if (hook == null) return;
        try {
            if (hook.isExpired()) {
                plugin.getLogger().fine("Skipping expired Discord Roulette interaction hook for " + playerId);
                return;
            }
            String discordId = DiscordSRV.getPlugin().getAccountLinkManager()
                    .getDiscordIdFromCache(playerId);
            String mention = discordId == null || discordId.isBlank() ? "" : "<@" + discordId + "> ";
            CompletableFuture<Void> completion = new CompletableFuture<>();
            followUps.add(completion);
            hook.setEphemeral(true).sendMessage(mention + message).queue(
                    ignored -> completeFollowUp(completion, null),
                    failure -> completeFollowUp(completion, failure));
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Discord Roulette outcome follow-up failed for " + playerId,
                    throwable);
        }
    }

    private void completeFollowUp(CompletableFuture<Void> completion, Throwable failure) {
        followUps.remove(completion);
        if (failure == null) {
            completion.complete(null);
        } else {
            plugin.getLogger().log(Level.WARNING, "Discord Roulette outcome follow-up delivery failed", failure);
            completion.completeExceptionally(failure);
        }
    }

    void clearPendingHooks() {
        pendingBetHooks.clear();
    }

    boolean hasPendingFollowUps() {
        return !pendingBetHooks.isEmpty() || !followUps.isEmpty();
    }

    void awaitFollowUps() {
        CompletableFuture<?>[] pending = followUps.toArray(CompletableFuture[]::new);
        if (pending.length == 0) return;
        try {
            CompletableFuture.allOf(pending).get(1_800L, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Discord Roulette outcome follow-ups exceeded the shutdown wait", e);
        } finally {
            clearPendingHooks();
        }
    }

    private void reply(SlashCommandEvent event, String message) {
        try {
            if (event.getHook() == null || event.getHook().isExpired()) return;
            event.getHook().setEphemeral(true).sendMessage(message).queue(
                    ignored -> {},
                    failure -> plugin.getLogger().log(Level.WARNING,
                            "Discord Roulette reply failed", failure));
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Discord Roulette reply setup failed", throwable);
        }
    }

    private record BetRequest(int amount, BetType type, int selector) {
    }

    @FunctionalInterface
    private interface ReplyOperation {
        String run(UUID playerId);
    }
}
