package me.beeliebub.tweaks.listeners;

import me.beeliebub.tweaks.InventoryUtil;
import me.beeliebub.tweaks.managers.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class SeparatorListener implements Listener {

    private final StorageManager storage;
    private static final String ARCHIVE_WORLD_KEY = "jass:archive";
    private static final String LOBBY_WORLD_KEY = "jass:lobby";

    private static final String PROFILE_ARCHIVE = "archive";
    private static final String PROFILE_LOBBY = "lobby";
    private static final String PROFILE_STANDARD = "standard";

    public SeparatorListener(StorageManager storage) {
        this.storage = storage;
    }

    private String getProfileForWorldKey(String worldKey) {
        if (worldKey.equalsIgnoreCase(ARCHIVE_WORLD_KEY)) return PROFILE_ARCHIVE;
        if (worldKey.equalsIgnoreCase(LOBBY_WORLD_KEY)) return PROFILE_LOBBY;

        return PROFILE_STANDARD;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        storage.loadPlayerInventoriesAsync(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        String currentWorldKey = player.getWorld().getKey().asString();
        String currentProfile = getProfileForWorldKey(currentWorldKey);

        storage.cacheInventory(player.getUniqueId(), currentProfile, InventoryUtil.toBase64(player.getInventory().getContents()));
        storage.unloadAndSavePlayerInventoriesAsync(player.getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        String fromWorldKey = event.getFrom().getKey().asString();
        String toWorldKey = player.getWorld().getKey().asString();

        String fromProfile = getProfileForWorldKey(fromWorldKey);
        String toProfile = getProfileForWorldKey(toWorldKey);

        if (fromProfile.equals(toProfile)) return;

        ItemStack[] currentItems = player.getInventory().getContents();
        storage.cacheInventory(player.getUniqueId(), fromProfile, InventoryUtil.toBase64(currentItems));

        player.getInventory().clear();

        String base64New = storage.getCachedInventory(player.getUniqueId(), toProfile);
        if (base64New != null && !base64New.isEmpty()) {
            player.getInventory().setContents(InventoryUtil.fromBase64(base64New));
        }

        player.sendMessage(Component.text("Inventory profile switched to: " + toProfile).color(NamedTextColor.YELLOW));
    }
}