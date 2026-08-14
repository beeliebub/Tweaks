package me.beeliebub.tweaks.minigames.roulette;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.discord.DiscordAnnouncer;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.lottery.LotteryManager;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingPaths;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.ranks.RankManager;
import me.beeliebub.tweaks.utils.GeometryUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Bukkit event handling and setup/removal wizard glue for physical Roulette boards, covering board
 * discovery/persistence, the betting flow, spin animation/settlement, and teardown. Mirrors
 * {@code minigames.blackjack.BlackjackListener}'s shape: owns and delegates to
 * {@link RouletteBoardStore} (world-PDC persistence + click/control index) and the stateless
 * {@link RouletteSegmentScanner} / {@link RouletteHitboxManager} helpers. {@link #deactivateBoard}
 * is the board-level teardown funnel, reached by board removal, a (should-be-unreachable) chunk
 * unload, and {@link #shutdown()} — see that method's Javadoc.
 *
 * <h2>Setup wizard</h2>
 * {@code /roulette createboard <min> <max>} puts the admin into a pending state; the next
 * right-click on a wall-mounted button or lever registers it as the board's spin control. That
 * click's own location is the scan origin — segments are discovered by radius from wherever the
 * control was clicked, not from the admin's position when the command was typed.
 *
 * <h2>Force-loading is never blocking</h2>
 * A registered board holds a plugin chunk ticket for every chunk its scan radius touches, for its
 * entire lifetime. Taking a ticket does not itself block; activation (spawning hitboxes) only
 * happens once every touched chunk is confirmed already loaded — either immediately in
 * {@link #reactivateAllBoards()} or later via the scoped {@link #onChunkLoad} handler — never
 * through a synchronous force-load. See the root {@code CLAUDE.md}'s "no blocking calls in a
 * bootstrap registrar" rule; {@link #reactivateAllBoards()} runs from exactly such a registrar.
 *
 * <h2>Hitboxes are ephemeral by design</h2>
 * Every spawned {@code Interaction} is {@code setPersistent(false)} — the physical board is
 * someone else's build; this plugin's overlay is never saved to the world file and is always
 * rebuilt fresh from a live segment scan, on every activation.
 */
public final class RouletteListener implements Listener {

    private final JavaPlugin plugin;
    private final RouletteBoardStore boardStore;
    private final RouletteRestPoseStore restPoseStore;
    private final RouletteRenderer renderer;
    private final RouletteSessionManager sessions;

    private record PendingBoardSetup(int minBet, int maxBet) {}

    private final Map<UUID, PendingBoardSetup> pendingSetups = new HashMap<>();
    private final Set<UUID> pendingRemovals = new HashSet<>();
    private final Set<UUID> pendingDiscordDesignations = new HashSet<>();

    /** Dedupes the "control block is no longer a button/lever" warning per board. */
    private final Set<RouletteBoardStore.BoardEntry> loggedBrokenControl = new HashSet<>();

    /** Dedupes the "lost its chunk ticket" warning per board — see {@link #onChunkUnload}. */
    private final Set<RouletteBoardStore.BoardEntry> loggedLostTicket = new HashSet<>();

    public RouletteListener(JavaPlugin plugin, EconomyManager economyManager,
                             HouseAccount houseAccount, RankManager rankManager) {
        this(plugin, economyManager, houseAccount, rankManager, null, DiscordAnnouncer.NOOP);
    }

    public RouletteListener(JavaPlugin plugin, EconomyManager economyManager,
                             HouseAccount houseAccount, RankManager rankManager,
                             LotteryManager lotteryManager) {
        this(plugin, economyManager, houseAccount, rankManager, lotteryManager, DiscordAnnouncer.NOOP);
    }

    public RouletteListener(JavaPlugin plugin, EconomyManager economyManager,
                             HouseAccount houseAccount, RankManager rankManager,
                             LotteryManager lotteryManager, DiscordAnnouncer discordAnnouncer) {
        this.plugin = plugin;
        this.boardStore = new RouletteBoardStore(plugin);
        this.restPoseStore = new RouletteRestPoseStore(plugin);
        this.renderer = new RouletteRenderer(plugin, houseAccount, restPoseStore);
        this.sessions = new RouletteSessionManager(
                plugin, economyManager, houseAccount, rankManager, boardStore, renderer, restPoseStore,
                lotteryManager, discordAnnouncer);
    }

    // ---- Public API -----------------------------------------------------------------

    /** Puts {@code admin} into board-setup mode. The next right-click on a wall-mounted button or
     *  lever finalizes the board: scans segments from that click's location, persists, and
     *  activates it. */
    public void beginBoardSetup(Player admin, int minBet, int maxBet) {
        UUID id = admin.getUniqueId();
        pendingRemovals.remove(id); // a fresh setup supersedes any pending removal for this admin
        pendingDiscordDesignations.remove(id);
        pendingSetups.put(id, new PendingBoardSetup(minBet, maxBet));
        admin.sendMessage(Messages.MINIGAMES.rouletteControlSetupPrompt(minBet, maxBet));
    }

    /** Puts {@code admin} into board-removal mode. The next right-click on a registered board's
     *  spin control unregisters that board. */
    public void beginBoardRemoval(Player admin) {
        UUID id = admin.getUniqueId();
        pendingSetups.remove(id); // a fresh removal supersedes any pending setup for this admin
        pendingDiscordDesignations.remove(id);
        pendingRemovals.add(id);
        admin.sendMessage(Messages.MINIGAMES.rouletteRemovalPrompt());
    }

    /** Puts {@code admin} into Discord-designation mode for the next registered board control click. */
    public void beginDiscordDesignation(Player admin) {
        UUID id = admin.getUniqueId();
        pendingSetups.remove(id);
        pendingRemovals.remove(id);
        pendingDiscordDesignations.add(id);
        admin.sendMessage(Messages.MINIGAMES.rouletteDiscordDesignationPrompt());
    }

    /** Clears the current Discord designation directly; no board click is required. */
    public void clearDiscordDesignation(Player admin) {
        List<RouletteBoardStore.BoardEntry> designated = boardStore.designatedBoards();
        if (designated.isEmpty()) {
            admin.sendMessage(Messages.MINIGAMES.rouletteDiscordBoardNotDesignated());
            return;
        }
        if (designated.stream().anyMatch(sessions::hasRoundInFlight)) {
            admin.sendMessage(Messages.MINIGAMES.rouletteBoardBusy());
            return;
        }
        try {
            for (RouletteBoardStore.BoardEntry current : designated) {
                RouletteBoardStore.BoardEntry persisted = boardStore.setDiscordDesignation(current, false);
                if (persisted == null) {
                    throw new IllegalStateException("designated board row was not found");
                }
                swapBoardIdentity(current, persisted);
            }
            admin.sendMessage(Messages.MINIGAMES.rouletteDiscordDesignationCleared());
            logDiscordDesignationChange(admin, "cleared", designated.get(0));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Roulette: failed to clear Discord designation for "
                    + describeLocation(designated.get(0).center()), e);
            admin.sendMessage(Messages.MINIGAMES.rouletteDiscordDesignationFailed());
        }
    }

    /** Sets {@code admin}'s (in practice, any player's) sticky Roulette stake, consumed by the next
     *  segment click at any board. Called from {@link RouletteCommand}'s {@code stake} subcommand. */
    public void setStickyStake(Player player, int amount) {
        sessions.setStake(player.getUniqueId(), amount);
    }

    /** UUID-only status snapshot for the optional Discord bridge. */
    public DiscordBoardStatus discordBoardStatus() {
        return sessions.discordGateway().discordBoardStatus();
    }

    /** Main-thread UUID-only Discord betting operation. */
    public DiscordBetResult placeDiscordBet(UUID playerId, BetType type, int selector, int amount) {
        return sessions.discordGateway().placeDiscordBet(playerId, type, selector, amount);
    }

    /** Returns this Discord-linked bettor's current bets on the designated board. */
    public List<RouletteBet> discordBetsFor(UUID playerId) {
        return sessions.discordGateway().discordBetsFor(playerId);
    }

    /**
     * Wired into {@code Tweaks#onDisable()} via {@code MinigamesBootstrap.shutdown}. Tears
     * down every active board through {@link #deactivateBoard} — settling any round mid-
     * {@code SPINNING} and refunding any round still {@code BETTING} (the only refund path in
     * this feature) — WITHOUT releasing chunk tickets: Paper drops a plugin's tickets on disable
     * regardless, and releasing them first could unload the very chunks holding the entities this
     * teardown still needs to restore/remove. {@code sessions.shutdown()} is a defensive catch-all
     * for a context that can outlive its board's own runtime (e.g. a misaligned board deactivated
     * mid-linger); {@code renderer.shutdown()} and {@code boardStore.clearAll()} run last, in that
     * order, since the despawn/restore work above still needs both classes' in-memory state.
     */
    public void shutdown() {
        for (RouletteBoardStore.BoardEntry board : boardStore.activeBoards()) {
            try {
                deactivateBoard(board, RouletteTeardownPolicy.EndReason.SHUTDOWN, false);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Roulette: failed to deactivate board at "
                        + describeLocation(board.center()) + " during shutdown — continuing with "
                        + "the remaining boards.", e);
            }
        }
        // Each of these three is isolated from the others (matching Tweaks#runShutdownStep's and
        // MinigamesBootstrap.shutdown's own per-participant isolation one level up): sessions
        // .shutdown() in particular starts with an unguarded scheduler-cancel call, and letting it
        // throw must not also skip renderer.shutdown()'s ball/hologram/glow cleanup or
        // boardStore.clearAll()'s in-memory reset.
        try {
            sessions.shutdown();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Roulette: sessions.shutdown() failed", e);
        }
        try {
            renderer.shutdown();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Roulette: renderer.shutdown() failed", e);
        }
        try {
            boardStore.clearAll();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Roulette: boardStore.clearAll() failed", e);
        }
    }

    /**
     * The board-level teardown funnel — always calls {@link
     * RouletteSessionManager#endRound} FIRST, before {@link RouletteBoardStore#clearBoardRuntime}
     * removes the board's rest-pose snapshot the funnel needs, then despawns hitboxes through the
     * sole {@link RouletteHitboxManager#despawn} site, then delegates the rest of the visual
     * teardown to {@link RouletteRenderer#onBoardRemoved}. Ticket release, when requested, runs in
     * a {@code finally} so a failure anywhere above it can never leak a permanent force-load
     * ticket — mirroring the guarantee {@link #handleBoardRemoval} already established. Board
     * removal is the only caller that ever passes {@code releaseTickets = true}: a chunk unload
     * and a shutdown both need the board to stay force-loaded and reactivatable.
     */
    private void deactivateBoard(RouletteBoardStore.BoardEntry board, RouletteTeardownPolicy.EndReason reason,
                                  boolean releaseTickets) {
        try {
            sessions.endRound(board, reason);
            List<RouletteSegmentScanner.SegmentScan> restPose = boardStore.restPose(board);
            // boardStore.clearBoardRuntime(board) is evaluated (and fully completes, flipping
            // isActive false) BEFORE despawn() is invoked — Java evaluates arguments left-to-right
            // — so a throw from despawn or from renderer.onBoardRemoved below can never leave this
            // board stranded as "active" with no working hitboxes; isActive is already correct by
            // then regardless of what happens next in this method.
            RouletteHitboxManager.despawn(boardStore.clearBoardRuntime(board));
            if (reason == RouletteTeardownPolicy.EndReason.BOARD_REMOVED) {
                // Only a permanent removal retires these dedupe entries — a transient chunk-unload
                // or shutdown deactivation must not un-suppress a warning it just logged (or is
                // about to log) for a board that is still fully registered.
                loggedBrokenControl.remove(board);
                loggedLostTicket.remove(board);
            }
            renderer.onBoardRemoved(board, restPose);
        } finally {
            if (releaseTickets) {
                releaseChunkTickets(board);
            }
        }
    }

    /**
     * Called once from {@code MinigamesBootstrap.register} at plugin enable. Takes every
     * persisted board's chunk tickets and activates it immediately if its whole footprint happens
     * to already be loaded; otherwise the scoped {@link #onChunkLoad} handler activates it once
     * the ticket brings the rest of its chunks in. Never performs a blocking chunk load itself.
     */
    public void reactivateAllBoards() {
        for (World world : Bukkit.getWorlds()) {
            for (RouletteBoardStore.BoardEntry board : boardStore.loadBoardsForWorld(world)) {
                try {
                    takeChunkTickets(board);
                    if (allTouchedChunksLoaded(board)) {
                        tryActivate(board);
                    }
                } catch (RuntimeException e) {
                    // One corrupted board must not abort the whole bootstrap tier — every other
                    // package still needs to register after this one.
                    plugin.getLogger().log(Level.SEVERE, "Roulette: failed to reactivate board at "
                            + describeLocation(board.center())
                            + " — leaving it inactive and continuing with other boards.", e);
                }
            }
        }
    }

    // ---- Chunk load: scoped reactivation + orphan sweep -------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        long key = GeometryUtil.chunkKey(chunk.getX(), chunk.getZ());
        boolean touchesAnyBoard = false;
        for (RouletteBoardStore.BoardEntry board : boardStore.loadBoardsForWorld(chunk.getWorld())) {
            if (!contains(RouletteBoardStore.chunkKeysTouchedBy(board), key)) {
                continue;
            }
            touchesAnyBoard = true;
            if (!boardStore.isActive(board)) {
                try {
                    reassertChunkTickets(board);
                    if (allTouchedChunksLoaded(board)) {
                        tryActivate(board);
                    }
                } catch (RuntimeException e) {
                    // One board's failure must not stop a sibling board sharing this chunk from
                    // being considered, nor skip the orphan sweep below.
                    plugin.getLogger().log(Level.SEVERE, "Roulette: failed to activate board at "
                            + describeLocation(board.center())
                            + " on chunk load — leaving it inactive.", e);
                }
            }
        }
        if (touchesAnyBoard) {
            try {
                // Also catches orphans left behind by a crash, for a board that was already
                // active before this particular chunk of its footprint finished loading.
                RouletteHitboxManager.sweepOrphans(chunk, boardStore.allKnownHitboxIds());
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Roulette: orphan sweep failed for chunk "
                        + chunk.getX() + "," + chunk.getZ() + " in " + chunk.getWorld().getName(), e);
            }
        }
    }

    /**
     * This should be unreachable — a registered board holds a plugin chunk ticket for
     * its whole footprint for its entire lifetime — but is handled anyway rather than
     * left as a silent gap. Only an {@code isActive} board is deactivated: an inactive board's
     * chunk unloading is unremarkable (it was never activated, or a normal {@code shutdown()} has
     * already cleared it), so gating here also prevents a duplicate teardown racing shutdown's own
     * world-chunk unload sequence. Never releases the chunk ticket — the board stays persisted and
     * ticketed, and {@link #onChunkLoad} reactivates it the moment its footprint reloads.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        long key = GeometryUtil.chunkKey(chunk.getX(), chunk.getZ());
        for (RouletteBoardStore.BoardEntry board : boardStore.loadBoardsForWorld(chunk.getWorld())) {
            if (!boardStore.isActive(board) || !contains(RouletteBoardStore.chunkKeysTouchedBy(board), key)) {
                continue;
            }
            if (loggedLostTicket.add(board)) {
                plugin.getLogger().warning("Roulette: board at " + describeLocation(board.center())
                        + " lost its chunk ticket — chunk " + chunk.getX() + "," + chunk.getZ()
                        + " in " + chunk.getWorld().getName() + " is unloading while the board is "
                        + "still active. This should be unreachable (a board holds a permanent "
                        + "chunk ticket) — deactivating the board headlessly; it will reactivate on the "
                        + "next chunk load.");
            }
            try {
                deactivateBoard(board, RouletteTeardownPolicy.EndReason.CHUNK_UNLOAD, false);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Roulette: failed to deactivate board at "
                        + describeLocation(board.center()) + " on chunk unload.", e);
            }
        }
    }

    // ---- Main interaction handler ------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // PlayerInteractEvent can fire for both hands on one physical click
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        // Cheap material check before any map lookup — only buttons/levers can ever be a spin
        // control, and Tag.BUTTONS alone excludes levers (this feature allows either).
        if (!Tag.BUTTONS.isTagged(clicked.getType()) && clicked.getType() != Material.LEVER) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String key = RouletteBoardStore.blockKey(clicked.getLocation());

        if (pendingSetups.containsKey(playerId)) {
            event.setCancelled(true);
            handleBoardSetup(player, clicked);
            return;
        }

        if (pendingRemovals.contains(playerId)) {
            RouletteBoardStore.BoardEntry board = boardStore.lookupControl(key);
            if (board != null) {
                event.setCancelled(true);
                handleBoardRemoval(player, board);
            }
            return;
        }

        if (pendingDiscordDesignations.contains(playerId)) {
            event.setCancelled(true);
            handleDiscordDesignation(player, clicked);
            return;
        }

        RouletteBoardStore.BoardEntry board = boardStore.lookupControl(key);
        if (board == null) {
            return;
        }
        if (!(clicked.getBlockData() instanceof Switch)) {
            if (loggedBrokenControl.add(board)) {
                plugin.getLogger().warning("Roulette: control block for the board at "
                        + describeLocation(board.center()) + " is no longer a button/lever. "
                        + "The board still resolves on its own timer, but has no admin "
                        + "force-spin control until re-registered.");
            }
            return;
        }

        event.setCancelled(true);
        if (!player.hasPermission(Permissions.ROULETTE_FORCESPIN)) {
            player.sendMessage(Messages.noPermission());
            return;
        }
        // The control is admin-only and routes through the exact same beginSpin path
        // as a window naturally expiring — never a parallel one. A board deactivated (e.g. after
        // an incomplete rest-pose restore) refuses bets and must refuse a forced spin too — its
        // hitbox↔segment mapping is untrustworthy, not just its click targets.
        if (!boardStore.isActive(board)) {
            player.sendMessage(Messages.MINIGAMES.rouletteForceSpinBoardInactive());
            return;
        }
        try {
            RouletteSessionManager.ForceSpinResult result = sessions.forceSpin(board);
            switch (result) {
                case SPUN -> player.sendMessage(Messages.MINIGAMES.rouletteForceSpinTriggered());
                case NOTHING_TO_SPIN -> player.sendMessage(Messages.MINIGAMES.rouletteForceSpinNothingToSpin());
                case ALREADY_SPINNING -> player.sendMessage(Messages.MINIGAMES.rouletteForceSpinAlreadySpinning());
            }
        } catch (RuntimeException e) {
            // Without this, an uncaught exception here is silently swallowed by Bukkit's event
            // dispatcher and the admin gets zero feedback — matches onHitboxClick's precedent.
            plugin.getLogger().log(Level.SEVERE, "Roulette: force-spin failed for " + player.getName()
                    + " on board at " + describeLocation(board.center()), e);
            player.sendMessage(Messages.MINIGAMES.rouletteForceSpinFailed());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHitboxClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // PlayerInteractEntityEvent fires for both hands on one physical click
        }
        RouletteBoardStore.SegmentRef ref = boardStore.lookupHitbox(event.getRightClicked().getUniqueId());
        if (ref == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        try {
            sessions.handleSegmentClick(player, ref);
        } catch (RuntimeException e) {
            // Without this, an uncaught exception here is silently swallowed by Bukkit's event
            // dispatcher and the player gets zero feedback — matches this package's precedent in
            // RouletteScanCommand.onCommand.
            plugin.getLogger().log(Level.SEVERE, "Roulette: bet placement failed for "
                    + player.getName() + " on " + ref.type() + " " + ref.selector(), e);
            player.sendMessage(Messages.MINIGAMES.rouletteBetRejected());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pendingSetups.remove(id);
        pendingRemovals.remove(id);
        pendingDiscordDesignations.remove(id);
        sessions.onPlayerQuit(id);
    }

    // ---- Setup handler ------------------------------------------------------------------

    /**
     * Every rejection branch below restores {@code pendingSetups} so the admin can simply retry
     * (fix the click, fix the build, or re-run with different bounds) without re-typing the whole
     * command — only a successful registration consumes the pending state for good.
     */
    private int scanRadius() {
        int radius = plugin.getConfig().getInt("minigames.roulette.scan-radius", 16);
        return radius >= 1 ? radius : 16;
    }

    private void handleBoardSetup(Player admin, Block clicked) {
        UUID adminId = admin.getUniqueId();
        PendingBoardSetup pending = pendingSetups.remove(adminId);

        // A wheel physically rotating right now (any board, not just one sharing this build) could
        // be captured mid-rotation by the scan below, persisting a wrong wheel centre forever.
        if (sessions.anySpinInProgress()) {
            admin.sendMessage(Messages.MINIGAMES.rouletteSpinInProgressElsewhere());
            pendingSetups.put(adminId, pending);
            return;
        }

        if (!(clicked.getBlockData() instanceof Switch sw)) {
            admin.sendMessage(Messages.MINIGAMES.rouletteWallButtonRequired());
            pendingSetups.put(adminId, pending);
            return;
        }
        BlockFace facing = sw.getFacing();
        if (facing == BlockFace.UP || facing == BlockFace.DOWN) {
            admin.sendMessage(Messages.MINIGAMES.rouletteWallMountRequired());
            pendingSetups.put(adminId, pending);
            return;
        }

        Location controlLoc = clicked.getLocation();
        // Live read (no constructor-time caching), but the resulting radius is then baked into
        // the persisted BoardEntry below - only affects boards registered AFTER a /tconfig edit,
        // never a board's already-persisted scanRadius.
        int scanRadius = scanRadius();
        RouletteSegmentScanner.ScanOutcome outcome =
                RouletteSegmentScanner.scan(controlLoc, scanRadius, plugin);
        if (!outcome.isComplete()) {
            admin.sendMessage(Messages.MINIGAMES.rouletteBoardIncomplete(outcome.problems()));
            pendingSetups.put(adminId, pending);
            return;
        }

        RouletteGeometry.Vec3 centre = outcome.wheelCentre();
        Location center = new Location(controlLoc.getWorld(), centre.x(), centre.y(), centre.z());
        RouletteBoardStore.BoardEntry entry = new RouletteBoardStore.BoardEntry(
                center, pending.minBet(), pending.maxBet(), scanRadius, controlLoc, facing, false);

        if (!boardStore.persistBoard(entry)) {
            admin.sendMessage(Messages.MINIGAMES.rouletteBoardAlreadyRegistered());
            pendingSetups.put(adminId, pending);
            return;
        }
        try {
            takeChunkTickets(entry);
            if (!activateBoard(entry, outcome)) {
                // Brand-new registration that never came alive — unwind rather than leaving a
                // persisted, permanently-ticketed board with no working hitboxes behind forever.
                releaseChunkTickets(entry);
                boardStore.unpersistBoard(entry);
                admin.sendMessage(Messages.MINIGAMES.rouletteBoardActivationFailed());
                pendingSetups.put(adminId, pending);
                return;
            }
        } catch (RuntimeException e) {
            // Without this, an exception from taking chunk tickets (or, in principle, from
            // activateBoard itself, though that method now catches internally and returns false
            // instead) would propagate out of onPlayerInteract uncaught, silently swallowed by
            // Bukkit's event dispatcher: the admin gets zero feedback, pendingSetups is never
            // restored, and — since persistBoard above already succeeded — a live board entry
            // could be left in the world PDC with no working hitboxes and no ticket. Unwind exactly
            // like the !activateBoard(...) branch above; both calls are safe no-ops if this entry
            // was never actually ticketed/activated.
            releaseChunkTickets(entry);
            boardStore.unpersistBoard(entry);
            plugin.getLogger().log(Level.SEVERE, "Roulette: board setup failed for " + admin.getName()
                    + " at " + describeLocation(entry.center()), e);
            admin.sendMessage(Messages.MINIGAMES.rouletteBoardActivationFailed());
            pendingSetups.put(adminId, pending);
            return;
        }

        admin.sendMessage(Messages.MINIGAMES.rouletteBoardRegistered(
                String.format(Locale.ROOT, "%.1f, %.1f, %.1f", center.x(), center.y(), center.z()),
                pending.minBet(), pending.maxBet()));
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) eventLog.log(LoggingPaths.ROULETTE_BOARD, () ->
                "[Roulette] " + ConsoleEventLog.actorLabel(admin.getName(), admin.getUniqueId())
                        + " created board at " + describeLocation(center) + " with bets $"
                        + pending.minBet() + "-$" + pending.maxBet());
    }

    private void handleDiscordDesignation(Player admin, Block clicked) {
        RouletteBoardStore.BoardEntry selected = boardStore.lookupControl(
                RouletteBoardStore.blockKey(clicked.getLocation()));
        if (selected == null) {
            admin.sendMessage(Messages.MINIGAMES.rouletteDiscordBoardNotFound());
            return;
        }
        List<RouletteBoardStore.BoardEntry> currentDesignated = boardStore.designatedBoards();
        boolean selectedAlready = currentDesignated.stream()
                .anyMatch(entry -> RouletteBoardStore.samePhysicalBoard(entry, selected));
        if (selectedAlready && currentDesignated.size() == 1) {
            pendingDiscordDesignations.remove(admin.getUniqueId());
            admin.sendMessage(Messages.MINIGAMES.rouletteDiscordAlreadyDesignated());
            return;
        }
        if (sessions.hasRoundInFlight(selected)
                || currentDesignated.stream().anyMatch(sessions::hasRoundInFlight)) {
            admin.sendMessage(Messages.MINIGAMES.rouletteBoardBusy());
            return;
        }
        if (!boardStore.isActive(selected)) {
            admin.sendMessage(Messages.MINIGAMES.rouletteDiscordBoardUnavailable());
            return;
        }

        RouletteBoardStore.BoardEntry selectedRuntime = selected;
        RouletteBoardStore.BoardEntry selectedReplacement = null;
        try {
            for (RouletteBoardStore.BoardEntry current : currentDesignated) {
                RouletteBoardStore.BoardEntry currentReplacement = boardStore.setDiscordDesignation(current, false);
                if (currentReplacement == null) {
                    throw new IllegalStateException("previous Discord designation row was not found");
                }
                swapBoardIdentity(current, currentReplacement);
                if (RouletteBoardStore.samePhysicalBoard(current, selected)) {
                    selectedRuntime = currentReplacement;
                }
            }
            selectedReplacement = boardStore.setDiscordDesignation(selectedRuntime, true);
            if (selectedReplacement == null) {
                throw new IllegalStateException("new Discord designation row was not found");
            }
            swapBoardIdentity(selectedRuntime, selectedReplacement);
            pendingDiscordDesignations.remove(admin.getUniqueId());
            admin.sendMessage(Messages.MINIGAMES.rouletteDiscordDesignationSet());
            logDiscordDesignationChange(admin, "set", selected);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Roulette: failed to move Discord designation to "
                    + describeLocation(selected.center()), e);
            if (selectedReplacement != null) {
                try {
                    boardStore.setDiscordDesignation(selectedReplacement, false);
                    swapBoardIdentity(selectedReplacement, selectedRuntime);
                } catch (RuntimeException rollback) {
                    plugin.getLogger().log(Level.SEVERE, "Roulette: failed to restore selected board after "
                            + "Discord designation failure at " + describeLocation(selected.center()), rollback);
                }
            }
            admin.sendMessage(Messages.MINIGAMES.rouletteDiscordDesignationFailed());
        }
    }

    /** Migrates one active board's immutable runtime identity while retaining its chunk ownership. */
    private void swapBoardIdentity(RouletteBoardStore.BoardEntry oldEntry,
                                   RouletteBoardStore.BoardEntry newEntry) {
        if (oldEntry == null || RouletteBoardStore.samePhysicalBoard(oldEntry, newEntry)
                && oldEntry.discordEnabled() == newEntry.discordEnabled()) {
            return;
        }
        if (!boardStore.isActive(oldEntry)) {
            return;
        }

        boolean newOwnershipRegistered = false;
        try {
            deactivateBoard(oldEntry, RouletteTeardownPolicy.EndReason.RECYCLED, false);
            boardStore.unregisterControl(oldEntry);
            // Register the replacement before releasing the old identity. Overlapping board
            // footprints are ref-counted by BoardStore, so this cannot drop a needed ticket.
            takeChunkTickets(newEntry);
            newOwnershipRegistered = true;
            if (!allTouchedChunksLoaded(newEntry)) {
                throw new IllegalStateException("replacement board footprint is not fully loaded");
            }
            RouletteSegmentScanner.ScanOutcome outcome =
                    RouletteSegmentScanner.scan(newEntry.center(), newEntry.scanRadius(), plugin);
            if (!outcome.isComplete()) {
                throw new IllegalStateException("replacement board scan is incomplete: " + outcome.problems());
            }
            if (!activateBoard(newEntry, outcome)) {
                throw new IllegalStateException("replacement board hitbox activation failed");
            }
            sessions.replaceDiscordBoardIdentity(oldEntry, newEntry);
            if (loggedBrokenControl.remove(oldEntry)) {
                loggedBrokenControl.add(newEntry);
            }
            if (loggedLostTicket.remove(oldEntry)) {
                loggedLostTicket.add(newEntry);
            }
            releaseChunkTickets(oldEntry);
        } catch (RuntimeException e) {
            boardStore.unregisterControl(oldEntry);
            if (newOwnershipRegistered) {
                releaseChunkTickets(newEntry);
            }
            try {
                if (!boardStore.isActive(oldEntry) && allTouchedChunksLoaded(oldEntry)) {
                    RouletteSegmentScanner.ScanOutcome restore =
                            RouletteSegmentScanner.scan(oldEntry.center(), oldEntry.scanRadius(), plugin);
                    if (restore.isComplete() && !activateBoard(oldEntry, restore)) {
                        throw new IllegalStateException("old board hitbox activation failed");
                    }
                }
            } catch (RuntimeException restoreFailure) {
                plugin.getLogger().log(Level.SEVERE, "Roulette: board identity rollback failed at "
                        + describeLocation(oldEntry.center()), restoreFailure);
            }
            throw e;
        }
    }

    private void logDiscordDesignationChange(Player admin, String action,
                                             RouletteBoardStore.BoardEntry board) {
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) {
            eventLog.log(LoggingPaths.ROULETTE_BOARD, () ->
                    "[Roulette] " + ConsoleEventLog.actorLabel(admin.getName(), admin.getUniqueId())
                            + " " + action + " Discord betting designation for "
                            + describeLocation(board.center()));
        }
    }

    // ---- Removal handler ----------------------------------------------------------------

    private void handleBoardRemoval(Player admin, RouletteBoardStore.BoardEntry board) {
        // A round mid-BETTING/SPINNING would have its already-debited stakes silently destroyed by
        // removal — refuse rather than build a second refund path (this feature allows exactly
        // one: the server-shutdown-during-betting refund). pendingRemovals is left armed so the admin
        // can just wait and re-click the same control once the round finishes.
        if (sessions.hasRoundInFlight(board)) {
            admin.sendMessage(Messages.MINIGAMES.rouletteBoardBusy());
            return;
        }
        pendingRemovals.remove(admin.getUniqueId());

        // deactivateBoard's own `finally` releases the chunk ticket even if something above it
        // throws, so a failure here can never leak a permanent force-load ticket. The success
        // message is sent only on the success path — a failure here must never tell the admin the
        // board was removed when cleanup may be partial.
        try {
            deactivateBoard(board, RouletteTeardownPolicy.EndReason.BOARD_REMOVED, true);
            boardStore.unpersistBoard(board);
            boardStore.unregisterControl(board);
            admin.sendMessage(Messages.MINIGAMES.rouletteBoardRemoved());
            ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
            if (eventLog != null) eventLog.log(LoggingPaths.ROULETTE_BOARD, () ->
                    "[Roulette] " + ConsoleEventLog.actorLabel(admin.getName(), admin.getUniqueId())
                            + " removed board at " + describeLocation(board.center()));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Roulette: error while removing board at "
                    + describeLocation(board.center()) + " — its chunk tickets will still be released.", e);
            admin.sendMessage(Messages.MINIGAMES.rouletteBoardRemovalFailed());
        }
    }

    // ---- Activation ---------------------------------------------------------------------

    private void tryActivate(RouletteBoardStore.BoardEntry board) {
        // A crash mid-spin left the build rotated; restore its rest pose BEFORE scanning, or the
        // scan would measure — and this board would activate against — the rotated geometry.
        if (!renderer.restorePersistedRestPose(board)) {
            plugin.getLogger().severe("Roulette: board at " + describeLocation(board.center())
                    + " has a persisted rest-pose snapshot that could not be fully restored (a "
                    + "segment entity is missing) — leaving it inactive.");
            return;
        }
        RouletteSegmentScanner.ScanOutcome outcome =
                RouletteSegmentScanner.scan(board.center(), board.scanRadius(), plugin);
        if (!outcome.isComplete()) {
            plugin.getLogger().severe("Roulette: board at " + describeLocation(board.center())
                    + " failed its activation scan (" + outcome.problems()
                    + ") — leaving it inactive. Fix the build and restart, or re-register it.");
            return;
        }
        activateBoard(board, outcome);
    }

    /**
     * Spawns this board's hitboxes and registers them together, then sweeps every chunk the board
     * touches for any {@code roulette_hitbox} entity not currently known to belong to ANY board.
     * Spawn-then-sweep (never sweep-then-spawn) is what makes this safe when two boards share a
     * chunk: a sibling board's live hitboxes are already back in the index by the time the sweep
     * runs, so they're kept rather than mistaken for orphans.
     *
     * @return {@code false} if hitbox spawning failed (nothing is registered/persisted-looking in
     *         that case — see {@link RouletteHitboxManager#spawn}); {@code true} once the board is
     *         fully live.
     */
    private boolean activateBoard(RouletteBoardStore.BoardEntry board, RouletteSegmentScanner.ScanOutcome outcome) {
        World world = board.center().getWorld();

        // Glow must be clear BEFORE hitboxes are spawned/registered, not after — a
        // click landing in the window between spawn and onBoardActivated's own clear must never be
        // accepted against a still-glowing board (e.g. one recovering from a crash mid-spin). Glow
        // clearing has no dependency on hitboxes existing yet.
        try {
            renderer.clearAllGlow(outcome.segments());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Roulette: pre-activation glow clear failed for "
                    + "board at " + describeLocation(board.center()), e);
        }

        Map<UUID, RouletteBoardStore.SegmentRef> refs =
                RouletteHitboxManager.spawn(plugin, world, board, outcome.segments());
        if (refs == null) {
            plugin.getLogger().severe("Roulette: hitbox spawn failed for board at "
                    + describeLocation(board.center()) + " — leaving it inactive.");
            return false;
        }
        try {
            boardStore.registerHitboxes(board, refs);
            boardStore.storeRestPose(board, outcome.segments());
            boardStore.registerControl(board);

            Set<UUID> keep = boardStore.allKnownHitboxIds();
            for (long key : RouletteBoardStore.chunkKeysTouchedBy(board)) {
                Chunk chunk = world.getChunkAt(GeometryUtil.chunkX(key), GeometryUtil.chunkZ(key));
                RouletteHitboxManager.sweepOrphans(chunk, keep);
            }
        } catch (RuntimeException e) {
            // A failure anywhere in this block (hitboxes were spawned and possibly indexed, but
            // activation never completed) must not leave a half-registered board behind — roll
            // back exactly like the RouletteHitboxManager#spawn == null case above, so this
            // method's "false means nothing playable is left behind" contract holds regardless of
            // which line failed.
            plugin.getLogger().log(Level.SEVERE, "Roulette: activation failed for board at "
                    + describeLocation(board.center()) + " after hitboxes were spawned — rolling "
                    + "back.", e);
            RouletteHitboxManager.despawn(refs.keySet());
            boardStore.clearBoardRuntime(board);
            boardStore.unregisterControl(board);
            return false;
        }

        try {
            renderer.onBoardActivated(board, outcome.segments());
        } catch (RuntimeException e) {
            // The board is still fully live and playable without its glow-clear/holograms; a
            // rendering hiccup here must not undo the hitbox registration above.
            plugin.getLogger().log(Level.WARNING, "Roulette: post-activation rendering failed for "
                    + "board at " + describeLocation(board.center()), e);
        }
        return true;
    }

    // ---- Chunk tickets --------------------------------------------------------------------

    /** Ref-counted through {@link RouletteBoardStore#registerChunkOwnership} — only actually takes
     *  a ticket for a chunk no other registered board already owns, so two boards sharing a chunk
     *  never race each other's ticket lifecycle. */
    private void takeChunkTickets(RouletteBoardStore.BoardEntry board) {
        World world = board.center().getWorld();
        Set<Long> newlyOwned = boardStore.registerChunkOwnership(board);
        Set<Long> taken = new HashSet<>();
        try {
            for (long key : newlyOwned) {
                world.addPluginChunkTicket(GeometryUtil.chunkX(key), GeometryUtil.chunkZ(key), plugin);
                taken.add(key);
            }
        } catch (RuntimeException e) {
            for (long key : boardStore.unregisterChunkOwnership(board)) {
                if (taken.contains(key)) {
                    try {
                        world.removePluginChunkTicket(GeometryUtil.chunkX(key), GeometryUtil.chunkZ(key), plugin);
                    } catch (RuntimeException cleanup) {
                        plugin.getLogger().log(Level.SEVERE, "Roulette: failed to roll back chunk ticket "
                                + key + " for " + describeLocation(board.center()), cleanup);
                    }
                }
            }
            throw e;
        }
    }

    /** Ref-counted through {@link RouletteBoardStore#unregisterChunkOwnership} — only actually
     *  releases a chunk's ticket once no other registered board still needs it. */
    private void releaseChunkTickets(RouletteBoardStore.BoardEntry board) {
        World world = board.center().getWorld();
        for (long key : boardStore.unregisterChunkOwnership(board)) {
            try {
                world.removePluginChunkTicket(GeometryUtil.chunkX(key), GeometryUtil.chunkZ(key), plugin);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Roulette: failed to release chunk ticket " + key
                        + " for " + describeLocation(board.center()), e);
            }
        }
    }

    private void reassertChunkTickets(RouletteBoardStore.BoardEntry board) {
        World world = board.center().getWorld();
        for (long key : RouletteBoardStore.chunkKeysTouchedBy(board)) {
            try {
                world.addPluginChunkTicket(GeometryUtil.chunkX(key), GeometryUtil.chunkZ(key), plugin);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Roulette: failed to reassert chunk ticket " + key
                        + " for " + describeLocation(board.center()), e);
                throw e;
            }
        }
    }

    private boolean allTouchedChunksLoaded(RouletteBoardStore.BoardEntry board) {
        World world = board.center().getWorld();
        for (long key : RouletteBoardStore.chunkKeysTouchedBy(board)) {
            if (!world.isChunkLoaded(GeometryUtil.chunkX(key), GeometryUtil.chunkZ(key))) {
                return false;
            }
        }
        return true;
    }

    // ---- Helpers --------------------------------------------------------------------------

    private static boolean contains(long[] values, long target) {
        for (long v : values) {
            if (v == target) {
                return true;
            }
        }
        return false;
    }

    private static String describeLocation(Location loc) {
        return String.format(Locale.ROOT, "%s (%.1f, %.1f, %.1f)",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }
}
