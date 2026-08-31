package me.beeliebub.tweaks.discord;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.Permission;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.entities.VoiceChannel;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.WebhookUtil;
import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** DiscordSRV-backed implementation of the optional Discord seam. */
final class DiscordSrvAnnouncer implements DiscordAnnouncer {

    private final Tweaks plugin;
    private final SettlementLineBuffer settlementBuffer;
    private final SlashCommandPushPolicy slashCommandPushPolicy = new SlashCommandPushPolicy();
    private String bufferedChannelId;
    private DiscordRouletteBridge rouletteBridge;
    private DiscordReadyListener readyListener;
    private volatile DiscordConfigSnapshot configSnapshot = DiscordConfigSnapshot.from(null);
    private volatile String lastPushedChannelId;
    private volatile boolean lastPushResolvedGuild;
    private String lastSlashPreflightReport;

    DiscordSrvAnnouncer(Tweaks plugin) {
        this.plugin = plugin;
        this.settlementBuffer = new SettlementLineBuffer(
                message -> plugin.getLogger().warning(message),
                message -> plugin.getLogger().info(message));
    }

    void registerRouletteBridge(me.beeliebub.tweaks.minigames.roulette.RouletteListener roulette,
                                me.beeliebub.tweaks.economy.EconomyManager economy) {
        DiscordRouletteBridge bridge = new DiscordRouletteBridge(plugin, roulette, economy,
                this::configSnapshot);
        try {
            DiscordSRV.api.addSlashCommandProvider(bridge);
            rouletteBridge = bridge;
        } catch (Throwable throwable) {
            rouletteBridge = null;
            logFailure("Discord Roulette slash-command provider registration failed", throwable);
        }
    }

    void unregisterRouletteBridge() {
        DiscordRouletteBridge bridge = rouletteBridge;
        if (bridge == null) return;
        try {
            DiscordSRV.api.removeSlashCommandProvider(bridge);
        } catch (Throwable throwable) {
            logFailure("Discord Roulette slash-command removal failed", throwable);
        } finally {
            lastPushedChannelId = null;
            lastPushResolvedGuild = false;
            pushSlashCommands();
        }
    }

    void subscribeReadyListener() {
        if (readyListener != null) return;
        DiscordReadyListener listener = new DiscordReadyListener(plugin, this);
        try {
            DiscordSRV.api.subscribe(listener);
            readyListener = listener;
        } catch (Throwable throwable) {
            logFailure("Discord Roulette ready listener subscription failed", throwable);
        }
    }

    void unsubscribeReadyListener() {
        DiscordReadyListener listener = readyListener;
        readyListener = null;
        if (listener != null) DiscordSRV.api.unsubscribe(listener);
    }

    void refreshConfigSnapshot() {
        configSnapshot = DiscordConfigSnapshot.from(
                plugin.getConfig().getConfigurationSection("discord"));
    }

    DiscordConfigSnapshot configSnapshot() {
        return configSnapshot;
    }

    boolean isDiscordReady() {
        return DiscordSRV.isReady;
    }

    String lastPushedChannelId() {
        return lastPushedChannelId;
    }

    boolean lastPushResolvedGuild() {
        return lastPushResolvedGuild;
    }

