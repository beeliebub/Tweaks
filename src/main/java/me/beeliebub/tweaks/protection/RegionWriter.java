package me.beeliebub.tweaks.protection;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

// Async, atomic YAML writer for Regions. The loader already accepts the schema
// we emit here; this is the inverse direction so claim/mutation state survives
// a server restart.
//
// Layout: each region writes to <regionsDir>/<id>.yml at the top level. The
// loader walks subdirectories so admin-organized files (regions/players/*.yml
// etc.) keep loading; we just don't auto-shard new claims into them. Admins
// can move files around between sessions without breaking anything.
//
// Atomic write discipline mirrors PendingStampsStore: serialize to <id>.tmp,
// then Files.move(... ATOMIC_MOVE). A crash mid-write leaves either the old
// file intact or an orphan .tmp the next write naturally overwrites.
public final class RegionWriter {

    private final Tweaks plugin;
    private final File regionsDir;
    private final Object writeLock = new Object();

    public RegionWriter(Tweaks plugin, File regionsDir) {
        this.plugin = plugin;
        this.regionsDir = regionsDir;
    }

    // Enqueue a write on the async scheduler. Caller-thread agnostic; safe to
    // invoke from event handlers without blocking the tick loop on disk I/O.
    public void queue(Region region) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                writeNow(region);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to write region '" + region.id() + "'; will retry on next mutation", e);
            }
        });
    }

    public void writeNow(Region region) throws IOException {
        if (region == null) return;
        synchronized (writeLock) {
            if (!regionsDir.exists() && !regionsDir.mkdirs()) {
                throw new IOException("Could not create regions directory " + regionsDir);
            }
            File target = locate(region.id());
            File tmp = new File(target.getParentFile(), target.getName() + ".tmp");

            YamlConfiguration yaml = new YamlConfiguration();
            serialize(yaml, region);
            yaml.save(tmp);

            Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    // Resolve the canonical write path for a region. If a file with this
    // region's id already exists somewhere in the tree (admin sharded the
    // YAMLs under subdirectories), overwrite it in place rather than
    // duplicating it at the root.
    private File locate(String id) {
        File flat = new File(regionsDir, id + ".yml");
        if (flat.exists()) return flat;

        File existing = findExisting(regionsDir, id + ".yml");
        return existing != null ? existing : flat;
    }

    private File findExisting(File dir, String fileName) {
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isDirectory()) {
                File hit = findExisting(child, fileName);
                if (hit != null) return hit;
            } else if (child.getName().equalsIgnoreCase(fileName)) {
                return child;
            }
        }
        return null;
    }

    private static void serialize(YamlConfiguration yaml, Region region) {
        yaml.set("id", region.id());
        yaml.set("owner", region.owner().toString());

        List<String> memberStrings = new ArrayList<>(region.members().size());
        for (UUID m : region.members()) {
            memberStrings.add(m.toString());
        }
        yaml.set("members", memberStrings);

        if (region.hasParent()) {
            yaml.set("parent", region.parentId());
        }
        if (region.worldName() != null) {
            yaml.set("world", region.worldName());
        }

        if (region.bounds() != null) {
            ConfigurationSection b = yaml.createSection("bounds");
            b.set("min_chunk_x", region.bounds().minChunkX());
            b.set("min_chunk_z", region.bounds().minChunkZ());
            b.set("max_chunk_x", region.bounds().maxChunkX());
            b.set("max_chunk_z", region.bounds().maxChunkZ());
        }

        if (!region.flagRules().isEmpty()) {
            ConfigurationSection flags = yaml.createSection("flags");
            for (Map.Entry<RegionFlag, Map<FlagTarget, Boolean>> e : region.flagRules().entrySet()) {
                ConfigurationSection rules = flags.createSection(e.getKey().name());
                for (Map.Entry<FlagTarget, Boolean> rule : e.getValue().entrySet()) {
                    rules.set(rule.getKey().toKey(), rule.getValue());
                }
            }
        }

        if (!region.materialFlags().isEmpty()) {
            ConfigurationSection mat = yaml.createSection("material_flags");
            for (Map.Entry<RegionFlag, Set<Material>> e : region.materialFlags().entrySet()) {
                List<String> names = new ArrayList<>(e.getValue().size());
                for (Material m : e.getValue()) {
                    names.add(m.name());
                }
                mat.set(e.getKey().name(), names);
            }
        }
    }
}
