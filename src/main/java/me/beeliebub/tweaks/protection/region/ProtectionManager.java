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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

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
    private final RegionRegistry registry;
    private final RegionFlagResolver flagResolver;
    private final RegionMutators mutators;

    // Local aliases preserve the manager's existing hot-path implementation
    // while RegionRegistry remains the sole creator and owner of these maps.
    private final ConcurrentHashMap<String, Region> regions;
    private final ConcurrentHashMap<String, Set<String>> pendingStamps;
    private final Set<String> orphanedRegions;

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
        this.registry = new RegionRegistry(plugin == null ? null : plugin.getLogger());
        this.flagResolver = new RegionFlagResolver(this);
        this.mutators = new RegionMutators(this);
        this.regions = registry.regions();
        this.pendingStamps = registry.pendingStamps();
        this.orphanedRegions = registry.orphanedRegions();
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

    public RegionWriter writer() {
        return writer;
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

    public static String stampKey(String worldName, long chunkKey) {
        return RegionRegistry.stampKey(worldName, chunkKey);
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
        return registry.migrateLegacyRegions(defaultWorldName,
                writer == null ? null : writer::queueLegacyMigration);
    }

    public ConcurrentHashMap<String, Set<String>> pendingStamps() {
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
        onClaimed(stamped);
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
        onClaimed(stamped);
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

    private void onClaimed(Region region) {
        orphanedRegions.remove(keyOf(region));
        if (writer != null) writer.untombstone(keyOf(region));
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
                    .computeIfAbsent(RegionRegistry.stampKey(world.getName(), key), k -> ConcurrentHashMap.newKeySet())
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
                String stampKey = RegionRegistry.stampKey(world.getName(), key);
                Set<String> pending = pendingStamps.get(stampKey);
                if (pending != null) pending.remove(regionId);
                if (pending != null && pending.isEmpty()) pendingStamps.remove(stampKey, pending);
            }
        }
    }

    // ------------------------------------------------------------------
    // Pointer lookup
    // ------------------------------------------------------------------

    // O(1) resolution of a location to its applicable Regions: read the
    // chunk's PDC pointer list and map each id through the regions cache.
    // Bounds validation keeps stale live pointers inert, while unresolved
    // pointers are left for chunk-load cleanup. This method never writes PDC.
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
            if (r == null) continue;
            // A pointer can outlive the claim that placed it. Bounds validation
            // keeps that stale pointer inert while preserving legacy null bounds.
            if (r.bounds() != null && !r.bounds().contains(chunk.getX(), chunk.getZ())) continue;
            resolved.add(r);
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
        return flagResolver.isAllowed(loc, actor, flag);
    }

    /** Evaluates a previously resolved region list without repeating the chunk-pointer lookup. */
    public boolean isAllowed(List<Region> applicable, Location loc, UUID actor, RegionFlag flag) {
        return flagResolver.isAllowed(applicable, loc, actor, flag);
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
        return flagResolver.isBlockActionAllowed(loc, actor, material, baseFlag);
    }

    // World-aware resolution for mutators. Per-world globals are addressed by
    // (world, "__global__") and lazy-initialised; everything else goes through
    // the standard scoped-then-anyWorld lookup.
    private Region resolveForMutation(World world, String id) {
        if (GLOBAL_REGION_ID.equals(id)) {
            return globalRegion(world);
        }
        if (world != null) return byName(world, id);
        return byNameAnyWorld(id);
    }

    // -- Material flag mutators ---------------------------------------------

    public boolean setMaterials(String id, RegionFlag flag, Set<Material> materials) {
        return mutators.setMaterials(null, id, flag, materials);
    }

    public boolean setMaterials(World world, String id, RegionFlag flag, Set<Material> materials) {
        return mutators.setMaterials(world, id, flag, materials);
    }

    public boolean addMaterials(String id, RegionFlag flag, Set<Material> materials) {
        return mutators.addMaterials(null, id, flag, materials);
    }

    public boolean addMaterials(World world, String id, RegionFlag flag, Set<Material> materials) {
        return mutators.addMaterials(world, id, flag, materials);
    }

    public boolean removeMaterials(String id, RegionFlag flag, Set<Material> materials) {
        return mutators.removeMaterials(null, id, flag, materials);
    }

    public boolean removeMaterials(World world, String id, RegionFlag flag, Set<Material> materials) {
        return mutators.removeMaterials(world, id, flag, materials);
    }

    public boolean clearMaterials(String id, RegionFlag flag) {
        return mutators.clearMaterials(null, id, flag);
    }

    public boolean clearMaterials(World world, String id, RegionFlag flag) {
        return mutators.clearMaterials(world, id, flag);
    }

    // Stricter variant of isAllowed used by listeners that enforce vanilla
    // gamerules (mob griefing, fire spread, ...). The contract is reversed:
    // an action is permitted ONLY when an overlapping region explicitly opts
    // in via flag=true. Wilderness — and regions whose entire hierarchy
    // chain stays silent on the flag — return false, so the gamerule's
    // "don't grief" default holds everywhere except where an admin has
    // explicitly written the override.
    public boolean isExplicitlyAllowed(Location loc, UUID actor, RegionFlag flag) {
        return flagResolver.isExplicitlyAllowed(loc, actor, flag);
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
    // Unclaim archives YAML synchronously before changing the cache. PDC
    // pointer removal remains lazy so a large claim does not trigger a flood
    // of asynchronous chunk loads; chunk-load cleanup removes those pointers.
    // ChunkLoadEvent cleanup strips stale pointers when their chunks load.
    // ------------------------------------------------------------------

    public enum UnclaimResult { OK, UNKNOWN_REGION, GLOBAL_DENIED, PERSISTENCE_FAILED }

    /** Destruction order is deepest descendant first, with the target last. */
    public record UnclaimOutcome(UnclaimResult result, List<Region> destroyed) {}

    public List<Region> descendantsOf(String id) {
        Region root = byNameAnyWorld(id);
        return descendantsOfRoot(root);
    }

    public List<Region> descendantsOf(World world, String id) {
        Region root = resolveForMutation(world, id);
        return descendantsOfRoot(root);
    }

    private List<Region> descendantsOfRoot(Region root) {
        if (root == null || GLOBAL_REGION_ID.equals(root.id())) return List.of();
        Map<String, List<Region>> byParent = childrenByParent();
        List<Region> descendants = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(keyOf(root));
        collectDescendants(root, byParent, visited, descendants);
        return List.copyOf(descendants);
    }

    private Map<String, List<Region>> childrenByParent() {
        Map<String, List<Region>> byParent = new HashMap<>();
        for (Region region : regions.values()) {
            if (GLOBAL_REGION_ID.equals(region.id()) || !region.hasParent()) continue;
            byParent.computeIfAbsent(region.parentId(), ignored -> new ArrayList<>()).add(region);
        }
        return byParent;
    }

    private void collectDescendants(Region parent, Map<String, List<Region>> byParent,
                                    Set<String> visited, List<Region> output) {
        for (Region child : childrenOf(parent, byParent)) {
            String childKey = keyOf(child);
            if (!visited.add(childKey)) continue;
            collectDescendants(child, byParent, visited, output);
            output.add(child);
        }
    }

    private List<Region> childrenOf(Region parent, Map<String, List<Region>> byParent) {
        List<Region> candidates = byParent.get(parent.id());
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<Region> children = new ArrayList<>(candidates.size());
        for (Region child : candidates) {
            if (Objects.equals(child.worldName(), parent.worldName())) children.add(child);
        }
        return children;
    }

    public List<String> auditCrossOwnerHierarchies() {
        Map<String, List<Region>> byParent = childrenByParent();
        List<String> crossOwner = new ArrayList<>();
        for (Region parent : regions.values()) {
            if (GLOBAL_REGION_ID.equals(parent.id())) continue;
            for (Region child : childrenOf(parent, byParent)) {
                if (!Objects.equals(child.owner(), parent.owner())) {
                    crossOwner.add(child.id() + " -> " + parent.id());
                }
            }
        }
        return List.copyOf(crossOwner);
    }

    public UnclaimOutcome unclaim(String id) {
        if (GLOBAL_REGION_ID.equals(id)) return new UnclaimOutcome(UnclaimResult.GLOBAL_DENIED, List.of());
        Region root = byNameAnyWorld(id);
        return unclaimResolved(root, id);
    }

    public UnclaimOutcome unclaim(World world, String id) {
        if (GLOBAL_REGION_ID.equals(id)) return new UnclaimOutcome(UnclaimResult.GLOBAL_DENIED, List.of());
        Region root = resolveForMutation(world, id);
        return unclaimResolved(root, id);
    }

    private UnclaimOutcome unclaimResolved(Region root, String id) {
        if (root == null) return new UnclaimOutcome(UnclaimResult.UNKNOWN_REGION, List.of());

        List<Region> targets = new ArrayList<>(descendantsOfRoot(root));
        targets.add(root);
        if (writer != null) {
            List<Region> archived = new ArrayList<>(targets.size());
            for (Region target : targets) {
                try {
                    writer.archive(target);
                    archived.add(target);
                } catch (IOException e) {
                    writer.untombstone(keyOf(target));
                    logPersistenceFailure("Failed to archive region '" + target.id()
                            + "' during unclaim", e);
                    rollbackArchives(archived);
                    return new UnclaimOutcome(UnclaimResult.PERSISTENCE_FAILED, List.of());
                }
            }
        }

        for (Region target : targets) {
            regions.remove(keyOf(target), target);
            orphanedRegions.add(keyOf(target));
            purgePendingStamps(target);
        }
        return new UnclaimOutcome(UnclaimResult.OK, List.copyOf(targets));
    }

    private void rollbackArchives(List<Region> archived) {
        for (Region region : archived) {
            try {
                writer.untombstone(keyOf(region));
                writer.writeNow(region);
            } catch (IOException rollbackFailure) {
                logPersistenceFailure("Failed to restore region '" + region.id()
                        + "' after an unclaim archive failure", rollbackFailure);
            }
        }
    }

    private void logPersistenceFailure(String message, IOException failure) {
        if (plugin != null && plugin.getLogger() != null) {
            plugin.getLogger().log(Level.SEVERE, message, failure);
        }
    }

    private void purgePendingStamps(Region target) {
        String regionId = target.id();
        String worldName = target.worldName();
        pendingStamps.entrySet().removeIf(entry -> {
            String stampKey = entry.getKey();
            int separator = stampKey.lastIndexOf(':');
            if (worldName != null && (separator <= 0
                    || !worldName.equals(stampKey.substring(0, separator)))) return false;
            Set<String> ids = entry.getValue();
            ids.remove(regionId);
            return ids.isEmpty();
        });
    }

    public int reconcilePendingStamps() {
        Set<String> pruned = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : pendingStamps.entrySet()) {
            int separator = entry.getKey().lastIndexOf(':');
            if (separator <= 0) {
                pendingStamps.remove(entry.getKey(), entry.getValue());
                continue;
            }
            String worldName = entry.getKey().substring(0, separator);
            entry.getValue().removeIf(regionId -> {
                if (regions.containsKey(keyOf(worldName, regionId))) return false;
                String orphanKey = keyOf(worldName, regionId);
                orphanedRegions.add(orphanKey);
                pruned.add(orphanKey);
                return true;
            });
        }
        pendingStamps.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return pruned.size();
    }

    public boolean addMember(String id, UUID member) {
        return mutators.addMember(null, id, member);
    }

    public boolean addMember(World world, String id, UUID member) {
        return mutators.addMember(world, id, member);
    }

    public boolean removeMember(String id, UUID member) {
        return mutators.removeMember(null, id, member);
    }

    public boolean removeMember(World world, String id, UUID member) {
        return mutators.removeMember(world, id, member);
    }

    public boolean addManager(String id, UUID manager) {
        return mutators.addManager(null, id, manager);
    }

    public boolean addManager(World world, String id, UUID manager) {
        return mutators.addManager(world, id, manager);
    }

    public boolean removeManager(String id, UUID manager) {
        return mutators.removeManager(null, id, manager);
    }

    public boolean removeManager(World world, String id, UUID manager) {
        return mutators.removeManager(world, id, manager);
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
        return mutators.addMemberGroup(null, id, group);
    }

    public boolean addMemberGroup(World world, String id, String group) {
        return mutators.addMemberGroup(world, id, group);
    }

    public boolean removeMemberGroup(String id, String group) {
        return mutators.removeMemberGroup(null, id, group);
    }

    public boolean removeMemberGroup(World world, String id, String group) {
        return mutators.removeMemberGroup(world, id, group);
    }

    public boolean addManagerGroup(String id, String group) {
        return mutators.addManagerGroup(null, id, group);
    }

    public boolean addManagerGroup(World world, String id, String group) {
        return mutators.addManagerGroup(world, id, group);
    }

    public boolean removeManagerGroup(String id, String group) {
        return mutators.removeManagerGroup(null, id, group);
    }

    public boolean removeManagerGroup(World world, String id, String group) {
        return mutators.removeManagerGroup(world, id, group);
    }

    // Entity-list resolution for CreatureSpawnEvent gating. Returns true if any
    // region applicable at `loc` lists `type` in its `flag` entity set. Used by
    // ProtectionListener#onCreatureSpawn to evaluate DENY_MOB_SPAWN /
    // ALLOW_MOB_SPAWN before falling back to the boolean MOB_SPAWNING rule.
    public boolean isEntityListed(Location loc, RegionFlag flag, org.bukkit.entity.EntityType type) {
        return flagResolver.isEntityListed(loc, flag, type);
    }

    /** Checks an already-resolved region list without performing another chunk lookup. */
    public boolean isEntityListed(List<Region> applicable, RegionFlag flag,
                                  org.bukkit.entity.EntityType type) {
        return flagResolver.isEntityListed(applicable, flag, type);
    }

    // -- Entity-list mutators -----------------------------------------------

    public boolean setEntities(String id, RegionFlag flag, Set<org.bukkit.entity.EntityType> entities) {
        return mutators.setEntities(null, id, flag, entities);
    }

    public boolean setEntities(World world, String id, RegionFlag flag, Set<org.bukkit.entity.EntityType> entities) {
        return mutators.setEntities(world, id, flag, entities);
    }

    public boolean clearEntities(String id, RegionFlag flag) {
        return mutators.clearEntities(null, id, flag);
    }

    public boolean clearEntities(World world, String id, RegionFlag flag) {
        return mutators.clearEntities(world, id, flag);
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
        DIFFERENT_OWNER,
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
        return setParent(childId, newParentId, false);
    }

    public SetParentResult setParent(String childId, String newParentId, boolean allowCrossOwner) {
        // Global region is the implicit root for wilderness; it cannot be
        // re-parented onto another region, and from a user perspective it
        // does not "exist" as a normal claim.
        if (GLOBAL_REGION_ID.equals(childId)) return SetParentResult.UNKNOWN_CHILD;
        Region child = byNameAnyWorld(childId);
        return setParentResolved(child, child == null || newParentId == null
                ? null : byNameAnyWorld(newParentId), childId, newParentId, allowCrossOwner);
    }

    public SetParentResult setParent(World world, String childId, String newParentId,
                                     boolean allowCrossOwner) {
        if (GLOBAL_REGION_ID.equals(childId)) return SetParentResult.UNKNOWN_CHILD;
        Region child = resolveForMutation(world, childId);
        Region parent = newParentId == null ? null : resolveForMutation(world, newParentId);
        return setParentResolved(child, parent, childId, newParentId, allowCrossOwner);
    }

    private SetParentResult setParentResolved(Region child, Region parent,
                                               String childId, String newParentId,
                                               boolean allowCrossOwner) {
        if (child == null) return SetParentResult.UNKNOWN_CHILD;

        String normalized = (newParentId == null || newParentId.isBlank()) ? null : newParentId;
        if (normalized != null) {
            if (normalized.equals(childId)) return SetParentResult.SELF_REFERENCE;
            if (parent == null) return SetParentResult.UNKNOWN_PARENT;
            if (wouldCreateCycle(child.worldName(), childId, normalized)) return SetParentResult.CYCLE;
            // Parent ownership is a hierarchy invariant. The caller supplies
            // an explicit administrative override for intentional mixed-owner
            // layouts; this layer does not perform permission checks.
            if (!allowCrossOwner && !Objects.equals(child.owner(), parent.owner())) {
                return SetParentResult.DIFFERENT_OWNER;
            }

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
                    if (!Objects.equals(sibling.worldName(), parent.worldName())) continue;
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
        return wouldCreateCycle(null, childId, candidateParentId);
    }

    private boolean wouldCreateCycle(String worldName, String childId, String candidateParentId) {
        Set<String> visited = new HashSet<>();
        String cursor = candidateParentId;
        while (cursor != null && visited.add(cursor)) {
            if (cursor.equals(childId)) return true;
            Region step = worldName == null ? byNameAnyWorld(cursor)
                    : regions.get(RegionRegistry.keyOf(worldName, cursor));
            if (step == null && worldName != null) step = regions.get(cursor);
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