    synchronized SlashCommandPushResult pushSlashCommands() {
        try {
            DiscordConfigSnapshot snapshot = configSnapshot();
            boolean ready = DiscordSRV.isReady;
            var jda = ready ? DiscordSRV.getPlugin().getJda() : null;
            int guildCount = jda == null ? 0 : jda.getGuilds().size();
            SlashCommandPushPolicy.Decision decision = slashCommandPushPolicy.decide(
                    ready, jda != null, guildCount, snapshot.bettingChannelId(),
                    lastPushedChannelId, lastPushResolvedGuild);
            if (decision != SlashCommandPushPolicy.Decision.PUSH) {
                return new SlashCommandPushResult(statusFor(decision), null);
            }

            DiscordRouletteBridge bridge = rouletteBridge;
            if (bridge == null) {
                return new SlashCommandPushResult(SlashCommandPushStatus.SKIPPED_NO_PROVIDER, null);
            }
            SlashCommandPreflight preflight = resolveSlashCommandPreflight(snapshot.bettingChannelId(), jda);
            bridge.setGuildResolution(preflight.guildId());

            DiscordSRV.api.updateSlashCommands();
            lastPushedChannelId = snapshot.bettingChannelId();
            lastPushResolvedGuild = preflight != null && preflight.resolvesGuild();
            if (preflight != null) reportSlashCommandPreflight(preflight);
            return new SlashCommandPushResult(SlashCommandPushStatus.PUSHED, preflight);
        } catch (Throwable throwable) {
            logFailure("Discord Roulette slash-command push failed", throwable);
            return new SlashCommandPushResult(SlashCommandPushStatus.FAILED, null);
        }
    }

    private SlashCommandPushStatus statusFor(SlashCommandPushPolicy.Decision decision) {
        return switch (decision) {
            case PUSH -> SlashCommandPushStatus.PUSHED;
            case SKIP_NOT_READY -> SlashCommandPushStatus.SKIPPED_NOT_READY;
            case SKIP_NO_GUILDS -> SlashCommandPushStatus.SKIPPED_NO_GUILDS;
            case SKIP_UNCHANGED -> SlashCommandPushStatus.SKIPPED_UNCHANGED;
        };
    }

    private SlashCommandPreflight resolveSlashCommandPreflight(String channelId, JDA jda) {
        if (channelId.isBlank()) return SlashCommandPreflight.disabled();
        if (jda == null) return SlashCommandPreflight.notReady(channelId);

        try {
            var guildChannel = jda.getGuildChannelById(channelId);
            if (guildChannel == null) {
                return unresolvedSlashChannel(channelId);
            }
            if (!(guildChannel instanceof TextChannel channel)) {
                return SlashCommandPreflight.nonText(channelId,
                        "Discord Roulette betting channel " + channelId + " resolves to a non-text channel ("
                                + guildChannel.getClass().getSimpleName() + "); choose a guild text channel.");
            }
            return SlashCommandPreflight.success(channelId, channel.getName(),
                    channel.getGuild().getId(), channel.getGuild().getName());
        } catch (Throwable throwable) {
            return unresolvedSlashChannel(channelId);
        }
    }

    private SlashCommandPreflight unresolvedSlashChannel(String channelId) {
        return SlashCommandPreflight.unresolved(channelId,
                "Discord Roulette slash commands could not resolve betting channel " + channelId
                        + "; verify the channel ID and the bot's guild authorization, including the "
                        + "applications.commands scope (https://scarsz.me/authorize).");
    }

    private void reportSlashCommandPreflight(SlashCommandPreflight report) {
        String state = report.status() + "|" + report.channelId() + "|" + report.guildId()
                + "|" + report.problem();
        if (state.equals(lastSlashPreflightReport)) return;
        lastSlashPreflightReport = state;
        switch (report.status()) {
            case DISABLED -> plugin.getLogger().info(
                    "Discord Roulette slash commands are disabled because discord.betting-channel-id is blank.");
            case SUCCESS -> plugin.getLogger().info("Discord Roulette slash commands are configured for #"
                    + report.channelName() + " (" + report.channelId() + ") in guild "
                    + report.guildName() + " (" + report.guildId() + ").");
            case UNRESOLVED, NON_TEXT -> plugin.getLogger().warning(report.problem());
            case NOT_READY -> {
                // A not-ready report is not emitted because the push guard handles that state first.
            }
        }
    }

    void clearRouletteBridge() {
        DiscordRouletteBridge bridge = rouletteBridge;
        rouletteBridge = null;
        if (bridge != null) bridge.clearPendingHooks();
    }

    boolean hasPendingRouletteFollowUps() {
        DiscordRouletteBridge bridge = rouletteBridge;
        return bridge != null && bridge.hasPendingFollowUps();
    }

