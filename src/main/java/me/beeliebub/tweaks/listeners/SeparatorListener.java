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

public class SeparatorListener implements Listener {

    private final JavaPlugin plugin;
    private final StorageManager storage;

    private static final String ARCHIVE_WORLD_KEY = "jass:archive";
    private static final String LOBBY_WORLD_KEY = "jass:lobby";
    private static final String PI_WORLD_KEY = "jass:pi";

    private static final String PROFILE_ARCHIVE = "archive";
    private static final String PROFILE_LOBBY = "lobby";
    private static final String PROFILE_STANDARD = "standard";
    private static final String PROFILE_PI = "pi";

    private static final String EC_PREFIX = "ec_";
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
            if (player.isOnline()) migrateEnderChest(player);
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

        storage.unloadAndSavePlayerInventoriesAsync(uuid);
    }

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

        player.sendMessage(Component.text("Inventory profile switched to: " + toProfile).color(NamedTextColor.YELLOW));
    }
}