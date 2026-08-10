package me.beeliebub.tweaks.skyblock.command.admin;

import me.beeliebub.tweaks.skyblock.tracking.TrackCategory;
import me.beeliebub.tweaks.skyblock.tracking.TrackKey;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Function;

/** Bukkit-light, side-effect-free parsing helpers for the Skyblock administrator command. */
public final class AdminArgumentParser {
    public static final String CONFIRM = "confirm";

    private AdminArgumentParser() {
    }

    /** Joins a command tail without exposing array-bound or null-element failures to the router. */
    public static String joinTail(String[] args, int start) {
        if (args == null || args.length == 0 || start >= args.length) return "";
        int first = Math.max(0, start);
        StringBuilder joined = new StringBuilder();
        for (int index = first; index < args.length; index++) {
            String value = args[index];
            if (value == null || value.isBlank()) continue;
            if (joined.length() > 0) joined.append(' ');
            joined.append(value.trim());
        }
        return joined.toString();
    }

    /** List overload for callers that have already copied command arguments. */
    public static String joinTail(List<String> args, int start) {
        if (args == null || args.isEmpty() || start >= args.size()) return "";
        int first = Math.max(0, start);
        StringBuilder joined = new StringBuilder();
        for (int index = first; index < args.size(); index++) {
            String value = args.get(index);
            if (value == null || value.isBlank()) continue;
            if (joined.length() > 0) joined.append(' ');
            joined.append(value.trim());
        }
        return joined.toString();
    }

    /** Parses a non-air Bukkit material using Paper's namespace- and case-tolerant matcher. */
    public static Optional<Material> parseMaterial(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            Material material = Material.matchMaterial(value.trim());
            return material == null || material.isAir() ? Optional.empty() : Optional.of(material);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /** Parses a material or throws a command-friendly validation exception. */
    public static Material requireMaterial(String value) {
        return parseMaterial(value).orElseThrow(() ->
                new IllegalArgumentException("unknown material " + safe(value)));
    }

    /** Parses an entity type by its stable Bukkit name. */
    public static Optional<EntityType> parseEntityType(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String normalized = value.trim();
        int separator = normalized.indexOf(':');
        if (separator >= 0 && separator + 1 < normalized.length()) {
            normalized = normalized.substring(separator + 1);
        }
        try {
            return Optional.ofNullable(EntityType.fromName(normalized));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /** Parses an entity type or throws a command-friendly validation exception. */
    public static EntityType requireEntityType(String value) {
        return parseEntityType(value).orElseThrow(() ->
                new IllegalArgumentException("unknown entity type " + safe(value)));
    }

    /** Parses a tracking key and rejects identifiers outside the category's identifier domain. */
    public static Optional<TrackKey> parseTrackKey(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            TrackKey key = TrackKey.parse(value.trim());
            return isValidIdentifier(key.category(), key.identifier()) ? Optional.of(key) : Optional.empty();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    /** Descriptive alias for {@link #parseTrackKey(String)}. */
    public static Optional<TrackKey> parseTrackedKey(String value) {
        return parseTrackKey(value);
    }

    /** Returns whether an identifier belongs to the material/entity domain declared by its category. */
    public static boolean isValidIdentifier(TrackCategory category, String identifier) {
        return validateIdentifier(category, identifier).isEmpty();
    }

    /** Alias used by validation call sites that refer to tracked identifiers explicitly. */
    public static boolean isValidTrackedIdentifier(TrackCategory category, String identifier) {
        return isValidIdentifier(category, identifier);
    }

    /**
     * Returns an empty optional when valid, or a short reason suitable for an invalid-input
     * message when the identifier is not recordable.
     */
    public static Optional<String> validateIdentifier(TrackCategory category, String identifier) {
        if (category == null) return Optional.of("tracking category is required");
        if (identifier == null || identifier.isBlank()) return Optional.of("tracking identifier is required");
        try {
            // TrackKey owns the stable identifier grammar and uppercase normalization.  Creating
            // one here keeps this helper aligned with the persistence key format.
            new TrackKey(category, identifier.trim());
        } catch (IllegalArgumentException error) {
            return Optional.of(error.getMessage() == null ? "invalid tracking identifier" : error.getMessage());
        }
        boolean known = switch (category.identifierDomain()) {
            case MATERIAL -> parseMaterial(identifier).isPresent();
            case ENTITY_TYPE -> parseEntityType(identifier).isPresent();
        };
        return known ? Optional.empty() : Optional.of("unknown "
                + category.identifierDomain().name().toLowerCase(Locale.ROOT)
                + " identifier: " + identifier.trim());
    }

    /** Descriptive alias for {@link #validateIdentifier(TrackCategory, String)}. */
    public static Optional<String> validateTrackedIdentifier(TrackCategory category, String identifier) {
        return validateIdentifier(category, identifier);
    }

    /** Case-insensitive, order-preserving prefix filter that never mutates the input collection. */
    public static List<String> filterPrefix(Collection<String> candidates, String prefix) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        String normalized = safe(prefix).toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                filtered.add(candidate);
            }
        }
        return List.copyOf(filtered);
    }

    /** Generic prefix filter for registry records or other completion candidates. */
    public static <T> List<T> filterPrefix(Collection<T> candidates, String prefix,
                                            Function<? super T, String> text) {
        Objects.requireNonNull(text, "text");
        if (candidates == null || candidates.isEmpty()) return List.of();
        String normalized = safe(prefix).toLowerCase(Locale.ROOT);
        List<T> filtered = new ArrayList<>();
        for (T candidate : candidates) {
            String value = candidate == null ? null : text.apply(candidate);
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(normalized)) filtered.add(candidate);
        }
        return List.copyOf(filtered);
    }

