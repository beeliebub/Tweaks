package me.beeliebub.tweaks.protection;

import java.util.Locale;
import java.util.Objects;

// Identifies the audience a single RegionFlag rule applies to.
//
// A region's flag table is keyed (RegionFlag, FlagTarget) -> Boolean, allowing
// the same flag to carry different verdicts for different players inside the
// same region (e.g. PVP=false for the default audience, PVP=true for the
// "duelists" permission group). The five target kinds are evaluated in
// priority order at lookup time inside Region#resolveFlag:
//   GROUP > OWNER > MANAGER > MEMBER > DEFAULT
// The "first matching kind wins" rule keeps resolution O(1) without sorting
// while still letting admins layer overrides cleanly.
public record FlagTarget(Type type, String groupName) {

    public enum Type { DEFAULT, OWNER, MANAGER, MEMBER, GROUP }

    // Sentinel constants for the four role-based targets. Reusing them keeps
    // the inner Map<FlagTarget, Boolean> hash buckets small and avoids the
    // micro-allocations that would happen if every flag rule rebuilt its key.
    public static final FlagTarget DEFAULT = new FlagTarget(Type.DEFAULT, null);
    public static final FlagTarget OWNER = new FlagTarget(Type.OWNER, null);
    public static final FlagTarget MANAGER = new FlagTarget(Type.MANAGER, null);
    public static final FlagTarget MEMBER = new FlagTarget(Type.MEMBER, null);

    public FlagTarget {
        Objects.requireNonNull(type, "type");
        if (type == Type.GROUP) {
            Objects.requireNonNull(groupName, "groupName required for GROUP target");
            if (groupName.isBlank()) {
                throw new IllegalArgumentException("groupName must be non-blank");
            }
        } else if (groupName != null) {
            throw new IllegalArgumentException("groupName must be null for non-GROUP target");
        }
    }

    public static FlagTarget group(String name) {
        return new FlagTarget(Type.GROUP, name.toLowerCase(Locale.ROOT));
    }

    // Canonical serialized form: "default", "owner", "manager", "member", or "group:<name>".
    // Used as the YAML key under flags.<FLAG_NAME> and by /region flags listing.
    public String toKey() {
        return switch (type) {
            case DEFAULT -> "default";
            case OWNER -> "owner";
            case MANAGER -> "manager";
            case MEMBER -> "member";
            case GROUP -> "group:" + groupName;
        };
    }

    // Inverse of toKey. Returns null for unrecognized keys so callers can warn
    // and skip rather than crash on admin-edited YAML.
    public static FlagTarget fromKey(String key) {
        if (key == null) return null;
        String lower = key.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "default" -> DEFAULT;
            case "owner" -> OWNER;
            case "manager" -> MANAGER;
            case "member" -> MEMBER;
            default -> {
                if (lower.startsWith("group:")) {
                    String name = lower.substring("group:".length());
                    if (name.isBlank()) yield null;
                    yield group(name);
                }
                yield null;
            }
        };
    }

    // Parse a user-supplied target arg from /region flag.
    //   null / blank -> DEFAULT (no [target] provided)
    //   "owner"      -> OWNER
    //   "manager"    -> MANAGER
    //   "member"     -> MEMBER
    //   any other    -> GROUP:<that string, lowercased>
    // The caller is expected to validate the resulting group name against
    // PermissionManager#getGroups before persisting the rule.
    public static FlagTarget parseCommandArg(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT;
        String lower = raw.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "default" -> DEFAULT;
            case "owner" -> OWNER;
            case "manager" -> MANAGER;
            case "member" -> MEMBER;
            default -> group(lower);
        };
    }
}
