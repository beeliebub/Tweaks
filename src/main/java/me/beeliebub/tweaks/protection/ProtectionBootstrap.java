package me.beeliebub.tweaks.protection;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.PendingStampsStore;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.RegionLoader;
import me.beeliebub.tweaks.protection.region.RegionWriter;
import me.beeliebub.tweaks.protection.ui.ProtectionCommand;
import me.beeliebub.tweaks.protection.ui.RegionSelectionManager;
import org.bukkit.Material;

import java.io.File;
import java.util.logging.Level;

/**
 * Tier:      1 - foundation state
 * Publishes: protectionManager, regionSelectionManager, protectionSelectionTool,
 *            pendingStampsStore (onDisable only)
 * Ordering:  RegionWriter is attached to ProtectionManager only after RegionLoader has
 *            finished populating the region cache, so the initial load never triggers a
 *            spurious write. ProtectionManager's constructor builds its own region-pointers
 *            NamespacedKey eagerly, so it is always ready before RegionLoader.load reads/writes
 *            chunk PDC through it. pendingStampsStore/regionSelectionManager are published to
 *            Services only after their load()/start() calls return, so "published" and "fully
 *            initialized" stay the same fact for onDisable()'s null-guards.
 */
public final class ProtectionBootstrap {

    private ProtectionBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        ProtectionManager protectionManager = new ProtectionManager(plugin);
        services.setProtectionManager(protectionManager);

        String configuredTool = plugin.getConfig().getString("protection.selection-tool", "STONE_AXE");
        Material resolvedTool = Material.matchMaterial(configuredTool);
        if (resolvedTool == null) {
            plugin.getLogger().warning("protection.selection-tool '" + configuredTool + "' is not a valid Material; falling back to STONE_AXE");
        } else {
            services.setProtectionSelectionTool(resolvedTool);
        }

        File regionsDir = new File(plugin.getDataFolder(), "regions");
        new RegionLoader(plugin.getLogger()).load(regionsDir, protectionManager.regions());
        protectionManager.setWriter(new RegionWriter(plugin, regionsDir));

        // Per-world refactor migration: regions saved before the world field
        // existed get assigned to the primary loaded world (overworld) so they
        // participate in per-world uniqueness going forward. Idempotent.
        if (!plugin.getServer().getWorlds().isEmpty()) {
            int migrated = protectionManager.migrateLegacyRegions(plugin.getServer().getWorlds().getFirst().getName());
            if (migrated > 0) {
                plugin.getLogger().info("Migrated " + migrated + " legacy region(s) to default world");
            }
        }

        PendingStampsStore pendingStampsStore = new PendingStampsStore(plugin, plugin.getDataFolder(), protectionManager.pendingStamps());
        pendingStampsStore.load();
        pendingStampsStore.start(20L * 60 * 5); // snapshot every 5 minutes
        services.setPendingStampsStore(pendingStampsStore);

        RegionSelectionManager regionSelectionManager = new RegionSelectionManager(plugin);
        regionSelectionManager.start();
        services.setRegionSelectionManager(regionSelectionManager);
        plugin.getServer().getPluginManager().registerEvents(regionSelectionManager, plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new ProtectionListeners(plugin, protectionManager, regionSelectionManager), plugin);

        ProtectionCommand protectionCommand = new ProtectionCommand(plugin, protectionManager, regionSelectionManager);
        plugin.getCommand("region").setExecutor(protectionCommand);
        plugin.getCommand("region").setTabCompleter(protectionCommand);
    }

    /** Takes both instances directly — see {@code EconomyBootstrap.shutdown}'s Javadoc for why. */
    public static void shutdown(Tweaks plugin, PendingStampsStore pendingStampsStore, RegionSelectionManager regionSelectionManager) {
        if (pendingStampsStore != null) {
            pendingStampsStore.stop();
            try {
                pendingStampsStore.writeNow();
            } catch (java.io.IOException e) {
                plugin.getLogger().log(Level.WARNING, "Final pending_stamps flush failed", e);
            }
        }
        if (regionSelectionManager != null) {
            regionSelectionManager.stop();
        }
    }
}
