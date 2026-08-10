package me.beeliebub.tweaks.skyblock.tracking;

import java.util.Locale;
import java.util.Objects;

/** Canonical {@code category:identifier} challenge-counter key. */
public record TrackKey(TrackCategory category, String identifier) {
    public TrackKey {
        category = Objects.requireNonNull(category, "category");
        if (identifier == null || identifier.isBlank() || identifier.indexOf(':') >= 0
                || !identifier.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid tracking identifier: " + identifier);
        }
        identifier = identifier.toUpperCase(Locale.ROOT);
    }

    public static TrackKey parse(String value) {
        if (value == null) throw new IllegalArgumentException("Tracking key is null");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
            throw new IllegalArgumentException("Tracking key must be category:identifier");
        }
        try {
            return new TrackKey(TrackCategory.valueOf(value.substring(0, separator).toUpperCase(Locale.ROOT)),
                    value.substring(separator + 1));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unknown tracking key: " + value, error);
        }
    }

    public String format() {
        return category.name().toLowerCase(Locale.ROOT) + ":" + identifier;
    }

    @Override
    public String toString() {
        return format();
    }
}
