package me.beeliebub.tweaks.protection.region;

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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

// Atomic, crash-safe disk persistence for the lazy-stamp pending map and the
// orphaned-region set. Both belong in one file because they are loaded,
// snapshotted, and flushed by the same lifecycle.
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
    private static final String ORPHANS_KEY = "orphans";
    private static final int MAX_PERSISTED_ORPHANS = 5_000;

    private final Tweaks plugin;
    private final File yamlFile;
    private final File tmpFile;
    private final ConcurrentHashMap<String, Set<String>> stamps;
    private final Set<String> orphanedRegions;
    private final ConcurrentHashMap<String, Region> regions;
    private final Object writeLock = new Object();

    private boolean orphanCapWarningLogged;
    private BukkitTask task;

    public PendingStampsStore(Tweaks plugin, File dataFolder,
                             ConcurrentHashMap<String, Set<String>> stamps,
                             Set<String> orphanedRegions) {
        this(plugin, dataFolder, stamps, orphanedRegions, null);
    }

    public PendingStampsStore(Tweaks plugin, File dataFolder,
                             ConcurrentHashMap<String, Set<String>> stamps,
                             Set<String> orphanedRegions,
                             ConcurrentHashMap<String, Region> regions) {
        this.plugin = plugin;
        this.yamlFile = new File(dataFolder, YAML_FILE);
        this.tmpFile = new File(dataFolder, TMP_FILE);
        this.stamps = stamps;
        this.orphanedRegions = orphanedRegions;
        this.regions = regions;
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
        migrateOrphans(yaml.getStringList(ORPHANS_KEY));

        ConfigurationSection section = yaml.getConfigurationSection(STAMPS_KEY);
        if (section == null) return;

        int migrated = 0;
        int dropped = 0;
        for (String key : section.getKeys(false)) {
            List<String> ids = section.getStringList(key);
            if (ids.isEmpty()) continue;
            int separator = key.lastIndexOf(':');
            if (separator > 0) {
                try {
                    Long.parseLong(key.substring(separator + 1));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Skipping pending-stamp entry with invalid chunk key '" + key + "'");
                    continue;
                }
                stamps.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).addAll(ids);
                continue;
            }

            try {
                long chunkKey = Long.parseLong(key);
                for (String id : ids) {
                    Region region = findRegion(id);
                    if (region == null || region.worldName() == null || region.worldName().isBlank()) {
                        dropped++;
                        continue;
                    }
                    stamps.computeIfAbsent(RegionRegistry.stampKey(region.worldName(), chunkKey),
                            ignored -> ConcurrentHashMap.newKeySet()).add(id);
                    migrated++;
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Skipping pending-stamp entry with invalid chunk key '" + key + "'");
            }
        }
        if (migrated > 0 || dropped > 0) {
            plugin.getLogger().info("Migrated " + migrated
                    + " legacy pending stamp(s); dropped " + dropped + " unresolved id(s)");
        }
    }

    // Snapshot the live map and atomically replace pending_stamps.yml.
    // Safe to call from the async scheduler thread or from the main thread
    // during shutdown. Synchronized to guarantee a single in-flight write
    // even if shutdown overlaps a scheduled tick.
    public void writeNow() throws IOException {
        Map<String, List<String>> snapshot = new HashMap<>(stamps.size());
        for (Map.Entry<String, Set<String>> e : stamps.entrySet()) {
            snapshot.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        // Keep the live set complete for the current process; only the durable
        // snapshot is capped. Any omitted pointer is inert because region
        // resolution validates bounds before applying protection.
        List<String> orphanSnapshot;
        synchronized (orphanedRegions) {
            orphanSnapshot = new ArrayList<>(orphanedRegions);
        }
        boolean orphanSnapshotTruncated = orphanSnapshot.size() > MAX_PERSISTED_ORPHANS;
        if (orphanSnapshotTruncated) {
            orphanSnapshot = new ArrayList<>(
                    orphanSnapshot.subList(orphanSnapshot.size() - MAX_PERSISTED_ORPHANS,
                            orphanSnapshot.size()));
        }

        synchronized (writeLock) {
            if (orphanSnapshotTruncated && !orphanCapWarningLogged) {
                plugin.getLogger().warning("pending_stamps orphan list exceeds "
                        + MAX_PERSISTED_ORPHANS + " entries; persisting only the first "
                        + MAX_PERSISTED_ORPHANS + " newest entries (current size "
                        + orphanedRegions.size() + ")");
                orphanCapWarningLogged = true;
            }

            File parent = yamlFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create parent directory " + parent);
            }

            YamlConfiguration yaml = new YamlConfiguration();
            ConfigurationSection section = yaml.createSection(STAMPS_KEY);
            for (Map.Entry<String, List<String>> e : snapshot.entrySet()) {
                section.set(e.getKey(), e.getValue());
            }
            yaml.set(ORPHANS_KEY, orphanSnapshot);
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

    private Region findRegion(String id) {
        if (regions == null || id == null) return null;
        Region exact = regions.get(id);
        if (exact != null) return exact;
        Region found = null;
        for (Region region : regions.values()) {
            if (!region.id().equals(id) || region.worldName() == null || region.worldName().isBlank()) continue;
            if (found != null && !found.worldName().equals(region.worldName())) return null;
            found = region;
        }
        return found;
    }

    private void migrateOrphans(Collection<String> loadedOrphans) {
        int expanded = 0;
        for (String orphan : loadedOrphans) {
            if (orphan == null || orphan.isBlank() || orphan.indexOf(':') >= 0 || regions == null) {
                if (orphan != null && !orphan.isBlank()) orphanedRegions.add(orphan);
                continue;
            }
            orphanedRegions.remove(orphan);
            List<org.bukkit.World> worlds = plugin.getServer().getWorlds();
            for (org.bukkit.World world : worlds) {
                if (regions.containsKey(RegionRegistry.keyOf(world.getName(), orphan))) continue;
                orphanedRegions.add(ProtectionManager.keyOf(world.getName(), orphan));
                expanded++;
            }
        }
        if (expanded > 0) {
            plugin.getLogger().info("Expanded " + expanded
                    + " legacy world-less orphan pointer(s) into world-scoped entries");
        }
    }
}
