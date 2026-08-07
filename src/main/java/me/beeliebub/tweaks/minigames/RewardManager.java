package me.beeliebub.tweaks.minigames;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

// Manages reward definitions (item sets) and pending rewards for players.
// Rewards are created by admins and granted to players by minigames; players claim them with /reward claim.
public class RewardManager {

    public static final int MAX_GRANT_COUNT = 64;

    private final JavaPlugin plugin;
    private final File rewardsFile;
    // Reward name -> array of items that make up the reward
    private final Map<String, ItemStack[]> rewards = new ConcurrentHashMap<>();

    // Pending rewards per player: UUID -> list of reward names to claim
    private final Map<UUID, List<String>> pendingRewards = new ConcurrentHashMap<>();
    private final File pendingFile;
    private final Object pendingLock = new Object();
    private CompletableFuture<Void> pendingWriteTail = CompletableFuture.completedFuture(null);

    public RewardManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.rewardsFile = new File(plugin.getDataFolder(), "rewards.yml");
        this.pendingFile = new File(plugin.getDataFolder(), "pending-rewards.yml");
        load();
    }

    public void createReward(String name) {
        rewards.put(name.toLowerCase(), new ItemStack[0]);
        saveAsync();
    }

    public boolean rewardExists(String name) {
        return rewards.containsKey(name.toLowerCase());
    }

    public void setRewardItems(String name, ItemStack[] items) {
        // Filter out null slots
        List<ItemStack> filtered = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                filtered.add(item);
            }
        }
        rewards.put(name.toLowerCase(), filtered.toArray(new ItemStack[0]));
        saveAsync();
    }

    public ItemStack[] getRewardItems(String name) {
        return rewards.getOrDefault(name.toLowerCase(), new ItemStack[0]);
    }

    public Set<String> getRewardNames() {
        return Collections.unmodifiableSet(rewards.keySet());
    }

    public CompletableFuture<Void> grantReward(UUID player, String rewardName) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(rewardName, "rewardName");
        synchronized (pendingLock) {
            pendingRewards.computeIfAbsent(player, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(rewardName.toLowerCase());
            return savePendingAsync();
        }
    }

    public List<String> getPendingRewards(UUID player) {
        synchronized (pendingLock) {
            return List.copyOf(pendingRewards.getOrDefault(player, Collections.emptyList()));
        }
    }

    public CompletableFuture<Void> clearPendingRewards(UUID player) {
        synchronized (pendingLock) {
            pendingRewards.remove(player);
            return savePendingAsync();
        }
    }

    JavaPlugin plugin() {
        return plugin;
    }

    private void load() {
        // Load reward definitions
        if (rewardsFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(rewardsFile);
            for (String name : config.getKeys(false)) {
                @SuppressWarnings("unchecked")
                List<ItemStack> items = (List<ItemStack>) config.getList(name, Collections.emptyList());
                rewards.put(name, items.toArray(new ItemStack[0]));
            }
        }

        // Load pending rewards
        if (pendingFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(pendingFile);
            for (String uuidStr : config.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    List<String> names = config.getStringList(uuidStr);
                    if (!names.isEmpty()) {
                        pendingRewards.put(uuid, Collections.synchronizedList(new ArrayList<>(names)));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void saveAsync() {
        // Snapshot the data to avoid concurrent modification
        Map<String, ItemStack[]> snapshot = new HashMap<>(rewards);
        CompletableFuture.runAsync(() -> {
            YamlConfiguration config = new YamlConfiguration();
            snapshot.forEach((name, items) -> config.set(name, Arrays.asList(items)));
            try {
                config.save(rewardsFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save rewards.yml", e);
            }
        });
    }

    private CompletableFuture<Void> savePendingAsync() {
        Map<UUID, List<String>> snapshot = new HashMap<>();
        pendingRewards.forEach((uuid, names) -> snapshot.put(uuid, new ArrayList<>(names)));
        CompletableFuture<Void> write = pendingWriteTail.handle((ignored, error) -> null).thenRunAsync(() -> {
            YamlConfiguration config = new YamlConfiguration();
            snapshot.forEach((uuid, names) -> config.set(uuid.toString(), new ArrayList<>(names)));
            try {
                config.save(pendingFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save pending-rewards.yml", e);
                throw new CompletionException(e);
            }
        });
        pendingWriteTail = write;
        return write;
    }
}
