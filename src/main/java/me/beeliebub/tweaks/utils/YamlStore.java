package me.beeliebub.tweaks.utils;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Shared async-YAML persistence helper. Two layers:
 * <ul>
 *   <li><b>Static primitives</b> ({@link #saveNow} / {@link #load}) — usable standalone by any
 *       caller that already manages its own file path and threading (e.g. a manager whose
 *       persistence shape doesn't fit a keyed directory of files).</li>
 *   <li><b>Instance store</b> — a directory of {@code <key>.yml} files, for managers shaped like
 *       "one in-memory cache, one file per cache key" ({@code profiles.StorageManager}'s homes/inventories,
 *       {@code EconomyManager}'s per-player files).</li>
 * </ul>
 *
 * <h2>The calling-thread-filler contract</h2>
 * {@link #writeAsync}'s {@code filler} runs <b>on the calling thread</b>; only the actual disk
 * write ({@code config.save(file)}) happens off-thread. Building the {@link YamlConfiguration}
 * <em>is</em> the snapshot — there is no separate "copy the cache first" step for callers to get
 * subtly wrong or skip. Because of this:
 * <ul>
 *   <li>Callers never write their own {@code CompletableFuture.runAsync} — so "no Bukkit API
 *       off the main thread" is enforced by this class's shape, not by caller discipline.</li>
 *   <li><b>Never touch Bukkit world/entity/inventory/player API from inside a {@code filler}, or
 *       from a continuation chained onto a future this class returns</b> (e.g.
 *       {@code writeAsync(...).thenRun(...)}) — the filler runs synchronously on the calling
 *       thread today, but that is this class's contract to change, not a caller's to rely on, and
 *       {@code thenAccept}/{@code thenRun} continuations on a completed future run on the common
 *       pool regardless.</li>
 * </ul>
 *
 * <p>Every failed save is logged at {@link Level#WARNING} with the {@link IOException} attached
 * (never swallowed to a bare message string), and {@link #writeAsync}'s returned future completes
 * exceptionally on failure — so a caller that wants an aggregate success signal (e.g. a shutdown
 * flush via {@code CompletableFuture.allOf(...)}) gets one, without losing the eagerly-logged
 * warning for callers that don't check the future at all.
 *
 * <p>Keys must be plugin-controlled filesystem-safe stems (e.g. a {@code UUID.toString()}), never
 * raw user/command input — this class does no key sanitization.
 *
 * <p>{@link #writeAsync} and {@link #deleteAsync} operations on the <b>same key</b> complete in
 * submission order — each is internally chained onto the previous one for that key, so a rapid
 * sequence of mutations (e.g. two calls in the same tick) can never have an older snapshot finish
 * writing after a newer one and clobber it on disk. Different keys still write fully concurrently.
 */
public final class YamlStore {

    private final JavaPlugin plugin;
    private final File directory;
    private final String label;

    // Per-key write/delete queue: chains each new off-thread operation onto the previous one for
    // the same key, so disk operations on a given file always land in submission order. Without
    // this, two quick mutations to the same key (e.g. setBalance then setRank) race on the common
    // pool with no ordering guarantee, and the OLDER snapshot finishing last would silently
    // clobber the newer one already on disk. Entries are removed once their chain drains, so this
    // never grows unbounded.
    private final ConcurrentMap<String, CompletableFuture<?>> pending = new ConcurrentHashMap<>();

    /**
     * Chains {@code task} onto the previous pending operation for {@code key} (write, delete, or
     * ordered read alike), so operations on the same key always execute in submission order
     * regardless of their result type.
     */
    private <T> CompletableFuture<T> chain(String key, Supplier<T> task) {
        AtomicReference<CompletableFuture<T>> next = new AtomicReference<>();
        pending.compute(key, (k, previous) -> {
            CompletableFuture<?> base = previous != null ? previous : CompletableFuture.completedFuture(null);
            // handle(...) absorbs a prior failure so one bad operation doesn't stall this key's queue.
            CompletableFuture<T> chained = base.handle((v, err) -> null).thenApplyAsync(ignored -> task.get());
            next.set(chained);
            return chained;
        });
        CompletableFuture<T> result = next.get();
        result.whenComplete((v, err) -> pending.remove(key, result));
        return result;
    }

    private CompletableFuture<Void> chain(String key, Runnable task) {
        return chain(key, () -> {
            task.run();
            return null;
        });
    }

    /**
     * @param directory root directory this store's {@code <key>.yml} files live in; created if
     *                  missing (a failure to create it is logged, not thrown).
     * @param label     human-readable noun used in log messages, e.g. {@code "home data"}.
     */
    public YamlStore(JavaPlugin plugin, File directory, String label) {
        this.plugin = plugin;
        this.directory = directory;
        this.label = label;
        if (!directory.exists() && !directory.mkdirs() && !directory.exists()) {
            plugin.getLogger().warning("Failed to create " + label + " directory: " + directory);
        }
    }

    // ---- Layer 1: static primitives, usable by any caller ---------------------

    /**
     * Builds a {@link YamlConfiguration} via {@code filler} and saves it to {@code file}
     * synchronously on the calling thread. Returns {@code false} (and logs at {@link Level#WARNING}
     * with the {@link IOException} attached) on failure instead of throwing.
     */
    public static boolean saveNow(JavaPlugin plugin, File file, String label,
                                  Consumer<YamlConfiguration> filler) {
        YamlConfiguration config;
        try {
            config = new YamlConfiguration();
            filler.accept(config);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to build " + label + " for " + file, e);
            return false;
        }
        try {
            writeAndLog(plugin, file, label, config);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Loads {@code file}, or an empty configuration if it does not exist. Never throws. */
    public static YamlConfiguration load(File file) {
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private static void writeAndLog(JavaPlugin plugin, File file, String label,
                                    YamlConfiguration config) throws IOException {
        try {
            File directory = file.getParentFile();
            if (directory != null && !directory.exists() && !directory.mkdirs() && !directory.exists()) {
                throw new IOException("Failed to create directory " + directory);
            }
            File temporary = File.createTempFile(file.getName() + ".", ".tmp", directory);
            try {
                config.save(temporary);
                try {
                    Files.move(temporary.toPath(), file.toPath(),
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary.toPath());
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save " + label + " to " + file, e);
            throw e;
        }
    }

    // ---- Layer 2: keyed directory store ----------------------------------------

    /**
     * The file a given key resolves to: {@code <directory>/<key>.yml}.
     *
     * @throws IllegalArgumentException if {@code key} contains a path separator or {@code ..} —
     *         keys must be plain filesystem-safe stems (see class Javadoc); this turns a caller
     *         bug that would otherwise silently place a file somewhere unexpected (this store's
     *         directory may be the plugin's own data folder root, as {@code warpStore} is) into
     *         a loud failure instead.
     */
    public File fileFor(String key) {
        if (key.contains("/") || key.contains("\\") || key.contains("..")) {
            throw new IllegalArgumentException("YamlStore key must be a plain filesystem-safe stem: " + key);
        }
        return new File(directory, key + ".yml");
    }

    /** True if {@code key}'s file currently exists on disk. */
    public boolean exists(String key) {
        return fileFor(key).exists();
    }

    /** Loads {@code key}'s file synchronously on the calling thread (empty config if absent). */
    public YamlConfiguration read(String key) {
        return load(fileFor(key));
    }

    /**
     * Runs {@code filler} on the calling thread (this <em>is</em> the snapshot — see class
     * Javadoc), then saves the result off-thread. The returned future completes exceptionally on
     * failure — whether the filler itself threw while building the document, or the subsequent
     * disk write hit an {@link IOException} — and the failure is logged either way, so a caller
     * that ignores the returned future still gets a warning in the log.
     */
    public CompletableFuture<Void> writeAsync(String key, Consumer<YamlConfiguration> filler) {
        File file = fileFor(key);
        YamlConfiguration config = new YamlConfiguration();
        try {
            filler.accept(config);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to build " + label + " for " + file, e);
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
        return chain(key, () -> {
            try {
                writeAndLog(plugin, file, label, config);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    /** Deletes {@code key}'s file off-thread, if present. A missing file is a silent no-op. */
    public CompletableFuture<Void> deleteAsync(String key) {
        File file = fileFor(key);
        return chain(key, () -> {
            if (file.exists() && !file.delete()) {
                plugin.getLogger().warning("Failed to delete " + label + " file: " + file);
            }
        });
    }

    /** Loads {@code key}'s file off-thread (empty config if absent). */
    public CompletableFuture<YamlConfiguration> readAsync(String key) {
        return CompletableFuture.supplyAsync(() -> read(key));
    }

    /**
     * Loads {@code key}'s file off-thread, ordered against every other {@code writeAsync}/
     * {@code deleteAsync}/{@code readOrderedAsync} call for the same key (unlike {@link #readAsync},
     * which is a plain unordered read). Use this when a caller must never observe a file a queued
     * write for the same key is about to replace — e.g. a read-modify-write across an async
     * boundary.
     */
    public CompletableFuture<YamlConfiguration> readOrderedAsync(String key) {
        return chain(key, () -> read(key));
    }

    /**
     * Like {@link #readOrderedAsync}, but a file that exists and fails to parse completes the
     * returned future <b>exceptionally</b> instead of silently degrading to an empty
     * configuration. {@code YamlConfiguration.loadConfiguration(File)} (what {@link #load} and
     * therefore {@link #read}/{@link #readOrderedAsync} use) swallows any {@link IOException}/
     * {@link InvalidConfigurationException} internally and returns a blank document — fine for a
     * read-only display, but dangerous for a read-modify-write: writing the result back would
     * silently replace a genuinely present-but-unreadable file with fabricated defaults. Use this
     * instead wherever that would happen (e.g. crediting a payment on top of a phantom-zero
     * balance read from a momentarily-unreadable file).
     */
    public CompletableFuture<YamlConfiguration> readOrderedAsyncStrict(String key) {
        return chain(key, () -> {
            File file = fileFor(key);
            if (!file.exists()) return new YamlConfiguration();
            YamlConfiguration config = new YamlConfiguration();
            try {
                config.load(file);
            } catch (IOException | InvalidConfigurationException e) {
                throw new CompletionException("Failed to strictly load " + label + " from " + file, e);
            }
            return config;
        });
    }
}
