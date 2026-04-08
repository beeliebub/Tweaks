package me.beeliebub.tweaks;

import org.bukkit.plugin.java.JavaPlugin;

public class Tweaks extends JavaPlugin {

    private StorageManager storageManager;
    private int maxHomes;

    @Override
    public void onEnable() {

        saveDefaultConfig();
        maxHomes = getConfig().getInt("max-homes", 3);
        
        storageManager = new StorageManager(this);
        
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

        getServer().getPluginManager().registerEvents(new EndPortalListener(this), this);
        getServer().getPluginManager().registerEvents(new SeparatorListener(storageManager), this);
        getServer().getPluginManager().registerEvents(new TrampleListener(), this);

        getLogger().info("Tweaks has been enabled safely. Async I/O and Teleportation active.");
    }

}
