package me.beeliebub.tweaks.deathinventory;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class DeathInventoryManager {

    private static final int SLOT_COUNT = 41; // 36 main + 4 armor + 1 offhand
    private static final long MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000; // 30 days

    private final File baseDir;

    public DeathInventoryManager(JavaPlugin plugin) {
        this.baseDir = new File(plugin.getDataFolder(), "data/deathinventories");
        this.baseDir.mkdirs();
        purgeOldEntries();
    }

    public void saveInventory(UUID playerUuid, ItemStack[] contents) {
        File dir = playerDir(playerUuid);
        dir.mkdirs();
        String filename = System.currentTimeMillis() + ".yml";
        File file = new File(dir, filename);

        YamlConfiguration cfg = new YamlConfiguration();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                cfg.set("slots." + i, contents[i]);
            }
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Returns saved death records sorted newest-first.
    public List<File> listInventories(UUID playerUuid) {
        File dir = playerDir(playerUuid);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) return List.of();
        List<File> result = new ArrayList<>(Arrays.asList(files));
        result.sort(Comparator.comparingLong(File::lastModified).reversed());
        return result;
    }

    // Loads a saved death inventory. Slots not present in the file are null.
    public ItemStack[] loadInventory(File file) {
        ItemStack[] result = new ItemStack[SLOT_COUNT];
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (int i = 0; i < SLOT_COUNT; i++) {
            result[i] = cfg.getItemStack("slots." + i);
        }
        return result;
    }

    // Resolves <playerUuid>/<id>.yml by bare stem name.
    public File getFile(UUID playerUuid, String id) {
        return new File(playerDir(playerUuid), id + ".yml");
    }

    // Deletes files older than 30 days and removes now-empty UUID directories.
    public void purgeOldEntries() {
        long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
        File[] uuidDirs = baseDir.listFiles(File::isDirectory);
        if (uuidDirs == null) return;
        for (File uuidDir : uuidDirs) {
            File[] entries = uuidDir.listFiles((d, name) -> name.endsWith(".yml"));
            if (entries != null) {
                for (File entry : entries) {
                    if (entry.lastModified() < cutoff) {
                        entry.delete();
                    }
                }
            }
            // Remove the UUID directory if now empty.
            File[] remaining = uuidDir.listFiles();
            if (remaining == null || remaining.length == 0) {
                uuidDir.delete();
            }
        }
    }

    private File playerDir(UUID playerUuid) {
        return new File(baseDir, playerUuid.toString());
    }
}
