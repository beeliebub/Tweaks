package me.beeliebub.tweaks.profiles;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

/**
 * Tier:      1 - foundation state
 * Publishes: storageManager (read by minigames' ResourceWorldListener at tier 2, and by
 *            teleport's TeleportCommandManager at tier 3)
 */
public final class ProfilesBootstrap {

    private ProfilesBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        StorageManager storageManager = new StorageManager(plugin);
        services.setStorageManager(storageManager);

        plugin.getServer().getPluginManager().registerEvents(new SeparatorListener(plugin, storageManager), plugin);
    }
}
