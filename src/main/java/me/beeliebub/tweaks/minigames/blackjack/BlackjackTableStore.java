package me.beeliebub.tweaks.minigames.blackjack;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns chunk-PDC persistence for physical Blackjack tables ({@code tweaks:blackjack_tables}),
 * the button geometry derived from a table's middle-button location and facing, and the fast
 * button-click index ({@link #lookupButton}) used by {@link BlackjackListener#onButtonClick}.
 */
public final class BlackjackTableStore {

    /** Width of the required solid block rectangle / table footprint (shorter axis). */
    private static final int TABLE_WIDTH = 2;
    /** Depth of the required solid block rectangle / table footprint (longer axis). */
    private static final int TABLE_DEPTH = 3;

    /** PDC key sub-name for the server-table store. Full key: {@code tweaks:blackjack_tables}. */
    private static final String TABLES_KEY_NAME = "blackjack_tables";

    private final JavaPlugin plugin;
    private final NamespacedKey tablesKey;

    /**
     * Fast O(1) button-to-server-table lookup. Keyed by block location string
     * ({@code "world:x:y:z"}). Each entry stores the table and whether the button
     * is the left, middle, or right control.
     */
    private final Map<String, ButtonRef> buttonMap = new HashMap<>();

    BlackjackTableStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tablesKey = new NamespacedKey(plugin, TABLES_KEY_NAME);
    }

    // ---- Value types --------------------------------------------------------

    /**
     * Serialized table entry. All geometry and PDC schema knowledge lives here.
     *
     * @param center       Support-block top-face center location (table surface).
     * @param bet          Bet amount for this table.
     * @param middleButton Middle-button block location.
     * @param facing       Direction the button faces (away from the wall).
     * @param backColor    Optional RGB integer for the card-back tint, or {@code null}
     *                     to use the pack's default back color.
     */
    record TableEntry(Location center, int bet, Location middleButton, BlockFace facing,
                      Integer backColor) {}

    /** Identifies a button's role at a table. */
    public enum ButtonRole { LEFT, MIDDLE, RIGHT }

    /** Associates a button block with its server-table entry and role. */
    record ButtonRef(TableEntry table, ButtonRole role) {}

    // ---- PDC serialisation --------------------------------------------------

    /**
     * Serialises a table entry to a pipe-delimited string with 9 or 10 fields:
     * {@code worldName|cx|cy|cz|bet|bx|by|bz|facing[|backColor]}
     *
     * <ul>
     *   <li>cx/cy/cz  — doubles: support-block top-face centre (table surface)</li>
     *   <li>bet       — int</li>
     *   <li>bx/by/bz  — ints: middle-button block coordinates</li>
     *   <li>facing    — {@link BlockFace#name()}</li>
     *   <li>backColor — optional int (hex RGB, e.g. {@code 16744448}); omitted when {@code null}</li>
     * </ul>
     *
     * Package-visible so tests can call it directly.
     */
    static String serializeTable(Location center, int bet,
                                 Location middleButton, BlockFace facing, Integer backColor) {
        StringBuilder sb = new StringBuilder();
        sb.append(center.getWorld().getName())
                .append('|').append(center.x())
                .append('|').append(center.y())
                .append('|').append(center.z())
                .append('|').append(bet)
                .append('|').append(middleButton.getBlockX())
                .append('|').append(middleButton.getBlockY())
                .append('|').append(middleButton.getBlockZ())
                .append('|').append(facing.name());
        if (backColor != null) {
            sb.append('|').append(backColor & 0xFFFFFF);
        }
        return sb.toString();
    }

    /**
     * Deserialises a string produced by {@link #serializeTable}.
     * Returns {@code null} if the string is malformed (wrong field count, unparseable
     * numbers, unknown block face) or the world is not currently loaded.
     *
     * <p>Accepts both the legacy 9-field format (backColor absent, defaulting to {@code null})
     * and the current 10-field format (backColor present as a decimal RGB integer).
     *
     * Package-visible so tests can call it directly.
     */
    static TableEntry deserializeTable(String raw) {
        // Split with limit 10 to allow an optional 10th field.
        String[] parts = raw.split("\\|", 10);
        if (parts.length < 9) {
            return null;
        }
        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                return null; // world not loaded — skip quietly
            }
            double cx = Double.parseDouble(parts[1]);
            double cy = Double.parseDouble(parts[2]);
            double cz = Double.parseDouble(parts[3]);
            int bet = Integer.parseInt(parts[4]);
            int bx = Integer.parseInt(parts[5]);
            int by = Integer.parseInt(parts[6]);
            int bz = Integer.parseInt(parts[7]);
            BlockFace facing = BlockFace.valueOf(parts[8]);
            Integer backColor = (parts.length == 10 && !parts[9].isEmpty())
                    ? Integer.parseInt(parts[9])
                    : null;
            Location center = new Location(world, cx, cy, cz);
            Location middleButton = new Location(world, bx, by, bz);
            return new TableEntry(center, bet, middleButton, facing, backColor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- Chunk PDC access ----------------------------------------------------

    /**
     * Reads and deserialises every table entry currently persisted in {@code chunk}'s PDC,
     * skipping malformed entries (logged) and entries whose stored world no longer matches
     * {@code chunk.getWorld()}.
     */
    List<TableEntry> loadTablesForChunk(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> tables = pdc.getOrDefault(
                tablesKey, PersistentDataType.LIST.strings(), List.of());
        List<TableEntry> result = new ArrayList<>();
        for (String raw : tables) {
            TableEntry entry = deserializeTable(raw);
            if (entry == null) {
                plugin.getLogger().warning(
                        "Blackjack: malformed table entry in chunk PDC (skipping): " + raw);
                continue;
            }
            if (!entry.center().getWorld().equals(chunk.getWorld())) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    /** Appends {@code entry}'s serialized form to its chunk's PDC table list. */
    void persistTable(TableEntry entry) {
        Chunk chunk = entry.center().getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> existing = pdc.getOrDefault(tablesKey, PersistentDataType.LIST.strings(), List.of());
        List<String> updated = new ArrayList<>(existing);
        updated.add(serializeTable(entry.center(), entry.bet(), entry.middleButton(),
                entry.facing(), entry.backColor()));
        pdc.set(tablesKey, PersistentDataType.LIST.strings(), updated);
    }

    /** Removes {@code entry}'s serialized form from its chunk's PDC table list. */
    void unpersistTable(TableEntry entry) {
        Chunk chunk = entry.center().getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> existing = pdc.getOrDefault(tablesKey, PersistentDataType.LIST.strings(), List.of());
        String targetSerialized = serializeTable(entry.center(), entry.bet(), entry.middleButton(),
                entry.facing(), entry.backColor());
        List<String> updated = new ArrayList<>(existing);
        updated.remove(targetSerialized);
        pdc.set(tablesKey, PersistentDataType.LIST.strings(), updated);
    }

    // ---- Button geometry ----------------------------------------------------

    /**
     * Returns the block-coordinate offset of the LEFT button relative to the MIDDLE
     * button for the given button facing.
     *
     * <p>Convention: "left" and "right" are from the perspective of a player standing
     * in front of the wall, facing the same direction as the button's facing vector
     * (i.e. looking at the button head-on to press it).
     *
     * <ul>
     *   <li>Facing NORTH (+Z wall, player presses toward −Z): left = +X (east), right = −X (west)</li>
     *   <li>Facing SOUTH (−Z wall, player presses toward +Z): left = −X (west), right = +X (east)</li>
     *   <li>Facing EAST  (−X wall, player presses toward +X): left = +Z (south), right = −Z (north)</li>
     *   <li>Facing WEST  (+X wall, player presses toward −X): left = −Z (north), right = +Z (south)</li>
     * </ul>
     */
    private static int[] leftButtonOffset(BlockFace facing) {
        return switch (facing) {
            case NORTH -> new int[]{1, 0, 0};  // +X
            case SOUTH -> new int[]{-1, 0, 0}; // −X
            case EAST  -> new int[]{0, 0, 1};  // +Z
            case WEST  -> new int[]{0, 0, -1}; // −Z
            default    -> new int[]{1, 0, 0};  // fallback to +X
        };
    }

    /** Opposite of {@link #leftButtonOffset}. */
    private static int[] rightButtonOffset(BlockFace facing) {
        int[] l = leftButtonOffset(facing);
        return new int[]{-l[0], -l[1], -l[2]};
    }

    /** Returns the Location of the LEFT button for the given MIDDLE button + facing. */
    static Location leftButtonLoc(Location middle, BlockFace facing) {
        int[] off = leftButtonOffset(facing);
        return middle.clone().add(off[0], off[1], off[2]);
    }

    /** Returns the Location of the RIGHT button for the given MIDDLE button + facing. */
    static Location rightButtonLoc(Location middle, BlockFace facing) {
        int[] off = rightButtonOffset(facing);
        return middle.clone().add(off[0], off[1], off[2]);
    }

    /** Canonical block location string for use as a map key: {@code "world:x:y:z"}. */
    public static String blockKey(Location loc) {
        return loc.getWorld().getName()
                + ":" + loc.getBlockX()
                + ":" + loc.getBlockY()
                + ":" + loc.getBlockZ();
    }

    /** Register all three button locations for {@code entry} in the button index. */
    void registerButtons(TableEntry entry) {
        Location mid   = entry.middleButton();
        Location left  = leftButtonLoc(mid, entry.facing());
        Location right = rightButtonLoc(mid, entry.facing());
        buttonMap.put(blockKey(mid),   new ButtonRef(entry, ButtonRole.MIDDLE));
        buttonMap.put(blockKey(left),  new ButtonRef(entry, ButtonRole.LEFT));
        buttonMap.put(blockKey(right), new ButtonRef(entry, ButtonRole.RIGHT));
    }

    /** Unregister all three button locations for {@code entry} from the button index. */
    void unregisterButtons(TableEntry entry) {
        Location mid   = entry.middleButton();
        Location left  = leftButtonLoc(mid, entry.facing());
        Location right = rightButtonLoc(mid, entry.facing());
        buttonMap.remove(blockKey(mid));
        buttonMap.remove(blockKey(left));
        buttonMap.remove(blockKey(right));
    }

    /** Fast O(1) lookup of the table/role registered at block-location key {@code key}, or {@code null}. */
    ButtonRef lookupButton(String key) {
        return buttonMap.get(key);
    }

    /** Returns whether a persisted table already occupies the same canonical surface centre. */
    boolean hasTableAtCenter(Location center) {
        String target = blockKey(center);
        return loadTablesForChunk(center.getChunk()).stream()
                .anyMatch(entry -> target.equals(blockKey(entry.center())));
    }

    /** Clears the entire button index. Called from {@link BlackjackListener#shutdown()}. */
    void clearButtons() {
        buttonMap.clear();
    }

    // ---- Table centre geometry (solid block detection) ----------------------

    /**
     * Given the MIDDLE button block and its facing direction, computes the centre of
     * the solid 2×3 block footprint that the button oversees. Returns {@code null} if
     * no valid footprint is found.
     *
     * <p>Physical layout: the button is wall-mounted on the SIDE of a support block.
     * A wall button shares its support block's Y — the support blocks form the table
     * surface and their top face (Y + 1.0) is where cards rest. The button faces AWAY
     * from the support block, so the support column lies in the direction OPPOSITE the
     * facing. We anchor the search on the support block directly behind the button.
     *
     * <p>The brute-force over (dx,dz) anchor offsets finds any fully-solid w×d rectangle
     * that contains the anchor cell, regardless of which corner the anchor occupies, so
     * the exact table orientation does not need to be known up front.
     */
    public static Location findTableCenterFromButton(Block button, BlockFace facing) {
        World world = button.getWorld();
        // Support blocks are at the button's own Y (wall buttons share the support block's Y).
        int supportY = button.getY();

        // The support block is opposite the button's facing (the button points away from it).
        int startX = button.getX() - facing.getModX();
        int startZ = button.getZ() - facing.getModZ();

        // Try both 2×3 and 3×2 orientations.
        int[][] dims = {{TABLE_WIDTH, TABLE_DEPTH}, {TABLE_DEPTH, TABLE_WIDTH}};
        for (int[] dim : dims) {
            int w = dim[0]; // width along X
            int d = dim[1]; // depth along Z
            for (int dx = 0; dx < w; dx++) {
                for (int dz = 0; dz < d; dz++) {
                    int minX = startX - dx;
                    int minZ = startZ - dz;
                    if (isValidTableRect(world, supportY, minX, minZ, w, d)) {
                        double cx = minX + w / 2.0;
                        double cz = minZ + d / 2.0;
                        // Table surface = top face of the support blocks.
                        double cy = supportY + 1.0;
                        return new Location(world, cx, cy, cz);
                    }
                }
            }
        }
        return null;
    }

    /** Returns true when every block in the w×d rectangle at height {@code y} is a solid block. */
    public static boolean isValidTableRect(World world, int y, int minX, int minZ, int w, int d) {
        for (int x = minX; x < minX + w; x++) {
            for (int z = minZ; z < minZ + d; z++) {
                if (!world.getBlockAt(x, y, z).getType().isSolid()) {
                    return false;
                }
            }
        }
        return true;
    }
}
