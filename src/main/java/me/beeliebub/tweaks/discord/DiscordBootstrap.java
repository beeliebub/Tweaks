package me.beeliebub.tweaks.discord;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/** Owns optional Discord integration tasks and their shutdown ordering. */
public final class DiscordBootstrap {

    private static final long PREFLIGHT_PERIOD_TICKS = 600L;
    private static final int MAX_PREFLIGHT_ATTEMPTS = 10;
    private static final long SHUTDOWN_FLUSH_TIMEOUT_MILLIS = 2_000L;

    private static DiscordAnnouncer announcer = DiscordAnnouncer.NOOP;
    private static BukkitTask preflightTask;
    private static BukkitTask settlementFlushTask;
    private static BukkitTask statRefreshTask;
    private static DiscordStatChannels statChannels;

    private DiscordBootstrap() {
    }

    public static void register(Tweaks plugin, Services services) {
        DiscordAnnouncer local = DiscordAnnouncer.NOOP;
        try {
            if (plugin.getServer().getPluginManager().isPluginEnabled("DiscordSRV")) {
                local = new DiscordSrvAnnouncer(plugin);
            }
        } catch (Throwable throwable) {
            // A server can enable a different DiscordSRV build than the compile-only API. Keep
            // that linkage failure at the optional boundary and publish the no-op seam instead.
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "DiscordSRV was enabled but its Tweaks integration could not be loaded; "
                            + "Discord features are disabled.", throwable);
        }
        announcer = local;
        services.setDiscordAnnouncer(local);
    }

    /** Starts producer-independent Discord tasks after all manager slots are published. */
    public static void start(Tweaks plugin, Services services) {
        if (!(announcer instanceof DiscordSrvAnnouncer srv)) return;

        schedulePreflight(plugin, srv);

        settlementFlushTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> flushSettlements(plugin, srv, false), 20L, 20L);

        statChannels = new DiscordStatChannels(plugin, services.houseAccount(),
                services.lotteryManager(), srv);
        scheduleStatRefresh(plugin);
    }

    private static void scheduleStatRefresh(Tweaks plugin) {
        if (statChannels == null) return;
        long delay = statRefreshDelayTicks();
        statRefreshTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            statRefreshTask = null;
            if (!plugin.isEnabled() || statChannels == null) return;
            try {
                statChannels.refresh();
            } catch (Throwable throwable) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Discord stat-channel refresh failed", throwable);
            }
            scheduleStatRefresh(plugin);
        }, delay);
    }

    private static long statRefreshDelayTicks() {
        int seconds = statChannels == null ? DiscordStatChannels.MIN_REFRESH_SECONDS
                : statChannels.refreshSeconds();
        return Math.max(1L, seconds * 20L);
    }

    private static void schedulePreflight(Tweaks plugin, DiscordSrvAnnouncer srv) {
        final int[] attempts = {0};
        preflightTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            attempts[0]++;
            DiscordSrvAnnouncer.CardPreflight report = srv.preflightCard();
            List<DiscordSrvAnnouncer.VoicePreflight> voiceReports = srv.preflightVoiceChannels();
            boolean voiceReady = voiceReports.stream().allMatch(DiscordSrvAnnouncer.VoicePreflight::ready);
            if (!report.ready() || !voiceReady) {
                if (attempts[0] >= MAX_PREFLIGHT_ATTEMPTS) {
                    plugin.getLogger().warning("DiscordSRV did not become ready for the announcement preflight; "
                            + "Discord announcements remain unavailable until the next restart.");
                    cancelPreflight();
                }
                return;
            }
            // Webhook delivery exposes no per-message success signal, so this one ready-time
            // check is the only observable warning for a bad channel or missing permission.
            if (!report.disabled() && report.problem() != null) {
                plugin.getLogger().warning(report.problem());
            } else if (!report.disabled()) {
                plugin.getLogger().info("Discord announcements are configured for #"
                        + report.channelName() + " (" + report.channelId() + ").");
            }
            for (DiscordSrvAnnouncer.VoicePreflight voice : voiceReports) {
                if (voice.problem() != null) {
                    plugin.getLogger().warning("Discord " + voice.key() + " " + voice.channelId()
                            + ": " + voice.problem());
                }
            }
            cancelPreflight();
        }, 1L, PREFLIGHT_PERIOD_TICKS);
    }

    private static void cancelPreflight() {
        if (preflightTask != null) {
            preflightTask.cancel();
            preflightTask = null;
        }
    }

    /** Cancels the preflight and leaves the settlement/stat task slots ready for later items. */
    public static void shutdown(Tweaks plugin) {
        if (preflightTask != null) {
            preflightTask.cancel();
            preflightTask = null;
        }
        if (settlementFlushTask != null) {
            settlementFlushTask.cancel();
            settlementFlushTask = null;
        }
        if (statRefreshTask != null) {
            statRefreshTask.cancel();
            statRefreshTask = null;
        }
        if (announcer instanceof DiscordSrvAnnouncer srv) {
            flushSettlementsOnShutdown(plugin, srv);
        }
        statChannels = null;
        announcer = DiscordAnnouncer.NOOP;
    }

    static void flushSettlements(Tweaks plugin, DiscordSrvAnnouncer srv, boolean synchronous) {
        if (!srv.hasPendingSettlements()) return;
        if (!synchronous && !srv.settlementCapHit() && !srv.settlementWindowElapsed()) return;
        List<String> blocks = srv.drainSettlementBlocks();
        for (String block : blocks) {
            srv.deliverSettlementBlock(block, synchronous);
        }
    }

    private static void flushSettlementsOnShutdown(Tweaks plugin, DiscordSrvAnnouncer srv) {
        if (!srv.hasPendingSettlements()) return;
        List<String> blocks = srv.drainSettlementBlocks();
        if (blocks.isEmpty()) return;

        String channelId = srv.configuredAnnouncementChannel();
        Thread worker = new Thread(() -> srv.deliverSettlementBlocks(blocks, channelId),
                "Tweaks-Discord-shutdown-flush");
        worker.setDaemon(true);
        worker.start();
        try {
            worker.join(SHUTDOWN_FLUSH_TIMEOUT_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Discord shutdown flush was interrupted; pending blocks may not have been delivered.",
                    interrupted);
        }
        if (worker.isAlive()) {
            worker.interrupt();
            plugin.getLogger().warning("Discord shutdown flush exceeded 2 seconds; pending blocks may not have been delivered.");
        }
    }
}
