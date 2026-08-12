package me.beeliebub.tweaks.skyblock.type;

import me.beeliebub.tweaks.skyblock.admin.SkyblockRegistryBackup;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/** Dialog-free type and difficulty authoring operations. */
public final class TypeAdminService {
    private final TypeRegistry registry;
    private final IslandManager islands;
    private final SkyblockRegistryBackup backup;

    public TypeAdminService(TypeRegistry registry, IslandManager islands) {
        this(registry, islands, null);
    }

    public TypeAdminService(TypeRegistry registry, IslandManager islands, JavaPlugin plugin) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.islands = Objects.requireNonNull(islands, "islands");
        this.backup = plugin == null ? null : new SkyblockRegistryBackup(plugin, "types.yml");
    }

    /** Raw upsert used by registry loading; command and GUI paths should use create/update methods. */
    public CompletableFuture<Void> registerType(IslandType type) {
        registry.registerType(type);
        return registry.saveAsync();
    }

    public EditResult createType(IslandType type) {
        if (type == null) return EditResult.failure("type is required");
        if (registry.type(type.id()).isPresent()) return EditResult.failure("type already exists");
        try {
            registry.registerType(type);
            return EditResult.success("saved", registry.saveAsync());
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    public EditResult updateType(String id, UnaryOperator<IslandType> mutator) {
        IslandType current = registry.type(id).orElse(null);
        if (current == null || mutator == null) return EditResult.failure("unknown type");
        try {
            IslandType next = Objects.requireNonNull(mutator.apply(current), "updated type");
            if (!current.id().equals(next.id())) return EditResult.failure("type id cannot change");
            registry.registerType(next);
            return EditResult.success("saved", registry.saveAsync());
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    public EditResult setKit(String id, List<IslandType.KitItem> kit) {
        return updateType(id, current -> new IslandType(current.id(), current.displayName(),
                current.difficultyIds(), current.templateId(), kit, current.biome(), current.allowedChallengeIds()));
    }

    public EditResult setAllowedChallenges(String id, Set<String> challengeIds) {
        return updateType(id, current -> new IslandType(current.id(), current.displayName(),
                current.difficultyIds(), current.templateId(), current.kit(), current.biome(),
                challengeIds == null ? Set.of() : new LinkedHashSet<>(challengeIds)));
    }

    public EditResult setDifficulties(String id, Set<String> difficultyIds) {
        return updateType(id, current -> new IslandType(current.id(), current.displayName(),
                difficultyIds, current.templateId(), current.kit(), current.biome(), current.allowedChallengeIds()));
    }

    public EditResult setTemplate(String id, String templateId) {
        return updateType(id, current -> new IslandType(current.id(), current.displayName(),
                current.difficultyIds(), templateId, current.kit(), current.biome(), current.allowedChallengeIds()));
    }

    public EditResult setBiome(String id, String biome) {
        return updateType(id, current -> new IslandType(current.id(), current.displayName(),
                current.difficultyIds(), current.templateId(), current.kit(), biome, current.allowedChallengeIds()));
    }

    public EditResult setDisplayName(String id, String displayName) {
        return updateType(id, current -> new IslandType(current.id(), displayName, current.difficultyIds(),
                current.templateId(), current.kit(), current.biome(), current.allowedChallengeIds()));
    }

    public EditResult registerDifficulty(IslandDifficulty difficulty) {
        if (difficulty == null) return EditResult.failure("difficulty is required");
        try {
            registry.registerDifficulty(difficulty);
            return EditResult.success("saved", registry.saveAsync());
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    public EditResult createDifficulty(IslandDifficulty difficulty) {
        if (difficulty == null) return EditResult.failure("difficulty is required");
        if (registry.difficulty(difficulty.id()).isPresent()) return EditResult.failure("difficulty already exists");
        return registerDifficulty(difficulty);
    }

    public TypeRegistry.DeleteResult deleteType(String id) {
        if (!backupSucceeded()) return new TypeRegistry.DeleteResult(false, 0, "registry backup failed");
        TypeRegistry.DeleteResult result = registry.deleteType(id, islands);
        return result.deleted()
                ? new TypeRegistry.DeleteResult(result.deleted(), result.references(), result.reason(), registry.saveAsync())
                : result;
    }

    public TypeRegistry.DeleteResult deleteDifficulty(String id) {
        if (!backupSucceeded()) return new TypeRegistry.DeleteResult(false, 0, "registry backup failed");
        TypeRegistry.DeleteResult result = registry.deleteDifficulty(id, islands);
        return result.deleted()
                ? new TypeRegistry.DeleteResult(result.deleted(), result.references(), result.reason(), registry.saveAsync())
                : result;
    }

    private boolean backupSucceeded() {
        if (backup == null) return true;
        try {
            backup.writeNow();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static EditResult failure(RuntimeException error) {
        return EditResult.failure(error.getMessage() == null ? "type rejected" : error.getMessage());
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
