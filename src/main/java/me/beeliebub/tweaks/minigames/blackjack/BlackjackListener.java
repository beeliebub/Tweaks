package me.beeliebub.tweaks.minigames.blackjack;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import me.beeliebub.tweaks.economy.BalanceCommand;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.minigames.cards.Card;
import me.beeliebub.tweaks.minigames.cards.CardItemFactory;
import me.beeliebub.tweaks.ranks.RankManager;
import net.kyori.adventure.text.event.ClickCallback;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /** PDC key sub-name for the PvP-table store. Full key: {@code tweaks:blackjack_pvp_tables}. */
    private static final String PVP_TABLES_KEY_NAME = "blackjack_pvp_tables";

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
    private NamespacedKey pvpTablesKey;
    private NamespacedKey cardDisplayKey;
    private NamespacedKey cardOwnerKey;

    /** Carries the pending bet and optional back-color for a server table being set up. */
    private record PendingSetup(int bet, Integer backColor) {}

    /** Carries the optional back-color for a PvP table being set up. */
    private record PendingPvpSetup(Integer backColor) {}

    /** Players waiting to finalise a server table by right-clicking the MIDDLE button. */
    private final Map<UUID, PendingSetup> pendingSetups = new HashMap<>();

    /** Players waiting to finalise a PvP table by right-clicking the first side's MIDDLE button. */
    private final Map<UUID, PendingPvpSetup> pendingPvpSetups = new HashMap<>();

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
     * Fast O(1) button-to-PvP-table lookup. Keyed by block location string
     * ({@code "world:x:y:z"}). Each entry stores the PvP table entry, the button role,
     * and which player side (0 = side-A / first registered, 1 = side-B / opposite side)
     * the button belongs to. This map is separate from {@link #buttonMap} so PvP buttons
     * are unambiguously distinguishable from server-table buttons at O(1) cost.
     */
    private final Map<String, PvpButtonRef> pvpButtonMap = new HashMap<>();

    /**
     * Fast O(1) money-betting-button lookup for PvP tables. Keyed by block location string
     * ({@code "world:x:y:z"}); each entry maps to the owning PvP table and player side.
     * Separate from {@link #pvpButtonMap} (the LEFT/MIDDLE/RIGHT play controls) so a money
     * button click opens a wager prompt instead of being treated as a gameplay action.
     */
    private final Map<String, MoneyButtonRef> pvpMoneyButtonMap = new HashMap<>();

    /**
     * Active PvP table sessions keyed by the table identity key {@code blockKey(middleA)}.
     * At most one running game (or pre-game lobby) per physical table.
     */
    private final Map<String, PvpSession> pvpSessions = new HashMap<>();

    /**
     * Players currently typing a money wager in chat, keyed by player UUID. Read from the
     * async chat thread, so it is a {@link ConcurrentHashMap}; all mutation of session/economy
     * state is marshalled back to the main thread.
     */
    private final Map<UUID, PvpWagerPrompt> pvpWagerPrompts = new ConcurrentHashMap<>();

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
        pvpTablesKey   = new NamespacedKey(plugin, PVP_TABLES_KEY_NAME);
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

    /**
     * Serialized PvP table entry. Captures both player sides and their buttons.
     *
     * @param center    Support-block top-face center location (table surface).
     * @param backColor Optional RGB integer for the card-back tint, or {@code null}.
     * @param middleA   Middle-button block location for player side A.
     * @param facingA   Direction side-A buttons face (away from the wall).
     * @param middleB   Middle-button block location for player side B (opposite side).
     * @param facingB   Direction side-B buttons face (always opposite of facingA).
     */
    public record PvpTableEntry(
            Location center,
            Integer backColor,
            Location middleA,
            BlockFace facingA,
            Location middleB,
            BlockFace facingB
    ) {}

    /**
     * Associates a button block with its PvP table entry, its role, and which
     * player side it belongs to (0 = side A, 1 = side B).
     */
    public record PvpButtonRef(PvpTableEntry table, ButtonRole role, int side) {}

    public record MoneyButtonRef(PvpTableEntry table, int side) {}

    /** Pending chat-wager prompt: the table identity key and side the typed amount applies to. */
    public record PvpWagerPrompt(String tableKey, int side) {}

    /**
     * A PvP table's running state: the two seated players, their escrowed money/item stakes,
     * readiness flags, the head-to-head game once dealt, and the rendered card entities.
     * One per physical table (keyed by {@link #tableKey} in {@link #pvpSessions}).
     */
    public static final class PvpSession {
        public final PvpTableEntry table;
        public final String tableKey;
        public final World world;
        public final long chunkKey;

        /** The head-to-head game state machine. Tracks phases and in-game actions. */
        public final BlackjackPvpGame game;

        /** Side occupants; null until a player claims that side. */
        public UUID playerA;
        public UUID playerB;

        /** Escrowed money stakes (already debited from balance) per side. */
        public int moneyStakeA;
        public int moneyStakeB;

        /** Escrowed wagered items per side. Currently always empty (item-betting is not yet implemented). */
        public final List<ItemStack> itemStakeA = new ArrayList<>();
        public final List<ItemStack> itemStakeB = new ArrayList<>();

        /**
         * Registration flags for the REGISTERING phase: set when a player presses MIDDLE
         * to claim their seat. Once both are true the game advances to BETTING.
         */
        public boolean registeredA;
        public boolean registeredB;

        /**
         * Ready-to-confirm flags for the BETTING phase: set when a player presses MIDDLE
         * to lock in their stake. Once both are true the game advances to CONFIRMING_BETS.
         */
        public boolean readyA;
        public boolean readyB;

        /** Confirmation flags for the CONFIRMING_BETS phase; set one-way (true only). */
        public boolean confirmedA;
        public boolean confirmedB;

        /** UUIDs of the spawned card {@link ItemDisplay} entities for this round. */
        public final List<UUID> displayIds = new ArrayList<>();

        /**
         * UUIDs of the face-up {@link ItemDisplay} for each card (visible only to the
         * card's owner during play; revealed to all at round end via {@link #revealAllPvpCards}).
         */
        public final List<UUID> faceUpIds = new ArrayList<>();

        /**
         * UUIDs of the face-down {@link ItemDisplay} for each card (visible to everyone
         * except the owner during play; hidden from all at round end).
         */
        public final List<UUID> faceDownIds = new ArrayList<>();

        public boolean waitingToClear;
        public int autoClearTaskId = -1;

        public PvpSession(PvpTableEntry table, String tableKey, World world, long chunkKey) {
            this.table = table;
            this.tableKey = tableKey;
            this.world = world;
            this.chunkKey = chunkKey;
            this.game = new BlackjackPvpGame();
        }

        public UUID occupant(int side) {
            return side == BlackjackPvpGame.SIDE_A ? playerA : playerB;
        }

        public void setOccupant(int side, UUID id) {
            if (side == BlackjackPvpGame.SIDE_A) {
                playerA = id;
            } else {
                playerB = id;
            }
        }

        public boolean registered(int side) {
            return side == BlackjackPvpGame.SIDE_A ? registeredA : registeredB;
        }

        public void setRegistered(int side, boolean value) {
            if (side == BlackjackPvpGame.SIDE_A) {
                registeredA = value;
            } else {
                registeredB = value;
            }
        }

        public boolean ready(int side) {
            return side == BlackjackPvpGame.SIDE_A ? readyA : readyB;
        }

        public void setReady(int side, boolean ready) {
            if (side == BlackjackPvpGame.SIDE_A) {
                readyA = ready;
            } else {
                readyB = ready;
            }
        }

        public boolean confirmed(int side) {
            return side == BlackjackPvpGame.SIDE_A ? confirmedA : confirmedB;
        }

        public void setConfirmed(int side, boolean value) {
            if (side == BlackjackPvpGame.SIDE_A) {
                confirmedA = value;
            } else {
                confirmedB = value;
            }
        }

        public int moneyStake(int side) {
            return side == BlackjackPvpGame.SIDE_A ? moneyStakeA : moneyStakeB;
        }

        public void setMoneyStake(int side, int amount) {
            if (side == BlackjackPvpGame.SIDE_A) {
                moneyStakeA = amount;
            } else {
                moneyStakeB = amount;
            }
        }

        public List<ItemStack> itemStake(int side) {
            return side == BlackjackPvpGame.SIDE_A ? itemStakeA : itemStakeB;
        }
    }

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

    /** Exposed for {@link BlackjackCommand} to write new table entries. */
    NamespacedKey pvpTablesKey() {
        return pvpTablesKey;
    }

    /**
     * Returns the live PvP button lookup map.
     * Package-visible for tests; callers outside this package should use
     * {@link #pvpButtonMap()} to distinguish PvP buttons from server-table buttons.
     */
    public Map<String, PvpButtonRef> pvpButtonMap() {
        return pvpButtonMap;
    }

    /**
     * Returns the live PvP money-betting-button lookup map.
     * Exposed for test assertions that money buttons are registered to the correct side.
     */
    public Map<String, MoneyButtonRef> pvpMoneyButtonMap() {
        return pvpMoneyButtonMap;
    }

    /**
     * Returns the live server-table button lookup map.
     * Exposed for test assertions that confirm PvP keys do not leak here.
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
                "<yellow>Right-click the <white>MIDDLE</white> button of the server table you want to remove, "
                        + "or <white>any</white> control button of a PvP table.</yellow>"));
    }

    /**
     * Put {@code player} into PvP-table-setup mode for the given optional card-back color.
     * The next right-click on a wall button anchors side A; the listener then validates
     * the opposite side's buttons and optional betting infrastructure before persisting.
     *
     * @param player    the admin initiating setup
     * @param backColor optional RGB integer for the card-back tint, or {@code null} for default
     */
    public void beginPvpTableSetup(Player player, Integer backColor) {
        pendingPvpSetups.put(player.getUniqueId(), new PendingPvpSetup(backColor));
        player.sendMessage(MM.deserialize(
                "<yellow>Right-click the <white>MIDDLE</white> control button on ONE side of the PvP table "
                        + "to register it.</yellow>"));
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
        text.text(MM.deserialize(
                "<gold><bold>Blackjack Table</bold></gold>\n"
                        + "<gray>Press MIDDLE to Play</gray>\n"
                        + "<yellow>Bet: $" + entry.bet() + "</yellow>"));
        text.setBillboard(Display.Billboard.CENTER);
        text.setPersistent(false);
        text.setDefaultBackground(false);
        text.setShadowed(true);

        ChunkId cid = chunkId(center.getChunk());
        tableEntities.computeIfAbsent(cid, k -> new ArrayList<>()).add(text.getUniqueId());

        // Register the three button locations for fast O(1) click lookup.
        registerButtonsForTable(entry);
    }

    /**
     * Spawn a {@link TextDisplay} hologram above the given PvP table centre and
     * register both sides' button locations in {@link #pvpButtonMap}.
     * Called on {@link ChunkLoadEvent} and when a new PvP table is placed.
     */
    public void spawnPvpTableHologram(PvpTableEntry entry) {
        Location center = entry.center();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        Location hologLoc = center.clone().add(0, HOLOGRAM_HEIGHT, 0);
        TextDisplay text = (TextDisplay) world.spawnEntity(hologLoc, EntityType.TEXT_DISPLAY);
        text.text(MM.deserialize(
                "<gold><bold>PvP Blackjack Table</bold></gold>\n"
                        + "<aqua>Player vs Player</aqua>"));
        text.setBillboard(Display.Billboard.CENTER);
        text.setPersistent(false);
        text.setDefaultBackground(false);
        text.setShadowed(true);

        ChunkId cid = chunkId(center.getChunk());
        tableEntities.computeIfAbsent(cid, k -> new ArrayList<>()).add(text.getUniqueId());

        registerPvpButtonsForTable(entry);
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

    // ---- PvP table PDC serialisation ----------------------------------------

    /**
     * Serialises a PvP table entry to a pipe-delimited string.
     *
     * <p>Field layout (all fields mandatory):
     * <pre>
     * worldName | cx | cy | cz
     *   | ax | ay | az | facingA
     *   | bx | by | bz | facingB
     *   | backColor      (decimal RGB int, or empty string when null)
     * </pre>
     * Total: 13 pipe-delimited fields.
     *
     * Package-visible so tests can call it directly.
     */
    public static String serializePvpTable(PvpTableEntry entry) {
        String world = entry.center().getWorld().getName();
        StringBuilder sb = new StringBuilder();
        sb.append(world)
          .append('|').append(entry.center().x())
          .append('|').append(entry.center().y())
          .append('|').append(entry.center().z())
          .append('|').append(entry.middleA().getBlockX())
          .append('|').append(entry.middleA().getBlockY())
          .append('|').append(entry.middleA().getBlockZ())
          .append('|').append(entry.facingA().name())
          .append('|').append(entry.middleB().getBlockX())
          .append('|').append(entry.middleB().getBlockY())
          .append('|').append(entry.middleB().getBlockZ())
          .append('|').append(entry.facingB().name())
          .append('|').append(entry.backColor() != null ? (entry.backColor() & 0xFFFFFF) : "");
        return sb.toString();
    }

    /**
     * Deserialises a string produced by {@link #serializePvpTable}.
     * Returns {@code null} if the string is malformed (wrong field count, unparseable
     * numbers, unknown block face) or the world is not currently loaded.
     *
     * <p>Accepts both the current 13-field format and the legacy 17-field format (which
     * carried barrel/money-button location lists in fields 13–16). The 4 trailing fields
     * of the old format are silently ignored so already-persisted tables survive a server
     * upgrade without being orphaned or dropped.
     *
     * Package-visible so tests can call it directly.
     */
    public static PvpTableEntry deserializePvpTable(String raw) {
        // Use limit 14 so any extra trailing fields beyond 13 are folded into parts[13].
        String[] parts = raw.split("\\|", 14);
        if (parts.length < 13) {
            return null;
        }
        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                return null;
            }
            double cx = Double.parseDouble(parts[1]);
            double cy = Double.parseDouble(parts[2]);
            double cz = Double.parseDouble(parts[3]);
            int ax = Integer.parseInt(parts[4]);
            int ay = Integer.parseInt(parts[5]);
            int az = Integer.parseInt(parts[6]);
            BlockFace facingA = BlockFace.valueOf(parts[7]);
            int bx = Integer.parseInt(parts[8]);
            int by = Integer.parseInt(parts[9]);
            int bz = Integer.parseInt(parts[10]);
            BlockFace facingB = BlockFace.valueOf(parts[11]);
            Integer backColor = parts[12].isEmpty() ? null : Integer.parseInt(parts[12]);
            // parts[13] and beyond (if present) are legacy barrel/money-button fields — ignored.

            Location center  = new Location(world, cx, cy, cz);
            Location middleA = new Location(world, ax, ay, az);
            Location middleB = new Location(world, bx, by, bz);

            return new PvpTableEntry(center, backColor, middleA, facingA, middleB, facingB);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- PvP button registration --------------------------------------------

    /** Register all six PvP button locations (three per side) in {@link #pvpButtonMap}. */
    public void registerPvpButtonsForTable(PvpTableEntry entry) {
        // Side A
        Location midA   = entry.middleA();
        Location leftA  = leftButtonLoc(midA, entry.facingA());
        Location rightA = rightButtonLoc(midA, entry.facingA());
        pvpButtonMap.put(blockKey(midA),   new PvpButtonRef(entry, ButtonRole.MIDDLE, 0));
        pvpButtonMap.put(blockKey(leftA),  new PvpButtonRef(entry, ButtonRole.LEFT,   0));
        pvpButtonMap.put(blockKey(rightA), new PvpButtonRef(entry, ButtonRole.RIGHT,  0));
        // Side B
        Location midB   = entry.middleB();
        Location leftB  = leftButtonLoc(midB, entry.facingB());
        Location rightB = rightButtonLoc(midB, entry.facingB());
        pvpButtonMap.put(blockKey(midB),   new PvpButtonRef(entry, ButtonRole.MIDDLE, 1));
        pvpButtonMap.put(blockKey(leftB),  new PvpButtonRef(entry, ButtonRole.LEFT,   1));
        pvpButtonMap.put(blockKey(rightB), new PvpButtonRef(entry, ButtonRole.RIGHT,  1));
    }

    /** Unregister all six PvP button locations for {@code entry} from {@link #pvpButtonMap}. */
    private void unregisterPvpButtonsForTable(PvpTableEntry entry) {
        Location midA   = entry.middleA();
        pvpButtonMap.remove(blockKey(midA));
        pvpButtonMap.remove(blockKey(leftButtonLoc(midA, entry.facingA())));
        pvpButtonMap.remove(blockKey(rightButtonLoc(midA, entry.facingA())));
        Location midB   = entry.middleB();
        pvpButtonMap.remove(blockKey(midB));
        pvpButtonMap.remove(blockKey(leftButtonLoc(midB, entry.facingB())));
        pvpButtonMap.remove(blockKey(rightButtonLoc(midB, entry.facingB())));
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

    /** Returns an unmodifiable view of the active PvP sessions. */
    public Map<String, PvpSession> pvpSessions() {
        return Collections.unmodifiableMap(pvpSessions);
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

    /**
     * Given side-A's MIDDLE button block and its facing, locates the OPPOSITE side's
     * (side B's) MIDDLE button {@link Location}, or {@code null} if no valid opposite row
     * of three wall buttons facing {@code facingB} can be found.
     *
     * <p>Geometry: the PvP table is a solid 2×3 (or 3×2) footprint. The two rows of three
     * control buttons sit on the table's two LONG edges, each facing outward toward its
     * player. The rows are therefore separated along the table's SHORT (facing) axis. With
     * {@code facingB = facingA.getOppositeFace()}, side-B's middle button lies at
     * {@code sideAButton + facingB * (crossDepth + 1)}, where {@code crossDepth} is the
     * support depth along the facing axis (side-A button is 1 block outside the near support
     * row, there are {@code crossDepth} support rows, and side-B's button is 1 block outside
     * the far support row).
     *
     * <p>Because {@code findTableCenterFromButton} accepts both 2×3 and 3×2 footprints, the
     * facing-axis depth may be {@link #TABLE_WIDTH} (2) or {@link #TABLE_DEPTH} (3). We try
     * both and accept the first candidate whose MIDDLE block plus its {@code leftButtonLoc}/
     * {@code rightButtonLoc} neighbors (computed with {@code facingB}) are all valid
     * {@code facingB} wall buttons per {@link #isPvpSideButtonValid}.
     */
    public static Location findOppositeMiddleButton(World world, Block sideAButton, BlockFace facingA) {
        BlockFace facingB = facingA.getOppositeFace();
        int by = sideAButton.getY();
        for (int crossDepth : new int[]{TABLE_WIDTH, TABLE_DEPTH}) {
            int step = crossDepth + 1;
            int midBX = sideAButton.getX() + facingB.getModX() * step;
            int midBZ = sideAButton.getZ() + facingB.getModZ() * step;
            Location middleB = new Location(world, midBX, by, midBZ);
            Location leftB   = leftButtonLoc(middleB, facingB);
            Location rightB  = rightButtonLoc(middleB, facingB);

            Block midBlock   = world.getBlockAt(midBX, by, midBZ);
            Block leftBlock  = world.getBlockAt(leftB.getBlockX(), leftB.getBlockY(), leftB.getBlockZ());
            Block rightBlock = world.getBlockAt(rightB.getBlockX(), rightB.getBlockY(), rightB.getBlockZ());

            if (isPvpSideButtonValid(midBlock, facingB)
                    && isPvpSideButtonValid(leftBlock, facingB)
                    && isPvpSideButtonValid(rightBlock, facingB)) {
                return middleB;
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

        // --- Load PvP tables ---
        List<String> pvpTables = pdc.getOrDefault(
                pvpTablesKey, PersistentDataType.LIST.strings(), List.of());
        for (String raw : pvpTables) {
            PvpTableEntry entry = deserializePvpTable(raw);
            if (entry == null) {
                plugin.getLogger().warning(
                        "Blackjack: malformed PvP table entry in chunk PDC (skipping): " + raw);
                continue;
            }
            if (!entry.center().getWorld().equals(chunk.getWorld())) {
                continue;
            }
            spawnPvpTableHologram(entry);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        ChunkId cid = chunkId(chunk);

        // Collect the table entries for this chunk so we can unregister their buttons.
        List<TableEntry> entries = collectEntriesForChunk(chunk);
        // Also collect PvP entries for button unregistration.
        List<PvpTableEntry> pvpEntries = collectPvpEntriesForChunk(chunk);

        removeTableHolograms(cid, entries);

        // Unregister PvP buttons (holograms share the same tableEntities map — they were
        // already removed in removeTableHolograms via the shared ChunkId list).
        for (PvpTableEntry pvpEntry : pvpEntries) {
            unregisterPvpButtonsForTable(pvpEntry);
        }

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

        // Tear down PvP sessions anchored in this chunk, refunding any un-settled escrow so
        // wagered money/items are never lost when the chunk unloads mid-round.
        for (PvpSession pvpSession : new ArrayList<>(pvpSessions.values())) {
            if (pvpSession.chunkKey == key && pvpSession.world.equals(event.getWorld())) {
                abortPvpSession(pvpSession, null);
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

    /**
     * Reads the chunk PDC to collect all deserializable PvpTableEntry objects for the given chunk.
     * Used so we can unregister PvP button map entries on chunk unload.
     */
    private List<PvpTableEntry> collectPvpEntriesForChunk(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> pvpTables = pdc.getOrDefault(
                pvpTablesKey, PersistentDataType.LIST.strings(), List.of());
        List<PvpTableEntry> result = new ArrayList<>();
        for (String raw : pvpTables) {
            PvpTableEntry entry = deserializePvpTable(raw);
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

        // ---- PvP-table setup mode takes precedence --------------------------
        if (pendingPvpSetups.containsKey(playerId)) {
            event.setCancelled(true);
            handlePvpSetup(player, clicked);
            return;
        }

        // ---- Removal mode takes precedence ----------------------------------
        if (pendingRemovals.contains(playerId)) {
            // Server tables: only a known MIDDLE button triggers removal.
            ButtonRef ref = buttonMap.get(key);
            if (ref != null && ref.role() == ButtonRole.MIDDLE) {
                event.setCancelled(true);
                handleRemoval(player, ref.table());
                return;
            }
            // PvP tables: accept ANY of the table's six control buttons. PvP tables have
            // two MIDDLE buttons (one per side), so the admin should not have to guess
            // which control to click. The first matching button removes the whole table.
            PvpButtonRef pvpRef = pvpButtonMap.get(key);
            if (pvpRef != null) {
                event.setCancelled(true);
                handlePvpRemoval(player, pvpRef.table());
            }
            return;
        }

        // ---- PvP money-betting button (opens a wager dialog prompt) -----------
        MoneyButtonRef money = pvpMoneyButtonMap.get(key);
        if (money != null) {
            event.setCancelled(true);
            promptPvpWager(player, money);
            return;
        }

        // ---- PvP gameplay buttons (LEFT/MIDDLE/RIGHT per side) ---------------
        PvpButtonRef pvpRef = pvpButtonMap.get(key);
        if (pvpRef != null) {
            event.setCancelled(true);
            handlePvpGameplay(player, pvpRef);
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
                    // Start a new game: economy flow.
                    int bet = table.bet();
                    double balance = economyManager.getBalance(playerId);
                    if (balance < bet) {
                        player.sendMessage(MM.deserialize(
                                "<red>You cannot afford a bet of $" + bet
                                        + ". Your balance is $" + (long) balance + ".</red>"));
                        return;
                    }
                    economyManager.removeBalance(playerId, bet);
                    boolean started = startGame(player, bet, tableCenter,
                            table.facing(), table.backColor());
                    if (!started) {
                        economyManager.addBalance(playerId, bet);
                        player.sendMessage(MM.deserialize(
                                "<red>Could not start a Blackjack game here. Your bet was refunded.</red>"));
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

    // ---- PvP setup handler --------------------------------------------------

    /**
     * Handles the right-click that anchors a PvP table setup.
     *
     * <p>Flow:
     * <ol>
     *   <li>Validate the clicked block is a wall-mounted button with a valid facing.</li>
     *   <li>Use {@link #findTableCenterFromButton} to locate the 2×3 footprint.</li>
     *   <li>Compute side-A's three button locations (left/middle/right).</li>
     *   <li>Compute the opposite side's (side-B) expected MIDDLE button position and
     *       validate that it — plus its left and right neighbours — are all wall buttons
     *       facing the opposite direction.</li>
     *   <li>Persist to chunk PDC under {@code tweaks:blackjack_pvp_tables} and spawn the
     *       hologram.</li>
     * </ol>
     */
    private void handlePvpSetup(Player player, Block button) {
        PendingPvpSetup pending = pendingPvpSetups.remove(player.getUniqueId());
        Integer backColor = pending.backColor();

        if (!(button.getBlockData() instanceof Switch sw)) {
            player.sendMessage(MM.deserialize(
                    "<red>That doesn't look like a wall button. Please right-click a wall-mounted button.</red>"));
            pendingPvpSetups.put(player.getUniqueId(), pending);
            return;
        }

        BlockFace facingA = sw.getFacing();
        if (facingA == BlockFace.UP || facingA == BlockFace.DOWN) {
            player.sendMessage(MM.deserialize(
                    "<red>Please use a wall-mounted button (not floor or ceiling).</red>"));
            pendingPvpSetups.put(player.getUniqueId(), pending);
            return;
        }

        // Compute the table surface centre from the button.
        Location center = findTableCenterFromButton(button, facingA);
        if (center == null) {
            player.sendMessage(MM.deserialize(
                    "<red>No valid 2x3 block area found for this button. "
                            + "Ensure a solid 2-wide by 3-deep block area sits beneath the three control buttons.</red>"));
            return;
        }

        Location middleA = button.getLocation();
        World world = button.getWorld();

        // Compute the opposite facing (side B faces the other way).
        BlockFace facingB = facingA.getOppositeFace();

        // Locate side-B's MIDDLE button across the table's short (facing) axis. The helper
        // validates that side-B's MIDDLE/LEFT/RIGHT are all wall buttons facing facingB,
        // trying both the 2×3 and 3×2 facing-axis depths.
        Location middleB = findOppositeMiddleButton(world, button, facingA);
        if (middleB == null) {
            player.sendMessage(MM.deserialize(
                    "<red>Could not find three valid wall buttons facing "
                            + facingB.name() + " on the opposite side of the table. "
                            + "Both long sides must have a row of three wall buttons facing away from the table.</red>"));
            return;
        }

        PvpTableEntry entry = new PvpTableEntry(center, backColor,
                middleA, facingA, middleB, facingB);

        String serialized = serializePvpTable(entry);
        Chunk chunk = center.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> existing = pdc.getOrDefault(pvpTablesKey, PersistentDataType.LIST.strings(), List.of());
        List<String> updated = new ArrayList<>(existing);
        updated.add(serialized);
        pdc.set(pvpTablesKey, PersistentDataType.LIST.strings(), updated);

        spawnPvpTableHologram(entry);

        String colorSuffix = backColor != null
                ? " <gray>| Back color:</gray> <yellow>#" + String.format("%06X", backColor) + "</yellow>"
                : "";
        player.sendMessage(MM.deserialize(
                "<green>PvP Blackjack table registered!</green> "
                        + "<gray>Center:</gray> <yellow>"
                        + String.format("%.1f, %.1f, %.1f", center.x(), center.y(), center.z())
                        + "</yellow>"
                        + colorSuffix));
    }

    /**
     * Returns true if {@code block} is a button from {@link Tag#BUTTONS} that is
     * wall-mounted and facing {@code expectedFacing}.
     */
    private static boolean isPvpSideButtonValid(Block block, BlockFace expectedFacing) {
        if (!Tag.BUTTONS.isTagged(block.getType())) {
            return false;
        }
        if (!(block.getBlockData() instanceof Switch sw)) {
            return false;
        }
        return sw.getFacing() == expectedFacing;
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
     * Remove a PvP Blackjack table that the admin clicked while in removal mode.
     *
     * <p>The matching PDC entry is dropped by <em>identity</em> rather than by exact
     * serialized-string equality: the existing {@code pvpTablesKey} list is read, every
     * raw string deserialized, and the entry whose two MIDDLE button block coordinates
     * (and world) match {@code table} is removed. Matching by identity avoids silent
     * failures if {@code serializePvpTable(deserializePvpTable(x))} ever drifts from the
     * original {@code x} (e.g. double or list formatting). Side A and side B middles
     * together uniquely identify a PvP table.
     */
    public void handlePvpRemoval(Player player, PvpTableEntry table) {
        pendingRemovals.remove(player.getUniqueId());

        // Tear down any live game/escrow for this table before the entry is dropped.
        PvpSession live = pvpSessions.get(pvpTableKey(table));
        if (live != null) {
            abortPvpSession(live, "<red>This PvP Blackjack table was removed. All wagers refunded.</red>");
        }

        Location center = table.center();
        Chunk chunk = center.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();

        // Read / modify / write the PvP table list on the table's CENTER chunk PDC.
        List<String> existing = pdc.getOrDefault(pvpTablesKey, PersistentDataType.LIST.strings(), List.of());
        List<String> updated = new ArrayList<>(existing.size());
        for (String raw : existing) {
            PvpTableEntry parsed = deserializePvpTable(raw);
            if (parsed != null && sameTableIdentity(parsed, table)) {
                continue; // drop the entry that matches the clicked table's identity
            }
            updated.add(raw);
        }
        pdc.set(pvpTablesKey, PersistentDataType.LIST.strings(), updated);

        // Remove the hologram entity for this specific table. PvP holograms live in the
        // same tableEntities map as server holograms, so removeHologramNear works as-is.
        removeHologramNear(center.clone().add(0, HOLOGRAM_HEIGHT, 0), chunkId(chunk));

        // Unregister all six PvP button keys.
        unregisterPvpButtonsForTable(table);

        player.sendMessage(MM.deserialize(
                "<green>PvP Blackjack table removed.</green>"));
    }

    /**
     * Identity comparison for two PvP table entries. Two entries refer to the same
     * physical table iff their side-A and side-B MIDDLE button block coordinates match
     * and they share the same world. Compared by block coordinates (not object identity
     * or floating-point center) so it is robust across a serialize/deserialize round-trip.
     */
    private static boolean sameTableIdentity(PvpTableEntry a, PvpTableEntry b) {
        Location aWorldAnchor = a.middleA();
        Location bWorldAnchor = b.middleA();
        World aw = aWorldAnchor.getWorld();
        World bw = bWorldAnchor.getWorld();
        if (aw == null || bw == null || !aw.getName().equals(bw.getName())) {
            return false;
        }
        return sameBlock(a.middleA(), b.middleA()) && sameBlock(a.middleB(), b.middleB());
    }

    /** True if the two locations share the same integer block coordinates. */
    private static boolean sameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
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

    // ---- PvP gameplay -------------------------------------------------------

    /** Stable per-table identity key for the {@link #pvpSessions} map. */
    private static String pvpTableKey(PvpTableEntry table) {
        return blockKey(table.middleA());
    }

    /** Get the existing session for a table, or create a fresh pre-game lobby. */
    private PvpSession pvpSessionFor(PvpTableEntry table) {
        String key = pvpTableKey(table);
        PvpSession existing = pvpSessions.get(key);
        if (existing != null) {
            return existing;
        }
        Location center = table.center();
        PvpSession session = new PvpSession(table, key, center.getWorld(),
                center.getChunk().getChunkKey());
        pvpSessions.put(key, session);
        return session;
    }

    /**
     * Verify that {@code player} holds {@code side}, or return {@code false} with a message.
     * This does NOT assign a seat — seat assignment is handled exclusively by the REGISTERING
     * phase MIDDLE-button press in {@link #handlePvpMiddle}.
     */
    private boolean claimSide(Player player, PvpSession session, int side) {
        UUID id = player.getUniqueId();
        UUID occupant = session.occupant(side);
        if (occupant != null && occupant.equals(id)) {
            return true;
        }
        if (occupant == null) {
            player.sendMessage(MM.deserialize(
                    "<red>You haven't registered for this side yet. Press the MIDDLE button first.</red>"));
        } else {
            player.sendMessage(MM.deserialize("<red>That side of the table is taken.</red>"));
        }
        return false;
    }

    /**
     * Opens the Paper Dialog wager prompt for the side whose money button was pressed.
     * Only the registered occupant of that side may set a wager, and only during the
     * BETTING phase. Keeps the {@code pvpWagerPrompts} entry so that the test/headless
     * {@link #applyWager(Player, String)} entry point can still function.
     */
    public void promptPvpWager(Player player, MoneyButtonRef money) {
        PvpSession session = pvpSessionFor(money.table());
        BlackjackPvpGame.State state = session.game.state();
        if (state != BlackjackPvpGame.State.BETTING) {
            if (state == BlackjackPvpGame.State.REGISTERING) {
                player.sendMessage(MM.deserialize(
                        "<red>Both players must register first (press MIDDLE) before placing wagers.</red>"));
            } else {
                player.sendMessage(MM.deserialize(
                        "<red>Wagers can only be changed during the betting phase.</red>"));
            }
            return;
        }
        if (!claimSide(player, session, money.side())) {
            return;
        }
        pvpWagerPrompts.put(player.getUniqueId(), new PvpWagerPrompt(session.tableKey, money.side()));
        openWagerDialog(player, session, money.side(), null);
    }

    /**
     * Opens (or reopens) the Paper Dialog menu for a PvP betting session.
     *
     * @param player  The player who must select a bet type or ready up.
     * @param session The active PvP session for their table.
     * @param side    0 for side A, 1 for side B.
     * @param error   Optional red error line shown when reopening after bad input; {@code null}
     *                on first open.
     */
    private void openWagerDialog(Player player, PvpSession session, int side, String error) {
        showPvpBetMenu(player, session, side, error);
    }

    /** Primary PvP betting menu with buttons for money, items, and readiness. */
    private void showPvpBetMenu(Player player, PvpSession session, int side, String error) {
        try {
            List<DialogBody> bodyLines = new ArrayList<>();
            String status = session.ready(side) ? "<green>READY" : "<red>NOT READY";
            bodyLines.add(DialogBody.plainMessage(MM.deserialize(
                    "<gray>Status: <white>" + status + "</white></gray>")));
            bodyLines.add(DialogBody.plainMessage(MM.deserialize(
                    "<gray>Money Stake: <yellow>$" + session.moneyStake(side) + "</yellow></gray>")));
            if (error != null) {
                bodyLines.add(DialogBody.plainMessage(MM.deserialize(error)));
            }

            ActionButton betMoney = ActionButton.builder(MM.deserialize("<!italic><gold>Bet Money"))
                    .tooltip(MM.deserialize("<!italic><gray>Open the money wager input."))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            showWagerDialog(p, session, side, null);
                        }
                    }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                    .build();

            ActionButton betItems = ActionButton.builder(MM.deserialize("<!italic><aqua>Bet Items"))
                    .tooltip(MM.deserialize("<!italic><gray>Open the item escrow GUI (coming soon)."))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            p.sendMessage(MM.deserialize("<yellow>Item betting is not yet implemented.</yellow>"));
                        }
                    }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                    .build();

            String readyLabel = session.ready(side) ? "Unlock" : "Ready";
            ActionButton ready = ActionButton.builder(MM.deserialize("<!italic><green><bold>" + readyLabel))
                    .tooltip(MM.deserialize("<!italic><gray>Lock in your stakes and wait for your opponent."))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            toggleReady(p, session, side);
                            // Refresh the menu if the game hasn't advanced to the next phase yet.
                            if (session.game.state() == BlackjackPvpGame.State.BETTING) {
                                showPvpBetMenu(p, session, side, null);
                            }
                        }
                    }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                    .build();

            ActionButton cancel = ActionButton.builder(MM.deserialize("<!italic><red>Cancel"))
                    .tooltip(MM.deserialize("<!italic><gray>Close the betting menu."))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            pvpWagerPrompts.remove(p.getUniqueId());
                        }
                    }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                    .build();

            DialogBase base = DialogBase.builder(MM.deserialize("<!italic><gold><bold>PvP Betting Menu"))
                    .body(bodyLines)
                    .build();

            Dialog dialog = Dialog.create(b -> b.empty()
                    .base(base)
                    .type(DialogType.multiAction(List.of(betMoney, betItems, ready, cancel), cancel, 2)));
            player.showDialog(dialog);
        } catch (Throwable ignored) {
            // Registry-backed API unavailable in MockBukkit.
        }
    }

    /** Builds and shows the money-wager input Dialog. */
    private void showWagerDialog(Player player, PvpSession session, int side, String error) {
        try {
            // Available = current balance + any already-escrowed stake (which a new wager refunds).
            double available = economyManager.getBalance(player.getUniqueId()) + session.moneyStake(side);
            List<DialogBody> bodyLines = new ArrayList<>();
            bodyLines.add(DialogBody.plainMessage(MM.deserialize(
                    "<gray>Your balance: <yellow>" + BalanceCommand.formatBalance(available)
                    + "</yellow>.</gray>")));
            if (error != null) {
                bodyLines.add(DialogBody.plainMessage(MM.deserialize(error)));
            }

            ActionButton accept = ActionButton.builder(
                            MM.deserialize("<!italic><green><bold>Set Wager"))
                    .tooltip(MM.deserialize("<!italic><gray>Confirm the amount and escrow it."))
                    .action(DialogAction.customClick(
                            (view, audience) -> {
                                if (audience instanceof Player p) {
                                    submitWager(p, view.getText("amount"));
                                }
                            },
                            ClickCallback.Options.builder()
                                    .uses(ClickCallback.UNLIMITED_USES)
                                    .build()))
                    .build();

            ActionButton cancel = ActionButton.builder(
                            MM.deserialize("<!italic><red><bold>Back"))
                    .tooltip(MM.deserialize("<!italic><gray>Return to the betting menu."))
                    .action(DialogAction.customClick(
                            (view, audience) -> {
                                if (audience instanceof Player p) {
                                    showPvpBetMenu(p, session, side, null);
                                }
                            },
                            ClickCallback.Options.builder()
                                    .uses(ClickCallback.UNLIMITED_USES)
                                    .build()))
                    .build();

            DialogBase base = DialogBase.builder(MM.deserialize("<!italic><gold><bold>Set Money Wager"))
                    .body(bodyLines)
                    .inputs(List.of(
                            DialogInput.text("amount",
                                            MM.deserialize("<!italic><yellow>Amount"))
                                    .maxLength(12)
                                    .build()))
                    .build();

            Dialog dialog = Dialog.create(b -> b.empty()
                    .base(base)
                    .type(DialogType.confirmation(accept, cancel)));
            player.showDialog(dialog);
        } catch (Throwable ignored) {
            // Registry-backed API unavailable in MockBukkit.
        }
    }

    /**
     * Called from the dialog Accept button (main thread). Parses and validates the amount;
     * reopens the dialog with an error message on failure.  On success delegates to
     * {@link #escrowWager(Player, PvpSession, int, int, int)} and returns to the primary menu.
     */
    private void submitWager(Player player, String raw) {
        UUID id = player.getUniqueId();
        PvpWagerPrompt prompt = pvpWagerPrompts.get(id);
        if (prompt == null) {
            return;
        }
        PvpSession session = pvpSessions.get(prompt.tableKey());
        if (session == null || session.game.state() != BlackjackPvpGame.State.BETTING) {
            pvpWagerPrompts.remove(id);
            player.sendMessage(MM.deserialize("<red>That table is no longer accepting wagers.</red>"));
            return;
        }
        String trimmed = raw == null ? "" : raw.trim();
        int amount;
        try {
            amount = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            showWagerDialog(player, session, prompt.side(),
                    "<red>That's not a whole number. Please enter a positive integer.</red>");
            return;
        }
        if (amount <= 0) {
            showWagerDialog(player, session, prompt.side(),
                    "<red>Wager must be greater than 0.</red>");
            return;
        }
        int side = prompt.side();
        if (!id.equals(session.occupant(side))) {
            pvpWagerPrompts.remove(id);
            player.sendMessage(MM.deserialize("<red>You no longer hold that side of the table.</red>"));
            return;
        }
        int previous = session.moneyStake(side);
        double available = economyManager.getBalance(id) + previous;
        if (available < amount) {
            showWagerDialog(player, session, side,
                    "<red>You can't afford $" + amount + ". Available: "
                    + BalanceCommand.formatBalance(available) + ".</red>");
            return;
        }
        pvpWagerPrompts.remove(id);
        escrowWager(player, session, side, previous, amount);
        // Return to the primary betting menu after successful money stake.
        showPvpBetMenu(player, session, side, null);
    }

    /**
     * Shared escrow logic used by both the dialog Accept path ({@link #submitWager}) and the
     * headless/test entry point ({@link #applyWager(Player, String)}).
     * Refunds any previous stake, debits the new amount, updates the session, and messages
     * the player.  Caller is responsible for removing the {@code pvpWagerPrompts} entry and
     * for all validation (non-null session, correct state, side ownership, affordability).
     *
     * @param previous the existing escrowed money stake to refund first (may be 0).
     * @param amount   the validated positive amount to escrow.
     */
    private void escrowWager(Player player, PvpSession session, int side, int previous, int amount) {
        UUID id = player.getUniqueId();
        if (previous > 0) {
            economyManager.addBalance(id, previous);
        }
        economyManager.removeBalance(id, amount);
        session.setMoneyStake(side, amount);
        player.sendMessage(MM.deserialize("<green>Wager set:</green> <yellow>$" + amount
                + "</yellow>. <gray>Press the MIDDLE button to ready up.</gray>"));
    }

    /**
     * Headless/test entry point: parse, validate, and escrow a money wager from a raw string.
     * Surfaces validation errors via chat messages (not by reopening a dialog).
     * Tests call {@code listener.promptPvpWager(player, ref)} then
     * {@code listener.applyWager(player, "100")} and assert {@code session.moneyStakeA == 100}.
     */
    public void applyWager(Player player, String raw) {
        UUID id = player.getUniqueId();
        PvpWagerPrompt prompt = pvpWagerPrompts.remove(id);
        if (prompt == null) {
            return;
        }
        if (raw.equalsIgnoreCase("cancel")) {
            player.sendMessage(MM.deserialize("<gray>Wager cancelled.</gray>"));
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            player.sendMessage(MM.deserialize("<red>That's not a whole number. Wager not set.</red>"));
            return;
        }
        if (amount <= 0) {
            player.sendMessage(MM.deserialize("<red>Wager must be greater than 0.</red>"));
            return;
        }
        PvpSession session = pvpSessions.get(prompt.tableKey());
        if (session == null || session.game.state() != BlackjackPvpGame.State.BETTING) {
            player.sendMessage(MM.deserialize("<red>That table is no longer accepting wagers.</red>"));
            return;
        }
        int side = prompt.side();
        if (!id.equals(session.occupant(side))) {
            player.sendMessage(MM.deserialize("<red>You no longer hold that side of the table.</red>"));
            return;
        }
        int previous = session.moneyStake(side);
        double available = economyManager.getBalance(id) + previous;
        if (available < amount) {
            player.sendMessage(MM.deserialize("<red>You can't afford a wager of $" + amount
                    + ". Your balance is $" + (long) economyManager.getBalance(id) + ".</red>"));
            return;
        }
        escrowWager(player, session, side, previous, amount);
    }

    /** Route a PvP control-button press to the correct handler. */
    private void handlePvpGameplay(Player player, PvpButtonRef ref) {
        PvpSession session = pvpSessionFor(ref.table());
        switch (ref.role()) {
            case MIDDLE -> handlePvpMiddle(player, session, ref.side());
            case LEFT   -> handlePvpAction(player, session, ref.side(), true);
            case RIGHT  -> handlePvpAction(player, session, ref.side(), false);
        }
    }

    /**
     * MIDDLE button: drives the full pre-game registration pipeline and clears a finished board.
     *
     * <pre>
     * REGISTERING    — both players press MIDDLE to claim seats.
     *                  Each press plays a confirmation sound + message.
     *                  When both have registered, game advances to BETTING.
     * BETTING        — both players press MIDDLE to lock in stakes.
     *                  Item stakes are captured at this point.
     *                  When both are ready, game advances to CONFIRMING_BETS and
     *                  both players see a summary of all escrowed bets.
     * CONFIRMING_BETS — both players press MIDDLE to confirm the displayed terms.
     *                  When both confirm, cards are dealt (ACTIVE).
     * ACTIVE         — gameplay only; MIDDLE does nothing during a live round.
     * </pre>
     */
    private void handlePvpMiddle(Player player, PvpSession session, int side) {
        // Clear a settled board immediately.
        if (session.game.isFinished() && session.waitingToClear) {
            cancelPvpAutoClear(session);
            clearPvpBoard(session);
            return;
        }

        BlackjackPvpGame.State state = session.game.state();
        if (state == BlackjackPvpGame.State.ACTIVE) {
            player.sendMessage(MM.deserialize(
                    "<red>The round is underway — use LEFT to hit, RIGHT to stand.</red>"));
            return;
        }

        UUID playerId = player.getUniqueId();

        // --- Phase 0: REGISTERING — claim a seat -----------------------------
        if (state == BlackjackPvpGame.State.REGISTERING) {
            UUID occupant = session.occupant(side);
            int otherSide = side == 0 ? 1 : 0;

            if (occupant != null && !occupant.equals(playerId)) {
                player.sendMessage(MM.deserialize("<red>That side of the table is already taken.</red>"));
                return;
            }
            if (playerId.equals(session.occupant(otherSide))) {
                player.sendMessage(MM.deserialize(
                        "<red>You're already seated on the other side of this table.</red>"));
                return;
            }

            // First-time registration for this side.
            if (occupant == null) {
                session.setOccupant(side, playerId);
                session.setRegistered(side, true);
                player.sendMessage(MM.deserialize(
                        "<green>Registered!</green> <gray>Waiting for your opponent to register...</gray>"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                notifyOpponentRegistered(session, side);
            } else {
                // Already registered; idempotent.
                player.sendMessage(MM.deserialize(
                        "<gray>You're already registered. Waiting for your opponent...</gray>"));
            }

            // Both registered — advance to BETTING.
            if (session.registeredA && session.registeredB) {
                session.game.setState(BlackjackPvpGame.State.BETTING);
                messageBoth(session,
                        "<gold>Both players registered!</gold> <gray>Place your money wager, "
                        + "then press MIDDLE when ready.</gray>");
                playBothSounds(session, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
            }
            return;
        }

        // Require the player to be the registered occupant for phases past REGISTERING.
        if (!claimSide(player, session, side)) {
            return;
        }

        // --- Phase 1: BETTING — lock in stakes --------------------------------
        if (state == BlackjackPvpGame.State.BETTING) {
            toggleReady(player, session, side);
            openWagerDialog(player, session, side, null);
            return;
        }

        // --- Phase 2: CONFIRMING_BETS — confirm displayed terms ---------------
        if (state == BlackjackPvpGame.State.CONFIRMING_BETS) {
            if (session.confirmed(side)) {
                player.sendMessage(MM.deserialize(
                        "<gray>You've already confirmed. Waiting for your opponent...</gray>"));
                return;
            }
            session.setConfirmed(side, true);
            player.sendMessage(MM.deserialize(
                    "<green>Confirmed!</green> <gray>Waiting for your opponent...</gray>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.6f);
            if (session.confirmedA && session.confirmedB) {
                dealPvp(session);
            }
        }
    }

    /**
     * Toggles the ready status for a player in the BETTING phase. If both players
     * become ready, the game advances to CONFIRMING_BETS.
     */
    private void toggleReady(Player player, PvpSession session, int side) {
        if (session.ready(side)) {
            // Allow un-readying so the player can adjust their stake.
            session.setReady(side, false);
            player.sendMessage(MM.deserialize(
                    "<yellow>Stake unlocked. Adjust your wager and press READY again.</yellow>"));
            return;
        }
        session.setReady(side, true);
        player.sendMessage(MM.deserialize("<green>Stake locked!</green> <gray>$"
                + session.moneyStake(side) + " money</gray>"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
        notifyOpponentReady(session, side);

        if (session.readyA && session.readyB) {
            session.game.setState(BlackjackPvpGame.State.CONFIRMING_BETS);
            announcePvpTerms(session);
            playBothSounds(session, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
        } else {
            player.sendMessage(MM.deserialize("<gray>Waiting for your opponent to lock in...</gray>"));
        }
    }

    /**
     * Displays the escrowed stakes of both players to confirm before dealing.
     * Called when entering the CONFIRMING_BETS phase.
     */
    private void announcePvpTerms(PvpSession session) {
        Player a = playerOrNull(session.playerA);
        Player b = playerOrNull(session.playerB);
        String nameA = a != null ? a.getName() : "Player A";
        String nameB = b != null ? b.getName() : "Player B";

        String msg = "<gold>Bets Summary — confirm to deal!</gold>\n"
                + "<gray> - <white>" + nameA + "</white>: <yellow>$" + session.moneyStakeA
                + "</yellow> money\n"
                + "<gray> - <white>" + nameB + "</white>: <yellow>$" + session.moneyStakeB
                + "</yellow> money\n"
                + "<yellow>Both players press MIDDLE to confirm and deal!</yellow>";
        messageBoth(session, msg);
    }

    /** LEFT (hit) / RIGHT (stand) during a live PvP round. */
    private void handlePvpAction(Player player, PvpSession session, int side, boolean isHit) {
        if (session.game.isFinished()) {
            return;
        }

        // --- Phase: Pre-deal — LEFT or RIGHT cancels the entire session -------
        if (session.game.state() != BlackjackPvpGame.State.ACTIVE) {
            abortPvpSession(session, "<red>The PvP Blackjack session was cancelled. "
                    + "All wagers have been refunded.</red>");
            return;
        }

        if (!player.getUniqueId().equals(session.occupant(side))) {
            player.sendMessage(MM.deserialize("<red>That's not your side.</red>"));
            return;
        }
        if (isHit) {
            session.game.hit(side);
        } else {
            session.game.stand(side);
        }
        if (session.game.isFinished()) {
            finishPvp(session);
        } else {
            redrawPvp(session);
            int value = side == BlackjackPvpGame.SIDE_A ? session.game.valueA() : session.game.valueB();
            String label = isHit ? "<green>Hit!</green>" : "<red>Stand.</red>";
            player.sendMessage(MM.deserialize(label + " <gray>Your hand:</gray> <yellow>"
                    + value + "</yellow>"));
        }
    }

    /**
     * Deal a fresh head-to-head game once both sides have confirmed and render both hands.
     */
    private void dealPvp(PvpSession session) {
        session.game.dealInitial();
        renderPvpHands(session);

        messageBoth(session, "<gold>PvP Blackjack!</gold> "
                + "<gray>Cards dealt. LEFT = Hit, RIGHT = Stand. "
                + "LEFT or RIGHT before your turn to cancel.</gray>");
        Player a = playerOrNull(session.playerA);
        Player b = playerOrNull(session.playerB);
        if (a != null) {
            a.sendMessage(MM.deserialize("<gray>Your hand:</gray> <yellow>" + session.game.valueA() + "</yellow>"));
        }
        if (b != null) {
            b.sendMessage(MM.deserialize("<gray>Your hand:</gray> <yellow>" + session.game.valueB() + "</yellow>"));
        }
        playBothSounds(session, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.4f);
    }

    /**
     * Settle a finished PvP round: distribute money + items per the result, then schedule the
     * auto-clear. Stakes are zeroed after distribution so cleanup paths never double-refund.
     */
    private void finishPvp(PvpSession session) {
        BlackjackPvpGame.Result result = session.game.result();
        UUID a = session.playerA;
        UUID b = session.playerB;
        int stakeA = session.moneyStakeA;
        int stakeB = session.moneyStakeB;

        switch (result) {
            case A_WINS -> {
                if (a != null) {
                    economyManager.addBalance(a, stakeA + stakeB);
                }
                giveItems(a, session.itemStakeA, session.itemStakeB, session);
                announceWinner(a, b, stakeB);
            }
            case B_WINS -> {
                if (b != null) {
                    economyManager.addBalance(b, stakeA + stakeB);
                }
                giveItems(b, session.itemStakeB, session.itemStakeA, session);
                announceWinner(b, a, stakeA);
            }
            case PUSH -> {
                if (a != null) {
                    economyManager.addBalance(a, stakeA);
                }
                if (b != null) {
                    economyManager.addBalance(b, stakeB);
                }
                giveItems(a, session.itemStakeA, List.of(), session);
                giveItems(b, session.itemStakeB, List.of(), session);
                messageBoth(session, "<yellow>Push! Both wagers are returned.</yellow>");
            }
        }

        session.moneyStakeA = 0;
        session.moneyStakeB = 0;
        session.itemStakeA.clear();
        session.itemStakeB.clear();

        redrawPvp(session);
        // Reveal all card faces to every online viewer now that the round is over.
        revealAllPvpCards(session);
        messageBoth(session, "<gray>Press MIDDLE to clear the board.</gray>");

        session.waitingToClear = true;
        schedulePvpAutoClear(session);
    }

    private void announceWinner(UUID winner, UUID loser, int opponentMoney) {
        Player w = playerOrNull(winner);
        Player l = playerOrNull(loser);
        if (w != null) {
            w.sendMessage(MM.deserialize("<green><bold>You win!</bold></green> <gray>You took</gray> "
                    + "<yellow>$" + opponentMoney + "</yellow> "
                    + "<gray>plus your opponent's wagered items.</gray>"));
        }
        if (l != null) {
            l.sendMessage(MM.deserialize("<red>You lost the round and your wager.</red>"));
        }
    }

    /**
     * Give {@code recipient} the combined {@code own} + {@code opponent} item stacks. Inventory
     * overflow drops at the player; if the recipient is offline, everything drops at the table.
     */
    private void giveItems(UUID recipientId, List<ItemStack> own, List<ItemStack> opponent,
                           PvpSession session) {
        List<ItemStack> all = new ArrayList<>(own.size() + opponent.size());
        all.addAll(own);
        all.addAll(opponent);
        if (all.isEmpty()) {
            return;
        }
        Player recipient = playerOrNull(recipientId);
        if (recipient != null) {
            var leftover = recipient.getInventory().addItem(all.toArray(new ItemStack[0]));
            for (ItemStack drop : leftover.values()) {
                recipient.getWorld().dropItemNaturally(recipient.getLocation(), drop);
            }
        } else {
            Location center = session.table.center();
            World world = center.getWorld();
            if (world != null) {
                for (ItemStack drop : all) {
                    world.dropItemNaturally(center, drop);
                }
            }
        }
    }

    /** Refund a single side's un-settled escrow (money + items), then clear it. */
    private void refundSide(PvpSession session, int side) {
        UUID id = session.occupant(side);
        int money = session.moneyStake(side);
        if (money > 0 && id != null) {
            economyManager.addBalance(id, money);
        }
        session.setMoneyStake(side, 0);
        List<ItemStack> items = session.itemStake(side);
        if (!items.isEmpty()) {
            giveItems(id, new ArrayList<>(items), List.of(), session);
            items.clear();
        }
    }

    /**
     * Abort a session entirely: refund any un-settled escrow on both sides, remove displays,
     * optionally message both players, and drop the session.
     */
    private void abortPvpSession(PvpSession session, String reason) {
        cancelPvpAutoClear(session);
        refundSide(session, BlackjackPvpGame.SIDE_A);
        refundSide(session, BlackjackPvpGame.SIDE_B);
        removePvpDisplays(session);
        if (reason != null) {
            messageBoth(session, reason);
        }
        pvpSessions.remove(session.tableKey);
    }

    /** Tear down a finished board (stakes already settled) without refunding. */
    private void clearPvpBoard(PvpSession session) {
        removePvpDisplays(session);
        pvpSessions.remove(session.tableKey);
    }

    private void schedulePvpAutoClear(PvpSession session) {
        session.autoClearTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PvpSession current = pvpSessions.get(session.tableKey);
            if (current == session && session.waitingToClear) {
                removePvpDisplays(session);
                pvpSessions.remove(session.tableKey);
            }
        }, AUTO_CLEAR_TICKS).getTaskId();
    }

    private void cancelPvpAutoClear(PvpSession session) {
        if (session.autoClearTaskId != -1) {
            Bukkit.getScheduler().cancelTask(session.autoClearTaskId);
            session.autoClearTaskId = -1;
        }
    }

    /** Handle a quitting player's involvement in any PvP table. */
    private void handlePvpQuit(UUID id) {
        for (PvpSession session : new ArrayList<>(pvpSessions.values())) {
            int side;
            if (id.equals(session.playerA)) {
                side = BlackjackPvpGame.SIDE_A;
            } else if (id.equals(session.playerB)) {
                side = BlackjackPvpGame.SIDE_B;
            } else {
                continue;
            }
            if (session.game.state() != BlackjackPvpGame.State.ACTIVE) {
                // Pre-game: refund just this side and vacate the seat.
                refundSide(session, side);
                session.setOccupant(side, null);
                session.setRegistered(side, false);
                session.setReady(side, false);
                session.setConfirmed(side, false);
                if (session.playerA == null && session.playerB == null) {
                    cancelPvpAutoClear(session);
                    removePvpDisplays(session);
                    pvpSessions.remove(session.tableKey);
                }
            } else if (!session.waitingToClear) {
                // Mid-round: a fair settlement isn't possible — refund both and abort.
                abortPvpSession(session, "<red>The PvP Blackjack round was cancelled because a "
                        + "player left. All wagers refunded.</red>");
            } else {
                // Already settled, just clearing the board.
                cancelPvpAutoClear(session);
                removePvpDisplays(session);
                pvpSessions.remove(session.tableKey);
            }
        }
        pvpWagerPrompts.remove(id);
    }

    // ---- PvP rendering ------------------------------------------------------

    /** Render both hands flat on the table surface, each oriented toward its own seat. */
    private void renderPvpHands(PvpSession session) {
        BlackjackPvpGame game = session.game;
        if (game.state() != BlackjackPvpGame.State.ACTIVE) {
            return;
        }
        Location center = session.table.center();
        double anchorY = center.y() + TABLE_CARD_HEIGHT;
        // Pass each hand's owner UUID so per-player visibility can be applied.
        renderPvpHand(session, center, anchorY, game.handA(), session.table.facingA(), session.playerA);
        renderPvpHand(session, center, anchorY, game.handB(), session.table.facingB(), session.playerB);
    }

    /**
     * Render one side's hand. The seat sits in the {@code facing} direction from the table
     * centre; cards are placed {@link #PLAYER_Z_OFFSET} toward that seat and spread along the
     * horizontal axis perpendicular to {@code facing}.
     *
     * @param ownerId UUID of the player who owns this hand (may be null if offline); used
     *                to apply per-player card visibility (owner sees face-up, others see back).
     */
    private void renderPvpHand(PvpSession session, Location center, double anchorY,
                               List<Card> hand, BlockFace facing, UUID ownerId) {
        int size = hand.size();
        if (size == 0) {
            return;
        }
        double seatX = facing.getModX() * PLAYER_Z_OFFSET;
        double seatZ = facing.getModZ() * PLAYER_Z_OFFSET;
        // The two PvP seats sit on the table's 3-long sides facing each other across the
        // 2-wide axis. The axis perpendicular to 'facing' is therefore the 3-wide axis —
        // the correct spread direction so cards fan along the long edge.
        // For NORTH/SOUTH: crossX = ±1, crossZ = 0 (spreads along X, the wide axis).
        // For EAST/WEST:   crossX = 0,  crossZ = ±1 (spreads along Z, the wide axis).
        int crossX = -facing.getModZ();
        int crossZ = facing.getModX();
        double totalWidth = (size - 1) * CARD_SPACING;
        Quaternionf rotation = flatCardRotationForFacing(facing);
        for (int i = 0; i < size; i++) {
            double off = -totalWidth / 2.0 + i * CARD_SPACING;
            Location cardLoc = new Location(center.getWorld(),
                    center.x() + seatX + crossX * off,
                    anchorY,
                    center.z() + seatZ + crossZ * off);
            spawnPvpCard(session, cardLoc, hand.get(i), rotation, ownerId);
        }
    }

    /**
     * Spawn a face-up and a face-down {@link ItemDisplay} for one PvP card at the same
     * location. The owner sees only the face-up copy; all other online players (opponent
     * and spectators) see only the face-down copy. Both UUIDs are tracked for cleanup.
     *
     * <p>If {@code ownerId} is null (owner offline), every viewer sees the face-down copy.
     *
     * <p>Known limitation: a player who joins the server mid-round after cards are spawned
     * will not have had {@code hideEntity} applied and may briefly see overlapping displays
     * until the next redraw.
     *
     * @param ownerId UUID of the player who owns this hand; may be null.
     */
    private void spawnPvpCard(PvpSession session, Location loc, Card card,
                              Quaternionf rotation, UUID ownerId) {
        World world = session.world;
        Integer backColor = session.table.backColor();

        // Spawn the face-up display (visible to the card owner only).
        ItemDisplay faceUp = (ItemDisplay) world.spawnEntity(loc, EntityType.ITEM_DISPLAY);
        faceUp.setItemStack(CardItemFactory.createCardItem(card, false, backColor));
        configurePvpDisplay(faceUp, rotation, session, ownerId);
        session.faceUpIds.add(faceUp.getUniqueId());

        // Spawn the face-down display (visible to everyone except the owner).
        ItemDisplay faceDown = (ItemDisplay) world.spawnEntity(loc, EntityType.ITEM_DISPLAY);
        faceDown.setItemStack(CardItemFactory.createCardItem(card, true, backColor));
        configurePvpDisplay(faceDown, rotation, session, ownerId);
        session.faceDownIds.add(faceDown.getUniqueId());

        // Apply initial per-viewer visibility.
        Player owner = ownerId != null ? playerOrNull(ownerId) : null;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(owner)) {
                // Owner sees face-up; hide the face-down copy.
                try { viewer.hideEntity(plugin, faceDown); } catch (Throwable ignored) {} // MockBukkit fix
            } else {
                // Everyone else sees face-down; hide the face-up copy.
                try { viewer.hideEntity(plugin, faceUp); } catch (Throwable ignored) {} // MockBukkit fix
            }
        }
    }

    /**
     * Apply shared {@link ItemDisplay} configuration to a PvP card entity: FIXED billboard
     * and transform, 0.4x scale, non-persistent, {@code cardDisplayKey} PDC tag, and
     * optional {@code cardOwnerKey} PDC string. Also adds the entity UUID to
     * {@code session.displayIds} so {@link #removePvpDisplays} picks it up.
     */
    private void configurePvpDisplay(ItemDisplay display, Quaternionf rotation,
                                     PvpSession session, UUID ownerId) {
        try {
            display.setBillboard(Display.Billboard.FIXED);
        } catch (Throwable ignored) {} // MockBukkit fix
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setTransformation(new Transformation(
                new Vector3f(),
                rotation,
                new Vector3f(0.4f, 0.4f, 0.4f),
                new Quaternionf()));
        display.setPersistent(false);
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        pdc.set(cardDisplayKey, PersistentDataType.BOOLEAN, true);
        if (ownerId != null) {
            pdc.set(cardOwnerKey, PersistentDataType.STRING, ownerId.toString());
        }
        session.displayIds.add(display.getUniqueId());
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

    private void redrawPvp(PvpSession session) {
        removePvpDisplays(session);
        renderPvpHands(session);
    }

    private void removePvpDisplays(PvpSession session) {
        for (UUID id : session.displayIds) {
            var entity = Bukkit.getEntity(id);
            if (entity instanceof ItemDisplay) {
                entity.remove();
            }
        }
        session.displayIds.clear();
        session.faceUpIds.clear();
        session.faceDownIds.clear();
    }

    /**
     * Reveal the true card faces to all online viewers at round end. For every online player:
     * show each face-up entity (owner's real card) and hide each face-down entity (the backs),
     * so everybody — both players and spectators — sees the final hands face-up.
     */
    private void revealAllPvpCards(PvpSession session) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (UUID id : session.faceUpIds) {
                var entity = Bukkit.getEntity(id);
                if (entity != null) {
                    try { viewer.showEntity(plugin, entity); } catch (Throwable ignored) {} // MockBukkit fix
                }
            }
            for (UUID id : session.faceDownIds) {
                var entity = Bukkit.getEntity(id);
                if (entity != null) {
                    try { viewer.hideEntity(plugin, entity); } catch (Throwable ignored) {} // MockBukkit fix
                }
            }
        }
    }

    // ---- PvP helpers --------------------------------------------------------

    private static Player playerOrNull(UUID id) {
        return id == null ? null : Bukkit.getPlayer(id);
    }

    private void messageBoth(PvpSession session, String miniMessage) {
        Player a = playerOrNull(session.playerA);
        Player b = playerOrNull(session.playerB);
        if (a != null) {
            a.sendMessage(MM.deserialize(miniMessage));
        }
        if (b != null) {
            b.sendMessage(MM.deserialize(miniMessage));
        }
    }

    private void notifyOpponentReady(PvpSession session, int readiedSide) {
        int other = readiedSide == 0 ? 1 : 0;
        Player opponent = playerOrNull(session.occupant(other));
        if (opponent != null) {
            opponent.sendMessage(MM.deserialize("<gray>Your opponent has locked in their stake.</gray>"));
        }
    }

    private void notifyOpponentRegistered(PvpSession session, int registeredSide) {
        int other = registeredSide == 0 ? 1 : 0;
        Player opponent = playerOrNull(session.occupant(other));
        if (opponent != null) {
            opponent.sendMessage(MM.deserialize("<gray>Your opponent has registered at the table.</gray>"));
            opponent.playSound(opponent.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        }
    }

    /** Play a sound at each seated player's location. */
    private void playBothSounds(PvpSession session, Sound sound, float volume, float pitch) {
        Player a = playerOrNull(session.playerA);
        Player b = playerOrNull(session.playerB);
        if (a != null) {
            a.playSound(a.getLocation(), sound, volume, pitch);
        }
        if (b != null) {
            b.playSound(b.getLocation(), sound, volume, pitch);
        }
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
        int payout = game.payout();
        if (payout > 0) {
            economyManager.addBalance(player.getUniqueId(), payout);
        }

        // Rakeback on dealer win (work item 4).
        String rakebackSuffix = "";
        if (game.result() == BlackjackGame.Result.DEALER_WIN) {
            double rate = rankManager.getCasinoRakebackRate(player.getUniqueId());
            if (rate > 0.0) {
                int rakeback = (int) Math.floor(game.bet() * rate);
                if (rakeback > 0) {
                    economyManager.addBalance(player.getUniqueId(), rakeback);
                    rakebackSuffix = " <gray>(Rakeback: +$" + rakeback + ")</gray>";
                }
            }
        }

        String summary = switch (game.result()) {
            case PLAYER_BLACKJACK -> "<gold><bold>BLACKJACK!</bold></gold> <green>You won $"
                    + (payout - game.bet()) + "!</green>";
            case PLAYER_WIN -> "<green>You win! Payout: $" + payout + " (net +$" + game.bet() + ")</green>";
            case PUSH -> "<yellow>Push. Your bet of $" + game.bet() + " is returned.</yellow>";
            case DEALER_WIN -> "<red>Dealer wins. You lost $" + game.bet() + ".</red>" + rakebackSuffix;
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
        pendingPvpSetups.remove(id);
        pendingRemovals.remove(id);
        // Cancel any active session.
        Session session = sessions.remove(id);
        if (session != null) {
            cancelAutoClear(session);
            removeDisplays(session);
        }
        // Handle any PvP table the player was seated at.
        handlePvpQuit(id);
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
                    player.sendMessage(MM.deserialize(
                            "<red>Your Blackjack game was ended due to 10 minutes of inactivity. "
                                    + "Your bet of $" + session.game.bet() + " was forfeited.</red>"));
                }
            }
        }

        // Sweep idle PvP rounds.
        for (PvpSession pvpSession : new ArrayList<>(pvpSessions.values())) {
            if (pvpSession.game.isFinished() || pvpSession.waitingToClear) {
                continue;
            }

            long idle = now - pvpSession.game.lastInteractionTime();
            if (pvpSession.game.state() == BlackjackPvpGame.State.ACTIVE) {
                // Active game: 10-minute timeout.
                if (idle > INACTIVITY_TIMEOUT_MS) {
                    abortPvpSession(pvpSession, "<red>Your PvP Blackjack round was ended due to "
                            + "10 minutes of inactivity. All wagers refunded.</red>");
                }
            } else {
                // Setup phase: 3-minute timeout.
                if (idle > 3L * 60L * 1000L) {
                    abortPvpSession(pvpSession, "<red>The PvP Blackjack setup was cancelled due to "
                            + "3 minutes of inactivity. All wagers refunded.</red>");
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
        pvpButtonMap.clear();

        // Refund any in-flight PvP escrow and remove PvP card displays.
        for (PvpSession session : new ArrayList<>(pvpSessions.values())) {
            cancelPvpAutoClear(session);
            refundSide(session, BlackjackPvpGame.SIDE_A);
            refundSide(session, BlackjackPvpGame.SIDE_B);
            removePvpDisplays(session);
        }
        pvpSessions.clear();
        pvpMoneyButtonMap.clear();
        pvpWagerPrompts.clear();
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
