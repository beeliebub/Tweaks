package me.beeliebub.tweaks.minigames.roulette;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owns the plugin-spawned {@code Interaction} click-target entities laid over a board's physical
 * segments. Every spawned entity is {@code setPersistent(false)} — this plugin's overlay is never
 * saved to the world file and is always rebuilt fresh from a live segment scan (see
 * {@link RouletteListener}'s class Javadoc).
 */
final class RouletteHitboxManager {

    /** Tag every plugin-owned hitbox carries, alongside its own segment tag. */
    static final String HITBOX_TAG = "roulette_hitbox";

    private static final float MIN_HITBOX_WIDTH = 0.1f;
    private static final float HITBOX_HEIGHT = 0.25f;

    private RouletteHitboxManager() {}

    /**
     * Spawns one {@code Interaction} per scanned segment, sized from that segment's own measured
     * scale, and returns the UUID -&gt; {@link RouletteBoardStore.SegmentRef} map to register.
     *
     * <p>If any single segment fails to spawn, every entity already spawned by this call is
     * removed and {@code null} is returned — a half-spawned board (some hitboxes live, none
     * registered in the index) is worse than no board at all, since it would be indistinguishable
     * from a working one except that some clicks silently do nothing. The caller treats a
     * {@code null} return as "activation failed." The triggering exception is logged here (rather
     * than only reported as a bare failure to the caller) so an operator can actually diagnose why
     * a board refused to activate.
     */
    static Map<UUID, RouletteBoardStore.SegmentRef> spawn(JavaPlugin plugin, World world,
                                                            RouletteBoardStore.BoardEntry board,
                                                            List<RouletteSegmentScanner.SegmentScan> segments) {
        Map<UUID, RouletteBoardStore.SegmentRef> refs = new HashMap<>();
        List<Entity> spawned = new ArrayList<>();
        try {
            for (RouletteSegmentScanner.SegmentScan segment : segments) {
                RouletteGeometry.Vec3 c = segment.centroid();
                Location at = new Location(world, c.x(), c.y(), c.z());
                float width = (float) Math.max(MIN_HITBOX_WIDTH,
                        Math.max(segment.scale().x(), segment.scale().z()));

                Interaction interaction = world.spawn(at, Interaction.class, i -> {
                    i.setInteractionWidth(width);
                    i.setInteractionHeight(HITBOX_HEIGHT);
                    i.setPersistent(false);
                    i.setResponsive(true);
                    i.addScoreboardTag(HITBOX_TAG);
                    i.addScoreboardTag(segment.rawTag());
                });
                spawned.add(interaction);

                refs.put(interaction.getUniqueId(),
                        new RouletteBoardStore.SegmentRef(board, segment.type(), segment.selector()));
            }
            return refs;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Roulette: hitbox spawn failed for board at "
                    + board.center() + " after " + spawned.size() + "/" + segments.size()
                    + " segments — rolling back.", e);
            for (Entity entity : spawned) {
                entity.remove();
            }
            return null;
        }
    }

    /**
     * Removes every {@code roulette_hitbox}-tagged entity in {@code chunk} that is not in
     * {@code keep}. Meant to be called with the freshly-updated global hitbox index as
     * {@code keep}, immediately after a board's own new hitboxes are registered — spawn-then-sweep
     * (never sweep-then-spawn) is what keeps this safe when two boards share a chunk, since a
     * sibling board's live hitboxes are still in the index and therefore kept.
     *
     * <p>Each removal is individually guarded, matching {@link #despawn}'s convention in this same
     * class: one entity throwing from {@code Entity#remove()} must not abort the sweep for the
     * rest of the chunk (callers like {@code RouletteListener#activateBoard} run this inline during
     * board activation with no surrounding try/catch of their own).
     */
    static void sweepOrphans(Chunk chunk, Set<UUID> keep) {
        for (Entity entity : chunk.getEntities()) {
            if (entity.getScoreboardTags().contains(HITBOX_TAG) && !keep.contains(entity.getUniqueId())) {
                try {
                    entity.remove();
                } catch (RuntimeException e) {
                    Bukkit.getLogger().log(Level.WARNING, "Roulette: failed to remove orphaned "
                            + "hitbox " + entity.getUniqueId() + " in chunk " + chunk, e);
                }
            }
        }
    }

    /**
     * Removes every hitbox entity in {@code ids} by UUID, null-tolerant (an already-gone entity is
     * a silent no-op, not an error). The sole hitbox-removal site — {@code RouletteListener} and
     * {@code RouletteSessionManager} previously each ran their own copy of this loop; both now
     * delegate here so "one place despawns hitboxes" is actually true. Each removal is individually
     * guarded so one entity throwing from {@code Entity#remove()} can't abort the rest of the
     * batch — any entity left behind this way is still caught by the next {@link #sweepOrphans}.
     */
    static void despawn(Set<UUID> ids) {
        for (UUID id : ids) {
            try {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
            } catch (RuntimeException e) {
                // Best-effort: this entity is left for the next sweepOrphans call to catch, but the
                // failure is still logged so a persistent leak has a trail to diagnose it by.
                Bukkit.getLogger().log(Level.WARNING, "Roulette: failed to despawn hitbox " + id, e);
            }
        }
    }
}
