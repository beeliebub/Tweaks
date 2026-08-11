package me.beeliebub.tweaks.logging;

import org.bukkit.configuration.Configuration;

import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe runtime cache of the boolean event-log switches. */
public final class LoggingConfigCache {

    private final ConcurrentHashMap<String, Boolean> values = new ConcurrentHashMap<>();

    /** Primes every known path after config-default reconciliation. */
    public void prime(Configuration configuration) {
        for (String path : LoggingPaths.allPaths()) {
            values.put(path, configuration.getBoolean(path, false));
        }
    }

    /** Returns the cached value; unknown or unprimed paths are disabled. */
    public boolean enabled(String path) {
        return values.getOrDefault(path, false);
    }

    /** Updates a live logging toggle after its disk write has been confirmed. */
    public void update(String path, boolean enabled) {
        if (path != null && path.startsWith("logging.")) values.put(path, enabled);
    }

    /** Package/test visibility without exposing the mutable map. */
    public int size() {
        return values.size();
    }
}
