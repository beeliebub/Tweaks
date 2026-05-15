package me.beeliebub.tweaks.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.UserPermissions;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// Central holder for the two in-memory caches that back the hybrid protection
// architecture. Keeping them on a single owner simplifies lifecycle (load on
// onEnable, periodic async snapshot, drain on onDisable) and avoids scattering
// ConcurrentHashMap instances across listeners.
//
// Both maps expose the concrete ConcurrentHashMap type because their
// thread-safety contract IS the API: the protection event-resolution path
// depends on lock-free reads, and the lazy-stamp ChunkLoadEvent listener
// depends on atomic CAS removal of processed entries.
public final class ProtectionManager {

    // RegionID -> source-of-truth Region. Read by every protection event;
    // written only on claim/unclaim or YAML reload.
    private final ConcurrentHashMap<String, Region> regions = new ConcurrentHashMap<>();

    // Chunk key (long-packed X/Z via Chunk.getChunkKey) -> set of RegionIDs
    // awaiting physical PDC stamping. Populated by the lazy-stamp engine for
    // claims that exceed the immediate-async threshold; drained synchronously
    // inside the ChunkLoadEvent listener. Inner Set is
    // ConcurrentHashMap.newKeySet() for thread-safe mutation and natural
    // dedup of duplicate stamp requests.
    private final ConcurrentHashMap<Long, Set<String>> pendingStamps = new ConcurrentHashMap<>();

    // RegionIDs that have been logically deleted (removed from the regions
    // cache + YAML) but whose PDC pointers may still exist on chunks that
    // haven't loaded since the deletion. Drained opportunistically by the
    // ChunkLoadEvent listener: any orphan id present in a chunk's PDC is
    // stripped when that chunk next loads. Amortizes physical cleanup of
    // huge sprawling unclaims over organic player exploration.
    private final Set<String> orphanedRegions = ConcurrentHashMap.newKeySet();

    private final Tweaks plugin;

    public ProtectionManager(Tweaks plugin) {
        this.plugin = plugin;
    }

    public Tweaks plugin() {
        return plugin;
    }

    public ConcurrentHashMap<String, Region> regions() {
        return regions;
    }

    public ConcurrentHashMap<Long, Set<String>> pendingStamps() {
        return pendingStamps;
    }

    public Set<String> orphanedRegions() {
        return orphanedRegions;
    }

    // ------------------------------------------------------------------
    // Tiered claim engine (Sprint 3)
    // ------------------------------------------------------------------

    // Claims spanning at most this many chunks are stamped immediately via
    // async chunk loads. Larger claims defer their PDC writes to natural
    // ChunkLoadEvents so a single big admin claim cannot starve the chunk
    // I/O queue and stall organic player chunk loads.
    public static final int ASYNC_STAMP_THRESHOLD = 5;