    /** Parses a finite integer without throwing for malformed command input. */
    public static OptionalInt parseInt(String value) {
        if (value == null || value.isBlank()) return OptionalInt.empty();
        try {
            return OptionalInt.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    /** Boxed Optional variant for APIs that use ordinary generic optionals. */
    public static Optional<Integer> parseInteger(String value) {
        OptionalInt parsed = parseInt(value);
        return parsed.isPresent() ? Optional.of(parsed.getAsInt()) : Optional.empty();
    }

    /** Parses a finite double without throwing for malformed command input. */
    public static OptionalDouble parseDouble(String value) {
        if (value == null || value.isBlank()) return OptionalDouble.empty();
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? OptionalDouble.of(parsed) : OptionalDouble.empty();
        } catch (NumberFormatException ignored) {
            return OptionalDouble.empty();
        }
    }

    /** Boxed Optional variant for APIs that use ordinary generic optionals. */
    public static Optional<Double> parseDecimal(String value) {
        OptionalDouble parsed = parseDouble(value);
        return parsed.isPresent() ? Optional.of(parsed.getAsDouble()) : Optional.empty();
    }

    /** Parses an integer or throws a short, stable validation exception. */
    public static int requireInt(String value) {
        return parseInt(value).orElseThrow(() ->
                new IllegalArgumentException("invalid integer: " + safe(value)));
    }

    public static Optional<Long> parseLong(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public static long requireLong(String value) {
        return parseLong(value).orElseThrow(() ->
                new IllegalArgumentException("invalid integer: " + safe(value)));
    }

    /** Parses a double or throws a short, stable validation exception. */
    public static double requireDouble(String value) {
        return parseDouble(value).orElseThrow(() ->
                new IllegalArgumentException("invalid number: " + safe(value)));
    }

    /** Detects the literal confirmation token without changing the caller's argument array. */
    public static boolean hasTrailingConfirm(String[] args) {
        return args != null && args.length > 0 && isConfirm(args[args.length - 1]);
    }

    /** List overload for confirmation checks. */
    public static boolean hasTrailingConfirm(List<String> args) {
        return args != null && !args.isEmpty() && isConfirm(args.get(args.size() - 1));
    }

    /** Returns a new array with a trailing confirmation token removed, leaving the input untouched. */
    public static String[] withoutTrailingConfirm(String[] args) {
        if (args == null) return new String[0];
        return hasTrailingConfirm(args) ? Arrays.copyOf(args, args.length - 1) : args.clone();
    }

    /** Returns an immutable list with a trailing confirmation token removed, leaving the input untouched. */
    public static List<String> withoutTrailingConfirm(List<String> args) {
        if (args == null || args.isEmpty()) return List.of();
        int end = hasTrailingConfirm(args) ? args.size() - 1 : args.size();
        return List.copyOf(args.subList(0, end));
    }

    private static boolean isConfirm(String value) {
        return value != null && CONFIRM.equalsIgnoreCase(value.trim());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
