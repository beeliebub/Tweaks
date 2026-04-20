package me.beeliebub.tweaks.minigames;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// Manages reward definitions (item sets) and pending rewards for players.
// Rewards are created by admins and granted to players by minigames; players claim them with /reward claim.
public class RewardManager {

    private final JavaPlugin plugin;
    private final File rewardsFile;
    // Reward name -> array of items that make up the reward
    private final Map<String, ItemStack[]> rewards = new ConcurrentHashMap<>();

    // Pending rewards per player: UUID -> list of reward names to claim
    private final Map<UUID, List<String>> pendingRewards = new ConcurrentHashMap<>();
    private final File pendingFile;

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

    public void grantReward(UUID player, String rewardName) {
        pendingRewards.computeIfAbsent(player, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(rewardName.toLowerCase());
        savePendingAsync();
    }

    public List<String> getPendingRewards(UUID player) {
        return pendingRewards.getOrDefault(player, Collections.emptyList());
    }

    public void clearPendingRewards(UUID player) {
        pendingRewards.remove(player);
        savePendingAsync();
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
                plugin.getLogger().severe("Failed to save rewards.yml: " + e.getMessage());
            }
        });
    }

    private void savePendingAsync() {
        Map<UUID, List<String>> snapshot = new HashMap<>(pendingRewards);
        CompletableFuture.runAsync(() -> {
            YamlConfiguration config = new YamlConfiguration();
            snapshot.forEach((uuid, names) -> config.set(uuid.toString(), new ArrayList<>(names)));
            try {
                config.save(pendingFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save pending-rewards.yml: " + e.getMessage());
            }
        });
    }
}