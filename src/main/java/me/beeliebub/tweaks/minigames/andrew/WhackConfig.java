package me.beeliebub.tweaks.minigames.andrew;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

// Loads and saves Whack-an-Andrew settings from whack.yml, including game parameters,
// profile names, spawn chances, reward assignments, and arena persistence.
public class WhackConfig {

    private final JavaPlugin plugin;
    private final File configFile;
    private YamlConfiguration config;

    private int roundDuration;
    private int difficulty;
    private String profileName;
    private int mannequinLifespan;
    private int baseSpawnInterval;
    private int minSpawnInterval;
    private int maxAlive;
    private boolean showActionbar;
    private boolean announceResults;
    private String firstPlaceReward;
    private String secondPlaceReward;
    private String thirdPlaceReward;
    private String secondaryProfileName;
    private int secondaryPoints;
    private double secondaryChance;
    private String ternaryProfileName;
    private int ternaryPoints;
    private double ternaryChance;

    public WhackConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "whack.yml");
        load();
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("whack.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defaults = plugin.getResource("whack.yml");
        if (defaults != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults)));
        }

        roundDuration = config.getInt("round-duration", 180);
        difficulty = Math.clamp(config.getInt("difficulty", 1), 1, 10);
        profileName = config.getString("profile-name", "andrewkm");
        mannequinLifespan = config.getInt("mannequin-lifespan", 60);
        baseSpawnInterval = config.getInt("base-spawn-interval", 60);
        minSpawnInterval = config.getInt("min-spawn-interval", 10);
        maxAlive = config.getInt("max-alive", 10);
        showActionbar = config.getBoolean("show-actionbar", true);
        announceResults = config.getBoolean("announce-results", true);
        firstPlaceReward = config.getString("rewards.first", "");
        secondPlaceReward = config.getString("rewards.second", "");
        thirdPlaceReward = config.getString("rewards.third", "");
        secondaryProfileName = config.getString("secondary-profile.name", "ClarinetPhoenix");
        secondaryPoints = config.getInt("secondary-profile.points", 3);
        secondaryChance = config.getDouble("secondary-profile.chance", 10.0);
        ternaryProfileName = config.getString("ternary-profile.name", "Videowiz92");
        ternaryPoints = config.getInt("ternary-profile.points", 2);
        ternaryChance = config.getDouble("ternary-profile.chance", 10.0);
    }

    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save whack.yml: " + e.getMessage());
        }
    }

    public int getRoundDuration() { return roundDuration; }
    public int getDifficulty() { return difficulty; }
    public String getProfileName() { return profileName; }
    public int getMannequinLifespan() { return mannequinLifespan; }
    public int getBaseSpawnInterval() { return baseSpawnInterval; }
    public int getMinSpawnInterval() { return minSpawnInterval; }
    public int getMaxAlive() { return maxAlive; }
    public boolean isShowActionbar() { return showActionbar; }
    public boolean isAnnounceResults() { return announceResults; }
    public String getFirstPlaceReward() { return firstPlaceReward; }
    public String getSecondPlaceReward() { return secondPlaceReward; }
    public String getThirdPlaceReward() { return thirdPlaceReward; }
    public String getSecondaryProfileName() { return secondaryProfileName; }
    public int getSecondaryPoints() { return secondaryPoints; }
    public double getSecondaryChance() { return secondaryChance; }
    public String getTernaryProfileName() { return ternaryProfileName; }
    public int getTernaryPoints() { return ternaryPoints; }
    public double getTernaryChance() { return ternaryChance; }

    public void setPlaceReward(int place, String rewardName) {
        switch (place) {
            case 1 -> { firstPlaceReward = rewardName; config.set("rewards.first", rewardName); }
            case 2 -> { secondPlaceReward = rewardName; config.set("rewards.second", rewardName); }
            case 3 -> { thirdPlaceReward = rewardName; config.set("rewards.third", rewardName); }
        }
        save();
    }

    // Persist arena corners and spawn block materials to whack.yml so it survives restarts
    public void saveArena(WhackArena arena, List<Material> spawnBlockMaterials) {
        config.set("arena.world", arena.getWorld().getName());
        config.set("arena.corner1.x", arena.getMinX());
        config.set("arena.corner1.y", arena.getMinY());
        config.set("arena.corner1.z", arena.getMinZ());
        config.set("arena.corner2.x", arena.getMaxX());
        config.set("arena.corner2.y", arena.getMaxY());
        config.set("arena.corner2.z", arena.getMaxZ());
        config.set("arena.spawn-block-materials", spawnBlockMaterials.stream().map(Material::name).toList());
        save();
    }

    // Rebuild the arena from saved config, re-scanning the world for spawn blocks
    public WhackArena loadArena() {
        if (!config.contains("arena.world")) return null;

        String worldName = config.getString("arena.world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Arena world '" + worldName + "' not found, skipping arena load.");
            return null;
        }

        Location corner1 = new Location(world,
                config.getInt("arena.corner1.x"),
                config.getInt("arena.corner1.y"),
                config.getInt("arena.corner1.z"));
        Location corner2 = new Location(world,
                config.getInt("arena.corner2.x"),
                config.getInt("arena.corner2.y"),
                config.getInt("arena.corner2.z"));

        WhackArena arena = new WhackArena(corner1, corner2);

        List<String> materialNames = config.getStringList("arena.spawn-block-materials");
        if (!materialNames.isEmpty()) {
            Material[] materials = materialNames.stream()
                    .map(Material::matchMaterial)
                    .filter(m -> m != null)
                    .toArray(Material[]::new);
            if (materials.length > 0) {
                arena.scanForBlocks(materials);
            }
        }

        return arena;
    }
}