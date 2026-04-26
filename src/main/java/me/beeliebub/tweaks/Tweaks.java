package me.beeliebub.tweaks;

import me.beeliebub.tweaks.combos.*;
import me.beeliebub.tweaks.commands.*;
import me.beeliebub.tweaks.enchantments.*;
import me.beeliebub.tweaks.enchantments.quality.*;
import me.beeliebub.tweaks.listeners.*;
import me.beeliebub.tweaks.managers.*;
import me.beeliebub.tweaks.minigames.RewardCommand;
import me.beeliebub.tweaks.minigames.RewardListener;
import me.beeliebub.tweaks.minigames.RewardManager;
import me.beeliebub.tweaks.minigames.andrew.MannequinAI;
import me.beeliebub.tweaks.minigames.andrew.WhackCommand;
import me.beeliebub.tweaks.minigames.andrew.WhackConfig;
import org.bukkit.plugin.java.JavaPlugin;

// Main plugin class - registers all commands, listeners, enchantments, and minigame systems
public class Tweaks extends JavaPlugin {

    private StorageManager storageManager;
    private int maxHomes;

    @Override
    public void onEnable() {

        // Load config.yml and initialize data storage
        saveDefaultConfig();
        maxHomes = getConfig().getInt("max-homes", 3);
        storageManager = new StorageManager(this);

        // Commands - Combos
        TabManager tabManager = new TabManager();
        NickCommand nickCommand = new NickCommand(this);
        TPACommand tpaCommand = new TPACommand(this);
        BackCommand backCommand = new BackCommand(this);
        FlyCommand flyCommand = new FlyCommand(this);

        getCommand("nick").setExecutor(nickCommand);
        getCommand("tpa").setExecutor(tpaCommand);
        getCommand("tpa").setTabCompleter(tpaCommand);
        getCommand("tpahere").setExecutor(tpaCommand);
        getCommand("tpahere").setTabCompleter(tpaCommand);
        getCommand("tpaccept").setExecutor(tpaCommand);
        getCommand("tpaccept").setTabCompleter(tpaCommand);
        getCommand("tpdeny").setExecutor(tpaCommand);
        getCommand("tpdeny").setTabCompleter(tpaCommand);
        getCommand("back").setExecutor(backCommand);
        getCommand("fly").setExecutor(flyCommand);

        // Commands - Homes
        HomeCommand homeCommand = new HomeCommand(storageManager);
        getCommand("home").setExecutor(homeCommand);
        getCommand("home").setTabCompleter(homeCommand);
        SetHomeCommand setHomeCommand = new SetHomeCommand(storageManager, maxHomes);
        getCommand("sethome").setExecutor(setHomeCommand);
        getCommand("sethome").setTabCompleter(setHomeCommand);
        DelHomeCommand delHomeCommand = new DelHomeCommand(storageManager);
        getCommand("delhome").setExecutor(delHomeCommand);
        getCommand("delhome").setTabCompleter(delHomeCommand);
        HomesCommand homesCommand = new HomesCommand(storageManager);
        getCommand("homes").setExecutor(homesCommand);
        getCommand("homes").setTabCompleter(homesCommand);

        // Commands - Warps
        WarpCommand warpCommand = new WarpCommand(storageManager);
        getCommand("warp").setExecutor(warpCommand);
        getCommand("warp").setTabCompleter(warpCommand);
        SetWarpCommand setWarpCommand = new SetWarpCommand(storageManager);
        getCommand("setwarp").setExecutor(setWarpCommand);
        getCommand("setwarp").setTabCompleter(setWarpCommand);
        DelWarpCommand delWarpCommand = new DelWarpCommand(storageManager);
        getCommand("delwarp").setExecutor(delWarpCommand);
        getCommand("delwarp").setTabCompleter(delWarpCommand);
        getCommand("warps").setExecutor(new WarpsCommand(storageManager));

        // Commands - Misc
        getCommand("spawn").setExecutor(new SpawnCommand(storageManager));
        getCommand("nv").setExecutor(new NightVisionCommand());
        getCommand("more").setExecutor(new MoreCommand());
        getCommand("fullmoon").setExecutor(new FullMoonCommand());
        ConfigCommand configCommand = new ConfigCommand(this);
        getCommand("tconfig").setExecutor(configCommand);
        getCommand("tconfig").setTabCompleter(configCommand);

        // Listeners - General
        getServer().getPluginManager().registerEvents(tabManager, this);
        getServer().getPluginManager().registerEvents(nickCommand, this);
        getServer().getPluginManager().registerEvents(backCommand, this);
        getServer().getPluginManager().registerEvents(flyCommand, this);
        getServer().getPluginManager().registerEvents(new EndPortalListener(this), this);
        getServer().getPluginManager().registerEvents(new SeparatorListener(this, storageManager), this);
        getServer().getPluginManager().registerEvents(new TrampleListener(), this);
        getServer().getPluginManager().registerEvents(new MobGriefListener(), this);

        // Quality Enchantment System
        QualityRegistry qualityRegistry = new QualityRegistry(this);
        BloodMoonManager bloodMoonManager = new BloodMoonManager(this);
        bloodMoonManager.start();
        getServer().getPluginManager().registerEvents(bloodMoonManager, this);
        getCommand("bloodmoon").setExecutor(new BloodMoonCommand(bloodMoonManager));

        // Enchantments
        Telekinesis telekinesis = new Telekinesis(this);
        Smelter smelter = new Smelter(this, telekinesis);
        FortuneQualityListener fortuneQuality = new FortuneQualityListener(qualityRegistry);
        Lumberjack lumberjack = new Lumberjack(this, telekinesis, qualityRegistry, fortuneQuality);
        GemConnoisseur gemConnoisseur = new GemConnoisseur(this, telekinesis);
        SpawnerPickup spawnerPickup = new SpawnerPickup(this);
        EggCollector eggCollector = new EggCollector(this);

        getServer().getPluginManager().registerEvents(telekinesis, this);
        getServer().getPluginManager().registerEvents(smelter, this);
        getServer().getPluginManager().registerEvents(lumberjack, this);
        getServer().getPluginManager().registerEvents(gemConnoisseur, this);
        getServer().getPluginManager().registerEvents(new Tunneller(this, telekinesis, smelter, gemConnoisseur, qualityRegistry, fortuneQuality), this);
        getServer().getPluginManager().registerEvents(spawnerPickup, this);
        getServer().getPluginManager().registerEvents(eggCollector, this);
        getServer().getPluginManager().registerEvents(new AnvilListener(spawnerPickup, eggCollector), this);
        getServer().getPluginManager().registerEvents(new Replant(this, telekinesis, lumberjack), this);
        getServer().getPluginManager().registerEvents(new Efficacy(this, qualityRegistry), this);

        // Quality Enchantment Listeners
        getServer().getPluginManager().registerEvents(new EnchantTableListener(qualityRegistry, bloodMoonManager), this);
        getServer().getPluginManager().registerEvents(fortuneQuality, this);
        getServer().getPluginManager().registerEvents(new LootingQualityListener(qualityRegistry), this);
        getServer().getPluginManager().registerEvents(new LuckOfSeaQualityListener(qualityRegistry, this), this);

        // Minigames - Mannequin AI
        MannequinAI mannequinAI = new MannequinAI(this);
        getServer().getPluginManager().registerEvents(mannequinAI, this);
        mannequinAI.resumeAll();

        // Minigames - Rewards
        RewardManager rewardManager = new RewardManager(this);
        RewardCommand rewardCommand = new RewardCommand(rewardManager);
        getCommand("reward").setExecutor(rewardCommand);
        getCommand("reward").setTabCompleter(rewardCommand);
        getServer().getPluginManager().registerEvents(new RewardListener(rewardManager, rewardCommand), this);

        // Minigames - Whack an Andrew
        WhackConfig whackConfig = new WhackConfig(this);
        WhackCommand whackCommand = new WhackCommand(this, whackConfig, rewardManager);
        getCommand("whack").setExecutor(whackCommand);
        getCommand("whack").setTabCompleter(whackCommand);

        getLogger().info("Tweaks has been enabled safely. Async I/O and Teleportation active.");
    }

}