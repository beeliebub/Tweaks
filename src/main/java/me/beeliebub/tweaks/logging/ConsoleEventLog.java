package me.beeliebub.tweaks.logging;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Console-only facade for individually toggleable event records and hot-path summaries. */
public final class ConsoleEventLog {

    /** Returns the published logger for Tweaks handles, or null in isolated/test contexts. */
    public static ConsoleEventLog forPlugin(org.bukkit.plugin.Plugin plugin) {
        return plugin instanceof Tweaks tweaks ? tweaks.getConsoleEventLog() : null;
    }

    private final Logger logger;
    private final LoggingConfigCache cache;
    private final HotPathEventBuffer hotBuffer;
    private volatile boolean closed;
    private volatile org.bukkit.scheduler.BukkitTask timer;

    public ConsoleEventLog(JavaPlugin plugin, LoggingConfigCache cache, HotPathEventBuffer hotBuffer) {
        this(plugin.getLogger(), cache, hotBuffer);
    }

    public ConsoleEventLog(Logger logger, LoggingConfigCache cache, HotPathEventBuffer hotBuffer) {
        this.logger = logger;
        this.cache = cache;
        this.hotBuffer = hotBuffer;
    }

    public void setTimer(org.bukkit.scheduler.BukkitTask timer) {
        this.timer = timer;
    }

    /** Narrow write-through seam used by the config editor after a confirmed disk save. */
    public void updateCachedBoolean(String path, boolean enabled) {
        cache.update(path, enabled);
    }

    /** Fast cached check for call sites that must capture hot-event metadata lazily. */
    public boolean enabled(String path) {
        return !closed && cache.enabled(path);
    }

    /** Logs a normal event only when its cached switch is enabled. */
    public void log(String path, Supplier<String> line) {
        if (closed || !cache.enabled(path)) return;
        try {
            String message = line.get();
            if (message != null) logger.info(message);
        } catch (RuntimeException error) {
            logger.log(Level.WARNING, "Event log supplier failed for " + path, error);
        }
    }

    /** Records a hot event using the UUID as the fallback display name. */
    public void logHot(String path, HotPathEventBuffer.HotKey key) {
        logHot(path, key, null);
    }

    /** Records a hot event with an immutable display-name snapshot captured at the event boundary. */
    public void logHot(String path, HotPathEventBuffer.HotKey key, String actorName) {
        if (closed || !cache.enabled(path)) return;
        hotBuffer.record(path, key, actorName);
    }

    /** Flushes one detached hot window. */
    public void flushHot() {
        HotPathEventBuffer.Drain drain = hotBuffer.drain();
        for (HotPathEventBuffer.Summary summary : drain.summaries()) {
            HotPathEventBuffer.HotKey key = summary.key();
            boolean protectionDenial = summary.path().startsWith("logging.protection.")
                    && key.flag() != null;
            String action = key.flag() == null ? summary.path().substring(summary.path().lastIndexOf('.') + 1)
                    : key.flag().name().toLowerCase(Locale.ROOT).replace('_', '-');
            if (!protectionDenial) action = action.replace('-', ' ');
            String actor = actorLabel(summary.actorName(), key.actor());
            String region = key.regionId() == null ? "<unknown>" : key.regionId();
            String verb = protectionDenial ? "denied" : "recorded";
            logger.info("[" + LoggingPaths.categoryDisplay(summary.path()) + "] "
                    + actor + " " + verb + " " + action + " x" + summary.count()
                    + " at region " + region + " in the last "
                    + HotPathEventBuffer.WINDOW_SECONDS + "s");
        }
        if (drain.droppedAdmissions() > 0) {
            logger.info("[Logging] hot-path audit window dropped "
                    + drain.droppedAdmissions() + " distinct event admission(s).");
        }
    }

    /** Stops admission, cancels the timer, and drains the final window synchronously. */
    public void shutdown() {
        closed = true;
        org.bukkit.scheduler.BukkitTask currentTimer = timer;
        if (currentTimer != null) currentTimer.cancel();
        flushHot();
    }

    public static String actorLabel(String name, UUID uuid) {
        if (uuid == null) return "(console)";
        if (name != null && !name.isBlank()) return name + " (" + uuid + ")";
        return uuid.toString() + " (" + uuid + ")";
    }
}
