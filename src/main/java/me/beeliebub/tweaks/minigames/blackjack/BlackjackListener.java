package me.beeliebub.tweaks.minigames.blackjack;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.minigames.cards.Card;
import me.beeliebub.tweaks.minigames.cards.CardItemFactory;
import me.beeliebub.tweaks.ranks.RankManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


/**
 * Owns active {@link BlackjackGame} sessions, their rendered {@link ItemDisplay}
 * card layouts, all player interaction during play, and the lifecycle of table
 * holograms for physical Blackjack tables stored in chunk PDC.
 *
 * <h2>Physical Tables</h2>
 * Tables are placed by admins via {@link BlackjackCommand} using button-linked setup
 * mode. Each table is anchored to a horizontal row of three wall buttons:
 * <ul>
 *   <li>LEFT  — hit action</li>
 *   <li>MIDDLE — start game / clear board</li>
 *   <li>RIGHT — stand action</li>
 * </ul>
 * The "left" and "right" buttons are determined from the perspective of a player
 * standing in front of the wall pressing the button (i.e. facing the same direction
 * as the button's facing vector). See {@link #leftButtonOffset} and
 * {@link #rightButtonOffset} for the per-facing convention.
 *
 * <p>Tables are stored in chunk PDC under {@code tweaks:blackjack_tables}. On chunk
 * load a {@link TextDisplay} hologram is spawned above each table center. There is no
 * longer any {@code Interaction} entity.
 *
 * <h2>Card Rendering</h2>
 * Cards are rendered as {@link ItemDisplay} entities lying flat on the table surface
 * (rotated −90° about the X-axis via {@code new Quaternionf().rotateX(-PI/2)}).
 * Card centers are anchored at the support-block top face ({@code supportY + 1.0}).
 * Each hand (dealer and player) is centred independently on the table's Z axis.
 * Dealer hand sits at positive Z offset; player hand at negative Z offset.
 *
 * <h2>Controls</h2>
 * All actions are delivered through {@link PlayerInteractEvent} RIGHT_CLICK_BLOCK on
 * one of the three buttons:
 * <ul>
 *   <li>MIDDLE — start game (or clear finished board immediately)</li>
 *   <li>LEFT   — hit</li>
 *   <li>RIGHT  — stand</li>
 * </ul>
 *
 * <h2>Cleanup contract</h2>
 * Every spawned display entity is tracked and removed on every exit path — natural
 * end (with delayed auto-clear), {@link PlayerQuitEvent}, {@link ChunkUnloadEvent},
 * and server shutdown via {@link #shutdown()}.
 */
