package me.beeliebub.tweaks.enchantments;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.modes.EnchantMode;
import me.beeliebub.tweaks.enchantments.quality.EnchantTableListener;
import me.beeliebub.tweaks.enchantments.quality.FortuneQualityListener;
import me.beeliebub.tweaks.enchantments.quality.LootingQualityListener;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.enchantments.quality.SilkTouchQualityListener;

/**
 * Tier:       4 - enchantments
 * Reads:      itemFilterCommand (tier 3, Telekinesis), resourceHunt (tier 2, Smelter/
 *             SilkTouchQualityListener/GemConnoisseur/Tunneller), moonSystem (tier 2,
 *             EnchantTableListener)
 * Publishes:  qualityRegistry (read by itemadmin's ToolProtectCommand straddle at tier 5),
 *             telekinesis, replant (Tweaks#getTelekinesis()/getReplant())
 * Wire-backs: EnchantMode is constructed early in this method so Tunneller/Efficacy can take
 *             it as a constructor arg, then wired back via wireEnchantments once both exist -
 *             the same cycle-breaking shape as TabBootstrap's setTabManager calls.
 */
public final class EnchantmentsBootstrap {

    private EnchantmentsBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        // Quality Enchantment System
        QualityRegistry qualityRegistry = new QualityRegistry(plugin);
        services.setQualityRegistry(qualityRegistry);

        Telekinesis telekinesis = new Telekinesis(plugin, services.itemFilterCommand());
        services.setTelekinesis(telekinesis);
        Smelter smelter = new Smelter(plugin, telekinesis, services.resourceHunt());
        FortuneQualityListener fortuneQuality = new FortuneQualityListener(qualityRegistry);
        SilkTouchQualityListener silkTouchQuality = new SilkTouchQualityListener(qualityRegistry, telekinesis, services.resourceHunt());
        Lumberjack lumberjack = new Lumberjack(plugin, telekinesis, qualityRegistry, fortuneQuality);
        services.setLumberjack(lumberjack);
        GemConnoisseur gemConnoisseur = new GemConnoisseur(plugin, telekinesis, services.resourceHunt());
        SpawnerPickup spawnerPickup = new SpawnerPickup(plugin);
        EggCollector eggCollector = new EggCollector(plugin, qualityRegistry);
        EnchantMode enchantMode = new EnchantMode(plugin);
        Tunneller tunneller = new Tunneller(plugin, telekinesis, smelter, gemConnoisseur, qualityRegistry, fortuneQuality, silkTouchQuality, services.resourceHunt(), enchantMode);
        services.setTunneller(tunneller);
        Efficacy efficacy = new Efficacy(plugin, qualityRegistry, enchantMode);
        services.setEfficacy(efficacy);

        plugin.getServer().getPluginManager().registerEvents(telekinesis, plugin);
        plugin.getServer().getPluginManager().registerEvents(smelter, plugin);
        plugin.getServer().getPluginManager().registerEvents(lumberjack, plugin);
        plugin.getServer().getPluginManager().registerEvents(gemConnoisseur, plugin);
        plugin.getServer().getPluginManager().registerEvents(tunneller, plugin);
        plugin.getServer().getPluginManager().registerEvents(spawnerPickup, plugin);
        plugin.getServer().getPluginManager().registerEvents(eggCollector, plugin);
        Replant replant = new Replant(plugin, telekinesis, lumberjack);
        services.setReplant(replant);
        plugin.getServer().getPluginManager().registerEvents(replant, plugin);
        plugin.getServer().getPluginManager().registerEvents(efficacy, plugin);
        enchantMode.wireEnchantments(tunneller, efficacy);
        plugin.getServer().getPluginManager().registerEvents(enchantMode, plugin);

        // Quality Enchantment Listeners
        plugin.getServer().getPluginManager().registerEvents(new EnchantTableListener(plugin, qualityRegistry, services.moonSystem()), plugin);
        plugin.getServer().getPluginManager().registerEvents(fortuneQuality, plugin);
        plugin.getServer().getPluginManager().registerEvents(silkTouchQuality, plugin);
        plugin.getServer().getPluginManager().registerEvents(new LootingQualityListener(qualityRegistry), plugin);

        // Dice Converter: throwing a splash potion with this enchantment briefly blocks
        // the thrower from picking up other splash_potion item entities.
        plugin.getServer().getPluginManager().registerEvents(new DiceConverterListener(plugin), plugin);
    }
}
