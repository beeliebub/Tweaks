package me.beeliebub.tweaks.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.UserPermissions;
import me.beeliebub.tweaks.utils.GeometryUtil;
import me.beeliebub.tweaks.utils.PDCUtil;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

    // Reserved id for the implicit "wilderness" region that owns every chunk
    // not stamped with a player region. Lives in the regions cache at this
    // plain key so byName / byNameAnyWorld surface it for the admin command
    // layer; it is excluded from claim, unclaim, setParent, and the legacy-
    // region migration path so users cannot interact with it as if it were
    // a normal region.
    public static final String GLOBAL_REGION_ID = "__global__";

    // Sentinel owner UUID for the global region. The nil UUID (RFC 4122) is
    // guaranteed never to collide with a real Mojang account, so the existing
    // owner-equality checks in Region#isOwner / #isMember return false for
    // every actual player without any null-handling gymnastics. Editing the
    // global region is therefore restricted to senders with the
    // tweaks.protection.admin permission, which is exactly the policy this
    // feature requires.
    public static final UUID SERVER_OWNER = new UUID(0L, 0L);

    // Owns the exact concurrent caches exposed by this stable public facade.
    // No copying wrapper is involved: event and chunk-load paths retain their
    // original lock-free map instances and atomic mutation semantics.
    private final RegionRegistry registry = new RegionRegistry();

    // Local aliases preserve the manager's existing hot-path implementation
    // while RegionRegistry remains the sole creator and owner of these maps.
    private final ConcurrentHashMap<String, Region> regions = registry.regions();
    private final ConcurrentHashMap<Long, Set<String>> pendingStamps = registry.pendingStamps();
    private final Set<String> orphanedRegions = registry.orphanedRegions();

    private final Tweaks plugin;
    private RegionWriter writer;

    // Key under which a chunk's PDC stores the list of RegionID strings that
    // mathematically cover that chunk. Built directly here, the same way every
    // other manager class in this plugin builds its own NamespacedKey fields
    // (e.g. PlayerAdminManager, BlackjackTableStore) — this used to live on a
    // separate ProtectionKeys holder with a mutable static init(plugin), the
    // only such static in the codebase.
    //
    // Deliberately built from the (String namespace, String key) constructor rather
    // than (Plugin, String): every other manager's key ties itself to the live
    // plugin's registered name, but this class is unit-tested with dozens of bare
    // `mock(Tweaks.class)` instances (unlike MockBukkit.load(Tweaks.class), a bare
    // mock's getName() returns null, which the Plugin-based constructor requires to
    // be a valid non-null name). The namespace "tweaks" mirrors plugin.yml's `name`
    // lowercased — the exact string Bukkit's Plugin-based constructor would already
    // produce — so on-disk chunk PDC data is unaffected. If the plugin is ever
    // renamed, update this literal alongside plugin.yml.
    private final NamespacedKey regionPointersKey;

    public ProtectionManager(Tweaks plugin) {
        this.plugin = plugin;
        this.regionPointersKey = new NamespacedKey("tweaks", "region_pointers");
        // Global regions are now per-world (keyed by "<worldName>:__global__") and
        // lazily seeded on first access via globalRegion(World). Setting a flag on
        // the wilderness in one world must not bleed into another.
    }

    // Exposed so ProtectionListeners (the one other production consumer of PDCUtil)
    // can stamp/read the same chunk PDC key without this class needing to depend on
    // the protection package root, or PDCUtil needing to depend on protection.region.
    public NamespacedKey regionPointersKey() {
        return regionPointersKey;
    }

    public static boolean isGlobal(Region region) {
        return region != null && GLOBAL_REGION_ID.equals(region.id());
    }

    // Returns the wilderness/global region for `world`, lazy-initialising it on
    // first access. Null bounds keep it invisible to overlap iteration; the
    // worldName field scopes it to that world for tab completion and lookup so
    // each world has its own independent fallback rule set.
    public Region globalRegion(World world) {
        return registry.globalRegion(world);
    }

    // List wrapper that the permission methods use whenever regionsAt(loc) is
    // empty — chunks not covered by any player region fall back to the global
    // region so admins can govern wilderness with the same flag machinery
    // that runs inside claimed regions. The fallback is per-world: a chunk in
    // world A consults world A's global region only.
    private List<Region> globalAsFallback(World world) {
        return registry.globalAsFallback(world);
    }

    // Optional persister for region YAML files. Wired by Tweaks#onEnable
    // after the loader populates the cache. May be null in tests or before
    // wiring completes; mutators no-op the disk write when null.
    public void setWriter(RegionWriter writer) {
        this.writer = writer;
    }

    public Tweaks plugin() {
        return plugin;
    }

    public ConcurrentHashMap<String, Region> regions() {
        return registry.regions();
    }

    // Sum the chunk count of every region in the in-memory cache owned by the
    // given player UUID. Used by the claim-limit enforcement in the command
    // layer to gate further claims when a player has reached their cap.
    //
    // Reads exclusively from the `regions` cache (no PDC / disk I/O) so the
    // call is safe on the main thread and consistent with the rest of
    // ProtectionManager's lock-free read paths. Regions without bounds
    // (legacy, pre-bounds claims and the per-world wilderness/global region)
    // contribute 0 chunks — they cannot have been "claimed" through the
    // chunk-counting claim engine, so they correctly do not count against
    // a player's allowance. The nil-UUID sentinel that owns globals never
    // matches a real player owner, so globals naturally drop out via the
    // isOwner check as well.
    public int getTotalChunksOwned(UUID owner) {
        if (owner == null) return 0;
        int total = 0;
        for (Region r : registry.regions().values()) {
            if (!r.isOwner(owner)) continue;
            Region.RegionBounds b = r.bounds();
            if (b == null) continue;
            total += (b.maxChunkX() - b.minChunkX() + 1)
                    * (b.maxChunkZ() - b.minChunkZ() + 1);
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Per-world cache addressing
    //
    // Region uniqueness is enforced per world rather than globally. The cache
    // key composition uses "<worldName>:<regionId>" when the region carries a
    // world tag (new claims always do), or the bare regionId for legacy regions
    // loaded from pre-per-world YAML (which stay at plain keys until they get
    // touched / migrated).
    //
    // The two forms coexist in the same ConcurrentHashMap so tests that put
    // null-world regions at plain keys continue to work alongside new claims
    // stored at composite keys.
    // ------------------------------------------------------------------

    public static String keyOf(String worldName, String id) {
        return RegionRegistry.keyOf(worldName, id);
    }

    public static String keyOf(Region region) {
        return RegionRegistry.keyOf(region);
    }

    // World-aware lookup: returns the region named `id` in the specified world,
    // falling back to a plain-keyed legacy region if no world-tagged match is
    // found. Pass world=null to skip the composite-key probe (legacy-only).
    public Region byName(World world, String id) {
        return registry.byName(world, id);
    }

    // Search every world for a region with this id and return the first match
    // (legacy plain-key entries first, then world-tagged). Used by commands
    // run from console where there is no implicit world context.
    //
    // Returns null for the global region id: per-world globals are intentionally
    // not addressable without world context — use globalRegion(World) instead.
    // Letting this method pick an arbitrary world's global would silently mutate
    // the wrong wilderness flag set on a multi-world server.
    public Region byNameAnyWorld(String id) {
        return registry.byNameAnyWorld(id);
    }

    // Reassigns null-worldName regions to `defaultWorldName` so they participate
    // in the per-world uniqueness model. Idempotent; safe to call after a
    // successful load to upgrade old YAML on first launch under the new layout.
    public int migrateLegacyRegions(String defaultWorldName) {
        if (defaultWorldName == null || defaultWorldName.isEmpty()) return 0;
        int migrated = registry.migrateLegacyRegions(defaultWorldName,
                writer == null ? null : writer::queue);
        for (var e : new ArrayList<>(regions.entrySet())) {
            String key = e.getKey();
            Region r = e.getValue();
            // Global is intentionally world-less — it applies everywhere.
            if (GLOBAL_REGION_ID.equals(r.id())) continue;
            if (r.worldName() != null) continue;
            Region updated = r.withWorld(defaultWorldName);
            regions.remove(key);
            regions.put(keyOf(updated), updated);
            if (writer != null) writer.queue(updated);
            migrated++;
        }
        return migrated;
    }

    public ConcurrentHashMap<Long, Set<String>> pendingStamps() {
        return registry.pendingStamps();
    }

    public Set<String> orphanedRegions() {
        return registry.orphanedRegions();
    }

    // ------------------------------------------------------------------
    // Tiered claim engine
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
    // Discriminated result for claim() so callers can react to specific
    // failure modes (overlap with foreign region, duplicate id) without
    // string-matching error messages.
    public enum ClaimResult { OK, ID_TAKEN, OVERLAPS_FOREIGN_REGION }

    // Attempt to claim. Returns OK and starts stamping; otherwise returns the
    // reason without mutating state. `pendingChunks` is filled with the
    // CompletableFuture that signals "all immediate PDC writes done" so the
    // caller can chain a message or persistence step; null on non-OK results.
    public ClaimResult tryClaim(Region region, World world, int x1, int z1, int x2, int z2,
                                java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>> pendingChunks) {
        // The wilderness region id is reserved — never let a user claim it
        // out from under the fallback machinery.
        if (GLOBAL_REGION_ID.equals(region.id())) return ClaimResult.ID_TAKEN;
        String worldName = world.getName();
        // Per-world uniqueness: a same-name region in a DIFFERENT world is fine.
        if (regions.containsKey(keyOf(worldName, region.id()))) return ClaimResult.ID_TAKEN;
        // Also block on a legacy plain-keyed region with this id (it has no
        // explicit world, so we conservatively assume same-world ambiguity).
        Region legacy = regions.get(region.id());
        if (legacy != null && (legacy.worldName() == null || legacy.worldName().equals(worldName))) {
            return ClaimResult.ID_TAKEN;
        }

        Region.RegionBounds bounds = new Region.RegionBounds(
                GeometryUtil.blockToChunk(Math.min(x1, x2)),
                GeometryUtil.blockToChunk(Math.min(z1, z2)),
                GeometryUtil.blockToChunk(Math.max(x1, x2)),
                GeometryUtil.blockToChunk(Math.max(z1, z2)));

        // Overlap policy: a claim is rejected only if it overlaps an existing
        // region in the same world that the claimer does NOT own. The owner-
        // permits-self carve-out is what lets a player carve a sub-region
        // out of their own larger claim before running /region setparent.
        UUID claimer = region.owner();
        for (Region other : regions.values()) {
            if (other.bounds() == null) continue;
            if (other.worldName() == null || !other.worldName().equals(worldName)) continue;
            if (claimer != null && other.isOwner(claimer)) continue;
            if (boundsIntersect(bounds, other.bounds())) {
                return ClaimResult.OVERLAPS_FOREIGN_REGION;
            }
        }

        Region stamped = region
                .withBounds(region.bounds() == null ? bounds : region.bounds())
                .withWorld(region.worldName() == null ? worldName : region.worldName());
        regions.put(keyOf(stamped), stamped);
        if (writer != null) {
            writer.queue(stamped);
        }
        long[] keys = GeometryUtil.chunkKeysInBox(x1, z1, x2, z2);
        CompletableFuture<Void> done;
        if (keys.length <= ASYNC_STAMP_THRESHOLD) {
            done = claimAsync(world, keys, stamped.id());
        } else {
            claimLazy(world, keys, stamped.id());
            done = CompletableFuture.completedFuture(null);
        }
        if (pendingChunks != null) pendingChunks.set(done);
        return ClaimResult.OK;
    }

    // Legacy claim() preserved so existing tests and the command-layer path
    // (which doesn't care about the discriminated result for back-compat) keep
    // working. Throws IllegalStateException on a duplicate id and IGNORES
    // overlap-policy rejections — kept identical to pre-overlap behavior so
    // tests written before this feature don't need to be rewritten just to
    // express "skip the overlap check, I'm testing chunk stamping".
    public CompletableFuture<Void> claim(Region region, World world, int x1, int z1, int x2, int z2) {
        Region.RegionBounds bounds = new Region.RegionBounds(
                GeometryUtil.blockToChunk(Math.min(x1, x2)),
                GeometryUtil.blockToChunk(Math.min(z1, z2)),
                GeometryUtil.blockToChunk(Math.max(x1, x2)),
                GeometryUtil.blockToChunk(Math.max(z1, z2)));
        Region stamped = region
                .withBounds(region.bounds() == null ? bounds : region.bounds())
                .withWorld(region.worldName() == null ? world.getName() : region.worldName());
        regions.put(keyOf(stamped), stamped);
        if (writer != null) {
            writer.queue(stamped);
        }
        long[] keys = GeometryUtil.chunkKeysInBox(x1, z1, x2, z2);
        if (keys.length <= ASYNC_STAMP_THRESHOLD) {
            return claimAsync(world, keys, stamped.id());
        }
        claimLazy(world, keys, stamped.id());
        return CompletableFuture.completedFuture(null);
    }

    private static boolean boundsIntersect(Region.RegionBounds a, Region.RegionBounds b) {
        return a.minChunkX() <= b.maxChunkX()
                && a.maxChunkX() >= b.minChunkX()
                && a.minChunkZ() <= b.maxChunkZ()
                && a.maxChunkZ() >= b.minChunkZ();
    }

    private static boolean boundsContains(Region.RegionBounds outer, Region.RegionBounds inner) {
        return inner.minChunkX() >= outer.minChunkX()
                && inner.maxChunkX() <= outer.maxChunkX()
                && inner.minChunkZ() >= outer.minChunkZ()
                && inner.maxChunkZ() <= outer.maxChunkZ();
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
                    PDCUtil.append(chunk, regionId, regionPointersKey);
                } finally {
                    chunk.removePluginChunkTicket(plugin);
                }
            }));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private void claimLazy(World world, long[] keys, String regionId) {
        for (long key : keys) {
            pendingStamps
                    .computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                    .add(regionId);
        }
        // Chunks already loaded at claim time will not fire a ChunkLoadEvent,
        // so stamp them directly here; otherwise only the chunk the player
        // interacted with would ever get the pointer until an unload/reload.
        for (long key : keys) {
            int cx = GeometryUtil.chunkX(key);
            int cz = GeometryUtil.chunkZ(key);
            if (world.isChunkLoaded(cx, cz)) {
                Chunk chunk = world.getChunkAt(cx, cz);
                PDCUtil.append(chunk, regionId, regionPointersKey);
                Set<String> pending = pendingStamps.get(key);
                if (pending != null) pending.remove(regionId);
            }
        }
    }

    // ------------------------------------------------------------------
    // Pointer lookup
    // ------------------------------------------------------------------

    // O(1) resolution of a location to its applicable Regions: read the
    // chunk's PDC pointer list, map each id through the regions cache,
    // and drop any orphans (id that no longer has a live Region — those
    // get physically purged on the next ChunkLoadEvent).
    public List<Region> regionsAt(Location loc) {
        Chunk chunk = loc.getChunk();
        List<String> ids = PDCUtil.read(chunk, regionPointersKey);
        if (ids.isEmpty()) return List.of();
        List<Region> resolved = new ArrayList<>(ids.size());
        World world = chunk.getWorld();
        for (String id : ids) {
            // World-aware lookup so a per-world region named the same as another
            // world's region resolves to the correct one for this chunk.
            Region r = byName(world, id);
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
        if (applicable.isEmpty()) {
            applicable = globalAsFallback(loc.getWorld());
            if (applicable.isEmpty()) return true;
        }

        return isAllowed(applicable, loc, actor, flag);
    }

    /** Evaluates a previously resolved region list without repeating the chunk-pointer lookup. */
    public boolean isAllowed(List<Region> applicable, Location loc, UUID actor, RegionFlag flag) {
        if (applicable.isEmpty()) {
            applicable = globalAsFallback(loc.getWorld());
            if (applicable.isEmpty()) return true;
        }

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
            //
            // The wilderness/global region has no membership concept, so
            // silent flags fall back to "allow" — this preserves vanilla
            // permissive behaviour in unclaimed chunks until an admin
            // explicitly restricts a flag on the global region.
            if (isGlobal(leaf)) continue;
            if (actor != null && leaf.isMember(actor, groups)) continue;
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
            boolean isManager = actor != null && cursor.isManager(actor, groups);
            boolean isMember = actor != null && cursor.isMember(actor, groups);
            Optional<Boolean> resolved = cursor.resolveFlag(flag, isOwner, isManager, isMember, groups);
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
        if (applicable.isEmpty()) {
            applicable = globalAsFallback(loc.getWorld());
            if (applicable.isEmpty()) return true;
        }

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
            // Same wilderness-permissive semantic as isAllowed.
            if (isGlobal(leaf)) continue;
            if (actor != null && leaf.isMember(actor, groups)) continue;
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
            boolean isManager = actor != null && cursor.isManager(actor, groups);
            boolean isMember = actor != null && cursor.isMember(actor, groups);
            Optional<Boolean> boolVerdict = cursor.resolveFlag(baseFlag, isOwner, isManager, isMember, groups);
            if (boolVerdict.isPresent()) return boolVerdict;

            cursor = cursor.hasParent() ? regions.get(cursor.parentId()) : null;
        }
        return Optional.empty();
    }

    // World-aware resolution for mutators. Per-world globals are addressed by
    // (world, "__global__") and lazy-initialised; everything else goes through
    // the standard scoped-then-anyWorld lookup.
    private Region resolveForMutation(World world, String id) {
        if (GLOBAL_REGION_ID.equals(id)) {
            return globalRegion(world);
        }
        if (world != null) {
            Region scoped = byName(world, id);
            if (scoped != null) return scoped;
        }
        return byNameAnyWorld(id);
    }

    // -- Material flag mutators ---------------------------------------------

    public boolean setMaterials(String id, RegionFlag flag, Set<Material> materials) {
        return setMaterialsResolved(byNameAnyWorld(id), flag, materials);
    }

    public boolean setMaterials(World world, String id, RegionFlag flag, Set<Material> materials) {
        return setMaterialsResolved(resolveForMutation(world, id), flag, materials);
    }

    private boolean setMaterialsResolved(Region r, RegionFlag flag, Set<Material> materials) {
        if (r == null) return false;
        if (!flag.isMaterialFlag()) {
            throw new IllegalArgumentException("Not a material-list flag: " + flag);
        }
        Set<Material> current = r.materialsFor(flag);
        Set<Material> normalized = (materials == null || materials.isEmpty())
                ? Set.of()
                : EnumSet.copyOf(materials);
        if (current.equals(normalized)) return false;
        Region updated = r.withMaterials(flag, normalized);
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
        return true;
    }

    public boolean addMaterials(String id, RegionFlag flag, Set<Material> materials) {
        return addMaterialsResolved(byNameAnyWorld(id), flag, materials);
    }

    public boolean addMaterials(World world, String id, RegionFlag flag, Set<Material> materials) {
        return addMaterialsResolved(resolveForMutation(world, id), flag, materials);
    }

    private boolean addMaterialsResolved(Region r, RegionFlag flag, Set<Material> materials) {
        if (r == null || materials == null || materials.isEmpty()) return false;
        if (!flag.isMaterialFlag()) {
            throw new IllegalArgumentException("Not a material-list flag: " + flag);
        }
        EnumSet<Material> merged = EnumSet.noneOf(Material.class);
        merged.addAll(r.materialsFor(flag));
        boolean changed = merged.addAll(materials);
        if (!changed) return false;
        Region updated = r.withMaterials(flag, merged);
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
        return true;
    }

    public boolean removeMaterials(String id, RegionFlag flag, Set<Material> materials) {
        return removeMaterialsResolved(byNameAnyWorld(id), flag, materials);
    }

    public boolean removeMaterials(World world, String id, RegionFlag flag, Set<Material> materials) {
        return removeMaterialsResolved(resolveForMutation(world, id), flag, materials);
    }

    private boolean removeMaterialsResolved(Region r, RegionFlag flag, Set<Material> materials) {
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
        Region updated = r.withMaterials(flag, reduced);
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
        return true;
    }

    public boolean clearMaterials(String id, RegionFlag flag) {
        return clearMaterialsResolved(byNameAnyWorld(id), flag);
    }

    public boolean clearMaterials(World world, String id, RegionFlag flag) {
        return clearMaterialsResolved(resolveForMutation(world, id), flag);
    }

    private boolean clearMaterialsResolved(Region r, RegionFlag flag) {
        if (r == null) return false;
        if (!flag.isMaterialFlag()) {
            throw new IllegalArgumentException("Not a material-list flag: " + flag);
        }
        if (r.materialsFor(flag).isEmpty()) return false;
        Region updated = r.withMaterials(flag, Set.of());
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
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
        if (applicable.isEmpty()) {
            applicable = globalAsFallback(loc.getWorld());
            if (applicable.isEmpty()) return false;
        }

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
    //
    // Public so protection.ui's RegionAuth can resolve the same group set for
    // command/GUI authorization that this class uses for in-world enforcement
    // (isAllowed/isBlockActionAllowed) — a single source of truth keeps the two
    // from drifting apart the way they previously did (RegionAuth used to only
    // check UUID-listed managers, rejecting group-promoted managers that
    // in-world enforcement already accepted).
    public Set<String> groupsOf(UUID actor) {
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
    // Mutators (Execution binding)
    //
    // Region is immutable, so every change to ownership/members/flags
    // builds a fresh Region and atomically replaces the cache entry.
    // Unclaim is logical-only: the id is moved to orphanedRegions so the
    // ChunkLoadEvent listener can strip the dead pointer when the chunk
    // next loads (Edge Case 4 — avoids the 1000-chunk getChunkAtAsync
    // flood that synchronous physical deletion would cause).
    // ------------------------------------------------------------------

    public boolean unclaim(String id) {
        if (GLOBAL_REGION_ID.equals(id)) return false;
        Region r = byNameAnyWorld(id);
        if (r == null) return false;
        regions.remove(keyOf(r));
        orphanedRegions.add(r.id());
        return true;
    }

    public boolean addMember(String id, UUID member) {
        Region r = byNameAnyWorld(id);
        if (r == null || r.members().contains(member)) return false;
        List<UUID> newMembers = new ArrayList<>(r.members());
        newMembers.add(member);
        Region updated = r.withMembers(newMembers);
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
        return true;
    }

    public boolean removeMember(String id, UUID member) {
        Region r = byNameAnyWorld(id);
        if (r == null || !r.members().contains(member)) return false;
        List<UUID> newMembers = new ArrayList<>(r.members());
        newMembers.remove(member);
        Region updated = r.withMembers(newMembers);
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
        return true;
    }

    public boolean addManager(String id, UUID manager) {
        Region r = byNameAnyWorld(id);
        if (r == null || manager == null || r.managers().contains(manager)) return false;
        Region updated = r.addManager(manager);
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
        return true;
    }

    public boolean removeManager(String id, UUID manager) {
        Region r = byNameAnyWorld(id);
        if (r == null || manager == null || !r.managers().contains(manager)) return false;
        Region updated = r.removeManager(manager);
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
        return true;
    }

    // -- Member/manager GROUP mutators --------------------------------------
    //
    // A region's member/manager lists may contain permission GROUPS (stored as
    // lowercase names): any player in that group gains member/manager access
    // without their UUID being listed. These mirror the UUID add*/remove*
    // mutators above — immutable copy, atomic cache swap, async persist — and
    // return false when the region is missing or the change is a no-op (group
    // already present on add / absent on remove). Group names are normalized to
    // lowercase by the Region copy methods, so any casing is accepted.

    public boolean addMemberGroup(String id, String group) {
        Region r = byNameAnyWorld(id);
        if (r == null || group == null || group.isBlank()) return false;
        Region updated = r.addMemberGroup(group);
        if (updated == r) return false;
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
        return true;
    }

    public boolean removeMemberGroup(String id, String group) {
        Region r = byNameAnyWorld(id);
        if (r == null || group == null) return false;
        Region updated = r.removeMemberGroup(group);
        if (updated == r) return false;
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
        return true;
    }

    public boolean addManagerGroup(String id, String group) {
        Region r = byNameAnyWorld(id);
        if (r == null || group == null || group.isBlank()) return false;
        Region updated = r.addManagerGroup(group);
        if (updated == r) return false;
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
        return true;
    }

    public boolean removeManagerGroup(String id, String group) {
        Region r = byNameAnyWorld(id);
        if (r == null || group == null) return false;
        Region updated = r.removeManagerGroup(group);
        if (updated == r) return false;
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
        return true;
    }

    // Entity-list resolution for CreatureSpawnEvent gating. Returns true if any
    // region applicable at `loc` lists `type` in its `flag` entity set. Used by
    // ProtectionListener#onCreatureSpawn to evaluate DENY_MOB_SPAWN /
    // ALLOW_MOB_SPAWN before falling back to the boolean MOB_SPAWNING rule.
    public boolean isEntityListed(Location loc, RegionFlag flag, org.bukkit.entity.EntityType type) {
        if (!flag.isEntityFlag()) return false;
        return isEntityListed(regionsAt(loc), flag, type);
    }

    /** Checks an already-resolved region list without performing another chunk lookup. */
    public boolean isEntityListed(List<Region> applicable, RegionFlag flag,
                                  org.bukkit.entity.EntityType type) {
        if (!flag.isEntityFlag()) return false;
        for (Region r : applicable) {
            if (r.entitiesFor(flag).contains(type)) return true;
        }
        return false;
    }

    // -- Entity-list mutators -----------------------------------------------

    public boolean setEntities(String id, RegionFlag flag, Set<org.bukkit.entity.EntityType> entities) {
        return setEntitiesResolved(byNameAnyWorld(id), flag, entities);
    }

    public boolean setEntities(World world, String id, RegionFlag flag, Set<org.bukkit.entity.EntityType> entities) {
        return setEntitiesResolved(resolveForMutation(world, id), flag, entities);
    }

    private boolean setEntitiesResolved(Region r, RegionFlag flag, Set<org.bukkit.entity.EntityType> entities) {
        if (r == null) return false;
        if (!flag.isEntityFlag()) {
            throw new IllegalArgumentException("Not an entity-list flag: " + flag);
        }
        Set<org.bukkit.entity.EntityType> current = r.entitiesFor(flag);
        Set<org.bukkit.entity.EntityType> normalized = (entities == null || entities.isEmpty())
                ? Set.of()
                : EnumSet.copyOf(entities);
        if (current.equals(normalized)) return false;
        Region updated = r.withEntities(flag, normalized);
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
        return true;
    }

    public boolean clearEntities(String id, RegionFlag flag) {
        return clearEntitiesResolved(byNameAnyWorld(id), flag);
    }

    public boolean clearEntities(World world, String id, RegionFlag flag) {
        return clearEntitiesResolved(resolveForMutation(world, id), flag);
    }

    private boolean clearEntitiesResolved(Region r, RegionFlag flag) {
        if (r == null) return false;
        if (!flag.isEntityFlag()) {
            throw new IllegalArgumentException("Not an entity-list flag: " + flag);
        }
        if (r.entitiesFor(flag).isEmpty()) return false;
        Region updated = r.withEntities(flag, Set.of());
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
        return true;
    }

    // -- Hierarchy ----------------------------------------------------------

    public enum SetParentResult {
        OK,
        UNKNOWN_CHILD,
        UNKNOWN_PARENT,
        SELF_REFERENCE,
        CYCLE,
        NO_CHANGE,
        DIFFERENT_WORLDS,
        NOT_CONTAINED_IN_PARENT,
        OVERLAPS_SIBLING
    }

    // Reparent (or unparent — pass null) a region. Validates that:
    //   - the child exists,
    //   - the parent exists when non-null,
    //   - the child is not setting itself as parent,
    //   - the new parent's ancestor chain does not include the child (cycle).
    // Returns a discriminated result so the command layer can produce a
    // specific error message without re-walking the cache.
    public SetParentResult setParent(String childId, String newParentId) {
        // Global region is the implicit root for wilderness; it cannot be
        // re-parented onto another region, and from a user perspective it
        // does not "exist" as a normal claim.
        if (GLOBAL_REGION_ID.equals(childId)) return SetParentResult.UNKNOWN_CHILD;
        Region child = byNameAnyWorld(childId);
        if (child == null) return SetParentResult.UNKNOWN_CHILD;

        String normalized = (newParentId == null || newParentId.isBlank()) ? null : newParentId;
        if (normalized != null) {
            if (normalized.equals(childId)) return SetParentResult.SELF_REFERENCE;
            Region parent = byNameAnyWorld(normalized);
            if (parent == null) return SetParentResult.UNKNOWN_PARENT;
            if (wouldCreateCycle(childId, normalized)) return SetParentResult.CYCLE;

            // Geometry checks only run when both regions have bounds + world
            // recorded — legacy claims (loaded before those fields existed)
            // are exempt so admins can still reparent them via /region
            // setparent without first re-claiming. Once both sides have
            // bounds, we enforce containment and sibling non-overlap.
            if (child.bounds() != null && parent.bounds() != null) {
                if (child.worldName() != null && parent.worldName() != null
                        && !child.worldName().equals(parent.worldName())) {
                    return SetParentResult.DIFFERENT_WORLDS;
                }
                if (!boundsContains(parent.bounds(), child.bounds())) {
                    return SetParentResult.NOT_CONTAINED_IN_PARENT;
                }
                for (Region sibling : regions.values()) {
                    if (sibling.id().equals(childId)) continue;
                    if (!normalized.equals(sibling.parentId())) continue;
                    if (sibling.bounds() == null) continue;
                    if (boundsIntersect(child.bounds(), sibling.bounds())) {
                        return SetParentResult.OVERLAPS_SIBLING;
                    }
                }
            }
        }

        String current = child.parentId();
        if (java.util.Objects.equals(current, normalized)) return SetParentResult.NO_CHANGE;

        Region updated = child.withParent(normalized);
        regions.put(keyOf(updated), updated);
        if (writer != null) {
            writer.queue(updated);
        }
        return SetParentResult.OK;
    }

    private boolean wouldCreateCycle(String childId, String candidateParentId) {
        Set<String> visited = new HashSet<>();
        String cursor = candidateParentId;
        while (cursor != null && visited.add(cursor)) {
            if (cursor.equals(childId)) return true;
            Region step = byNameAnyWorld(cursor);
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
        return setFlagResolved(byNameAnyWorld(id), flag, target, value);
    }

    public boolean setFlag(World world, String id, RegionFlag flag, FlagTarget target, boolean value) {
        return setFlagResolved(resolveForMutation(world, id), flag, target, value);
    }

    private boolean setFlagResolved(Region r, RegionFlag flag, FlagTarget target, boolean value) {
        if (flag.isMaterialFlag()) {
            throw new IllegalArgumentException(
                    flag + " is a material-list flag; use setMaterials / addMaterials.");
        }
        if (r == null) return false;
        Boolean current = r.rulesFor(flag).get(target);
        if (current != null && current == value) return false;
        Region updated = r.withFlagRule(flag, target, value);
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
        return true;
    }

    // Targeted remover: drop (flag, target) entirely. Returns false if no
    // such rule existed.
    public boolean removeFlag(String id, RegionFlag flag, FlagTarget target) {
        return removeFlagResolved(byNameAnyWorld(id), flag, target);
    }

    public boolean removeFlag(World world, String id, RegionFlag flag, FlagTarget target) {
        return removeFlagResolved(resolveForMutation(world, id), flag, target);
    }

    private boolean removeFlagResolved(Region r, RegionFlag flag, FlagTarget target) {
        if (flag.isMaterialFlag()) {
            throw new IllegalArgumentException(
                    flag + " is a material-list flag; use clearMaterials / removeMaterials.");
        }
        if (r == null) return false;
        if (!r.rulesFor(flag).containsKey(target)) return false;
        Region updated = r.withFlagRule(flag, target, null);
        regions.put(keyOf(updated), updated);
        if (writer != null) writer.queue(updated);
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
