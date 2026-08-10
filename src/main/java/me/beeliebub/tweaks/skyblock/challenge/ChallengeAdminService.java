package me.beeliebub.tweaks.skyblock.challenge;

import me.beeliebub.tweaks.skyblock.admin.SkyblockRegistryBackup;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Dialog-free authoring operations for challenge definitions. */
public final class ChallengeAdminService {
    private final ChallengeRegistry registry;
    private final SkyblockRegistryBackup backup;
    private final TypeRegistry types;

    public ChallengeAdminService(ChallengeRegistry registry) {
        this(registry, null, null);
    }

    public ChallengeAdminService(ChallengeRegistry registry, JavaPlugin plugin) {
        this(registry, null, plugin);
    }

    public ChallengeAdminService(ChallengeRegistry registry, TypeRegistry types, JavaPlugin plugin) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.backup = plugin == null ? null : new SkyblockRegistryBackup(plugin, "challenges.yml");
        this.types = types;
    }

    public EditResult create(Challenge challenge) {
        if (challenge == null) return new EditResult(false, "challenge is required");
        if (registry.challenge(challenge.id()).isPresent()) return new EditResult(false, "challenge already exists");
        try {
            registry.register(challenge);
            registry.saveAsync();
            return new EditResult(true, "Counters start at zero; blocks placed before this challenge existed are untainted.");
        } catch (RuntimeException error) {
            return new EditResult(false, error.getMessage() == null ? "challenge rejected" : error.getMessage());
        }
    }
    public EditResult delete(String id) {
        if (!backupSucceeded()) return new EditResult(false, "registry backup failed");
        ChallengeRegistry.DeleteResult result = registry.deleteChallenge(id);
        if (result.deleted()) registry.saveAsync();
        return new EditResult(result.deleted(), result.deleted() ? "deleted"
                : result.reason() + (result.references() == 0 ? "" : " (" + result.references() + " reference(s))"));
    }

    public EditResult createCategory(ChallengeCategory category) {
        try {
            registry.registerCategory(category);
            registry.saveAsync();
            return new EditResult(true, "category saved");
        } catch (RuntimeException error) {
            return new EditResult(false, error.getMessage() == null ? "category rejected" : error.getMessage());
        }
    }

    public EditResult deleteCategory(String id) {
        if (!backupSucceeded()) return new EditResult(false, "registry backup failed");
        ChallengeRegistry.DeleteResult result = registry.deleteCategory(id);
        if (result.deleted()) registry.saveAsync();
        return new EditResult(result.deleted(), result.deleted()
                ? result.reason() : result.reason() + " (" + result.references() + " reference(s))");
    }

    public EditResult addRequirement(String id, ChallengeRequirement requirement) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null || requirement == null) return new EditResult(false, "unknown challenge");
        List<ChallengeRequirement> requirements = new ArrayList<>(challenge.requirements());
        requirements.add(requirement);
        return replace(challenge, requirements, challenge.prerequisites(), challenge.anyOfGroups(),
                challenge.rewards(), challenge.typeIds());
    }

    public EditResult addReward(String id, ChallengeReward reward) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null || reward == null) return new EditResult(false, "unknown challenge");
        List<ChallengeReward> rewards = new ArrayList<>(challenge.rewards());
        rewards.add(reward);
        return replace(challenge, challenge.requirements(), challenge.prerequisites(), challenge.anyOfGroups(),
                rewards, challenge.typeIds());
    }

    public EditResult removeRequirement(String id, int index) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null) return new EditResult(false, "unknown challenge");
        if (index < 0 || index >= challenge.requirements().size()) return new EditResult(false, "requirement index out of range");
        List<ChallengeRequirement> values = new ArrayList<>(challenge.requirements());
        values.remove(index);
        return replace(challenge, values, challenge.prerequisites(), challenge.anyOfGroups(),
                challenge.rewards(), challenge.typeIds());
    }

    public EditResult moveRequirement(String id, int from, int to) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null) return new EditResult(false, "unknown challenge");
        if (!validMove(from, to, challenge.requirements().size())) return new EditResult(false, "requirement index out of range");
        List<ChallengeRequirement> values = new ArrayList<>(challenge.requirements());
        ChallengeRequirement value = values.remove(from);
        values.add(to, value);
        return replace(challenge, values, challenge.prerequisites(), challenge.anyOfGroups(),
                challenge.rewards(), challenge.typeIds());
    }

    public EditResult removeReward(String id, int index) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null) return new EditResult(false, "unknown challenge");
        if (index < 0 || index >= challenge.rewards().size()) return new EditResult(false, "reward index out of range");
        List<ChallengeReward> values = new ArrayList<>(challenge.rewards());
        values.remove(index);
        return replace(challenge, challenge.requirements(), challenge.prerequisites(), challenge.anyOfGroups(),
                values, challenge.typeIds());
    }

    public EditResult moveReward(String id, int from, int to) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null) return new EditResult(false, "unknown challenge");
        if (!validMove(from, to, challenge.rewards().size())) return new EditResult(false, "reward index out of range");
        List<ChallengeReward> values = new ArrayList<>(challenge.rewards());
        ChallengeReward value = values.remove(from);
        values.add(to, value);
        return replace(challenge, challenge.requirements(), challenge.prerequisites(), challenge.anyOfGroups(),
                values, challenge.typeIds());
    }

    public EditResult setRewards(String id, List<ChallengeReward> rewards) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null) return new EditResult(false, "unknown challenge");
        return replace(challenge, challenge.requirements(), challenge.prerequisites(), challenge.anyOfGroups(),
                rewards == null ? List.of() : rewards, challenge.typeIds());
    }

    public EditResult setCategory(String id, String categoryId) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null) return new EditResult(false, "unknown challenge");
        if (registry.category(categoryId).isEmpty()) return new EditResult(false, "unknown challenge category");
        try {
            registry.register(new Challenge(challenge.id(), categoryId, challenge.displayName(), challenge.description(),
                    challenge.requirements(), challenge.prerequisites(), challenge.anyOfGroups(), challenge.rewards(),
                    challenge.typeIds()));
            registry.saveAsync();
            return new EditResult(true, "challenge saved");
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    public EditResult setAnyOfGroups(String id, List<Challenge.PrerequisiteGroup> groups) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null) return new EditResult(false, "unknown challenge");
        return replace(challenge, challenge.requirements(), challenge.prerequisites(),
                groups == null ? List.of() : groups, challenge.rewards(), challenge.typeIds());
    }

    public EditResult setCategoryOrder(String categoryId, int order) {
        ChallengeCategory category = registry.category(categoryId).orElse(null);
        if (category == null) return new EditResult(false, "unknown challenge category");
        try {
            registry.registerCategory(new ChallengeCategory(category.id(), category.displayName(), order));
            registry.saveAsync();
            return new EditResult(true, "category saved");
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    public EditResult editCategory(String categoryId, String displayName, int order) {
        ChallengeCategory category = registry.category(categoryId).orElse(null);
        if (category == null) return new EditResult(false, "unknown challenge category");
        try {
            registry.registerCategory(new ChallengeCategory(category.id(), displayName, order));
            registry.saveAsync();
            return new EditResult(true, "category saved");
        } catch (RuntimeException error) {
            return failure(error);
        }
    }

    public EditResult setPrerequisites(String id, List<String> prerequisites) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null) return new EditResult(false, "unknown challenge");
        return replace(challenge, challenge.requirements(), prerequisites, challenge.anyOfGroups(),
                challenge.rewards(), challenge.typeIds());
    }

    public EditResult setTypes(String id, Set<String> typeIds) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null) return new EditResult(false, "unknown challenge");
        Set<String> normalized = new LinkedHashSet<>(typeIds == null ? Set.of() : typeIds);
        if (types != null) {
            for (String typeId : normalized) if (types.type(typeId).isEmpty()) {
                return new EditResult(false, "unknown island type: " + typeId);
            }
        }
        return replace(challenge, challenge.requirements(), challenge.prerequisites(), challenge.anyOfGroups(),
                challenge.rewards(), normalized);
    }

    public EditResult editText(String id, String displayName, String description) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null) return new EditResult(false, "unknown challenge");
        try {
            registry.register(new Challenge(challenge.id(), challenge.categoryId(), displayName,
                    description, challenge.requirements(), challenge.prerequisites(), challenge.anyOfGroups(),
                    challenge.rewards(), challenge.typeIds()));
            registry.saveAsync();
            return new EditResult(true, "challenge saved");
        } catch (RuntimeException error) {
            return new EditResult(false, error.getMessage() == null ? "challenge rejected" : error.getMessage());
        }
    }

    public EditResult editDisplayName(String id, String displayName) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null) return new EditResult(false, "unknown challenge");
        try {
            registry.register(new Challenge(challenge.id(), challenge.categoryId(), displayName,
                    challenge.description(), challenge.requirements(), challenge.prerequisites(), challenge.anyOfGroups(),
                    challenge.rewards(), challenge.typeIds()));
            registry.saveAsync();
            return new EditResult(true, "challenge saved");
        } catch (RuntimeException error) {
            return new EditResult(false, error.getMessage() == null ? "challenge rejected" : error.getMessage());
        }
    }

    public EditResult editDescription(String id, String description) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null) return new EditResult(false, "unknown challenge");
        try {
            registry.register(new Challenge(challenge.id(), challenge.categoryId(), challenge.displayName(),
                    description, challenge.requirements(), challenge.prerequisites(), challenge.anyOfGroups(),
                    challenge.rewards(), challenge.typeIds()));
            registry.saveAsync();
            return new EditResult(true, "challenge saved");
        } catch (RuntimeException error) {
            return new EditResult(false, error.getMessage() == null ? "challenge rejected" : error.getMessage());
        }
    }

    public EditResult editRequirement(String id, int index, ChallengeRequirement replacement) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null) return new EditResult(false, "unknown challenge");
        if (replacement == null || index < 0 || index >= challenge.requirements().size()) {
            return new EditResult(false, "requirement index out of range");
        }
        List<ChallengeRequirement> values = new ArrayList<>(challenge.requirements());
        values.set(index, replacement);
        return replace(challenge, values, challenge.prerequisites(), challenge.anyOfGroups(),
                challenge.rewards(), challenge.typeIds());
    }

    public EditResult editReward(String id, int index, ChallengeReward replacement) {
        Challenge challenge = registry.challenge(id).orElse(null);
        if (challenge == null) return new EditResult(false, "unknown challenge");
        if (replacement == null || index < 0 || index >= challenge.rewards().size()) {
            return new EditResult(false, "reward index out of range");
        }
        List<ChallengeReward> values = new ArrayList<>(challenge.rewards());
        values.set(index, replacement);
        return replace(challenge, challenge.requirements(), challenge.prerequisites(), challenge.anyOfGroups(),
                values, challenge.typeIds());
    }

    private EditResult replace(Challenge current, List<ChallengeRequirement> requirements,
                               List<String> prerequisites, List<Challenge.PrerequisiteGroup> groups,
                               List<ChallengeReward> rewards, Set<String> typeIds) {
        try {
            registry.register(new Challenge(current.id(), current.categoryId(), current.displayName(),
                    current.description(), requirements, prerequisites, groups, rewards, typeIds));
            registry.saveAsync();
            return new EditResult(true, "challenge saved");
        } catch (RuntimeException error) {
            return new EditResult(false, error.getMessage() == null ? "challenge rejected" : error.getMessage());
        }
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

    private static boolean validMove(int from, int to, int size) {
        return from >= 0 && to >= 0 && from < size && to < size;
    }

    private static EditResult failure(RuntimeException error) {
        return new EditResult(false, error.getMessage() == null ? "challenge rejected" : error.getMessage());
    }
    public record EditResult(boolean success, String message) { }
}
