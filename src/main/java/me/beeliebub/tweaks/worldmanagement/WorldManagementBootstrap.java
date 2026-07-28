package me.beeliebub.tweaks.worldmanagement;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

/**
 * Tier:      2 - shared game state
 * Reads:     protectionManager (tier 1, WorldRuleListener)
 * Publishes: moonSystem (read by enchantments' EnchantTableListener at tier 4)
 */
public final class WorldManagementBootstrap {

    private WorldManagementBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        // MoonSystem owns the blood moon engine and the /bloodmoon, /fullmoon commands.
        MoonSystem moonSystem = new MoonSystem(plugin);
        services.setMoonSystem(moonSystem);
        moonSystem.start();
        plugin.getServer().getPluginManager().registerEvents(moonSystem, plugin);
        plugin.getCommand("bloodmoon").setExecutor(moonSystem);
        plugin.getCommand("fullmoon").setExecutor(moonSystem);

        // World rules: trample, portals, mob-grief, spawner eggs, villager trades.
        plugin.getServer().getPluginManager().registerEvents(new WorldRuleListener(plugin, services.protectionManager()), plugin);
    }
}
