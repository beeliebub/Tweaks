package me.beeliebub.tweaks.skyblock.island;

import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable markers for player wipes that must be applied on the next login. */
public final class PendingWipeStore {

    private static final String STORE_KEY = "pending-wipes";
    private static final String PLAYERS_PATH = "players";

    private final JavaPlugin plugin;
    private final YamlStore store;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean acceptingWrites = new AtomicBoolean(true);
    private final Set<CompletableFuture<Void>> writes = ConcurrentHashMap.newKeySet();
    private final CompletableFuture<Void> loadFuture;

    public PendingWipeStore(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.store = new YamlStore(plugin, new File(plugin.getDataFolder(), "skyblock"),
                "skyblock pending-wipe data");
        this.loadFuture = store.readOrderedAsyncStrict(STORE_KEY).thenAccept(this::loadSnapshot);
    }

    private void loadSnapshot(YamlConfiguration yaml) {
        for (String value : yaml.getStringList(PLAYERS_PATH)) {
            addParsed(value);
        }

        // Accept the original root-key shape as well, so an interrupted migration cannot discard
        // a marker written before the list representation was introduced.
        for (String key : yaml.getKeys(false)) {
            if (PLAYERS_PATH.equals(key)) continue;
            if (yaml.getBoolean(key, false)) addParsed(key);
        }
    }

    private void addParsed(String value) {
        try {
            pending.add(UUID.fromString(value));
        } catch (IllegalArgumentException error) {
            plugin.getLogger().warning("Skipping invalid pending Skyblock wipe UUID: " + value);
        }
    }

    public CompletableFuture<Void> whenLoaded() {
        return loadFuture;
    }

    public boolean isLoaded() {
        return loadFuture.isDone() && !loadFuture.isCompletedExceptionally();
    }

    public boolean contains(UUID player) {
        return pending.contains(Objects.requireNonNull(player, "player"));
    }

    public boolean isPending(UUID player) {
        return contains(player);
    }

    public Set<UUID> snapshot() {
        return Collections.unmodifiableSet(Set.copyOf(pending));
    }

    public CompletableFuture<Void> markPending(UUID player) {
        ensureAcceptingWrites();
        UUID value = Objects.requireNonNull(player, "player");
        return loadFuture.thenCompose(ignored -> {
            if (!pending.add(value)) return CompletableFuture.completedFuture(null);
            return persistSnapshot();
        });
    }

    public CompletableFuture<Void> add(UUID player) {
        return markPending(player);
    }

    public CompletableFuture<Void> clearPending(UUID player) {
        ensureAcceptingWrites();
        UUID value = Objects.requireNonNull(player, "player");
        return loadFuture.thenCompose(ignored -> {
            if (!pending.remove(value)) return CompletableFuture.completedFuture(null);
            return persistSnapshot();
        });
    }

    public CompletableFuture<Void> remove(UUID player) {
        return clearPending(player);
    }

    /** Atomically consumes a marker in memory and queues its durable removal. */
    public CompletableFuture<Boolean> consume(UUID player) {
        ensureAcceptingWrites();
        UUID value = Objects.requireNonNull(player, "player");
        return loadFuture.thenCompose(ignored -> {
            if (!pending.remove(value)) return CompletableFuture.completedFuture(false);
            return persistSnapshot().thenApply(ignoredWrite -> true);
        });
    }

    public CompletableFuture<Void> flush() {
        return loadFuture.thenCompose(ignored -> {
            CompletableFuture<Void> snapshot = persistSnapshot();
            List<CompletableFuture<Void>> all = new ArrayList<>(writes);
            all.add(snapshot);
            return CompletableFuture.allOf(all.toArray(CompletableFuture[]::new));
        });
    }

    public CompletableFuture<Void> shutdown() {
        acceptingWrites.set(false);
        return flush();
    }

    private CompletableFuture<Void> persistSnapshot() {
        List<String> snapshot = new ArrayList<>(pending.size());
        for (UUID player : pending) snapshot.add(player.toString());
        snapshot.sort(String::compareTo);
        List<String> immutableSnapshot = List.copyOf(snapshot);
        CompletableFuture<Void> write = store.writeAsync(STORE_KEY, yaml -> yaml.set(PLAYERS_PATH, immutableSnapshot));
        writes.add(write);
        write.whenComplete((ignored, error) -> writes.remove(write));
        return write;
    }

    private void ensureAcceptingWrites() {
        if (!acceptingWrites.get()) throw new IllegalStateException("PendingWipeStore is shutting down");
    }
}
