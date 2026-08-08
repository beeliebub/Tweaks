package me.beeliebub.tweaks.protection.region;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.scheduler.BukkitTask;

// Async, atomic YAML writer for Regions. The loader already accepts the schema
// we emit here; this is the inverse direction so claim/mutation state survives
// a server restart. Unclaimed files are moved into the reserved _deleted
// archive and tombstoned so queued writes cannot recreate them.
//
// Layout: world-tagged regions write to <regionsDir>/<world>/<id>.yml, while
// legacy null-world regions retain their existing flat or administrator-chosen
// location. The loader walks those locations but never loads _deleted.
//
// Atomic write discipline mirrors PendingStampsStore: serialize to <id>.tmp,
// then Files.move(... ATOMIC_MOVE). A crash mid-write leaves either the old
// file intact or an orphan .tmp the next write naturally overwrites.
public final class RegionWriter {

    static final String ARCHIVE_DIR = "_deleted";

    private final Tweaks plugin;
    private final File regionsDir;
    private final Object writeLock = new Object();
    private final Set<String> tombstoned = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, CompletableFuture<Void>> writeChains = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Region> latestSnapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Region> failedWrites = new ConcurrentHashMap<>();
    private BukkitTask retryTask;

    private Logger logger() {
        Logger logger = plugin == null ? null : plugin.getLogger();
        return logger == null ? Logger.getLogger(RegionWriter.class.getName()) : logger;
    }

    public RegionWriter(Tweaks plugin, File regionsDir) {
        this.plugin = plugin;
        this.regionsDir = regionsDir;
    }

    // Enqueue a write on the async scheduler. Caller-thread agnostic; safe to
    // invoke from event handlers without blocking the tick loop on disk I/O.
    public void queue(Region region) {
        queueInternal(region, false);
    }

    public void queueLegacyMigration(Region region) {
        queueInternal(region, true);
    }

    private void queueInternal(Region region, boolean archiveLegacySource) {
        if (region == null) return;
        String key = ProtectionManager.keyOf(region);
        latestSnapshots.put(key, region);
        CompletableFuture<Void> previous = writeChains.getOrDefault(key,
                CompletableFuture.completedFuture(null));
        CompletableFuture<Void> current = previous.handle((ignored, failure) -> null)
                .thenCompose(ignored -> submitAsync(region, archiveLegacySource));
        writeChains.put(key, current);
        current.whenComplete((ignored, failure) -> writeChains.remove(key, current));
    }

