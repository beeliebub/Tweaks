package me.beeliebub.tweaks.permissions;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

/**
 * Tier:      1 - foundation state
 * Publishes: permissionManager (read by Tweaks#getPermissionManager()/onDisable())
 */
public final class PermissionsBootstrap {

    private PermissionsBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        PermissionManager permissionManager = new PermissionManager(plugin);
        services.setPermissionManager(permissionManager);

        PermissionCommand permissionCommand = new PermissionCommand(plugin, permissionManager);
        plugin.getCommand("tprm").setExecutor(permissionCommand);
        plugin.getCommand("tprm").setTabCompleter(permissionCommand);

        plugin.getServer().getPluginManager().registerEvents(permissionManager, plugin);
    }

    /** Takes {@code permissionManager} directly — see {@code EconomyBootstrap.shutdown}'s Javadoc for why. */
    public static void shutdown(Tweaks plugin, PermissionManager permissionManager) {
        if (permissionManager != null) {
            permissionManager.shutdown();
        }
    }
}
