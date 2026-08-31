package me.beeliebub.tweaks.minigames.blackjack;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.discord.DiscordAnnouncer;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.lottery.LotteryManager;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingPaths;
import me.beeliebub.tweaks.ranks.RankManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bukkit event handling and setup/removal wizard glue for physical Blackjack tables. The actual
 * work is delegated to three collaborators constructed and owned by this class:
 * <ul>
 *   <li>{@link BlackjackTableStore} — chunk-PDC table persistence and button geometry/index.</li>
 *   <li>{@link BlackjackRenderer} — table holograms, dealer mannequin, and card rendering.</li>
 *   <li>{@link BlackjackSessionManager} — session lifecycle, economy flow, inactivity sweep.</li>
 * </ul>
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
 * as the button's facing vector).
 *
 * <p>Tables are stored in chunk PDC under {@code tweaks:blackjack_tables}. On chunk
 * load a {@link TextDisplay} hologram is spawned above each table center. There is no
 * longer any {@code Interaction} entity.
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
 * and server shutdown via {@link #shutdown()} — via {@link BlackjackSessionManager#endSession},
 * the single teardown funnel all of these routes call into.
 */
public final class BlackjackListener implements Listener {

    private final JavaPlugin plugin;
    private final BlackjackTableStore tableStore;
    private final BlackjackRenderer renderer;
    private final BlackjackSessionManager sessionManager;

    /** Carries the pending bet and optional back-color for a server table being set up. */
    private record PendingSetup(int bet, Integer backColor) {}

    /** Players waiting to finalise a server table by right-clicking the MIDDLE button. */
    private final Map<UUID, PendingSetup> pendingSetups = new HashMap<>();

    /** Players waiting to remove a table by right-clicking its MIDDLE button. */
    private final Set<UUID> pendingRemovals = new HashSet<>();

    public BlackjackListener(JavaPlugin plugin, EconomyManager economyManager, HouseAccount houseAccount,
                             RankManager rankManager) {
        this(plugin, economyManager, houseAccount, rankManager, null, DiscordAnnouncer.NOOP);
    }

    public BlackjackListener(JavaPlugin plugin, EconomyManager economyManager, HouseAccount houseAccount,
                             RankManager rankManager, LotteryManager lotteryManager) {
        this(plugin, economyManager, houseAccount, rankManager, lotteryManager, DiscordAnnouncer.NOOP);
    }

    public BlackjackListener(JavaPlugin plugin, EconomyManager economyManager, HouseAccount houseAccount,
                             RankManager rankManager, LotteryManager lotteryManager,
                             DiscordAnnouncer discordAnnouncer) {
        this.plugin = plugin;
        this.tableStore = new BlackjackTableStore(plugin);
        this.renderer = new BlackjackRenderer(plugin);
        this.sessionManager = new BlackjackSessionManager(plugin, economyManager, houseAccount, rankManager,
                renderer, lotteryManager, discordAnnouncer);
    }

    // ---- Public API ---------------------------------------------------------

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
        player.sendMessage(Messages.MINIGAMES.blackjackSetupPrompt(bet));
    }

    /**
     * Put {@code player} into table-removal mode.
     * The next right-click on a registered table's MIDDLE button unregisters that
     * table and removes its hologram.
     */
    public void beginTableRemoval(Player player) {
        pendingRemovals.add(player.getUniqueId());
        player.sendMessage(Messages.MINIGAMES.blackjackRemovalPrompt());
    }

    /** Called from {@code Tweaks#onDisable} to guarantee no displays survive a stop. */
    public void shutdown() {
        sessionManager.shutdown();
        renderer.shutdownAllHolograms();
        tableStore.clearButtons();
    }

    // ---- Table activation (pairs persistence index + hologram) ---------------

    /**
     * Spawns a table's hologram and registers its buttons together — the two must always
     * happen as a pair for a table becoming "live" in a loaded chunk. Called from
     * {@link #onChunkLoad} (batch restore) and {@link #handleSetup} (new table placed
     * while its chunk is loaded).
     */
    private void activateTable(BlackjackTableStore.TableEntry entry) {
        renderer.spawnTableHologram(entry);
        tableStore.registerButtons(entry);
    }

    // ---- Chunk load / unload ------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (BlackjackTableStore.TableEntry entry : tableStore.loadTablesForChunk(chunk)) {
            activateTable(entry);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (BlackjackTableStore.TableEntry entry : tableStore.loadTablesForChunk(chunk)) {
            tableStore.unregisterButtons(entry);
        }
        renderer.removeAllHolograms(chunk);
        sessionManager.evictSessionsInChunk(chunk.getChunkKey(), event.getWorld());
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
     *   <li>Block location must be a registered table button</li>
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
        String key = BlackjackTableStore.blockKey(clicked.getLocation());

        // ---- Server-table setup mode takes precedence -----------------------
        if (pendingSetups.containsKey(playerId)) {
            event.setCancelled(true);
            handleSetup(player, clicked);
            return;
        }

        // ---- Removal mode takes precedence ----------------------------------
        if (pendingRemovals.contains(playerId)) {
            // Only a known MIDDLE button triggers removal.
            BlackjackTableStore.ButtonRef ref = tableStore.lookupButton(key);
            if (ref != null && ref.role() == BlackjackTableStore.ButtonRole.MIDDLE) {
                event.setCancelled(true);
                handleRemoval(player, ref.table());
            }
            return;
        }

        // 3. Must be a registered blackjack button for gameplay.
        BlackjackTableStore.ButtonRef ref = tableStore.lookupButton(key);
        if (ref == null) {
            return;
        }

        event.setCancelled(true);
        BlackjackTableStore.TableEntry table = ref.table();

        switch (ref.role()) {
            case MIDDLE -> sessionManager.handleMiddleClick(player, table);
            case LEFT   -> sessionManager.hit(player);
            case RIGHT  -> sessionManager.stand(player);
        }
    }

    // ---- Setup handler ------------------------------------------------------

    private void handleSetup(Player player, Block button) {
        PendingSetup pending = pendingSetups.remove(player.getUniqueId());
        int bet = pending.bet();
        Integer backColor = pending.backColor();

        // Get the button's facing direction from its block data.
        if (!(button.getBlockData() instanceof Switch sw)) {
            player.sendMessage(Messages.MINIGAMES.blackjackWallButtonRequired());
            pendingSetups.put(player.getUniqueId(), pending); // restore pending state
            return;
        }

        BlockFace facing = sw.getFacing();

        // Validate that the button is wall-mounted (not on floor/ceiling).
        if (facing == BlockFace.UP || facing == BlockFace.DOWN) {
            player.sendMessage(Messages.MINIGAMES.blackjackWallMountRequired());
            pendingSetups.put(player.getUniqueId(), pending);
            return;
        }

        // Compute the table surface centre from the button location and facing.
        Location center = BlackjackTableStore.findTableCenterFromButton(button, facing);
        if (center == null) {
            player.sendMessage(Messages.MINIGAMES.blackjackTableSurfaceRequired());
            pendingSetups.put(player.getUniqueId(), pending);
            return;
        }

        // Persist the table and bring it live in this (already-loaded) chunk.
        Location middleButtonLoc = button.getLocation();
        if (tableStore.lookupButton(BlackjackTableStore.blockKey(middleButtonLoc)) != null
                || tableStore.hasTableAtCenter(center)) {
            player.sendMessage(Messages.MINIGAMES.blackjackTableExists());
            pendingSetups.put(player.getUniqueId(), pending);
            return;
        }
        BlackjackTableStore.TableEntry entry = new BlackjackTableStore.TableEntry(
                center, bet, middleButtonLoc, facing, backColor);
        tableStore.persistTable(entry);
        activateTable(entry);

        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) eventLog.log(LoggingPaths.BLACKJACK_TABLE, () ->
                "[Blackjack] " + ConsoleEventLog.actorLabel(player.getName(), player.getUniqueId())
                        + " created table at " + center.getBlockX() + "," + center.getBlockY()
                        + "," + center.getBlockZ() + " with bet $" + bet);

        player.sendMessage(Messages.MINIGAMES.blackjackTableRegistered(
                String.format("%.1f, %.1f, %.1f", center.x(), center.y(), center.z()), bet));
    }

    // ---- Removal handler ----------------------------------------------------

    private void handleRemoval(Player player, BlackjackTableStore.TableEntry table) {
        if (sessionManager.isTableOccupied(BlackjackTableStore.blockKey(table.center()))) {
            player.sendMessage(Messages.MINIGAMES.blackjackTableBusy());
            return;
        }
        pendingRemovals.remove(player.getUniqueId());

        Location center = table.center();
        Chunk chunk = center.getChunk();

        tableStore.unpersistTable(table);
        // Multiple tables per chunk can share the same hologram list, so removal is by
        // nearest-entity proximity rather than by per-table UUID (see removeHologramNear).
        renderer.removeHologramNear(renderer.hologramAnchor(center), chunk);
        tableStore.unregisterButtons(table);

        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) eventLog.log(LoggingPaths.BLACKJACK_TABLE, () ->
                "[Blackjack] " + ConsoleEventLog.actorLabel(player.getName(), player.getUniqueId())
                        + " removed table at " + center.getBlockX() + "," + center.getBlockY()
                        + "," + center.getBlockZ());

        player.sendMessage(Messages.MINIGAMES.blackjackTableRemoved());
    }

    // ---- Cleanup paths ------------------------------------------------------

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // Cancel pending setup/removal modes.
        pendingSetups.remove(id);
        pendingRemovals.remove(id);
        // Cancel any active session.
        sessionManager.onPlayerQuit(id);
    }
}