    void flushRouletteFollowUps() {
        DiscordRouletteBridge bridge = rouletteBridge;
        if (bridge != null) bridge.awaitFollowUps();
    }

    @Override
    public void announceCard(String message, int color, OfflinePlayer subject) {
        try {
            String channelId = configuredAnnouncementChannel();
            if (channelId.isBlank() || !DiscordSRV.isReady) return;
            String webhookName = configuredWebhookName();
            String webhookAvatarUrl = configuredWebhookAvatarUrl();
            MessageEmbed embed = new EmbedBuilder()
                    .setDescription(message)
                    .setColor(color)
                    .build();

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    TextChannel channel = DiscordUtil.getTextChannelById(channelId);
                    if (channel == null) return;
                    if (subject != null) {
                        WebhookUtil.deliverMessage(channel, subject, webhookName, "", embed);
                    } else {
                        // This overload resolves or creates the channel webhook before its own
                        // scheduleAsync flag is consulted, so it must never be called on the
                        // server thread when the per-channel webhook cache is cold.
                        WebhookUtil.deliverMessage(channel, webhookName, webhookAvatarUrl, "", embed);
                    }
                } catch (Throwable throwable) {
                    logFailure("Discord announcement delivery failed", throwable);
                }
            });
        } catch (Throwable throwable) {
            // DiscordSRV is optional and its API is supplied by another plugin. Contain linkage
            // errors as well as ordinary exceptions so a mismatched optional dependency cannot
            // suppress the in-game announcement that preceded this call.
            logFailure("Discord announcement setup failed", throwable);
        }
    }

    @Override
    public void announceSettlement(SettlementLine line) {
        try {
            String channelId = configuredAnnouncementChannel();
            if (channelId.isBlank()) {
                settlementBuffer.clear();
                bufferedChannelId = null;
                return;
            }
            if (bufferedChannelId != null && !bufferedChannelId.equals(channelId)) {
                // Do not reroute lines created for an old channel to a newly edited ID.
                settlementBuffer.clear();
            }
            bufferedChannelId = channelId;
            settlementBuffer.add(line);
        } catch (Throwable throwable) {
            logFailure("Discord settlement announcement could not be buffered", throwable);
        }
    }

    @Override
    public void announceRouletteOutcome(java.util.UUID playerId, String message) {
        DiscordRouletteBridge bridge = rouletteBridge;
        if (bridge != null) {
            bridge.announceOutcome(playerId, message);
        }
    }

    @Override
    public void clearRouletteBetHooks() {
        DiscordRouletteBridge bridge = rouletteBridge;
        if (bridge != null) {
            bridge.clearPendingHooks();
        }
    }

    @Override
    public void renameChannel(String channelId, String name) {
        renameChannel(channelId, name, () -> {}, () -> {});
    }

    @Override
    public void renameChannel(String channelId, String name, Runnable onSuccess, Runnable onFailure) {
        try {
            if (channelId == null || channelId.isBlank() || !DiscordSRV.isReady) {
                runOnMain(onFailure);
                return;
            }
            VoiceChannel channel = DiscordUtil.getJda().getVoiceChannelById(channelId.trim());
            if (channel == null) {
                runOnMain(onFailure);
                return;
            }
            channel.getManager().setName(name).queue(
                    ignored -> runOnMain(onSuccess),
                    failure -> {
                        logFailure("Discord stat-channel rename failed", failure);
                        runOnMain(onFailure);
                    });
        } catch (Throwable throwable) {
            logFailure("Discord stat-channel rename failed", throwable);
            runOnMain(onFailure);
        }
    }

    private void runOnMain(Runnable callback) {
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    callback.run();
                } catch (Throwable throwable) {
                    logFailure("Discord stat-channel rename callback failed", throwable);
                }
            });
        } catch (Throwable throwable) {
            logFailure("Discord stat-channel rename callback could not be scheduled", throwable);
        }
    }

    CardPreflight preflightCard() {
        try {
            String channelId = configuredAnnouncementChannel();
            if (channelId.isBlank()) return CardPreflight.disabledReport();
            if (!DiscordSRV.isReady) return CardPreflight.notReady();
            TextChannel channel = DiscordUtil.getTextChannelById(channelId);
            if (channel == null) {
                return CardPreflight.problem(channelId,
                        "announcement channel " + channelId + " could not be resolved");
            }
            if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
                return CardPreflight.problem(channelId,
                        "announcement channel " + channelId
                                + " is missing Manage Webhooks; announcements will be silently dropped");
            }
            return CardPreflight.ok(channelId, channel.getName());
        } catch (Throwable throwable) {
            logFailure("Discord announcement preflight failed", throwable);
            return CardPreflight.problem(configuredAnnouncementChannel(),
                    "announcement channel preflight failed");
        }
    }

    List<VoicePreflight> preflightVoiceChannels() {
        List<VoicePreflight> reports = new ArrayList<>();
        checkVoiceChannel("house-channel-id", reports);
        checkVoiceChannel("lottery-channel-id", reports);
        return reports;
    }

    List<String> drainSettlementBlocks() {
        return settlementBuffer.drainBlocks();
    }

    void deliverSettlementBlock(String block, boolean synchronous) {
        try {
            String channelId = configuredAnnouncementChannel();
            if (channelId.isBlank() || !DiscordSRV.isReady) return;
            TextChannel channel = DiscordUtil.getTextChannelById(channelId);
            if (channel == null) return;
            String webhookName = configuredWebhookName();
            String webhookAvatarUrl = configuredWebhookAvatarUrl();
            if (synchronous) {
                WebhookUtil.deliverMessage(channel, webhookName, webhookAvatarUrl,
                        block, (MessageEmbed) null);
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    WebhookUtil.deliverMessage(channel, webhookName, webhookAvatarUrl,
                            block, (MessageEmbed) null);
                } catch (Throwable throwable) {
                    logFailure("Discord settlement delivery failed", throwable);
                }
            });
        } catch (Throwable throwable) {
            logFailure("Discord settlement delivery setup failed", throwable);
        }
    }

    void deliverSettlementBlocks(List<String> blocks, String channelId,
                                String webhookName, String webhookAvatarUrl) {
        try {
            if (channelId.isBlank() || !DiscordSRV.isReady) return;
            TextChannel channel = DiscordUtil.getTextChannelById(channelId);
            if (channel == null) return;
            for (String block : blocks) {
                try {
                    WebhookUtil.deliverMessage(channel, webhookName, webhookAvatarUrl,
                            block, (MessageEmbed) null);
                } catch (Throwable throwable) {
                    logFailure("Discord shutdown settlement delivery failed", throwable);
                }
            }
        } catch (Throwable throwable) {
            logFailure("Discord shutdown settlement delivery setup failed", throwable);
        }
    }

    boolean hasPendingSettlements() {
        return DiscordSRV.isReady && settlementDestinationCurrent() && settlementBuffer.hasPending();
    }

    boolean settlementCapHit() {
        return settlementBuffer.flushRequested();
    }

    boolean settlementWindowElapsed() {
        int seconds = plugin.getConfig().getInt("discord.group-window-seconds", 2);
        if (seconds < 1) seconds = 2;
        return settlementBuffer.oldestAgeMillis() >= seconds * 1_000L;
    }

    private void checkVoiceChannel(String key, List<VoicePreflight> reports) {
        try {
            String id = configured(key);
            if (id.isBlank()) return;
            if (!DiscordSRV.isReady) {
                reports.add(VoicePreflight.notReady(key, id));
                return;
            }
            VoiceChannel channel = DiscordUtil.getJda().getVoiceChannelById(id);
            if (channel == null) {
                reports.add(VoicePreflight.problem(key, id, "channel could not be resolved or is not a voice channel"));
                return;
            }
            if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_CHANNEL)) {
                reports.add(VoicePreflight.problem(key, id, "channel is missing Manage Channel"));
                return;
            }
            reports.add(VoicePreflight.ok(key, id, channel.getName()));
        } catch (Throwable throwable) {
            logFailure("Discord voice-channel preflight failed for " + key, throwable);
            reports.add(VoicePreflight.problem(key, configured(key), "channel preflight failed"));
        }
    }

    String configuredAnnouncementChannel() {
        return configured("channel-id");
    }

    String configuredWebhookName() {
        String value = configured("webhook-name");
        return value.isBlank() ? "House" : value;
    }

    String configuredWebhookAvatarUrl() {
        return configured("webhook-avatar-url");
    }

    private boolean settlementDestinationCurrent() {
        String channelId = configuredAnnouncementChannel();
        if (channelId.isBlank()) {
            settlementBuffer.clear();
            bufferedChannelId = null;
            return false;
        }
        if (bufferedChannelId != null && !bufferedChannelId.equals(channelId)) {
            settlementBuffer.clear();
            bufferedChannelId = channelId;
            return false;
        }
        return true;
    }

    private String configured(String key) {
        String value = plugin.getConfig().getString("discord." + key, "");
        return value == null ? "" : value.trim();
    }

    private void logFailure(String message, Throwable throwable) {
        plugin.getLogger().log(Level.WARNING, message, throwable);
    }

    record CardPreflight(boolean ready, boolean disabled, String channelId, String problem, String channelName) {
        static CardPreflight disabledReport() { return new CardPreflight(true, true, "", null, null); }
        static CardPreflight notReady() { return new CardPreflight(false, false, "", null, null); }
        static CardPreflight problem(String id, String message) {
            return new CardPreflight(true, false, id, message, null);
        }
        static CardPreflight ok(String id, String name) {
            return new CardPreflight(true, false, id, null, name);
        }
    }

    record VoicePreflight(String key, String channelId, boolean ready, String problem, String channelName) {
        static VoicePreflight notReady(String key, String id) {
            return new VoicePreflight(key, id, false, null, null);
        }
        static VoicePreflight problem(String key, String id, String message) {
            return new VoicePreflight(key, id, true, message, null);
        }
        static VoicePreflight ok(String key, String id, String name) {
            return new VoicePreflight(key, id, true, null, name);
        }
    }

    enum SlashCommandPushStatus {
        PUSHED,
        SKIPPED_NOT_READY,
        SKIPPED_NO_GUILDS,
        SKIPPED_UNCHANGED,
        SKIPPED_NO_PROVIDER,
        FAILED
    }

    record SlashCommandPushResult(SlashCommandPushStatus status, SlashCommandPreflight preflight) {
    }

    record SlashCommandPreflight(Status status, String channelId, String problem,
                                 String channelName, String guildId, String guildName) {
        static SlashCommandPreflight disabled() {
            return new SlashCommandPreflight(Status.DISABLED, "", null, null, null, null);
        }

        static SlashCommandPreflight notReady(String id) {
            return new SlashCommandPreflight(Status.NOT_READY, id, null, null, null, null);
        }

        static SlashCommandPreflight unresolved(String id, String problem) {
            return new SlashCommandPreflight(Status.UNRESOLVED, id, problem, null, null, null);
        }

        static SlashCommandPreflight nonText(String id, String problem) {
            return new SlashCommandPreflight(Status.NON_TEXT, id, problem, null, null, null);
        }

        static SlashCommandPreflight success(String id, String channelName,
                                             String guildId, String guildName) {
            return new SlashCommandPreflight(Status.SUCCESS, id, null, channelName, guildId, guildName);
        }

        boolean resolvesGuild() {
            return status == Status.SUCCESS && guildId != null && !guildId.isBlank();
        }

        enum Status {
            DISABLED,
            NOT_READY,
            UNRESOLVED,
            NON_TEXT,
            SUCCESS
        }
    }
}
