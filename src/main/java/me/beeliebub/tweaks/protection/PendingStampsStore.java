package me.beeliebub.tweaks.protection;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

// Atomic, crash-safe disk persistence for the lazy-stamp pending map.
//
// Why atomic-move instead of a direct overwrite: if power is lost mid-write,
// a direct overwrite leaves a zero-byte or partially-written file that fails
// YAML parsing on next boot, taking out every pending admin claim with it.
// Writing to <name>.tmp and then Files.move(... ATOMIC_MOVE) means the OS
// either fully swaps the file or leaves the original intact. Crash recovery
// just deletes the orphaned .tmp.
//
// Why ConcurrentHashMap iteration without a snapshot lock: CHM iterators are
// weakly consistent — they never throw CME and reflect the state of the map
// at some point during iteration. That is sufficient for our snapshot, since
// any stamp racing with the snapshot will be captured on the next interval.
public final class PendingStampsStore {

    private static final String YAML_FILE = "pending_stamps.yml";
    private static final String TMP_FILE = "pending_stamps.tmp";
    private static final String STAMPS_KEY = "stamps";

    private final Tweaks plugin;
    private final File yamlFile;
    private final File tmpFile;
    private final ConcurrentHashMap<Long, Set<String>> stamps;
    private final Object writeLock = new Object();

    private BukkitTask task;

    public PendingStampsStore(Tweaks plugin, File dataFolder, ConcurrentHashMap<Long, Set<String>> stamps) {
        this.plugin = plugin;
        this.yamlFile = new File(dataFolder, YAML_FILE);
        this.tmpFile = new File(dataFolder, TMP_FILE);
        this.stamps = stamps;
    }

    // Synchronously read pending_stamps.yml on plugin enable. Also evicts any
    // orphaned .tmp file left behind by a crash mid-write so the next
    // writeNow() starts from clean state.
    public void load() {
        if (tmpFile.exists() && !tmpFile.delete()) {
            plugin.getLogger().warning("Could not delete orphaned " + tmpFile.getName());
        }
        if (!yamlFile.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(yamlFile);
        ConfigurationSection section = yaml.getConfigurationSection(STAMPS_KEY);
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            long chunkKey;
            try {
                chunkKey = Long.parseLong(key);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Skipping pending-stamp entry with non-numeric chunk key '" + key + "'");
                continue;
            }
            List<String> ids = section.getStringList(key);
            if (ids.isEmpty()) continue;
            Set<String> set = ConcurrentHashMap.newKeySet();
            set.addAll(ids);
            stamps.put(chunkKey, set);
        }
    }

    // Snapshot the live map and atomically replace pending_stamps.yml.
    // Safe to call from the async scheduler thread or from the main thread
    // during shutdown. Synchronized to guarantee a single in-flight write
    // even if shutdown overlaps a scheduled tick.
    public void writeNow() throws IOException {
        Map<Long, List<String>> snapshot = new HashMap<>(stamps.size());
        for (Map.Entry<Long, Set<String>> e : stamps.entrySet()) {
            snapshot.put(e.getKey(), new ArrayList<>(e.getValue()));
        }

        synchronized (writeLock) {
            File parent = yamlFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create parent directory " + parent);
            }

            YamlConfiguration yaml = new YamlConfiguration();
            ConfigurationSection section = yaml.createSection(STAMPS_KEY);
            for (Map.Entry<Long, List<String>> e : snapshot.entrySet()) {
                section.set(String.valueOf(e.getKey()), e.getValue());
            }
            yaml.save(tmpFile);

            Files.move(
                    tmpFile.toPath(),
                    yamlFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    // Schedule an async repeating snapshot. periodTicks is the interval
    // between writes; first write fires after the same interval to avoid
    // double-writing immediately after a load.
    public void start(long periodTicks) {
        if (task != null) return;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    writeNow();
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "pending_stamps snapshot failed; will retry", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
