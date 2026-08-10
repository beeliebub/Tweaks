package me.beeliebub.tweaks.skyblock;

import me.beeliebub.tweaks.skyblock.challenge.Challenge;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRequirement;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeReward;
import me.beeliebub.tweaks.skyblock.economy.ShopCatalog;
import me.beeliebub.tweaks.skyblock.generator.GeneratorTier;
import me.beeliebub.tweaks.skyblock.tracking.TrackIdentifierDomain;
import me.beeliebub.tweaks.skyblock.type.IslandDifficulty;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure, ordered validation rules shared by the admin command and Dialog surfaces. */
public final class SkyblockValidation {

    private SkyblockValidation() {
    }

    public record Problem(String code, String message) {
        public Problem {
            code = Objects.requireNonNull(code, "code");
            message = Objects.requireNonNull(message, "message");
        }

        @Override
        public String toString() {
            return message;
        }
    }

    public static List<Problem> validateChallenge(Challenge challenge, Collection<Challenge> challenges,
                                                   Collection<IslandType> types,
                                                   Collection<GeneratorTier> tiers) {
        Objects.requireNonNull(challenge, "challenge");
        Set<String> knownChallenges = ids(challenges);
        Set<String> knownTypes = ids(types);
        Set<String> knownTiers = ids(tiers);
        List<Problem> problems = new ArrayList<>();
        if (challenge.requirements().isEmpty()) {
            problems.add(problem("NO_REQUIREMENTS", "has no requirements"));
        }

        Set<String> missingPrerequisites = new LinkedHashSet<>();
        for (String id : challenge.prerequisites()) {
            if (!knownChallenges.contains(id)) missingPrerequisites.add(id);
        }
        for (Challenge.PrerequisiteGroup group : challenge.anyOfGroups()) {
            for (String id : group.challengeIds()) {
                if (!knownChallenges.contains(id)) missingPrerequisites.add(id);
            }
        }
        if (!missingPrerequisites.isEmpty()) {
            problems.add(problem("MISSING_PREREQUISITE",
                    "references missing prerequisite(s): " + String.join(", ", missingPrerequisites)));
        }
        if (hasCycle(challenge, challenges)) {
            problems.add(problem("PREREQUISITE_CYCLE", "contains a prerequisite cycle"));
        }

        for (ChallengeRequirement requirement : challenge.requirements()) {
            if (!(requirement instanceof ChallengeRequirement.Tracked tracked)) continue;
            if (!recordable(tracked)) {
                problems.add(problem("INVALID_TRACKED_IDENTIFIER",
                        "tracked key cannot be recorded: " + tracked.key().format()));
            }
        }
        for (ChallengeReward reward : challenge.rewards()) {
            if (reward instanceof ChallengeReward.GeneratorUnlock generator
                    && !knownTiers.contains(generator.tierId())) {
                problems.add(problem("MISSING_GENERATOR", "references missing generator tier: " + generator.tierId()));
            }
        }
        if (!challenge.typeIds().isEmpty()) {
            boolean compatible = types != null && types.stream()
                    .filter(type -> challenge.typeIds().contains(type.id()))
                    .anyMatch(type -> type.allowedChallengeIds().isEmpty()
                            || type.allowedChallengeIds().contains(challenge.id()));
            if (!compatible || knownTypes.isEmpty()) {
                problems.add(problem("TYPE_GATING", "type gating excludes every configured island type"));
            }
        }
        return List.copyOf(problems);
    }

    public static List<Problem> validateIslandType(IslandType type,
                                                   Collection<IslandDifficulty> difficulties,
                                                   Set<String> templateIds) {
        Objects.requireNonNull(type, "type");
        Set<String> knownDifficulties = difficulties == null ? Set.of() : difficulties.stream()
                .map(IslandDifficulty::id).collect(java.util.stream.Collectors.toSet());
        Set<String> knownTemplates = templateIds == null ? Set.of() : Set.copyOf(templateIds);
        List<Problem> problems = new ArrayList<>();
        if (type.difficultyIds().isEmpty()) {
            problems.add(problem("NO_DIFFICULTIES", "has no difficulty options"));
        }
        List<String> missingDifficulties = type.difficultyIds().stream()
                .filter(id -> !knownDifficulties.contains(id)).toList();
        if (!missingDifficulties.isEmpty()) {
            problems.add(problem("MISSING_DIFFICULTY",
                    "references missing difficulty(s): " + String.join(", ", missingDifficulties)));
        }
        if (!type.templateId().isBlank() && !knownTemplates.contains(type.templateId())) {
            problems.add(problem("MISSING_TEMPLATE", "references missing template: " + type.templateId()));
        }
        for (int index = 0; index < type.kit().size(); index++) {
            try {
                type.kit().get(index).itemStack().serialize();
            } catch (RuntimeException error) {
                problems.add(problem("INVALID_KIT", "kit entry " + index + " cannot be serialized"));
            }
        }
        if (type.templateId().isBlank() && type.kit().isEmpty()) {
            problems.add(problem("VOID_ISLAND", "produces a void island because it has no template or kit"));
        }
        return List.copyOf(problems);
    }