    // Register the region and stamp every chunk it covers. Returns a future
    // that completes once all immediate (async path) PDC writes are done;
    // the lazy path resolves immediately because actual stamping happens
    // organically on subsequent ChunkLoadEvents.
    //
    // Note: this only stamps chunks the loader actually visits. If a chunk
    // is currently loaded when a lazy claim is created, its PDC will not
    // gain the pointer until it next unloads + reloads. A follow-up bead
    // can stamp already-loaded chunks immediately on the lazy path.
    public CompletableFuture<Void> claim(Region region, World world, int x1, int z1, int x2, int z2) {
        regions.put(region.id(), region);
        long[] keys = GeometryUtil.chunkKeysInBox(x1, z1, x2, z2);
        if (keys.length <= ASYNC_STAMP_THRESHOLD) {
            return claimAsync(world, keys, region.id());
        }
        claimLazy(keys, region.id());
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> claimAsync(World world, long[] keys, String regionId) {
        List<CompletableFuture<?>> futures = new ArrayList<>(keys.length);
        for (long key : keys) {
            int cx = GeometryUtil.chunkX(key);
            int cz = GeometryUtil.chunkZ(key);
            CompletableFuture<Chunk> load = world.getChunkAtAsync(cx, cz, true);
            futures.add(load.thenAccept(chunk -> {
                // Edge Case 1: pin the chunk via a plugin ticket so it
                // cannot unload mid-callback, then release after the
                // PDC mutation has flagged it dirty for the next save.
                chunk.addPluginChunkTicket(plugin);
                try {
                    PDCUtil.append(chunk, regionId);
                } finally {
                    chunk.removePluginChunkTicket(plugin);
                }
            }));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private void claimLazy(long[] keys, String regionId) {
        for (long key : keys) {
            pendingStamps
                    .computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                    .add(regionId);
        }
    }

    // ------------------------------------------------------------------
    // Pointer lookup (Sprint 4.1)
    // ------------------------------------------------------------------

    // O(1) resolution of a location to its applicable Regions: read the
    // chunk's PDC pointer list, map each id through the regions cache,
    // and drop any orphans (id that no longer has a live Region — those
    // get physically purged on the next ChunkLoadEvent).
    public List<Region> regionsAt(Location loc) {
        Chunk chunk = loc.getChunk();
        List<String> ids = PDCUtil.read(chunk);
        if (ids.isEmpty()) return List.of();
        List<Region> resolved = new ArrayList<>(ids.size());
        for (String id : ids) {
            Region r = regions.get(id);
            if (r != null) resolved.add(r);
        }
        return resolved;
    }

    // Permission check for `actor` performing an action governed by `flag`
    // at `loc`. Returns true if every leaf region individually permits the
    // actor.
    //
    // Hierarchy handling: when overlapping regions form a parent/child chain
    // (sub-region claim inside a parent claim), we only consider the leaves
    // (regions that no other applicable region descends from). For each leaf
    // we walk the chain (leaf -> parent -> grandparent ...) and use the first
    // matching rule we encounter — sub-region flags transparently override
    // their parent's flags at the lookup level, no precedence math needed in
    // the listeners. Role checks (owner/member) are re-evaluated at each link
    // because membership can differ between parent and child.
    //
    // Unrelated overlapping leaves (admin safezone over a player base) still
    // combine via the WorldGuard-style "all must permit" semantic.
    //
    // Per-region verdict on the resolved Optional:
    //   true  -> region permits, continue checking the rest
    //   false -> region denies, short-circuit return false
    // When the entire chain returns empty, fall back to the legacy default
    // applied at the leaf: members may act, non-members may not.
    //
    // Pass actor=null for actor-less events (TNT, creeper, etc.) — then only
    // the DEFAULT target can permit the action.
    public boolean isAllowed(Location loc, UUID actor, RegionFlag flag) {
        List<Region> applicable = regionsAt(loc);
        if (applicable.isEmpty()) return true;

        Set<String> groups = (actor == null) ? Set.of() : groupsOf(actor);
        List<Region> leaves = leavesOf(applicable);

        for (Region leaf : leaves) {
            Optional<Boolean> resolved = resolveAcrossChain(leaf, flag, actor, groups);
            if (resolved.isPresent()) {
                if (!resolved.get()) return false;
                continue;
            }

            // No rule anywhere in this chain: members of the leaf may act,
            // non-members (and null-actor events) may not. Preserves
            // pre-refactor behavior at the most specific scope.
            if (actor != null && leaf.isMember(actor)) continue;
            return false;
        }
        return true;
    }

    // Filter `applicable` down to leaves: regions not referenced as parentId
    // by any other region in the same set. A region whose parent is NOT in
    // the set is still a leaf (the parent is just unrelated/absent at this
    // location).
    private List<Region> leavesOf(List<Region> applicable) {
        if (applicable.size() == 1) return applicable;
        Set<String> referenced = new HashSet<>();
        for (Region r : applicable) {
            if (r.hasParent()) referenced.add(r.parentId());
        }
        List<Region> leaves = new ArrayList<>(applicable.size());
        for (Region r : applicable) {
            if (!referenced.contains(r.id())) leaves.add(r);
        }
        // Defensive: if every region in the set is referenced as someone's
        // parent (impossible with a well-formed DAG but guard against a cycle
        // that slipped past setParent's validation), fall back to the input
        // so we never silently lose protection.
        return leaves.isEmpty() ? applicable : leaves;
    }

    // Walk leaf -> parent -> grandparent until a Region#resolveFlag hit, or
    // the chain runs out / cycles. Re-evaluates role/membership at each link
    // because a player can be a member of the child but not the parent
    // (or vice versa).
    private Optional<Boolean> resolveAcrossChain(
            Region start, RegionFlag flag, UUID actor, Set<String> groups) {
        Set<String> visited = new LinkedHashSet<>();
        Region cursor = start;
        while (cursor != null && visited.add(cursor.id())) {
            boolean isOwner = actor != null && cursor.isOwner(actor);
            boolean isMember = actor != null && cursor.isMember(actor);
            Optional<Boolean> resolved = cursor.resolveFlag(flag, isOwner, isMember, groups);
            if (resolved.isPresent()) return resolved;
            cursor = cursor.hasParent() ? regions.get(cursor.parentId()) : null;
        }
        return Optional.empty();
    }

    // Material-aware variant of isAllowed for block-break and block-place
    // events. Per leaf chain, at each link:
    //   1. DENY_<action> contains material -> deny (highest priority)
    //   2. ALLOW_<action> contains material -> allow
    //   3. boolean <action> rule -> use it
    //   4. otherwise climb to parent
    // After the chain exhausts, fall back to the legacy default applied at
    // the leaf (members allowed, non-members blocked). All leaves must permit.
    public boolean isBlockActionAllowed(
            Location loc, UUID actor, Material material, RegionFlag baseFlag) {
        if (baseFlag != RegionFlag.BLOCK_BREAK && baseFlag != RegionFlag.BLOCK_PLACE) {
            throw new IllegalArgumentException(
                    "isBlockActionAllowed requires BLOCK_BREAK or BLOCK_PLACE, got " + baseFlag);
        }
        List<Region> applicable = regionsAt(loc);
        if (applicable.isEmpty()) return true;

        RegionFlag denyFlag = (baseFlag == RegionFlag.BLOCK_BREAK)
                ? RegionFlag.DENY_BLOCK_BREAK : RegionFlag.DENY_BLOCK_PLACE;
        RegionFlag allowFlag = (baseFlag == RegionFlag.BLOCK_BREAK)
                ? RegionFlag.ALLOW_BLOCK_BREAK : RegionFlag.ALLOW_BLOCK_PLACE;

        Set<String> groups = (actor == null) ? Set.of() : groupsOf(actor);
        List<Region> leaves = leavesOf(applicable);

        for (Region leaf : leaves) {
            Optional<Boolean> resolved = resolveBlockActionChain(
                    leaf, baseFlag, denyFlag, allowFlag, material, actor, groups);
            if (resolved.isPresent()) {
                if (!resolved.get()) return false;
                continue;
            }
            if (actor != null && leaf.isMember(actor)) continue;
            return false;
        }
        return true;
    }

    private Optional<Boolean> resolveBlockActionChain(
            Region start, RegionFlag baseFlag, RegionFlag denyFlag, RegionFlag allowFlag,
            Material material, UUID actor, Set<String> groups) {
        Set<String> visited = new LinkedHashSet<>();
        Region cursor = start;
        while (cursor != null && visited.add(cursor.id())) {
            Optional<Boolean> matVerdict = cursor.resolveMaterial(denyFlag, allowFlag, material);
            if (matVerdict.isPresent()) return matVerdict;

            boolean isOwner = actor != null && cursor.isOwner(actor);
            boolean isMember = actor != null && cursor.isMember(actor);
            Optional<Boolean> boolVerdict = cursor.resolveFlag(baseFlag, isOwner, isMember, groups);
            if (boolVerdict.isPresent()) return boolVerdict;

            cursor = cursor.hasParent() ? regions.get(cursor.parentId()) : null;
        }
        return Optional.empty();
    }

    // -- Material flag mutators ---------------------------------------------

    public boolean setMaterials(String id, RegionFlag flag, Set<Material> materials) {
        Region r = regions.get(id);
        if (r == null) return false;
        if (!flag.isMaterialFlag()) {
            throw new IllegalArgumentException("Not a material-list flag: " + flag);
        }
        Set<Material> current = r.materialsFor(flag);
        Set<Material> normalized = (materials == null || materials.isEmpty())
                ? Set.of()
                : EnumSet.copyOf(materials);
        if (current.equals(normalized)) return false;
        regions.put(id, r.withMaterials(flag, normalized));
        return true;
    }

    public boolean addMaterials(String id, RegionFlag flag, Set<Material> materials) {
        Region r = regions.get(id);
        if (r == null || materials == null || materials.isEmpty()) return false;
        if (!flag.isMaterialFlag()) {
            throw new IllegalArgumentException("Not a material-list flag: " + flag);
        }
        EnumSet<Material> merged = EnumSet.noneOf(Material.class);
        merged.addAll(r.materialsFor(flag));
        boolean changed = merged.addAll(materials);
        if (!changed) return false;
        regions.put(id, r.withMaterials(flag, merged));
        return true;
    }

    public boolean removeMaterials(String id, RegionFlag flag, Set<Material> materials) {
        Region r = regions.get(id);
        if (r == null || materials == null || materials.isEmpty()) return false;
        if (!flag.isMaterialFlag()) {
            throw new IllegalArgumentException("Not a material-list flag: " + flag);
        }
        Set<Material> current = r.materialsFor(flag);
        if (current.isEmpty()) return false;
        EnumSet<Material> reduced = EnumSet.noneOf(Material.class);
        reduced.addAll(current);
        boolean changed = reduced.removeAll(materials);
        if (!changed) return false;
        regions.put(id, r.withMaterials(flag, reduced));
        return true;
    }

    public boolean clearMaterials(String id, RegionFlag flag) {
        Region r = regions.get(id);
        if (r == null) return false;
        if (!flag.isMaterialFlag()) {
            throw new IllegalArgumentException("Not a material-list flag: " + flag);
        }
        if (r.materialsFor(flag).isEmpty()) return false;
        regions.put(id, r.withMaterials(flag, Set.of()));
        return true;
    }

    // Stricter variant of isAllowed used by listeners that enforce vanilla
    // gamerules (mob griefing, fire spread, ...). The contract is reversed:
    // an action is permitted ONLY when an overlapping region explicitly opts
    // in via flag=true. Wilderness — and regions whose entire hierarchy
    // chain stays silent on the flag — return false, so the gamerule's
    // "don't grief" default holds everywhere except where an admin has
    // explicitly written the override.
    public boolean isExplicitlyAllowed(Location loc, UUID actor, RegionFlag flag) {
        List<Region> applicable = regionsAt(loc);
        if (applicable.isEmpty()) return false;

        Set<String> groups = (actor == null) ? Set.of() : groupsOf(actor);
        List<Region> leaves = leavesOf(applicable);

        for (Region leaf : leaves) {
            Optional<Boolean> resolved = resolveAcrossChain(leaf, flag, actor, groups);
            if (resolved.isEmpty() || !resolved.get()) return false;
        }
        return true;
    }

    // Look up the actor's permission groups. Returns an empty set when no
    // permission manager is wired in (test contexts) or the user has no
    // record. Group names are normalized to lowercase to match FlagTarget#group.
    private Set<String> groupsOf(UUID actor) {
        if (plugin == null) return Set.of();
        PermissionManager pm = plugin.getPermissionManager();
        if (pm == null) return Set.of();
        Map<UUID, UserPermissions> users = pm.getUsers();
        if (users == null) return Set.of();
        UserPermissions up = users.get(actor);
        if (up == null) {
            // Mirror PermissionManager's implicit-default behavior so a flag
            // rule targeting "group:default" still hits unknown players.
            return Set.of("default");
        }
        Set<String> declared = up.getGroups();
        if (declared == null || declared.isEmpty()) return Set.of("default");
        Set<String> normalized = new HashSet<>(declared.size());
        for (String g : declared) {
            normalized.add(g.toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    // ------------------------------------------------------------------
    // Mutators (Sprint 5 execution binding)
    //
    // Region is immutable, so every change to ownership/members/flags
    // builds a fresh Region and atomically replaces the cache entry.
    // Unclaim is logical-only: the id is moved to orphanedRegions so the
    // ChunkLoadEvent listener can strip the dead pointer when the chunk
    // next loads (Edge Case 4 — avoids the 1000-chunk getChunkAtAsync
    // flood that synchronous physical deletion would cause).
    // ------------------------------------------------------------------

    public boolean unclaim(String id) {
        Region removed = regions.remove(id);
        if (removed == null) return false;
        orphanedRegions.add(id);
        return true;
    }

    public boolean addMember(String id, UUID member) {
        Region r = regions.get(id);
        if (r == null || r.members().contains(member)) return false;
        List<UUID> newMembers = new ArrayList<>(r.members());
        newMembers.add(member);
        regions.put(id, new Region(r.id(), r.owner(), newMembers,
                r.flagRules(), r.materialFlags(), r.parentId()));
        return true;
    }

    public boolean removeMember(String id, UUID member) {
        Region r = regions.get(id);
        if (r == null || !r.members().contains(member)) return false;
        List<UUID> newMembers = new ArrayList<>(r.members());
        newMembers.remove(member);
        regions.put(id, new Region(r.id(), r.owner(), newMembers,
                r.flagRules(), r.materialFlags(), r.parentId()));
        return true;
    }

    // -- Hierarchy ----------------------------------------------------------

    public enum SetParentResult {
        OK,
        UNKNOWN_CHILD,
        UNKNOWN_PARENT,
        SELF_REFERENCE,
        CYCLE,
        NO_CHANGE
    }

    // Reparent (or unparent — pass null) a region. Validates that:
    //   - the child exists,
    //   - the parent exists when non-null,
    //   - the child is not setting itself as parent,
    //   - the new parent's ancestor chain does not include the child (cycle).
    // Returns a discriminated result so the command layer can produce a
    // specific error message without re-walking the cache.
    public SetParentResult setParent(String childId, String newParentId) {
        Region child = regions.get(childId);
        if (child == null) return SetParentResult.UNKNOWN_CHILD;

        String normalized = (newParentId == null || newParentId.isBlank()) ? null : newParentId;
        if (normalized != null) {
            if (normalized.equals(childId)) return SetParentResult.SELF_REFERENCE;
            if (!regions.containsKey(normalized)) return SetParentResult.UNKNOWN_PARENT;
            if (wouldCreateCycle(childId, normalized)) return SetParentResult.CYCLE;
        }

        String current = child.parentId();
        if (java.util.Objects.equals(current, normalized)) return SetParentResult.NO_CHANGE;

        regions.put(childId, child.withParent(normalized));
        return SetParentResult.OK;
    }

    private boolean wouldCreateCycle(String childId, String candidateParentId) {
        Set<String> visited = new HashSet<>();
        String cursor = candidateParentId;
        while (cursor != null && visited.add(cursor)) {
            if (cursor.equals(childId)) return true;
            Region step = regions.get(cursor);
            if (step == null) return false;
            cursor = step.parentId();
        }
        return false;
    }

    // Targeted setter: write (flag, target) -> value. No-op (returns false) if
    // the rule already carries that value. Atomically replaces the cached
    // Region so a concurrent reader sees either the old or new rule set,
    // never a partially-mutated state.
    //
    // Material-list flags (the ALLOW_/DENY_ variants) are not booleans; use
    // setMaterials / addMaterials / removeMaterials instead.
    public boolean setFlag(String id, RegionFlag flag, FlagTarget target, boolean value) {
        if (flag.isMaterialFlag()) {
            throw new IllegalArgumentException(
                    flag + " is a material-list flag; use setMaterials / addMaterials.");
        }
        Region r = regions.get(id);
        if (r == null) return false;
        Boolean current = r.rulesFor(flag).get(target);
        if (current != null && current == value) return false;
        regions.put(id, r.withFlagRule(flag, target, value));
        return true;
    }

    // Targeted remover: drop (flag, target) entirely. Returns false if no
    // such rule existed.
    public boolean removeFlag(String id, RegionFlag flag, FlagTarget target) {
        if (flag.isMaterialFlag()) {
            throw new IllegalArgumentException(
                    flag + " is a material-list flag; use clearMaterials / removeMaterials.");
        }
        Region r = regions.get(id);
        if (r == null) return false;
        if (!r.rulesFor(flag).containsKey(target)) return false;
        regions.put(id, r.withFlagRule(flag, target, null));
        return true;
    }

    // Legacy overload preserved for backward compat: setFlag(id, flag, true)
    // adds a DEFAULT=true rule; setFlag(id, flag, false) removes any existing
    // DEFAULT rule. This mirrors the pre-refactor "add to / remove from
    // EnumSet" behavior so existing tests and call sites keep working.
    public boolean setFlag(String id, RegionFlag flag, boolean value) {
        if (value) {
            return setFlag(id, flag, FlagTarget.DEFAULT, true);
        }
        return removeFlag(id, flag, FlagTarget.DEFAULT);
    }
}
