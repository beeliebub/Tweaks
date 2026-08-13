package me.beeliebub.tweaks.discord;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
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

    private static final String WEBHOOK_NAME = "Tweaks";

    private final Tweaks plugin;
    private final SettlementLineBuffer settlementBuffer;
    private String bufferedChannelId;

    DiscordSrvAnnouncer(Tweaks plugin) {
        this.plugin = plugin;
        this.settlementBuffer = new SettlementLineBuffer(
                message -> plugin.getLogger().warning(message),
                message -> plugin.getLogger().info(message));
    }

    @Override
    public void announceCard(String message, int color, OfflinePlayer subject) {
        try {
            String channelId = configuredAnnouncementChannel();
            if (channelId.isBlank() || !DiscordSRV.isReady) return;
            TextChannel channel = DiscordUtil.getTextChannelById(channelId);
            if (channel == null) return;

            MessageEmbed embed = new EmbedBuilder()
                    .setDescription(message)
                    .setColor(color)
                    .build();
            if (subject != null) {
                WebhookUtil.deliverMessage(channel, subject, WEBHOOK_NAME, "", embed);
                return;
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    // This overload resolves or creates the channel webhook before its own
                    // scheduleAsync flag is consulted, so it must never be called on the server
                    // thread when the per-channel webhook cache is cold.
                    WebhookUtil.deliverMessage(channel, WEBHOOK_NAME, "", "", embed);
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
            if (!DiscordSRV.isReady) return;
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
            if (synchronous) {
                WebhookUtil.deliverMessage(channel, WEBHOOK_NAME, "", block, (MessageEmbed) null);
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    WebhookUtil.deliverMessage(channel, WEBHOOK_NAME, "", block, (MessageEmbed) null);
                } catch (Throwable throwable) {
                    logFailure("Discord settlement delivery failed", throwable);
                }
            });
        } catch (Throwable throwable) {
            logFailure("Discord settlement delivery setup failed", throwable);
        }
    }

    void deliverSettlementBlocks(List<String> blocks, String channelId) {
        try {
            if (channelId.isBlank() || !DiscordSRV.isReady) return;
            TextChannel channel = DiscordUtil.getTextChannelById(channelId);
            if (channel == null) return;
            for (String block : blocks) {
                try {
                    WebhookUtil.deliverMessage(channel, WEBHOOK_NAME, "", block, (MessageEmbed) null);
                } catch (Throwable throwable) {
                    logFailure("Discord shutdown settlement delivery failed", throwable);
                }
            }
        } catch (Throwable throwable) {
            logFailure("Discord shutdown settlement delivery setup failed", throwable);
        }
    }

    boolean hasPendingSettlements() {
        return settlementDestinationCurrent() && settlementBuffer.hasPending();
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
}
