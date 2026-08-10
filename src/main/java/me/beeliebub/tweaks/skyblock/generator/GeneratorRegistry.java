package me.beeliebub.tweaks.skyblock.generator;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runtime registry for per-island weighted generator tiers. */
public final class GeneratorRegistry {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final File file;
    private final Map<String, GeneratorTier> tiers = new LinkedHashMap<>();
    private CompletableFuture<Void> saveTail = CompletableFuture.completedFuture(null);

    public GeneratorRegistry(JavaPlugin plugin) {
        this(plugin, plugin.getLogger(), new File(plugin.getDataFolder(), "skyblock/generators.yml"));
    }

    public GeneratorRegistry(Logger logger) {
        this(null, logger, null);
    }

    private GeneratorRegistry(JavaPlugin plugin, Logger logger, File file) {
        this.plugin = plugin;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.file = file;
        register(new GeneratorTier("default", "Default", Map.of(Material.COBBLESTONE, 1.0d)));
    }

    public void load() {
        if (file != null) load(YamlStore.load(file));
    }

    public synchronized void load(YamlConfiguration yaml) {
        Map<String, GeneratorTier> loaded = new LinkedHashMap<>();
        loaded.put("default", new GeneratorTier("default", "Default", Map.of(Material.COBBLESTONE, 1.0d)));
        for (Map<?, ?> raw : yaml.getMapList("tiers")) {
            try {
                String id = String.valueOf(raw.get("id"));
                String name = raw.get("name") == null ? id : String.valueOf(raw.get("name"));
                Map<Material, Double> outputs = new LinkedHashMap<>();
                Object values = raw.get("outputs");
                if (values instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        Material material = Material.matchMaterial(String.valueOf(entry.getKey()));
                        if (material == null) throw new IllegalArgumentException("Unknown material " + entry.getKey());
                        outputs.put(material, Double.parseDouble(String.valueOf(entry.getValue())));
                    }
                }
                loaded.put(id.toLowerCase(), new GeneratorTier(id, name, outputs));
            } catch (RuntimeException error) {
                logger.log(Level.WARNING, "Skipping malformed Skyblock generator tier: " + raw, error);
            }
        }
        tiers.clear();
        tiers.putAll(loaded);
    }

    public synchronized void register(GeneratorTier tier) {
        tiers.put(Objects.requireNonNull(tier, "tier").id(), tier);
    }

    public synchronized Optional<GeneratorTier> tier(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(tiers.get(id.toLowerCase()));
    }

    public synchronized Collection<GeneratorTier> tiers() {
        return List.copyOf(tiers.values());
    }

    public synchronized CompletableFuture<Void> saveAsync() {
        if (plugin == null || file == null) {
            saveTail = CompletableFuture.failedFuture(
                    new IllegalStateException("Generator registry has no persistence target"));
            return saveTail.copy();
        }
        List<Map<String, Object>> rows = tiers().stream().map(tier -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", tier.id());
            row.put("name", tier.displayName());
            Map<String, Double> outputs = new LinkedHashMap<>();
            tier.outputs().forEach((material, weight) -> outputs.put(material.name(), weight));
            row.put("outputs", outputs);
            return row;
        }).toList();
        saveTail = saveTail.handle((ignored, error) -> null).thenRunAsync(() -> {
            if (!YamlStore.saveNow(plugin, file, "skyblock generator data", yaml -> yaml.set("tiers", rows))) {
                throw new IllegalStateException("Generator registry could not be saved");
            }
        });
        return saveTail.copy();
    }

    /** Returns a detached observer of the ordered save queue for bounded lifecycle waits. */
    public synchronized CompletableFuture<Void> flush() {
        return saveTail.copy();
    }

    public synchronized DeleteResult delete(String id, IslandManager islands) {
        if (id == null || "default".equalsIgnoreCase(id)) return new DeleteResult(false, 0, "default tier is protected");
        GeneratorTier tier = tiers.get(id.toLowerCase());
        if (tier == null) return new DeleteResult(false, 0, "unknown tier");
        long uses = islands == null ? 0 : islands.all().stream().filter(i -> tier.id().equals(i.generatorTierId())).count();
        if (uses > 0) return new DeleteResult(false, uses, "tier is in use");
        tiers.remove(tier.id());
        return new DeleteResult(true, 0, "deleted");
    }

    public record DeleteResult(boolean deleted, long references, String reason) { }
}
