package me.beeliebub.tweaks.minigames.roulette;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * World-PDC persistence for a board's mid-spin rest-pose snapshot ({@code tweaks:roulette_restpose}),
 * separate from {@link RouletteBoardStore} because this is a spin-lifetime concern (written once at
 * spin start, cleared once after a fully successful restore), not board-registration state.
 *
 * <p>World PDC, not {@code YamlStore}: the snapshot's whole job is to stay consistent with the
 * segment entities it describes, which live in and are saved with the world. World PDC is written
 * and saved in the same unit as those entities; an async-flushed keyed store could be stale relative
 * to the world file after a hard kill. {@link RouletteBoardStore} already established world PDC as
 * this package's persistence mechanism for exactly this class of reason.
 */
final class RouletteRestPoseStore {

    private static final String RESTPOSE_KEY_NAME = "roulette_restpose";

    private final JavaPlugin plugin;
    private final NamespacedKey restPoseKey;

    RouletteRestPoseStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.restPoseKey = new NamespacedKey(plugin, RESTPOSE_KEY_NAME);
    }

    /** One segment's exact rest-pose {@code Location} components, captured live at spin start. */
    record SegmentPose(UUID entityId, double x, double y, double z, float yaw, float pitch) {}

    /**
     * Serialises to {@code boardKey|entityId|x|y|z|yaw|pitch}. {@code boardKey} scopes the row to
     * one board (via {@link RouletteBoardStore#blockKey}) so two simultaneously-spinning boards in
     * the same world can never clobber each other's rows. Package-visible so tests can call it
     * directly.
     */
    static String serializePose(String boardKey, SegmentPose pose) {
        return boardKey + '|' + pose.entityId()
                + '|' + pose.x() + '|' + pose.y() + '|' + pose.z()
                + '|' + pose.yaw() + '|' + pose.pitch();
    }

    /**
     * Deserialises a row produced by {@link #serializePose}, returning {@code null} (never
     * throwing) on a malformed row, an unparseable field, or a {@code boardKey} that doesn't match
     * {@code expectedBoardKey}. Package-visible so tests can call it directly.
     */
    static SegmentPose deserializePose(String raw, String expectedBoardKey) {
        String[] p = raw.split("\\|");
        if (p.length != 7 || !p[0].equals(expectedBoardKey)) {
            return null;
        }
        try {
            UUID entityId = UUID.fromString(p[1]);
            double x = Double.parseDouble(p[2]);
            double y = Double.parseDouble(p[3]);
            double z = Double.parseDouble(p[4]);
            float yaw = Float.parseFloat(p[5]);
            float pitch = Float.parseFloat(p[6]);
            return new SegmentPose(entityId, x, y, z, yaw, pitch);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Replaces {@code board}'s rows only — other boards' rows in the same world are untouched. */
    void save(RouletteBoardStore.BoardEntry board, List<SegmentPose> poses) {
        World world = board.center().getWorld();
        String boardKey = RouletteBoardStore.blockKey(board.control());
        PersistentDataContainer pdc = world.getPersistentDataContainer();
        List<String> existing = pdc.getOrDefault(restPoseKey, PersistentDataType.LIST.strings(), List.of());
        List<String> kept = new ArrayList<>();
        for (String row : existing) {
            if (!row.startsWith(boardKey + '|')) {
                kept.add(row);
            }
        }
        for (SegmentPose pose : poses) {
            kept.add(serializePose(boardKey, pose));
        }
        pdc.set(restPoseKey, PersistentDataType.LIST.strings(), kept);
    }

    /**
     * Loads {@code board}'s persisted rest-pose snapshot, skipping (and not throwing on) malformed
     * rows. A skipped row is logged rather than silently dropped: {@link RouletteRenderer}'s
     * restore-completeness check only sees the poses this method actually returns, so a corrupted
     * row that vanished without a trace would make a partially-restorable snapshot look fully
     * restorable — the exact "activate against possibly-wrong geometry" case this snapshot exists
     * to prevent. Mirrors {@link RouletteBoardStore#loadBoardsForWorld}'s malformed-entry logging.
     */
    List<SegmentPose> load(RouletteBoardStore.BoardEntry board) {
        World world = board.center().getWorld();
        String boardKey = RouletteBoardStore.blockKey(board.control());
        PersistentDataContainer pdc = world.getPersistentDataContainer();
        List<String> existing = pdc.getOrDefault(restPoseKey, PersistentDataType.LIST.strings(), List.of());
        List<SegmentPose> result = new ArrayList<>();
        for (String row : existing) {
            SegmentPose pose = deserializePose(row, boardKey);
            if (pose != null) {
                result.add(pose);
            } else if (row.startsWith(boardKey + '|')) {
                plugin.getLogger().warning("Roulette: malformed rest-pose row for board at "
                        + board.center() + " (skipping): " + row);
            }
        }
        return result;
    }

    /** Clears {@code board}'s persisted rest-pose snapshot — called only after a fully successful restore. */
    void clear(RouletteBoardStore.BoardEntry board) {
        World world = board.center().getWorld();
        String boardKey = RouletteBoardStore.blockKey(board.control());
        PersistentDataContainer pdc = world.getPersistentDataContainer();
        List<String> existing = pdc.getOrDefault(restPoseKey, PersistentDataType.LIST.strings(), List.of());
        List<String> kept = new ArrayList<>();
        for (String row : existing) {
            if (!row.startsWith(boardKey + '|')) {
                kept.add(row);
            }
        }
        pdc.set(restPoseKey, PersistentDataType.LIST.strings(), kept);
    }
}
