package me.beeliebub.tweaks.protection;

import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Source-of-truth definition of a single protected region.
//
// Lives in the in-memory ConcurrentHashMap<String, Region> Region Cache that
// backs every protection lookup. Stamped onto chunks indirectly via the PDC
// pointer system (chunk PDC stores the String id; the cache resolves id ->
// Region). Persisted as one YAML file per region under regions/*.yml.
//
// Immutable on purpose: mutation of a live Region between an event handler
// reading flags and acting on them would re-introduce the race conditions the
// hybrid architecture is designed to eliminate. To change any field,
// construct a new Region and atomically replace the cache entry.
//
// Two flag tables share resolution duties:
//   * flagRules     — Map<RegionFlag, Map<FlagTarget, Boolean>> for the
//                     boolean flags, with role/group targeting.
//   * materialFlags — Map<RegionFlag, Set<Material>> for the four list-
//                     based ALLOW_/DENY_ flags. Untargeted; the listed
//                     materials override the corresponding base boolean
//                     flag for everyone.
//
// `parentId` opts the region into the hierarchical model: when set, a missing
// flag rule on the child falls through to the parent (and on up the chain).
// Hierarchy lookups happen in ProtectionManager because they need the live
// regions cache to resolve ids.
public record Region(
        String id,
        UUID owner,
        List<UUID> members,
        Map<RegionFlag, Map<FlagTarget, Boolean>> flagRules,
        Map<RegionFlag, Set<Material>> materialFlags,
        String parentId
) {

    public Region {
        members = List.copyOf(members);
        flagRules = deepCopyFlagRules(flagRules);
        materialFlags = deepCopyMaterialFlags(materialFlags);
    }

    private static Map<RegionFlag, Map<FlagTarget, Boolean>> deepCopyFlagRules(
            Map<RegionFlag, Map<FlagTarget, Boolean>> source) {
        if (source.isEmpty()) return Map.of();
        Map<RegionFlag, Map<FlagTarget, Boolean>> copy = new EnumMap<>(RegionFlag.class);
        for (Map.Entry<RegionFlag, Map<FlagTarget, Boolean>> e : source.entrySet()) {
            Map<FlagTarget, Boolean> inner = e.getValue();
            if (inner == null || inner.isEmpty()) continue;
            copy.put(e.getKey(), Map.copyOf(inner));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<RegionFlag, Set<Material>> deepCopyMaterialFlags(
            Map<RegionFlag, Set<Material>> source) {
        if (source.isEmpty()) return Map.of();
        Map<RegionFlag, Set<Material>> copy = new EnumMap<>(RegionFlag.class);
        for (Map.Entry<RegionFlag, Set<Material>> e : source.entrySet()) {
            if (!e.getKey().isMaterialFlag()) continue;
            Set<Material> inner = e.getValue();
            if (inner == null || inner.isEmpty()) continue;
            copy.put(e.getKey(), Set.copyOf(EnumSet.copyOf(inner)));
        }
        return Collections.unmodifiableMap(copy);
    }

    // Convenience constructor without material flags or parent — for pre-fyu2
    // call sites that only care about boolean rules.
    public Region(String id, UUID owner, List<UUID> members,
                  Map<RegionFlag, Map<FlagTarget, Boolean>> flagRules) {
        this(id, owner, members, flagRules, Map.of(), null);
    }

    // Convenience constructor without material flags but WITH parent — for
    // pre-fyu2 call sites that already set up hierarchy.
    public Region(String id, UUID owner, List<UUID> members,
                  Map<RegionFlag, Map<FlagTarget, Boolean>> flagRules,
                  String parentId) {
        this(id, owner, members, flagRules, Map.of(), parentId);
    }

    // Legacy constructor preserved so call sites (and tests) that still pass an
    // EnumSet of "permitted-for-non-members" flags continue to compile. Each
    // listed flag is translated to a DEFAULT-target rule with value=true,
    // matching the pre-refactor "flag in set = non-members may act" semantic.
    public Region(String id, UUID owner, List<UUID> members, EnumSet<RegionFlag> legacyFlags) {
        this(id, owner, members, legacyToTargeted(legacyFlags), Map.of(), null);
    }

    private static Map<RegionFlag, Map<FlagTarget, Boolean>> legacyToTargeted(EnumSet<RegionFlag> legacyFlags) {
        if (legacyFlags.isEmpty()) return Map.of();
        Map<RegionFlag, Map<FlagTarget, Boolean>> out = new EnumMap<>(RegionFlag.class);
        for (RegionFlag f : legacyFlags) {
            out.put(f, Map.of(FlagTarget.DEFAULT, true));
        }
        return out;
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public boolean isMember(UUID uuid) {
        return owner.equals(uuid) || members.contains(uuid);
    }

    public boolean hasParent() {
        return parentId != null && !parentId.isEmpty();
    }

    // Legacy convenience: returns true iff this flag carries an explicit
    // DEFAULT-target rule with value=true. Used by the old tests and a handful
    // of internal call sites that only ever cared about the "catch-all" verdict.
    public boolean hasFlag(RegionFlag flag) {
        Map<FlagTarget, Boolean> rules = flagRules.get(flag);
        return rules != null && Boolean.TRUE.equals(rules.get(FlagTarget.DEFAULT));
    }

    // Legacy projection of flagRules back to an EnumSet of "DEFAULT=true"
    // flags. Kept so any code (or test) that still reads region.flags() as
    // an EnumSet for compatibility purposes keeps working.
    public EnumSet<RegionFlag> flags() {
        EnumSet<RegionFlag> out = EnumSet.noneOf(RegionFlag.class);
        for (Map.Entry<RegionFlag, Map<FlagTarget, Boolean>> e : flagRules.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue().get(FlagTarget.DEFAULT))) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    // Read-only view of the rule table for one boolean flag. Returns Map.of()
    // when no rules exist; the returned map is always unmodifiable.
    public Map<FlagTarget, Boolean> rulesFor(RegionFlag flag) {
        return flagRules.getOrDefault(flag, Map.of());
    }

    // Read-only view of the material set for one list-based flag.
    public Set<Material> materialsFor(RegionFlag flag) {
        return materialFlags.getOrDefault(flag, Set.of());
    }

    // Resolve the effective rule for an actor at this region for one BOOLEAN
    // flag.
    //
    // Priority order:
    //   1. GROUP rule (any group the actor belongs to). When more than one
    //      group rule matches, ALLOW wins.
    //   2. OWNER (only if actor is the region owner).
    //   3. MEMBER (only if actor is a region member; owner counts as member).
    //   4. DEFAULT (catch-all).
    //
    // Returns Optional.empty() when no rule applies; the caller then either
    // walks up the parent chain (sub-region case) or falls back to the legacy
    // "members allowed, non-members blocked" default.
    public Optional<Boolean> resolveFlag(
            RegionFlag flag, boolean actorIsOwner, boolean actorIsMember, Set<String> actorGroups) {
        Map<FlagTarget, Boolean> rules = flagRules.get(flag);
        if (rules == null || rules.isEmpty()) return Optional.empty();

        if (actorGroups != null && !actorGroups.isEmpty()) {
            Boolean groupVerdict = null;
            for (Map.Entry<FlagTarget, Boolean> e : rules.entrySet()) {
                FlagTarget t = e.getKey();
                if (t.type() != FlagTarget.Type.GROUP) continue;
                if (!actorGroups.contains(t.groupName())) continue;
                if (Boolean.TRUE.equals(e.getValue())) {
                    return Optional.of(true);
                }
                groupVerdict = false;
            }
            if (groupVerdict != null) return Optional.of(groupVerdict);
        }

        if (actorIsOwner) {
            Boolean v = rules.get(FlagTarget.OWNER);
            if (v != null) return Optional.of(v);
        }
        if (actorIsMember) {
            Boolean v = rules.get(FlagTarget.MEMBER);
            if (v != null) return Optional.of(v);
        }
        Boolean def = rules.get(FlagTarget.DEFAULT);
        if (def != null) return Optional.of(def);
        return Optional.empty();
    }

    // Resolve a material-list verdict for one block action at this region.
    // `denyFlag` and `allowFlag` are the corresponding ALLOW_/DENY_ enum
    // values. Returns Optional.empty() when neither list contains the
    // material, telling the caller to fall back to the boolean rule or the
    // parent chain.
    public Optional<Boolean> resolveMaterial(
            RegionFlag denyFlag, RegionFlag allowFlag, Material material) {
        Set<Material> deny = materialFlags.get(denyFlag);
        if (deny != null && deny.contains(material)) return Optional.of(false);
        Set<Material> allow = materialFlags.get(allowFlag);
        if (allow != null && allow.contains(material)) return Optional.of(true);
        return Optional.empty();
    }

    // Return a new Region with a single (flag, target) rule overwritten or
    // removed on the boolean flag table. Used by ProtectionManager mutators;
    // null `value` removes the rule entirely (and drops the flag from the
    // outer map if it becomes empty).
    public Region withFlagRule(RegionFlag flag, FlagTarget target, Boolean value) {
        Map<RegionFlag, Map<FlagTarget, Boolean>> newRules = new EnumMap<>(RegionFlag.class);
        newRules.putAll(flagRules);

        Map<FlagTarget, Boolean> existing = flagRules.get(flag);
        Map<FlagTarget, Boolean> updated = (existing == null)
                ? new HashMap<>()
                : new HashMap<>(existing);

        if (value == null) {
            updated.remove(target);
        } else {
            updated.put(target, value);
        }

        if (updated.isEmpty()) {
            newRules.remove(flag);
        } else {
            newRules.put(flag, updated);
        }
        return new Region(id, owner, members, newRules, materialFlags, parentId);
    }

    // Return a new Region with the material set for `flag` replaced wholesale.
    // Passing an empty set clears the entry.
    public Region withMaterials(RegionFlag flag, Set<Material> materials) {
        if (!flag.isMaterialFlag()) {
            throw new IllegalArgumentException("Not a material-list flag: " + flag);
        }
        Map<RegionFlag, Set<Material>> newMaterials = new EnumMap<>(RegionFlag.class);
        newMaterials.putAll(materialFlags);
        if (materials == null || materials.isEmpty()) {
            newMaterials.remove(flag);
        } else {
            newMaterials.put(flag, Set.copyOf(EnumSet.copyOf(materials)));
        }
        return new Region(id, owner, members, flagRules, newMaterials, parentId);
    }

    // Returns a copy of this region with the parent pointer rewritten. Passing
    // null clears the parent (promotes the region back to top-level).
    public Region withParent(String newParentId) {
        String normalized = (newParentId == null || newParentId.isBlank()) ? null : newParentId;
        return new Region(id, owner, members, flagRules, materialFlags, normalized);
    }
}