    public static List<Problem> validateGeneratorTier(GeneratorTier tier) {
        Objects.requireNonNull(tier, "tier");
        List<Problem> problems = new ArrayList<>();
        if (tier.outputs().isEmpty()) {
            problems.add(problem("EMPTY_OUTPUTS", "has an empty output table"));
        }
        double total = 0.0;
        for (Map.Entry<Material, Double> output : tier.outputs().entrySet()) {
            Double weight = output.getValue();
            if (weight == null || !Double.isFinite(weight) || weight <= 0) {
                problems.add(problem("INVALID_WEIGHT",
                        "has a non-finite or non-positive output weight for " + output.getKey()));
            } else {
                total += weight;
            }
        }
        if (!Double.isFinite(total) || total <= 0) {
            problems.add(problem("INVALID_TOTAL_WEIGHT", "has a non-finite or non-positive total weight"));
        }
        return List.copyOf(problems);
    }

    public static List<Problem> validateShopEntry(ShopCatalog.Entry entry) {
        Objects.requireNonNull(entry, "entry");
        return entry.buyable() || entry.sellable()
                ? List.of() : List.of(problem("NO_ENABLED_DIRECTION", "has both buy and sell directions disabled"));
    }

    /** Compatibility descriptions for callers that only need human-readable messages. */
    public static List<String> challenge(Challenge challenge, Collection<Challenge> challenges,
                                         Collection<IslandType> types, Collection<GeneratorTier> tiers) {
        return messages(validateChallenge(challenge, challenges, types, tiers));
    }

    public static List<String> islandType(IslandType type, Collection<IslandDifficulty> difficulties,
                                          Set<String> templateIds) {
        return messages(validateIslandType(type, difficulties, templateIds));
    }

    public static List<String> generatorTier(GeneratorTier tier) {
        return messages(validateGeneratorTier(tier));
    }

    public static List<String> shopEntry(ShopCatalog.Entry entry) {
        return messages(validateShopEntry(entry));
    }

    private static Problem problem(String code, String message) {
        return new Problem(code, message);
    }

    private static List<String> messages(List<Problem> problems) {
        return problems.stream().map(Problem::message).toList();
    }

    private static boolean recordable(ChallengeRequirement.Tracked tracked) {
        String identifier = tracked.key().identifier();
        return switch (tracked.key().category().identifierDomain()) {
            case MATERIAL -> Material.matchMaterial(identifier) != null;
            case ENTITY_TYPE -> EntityType.fromName(identifier) != null;
        };
    }

    private static boolean hasCycle(Challenge start, Collection<Challenge> all) {
        if (all == null || all.isEmpty()) return false;
        Map<String, Challenge> definitions = new HashMap<>();
        for (Challenge challenge : all) definitions.put(challenge.id(), challenge);
        definitions.put(start.id(), start);
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        return visit(start.id(), definitions, visiting, visited);
    }

    private static boolean visit(String id, Map<String, Challenge> definitions,
                                 Set<String> visiting, Set<String> visited) {
        if (!definitions.containsKey(id)) return false;
        if (!visiting.add(id)) return true;
        if (visited.contains(id)) {
            visiting.remove(id);
            return false;
        }
        Challenge challenge = definitions.get(id);
        for (String prerequisite : challenge.prerequisites()) {
            if (visit(prerequisite, definitions, visiting, visited)) return true;
        }
        for (Challenge.PrerequisiteGroup group : challenge.anyOfGroups()) {
            for (String prerequisite : group.challengeIds()) {
                if (visit(prerequisite, definitions, visiting, visited)) return true;
            }
        }
        visiting.remove(id);
        visited.add(id);
        return false;
    }

    private static Set<String> ids(Collection<?> definitions) {
        if (definitions == null) return Set.of();
        Set<String> ids = new HashSet<>();
        for (Object definition : definitions) {
            if (definition instanceof Challenge challenge) ids.add(challenge.id());
            else if (definition instanceof IslandType type) ids.add(type.id());
            else if (definition instanceof GeneratorTier tier) ids.add(tier.id());
        }
        return ids;
    }
}
