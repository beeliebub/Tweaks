package me.beeliebub.tweaks.skyblock.economy;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/** Per-island Skyblock wallet; it never reads or mutates the global economy. */
public final class SkyblockEconomy {
    public static final long MAX_EXACT_DOUBLE_INTEGER = 1L << 53;
    private final JavaPlugin plugin;
    private final YamlStore store;
    private final YamlStore transferJournal;
    private final ConcurrentMap<String, Double> balances = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<Void>> inFlight = ConcurrentHashMap.newKeySet();
    private final IslandManager islands;

    public SkyblockEconomy(JavaPlugin plugin, IslandManager islands) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.store = new YamlStore(plugin, new File(plugin.getDataFolder(), "skyblock/balances"), "skyblock balance data");
        this.transferJournal = new YamlStore(plugin, new File(plugin.getDataFolder(), "skyblock/wallet-transfers"),
                "skyblock wallet transfer journal");
        this.islands = Objects.requireNonNull(islands, "islands");
    }

    public SkyblockEconomy(JavaPlugin plugin, IslandManager islands, Map<String, Double> initial) {
        this(plugin, islands);
        if (initial != null) initial.forEach((id, value) -> { if (valid(value)) balances.put(id, value); });
    }

    public double balance(String islandId) {
        if (islandId == null) return 0.0d;
        return balances.getOrDefault(islandId, 0.0d);
    }

    /** Loads existing wallet snapshots before the command and tab surfaces are exposed. */
    public void preload() {
        File directory = new File(plugin.getDataFolder(), "skyblock/balances");
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            double value = YamlStore.load(file).getDouble("balance", 0.0d);
            if (valid(value) && value >= 0) balances.put(id, value);
        }
        replayTransferJournals();
    }

    public boolean delete(String islandId) {
        if (islandId == null) return false;
        balances.remove(islandId);
        deleteAsync(islandId);
        return true;
    }

    public CompletableFuture<Void> deleteAsync(String islandId) {
        if (islandId == null) return CompletableFuture.completedFuture(null);
        return track(store.deleteAsync(islandId));
    }

    public double balance(Island island) {
        return island == null ? 0.0d : balance(island.id());
    }

    public Mutation credit(Island island, double amount) {
        return mutate(island, amount, false);
    }

    public Mutation debit(Island island, double amount) {
        return mutate(island, amount, true);
    }

    public Mutation credit(UUIDPlayer player, double amount) {
        return player == null ? Mutation.NO_ISLAND : islands.forPlayer(player.uuid()).map(i -> credit(i, amount)).orElse(Mutation.NO_ISLAND);
    }

    public Mutation debit(UUIDPlayer player, double amount) {
        return player == null ? Mutation.NO_ISLAND : islands.forPlayer(player.uuid()).map(i -> debit(i, amount)).orElse(Mutation.NO_ISLAND);
    }

    public Mutation credit(java.util.UUID player, double amount) {
        return player == null ? Mutation.NO_ISLAND : islands.forPlayer(player).map(i -> credit(i, amount)).orElse(Mutation.NO_ISLAND);
    }

    public Mutation debit(java.util.UUID player, double amount) {
        return player == null ? Mutation.NO_ISLAND : islands.forPlayer(player).map(i -> debit(i, amount)).orElse(Mutation.NO_ISLAND);
    }

    public Transfer transfer(Island from, Island to, double amount) {
        if (from == null || to == null) return Transfer.NO_ISLAND;
        if (from.id().equals(to.id())) return Transfer.SAME_ISLAND;
        if (!valid(amount) || amount <= 0) return Transfer.REJECTED;
        synchronized (lockFor(from.id(), to.id())) {
            double source = balance(from.id());
            double target = balance(to.id());
            double nextSource = source - amount;
            double nextTarget = target + amount;
            if (!validMutation(source, -amount, nextSource) || !validMutation(target, amount, nextTarget) || source < amount) return Transfer.INSUFFICIENT;
            String journalId = java.util.UUID.randomUUID().toString().replace("-", "");
            try {
                transferJournal.writeAsync(journalId, yaml -> {
                    yaml.set("from", from.id());
                    yaml.set("to", to.id());
                    yaml.set("from-balance", nextSource);
                    yaml.set("to-balance", nextTarget);
                    yaml.set("amount", amount);
                }).join();
            } catch (RuntimeException error) {
                if (error instanceof java.util.concurrent.CompletionException completion
                        && completion.getCause() != null) error = completion.getCause() instanceof RuntimeException runtime
                        ? runtime : new RuntimeException(completion.getCause());
                plugin.getLogger().log(Level.WARNING, "Skyblock wallet transfer could not be journaled", error);
                return Transfer.REJECTED;
            }
            balances.put(from.id(), nextSource);
            balances.put(to.id(), nextTarget);
            CompletableFuture<Void> writes = CompletableFuture.allOf(persist(from.id(), nextSource),
                    persist(to.id(), nextTarget));
            writes.whenComplete((ignored, error) -> {
                if (error == null) transferJournal.deleteAsync(journalId);
            });
            return Transfer.APPLIED;
        }
    }

    public CompletableFuture<Void> flush() {
        List<CompletableFuture<Void>> writes = new ArrayList<>(inFlight);
        balances.forEach((id, value) -> writes.add(persist(id, value)));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<Void> shutdown() { return flush(); }

    private Mutation mutate(Island island, double amount, boolean debit) {
        if (island == null) return Mutation.NO_ISLAND;
        if (!valid(amount) || amount < 0) return Mutation.REJECTED;
        synchronized (lockFor(island.id(), island.id())) {
            double current = balance(island.id());
            double delta = debit ? -amount : amount;
            double next = current + delta;
            if (debit && current < amount) return Mutation.INSUFFICIENT;
            if (!validMutation(current, delta, next)) return Mutation.REJECTED;
            balances.put(island.id(), next);
            persist(island.id(), next);
            return Mutation.APPLIED;
        }
    }

    private CompletableFuture<Void> persist(String id, double value) {
        return track(store.writeAsync(id, yaml -> yaml.set("balance", value)));
    }

    private CompletableFuture<Void> track(CompletableFuture<Void> future) {
        inFlight.add(future);
        future.whenComplete((ignored, error) -> inFlight.remove(future));
        return future;
    }

    private void replayTransferJournals() {
        File directory = new File(plugin.getDataFolder(), "skyblock/wallet-transfers");
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            YamlConfiguration yaml = YamlStore.load(file);
            String from = yaml.getString("from");
            String to = yaml.getString("to");
            double fromBalance = yaml.getDouble("from-balance", Double.NaN);
            double toBalance = yaml.getDouble("to-balance", Double.NaN);
            if (!Island.isValidId(from) || !Island.isValidId(to) || from.equals(to)
                    || !valid(fromBalance) || !valid(toBalance) || fromBalance < 0 || toBalance < 0) {
                plugin.getLogger().severe("Skyblock wallet transfer journal " + id
                        + " is malformed; leaving it for manual reconciliation.");
                continue;
            }
            balances.put(from, fromBalance);
            balances.put(to, toBalance);
            CompletableFuture<Void> writes = CompletableFuture.allOf(persist(from, fromBalance),
                    persist(to, toBalance));
            writes.whenComplete((ignored, error) -> {
                if (error == null) transferJournal.deleteAsync(id);
                else plugin.getLogger().log(Level.WARNING,
                        "Skyblock wallet transfer journal replay failed for " + id, error);
            });
        }
    }

    private Object lockFor(String first, String second) {
        return locks.computeIfAbsent(first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first, ignored -> new Object());
    }

    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();
    private static boolean valid(double value) { return Double.isFinite(value) && Math.abs(value) <= MAX_EXACT_DOUBLE_INTEGER; }
    private static boolean validMutation(double current, double delta, double next) { return valid(current) && valid(delta) && valid(next) && next - current == delta; }

    public enum Mutation { APPLIED, INSUFFICIENT, REJECTED, NO_ISLAND }
    public enum Transfer { APPLIED, INSUFFICIENT, REJECTED, SAME_ISLAND, NO_ISLAND }
    public record UUIDPlayer(java.util.UUID uuid) { }
}
