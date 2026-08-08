package me.beeliebub.tweaks.protection.region;

import org.bukkit.Material;

import java.util.ArrayList;
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
// Membership roles:
//   * owner       — single UUID, set at claim time.
//   * managers    — UUIDs with the same access as members PLUS the ability to
//                   edit flags and add/remove members. Cannot unclaim, edit
//                   the manager set, or change ownership. Resolves between
//                   OWNER and MEMBER in flag-target priority order.
//   * members     — UUIDs allowed to act inside the region (default permit).
//   * memberGroups / managerGroups — permission GROUP names (lowercase, stored
//                   without the `group:` prefix) that grant member/manager
//                   access to every player belonging to that group, without
//                   their UUID being listed. These are a SEPARATE concept from
//                   FlagTarget GROUP rules (flags.<FLAG>.group:<name>); they
//                   control membership/role, not per-flag verdicts.
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
//
// `bounds` is the chunk-AABB the region was claimed with. Nullable because
// regions persisted before the bounds field was added load without it; new
// claims always set it. Used by /region select to restore the player's
// pos1/pos2 to the originally-claimed box without scanning every chunk PDC.
//
// `worldName` records the Bukkit world the region was claimed in. Pairs with
// `bounds` for overlap-prevention checks (two regions occupying the same
// chunk coords in different worlds do NOT conflict). Nullable for the same
// "loaded before the field existed" reason as bounds.
public record Region(
        String id,
        UUID owner,
        List<UUID> members,
        Map<RegionFlag, Map<FlagTarget, Boolean>> flagRules,
        Map<RegionFlag, Set<Material>> materialFlags,
        String parentId,
        RegionBounds bounds,
        String worldName,
        List<UUID> managers,
        Map<RegionFlag, Set<org.bukkit.entity.EntityType>> entityFlags,
        int cost,
        List<String> memberGroups,
        List<String> managerGroups
) {

    // Inclusive chunk-coordinate AABB. Stored as four ints rather than two
    // packed chunk keys so the YAML serialization stays readable (min_chunk_x:
    // -5 reads cleaner than a 64-bit key) and so a future "move/resize" path
    // can tweak one edge without re-packing.
    public record RegionBounds(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        public RegionBounds {
            if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) {
                throw new IllegalArgumentException(
                        "Bounds min must be <= max: x[" + minChunkX + ".." + maxChunkX
                        + "] z[" + minChunkZ + ".." + maxChunkZ + "]");
            }
        }

        public boolean contains(int chunkX, int chunkZ) {
            return chunkX >= minChunkX && chunkX <= maxChunkX
                    && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
        }
    }

    public Region {
        members = List.copyOf(members);
        flagRules = deepCopyFlagRules(flagRules);
        materialFlags = deepCopyMaterialFlags(materialFlags);
        managers = managers == null ? List.of() : List.copyOf(managers);
        entityFlags = deepCopyEntityFlags(entityFlags);
        memberGroups = normalizeGroups(memberGroups);
        managerGroups = normalizeGroups(managerGroups);
    }

    // Lowercase + dedupe-preserving copy of a group-name list. Null and empty
    // both collapse to an immutable empty list so callers never see null.
    private static List<String> normalizeGroups(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(raw.size());
        for (String g : raw) {
            if (g == null) continue;
            String norm = g.toLowerCase(java.util.Locale.ROOT);
            if (!norm.isEmpty() && !out.contains(norm)) out.add(norm);
        }
        return List.copyOf(out);
    }

    // Pre-bounds canonical constructor used by every call site written before
    // the field existed. Keeps tests and admin tooling that build Regions
    // by hand from having to thread `null` through their argument lists.
    public Region(String id, UUID owner, List<UUID> members,
                  Map<RegionFlag, Map<FlagTarget, Boolean>> flagRules,
                  Map<RegionFlag, Set<Material>> materialFlags,
                  String parentId) {
        this(id, owner, members, flagRules, materialFlags, parentId, null, null, List.of(), Map.of(), 0, List.of(), List.of());
    }

    // Bounds-aware constructor without world (legacy bounds-only call sites).
    public Region(String id, UUID owner, List<UUID> members,
                  Map<RegionFlag, Map<FlagTarget, Boolean>> flagRules,
                  Map<RegionFlag, Set<Material>> materialFlags,
                  String parentId,
                  RegionBounds bounds) {
        this(id, owner, members, flagRules, materialFlags, parentId, bounds, null, List.of(), Map.of(), 0, List.of(), List.of());
    }

    // World + bounds constructor without managers/entityFlags — legacy 8-arg
    // call sites (and existing tests) keep compiling with empty manager lists.
    public Region(String id, UUID owner, List<UUID> members,
                  Map<RegionFlag, Map<FlagTarget, Boolean>> flagRules,
                  Map<RegionFlag, Set<Material>> materialFlags,
                  String parentId,
                  RegionBounds bounds,
                  String worldName) {
        this(id, owner, members, flagRules, materialFlags, parentId, bounds, worldName, List.of(), Map.of(), 0, List.of(), List.of());
    }

    // Full constructor without cost — every pre-cost call site (loader, mutators,
    // tests) lands here and gets a default cost of 0. New claim sites that need
    // to record a cost use the canonical 11-arg constructor or withCost().
    public Region(String id, UUID owner, List<UUID> members,
                  Map<RegionFlag, Map<FlagTarget, Boolean>> flagRules,
                  Map<RegionFlag, Set<Material>> materialFlags,
                  String parentId,
                  RegionBounds bounds,
                  String worldName,
                  List<UUID> managers,
                  Map<RegionFlag, Set<org.bukkit.entity.EntityType>> entityFlags) {
        this(id, owner, members, flagRules, materialFlags, parentId, bounds, worldName, managers, entityFlags, 0, List.of(), List.of());
    }

    // Full constructor WITH cost but without member/manager groups — preserves
    // every pre-group call site (claim flow, mutators) that passed the 11-arg
    // canonical form. Groups default to empty; use withMemberGroups/with-
    // ManagerGroups (or the canonical 13-arg constructor) to populate them.
    public Region(String id, UUID owner, List<UUID> members,
                  Map<RegionFlag, Map<FlagTarget, Boolean>> flagRules,
                  Map<RegionFlag, Set<Material>> materialFlags,
                  String parentId,
                  RegionBounds bounds,
                  String worldName,
                  List<UUID> managers,
                  Map<RegionFlag, Set<org.bukkit.entity.EntityType>> entityFlags,
                  int cost) {
        this(id, owner, members, flagRules, materialFlags, parentId, bounds, worldName, managers, entityFlags, cost, List.of(), List.of());
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

    private static Map<RegionFlag, Set<org.bukkit.entity.EntityType>> deepCopyEntityFlags(
            Map<RegionFlag, Set<org.bukkit.entity.EntityType>> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<RegionFlag, Set<org.bukkit.entity.EntityType>> copy = new EnumMap<>(RegionFlag.class);
        for (Map.Entry<RegionFlag, Set<org.bukkit.entity.EntityType>> e : source.entrySet()) {
            if (!e.getKey().isEntityFlag()) continue;
            Set<org.bukkit.entity.EntityType> inner = e.getValue();
            if (inner == null || inner.isEmpty()) continue;
            copy.put(e.getKey(), Set.copyOf(EnumSet.copyOf(inner)));
        }
        return Collections.unmodifiableMap(copy);
    }

    // Convenience constructor without material flags or parent — for pre-fyu2
    // call sites that only care about boolean rules.
    public Region(String id, UUID owner, List<UUID> members,
                  Map<RegionFlag, Map<FlagTarget, Boolean>> flagRules) {
        this(id, owner, members, flagRules, Map.of(), null, null, null, List.of(), Map.of(), 0, List.of(), List.of());
    }

    // Convenience constructor without material flags but WITH parent — for
    // pre-fyu2 call sites that already set up hierarchy.
    public Region(String id, UUID owner, List<UUID> members,
                  Map<RegionFlag, Map<FlagTarget, Boolean>> flagRules,
                  String parentId) {
        this(id, owner, members, flagRules, Map.of(), parentId, null, null, List.of(), Map.of(), 0, List.of(), List.of());
    }

    // Legacy constructor preserved so call sites (and tests) that still pass an
    // EnumSet of "permitted-for-non-members" flags continue to compile. Each
    // listed flag is translated to a DEFAULT-target rule with value=true,
    // matching the pre-refactor "flag in set = non-members may act" semantic.
    public Region(String id, UUID owner, List<UUID> members, EnumSet<RegionFlag> legacyFlags) {
        this(id, owner, members, legacyToTargeted(legacyFlags), Map.of(), null, null, null, List.of(), Map.of(), 0, List.of(), List.of());
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

    public boolean isManager(UUID uuid) {
        return uuid != null && managers.contains(uuid);
    }

    public boolean isMember(UUID uuid) {
        if (uuid == null) return false;
        return owner.equals(uuid) || managers.contains(uuid) || members.contains(uuid);
    }

    // Group-aware manager check: true if the UUID is a listed manager OR the
    // actor belongs to any group in managerGroups. `groups` is the actor's
    // lowercased permission groups (see ProtectionManager#groupsOf); null is
    // treated as empty. memberGroups/managerGroups are stored lowercase.
    public boolean isManager(UUID uuid, Set<String> groups) {
        if (isManager(uuid)) return true;
        return intersects(groups, managerGroups);
    }

    // Group-aware member check: true if the UUID is owner/manager/member OR the
    // actor belongs to any group in memberGroups OR managerGroups (managers are
    // members). `groups` null is treated as empty.
    public boolean isMember(UUID uuid, Set<String> groups) {
        if (isMember(uuid)) return true;
        return intersects(groups, memberGroups) || intersects(groups, managerGroups);
    }

    // True if any element of the actor's lowercased `groups` appears in the
    // lowercased `regionGroups` list. Both sides are already normalized, so the
    // comparison is a plain contains.
    private static boolean intersects(Set<String> groups, List<String> regionGroups) {
        if (groups == null || groups.isEmpty() || regionGroups.isEmpty()) return false;
        for (String g : groups) {
            if (regionGroups.contains(g)) return true;
        }
        return false;
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

    // Read-only view of the entity-type set for one entity-list flag.
    public Set<org.bukkit.entity.EntityType> entitiesFor(RegionFlag flag) {
        return entityFlags.getOrDefault(flag, Set.of());
    }

    // Three-arg legacy overload for callers that haven't been updated to
    // pass actorIsManager. Routes to the 5-arg variant with actorIsManager=false.
    public Optional<Boolean> resolveFlag(
            RegionFlag flag, boolean actorIsOwner, boolean actorIsMember, Set<String> actorGroups) {
        return resolveFlag(flag, actorIsOwner, false, actorIsMember, actorGroups);
    }

    // Resolve the effective rule for an actor at this region for one BOOLEAN
    // flag.
    //
    // Priority order:
    //   1. GROUP rule (any group the actor belongs to). When more than one
    //      group rule matches, ALLOW wins.
    //   2. OWNER (only if actor is the region owner).
    //   3. MANAGER (only if actor is a region manager; owner counts as manager
    //      for the purposes of falling through to the MANAGER bucket since
    //      OWNER is checked first).
    //   4. MEMBER (only if actor is a region member; owner/manager count as
    //      members for fallback to MEMBER bucket).
    //   5. DEFAULT (catch-all).
    //
    // Returns Optional.empty() when no rule applies; the caller then either
    // walks up the parent chain (sub-region case) or falls back to the legacy
    // "members allowed, non-members blocked" default.
    public Optional<Boolean> resolveFlag(
            RegionFlag flag, boolean actorIsOwner, boolean actorIsManager, boolean actorIsMember,
            Set<String> actorGroups) {
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
        if (actorIsManager) {
            Boolean v = rules.get(FlagTarget.MANAGER);
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

    // Resolve an entity-list verdict for a CreatureSpawnEvent (or similar).
    // `denyFlag` should be the DENY_* variant; `allowFlag` the ALLOW_* variant.
    public Optional<Boolean> resolveEntity(
            RegionFlag denyFlag, RegionFlag allowFlag, org.bukkit.entity.EntityType type) {
        Set<org.bukkit.entity.EntityType> deny = entityFlags.get(denyFlag);
        if (deny != null && deny.contains(type)) return Optional.of(false);
        Set<org.bukkit.entity.EntityType> allow = entityFlags.get(allowFlag);
        if (allow != null && allow.contains(type)) return Optional.of(true);
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
        return new Region(id, owner, members, newRules, materialFlags, parentId, bounds, worldName, managers, entityFlags, cost, memberGroups, managerGroups);
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
        return new Region(id, owner, members, flagRules, newMaterials, parentId, bounds, worldName, managers, entityFlags, cost, memberGroups, managerGroups);
    }

    // Return a new Region with the entity-type set for `flag` replaced wholesale.
    public Region withEntities(RegionFlag flag, Set<org.bukkit.entity.EntityType> entities) {
        if (!flag.isEntityFlag()) {
            throw new IllegalArgumentException("Not an entity-list flag: " + flag);
        }
        Map<RegionFlag, Set<org.bukkit.entity.EntityType>> newEntities = new EnumMap<>(RegionFlag.class);
        newEntities.putAll(entityFlags);
        if (entities == null || entities.isEmpty()) {
            newEntities.remove(flag);
        } else {
            newEntities.put(flag, Set.copyOf(EnumSet.copyOf(entities)));
        }
        return new Region(id, owner, members, flagRules, materialFlags, parentId, bounds, worldName, managers, newEntities, cost, memberGroups, managerGroups);
    }

    // Returns a copy of this region with the parent pointer rewritten. Passing
    // null clears the parent (promotes the region back to top-level).
    public Region withParent(String newParentId) {
        String normalized = (newParentId == null || newParentId.isBlank()) ? null : newParentId;
        return new Region(id, owner, members, flagRules, materialFlags, normalized, bounds, worldName, managers, entityFlags, cost, memberGroups, managerGroups);
    }

    public Region withBounds(RegionBounds newBounds) {
        return new Region(id, owner, members, flagRules, materialFlags, parentId, newBounds, worldName, managers, entityFlags, cost, memberGroups, managerGroups);
    }

    public Region withWorld(String newWorldName) {
        return new Region(id, owner, members, flagRules, materialFlags, parentId, bounds, newWorldName, managers, entityFlags, cost, memberGroups, managerGroups);
    }

    public Region withMembers(List<UUID> newMembers) {
        return new Region(id, owner, members(newMembers), flagRules, materialFlags, parentId, bounds, worldName, managers, entityFlags, cost, memberGroups, managerGroups);
    }

    public Region withManagers(List<UUID> newManagers) {
        return new Region(id, owner, members, flagRules, materialFlags, parentId, bounds, worldName,
                newManagers == null ? List.of() : newManagers, entityFlags, cost, memberGroups, managerGroups);
    }

    // Replace the member-group list wholesale. Null collapses to empty; names
    // are lowercased/deduped by the compact constructor.
    public Region withMemberGroups(List<String> newMemberGroups) {
        return new Region(id, owner, members, flagRules, materialFlags, parentId, bounds, worldName,
                managers, entityFlags, cost, newMemberGroups == null ? List.of() : newMemberGroups, managerGroups);
    }

    // Replace the manager-group list wholesale.
    public Region withManagerGroups(List<String> newManagerGroups) {
        return new Region(id, owner, members, flagRules, materialFlags, parentId, bounds, worldName,
                managers, entityFlags, cost, memberGroups, newManagerGroups == null ? List.of() : newManagerGroups);
    }

    // Returns a copy of this region with the `cost` field replaced. Used by the
    // claim flow to stamp the calculated chunk-purchase price onto the new
    // Region so it round-trips through RegionWriter and is available for refund
    // at unclaim time. The full price paid (no depreciation) is refunded on
    // unclaim, so the field stays unchanged for the lifetime of the region.
    public Region withCost(int newCost) {
        return new Region(id, owner, members, flagRules, materialFlags, parentId, bounds, worldName, managers, entityFlags, newCost, memberGroups, managerGroups);
    }

    public Region addManager(UUID uuid) {
        if (uuid == null || managers.contains(uuid)) return this;
        List<UUID> updated = new ArrayList<>(managers);
        updated.add(uuid);
        return withManagers(updated);
    }

    public Region removeManager(UUID uuid) {
        if (uuid == null || !managers.contains(uuid)) return this;
        List<UUID> updated = new ArrayList<>(managers);
        updated.remove(uuid);
        return withManagers(updated);
    }

    // Group membership mutators. Names are normalized to lowercase before the
    // contains/equality check so callers may pass any casing. A no-op returns
    // `this` (no new allocation) so cache swaps stay cheap when nothing changes.
    public Region addMemberGroup(String group) {
        if (group == null) return this;
        String norm = group.toLowerCase(java.util.Locale.ROOT);
        if (norm.isEmpty() || memberGroups.contains(norm)) return this;
        List<String> updated = new ArrayList<>(memberGroups);
        updated.add(norm);
        return withMemberGroups(updated);
    }

    public Region removeMemberGroup(String group) {
        if (group == null) return this;
        String norm = group.toLowerCase(java.util.Locale.ROOT);
        if (!memberGroups.contains(norm)) return this;
        List<String> updated = new ArrayList<>(memberGroups);
        updated.remove(norm);
        return withMemberGroups(updated);
    }

    public Region addManagerGroup(String group) {
        if (group == null) return this;
        String norm = group.toLowerCase(java.util.Locale.ROOT);
        if (norm.isEmpty() || managerGroups.contains(norm)) return this;
        List<String> updated = new ArrayList<>(managerGroups);
        updated.add(norm);
        return withManagerGroups(updated);
    }

    public Region removeManagerGroup(String group) {
        if (group == null) return this;
        String norm = group.toLowerCase(java.util.Locale.ROOT);
        if (!managerGroups.contains(norm)) return this;
        List<String> updated = new ArrayList<>(managerGroups);
        updated.remove(norm);
        return withManagerGroups(updated);
    }

    private static List<UUID> members(List<UUID> raw) {
        return raw == null ? List.of() : raw;
    }
}
