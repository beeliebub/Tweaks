package me.beeliebub.tweaks.minigames.roulette;

import me.beeliebub.tweaks.utils.GeometryUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns world-PDC persistence for physical Roulette boards ({@code tweaks:roulette_boards}) and
 * the runtime click/control indices used by {@link RouletteListener}. Deliberately diverges from
 * {@code BlackjackTableStore}'s chunk-PDC storage: a board must be knowable *before* its chunk
 * loads (to take a force-load chunk ticket for it — see the package {@code CLAUDE.md}), which a
 * chunk-PDC-only design cannot provide.
 */
final class RouletteBoardStore {

    /** PDC key sub-name. Full key: {@code tweaks:roulette_boards}. */
    private static final String BOARDS_KEY_NAME = "roulette_boards";

    private final JavaPlugin plugin;
    private final NamespacedKey boardsKey;

    /** Fast O(1) hitbox-entity-UUID -> bet lookup, the hot path fired on every hitbox click. */
    private final Map<UUID, SegmentRef> hitboxIndex = new HashMap<>();

    /** Board -> its own hitbox UUIDs, so removal doesn't scan the global index to find them. */
    private final Map<BoardEntry, Set<UUID>> hitboxesByBoard = new HashMap<>();

    /** Spin-control block-location-key -> board, mirroring {@code BlackjackTableStore.blockKey}. */
    private final Map<String, BoardEntry> controlIndex = new HashMap<>();

    /** The "wheel at rest" transform snapshot per board, captured at scan time. Restored from
     *  (never accumulated onto) by a later phase's rotation animation. */
    private final Map<BoardEntry, List<RouletteSegmentScanner.SegmentScan>> restPoseByBoard = new HashMap<>();

    /** Per-world cache of {@link #loadBoardsForWorld}'s deserialized result, invalidated only by
     *  {@link #persistBoard}/{@link #unpersistBoard} — avoids re-parsing the world PDC on every
     *  {@code ChunkLoadEvent}, which fires far more often than boards actually change. */
    private final Map<World, List<BoardEntry>> boardsByWorldCache = new HashMap<>();

    /**
     * Ref-counts which boards currently need a chunk's plugin ticket held, per world. Two boards
     * can share a chunk (overlapping footprints, or the documented "same wheel registered twice
     * against a different control block" gap) — without this, releasing one board's tickets could
     * silently strip force-load from a chunk a sibling board still needs.
     */
    private final Map<World, Map<Long, Set<BoardEntry>>> chunkTicketOwners = new HashMap<>();

    RouletteBoardStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.boardsKey = new NamespacedKey(plugin, BOARDS_KEY_NAME);
    }

    // ---- Value types ------------------------------------------------------------

    /**
     * @param center        wheel centre — the mean of every segment's measured centroid
     * @param minBet        board's minimum stake
     * @param maxBet        board's maximum stake
     * @param scanRadius    radius used to discover this board's segments, and to derive its
     *                      chunk footprint
     * @param control       the admin-only spin control block's location
     * @param controlFacing the control block's facing (rejects UP/DOWN at registration)
     */
    record BoardEntry(Location center, int minBet, int maxBet, int scanRadius,
                       Location control, BlockFace controlFacing) {}

    /** Associates a clicked hitbox with the board and bet it represents. */
    record SegmentRef(BoardEntry board, BetType type, int selector) {}

    // ---- PDC serialisation --------------------------------------------------------

    /**
     * Serialises to {@code worldName|cx|cy|cz|minBet|maxBet|scanRadius|ctrlX|ctrlY|ctrlZ|ctrlFacing}.
     * Package-visible so tests can call it directly.
     */
    static String serializeBoard(BoardEntry entry) {
        Location c = entry.center();
        Location ctrl = entry.control();
        return c.getWorld().getName()
                + '|' + c.x() + '|' + c.y() + '|' + c.z()
                + '|' + entry.minBet() + '|' + entry.maxBet() + '|' + entry.scanRadius()
                + '|' + ctrl.getBlockX() + '|' + ctrl.getBlockY() + '|' + ctrl.getBlockZ()
                + '|' + entry.controlFacing().name();
    }

    /**
     * Deserialises a string produced by {@link #serializeBoard}. Returns {@code null} if
     * malformed (wrong field count, unparseable numbers, unknown block face) or the world is not
     * currently loaded. Package-visible so tests can call it directly.
     */
    static BoardEntry deserializeBoard(String raw) {
        String[] p = raw.split("\\|");
        if (p.length != 11) {
            return null;
        }
        try {
            World world = Bukkit.getWorld(p[0]);
            if (world == null) {
                return null;
            }
            double cx = Double.parseDouble(p[1]);
            double cy = Double.parseDouble(p[2]);
            double cz = Double.parseDouble(p[3]);
            int minBet = Integer.parseInt(p[4]);
            int maxBet = Integer.parseInt(p[5]);
            int scanRadius = Integer.parseInt(p[6]);
            int ctrlX = Integer.parseInt(p[7]);
            int ctrlY = Integer.parseInt(p[8]);
            int ctrlZ = Integer.parseInt(p[9]);
            BlockFace facing = BlockFace.valueOf(p[10]);
            Location center = new Location(world, cx, cy, cz);
            Location control = new Location(world, ctrlX, ctrlY, ctrlZ);
            return new BoardEntry(center, minBet, maxBet, scanRadius, control, facing);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- World PDC access ---------------------------------------------------------

    /** Reads and deserialises every board entry persisted in {@code world}'s PDC, skipping and
     *  logging any malformed entry. Cached per world until the next {@link #persistBoard}/
     *  {@link #unpersistBoard} call for that world — {@code ChunkLoadEvent} fires far more often
     *  than boards actually change, and this is called from that handler. */
    List<BoardEntry> loadBoardsForWorld(World world) {
        List<BoardEntry> cached = boardsByWorldCache.get(world);
        if (cached != null) {
            return cached;
        }
        PersistentDataContainer pdc = world.getPersistentDataContainer();
        List<String> raw = pdc.getOrDefault(boardsKey, PersistentDataType.LIST.strings(), List.of());
        List<BoardEntry> result = new ArrayList<>();
        for (String s : raw) {
            BoardEntry entry = deserializeBoard(s);
            if (entry == null) {
                plugin.getLogger().warning("Roulette: malformed board entry in world PDC (skipping): " + s);
                continue;
            }
            result.add(entry);
        }
        boardsByWorldCache.put(world, result);
        return result;
    }

    /**
     * Appends {@code entry}'s serialized form to its world's PDC board list. A byte-identical
     * entry already present is not duplicated (idempotent re-registration against the exact same
     * control block); returns {@code false} in that case and {@code true} otherwise.
     */
    boolean persistBoard(BoardEntry entry) {
        World world = entry.center().getWorld();
        PersistentDataContainer pdc = world.getPersistentDataContainer();
        List<String> existing = pdc.getOrDefault(boardsKey, PersistentDataType.LIST.strings(), List.of());
        String serialized = serializeBoard(entry);
        if (existing.contains(serialized)) {
            return false;
        }
        List<String> updated = new ArrayList<>(existing);
        updated.add(serialized);
        pdc.set(boardsKey, PersistentDataType.LIST.strings(), updated);
        boardsByWorldCache.remove(world);
        return true;
    }

    /** Removes {@code entry}'s serialized form from its world's PDC board list. */
    void unpersistBoard(BoardEntry entry) {
        World world = entry.center().getWorld();
        PersistentDataContainer pdc = world.getPersistentDataContainer();
        List<String> existing = pdc.getOrDefault(boardsKey, PersistentDataType.LIST.strings(), List.of());
        List<String> updated = new ArrayList<>(existing);
        updated.remove(serializeBoard(entry));
        pdc.set(boardsKey, PersistentDataType.LIST.strings(), updated);
        boardsByWorldCache.remove(world);
    }

    // ---- Chunk footprint -----------------------------------------------------------

    /** Every chunk key touched by {@code entry}'s scan radius, derived on demand from its
     *  immutable center/scanRadius — never stored as a separate ledger. Static: uses no instance
     *  state, and package-visible so tests can call it directly. */
    static long[] chunkKeysTouchedBy(BoardEntry entry) {
        Location c = entry.center();
        int r = entry.scanRadius();
        return GeometryUtil.chunkKeysInBox(
                c.getBlockX() - r, c.getBlockZ() - r,
                c.getBlockX() + r, c.getBlockZ() + r);
    }

    // ---- Chunk ticket ownership (ref-counted across boards) ------------------------

    /**
     * Registers {@code entry} as an owner of every chunk it touches, returning only the chunk
     * keys that had no prior owner — the subset that actually need a new plugin ticket taken.
     * Calling this twice for the same entry without an intervening unregister is idempotent
     * (a {@code Set} absorbs the duplicate) but not expected in normal use.
     */
    Set<Long> registerChunkOwnership(BoardEntry entry) {
        Map<Long, Set<BoardEntry>> perWorld =
                chunkTicketOwners.computeIfAbsent(entry.center().getWorld(), w -> new HashMap<>());
        Set<Long> newlyOwned = new HashSet<>();
        for (long key : chunkKeysTouchedBy(entry)) {
            Set<BoardEntry> owners = perWorld.computeIfAbsent(key, k -> new HashSet<>());
            if (owners.isEmpty()) {
                newlyOwned.add(key);
            }
            owners.add(entry);
        }
        return newlyOwned;
    }

    /**
     * Unregisters {@code entry} as an owner of every chunk it touches, returning only the chunk
     * keys left with no remaining owner — the subset whose plugin ticket is actually safe to
     * release. A chunk still owned by a sibling board (overlapping footprints, or two entries for
     * the same physical wheel — see the package {@code CLAUDE.md}'s known gap) is never included.
     */
    Set<Long> unregisterChunkOwnership(BoardEntry entry) {
        Map<Long, Set<BoardEntry>> perWorld = chunkTicketOwners.get(entry.center().getWorld());
        if (perWorld == null) {
            return Set.of();
        }
        Set<Long> nowUnowned = new HashSet<>();
        for (long key : chunkKeysTouchedBy(entry)) {
            Set<BoardEntry> owners = perWorld.get(key);
            if (owners == null) {
                continue;
            }
            owners.remove(entry);
            if (owners.isEmpty()) {
                perWorld.remove(key);
                nowUnowned.add(key);
            }
        }
        return nowUnowned;
    }

    // ---- Control index --------------------------------------------------------------

    /** Canonical block location string for use as a map key: {@code "world:x:y:z"}. */
    static String blockKey(Location loc) {
        return loc.getWorld().getName()
                + ":" + loc.getBlockX()
                + ":" + loc.getBlockY()
                + ":" + loc.getBlockZ();
    }

    void registerControl(BoardEntry entry) {
        controlIndex.put(blockKey(entry.control()), entry);
    }

    void unregisterControl(BoardEntry entry) {
        controlIndex.remove(blockKey(entry.control()));
    }

    /** Fast O(1) lookup of the board registered at control block-location key {@code key}. */
    BoardEntry lookupControl(String key) {
        return controlIndex.get(key);
    }

    // ---- Hitbox index ----------------------------------------------------------------

    boolean isActive(BoardEntry entry) {
        return hitboxesByBoard.containsKey(entry);
    }

    /** Every currently-active board — used by the renderer's self-healing per-board refresh loop. */
    Set<BoardEntry> activeBoards() {
        return Set.copyOf(hitboxesByBoard.keySet());
    }

    void registerHitboxes(BoardEntry entry, Map<UUID, SegmentRef> refs) {
        hitboxIndex.putAll(refs);
        hitboxesByBoard.computeIfAbsent(entry, e -> new HashSet<>()).addAll(refs.keySet());
    }

    SegmentRef lookupHitbox(UUID entityId) {
        return hitboxIndex.get(entityId);
    }

    /** Every hitbox UUID currently known to belong to ANY board — used to distinguish a live
     *  sibling board's hitboxes from a genuine orphan during a chunk-scoped sweep. */
    Set<UUID> allKnownHitboxIds() {
        return Set.copyOf(hitboxIndex.keySet());
    }

    /** Removes and returns {@code entry}'s own hitbox UUIDs, clearing both hitbox indices and its
     *  rest-pose snapshot. Does not touch the control index — see {@link #unregisterControl}. */
    Set<UUID> clearBoardRuntime(BoardEntry entry) {
        Set<UUID> ids = hitboxesByBoard.remove(entry);
        if (ids == null) {
            return Set.of();
        }
        ids.forEach(hitboxIndex::remove);
        restPoseByBoard.remove(entry);
        return ids;
    }

    void storeRestPose(BoardEntry entry, List<RouletteSegmentScanner.SegmentScan> snapshot) {
        restPoseByBoard.put(entry, List.copyOf(snapshot));
    }

    List<RouletteSegmentScanner.SegmentScan> restPose(BoardEntry entry) {
        return restPoseByBoard.getOrDefault(entry, List.of());
    }

    /** Clears every in-memory index. Called from {@link RouletteListener#shutdown()}. */
    void clearAll() {
        hitboxIndex.clear();
        hitboxesByBoard.clear();
        controlIndex.clear();
        restPoseByBoard.clear();
        boardsByWorldCache.clear();
        chunkTicketOwners.clear();
    }
}