    private CompletableFuture<Void> submitAsync(Region region, boolean archiveLegacySource) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String key = ProtectionManager.keyOf(region);
            try {
                writeNow(region);
                if (archiveLegacySource) archiveLegacySource(region);
                failedWrites.remove(key);
                result.complete(null);
            } catch (IOException e) {
                failedWrites.put(key, latestSnapshots.getOrDefault(key, region));
                logger().log(Level.WARNING,
                        "Failed to write region '" + region.id() + "'; it remains queued for retry", e);
                result.complete(null);
            }
        });
        return result;
    }

    public void writeNow(Region region) throws IOException {
        if (region == null) return;
        synchronized (writeLock) {
            if (tombstoned.contains(ProtectionManager.keyOf(region))) return;
            if (!regionsDir.exists() && !regionsDir.mkdirs()) {
                throw new IOException("Could not create regions directory " + regionsDir);
            }
            File target = contained(locate(region), region.id());
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create region parent directory " + parent);
            }
            ensureExactTarget(parent, target, region.id());
            File tmp = new File(parent, target.getName() + ".tmp");

            YamlConfiguration yaml = new YamlConfiguration();
            serialize(yaml, region);
            yaml.save(tmp);

            moveIntoPlace(tmp, target);
        }
    }

    public void archive(Region region) throws IOException {
        if (region == null) return;
        synchronized (writeLock) {
            String cacheKey = ProtectionManager.keyOf(region);
            tombstoned.add(cacheKey);

            File source = contained(locate(region), region.id());
            ensureExactTarget(source.getParentFile(), source, region.id());
            File tmp = new File(source.getParentFile(), source.getName() + ".tmp");
            if (tmp.exists() && !tmp.delete()) {
                logger().warning("Could not delete stale region temporary file " + tmp);
            }
            if (!source.exists()) {
                logger().warning("No region file to archive for '" + region.id()
                        + "' at " + source);
                return;
            }

            String worldSegment = region.worldName() == null || region.worldName().isEmpty()
                    ? "_legacy" : region.worldName();
            File archiveDir = contained(new File(new File(regionsDir, ARCHIVE_DIR), worldSegment), region.id());
            if (!archiveDir.exists() && !archiveDir.mkdirs()) {
                throw new IOException("Could not create region archive directory " + archiveDir);
            }

            long timestamp = System.currentTimeMillis();
            File destination = contained(new File(archiveDir,
                    region.id() + "-" + timestamp + ".yml"), region.id());
            for (int suffix = 1; destination.exists() && suffix <= 100; suffix++) {
                destination = contained(new File(archiveDir,
                        region.id() + "-" + timestamp + "-" + suffix + ".yml"), region.id());
            }
            if (destination.exists()) {
                throw new IOException("Could not allocate archive path for region " + region.id());
            }

            moveIntoPlace(source, destination);
        }
    }

    private static void moveIntoPlace(File source, File destination) throws IOException {
        try {
            Files.move(source.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void untombstone(String cacheKey) {
        if (cacheKey == null) return;
        synchronized (writeLock) {
            tombstoned.remove(cacheKey);
        }
    }

    // Resolve the canonical write path for a region. World-tagged regions write
    // to a world-specific subdirectory (regions/<worldName>/<id>.yml) so two
    // regions sharing a name in different worlds don't collide on disk. Legacy
    // null-world regions stay at the flat root path and may reuse an existing
    // file found anywhere in the tree.
    //
    // Crucially, world-tagged regions NEVER fall back to a file outside their
    // own world subdirectory — otherwise saving a same-name region from a
    // different world would overwrite the wrong file.
    private File locate(Region region) throws IOException {
        String id = region.id();
        String worldName = region.worldName();
        if (worldName == null || worldName.isEmpty()) {
            File flat = new File(regionsDir, id + ".yml");
            File existing = findDirectFile(regionsDir, id + ".yml");
            return existing != null ? existing : flat;
        }
        // World-tagged: only resolve inside the world's own subdir.
        File worldDir = contained(new File(regionsDir, worldName), id);
        File preferred = new File(worldDir, id + ".yml");
        File exact = findDirectFile(worldDir, id + ".yml");
        if (exact != null) return exact;
        File nested = findExistingUnder(worldDir, id + ".yml");
        return nested != null ? nested : preferred;
    }

    private File findExistingUnder(File dir, String fileName) {
        if (!dir.exists() || !dir.isDirectory()) return null;
        return findExisting(dir, fileName);
    }

    private File findExisting(File dir, String fileName) {
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isDirectory()) {
                if (dir.equals(regionsDir) && ARCHIVE_DIR.equals(child.getName())) continue;
                File hit = findExisting(child, fileName);
                if (hit != null) return hit;
            } else if (child.getName().equals(fileName)) {
                return child;
            }
        }
        return null;
    }

    private File findDirectFile(File dir, String fileName) {
        if (!dir.exists() || !dir.isDirectory()) return null;
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isFile() && child.getName().equals(fileName)) return child;
        }
        return null;
    }

    private void ensureExactTarget(File parent, File target, String regionId) throws IOException {
        if (parent == null || !parent.isDirectory()) return;
        File[] children = parent.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isFile() && child.getName().equalsIgnoreCase(target.getName())
                    && !child.getName().equals(target.getName())) {
                throw new IOException("Region '" + regionId + "' conflicts with differently-cased file " + child);
            }
        }
    }

    private File contained(File candidate, String regionId) throws IOException {
        java.nio.file.Path root = regionsDir.toPath().toAbsolutePath().normalize();
        java.nio.file.Path path = candidate.toPath().toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new IOException("Region '" + regionId + "' resolved outside " + root);
        }
        return path.toFile();
    }

    private void archiveLegacySource(Region region) throws IOException {
        File source = findDirectFile(regionsDir, region.id() + ".yml");
        if (source == null || !source.exists()) return;
        File archiveDir = new File(new File(regionsDir, ARCHIVE_DIR), "_legacy");
        if (!archiveDir.exists() && !archiveDir.mkdirs()) {
            throw new IOException("Could not create legacy region archive directory " + archiveDir);
        }
        File destination = contained(new File(archiveDir,
                region.id() + "-" + System.currentTimeMillis() + ".yml"), region.id());
        moveIntoPlace(source, destination);
    }

    public void startRetry(long periodTicks) {
        if (retryTask != null) return;
        retryTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                () -> {
                    for (Region region : failedWrites.values()) queue(region);
                }, periodTicks, periodTicks);
    }

    public void stopRetry() {
        if (retryTask != null) {
            retryTask.cancel();
            retryTask = null;
        }
    }

    public void flushNow(long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        waitForWrites(deadline);
        for (Region region : List.copyOf(failedWrites.values())) {
            try {
                writeNow(latestSnapshots.getOrDefault(ProtectionManager.keyOf(region), region));
                failedWrites.remove(ProtectionManager.keyOf(region));
            } catch (IOException e) {
                logger().log(Level.SEVERE,
                        "Final region write failed for '" + region.id() + "'", e);
            }
        }
        waitForWrites(deadline);
        if (!failedWrites.isEmpty()) {
            logger().severe("Regions still failed after final flush: "
                    + failedWrites.values().stream().map(Region::id).distinct().toList());
        }
    }

    private void waitForWrites(long deadline) {
        for (CompletableFuture<Void> future : List.copyOf(writeChains.values())) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return;
            try {
                future.get(remaining, TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                return;
            } catch (Exception ignored) {
                // Individual write failures are recorded by submitAsync; shutdown continues
                // so the remaining regions still receive their final attempt.
            }
        }
    }

    private static void serialize(YamlConfiguration yaml, Region region) {
        yaml.set("id", region.id());
        yaml.set("owner", region.owner().toString());

        // members/managers serialize as a mixed list of UUID strings and
        // `group:<name>` entries. RegionLoader splits them back apart on read,
        // so the round-trip is lossless. The members list is always written
        // (even when empty) to match the historical schema; managers is written
        // when there are UUID managers OR managerGroups.
        List<String> memberStrings = new ArrayList<>(region.members().size() + region.memberGroups().size());
        for (UUID m : region.members()) {
            memberStrings.add(m.toString());
        }
        for (String g : region.memberGroups()) {
            memberStrings.add("group:" + g);
        }
        yaml.set("members", memberStrings);

        if (!region.managers().isEmpty() || !region.managerGroups().isEmpty()) {
            List<String> managerStrings = new ArrayList<>(region.managers().size() + region.managerGroups().size());
            for (UUID m : region.managers()) {
                managerStrings.add(m.toString());
            }
            for (String g : region.managerGroups()) {
                managerStrings.add("group:" + g);
            }
            yaml.set("managers", managerStrings);
        }

        if (region.hasParent()) {
            yaml.set("parent", region.parentId());
        }
        if (region.worldName() != null) {
            yaml.set("world", region.worldName());
        }

        if (region.bounds() != null) {
            ConfigurationSection b = yaml.createSection("bounds");
            b.set("min_chunk_x", region.bounds().minChunkX());
            b.set("min_chunk_z", region.bounds().minChunkZ());
            b.set("max_chunk_x", region.bounds().maxChunkX());
            b.set("max_chunk_z", region.bounds().maxChunkZ());
        }

        // Persist the Resource Rupee price paid at claim time. Omit the key
        // entirely for free (admin or legacy) regions so YAML stays compact and
        // RegionLoader's default-to-zero path remains the canonical sentinel.
        if (region.cost() > 0) {
            yaml.set("cost", region.cost());
        }

        if (!region.flagRules().isEmpty()) {
            ConfigurationSection flags = yaml.createSection("flags");
            for (Map.Entry<RegionFlag, Map<FlagTarget, Boolean>> e : region.flagRules().entrySet()) {
                ConfigurationSection rules = flags.createSection(e.getKey().name());
                for (Map.Entry<FlagTarget, Boolean> rule : e.getValue().entrySet()) {
                    rules.set(rule.getKey().toKey(), rule.getValue());
                }
            }
        }

        if (!region.materialFlags().isEmpty()) {
            ConfigurationSection mat = yaml.createSection("material_flags");
            for (Map.Entry<RegionFlag, Set<Material>> e : region.materialFlags().entrySet()) {
                List<String> names = new ArrayList<>(e.getValue().size());
                for (Material m : e.getValue()) {
                    names.add(m.name());
                }
                mat.set(e.getKey().name(), names);
            }
        }

        if (!region.entityFlags().isEmpty()) {
            ConfigurationSection ent = yaml.createSection("entity_flags");
            for (Map.Entry<RegionFlag, Set<org.bukkit.entity.EntityType>> e : region.entityFlags().entrySet()) {
                List<String> names = new ArrayList<>(e.getValue().size());
                for (org.bukkit.entity.EntityType t : e.getValue()) {
                    names.add(t.name());
                }
                ent.set(e.getKey().name(), names);
            }
        }
    }
}
