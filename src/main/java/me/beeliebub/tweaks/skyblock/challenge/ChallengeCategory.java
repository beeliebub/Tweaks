package me.beeliebub.tweaks.skyblock.challenge;

import java.util.Locale;
import java.util.Objects;

/** A named group used to organize challenge screens. */
public record ChallengeCategory(String id, String displayName, int order) {
    public ChallengeCategory {
        Objects.requireNonNull(id, "id");
        id = id.trim().toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid challenge category: " + id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
    }
}
