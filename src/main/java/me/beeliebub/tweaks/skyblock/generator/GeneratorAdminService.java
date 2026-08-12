package me.beeliebub.tweaks.skyblock.generator;

import me.beeliebub.tweaks.skyblock.admin.SkyblockRegistryBackup;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Dialog-free generator tier authoring operations. */
public final class GeneratorAdminService {
    private final GeneratorRegistry registry;
    private final IslandManager islands;
    private final SkyblockRegistryBackup backup;
    public GeneratorAdminService(GeneratorRegistry registry, IslandManager islands) {
        this(registry, islands, null);
    }
    public GeneratorAdminService(GeneratorRegistry registry, IslandManager islands, JavaPlugin plugin) {
        this.registry = Objects.requireNonNull(registry);
        this.islands = Objects.requireNonNull(islands);
        this.backup = plugin == null ? null : new SkyblockRegistryBackup(plugin, "generators.yml");
    }
    public GeneratorRegistry.DeleteResult delete(String id) {
        if ("default".equalsIgnoreCase(id)) {
            return new GeneratorRegistry.DeleteResult(false, 0, "the default generator tier cannot be deleted");
        }
        if (backup != null) {
            try { backup.writeNow(); }
            catch (RuntimeException ignored) { return new GeneratorRegistry.DeleteResult(false, 0, "registry backup failed"); }
        }
        GeneratorRegistry.DeleteResult result = registry.delete(id, islands);
        return result.deleted()
                ? new GeneratorRegistry.DeleteResult(result.deleted(), result.references(), result.reason(), registry.saveAsync())
                : result;
    }
    public EditResult register(GeneratorTier tier) {
        if (tier == null) return EditResult.failure("generator tier is required");
        try {
            registry.register(tier);
            return EditResult.success("saved", registry.saveAsync());
        } catch (RuntimeException error) {
            return EditResult.failure(error.getMessage() == null ? "generator tier rejected" : error.getMessage());
        }
    }

    /** Creates an empty tier that can be completed with the output editor. */
    public EditResult create(String id, String displayName) {
        if (id == null || id.isBlank()) return EditResult.failure("generator tier id is required");
        if (registry.tier(id).isPresent()) return EditResult.failure("generator tier already exists");
        try {
            return register(new GeneratorTier(id, displayName, Map.of()));
        } catch (RuntimeException error) {
            return EditResult.failure(error.getMessage() == null ? "generator tier rejected" : error.getMessage());
        }
    }

    public EditResult setOutput(String id, Material material, double weight) {
        GeneratorTier current = registry.tier(id).orElse(null);
        if (current == null) return EditResult.failure("unknown tier");
        if (material == null || material.isAir()) return EditResult.failure("generator material cannot be air");
        try {
            Map<Material, Double> outputs = new LinkedHashMap<>(current.outputs());
            outputs.put(material, weight);
            return register(new GeneratorTier(current.id(), current.displayName(), outputs));
        } catch (RuntimeException error) {
            return EditResult.failure(error.getMessage() == null ? "generator output rejected" : error.getMessage());
        }
    }

    public EditResult removeOutput(String id, Material material) {
        GeneratorTier current = registry.tier(id).orElse(null);
        if (current == null) return EditResult.failure("unknown tier");
        if (material == null || !current.outputs().containsKey(material)) return EditResult.failure("unknown output");
        if (current.outputs().size() == 1) return EditResult.failure("a tier needs at least one output");
        Map<Material, Double> outputs = new LinkedHashMap<>(current.outputs());
        outputs.remove(material);
        if (backup != null) {
            try { backup.writeNow(); }
            catch (RuntimeException ignored) { return EditResult.failure("registry backup failed"); }
        }
        return register(new GeneratorTier(current.id(), current.displayName(), outputs));
    }

    public EditResult setDisplayName(String id, String displayName) {
        GeneratorTier current = registry.tier(id).orElse(null);
        if (current == null) return EditResult.failure("unknown tier");
        return register(new GeneratorTier(current.id(), displayName, current.outputs()));
    }

    public record EditResult(boolean success, String message, CompletableFuture<Void> persistence) {
        public EditResult(boolean success, String message) {
            this(success, message, CompletableFuture.completedFuture(null));
        }

        public EditResult {
            persistence = persistence == null ? CompletableFuture.completedFuture(null) : persistence;
        }

        static EditResult success(String message, CompletableFuture<Void> persistence) {
            return new EditResult(true, message, persistence);
        }

        static EditResult failure(String message) { return new EditResult(false, message); }
    }
}
