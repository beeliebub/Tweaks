package me.beeliebub.tweaks.skyblock.challenge;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Immutable administrator-authored challenge definition. */
public record Challenge(
        String id,
        String categoryId,
        String displayName,
        String description,
        List<ChallengeRequirement> requirements,
        List<String> prerequisites,
        List<PrerequisiteGroup> anyOfGroups,
        List<ChallengeReward> rewards,
        Set<String> typeIds
) {
    public Challenge {
        id = normalize(id, "challenge id");
        categoryId = normalize(categoryId == null ? "general" : categoryId, "category id");
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        description = description == null ? "" : description;
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        prerequisites = normalizeIds(prerequisites);
        anyOfGroups = anyOfGroups == null ? List.of() : List.copyOf(anyOfGroups);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        typeIds = normalizeIds(typeIds);
    }

    public Challenge(String id, String categoryId, String displayName,
                     List<ChallengeRequirement> requirements, List<ChallengeReward> rewards) {
        this(id, categoryId, displayName, "", requirements, List.of(), List.of(), rewards, Set.of());
    }

    public boolean unrestricted() {
        return typeIds.isEmpty();
    }

    public record PrerequisiteGroup(int required, Set<String> challengeIds) {
        public PrerequisiteGroup {
            if (required <= 0) throw new IllegalArgumentException("Prerequisite count must be positive");
            if (challengeIds == null || challengeIds.isEmpty()) {
                throw new IllegalArgumentException("Prerequisite group cannot be empty");
            }
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String id : challengeIds) normalized.add(normalize(id, "prerequisite id"));
            if (required > normalized.size()) {
                throw new IllegalArgumentException("Prerequisite count exceeds group size");
            }
            challengeIds = Collections.unmodifiableSet(normalized);
        }
    }

    private static List<String> normalizeIds(Iterable<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) normalized.add(normalize(value, "challenge id"));
        return List.copyOf(normalized);
    }

    private static Set<String> normalizeIds(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(new LinkedHashSet<>(normalizeIds((Iterable<String>) values)));
    }

    private static String normalize(String value, String label) {
        Objects.requireNonNull(value, label);
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid " + label + ": " + value);
        return normalized;
    }
}
