package me.beeliebub.tweaks.minigames.blackjack;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.minigames.cards.Card;
import me.beeliebub.tweaks.minigames.cards.CardItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns all Blackjack rendering: table holograms, the dealer {@link Mannequin}, and flat
 * card {@link ItemDisplay} entities. Every spawned entity is tracked here so that
 * {@link #removeDisplays} and {@link #shutdownAllHolograms} can guarantee no entity outlives
 * its session or the server.
 */
@SuppressWarnings("UnstableApiUsage") // Paper's Dialog API is @ApiStatus.Experimental in 26.2.
public final class BlackjackRenderer {

    /** Spacing between card centres, in blocks. */
    private static final float CARD_SPACING = 0.45f;

    /**
     * Z offset (in blocks) from table centre for a seat's hand row. In world space the
     * button faces toward the player, so the player side is +{@code facing} here.
     */
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

    /** Player name whose skin the dealer mannequin wears at game end. */
    private static final String DEALER_PROFILE_NAME = "LimeLush";

    private final JavaPlugin plugin;
    private final NamespacedKey cardDisplayKey;
    private final NamespacedKey cardOwnerKey;

    /**
     * Hologram TextDisplay UUIDs per chunk, keyed by composite world+chunk key.
     * Each registered table contributes one TextDisplay UUID.
     */
    private final Map<ChunkId, List<UUID>> tableEntities = new HashMap<>();

    public BlackjackRenderer(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cardDisplayKey = new NamespacedKey(plugin, CARD_DISPLAY_KEY_NAME);
        this.cardOwnerKey   = new NamespacedKey(plugin, CARD_OWNER_KEY_NAME);
    }

    /**
     * Composite key combining world UID and chunk key so that two chunks in different
     * worlds with the same numeric chunk key are never confused. Private to this class —
     * every public method accepts a {@link Chunk} and derives the key internally.
     */
    private record ChunkId(UUID worldId, long chunkKey) {}

    private static ChunkId chunkId(Chunk chunk) {
        return new ChunkId(chunk.getWorld().getUID(), chunk.getChunkKey());
    }

    // ---- Hologram lifecycle --------------------------------------------------

    /**
     * Spawn a {@link TextDisplay} hologram above the given table centre. Called both from
     * {@link BlackjackListener#onChunkLoad} (batch restore) and from the setup wizard when a
     * new table is placed while the chunk is loaded. Does not touch the button index —
     * callers pair this with {@link BlackjackTableStore#registerButtons}.
     */
    void spawnTableHologram(BlackjackTableStore.TableEntry entry) {
        Location center = entry.center();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        Location hologLoc = hologramAnchor(center);
        TextDisplay text = (TextDisplay) world.spawnEntity(hologLoc, EntityType.TEXT_DISPLAY);
        String betLabel = entry.bet() == 0 ? "FREE" : ("$" + entry.bet());
        text.text(Messages.MINIGAMES.blackjackTableHologram(betLabel));
        text.setBillboard(Display.Billboard.CENTER);
        text.setPersistent(false);
        text.setDefaultBackground(false);
        text.setShadowed(true);

        ChunkId cid = chunkId(center.getChunk());
        tableEntities.computeIfAbsent(cid, k -> new ArrayList<>()).add(text.getUniqueId());
    }

    /** World location of the hologram anchor for a table centred at {@code center}. */
    Location hologramAnchor(Location center) {
        return center.clone().add(0, HOLOGRAM_HEIGHT, 0);
    }

    /** Remove every tracked hologram entity for {@code chunk}. Does not touch the button index. */
    void removeAllHolograms(Chunk chunk) {
        List<UUID> ids = tableEntities.remove(chunkId(chunk));
        if (ids != null) {
            for (UUID id : ids) {
                var entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
    }

    /**
     * Finds and removes the TextDisplay hologram entity nearest to {@code target} from
     * {@code chunk}'s tracked hologram list. If no entities remain in the list for that
     * chunk after removal, the list is cleaned up.
     *
     * <p>Multiple tables can share a chunk's hologram list, so removal is by nearest-entity
     * proximity rather than a per-table UUID, since the list is not keyed per table.
     */
    void removeHologramNear(Location target, Chunk chunk) {
        ChunkId cid = chunkId(chunk);
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

    /** Remove every tracked hologram entity across every chunk. Called from {@link BlackjackListener#shutdown()}. */
    void shutdownAllHolograms() {
        for (List<UUID> ids : tableEntities.values()) {
            for (UUID id : ids) {
                var entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
        tableEntities.clear();
    }

    // ---- Card rendering geometry ----------------------------------------------

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

    // ---- Dealer mannequin -------------------------------------------------------

    /**
     * Spawn the dealer {@link Mannequin} at {@code dealerLoc}, wearing {@link #DEALER_PROFILE_NAME}'s
     * skin. Returns the entity's UUID on success, or {@code null} if spawning failed
     * (non-fatal — the caller's game continues without a mannequin).
     */
    UUID spawnDealerMannequin(World world, Location dealerLoc) {
        try {
            Mannequin dealer = (Mannequin) world.spawnEntity(dealerLoc, EntityType.MANNEQUIN);
            dealer.setProfile(ResolvableProfile.resolvableProfile().name(DEALER_PROFILE_NAME).build());
            dealer.getEquipment().clear();
            dealer.setPersistent(false);
            return dealer.getUniqueId();
        } catch (RuntimeException ex) {
            plugin.getLogger().warning(
                    "Blackjack: failed to spawn dealer mannequin at game start: " + ex.getMessage());
            return null;
        }
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
    void resolveDealerMannequin(Session session, BlackjackGame.Result result) {
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

    // ---- Card rendering -------------------------------------------------------

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
    void renderOnTable(Session session, Location tableCenter) {
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
        } catch (RuntimeException ex) {
            // MockBukkit's ItemDisplay mock doesn't implement setBillboard in some versions;
            // non-fatal on a real server, but logged so a genuine regression there isn't silent.
            plugin.getLogger().fine("Blackjack: setBillboard(FIXED) failed for a card display: " + ex.getMessage());
        }
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
    void redraw(Session session) {
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
    void removeCardDisplays(Session session) {
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
     * present. Idempotent. This is the universal cleanup hook, invoked exclusively from
     * {@link BlackjackSessionManager#endSession} — the single teardown funnel every exit path
     * (early clear, auto-clear, quit, chunk unload, shutdown, inactivity sweep) routes through —
     * so the dealer mannequin removal here guarantees no entity outlives the session.
     */
    void removeDisplays(Session session) {
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
