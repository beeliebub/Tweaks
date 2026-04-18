package me.beeliebub.tweaks;

import me.beeliebub.tweaks.combos.*;
import me.beeliebub.tweaks.commands.*;
import me.beeliebub.tweaks.enchantments.AnvilListener;
import me.beeliebub.tweaks.enchantments.EggCollector;
import me.beeliebub.tweaks.enchantments.Efficacy;
import me.beeliebub.tweaks.enchantments.GemConnoisseur;
import me.beeliebub.tweaks.enchantments.Lumberjack;
import me.beeliebub.tweaks.enchantments.Replant;
import me.beeliebub.tweaks.enchantments.Smelter;
import me.beeliebub.tweaks.enchantments.SpawnerPickup;
import me.beeliebub.tweaks.enchantments.Telekinesis;
import me.beeliebub.tweaks.enchantments.Tunneller;
import me.beeliebub.tweaks.listeners.*;
import me.beeliebub.tweaks.managers.*;
import me.beeliebub.tweaks.minigames.andrew.MannequinAI;
import org.bukkit.plugin.java.JavaPlugin;

public class Tweaks extends JavaPlugin {

    private StorageManager storageManager;
    private int maxHomes;

    @Override
    public void onEnable() {

        saveDefaultConfig();
        maxHomes = getConfig().getInt("max-homes", 3);
        storageManager = new StorageManager(this);

        TabManager tabManager = new TabManager();
        NickCommand nickCommand = new NickCommand(this);
        TPACommand tpaCommand = new TPACommand(this);
        BackCommand backCommand = new BackCommand(this);
        FlyCommand flyCommand = new FlyCommand(this);
        Telekinesis telekinesis = new Telekinesis(this);
        Smelter smelter = new Smelter(this, telekinesis);

        getCommand("home").setExecutor(new HomeCommand(storageManager));
        getCommand("sethome").setExecutor(new SetHomeCommand(storageManager, maxHomes));
        getCommand("delhome").setExecutor(new DelHomeCommand(storageManager));
        getCommand("homes").setExecutor(new HomesCommand(storageManager));
        getCommand("warp").setExecutor(new WarpCommand(storageManager));
        getCommand("setwarp").setExecutor(new SetWarpCommand(storageManager));
        getCommand("delwarp").setExecutor(new DelWarpCommand(storageManager));
        getCommand("warps").setExecutor(new WarpsCommand(storageManager));
        getCommand("spawn").setExecutor(new SpawnCommand(storageManager));
        getCommand("nv").setExecutor(new NightVisionCommand());
        getCommand("config").setExecutor(new ConfigCommand(this));
        getCommand("nick").setExecutor(nickCommand);
        getCommand("tpa").setExecutor(tpaCommand);
        getCommand("tpahere").setExecutor(tpaCommand);
        getCommand("tpaccept").setExecutor(tpaCommand);
        getCommand("tpdeny").setExecutor(tpaCommand);
        getCommand("back").setExecutor(backCommand);
        getCommand("fly").setExecutor(flyCommand);

        getServer().getPluginManager().registerEvents(tabManager, this);
        getServer().getPluginManager().registerEvents(nickCommand, this);
        getServer().getPluginManager().registerEvents(backCommand, this);
        getServer().getPluginManager().registerEvents(flyCommand, this);
        getServer().getPluginManager().registerEvents(new EndPortalListener(this), this);
        getServer().getPluginManager().registerEvents(new SeparatorListener(this, storageManager), this);
        getServer().getPluginManager().registerEvents(new TrampleListener(), this);
        getServer().getPluginManager().registerEvents(new MobGriefListener(), this);

        getServer().getPluginManager().registerEvents(telekinesis, this);
        getServer().getPluginManager().registerEvents(smelter, this);
        Lumberjack lumberjack = new Lumberjack(this, telekinesis);
        getServer().getPluginManager().registerEvents(lumberjack, this);
        getServer().getPluginManager().registerEvents(new Tunneller(this, telekinesis, smelter), this);
        SpawnerPickup spawnerPickup = new SpawnerPickup(this);
        EggCollector eggCollector = new EggCollector(this);
        getServer().getPluginManager().registerEvents(spawnerPickup, this);
        getServer().getPluginManager().registerEvents(eggCollector, this);
        getServer().getPluginManager().registerEvents(new AnvilListener(spawnerPickup, eggCollector), this);
        getServer().getPluginManager().registerEvents(new Replant(this, telekinesis, lumberjack), this);
        getServer().getPluginManager().registerEvents(new GemConnoisseur(this, telekinesis), this);
        getServer().getPluginManager().registerEvents(new Efficacy(this), this);

        MannequinAI mannequinAI = new MannequinAI(this);
        getServer().getPluginManager().registerEvents(mannequinAI, this);
        mannequinAI.resumeAll();

        getLogger().info("Tweaks has been enabled safely. Async I/O and Teleportation active.");
    }

}