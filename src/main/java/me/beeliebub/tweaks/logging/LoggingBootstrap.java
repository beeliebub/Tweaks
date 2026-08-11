package me.beeliebub.tweaks.logging;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;
import org.bukkit.scheduler.BukkitTask;

/** Tier 0 registration and final shutdown for console event logging. */
public final class LoggingBootstrap {

    private static final long FLUSH_TICKS = 20L * HotPathEventBuffer.WINDOW_SECONDS;

    private LoggingBootstrap() {}

    /**
     * Publishes a fully primed logger before any feature bootstrap can emit an event.
     * This method writes the Services slot but never reads Services state.
     */
    public static void register(Tweaks plugin, Services services) {
        LoggingConfigCache cache = new LoggingConfigCache();
        cache.prime(plugin.getConfig());
        ConsoleEventLog eventLog = new ConsoleEventLog(
                plugin, cache, new HotPathEventBuffer());
        // Summary formatting and java.util.logging are independent of Bukkit state. Flush off the
        // main thread so a saturated 2,000-key window cannot turn into a 2,000-line server tick.
        BukkitTask timer = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, eventLog::flushHot, FLUSH_TICKS, FLUSH_TICKS);
        eventLog.setTimer(timer);
        services.setConsoleEventLog(eventLog);
    }

    /** Drains the final window after all feature shutdown participants have stopped producing. */
    public static void shutdown(ConsoleEventLog eventLog) {
        if (eventLog != null) eventLog.shutdown();
    }
}
