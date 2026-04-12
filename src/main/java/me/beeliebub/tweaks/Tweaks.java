package me.beeliebub.tweaks;

import me.beeliebub.tweaks.combos.*;
import me.beeliebub.tweaks.commands.*;
import me.beeliebub.tweaks.listeners.*;
import me.beeliebub.tweaks.managers.*;
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

        // TODO this REALLY needs to be cleaned up...

        getServer().getPluginManager().registerEvents(tabManager, this);
        getCommand("nick").setExecutor(nickCommand);
        getServer().getPluginManager().registerEvents(nickCommand, this);
        
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
        getCommand("tpa").setExecutor(tpaCommand);
        getCommand("tpahere").setExecutor(tpaCommand);
        getCommand("tpaccept").setExecutor(tpaCommand);
        getCommand("tpdeny").setExecutor(tpaCommand);
        getCommand("back").setExecutor(backCommand);
        getCommand("fly").setExecutor(flyCommand);

        getServer().getPluginManager().registerEvents(new EndPortalListener(this), this);
        getServer().getPluginManager().registerEvents(new SeparatorListener(storageManager), this);
        getServer().getPluginManager().registerEvents(new TrampleListener(), this);
        getServer().getPluginManager().registerEvents(backCommand, this);
        getServer().getPluginManager().registerEvents(new MobGriefListener(), this);
        getServer().getPluginManager().registerEvents(flyCommand, this);

        getLogger().info("Tweaks has been enabled safely. Async I/O and Teleportation active.");
    }

}
