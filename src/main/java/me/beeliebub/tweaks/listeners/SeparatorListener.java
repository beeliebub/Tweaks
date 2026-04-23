package me.beeliebub.tweaks.listeners;

import me.beeliebub.tweaks.InventoryUtil;
import me.beeliebub.tweaks.managers.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Keeps separate inventories and ender chests per world group.
// When a player changes worlds, their current inventory is saved and the destination world's inventory is loaded.
public class SeparatorListener implements Listener {

    private final JavaPlugin plugin;
    private final StorageManager storage;

    // World keys mapped to inventory profile names
    private static final String ARCHIVE_WORLD_KEY = "jass:archive";
    private static final String LOBBY_WORLD_KEY = "jass:lobby";
    private static final String PI_WORLD_KEY = "jass:pi";

    private static final String PROFILE_ARCHIVE = "archive";
    private static final String PROFILE_LOBBY = "lobby";
    private static final String PROFILE_STANDARD = "standard";
    private static final String PROFILE_PI = "pi";

    // Ender chest data uses this prefix to distinguish from regular inventory data
    private static final String EC_PREFIX = "ec_";
    // Experience data uses this prefix to distinguish from regular inventory data
    private static final String XP_PREFIX = "xp_";
    // Tracks players who just died so their empty inventory isn't saved over their real one
    private final Set<UUID> recentDeaths = ConcurrentHashMap.newKeySet();

    public SeparatorListener(JavaPlugin plugin, StorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    private String getProfileForWorldKey(String worldKey) {
        if (worldKey.equalsIgnoreCase(ARCHIVE_WORLD_KEY)) return PROFILE_ARCHIVE;
        if (worldKey.equalsIgnoreCase(LOBBY_WORLD_KEY)) return PROFILE_LOBBY;
        if (worldKey.equalsIgnoreCase(PI_WORLD_KEY)) return PROFILE_PI;
        return PROFILE_STANDARD;
    }

    private boolean hasEnderChestData(UUID player) {
        return storage.getCachedInventory(player, EC_PREFIX + PROFILE_STANDARD) != null
                || storage.getCachedInventory(player, EC_PREFIX + PROFILE_LOBBY) != null
                || storage.getCachedInventory(player, EC_PREFIX + PROFILE_ARCHIVE) != null
                || storage.getCachedInventory(player, EC_PREFIX + PROFILE_PI) != null;
    }

    private boolean hasExperienceData(UUID player) {
        return storage.getCachedInventory(player, XP_PREFIX + PROFILE_STANDARD) != null
                || storage.getCachedInventory(player, XP_PREFIX + PROFILE_LOBBY) != null
                || storage.getCachedInventory(player, XP_PREFIX + PROFILE_ARCHIVE) != null
                || storage.getCachedInventory(player, XP_PREFIX + PROFILE_PI) != null;
    }

    // One-time migration: copy existing ender chest contents into the standard profile slot
    private void migrateEnderChest(Player player) {
        UUID uuid = player.getUniqueId();
        if (hasEnderChestData(uuid)) return;

        ItemStack[] contents = player.getEnderChest().getContents();
        storage.cacheInventory(uuid, EC_PREFIX + PROFILE_STANDARD, InventoryUtil.toBase64(contents));

        String profile = getProfileForWorldKey(player.getWorld().getKey().asString());
        if (!profile.equals(PROFILE_STANDARD)) {
            player.getEnderChest().clear();
        }
    }

    // One-time migration: save current XP to the standard profile and zero out all others.
    // If the player is in a non-standard profile, their XP is saved to standard and then zeroed.
    private void migrateExperience(Player player) {
        UUID uuid = player.getUniqueId();
        if (hasExperienceData(uuid)) return;

        float xp = player.getExp();
        int level = player.getLevel();
        String xpData = level + ":" + xp;

        // Always save current XP to the standard profile
        storage.cacheInventory(uuid, XP_PREFIX + PROFILE_STANDARD, xpData);
        // Zero out all other profiles
        storage.cacheInventory(uuid, XP_PREFIX + PROFILE_LOBBY, "0:0.0");
        storage.cacheInventory(uuid, XP_PREFIX + PROFILE_ARCHIVE, "0:0.0");
        storage.cacheInventory(uuid, XP_PREFIX + PROFILE_PI, "0:0.0");

        // If player is not in standard profile, zero their current XP
        String profile = getProfileForWorldKey(player.getWorld().getKey().asString());
        if (!profile.equals(PROFILE_STANDARD)) {
            player.setExp(0f);
            player.setLevel(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        recentDeaths.add(uuid);

        String currentProfile = getProfileForWorldKey(player.getWorld().getKey().asString());
        storage.cacheInventory(uuid, currentProfile, InventoryUtil.toBase64(new ItemStack[player.getInventory().getSize()]));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        storage.loadPlayerInventoriesAsync(uuid).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            migrateEnderChest(player);
            migrateExperience(player);
        }));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        recentDeaths.remove(uuid);

        String currentProfile = getProfileForWorldKey(player.getWorld().getKey().asString());

        storage.cacheInventory(uuid, currentProfile, InventoryUtil.toBase64(player.getInventory().getContents()));
        storage.cacheInventory(uuid, EC_PREFIX + currentProfile, InventoryUtil.toBase64(player.getEnderChest().getContents()));
        storage.cacheInventory(uuid, XP_PREFIX + currentProfile, player.getLevel() + ":" + player.getExp());

        storage.unloadAndSavePlayerInventoriesAsync(uuid);
    }

    // Swap inventory and ender chest contents when switching between world groups
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        String fromProfile = getProfileForWorldKey(event.getFrom().getKey().asString());
        String toProfile = getProfileForWorldKey(player.getWorld().getKey().asString());

        if (fromProfile.equals(toProfile)) return;
        boolean justDied = recentDeaths.remove(uuid);

        if (!justDied) {
            storage.cacheInventory(uuid, fromProfile, InventoryUtil.toBase64(player.getInventory().getContents()));
        }
        player.getInventory().clear();

        String invData = storage.getCachedInventory(uuid, toProfile);
        if (invData != null && !invData.isEmpty()) {
            player.getInventory().setContents(InventoryUtil.fromBase64(invData));
        }

        storage.cacheInventory(uuid, EC_PREFIX + fromProfile, InventoryUtil.toBase64(player.getEnderChest().getContents()));
        player.getEnderChest().clear();

        String ecData = storage.getCachedInventory(uuid, EC_PREFIX + toProfile);
        if (ecData != null && !ecData.isEmpty()) {
            player.getEnderChest().setContents(InventoryUtil.fromBase64(ecData));
        }

        storage.cacheInventory(uuid, XP_PREFIX + fromProfile, player.getLevel() + ":" + player.getExp());
        player.setExp(0f);
        player.setLevel(0);

        String xpData = storage.getCachedInventory(uuid, XP_PREFIX + toProfile);
        if (xpData != null && !xpData.isEmpty()) {
            String[] parts = xpData.split(":");
            player.setLevel(Integer.parseInt(parts[0]));
            player.setExp(Float.parseFloat(parts[1]));
        }

        player.sendMessage(Component.text("Inventory profile switched to: " + toProfile).color(NamedTextColor.YELLOW));
    }
}