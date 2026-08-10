package me.beeliebub.tweaks.skyblock.ui.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Dialog-free validation and list decisions shared by Skyblock admin screens and tests. */
public final class AdminActions {
    private AdminActions() {
    }

    public static Optional<String> validId(String value, String label) {
        if (value == null || value.isBlank()) return Optional.empty();
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]+")) return Optional.empty();
        return Optional.of(normalized);
    }

    public static <T> Optional<List<T>> removeAt(List<T> source, int index) {
        if (source == null || index < 0 || index >= source.size()) return Optional.empty();
        List<T> copy = new ArrayList<>(source);
        copy.remove(index);
        return Optional.of(List.copyOf(copy));
    }

    public static <T> Optional<List<T>> move(List<T> source, int from, int to) {
        if (source == null || from < 0 || from >= source.size() || to < 0 || to >= source.size()) {
            return Optional.empty();
        }
        List<T> copy = new ArrayList<>(source);
        T value = copy.remove(from);
        copy.add(to, value);
        return Optional.of(List.copyOf(copy));
    }

    public static <T> T require(T value, String label) {
        return Objects.requireNonNull(value, label);
    }
}