@SuppressWarnings("UnstableApiUsage") // Paper's Dialog API is @ApiStatus.Experimental in 26.1.2.
public final class BlackjackListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Spacing between card centres, in blocks. */
    private static final float CARD_SPACING = 0.45f;

    /**
     * Z offset (in blocks) from table centre for dealer's hand row.
     * Dealer sits at −Z (the wall side), player at +Z (the player side). In world
     * space the button faces toward the player, so the player side is +Z here.
     */
    private static final double DEALER_Z_OFFSET = -0.55;
    private static final double PLAYER_Z_OFFSET = 0.55;

    /**
     * Vertical offset (in blocks) from the table surface at which card centers are anchored.
     * The anchor sits at the top face of the support blocks ({@code supportY + 1.0}).
     * Empirically the ItemDisplay FIXED transform renders the model at the entity location
     */
    private static final double TABLE_CARD_HEIGHT = 0.0625;

    /**
     * Height above the table centre (support-block top face) at which the hologram text appears.
     */
    private static final double HOLOGRAM_HEIGHT = 1.8;

    /**
     * Width of the required solid block rectangle / table footprint (shorter axis). */
    private static final int TABLE_WIDTH = 2;
    /** Depth of the required solid block rectangle / table footprint (longer axis). */
    private static final int TABLE_DEPTH = 3;

    /** PDC key sub-name for the server-table store. Full key: {@code tweaks:blackjack_tables}. */
    private static final String TABLES_KEY_NAME = "blackjack_tables";

    /**
     * PDC tag key placed on each session {@link ItemDisplay} card entity so
     * hit-detection can identify it as a session card. Not needed for gameplay in the
     * new button model, but kept so future tooling can identify floating cards.
     */
    private static final String CARD_DISPLAY_KEY_NAME = "blackjack_card";

    /**
     * PDC tag key placed on each session {@link ItemDisplay} card entity storing the
     * owning player's UUID as a string.
     */
    private static final String CARD_OWNER_KEY_NAME = "blackjack_card_owner";

    /**
     * Auto-clear delay after a game finishes, in ticks (600 = 30 seconds).
     * Players can bypass it by pressing the MIDDLE button immediately.
     */
    private static final long AUTO_CLEAR_TICKS = 600L;

    /** Player name whose skin the dealer mannequin wears at game end. */
    private static final String DEALER_PROFILE_NAME = "LimeLush";

    /**
     * How long a mid-hand game may be idle before the sweeper forcibly ends it.
     * 10 minutes expressed in milliseconds.
     */
    public static final long INACTIVITY_TIMEOUT_MS = 10L * 60L * 1000L;

    /**
     * How often the inactivity sweeper runs, in ticks (1200 = 60 seconds).
     */
    private static final long SWEEP_PERIOD_TICKS = 1200L;

    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final RankManager rankManager;

    private NamespacedKey tablesKey;
    private NamespacedKey cardDisplayKey;
    private NamespacedKey cardOwnerKey;

    /** Carries the pending bet and optional back-color for a server table being set up. */
    private record PendingSetup(int bet, Integer backColor) {}

    /** Players waiting to finalise a server table by right-clicking the MIDDLE button. */
    private final Map<UUID, PendingSetup> pendingSetups = new HashMap<>();

    /** Players waiting to remove a table by right-clicking its MIDDLE button. */
    private final Set<UUID> pendingRemovals = new HashSet<>();

    /** Per-player active game session. At most one game per player at a time. */
    private final Map<UUID, Session> sessions = new HashMap<>();

    /**
     * Hologram TextDisplay UUIDs per chunk, keyed by composite world+chunk key.
     * Each registered table contributes one TextDisplay UUID.
     */
    private final Map<ChunkId, List<UUID>> tableEntities = new HashMap<>();

    /**
     * Fast O(1) button-to-server-table lookup. Keyed by block location string
     * ({@code "world:x:y:z"}). Each entry stores the table and whether the button
     * is the left, middle, or right control.
     */
    private final Map<String, ButtonRef> buttonMap = new HashMap<>();

    /**
     * Task id of the repeating inactivity-sweep scheduler, or {@code -1} when not yet
     * scheduled (e.g. during unit tests that skip the constructor scheduling path).
     */
    private int sweepTaskId = -1;

    // ---- Constructor --------------------------------------------------------

    public BlackjackListener(JavaPlugin plugin, EconomyManager economyManager,
                             RankManager rankManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.rankManager = rankManager;
        tablesKey      = new NamespacedKey(plugin, TABLES_KEY_NAME);
        cardDisplayKey = new NamespacedKey(plugin, CARD_DISPLAY_KEY_NAME);
        cardOwnerKey   = new NamespacedKey(plugin, CARD_OWNER_KEY_NAME);

        // Schedule the inactivity sweeper to run every 60 seconds (1200 ticks).
        sweepTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin,
                        () -> sweepInactiveSessions(System.currentTimeMillis()),
                        SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS)
                .getTaskId();
    }

    // ---- Inner types --------------------------------------------------------

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
    private record ButtonRef(TableEntry table, ButtonRole role) {}

    /** Holds a running game plus the entities used to render it. */
    private static final class Session {
        final BlackjackGame game;
        /** UUIDs of all spawned ItemDisplay card entities for this session. */
        final List<UUID> displayIds = new ArrayList<>();
        final World world;
        final long chunkKey;
        /** Table centre location (support-block top face). */
        final Location tableCenter;
        /**
         * The direction the player-side buttons face (away from the wall). Used to orient
         * card renders correctly for all four cardinal table alignments.
         */
        final BlockFace facing;
        /**
         * Optional card-back tint color (RGB integer) sourced from the table's PDC entry.
         * {@code null} means "use the pack default back".
         */
        final Integer backColor;

        /**
         * True once the game is finished and we are waiting for the auto-clear timeout.
         * A middle-button press while this flag is set cancels the timer immediately.
         */
        boolean waitingToClear = false;

        /**
         * Scheduled auto-clear task id, or -1 if none is scheduled.
         * Stored so the task can be cancelled on early clear, quit, or chunk unload.
         */
        int autoClearTaskId = -1;

        /**
         * UUID of the dealer Mannequin spawned at game end, or {@code null} if none is
         * currently alive. Tracked so every cleanup path can remove the entity even if
         * the scheduled delayed removal has not yet fired.
         */
        UUID dealerMannequinId = null;

        Session(BlackjackGame game, World world, long chunkKey, Location tableCenter,
                BlockFace facing, Integer backColor) {
            this.game = game;
            this.world = world;
            this.chunkKey = chunkKey;
            this.tableCenter = tableCenter;
            this.facing = facing;
            this.backColor = backColor;
        }
    }

    /**
     * Composite key combining world UID and chunk key so that two chunks in different
     * worlds with the same numeric chunk key are never confused.
     */
    private record ChunkId(UUID worldId, long chunkKey) {}

    private static ChunkId chunkId(Chunk chunk) {
        return new ChunkId(chunk.getWorld().getUID(), chunk.getChunkKey());
    }

    // ---- Public API ---------------------------------------------------------

    /**
     * Returns the live server-table button lookup map.
     * Exposed for test assertions.
     */
    public Map<String, ButtonRef> buttonMap() {
        return buttonMap;
    }

    /** True if the player already has a game running. */
    public boolean hasActiveGame(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /**
     * Returns the {@link BlackjackGame} for the given player if a session is active,
     * or {@code null} when no session exists. Exposed for test clock-injection via
     * {@link BlackjackGame#touchInteraction(long)} so the inactivity sweeper can be
     * exercised deterministically.
     */
    public BlackjackGame getActiveGame(UUID playerId) {
        Session s = sessions.get(playerId);
        return s != null ? s.game : null;
    }

    /**
     * Register a minimal session containing {@code game} for {@code playerId} without
     * spawning any card-display entities. Intended exclusively for unit tests so the
     * inactivity sweeper can be exercised without a running game server.
     *
     * <p>The session's {@code world} field is set to {@code null} and the display-ID
     * list is empty, which is safe for the sweeper path (it only calls
     * {@link #removeDisplays} and {@link #cancelAutoClear}, neither of which requires
     * a non-null world or a non-empty display list).
     *
     * <p>Do NOT call this from production code paths.
     *
     * @param playerId the player UUID whose session to register
     * @param game     a pre-constructed game whose {@code lastInteractionTime} can be
     *                 manipulated via {@link BlackjackGame#touchInteraction(long)}
     * @param center   table centre location used to anchor the session's chunk key
     */
    public void registerGameForTesting(UUID playerId, BlackjackGame game, Location center) {
        Chunk anchorChunk = center.getChunk();
        World world = center.getWorld();
        // Testing: use SOUTH as the default facing; inactivity sweeper doesn't need geometry.
        Session session = new Session(game, world, anchorChunk.getChunkKey(), center,
                BlockFace.SOUTH, null);
        sessions.put(playerId, session);
    }

    /**
     * Put {@code player} into table-setup mode for the given {@code bet} and optional
     * card-back color. The next right-click on a wall button finalises the table:
     * computes the centre from the button's solid block footprint, persists to chunk PDC,
     * and spawns a hologram.
     *
     * @param player    the admin initiating setup
     * @param bet       the required bet amount for the table
     * @param backColor optional RGB integer for the card-back tint, or {@code null} for default
     */
    public void beginTableSetup(Player player, int bet, Integer backColor) {
        pendingSetups.put(player.getUniqueId(), new PendingSetup(bet, backColor));
        player.sendMessage(MM.deserialize(
                "<yellow>Right-click the <white>MIDDLE</white> control button of the table to register it. "
                        + "Bet: <white>$" + bet + "</white></yellow>"));
    }

    /**
     * Put {@code player} into table-removal mode.
     * The next right-click on a registered table's MIDDLE button unregisters that
     * table and removes its hologram.
     */
    public void beginTableRemoval(Player player) {
        pendingRemovals.add(player.getUniqueId());
        player.sendMessage(MM.deserialize(
                "<yellow>Right-click the <white>MIDDLE</white> button of the server table you want to remove.</yellow>"));
    }

    /**
     * Spawn a {@link TextDisplay} hologram above the given table centre and register
     * the table's three button locations in {@link #buttonMap} for O(1) click lookup.
     * Called both from {@link ChunkLoadEvent} (batch restore) and from
     * {@link BlackjackCommand} when a new table is placed while the chunk is loaded.
     */
    public void spawnTableHologram(TableEntry entry) {
        Location center = entry.center();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        Location hologLoc = center.clone().add(0, HOLOGRAM_HEIGHT, 0);
        TextDisplay text = (TextDisplay) world.spawnEntity(hologLoc, EntityType.TEXT_DISPLAY);
        String betLabel = entry.bet() == 0 ? "FREE" : ("$" + entry.bet());
        text.text(MM.deserialize(
                "<gold><bold>Blackjack Table</bold></gold>\n"
                        + "<gray>Press MIDDLE to Play</gray>\n"
                        + "<yellow>Bet: " + betLabel + "</yellow>"));
        text.setBillboard(Display.Billboard.CENTER);
        text.setPersistent(false);
        text.setDefaultBackground(false);
        text.setShadowed(true);

        ChunkId cid = chunkId(center.getChunk());
        tableEntities.computeIfAbsent(cid, k -> new ArrayList<>()).add(text.getUniqueId());

        // Register the three button locations for fast O(1) click lookup.
        registerButtonsForTable(entry);
    }

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

    /**
     * Returns the Location of the LEFT button for the given MIDDLE button + facing.
     */
    static Location leftButtonLoc(Location middle, BlockFace facing) {
        int[] off = leftButtonOffset(facing);
        return middle.clone().add(off[0], off[1], off[2]);
    }

    /**
     * Returns the Location of the RIGHT button for the given MIDDLE button + facing.
     */
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

    /** Register all three button locations for {@code entry} in {@link #buttonMap}. */
    private void registerButtonsForTable(TableEntry entry) {
        Location mid   = entry.middleButton();
        Location left  = leftButtonLoc(mid, entry.facing());
        Location right = rightButtonLoc(mid, entry.facing());
        buttonMap.put(blockKey(mid),   new ButtonRef(entry, ButtonRole.MIDDLE));
        buttonMap.put(blockKey(left),  new ButtonRef(entry, ButtonRole.LEFT));
        buttonMap.put(blockKey(right), new ButtonRef(entry, ButtonRole.RIGHT));
    }

    /** Unregister all three button locations for {@code entry} from {@link #buttonMap}. */
    private void unregisterButtonsForTable(TableEntry entry) {
        Location mid   = entry.middleButton();
        Location left  = leftButtonLoc(mid, entry.facing());
        Location right = rightButtonLoc(mid, entry.facing());
        buttonMap.remove(blockKey(mid));
        buttonMap.remove(blockKey(left));
        buttonMap.remove(blockKey(right));
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

    // ---- Game session start (internal) ---------------------------------------

    /**
     * Start a new table game for {@code player} with {@code bet}, rendered on the surface
     * centred at {@code tableCenter}. The bet must already have been deducted by the caller.
     * Returns {@code true} on success; on failure returns {@code false} and the caller MUST
     * refund the bet.
     *
     * @param facing    the direction the player-side buttons face (used for card orientation)
     * @param backColor optional RGB integer for the card-back tint; {@code null} for pack default
     */
    public boolean startGame(Player player, int bet, Location tableCenter,
                             BlockFace facing, Integer backColor) {
        World world = player.getWorld();
        if (world == null) {
            return false;
        }

        BlackjackGame game = new BlackjackGame(player.getUniqueId(), bet);
        game.dealInitial();

        Chunk anchorChunk = tableCenter.getChunk();
        Session session = new Session(game, world, anchorChunk.getChunkKey(), tableCenter,
                facing, backColor);

        // Spawn the dealer mannequin at game start so it is present for the whole round.
        Location dealerLoc = dealerLocation(tableCenter, facing);
        try {
            Mannequin dealer = (Mannequin) world.spawnEntity(dealerLoc, EntityType.MANNEQUIN);
            dealer.setProfile(ResolvableProfile.resolvableProfile().name(DEALER_PROFILE_NAME).build());
            dealer.getEquipment().clear();
            dealer.setPersistent(false);
            session.dealerMannequinId = dealer.getUniqueId();
        } catch (RuntimeException ex) {
            plugin.getLogger().warning(
                    "Blackjack: failed to spawn dealer mannequin at game start: " + ex.getMessage());
            // Non-fatal: game continues without mannequin.
        }

        try {
            renderOnTable(session, tableCenter);
        } catch (RuntimeException ex) {
            removeDisplays(session);
            plugin.getLogger().warning(
                    "Blackjack table render failed for " + player.getName() + ": " + ex.getMessage());
            return false;
        }

        sessions.put(player.getUniqueId(), session);

        if (game.isFinished()) {
            finish(player, session);
        } else {
            player.sendMessage(MM.deserialize(
                    "<gold>Blackjack!</gold> <gray>Your hand:</gray> <yellow>" + game.playerValue()
                            + "</yellow> <gray>| Dealer shows:</gray> <yellow>"
                            + game.dealerHand().getFirst().rank().key() + "</yellow>"));
            player.sendMessage(MM.deserialize(
                    "<gray>LEFT button = Hit  |  RIGHT button = Stand  |  MIDDLE button = Clear</gray>"));
        }
        return true;
    }

    // ---- Hologram removal ---------------------------------------------------

    /** Remove all hologram entities for the given chunk and unregister their buttons. */
    private void removeTableHolograms(ChunkId cid, List<TableEntry> entries) {
        List<UUID> ids = tableEntities.remove(cid);
        if (ids != null) {
            for (UUID id : ids) {
                var entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
        if (entries != null) {
            for (TableEntry entry : entries) {
                unregisterButtonsForTable(entry);
            }
        }
    }

    // ---- Chunk load / unload ------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();

        // --- Load server tables ---
        List<String> tables = pdc.getOrDefault(
                tablesKey, PersistentDataType.LIST.strings(), List.of());
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
            spawnTableHologram(entry);
        }

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        ChunkId cid = chunkId(chunk);

        // Collect the table entries for this chunk so we can unregister their buttons.
        List<TableEntry> entries = collectEntriesForChunk(chunk);

        removeTableHolograms(cid, entries);

        // Cancel and remove any active game sessions whose grid lives in this chunk.
        long key = chunk.getChunkKey();
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Session session = sessions.get(id);
            if (session != null && session.chunkKey == key
                    && session.world.equals(event.getWorld())) {
                cancelAutoClear(session);
                removeDisplays(session);
                sessions.remove(id);
            }
        }

    }

    /**
     * Reads the chunk PDC to collect all deserializable TableEntry objects for the given chunk.
     * Used so we can unregister button map entries on chunk unload.
     */
    private List<TableEntry> collectEntriesForChunk(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> tables = pdc.getOrDefault(
                tablesKey, PersistentDataType.LIST.strings(), List.of());
        List<TableEntry> result = new ArrayList<>();
        for (String raw : tables) {
            TableEntry entry = deserializeTable(raw);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    // ---- Main interaction handler -------------------------------------------

    /**
     * Unified button-click handler. Handles setup/removal modes first (highest priority),
     * then routes gameplay actions through LEFT/MIDDLE/RIGHT button roles.
     *
     * <p>Early returns are ordered from cheapest to most expensive:
     * <ol>
     *   <li>Action must be RIGHT_CLICK_BLOCK</li>
     *   <li>Clicked block must exist</li>
     *   <li>Clicked block must be tagged as a button (Tag.BUTTONS)</li>
     *   <li>Block location must be in the buttonMap</li>
     * </ol>
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onButtonClick(PlayerInteractEvent event) {
        // 1. Must be a right-click on a block.
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        // 2. Must be a button (cheap material tag check before any map lookup).
        if (!Tag.BUTTONS.isTagged(clicked.getType())) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String key = blockKey(clicked.getLocation());

        // ---- Server-table setup mode takes precedence -----------------------
        if (pendingSetups.containsKey(playerId)) {
            event.setCancelled(true);
            handleSetup(player, clicked);
            return;
        }

        // ---- Removal mode takes precedence ----------------------------------
        if (pendingRemovals.contains(playerId)) {
            // Only a known MIDDLE button triggers removal.
            ButtonRef ref = buttonMap.get(key);
            if (ref != null && ref.role() == ButtonRole.MIDDLE) {
                event.setCancelled(true);
                handleRemoval(player, ref.table());
            }
            return;
        }

        // 3. Must be a registered blackjack button for gameplay.
        ButtonRef ref = buttonMap.get(key);
        if (ref == null) {
            return;
        }

        event.setCancelled(true);
        TableEntry table = ref.table();
        Location tableCenter = table.center();

        Session session = sessions.get(playerId);

        switch (ref.role()) {
            case MIDDLE -> {
                if (session != null && session.waitingToClear) {
                    // Clear the finished board immediately.
                    cancelAutoClear(session);
                    removeDisplays(session);
                    sessions.remove(playerId);
                } else if (session == null) {
                    // Start a new game: economy flow (skipped for free/practice tables).
                    int bet = table.bet();
                    if (bet > 0) {
                        double balance = economyManager.getBalance(playerId);
                        if (balance < bet) {
                            player.sendMessage(MM.deserialize(
                                    "<red>You cannot afford a bet of $" + bet
                                            + ". Your balance is $" + (long) balance + ".</red>"));
                            return;
                        }
                        economyManager.removeBalance(playerId, bet);
                    }
                    boolean started = startGame(player, bet, tableCenter,
                            table.facing(), table.backColor());
                    if (!started) {
                        if (bet > 0) economyManager.addBalance(playerId, bet);
                        player.sendMessage(MM.deserialize(
                                "<red>Could not start a Blackjack game here."
                                        + (bet > 0 ? " Your bet was refunded." : "") + "</red>"));
                    }
                } else {
                    // Session is active but not finished — inform the player.
                    player.sendMessage(MM.deserialize(
                            "<red>Your game is still in progress. Use the LEFT or RIGHT buttons.</red>"));
                }
            }
            case LEFT -> {
                if (session == null || session.game.isFinished()) {
                    return;
                }
                session.game.playerHit();
                afterAction(player, session, "<green>Hit!</green> <gray>Hand:</gray> <yellow>"
                        + session.game.playerValue() + "</yellow>");
            }
            case RIGHT -> {
                if (session == null || session.game.isFinished()) {
                    return;
                }
                session.game.playerStand();
                afterAction(player, session, "<red>Stand.</red>");
            }
        }
    }

    // ---- Setup handler ------------------------------------------------------

    private void handleSetup(Player player, Block button) {
        PendingSetup pending = pendingSetups.remove(player.getUniqueId());
        int bet = pending.bet();
        Integer backColor = pending.backColor();

        // Get the button's facing direction from its block data.
        if (!(button.getBlockData() instanceof Switch sw)) {
            player.sendMessage(MM.deserialize(
                    "<red>That doesn't look like a wall button. Please right-click a wall-mounted button.</red>"));
            pendingSetups.put(player.getUniqueId(), pending); // restore pending state
            return;
        }

        BlockFace facing = sw.getFacing();

        // Validate that the button is wall-mounted (not on floor/ceiling).
        if (facing == BlockFace.UP || facing == BlockFace.DOWN) {
            player.sendMessage(MM.deserialize(
                    "<red>Please use a wall-mounted button (not floor or ceiling).</red>"));
            pendingSetups.put(player.getUniqueId(), pending);
            return;
        }

        // Compute the table surface centre from the button location and facing.
        Location center = findTableCenterFromButton(button, facing);
        if (center == null) {
            player.sendMessage(MM.deserialize(
                    "<red>No valid 2x3 block area found for this button. "
                            + "Ensure a solid 2-wide by 3-deep block area sits beneath the three control buttons.</red>"));
            return;
        }

        // Persist the table to the centre block's chunk PDC.
        Location middleButtonLoc = button.getLocation();
        TableEntry entry = new TableEntry(center, bet, middleButtonLoc, facing, backColor);
        String serialized = serializeTable(center, bet, middleButtonLoc, facing, backColor);

        Chunk chunk = center.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> existing = pdc.getOrDefault(tablesKey, PersistentDataType.LIST.strings(), List.of());
        List<String> updated = new ArrayList<>(existing);
        updated.add(serialized);
        pdc.set(tablesKey, PersistentDataType.LIST.strings(), updated);

        // Spawn the hologram immediately (chunk is loaded — we're standing in it).
        spawnTableHologram(entry);

        player.sendMessage(MM.deserialize(
                "<green>Blackjack table registered!</green> "
                        + "<gray>Center:</gray> <yellow>"
                        + String.format("%.1f, %.1f, %.1f", center.x(), center.y(), center.z())
                        + "</yellow> <gray>| Bet:</gray> <yellow>$" + bet + "</yellow>"));
    }

    // ---- Removal handler ----------------------------------------------------

    private void handleRemoval(Player player, TableEntry table) {
        pendingRemovals.remove(player.getUniqueId());

        Location center = table.center();
        Chunk chunk = center.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();

        List<String> existing = pdc.getOrDefault(tablesKey, PersistentDataType.LIST.strings(), List.of());
        String targetSerialized = serializeTable(center, table.bet(), table.middleButton(),
                table.facing(), table.backColor());
        List<String> updated = new ArrayList<>(existing);
        updated.remove(targetSerialized);
        pdc.set(tablesKey, PersistentDataType.LIST.strings(), updated);

        // Remove the hologram entity for this specific table.
        ChunkId cid = chunkId(chunk);
        // The tableEntities list may contain holograms for multiple tables in the same chunk.
        // We track per-table holograms by removing the associated entity by proximity rather
        // than by per-table UUID list, because multiple tables per chunk share the same List<UUID>.
        // Strategy: find and remove the TextDisplay nearest to the expected hologram location.
        removeHologramNear(center.clone().add(0, HOLOGRAM_HEIGHT, 0), cid);

        // Unregister buttons.
        unregisterButtonsForTable(table);

        player.sendMessage(MM.deserialize(
                "<green>Blackjack table removed.</green>"));
    }

    /**
     * Finds and removes the TextDisplay hologram entity nearest to {@code target}
     * from the {@link #tableEntities} list for {@code cid}. If no entities remain
     * in the list for that chunk after removal, the list is cleaned up.
     */
    private void removeHologramNear(Location target, ChunkId cid) {
        List<UUID> ids = tableEntities.get(cid);
        if (ids == null) {
            return;
        }
        UUID best = null;
        double bestDist = Double.MAX_VALUE;
        for (UUID id : ids) {
            var entity = Bukkit.getEntity(id);
            if (entity instanceof TextDisplay td) {
                double dist = td.getLocation().distanceSquared(target);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = id;
                }
            }
        }
        if (best != null) {
            var entity = Bukkit.getEntity(best);
            if (entity != null) {
                entity.remove();
            }
            ids.remove(best);
        }
        if (ids.isEmpty()) {
            tableEntities.remove(cid);
        }
    }

    /**
     * Flat-on-table rotation that reads upright from the seat in direction {@code facing}.
     *
     * <p>Derivation: {@code rotateX(-π/2)} (applied first, since JOML post-multiplies) lays
     * the card flat and maps its top edge (local +Y) to −Z. The {@code rotateY(yaw)} term then
     * rotates that top edge onto the −{@code facing} direction — the far side of the card from
     * a player seated in the +{@code facing} direction — so the card reads upright from that seat.
     * <ul>
     *   <li>SOUTH (seat +Z, reads toward −Z): top edge → −Z, yaw = 0.</li>
     *   <li>NORTH (seat −Z, reads toward +Z): top edge → +Z, yaw = π.</li>
     *   <li>EAST  (seat +X, reads toward −X): top edge → −X, yaw = +π/2.</li>
     *   <li>WEST  (seat −X, reads toward +X): top edge → +X, yaw = −π/2.</li>
     * </ul>
     * EAST = +π/2 and WEST = −π/2 are the deterministic complements; together with the
     * NORTH/SOUTH pair they span the full 360°. (These two were previously swapped, which
     * rendered E/W-table cards upside-down — see Tweaks-940b.)
     */
    public static Quaternionf flatCardRotationForFacing(BlockFace facing) {
        float yaw = switch (facing) {
            case SOUTH -> 0f;
            case NORTH -> (float) Math.PI;
            case EAST  -> (float) (Math.PI / 2);   // top edge → −X, upright for the +X seat
            case WEST  -> (float) (-Math.PI / 2);  // top edge → +X, upright for the −X seat
            default    -> 0f;
        };
        return new Quaternionf().rotateY(yaw).rotateX((float) (-Math.PI / 2));
    }

    // ---- Dealer Mannequin geometry -----------------------------------------

    /**
     * Returns the world location where the dealer Mannequin should stand for the given
     * table centre and table orientation.
     *
     * <p>The dealer stands on the block opposite the player's middle button. The offset
     * distance is {@code PLAYER_Z_OFFSET + 1.0 = 1.55} blocks along
     * {@code facing.getOppositeFace()}, placing the mannequin clear of the dealer
     * hand row so it lands on the ground rather than on top of the cards. Y is unchanged
     * (table surface level).
     *
     * <p>The mannequin yaw is set so it looks toward {@code facing} (i.e., toward the
     * player seat): SOUTH→0, NORTH→180, EAST→−90, WEST→90.
     *
     * @param tableCenter support-block top-face centre (same coordinate used by the renderer)
     * @param facing      the direction the player-side buttons face; determines which side
     *                    is the dealer side and which way the dealer looks
     * @return a cloned location at the dealer's standing spot, facing the player
     */
    public static Location dealerLocation(Location tableCenter, BlockFace facing) {
        BlockFace dealerDir = facing.getOppositeFace();
        double dist = PLAYER_Z_OFFSET + 1.0; // 1.55
        Location loc = tableCenter.clone();
        loc.add(dealerDir.getModX() * dist, 0, dealerDir.getModZ() * dist);
        float yaw = switch (facing) {
            case SOUTH  ->    0f;
            case NORTH  ->  180f;
            case EAST   ->  -90f;
            case WEST   ->   90f;
            default     ->    0f;
        };
        loc.setYaw(yaw);
        loc.setPitch(0f);
        return loc;
    }

    // ---- Settlement (finish) ------------------------------------------------

    private void afterAction(Player player, Session session, String message) {
        player.sendMessage(MM.deserialize(message));
        if (session.game.isFinished()) {
            finish(player, session);
        } else {
            redraw(session);
        }
    }

    /**
     * Settle the game, credit the payout, display the outcome message, and schedule
     * an auto-clear of card displays after {@link #AUTO_CLEAR_TICKS} ticks.
     * The player can cancel the timer early by pressing the MIDDLE button.
     */
    private void finish(Player player, Session session) {
        BlackjackGame game = session.game;
        boolean freeTurn = game.bet() == 0;

        if (!freeTurn) {
            int payout = game.payout();
            if (payout > 0) {
                economyManager.addBalance(player.getUniqueId(), payout);
            }
        }

        // Rakeback on dealer win — skipped for free/practice tables.
        String rakebackSuffix = "";
        if (!freeTurn && game.result() == BlackjackGame.Result.DEALER_WIN) {
            double rate = rankManager.getCasinoRakebackRate(player.getUniqueId());
            if (rate > 0.0) {
                int rakeback = (int) Math.floor(game.bet() * rate);
                if (rakeback > 0) {
                    economyManager.addBalance(player.getUniqueId(), rakeback);
                    rakebackSuffix = " <gray>(Rakeback: +$" + rakeback + ")</gray>";
                }
            }
        }

        String practiceNote = freeTurn ? " <gray>(Practice table — no stakes)</gray>" : "";
        int payout = freeTurn ? 0 : game.payout();
        String summary = switch (game.result()) {
            case PLAYER_BLACKJACK -> "<gold><bold>BLACKJACK!</bold></gold>" + (freeTurn
                    ? " <green>You win!</green>" + practiceNote
                    : " <green>You won $" + (payout - game.bet()) + "!</green>");
            case PLAYER_WIN -> "<green>You win!</green>" + (freeTurn
                    ? practiceNote
                    : " <green>Payout: $" + payout + " (net +$" + game.bet() + ")</green>");
            case PUSH -> "<yellow>Push.</yellow>" + (freeTurn
                    ? " <yellow>Tie game.</yellow>" + practiceNote
                    : " <yellow>Your bet of $" + game.bet() + " is returned.</yellow>");
            case DEALER_WIN -> "<red>Dealer wins.</red>" + (freeTurn
                    ? practiceNote
                    : " <red>You lost $" + game.bet() + ".</red>" + rakebackSuffix);
        };
        player.sendMessage(MM.deserialize(summary));
        player.sendMessage(MM.deserialize(
                "<gray>You:</gray> <yellow>" + game.playerValue()
                        + "</yellow> <gray>vs Dealer:</gray> <yellow>" + game.dealerValue() + "</yellow>"));
        player.sendMessage(MM.deserialize(
                "<gray>Press MIDDLE to clear the board.</gray>"));

        // Re-render so dealer's hole card is revealed.
        redraw(session);

        // Resolve the dealer mannequin reaction against the already-spawned mannequin.
        resolveDealerMannequin(session, game.result());

        // Schedule auto-clear; store the task id so it can be cancelled early.
        session.waitingToClear = true;
        UUID playerId = player.getUniqueId();
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Guard: session may have been replaced or removed by the time this fires.
            Session current = sessions.get(playerId);
            if (current == session && session.waitingToClear) {
                removeDisplays(session);
                sessions.remove(playerId);
            }
        }, AUTO_CLEAR_TICKS).getTaskId();
        session.autoClearTaskId = taskId;
    }

    /**
     * Resolve the outcome-based reaction on the dealer {@link Mannequin} that was spawned
     * at game start. Looks up the existing mannequin via {@link Session#dealerMannequinId};
     * if it is no longer alive the method returns silently (no double-spawn).
     *
     * <ul>
     *   <li>{@code PLAYER_WIN} or {@code PLAYER_BLACKJACK} — the dealer "dies":
     *       a hurt animation plus {@code setHealth(0)} (death animation). The entity is
     *       removed by the death; {@link #removeDisplays(Session)} acts as a backstop.</li>
     *   <li>{@code DEALER_WIN} or {@code PUSH} — plays {@link Sound#ENTITY_VILLAGER_YES} at
     *       the table centre, then removes the mannequin.</li>
     * </ul>
     *
     * The mannequin's UUID is always cleared from the session after this call so
     * {@link #removeDisplays(Session)} cannot double-remove it on subsequent cleanup paths.
     */
    private void resolveDealerMannequin(Session session, BlackjackGame.Result result) {
        if (session.dealerMannequinId == null) {
            return;
        }
        var entity = Bukkit.getEntity(session.dealerMannequinId);
        session.dealerMannequinId = null;
        if (!(entity instanceof Mannequin dealer)) {
            return;
        }

        switch (result) {
            case PLAYER_WIN, PLAYER_BLACKJACK -> {
                // Player wins — dealer dies.
                dealer.playHurtAnimation(0f);
                dealer.setHealth(0.0);
            }
            case DEALER_WIN, PUSH -> {
                // Dealer wins or push — play a happy sound then remove the mannequin.
                World world = session.world;
                if (world != null) {
                    world.playSound(session.tableCenter, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                }
                dealer.remove();
            }
        }
    }

    // ---- Cleanup paths ------------------------------------------------------

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // Cancel pending setup/removal modes.
        pendingSetups.remove(id);
        pendingRemovals.remove(id);
        // Cancel any active session.
        Session session = sessions.remove(id);
        if (session != null) {
            cancelAutoClear(session);
            removeDisplays(session);
        }
    }

    /**
     * Forcibly end every mid-hand session that has been idle for longer than
     * {@link #INACTIVITY_TIMEOUT_MS}. The player loses their already-deducted bet
     * — no payout is credited.
     *
     * <p>Only in-progress (not yet finished / not {@code waitingToClear}) sessions are
     * swept. Sessions that are already in the 30-second auto-clear window are left alone
     * because their economy outcome is already settled.
     *
     * <p>Iterates over a snapshot of the session keys to avoid
     * {@link java.util.ConcurrentModificationException} while removing entries.
     *
     * @param now the current time in milliseconds (injected so tests can use a synthetic clock)
     */
    public void sweepInactiveSessions(long now) {
        for (UUID playerId : new ArrayList<>(sessions.keySet())) {
            Session session = sessions.get(playerId);
            if (session == null) {
                continue;
            }
            // Only sweep sessions that are still in-progress (not yet settled / waiting to clear).
            if (session.game.isFinished() || session.waitingToClear) {
                continue;
            }
            if (now - session.game.lastInteractionTime() > INACTIVITY_TIMEOUT_MS) {
                cancelAutoClear(session);
                removeDisplays(session);
                sessions.remove(playerId);

                // Notify the player if they are still online.
                var player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    String betClause = session.game.bet() == 0
                            ? "(Practice table — no stakes.)"
                            : "Your bet of $" + session.game.bet() + " was forfeited.";
                    player.sendMessage(MM.deserialize(
                            "<red>Your Blackjack game was ended due to 10 minutes of inactivity. "
                                    + betClause + "</red>"));
                }
            }
        }
    }

    /** Called from {@code Tweaks#onDisable} to guarantee no displays survive a stop. */
    public void shutdown() {
        // Cancel the inactivity sweeper.
        if (sweepTaskId != -1) {
            Bukkit.getScheduler().cancelTask(sweepTaskId);
            sweepTaskId = -1;
        }

        // Remove active game card displays (also cancel any pending auto-clear tasks).
        for (Session session : sessions.values()) {
            cancelAutoClear(session);
            removeDisplays(session);
        }
        sessions.clear();

        // Remove all table hologram entities.
        for (List<UUID> ids : tableEntities.values()) {
            for (UUID id : ids) {
                var entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
        tableEntities.clear();
        buttonMap.clear();
    }

    /** Cancel the session's auto-clear task if one is scheduled. */
    private void cancelAutoClear(Session session) {
        if (session.autoClearTaskId != -1) {
            Bukkit.getScheduler().cancelTask(session.autoClearTaskId);
            session.autoClearTaskId = -1;
        }
    }

    // ---- Rendering ----------------------------------------------------------

    /**
     * Render the dealer and player hands lying flat on the table surface, centred at
     * {@code tableCenter} (support-block top face Y). Each hand is independently centred:
     * <pre>
     *   totalWidth = (hand.size() - 1) * CARD_SPACING
     *   spreadOffset[i] = -totalWidth/2.0 + i * CARD_SPACING
     * </pre>
     * With {@code CARD_SPACING = 0.45f}: 2 cards land at ±0.225; 3 cards at −0.45/0/+0.45.
     *
     * <p>Card orientation is derived from {@code session.facing} (the player-side button
     * facing) so cards read upright from their respective seats regardless of whether the
     * table is aligned North/South or East/West.
     *
     * <p>Player hand: offset toward the player seat (in the facing direction).<br>
     * Dealer hand: offset toward the dealer seat (opposite facing direction).
     */
    private void renderOnTable(Session session, Location tableCenter) {
        double anchorY = tableCenter.y() + TABLE_CARD_HEIGHT;
        boolean gameFinished = session.game.isFinished();
        BlockFace playerFacing = session.facing != null ? session.facing : BlockFace.SOUTH;

        renderHand(session, tableCenter, anchorY, session.game.dealerHand(),
                playerFacing, true, gameFinished);
        renderHand(session, tableCenter, anchorY, session.game.playerHand(),
                playerFacing, false, gameFinished);
    }

    /**
     * Render one hand (dealer or player) flat on the table surface.
     *
     * <p>The seat offset axis is derived from {@code playerFacing} so the layout works for all
     * four cardinal table alignments:
     * <ul>
     *   <li>Player seat is in the {@code playerFacing} direction from the table centre.</li>
     *   <li>Dealer seat is in the opposite direction.</li>
     *   <li>Cards spread along the axis perpendicular to {@code playerFacing}.</li>
     * </ul>
     *
     * @param isDealer    true for the dealer hand — the hole card (index > 0) is face-down
     *                    while the game is in progress.
     * @param gameFinished when true, all cards are rendered face-up.
     */
    private void renderHand(Session session, Location tableCenter, double anchorY,
                             List<Card> hand, BlockFace playerFacing,
                             boolean isDealer, boolean gameFinished) {
        int size = hand.size();
        if (size == 0) {
            return;
        }
        // Determine seat direction: player sits toward playerFacing, dealer on opposite side.
        BlockFace seatFacing = isDealer ? playerFacing.getOppositeFace() : playerFacing;
        double seatDX = seatFacing.getModX() * PLAYER_Z_OFFSET;
        double seatDZ = seatFacing.getModZ() * PLAYER_Z_OFFSET;

        // Spread axis is perpendicular to the seat facing.
        int crossX = -seatFacing.getModZ();
        int crossZ = seatFacing.getModX();

        double totalWidth = (size - 1) * CARD_SPACING;
        // For the dealer hand, mirror the spread order so cards read left-to-right from
        // the dealer seat (which faces the opposite way from the player).
        for (int i = 0; i < size; i++) {
            Card card = hand.get(i);
            // Dealer's cards at index > 0 are face-down until the game ends.
            boolean faceDown = isDealer && !gameFinished && (i > 0);
            double off = isDealer
                    ? totalWidth / 2.0 - i * CARD_SPACING   // mirrored for dealer seat
                    : -totalWidth / 2.0 + i * CARD_SPACING; // natural order for player seat
            Location cardLoc = new Location(
                    tableCenter.getWorld(),
                    tableCenter.x() + seatDX + crossX * off,
                    anchorY,
                    tableCenter.z() + seatDZ + crossZ * off);
            spawnCard(session, cardLoc, card, faceDown, seatFacing);
        }
    }

    /**
     * Spawn a single card {@link ItemDisplay} lying flat on the table and track it in
     * the session. Cards use {@link #flatCardRotationForFacing(BlockFace)} with the seat's
     * facing direction so they read upright from any cardinal orientation.
     *
     * @param seatFacing the direction the seat (whose cards these are) faces away from the table.
     *                   Cards are oriented to read upright when viewed from that seat.
     */
    private void spawnCard(Session session, Location loc, Card card,
                           boolean faceDown, BlockFace seatFacing) {
        World world = session.world;
        ItemStack stack = CardItemFactory.createCardItem(card, faceDown, session.backColor);

        ItemDisplay display = (ItemDisplay) world.spawnEntity(loc, EntityType.ITEM_DISPLAY);
        display.setItemStack(stack);
        try {
            display.setBillboard(Display.Billboard.FIXED);
        } catch (Throwable ignored) {} // MockBukkit fix
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        Quaternionf flatRotation = flatCardRotationForFacing(seatFacing);
        display.setTransformation(new Transformation(
                new Vector3f(),
                flatRotation,
                new Vector3f(0.4f, 0.4f, 0.4f),
                new Quaternionf()));
        display.setPersistent(false);

        display.getPersistentDataContainer().set(
                cardDisplayKey, PersistentDataType.BOOLEAN, true);
        display.getPersistentDataContainer().set(
                cardOwnerKey, PersistentDataType.STRING, session.game.playerId().toString());

        session.displayIds.add(display.getUniqueId());
    }

    /** Replace the rendered grid with one reflecting the current hand state. */
    private void redraw(Session session) {
        // Re-render only the card displays. The dealer mannequin is spawned once at game
        // start and must persist across every mid-game redraw, so it is intentionally NOT
        // touched here — only {@link #removeDisplays(Session)} (teardown) removes it.
        removeCardDisplays(session);
        renderOnTable(session, session.tableCenter);
    }

    /**
     * Remove only the tracked card {@link ItemDisplay} entities for this session, leaving the
     * dealer mannequin in place. Used by {@link #redraw(Session)} on every hand re-render so
     * the mannequin survives the round. Idempotent.
     */
    private void removeCardDisplays(Session session) {
        for (UUID id : session.displayIds) {
            var entity = Bukkit.getEntity(id);
            if (entity instanceof ItemDisplay) {
                entity.remove();
            }
        }
        session.displayIds.clear();
    }

    /**
     * Remove every tracked card display for this session, plus the dealer mannequin if one is
     * present. Idempotent. This is the universal cleanup hook — it runs on early clear,
     * auto-clear, quit, chunk unload, shutdown, and the inactivity sweep — so the dealer
     * mannequin removal here guarantees no entity outlives the session on any exit path.
     */
    private void removeDisplays(Session session) {
        removeCardDisplays(session);

        if (session.dealerMannequinId != null) {
            var dealer = Bukkit.getEntity(session.dealerMannequinId);
            if (dealer != null) {
                dealer.remove();
            }
            session.dealerMannequinId = null;
        }
    }
}
