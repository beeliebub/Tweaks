package me.beeliebub.tweaks.xpbottle;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

/**
 * Tier: 5 - leaf features
 */
public final class XpBottleBootstrap {

    private XpBottleBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        // XP storage bottles (custom brewing-stand recipe + drinkable bottles)
        plugin.getServer().getPluginManager().registerEvents(new XpBottleListener(plugin), plugin);
    }
}
