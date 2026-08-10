package me.beeliebub.tweaks.skyblock.type;

import java.util.Locale;
import java.util.Objects;

/** Immutable difficulty definition used to filter island types. */
public record IslandDifficulty(String id, String displayName, int order, double multiplier) {
    public IslandDifficulty {
        id = normalize(id, "difficulty id");
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        if (!Double.isFinite(multiplier) || multiplier <= 0) throw new IllegalArgumentException("Difficulty multiplier must be finite and positive");
    }

    public IslandDifficulty(String id, String displayName, int order) {
        this(id, displayName, order, 1.0d);
    }

    private static String normalize(String value, String label) {
        Objects.requireNonNull(value, label);
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !normalized.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
        return normalized;
    }
}
