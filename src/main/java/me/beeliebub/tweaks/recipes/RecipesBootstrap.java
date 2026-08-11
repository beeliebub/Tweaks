package me.beeliebub.tweaks.recipes;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

/**
 * Tier: 5 - leaf features
 */
public final class RecipesBootstrap {

    private RecipesBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        // Resource Rupee currency (renamed emerald + emerald block with crafting grid conversion)
        ResourceRupee resourceRupee = new ResourceRupee(plugin);
        plugin.getServer().getPluginManager().registerEvents(resourceRupee, plugin);
    }
}
