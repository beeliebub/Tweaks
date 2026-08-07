package me.beeliebub.tweaks.deathinventory;

import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class DeathInventoryManager {

    private static final int SLOT_COUNT = 41; // 36 main + 4 armor + 1 offhand

    private final JavaPlugin plugin;
    private final File baseDir;

    public DeathInventoryManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.baseDir = new File(plugin.getDataFolder(), "data/deathinventories");
        this.baseDir.mkdirs();
        purgeOldEntries();
    }

    public void saveInventory(UUID playerUuid, ItemStack[] contents) {
        File dir = playerDir(playerUuid);
        dir.mkdirs();
        File file = new File(dir, System.currentTimeMillis() + ".yml");

        YamlStore.saveNow(plugin, file, "death inventory", cfg -> {
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null) {
                    cfg.set("slots." + i, contents[i]);
                }
            }
        });
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
        YamlConfiguration cfg = YamlStore.load(file);
        for (int i = 0; i < SLOT_COUNT; i++) {
            result[i] = cfg.getItemStack("slots." + i);
        }
        return result;
    }

    /**
     * Resolves {@code <playerUuid>/<id>.yml} by bare stem name. {@code id} comes from a raw
     * command argument ({@code /deathinventory <player> <id> [restore]}, gated behind
     * {@code Permissions.ADMIN_DEATH_INVENTORY}); every real record's stem is a decimal
     * {@code System.currentTimeMillis()} value, so anything else — including a path-traversal
     * attempt like {@code ../../../etc/passwd} — resolves to a fixed sentinel filename that no
     * {@link #saveInventory} call can ever legitimately produce, rather than being passed through
     * to {@link File} construction. Callers already handle a non-existent result via
     * {@code !file.exists()}.
     */
    public File getFile(UUID playerUuid, String id) {
        if (id.isEmpty() || !id.chars().allMatch(Character::isDigit)) {
            return new File(playerDir(playerUuid), "invalid-request.yml");
        }
        return new File(playerDir(playerUuid), id + ".yml");
    }

    // Package-private so DeathInventoryRetentionConfigTest can exercise the clamp/live-read
    // behavior directly, per this project's convention for members a test needs without widening
    // visibility to public.
    long maxAgeMillis() {
        long days = Math.max(1, plugin.getConfig().getInt("deathinventory.retention-days", 30));
        return days * 24L * 60 * 60 * 1000;
    }

    // Deletes files older than the configured retention and removes now-empty UUID directories.
    public void purgeOldEntries() {
        long cutoff = System.currentTimeMillis() - maxAgeMillis();
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
