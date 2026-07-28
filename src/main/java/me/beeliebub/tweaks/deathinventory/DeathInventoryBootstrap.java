package me.beeliebub.tweaks.deathinventory;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

/**
 * Tier: 5 - leaf features
 */
public final class DeathInventoryBootstrap {

    private DeathInventoryBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        // Death Inventory - capture and restore player inventories on death
        DeathInventoryManager deathInventoryManager = new DeathInventoryManager(plugin);
        DeathInventoryListener deathInventoryListener = new DeathInventoryListener(plugin, deathInventoryManager);
        plugin.getServer().getPluginManager().registerEvents(deathInventoryListener, plugin);
        plugin.getCommand("deathinventory").setExecutor(deathInventoryListener);
        plugin.getCommand("deathinventory").setTabCompleter(deathInventoryListener);
    }
}
