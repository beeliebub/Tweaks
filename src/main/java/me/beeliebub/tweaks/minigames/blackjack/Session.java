package me.beeliebub.tweaks.minigames.blackjack;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Holds a running {@link BlackjackGame} plus the entities used to render it.
 * Shared, mutable state read and written by both {@link BlackjackRenderer} (display/mannequin
 * fields) and {@link BlackjackSessionManager} (lifecycle fields), which is why fields stay
 * package-private rather than behind accessors.
 */
final class Session {
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
