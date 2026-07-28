package me.beeliebub.tweaks.minigames.roulette;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.utils.GeometryUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owns all Roulette spin/settlement rendering: the glow lifecycle, the ball {@link ItemDisplay}, the wheel's
 * entity-position-and-yaw rotation (never the builder's {@code Transformation} — see
 * {@code roulette/CLAUDE.md}'s rotation contract), and the two per-board holograms (house balance,
 * round status). Follows {@code blackjack.BlackjackRenderer}'s convention: every spawned entity is
 * tracked by UUID here so teardown can guarantee nothing outlives its board.
 */
final class RouletteRenderer {

    static final String BALL_TAG = "roulette_ball";
    static final String HOLO_TAG = "roulette_holo";
    static final String LABEL_TAG = "roulette_label";
    static final String MARKER_TAG = "roulette_marker";
    private static final String GLOW_TEAM_PREFIX = "roulette_glow_";

    private static final double HOUSE_HOLO_HEIGHT = 3.2;
    private static final double STATUS_HOLO_HEIGHT = 2.4;
    private static final double BALL_LIFT = 0.15;
    private static final float BALL_SCALE = 0.28f;
    private static final double SEGMENT_LABEL_LIFT = 0.5;
    private static final float LABEL_SCALE = 0.4f;
    private static final double SEGMENT_MARKER_LIFT = 0.2;
    private static final float MARKER_SCALE = 0.3f;
    private static final double MAX_RING_RESIDUAL = 0.05;
    private static final double WATCHER_RADIUS = 48.0;

    /** Spin timing — package-visible so {@link RouletteSessionManager} can schedule against it. */
    static final long FRAME_PERIOD_TICKS = 2L;
    static final int TOTAL_FRAMES = 80;
    static final long RESULT_LINGER_TICKS = 120L;

    private static final int WHEEL_KEYFRAME_EVERY = 5;
    private static final int WHEEL_KEYFRAME_TICKS = 10;
    private static final int WHEEL_TURNS = 3;
    private static final int BALL_TURNS = 7;

    private final JavaPlugin plugin;
    private final HouseAccount houseAccount;
    private final RouletteRestPoseStore restPoseStore;
    private final Scoreboard scoreboard;

    private final Map<RouletteBoardStore.BoardEntry, UUID> houseHoloByBoard = new HashMap<>();
    private final Map<RouletteBoardStore.BoardEntry, UUID> statusHoloByBoard = new HashMap<>();
    private final Map<RouletteBoardStore.BoardEntry, UUID> ballByBoard = new HashMap<>();
    private final Map<RouletteBoardStore.BoardEntry, Set<UUID>> glowingByBoard = new HashMap<>();
    private final Map<RouletteBoardStore.BoardEntry, Set<UUID>> segmentLabelsByBoard = new HashMap<>();
    private final Map<RouletteBoardStore.BoardEntry, Set<UUID>> segmentMarkersByBoard = new HashMap<>();
    private final Map<RouletteBoardStore.BoardEntry, Long> renderedHouseBalance = new HashMap<>();

    RouletteRenderer(JavaPlugin plugin, HouseAccount houseAccount, RouletteRestPoseStore restPoseStore) {
        this.plugin = plugin;
        this.houseAccount = houseAccount;
        this.restPoseStore = restPoseStore;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }

    /** Which glow team a segment is placed in. Exactly four teams, all {@code roulette_glow_*}. */
    enum GlowStyle { WIN_POCKET, WIN_DOZEN, WIN_RED, WIN_BLACK }

    /** Everything one spin's animation needs, computed once at spin start. */
    record SpinPlan(RouletteBoardStore.BoardEntry board, World world, int pocket,
                     double centreX, double centreZ, double ringRadius, double ringY,
                     boolean rotateWheel,
                     double ballStartAngleDeg, double ballSweepDeg, double pocketAngleDeg,
                     RouletteGeometry.Vec3 pocketCentroid,
                     UUID[] outerIdsByAngle, double[] outerAnglesDeg,
                     List<RouletteSegmentScanner.SegmentScan> allSegments) {}

    // ---- Board lifecycle ----------------------------------------------------------------

    /**
     * Restores every segment to its persisted rest-pose snapshot, if one exists — called BEFORE
     * the activation scan, since a crash mid-spin left the build rotated and the scan must not
     * measure that rotated pose. A missing snapshot (the common case) is a no-op returning
     * {@code true}. Returns {@code false} if a snapshot existed but at least one segment entity
     * could not be found (an incomplete restore) — the caller must leave the board inactive rather
     * than spawn hitboxes against a possibly-misaligned build.
     */
    boolean restorePersistedRestPose(RouletteBoardStore.BoardEntry board) {
        List<RouletteRestPoseStore.SegmentPose> snapshot = restPoseStore.load(board);
        if (snapshot.isEmpty()) {
            return true;
        }
        World world = board.center().getWorld();
        boolean allRestored = true;
        for (RouletteRestPoseStore.SegmentPose pose : snapshot) {
            if (!(Bukkit.getEntity(pose.entityId()) instanceof Display display)) {
                allRestored = false;
                continue;
            }
            display.setTeleportDuration(0);
            display.teleport(new Location(world, pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch()));
        }
        if (allRestored) {
            restPoseStore.clear(board);
        }
        return allRestored;
    }

    /**
     * Called after the activation scan, before hitboxes are spawned. Clears glow unconditionally
     * over every scanned segment (glow is durable on disk — a crash mid-animation with no session
     * left to clear it must still self-heal here), sweeps stray ball/hologram entities, and spawns
     * fresh holograms.
     */
    void onBoardActivated(RouletteBoardStore.BoardEntry board, List<RouletteSegmentScanner.SegmentScan> restPose) {
        clearAllGlow(restPose);
        glowingByBoard.remove(board);
        sweepOrphanRenderEntities(board);
        spawnHolograms(board);
        spawnSegmentLabels(board, restPose);
        spawnSegmentMarkers(board, restPose);
        refreshHouseBalance(board);
        refreshStatus(board, Messages.MINIGAMES.rouletteIdleBoardStatus(board.minBet(), board.maxBet()));
    }

    /** Called from board removal, inside its existing try block. */
    void onBoardRemoved(RouletteBoardStore.BoardEntry board, List<RouletteSegmentScanner.SegmentScan> restPose) {
        clearAllGlow(restPose);
        glowingByBoard.remove(board);
        removeBall(board);
        removeSegmentLabels(board);
        removeSegmentMarkers(board);
        removeHolograms(board);
    }

    /** Removes every tracked ball/hologram/label/marker, clears tracked glow, clears every map. */
    void shutdown() {
        removeAllTracked(ballByBoard);
        removeAllTracked(houseHoloByBoard);
        removeAllTracked(statusHoloByBoard);
        removeAllTrackedSets(segmentLabelsByBoard);
        removeAllTrackedSets(segmentMarkersByBoard);
        for (Set<UUID> ids : glowingByBoard.values()) {
            for (UUID id : ids) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.setGlowing(false);
                }
            }
        }
        glowingByBoard.clear();
        renderedHouseBalance.clear();
    }

    private static void removeAllTracked(Map<RouletteBoardStore.BoardEntry, UUID> byBoard) {
        for (UUID id : byBoard.values()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        byBoard.clear();
    }

    private static void removeAllTrackedSets(Map<RouletteBoardStore.BoardEntry, Set<UUID>> byBoard) {
        for (Set<UUID> ids : byBoard.values()) {
            for (UUID id : ids) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
        byBoard.clear();
    }

    /** Stray {@code roulette_ball}/{@code roulette_holo}/{@code roulette_label}/{@code roulette_marker}
     *  entities from a previous crashed run. */
    private void sweepOrphanRenderEntities(RouletteBoardStore.BoardEntry board) {
        Set<UUID> keep = new HashSet<>();
        addIfPresent(keep, ballByBoard.get(board));
        addIfPresent(keep, houseHoloByBoard.get(board));
        addIfPresent(keep, statusHoloByBoard.get(board));
        Set<UUID> labelIds = segmentLabelsByBoard.get(board);
        if (labelIds != null) {
            keep.addAll(labelIds);
        }
        Set<UUID> markerIds = segmentMarkersByBoard.get(board);
        if (markerIds != null) {
            keep.addAll(markerIds);
        }

        World world = board.center().getWorld();
        for (long key : RouletteBoardStore.chunkKeysTouchedBy(board)) {
            Chunk chunk = world.getChunkAt(GeometryUtil.chunkX(key), GeometryUtil.chunkZ(key));
            for (Entity entity : chunk.getEntities()) {
                Set<String> tags = entity.getScoreboardTags();
                boolean isTracked = tags.contains(BALL_TAG) || tags.contains(HOLO_TAG)
                        || tags.contains(LABEL_TAG) || tags.contains(MARKER_TAG);
                if (isTracked && !keep.contains(entity.getUniqueId())) {
                    entity.remove();
                }
            }
        }
    }

    private static void addIfPresent(Set<UUID> set, UUID id) {
        if (id != null) {
            set.add(id);
        }
    }

    // ---- Glow -----------------------------------------------------------------------------

    /** Unconditionally clears glow (and every glow-team entry) over every scanned segment. */
    void clearAllGlow(List<RouletteSegmentScanner.SegmentScan> segments) {
        for (RouletteSegmentScanner.SegmentScan segment : segments) {
            Entity entity = Bukkit.getEntity(segment.segmentEntityId());
            if (entity == null) {
                continue;
            }
            String key = entity.getUniqueId().toString();
            for (GlowStyle style : GlowStyle.values()) {
                Team team = scoreboard.getTeam(glowTeamName(style));
                if (team != null) {
                    team.removeEntry(key);
                }
            }
            entity.setGlowing(false);
        }
    }

    void applyGlow(RouletteBoardStore.BoardEntry board, Collection<UUID> segmentIds, GlowStyle style) {
        Team team = getOrCreateGlowTeam(style);
        Set<UUID> glowing = glowingByBoard.computeIfAbsent(board, b -> new HashSet<>());
        for (UUID id : segmentIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null) {
                continue;
            }
            team.addEntry(id.toString());
            entity.setGlowing(true);
            glowing.add(id);
        }
    }

    void clearGlow(RouletteBoardStore.BoardEntry board, Collection<UUID> segmentIds) {
        Set<UUID> glowing = glowingByBoard.get(board);
        for (UUID id : segmentIds) {
            for (GlowStyle style : GlowStyle.values()) {
                Team team = scoreboard.getTeam(glowTeamName(style));
                if (team != null) {
                    team.removeEntry(id.toString());
                }
            }
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.setGlowing(false);
            }
            if (glowing != null) {
                glowing.remove(id);
            }
        }
    }

    /**
     * Pairs {@link #spinEnd}'s restore with the "clear the persisted snapshot only on a fully
     * successful restore" rule in exactly one place — that pairing used to live split across
     * {@code RouletteSessionManager.finishSpin} and this class, and a mismatch there is precisely
     * how crash recovery breaks. No-ops (returns {@code true}) when this round never built spin
     * state at all (the decision-14 headless-settle path, or a round ending before a spin ever
     * started) and is safe to call more than once for the same context — every teleport target is
     * an absolute pose read from the snapshot, never a delta, so a repeat call is a no-op restore.
     */
    boolean finishRotation(RouletteRoundContext ctx) {
        if (ctx.spinPlan == null || ctx.restPoseSnapshot == null) {
            return true;
        }
        boolean restored = spinEnd(ctx);
        if (restored) {
            restPoseStore.clear(ctx.board);
        }
        return restored;
    }

    /**
     * The single delegation point for round-level visual teardown — the {@code removeDisplays}
     * analogue for {@code RouletteSessionManager#endRound}'s teardown funnel. Restores the wheel's rest
     * pose (via {@link #finishRotation}) and refreshes the idle status text for every reason except
     * {@link RouletteTeardownPolicy.EndReason#CHUNK_UNLOAD}/{@link RouletteTeardownPolicy.EndReason
     * #SHUTDOWN} — a chunk-unloading board must not teleport entities in a chunk that's about to go
     * away (the persisted rest-pose snapshot is left in place for the next chunk load to restore
     * instead), and a shutting-down board has no one left to show status text to. Glow and the ball
     * are always cleared: both are cheap, and glow in particular is durable on a persistent
     * {@code BlockDisplay} — leaving it set would write a glowing segment into the world file.
     */
    void endRoundVisuals(RouletteBoardStore.BoardEntry board, RouletteRoundContext ctx,
                          List<RouletteSegmentScanner.SegmentScan> restPose,
                          RouletteTeardownPolicy.EndReason reason) {
        if (reason != RouletteTeardownPolicy.EndReason.CHUNK_UNLOAD) {
            // Guarded on its own: if the rest-pose restore throws (e.g. a display entity gone
            // mid-teleport), glow/ball cleanup below must still run — the same win-glow-leak bug
            // class this package already fixed once for deactivateMisalignedBoard, reachable here
            // too since this method's own contract ("glow and the ball are always cleared") would
            // otherwise silently break for whichever call happens to hit this exception.
            try {
                finishRotation(ctx);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Roulette: rest-pose restore failed during "
                        + "teardown for board at " + board.center() + " — proceeding with glow/ball "
                        + "cleanup regardless.", e);
            }
        }
        clearAllGlow(restPose);
        glowingByBoard.remove(board);
        removeBall(board);
        if (reason != RouletteTeardownPolicy.EndReason.CHUNK_UNLOAD
                && reason != RouletteTeardownPolicy.EndReason.SHUTDOWN) {
            refreshStatus(board, Messages.MINIGAMES.rouletteIdleBoardStatus(board.minBet(), board.maxBet()));
        }
    }

    /**
     * Every touch of a {@code roulette_glow_*} team is idempotent-create-only — this class never
     * calls {@code Team#unregister()}, ever, not even on its own teams (stronger than the "never
     * unregister a team it didn't create" rule) — and never adds a player entry, so
     * {@code tab.TabManager}'s {@code cleanupTeamIfEmpty} (reachable only via
     * {@code scoreboard.getPlayerTeam(player)}) can never observe or touch one of these teams.
     */
    private Team getOrCreateGlowTeam(GlowStyle style) {
        String name = glowTeamName(style);
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
            team.color(colorFor(style));
        }
        return team;
    }

    private static String glowTeamName(GlowStyle style) {
        return GLOW_TEAM_PREFIX + style.name().toLowerCase(Locale.ROOT);
    }

    private static NamedTextColor colorFor(GlowStyle style) {
        return switch (style) {
            case WIN_POCKET -> NamedTextColor.GOLD;
            case WIN_DOZEN -> NamedTextColor.LIGHT_PURPLE;
            case WIN_RED -> NamedTextColor.RED;
            case WIN_BLACK -> NamedTextColor.GRAY; // pure black glow would be invisible
        };
    }

    // ---- Spin -----------------------------------------------------------------------------

    /** True if any player is within render distance of the board — the animate-only-when-watched
     *  invariant's check. */
    boolean hasWatcher(RouletteBoardStore.BoardEntry board) {
        World world = board.center().getWorld();
        return !world.getNearbyPlayers(board.center(), WATCHER_RADIUS).isEmpty();
    }

    /**
     * Computes everything the animation needs. The rotation centre/radius come from a Kåsa circle
     * fit over the OUTER ring only (never {@code BoardEntry.center()}, which is the mean over all
     * three rings and is off-axis for rotation purposes). {@code rotateWheel} is {@code false} —
     * layers 1+3 only, a deliberate escape hatch — when the fit's residual exceeds
     * {@link #MAX_RING_RESIDUAL} or the rest pose is empty/incomplete.
     */
    SpinPlan planSpin(RouletteBoardStore.BoardEntry board, List<RouletteSegmentScanner.SegmentScan> restPose, int pocket) {
        World world = board.center().getWorld();
        List<RouletteSegmentScanner.SegmentScan> outer = restPose.stream()
                .filter(s -> s.type() == BetType.STRAIGHT)
                .toList();
        if (outer.isEmpty()) {
            return new SpinPlan(board, world, pocket, board.center().x(), board.center().z(), 0, board.center().y(),
                    false, 0, 360.0 * BALL_TURNS, 0,
                    new RouletteGeometry.Vec3(board.center().x(), board.center().y(), board.center().z()),
                    new UUID[0], new double[0], restPose);
        }

        double[] outerX = outer.stream().mapToDouble(s -> s.centroid().x()).toArray();
        double[] outerZ = outer.stream().mapToDouble(s -> s.centroid().z()).toArray();
        double ringY = outer.stream().mapToDouble(s -> s.centroid().y()).average().orElse(board.center().y());
        RouletteGeometry.CircleFit fit = RouletteGeometry.kasaFit(outerX, outerZ);

        boolean rotate = fit.rmsResidual() <= MAX_RING_RESIDUAL;
        if (!rotate) {
            plugin.getLogger().warning("Roulette: board at " + board.center() + " ring residual "
                    + fit.rmsResidual() + " exceeds " + MAX_RING_RESIDUAL
                    + " — spinning with the ball and travelling glow only (no wheel rotation) this round.");
        }

        record Angled(UUID id, int selector, double angle, RouletteGeometry.Vec3 centroid) {}
        List<Angled> angled = new ArrayList<>();
        for (RouletteSegmentScanner.SegmentScan s : outer) {
            double angle = RouletteGeometry.angleDegrees(s.centroid().x(), s.centroid().z(), fit.centerX(), fit.centerZ());
            angled.add(new Angled(s.segmentEntityId(), s.selector(), angle, s.centroid()));
        }
        angled.sort(Comparator.comparingDouble(Angled::angle));
        UUID[] outerIdsByAngle = angled.stream().map(Angled::id).toArray(UUID[]::new);
        double[] outerAnglesDeg = angled.stream().mapToDouble(Angled::angle).toArray();

        Angled pocketEntry = angled.stream().filter(a -> a.selector() == pocket).findFirst().orElse(null);
        double pocketAngleDeg = pocketEntry != null ? pocketEntry.angle() : 0.0;
        RouletteGeometry.Vec3 pocketCentroid = pocketEntry != null ? pocketEntry.centroid()
                : new RouletteGeometry.Vec3(fit.centerX(), ringY, fit.centerZ());

        double ballStartAngleDeg = outerAnglesDeg[0];
        double ballSweep = RouletteSpinMath.ballSweepDeg(ballStartAngleDeg, pocketAngleDeg, BALL_TURNS);

        return new SpinPlan(board, world, pocket, fit.centerX(), fit.centerZ(), fit.radius(), ringY, rotate,
                ballStartAngleDeg, ballSweep, pocketAngleDeg, pocketCentroid,
                outerIdsByAngle, outerAnglesDeg, restPose);
    }

    /** Live entity {@code Location}s for every segment, captured at spin start — the exact restore target. */
    List<RouletteRestPoseStore.SegmentPose> snapshotRestPose(SpinPlan plan) {
        List<RouletteRestPoseStore.SegmentPose> poses = new ArrayList<>();
        for (RouletteSegmentScanner.SegmentScan segment : plan.allSegments()) {
            Entity entity = Bukkit.getEntity(segment.segmentEntityId());
            if (entity == null) {
                plugin.getLogger().warning("Roulette: segment " + segment.segmentEntityId()
                        + " missing at spin start for board at " + plan.board().center()
                        + " — rotation snapshot incomplete.");
                continue;
            }
            Location loc = entity.getLocation();
            poses.add(new RouletteRestPoseStore.SegmentPose(segment.segmentEntityId(),
                    loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
        }
        return poses;
    }

    /**
     * Clears any stale glow on all 48 segments, spawns the ball at its start angle, and hides
     * every segment label/marker — both are anchored to each segment's REST-POSE location and
     * don't track the wheel's rotation, so leaving them up would show them floating over a segment
     * that's visibly rotated away underneath by the time the spin is halfway done. Restored by
     * {@link #showResult} once the spin finishes, or by {@link #abortSpinVisuals} if setup fails
     * before the animation ever runs.
     */
    void spinStart(RouletteBoardStore.BoardEntry board, RouletteRoundContext ctx) {
        clearAllGlow(ctx.spinPlan.allSegments());
        glowingByBoard.remove(board);
        spawnBall(board, ctx.spinPlan);
        removeSegmentLabels(board);
        removeSegmentMarkers(board);
    }

    /** Ball every frame; a wheel keyframe every {@link #WHEEL_KEYFRAME_EVERY}th frame. */
    void spinFrame(RouletteRoundContext ctx, int frame) {
        SpinPlan plan = ctx.spinPlan;
        if (plan == null || plan.outerAnglesDeg().length == 0) {
            return;
        }
        double u = (double) frame / TOTAL_FRAMES;
        double ballAngle = RouletteSpinMath.normalize360(
                plan.ballStartAngleDeg() - RouletteSpinMath.easeOutCubic(u) * plan.ballSweepDeg());
        moveBall(ctx.board, plan, ballAngle);

        if (plan.rotateWheel() && ctx.restPoseSnapshot != null && frame % WHEEL_KEYFRAME_EVERY == 0) {
            double theta = RouletteSpinMath.wheelAngleDeg(frame, TOTAL_FRAMES, WHEEL_TURNS);
            rotateWheelToAngle(plan, ctx.restPoseSnapshot, theta, WHEEL_KEYFRAME_TICKS);
        }
    }

    /**
     * Final restore: every segment teleported back to its exact snapshotted pose verbatim (never
     * from the current pose). Returns {@code false} if any segment entity could not be found — the
     * caller must then refuse further bets on this board rather than misroute clicks against a
     * misaligned build.
     */
    boolean spinEnd(RouletteRoundContext ctx) {
        SpinPlan plan = ctx.spinPlan;
        if (!plan.rotateWheel() || ctx.restPoseSnapshot == null) {
            return true;
        }
        boolean allRestored = true;
        for (RouletteRestPoseStore.SegmentPose pose : ctx.restPoseSnapshot) {
            if (!(Bukkit.getEntity(pose.entityId()) instanceof Display display)) {
                allRestored = false;
                continue;
            }
            display.setTeleportDuration(0);
            display.teleport(new Location(plan.world(), pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch()));
        }
        return allRestored;
    }

    /**
     * Winning pocket + its dozen's 3 physical segments + its colour segment, lit; ball parked;
     * segment labels restored (hidden by {@link #spinStart} for the duration of the spin).
     */
    void showResult(RouletteRoundContext ctx) {
        SpinPlan plan = ctx.spinPlan;
        RouletteBoardStore.BoardEntry board = ctx.board;
        Set<UUID> glowing = glowingByBoard.getOrDefault(board, Set.of());
        clearGlow(board, new HashSet<>(glowing));

        List<RouletteSegmentScanner.SegmentScan> segments = plan.allSegments();
        int pocket = plan.pocket();

        applyGlow(board, segmentsMatching(segments, BetType.STRAIGHT, pocket), GlowStyle.WIN_POCKET);
        if (pocket != 0) {
            int dozen = RouletteWheel.dozenOf(pocket);
            applyGlow(board, segmentsMatching(segments, BetType.DOZEN, dozen), GlowStyle.WIN_DOZEN);
            RouletteWheel.PocketColor color = RouletteWheel.colorOf(pocket);
            int colorSelector = color == RouletteWheel.PocketColor.RED ? RouletteBet.COLOR_RED : RouletteBet.COLOR_BLACK;
            GlowStyle colorStyle = color == RouletteWheel.PocketColor.RED ? GlowStyle.WIN_RED : GlowStyle.WIN_BLACK;
            applyGlow(board, segmentsMatching(segments, BetType.COLOR, colorSelector), colorStyle);
        }

        UUID ballId = ballByBoard.get(board);
        if (ballId != null && Bukkit.getEntity(ballId) instanceof Display ball) {
            RouletteGeometry.Vec3 c = plan.pocketCentroid();
            ball.setTeleportDuration(0);
            ball.teleport(new Location(plan.world(), c.x(), c.y() + BALL_LIFT, c.z()));
        }

        spawnSegmentLabels(board, segments);
        spawnSegmentMarkers(board, segments);
    }

    /**
     * Best-effort cleanup when spin setup itself fails before any frame runs. Also restores
     * segment labels/markers if {@link #spinStart} ever got far enough to hide them — safe to call
     * even when it didn't, since {@link #spawnSegmentLabels}/{@link #spawnSegmentMarkers} always
     * clear any existing entity first.
     */
    void abortSpinVisuals(RouletteBoardStore.BoardEntry board, RouletteRoundContext ctx) {
        Set<UUID> glowing = glowingByBoard.get(board);
        if (glowing != null) {
            clearGlow(board, new HashSet<>(glowing));
        }
        removeBall(board);
        if (ctx.spinPlan != null) {
            spawnSegmentLabels(board, ctx.spinPlan.allSegments());
            spawnSegmentMarkers(board, ctx.spinPlan.allSegments());
        }
    }

    private static List<UUID> segmentsMatching(List<RouletteSegmentScanner.SegmentScan> segments, BetType type, int selector) {
        List<UUID> ids = new ArrayList<>();
        for (RouletteSegmentScanner.SegmentScan s : segments) {
            if (s.type() == type && s.selector() == selector) {
                ids.add(s.segmentEntityId());
            }
        }
        return ids;
    }

    private void rotateWheelToAngle(SpinPlan plan, List<RouletteRestPoseStore.SegmentPose> restPose,
                                     double thetaDeg, int teleportTicks) {
        for (RouletteRestPoseStore.SegmentPose pose : restPose) {
            if (!(Bukkit.getEntity(pose.entityId()) instanceof Display display)) {
                continue;
            }
            double[] xz = RouletteSpinMath.rotateAboutY(pose.x(), pose.z(), plan.centreX(), plan.centreZ(), thetaDeg);
            float yaw = RouletteSpinMath.yawAfter(pose.yaw(), thetaDeg);
            display.setTeleportDuration(teleportTicks);
            display.teleport(new Location(plan.world(), xz[0], pose.y(), xz[1], yaw, pose.pitch()));
        }
    }

    // ---- Ball -----------------------------------------------------------------------------

    private void spawnBall(RouletteBoardStore.BoardEntry board, SpinPlan plan) {
        removeBall(board);
        if (plan.outerAnglesDeg().length == 0) {
            return;
        }
        double[] xz = pointOnRing(plan, plan.ballStartAngleDeg());
        Location at = new Location(plan.world(), xz[0], plan.ringY() + BALL_LIFT, xz[1]);
        try {
            ItemDisplay ball = (ItemDisplay) plan.world().spawnEntity(at, EntityType.ITEM_DISPLAY);
            ball.setItemStack(new ItemStack(Material.SNOWBALL));
            ball.setBillboard(Display.Billboard.CENTER);
            ball.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(BALL_SCALE, BALL_SCALE, BALL_SCALE), new Quaternionf()));
            ball.setPersistent(false);
            ball.addScoreboardTag(BALL_TAG);
            ballByBoard.put(board, ball.getUniqueId());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Roulette: failed to spawn the spin ball for board at "
                    + board.center(), e);
        }
    }

    private void moveBall(RouletteBoardStore.BoardEntry board, SpinPlan plan, double angleDeg) {
        UUID id = ballByBoard.get(board);
        if (id == null || !(Bukkit.getEntity(id) instanceof Display display)) {
            return;
        }
        double[] xz = pointOnRing(plan, angleDeg);
        display.setTeleportDuration((int) FRAME_PERIOD_TICKS);
        display.teleport(new Location(plan.world(), xz[0], plan.ringY() + BALL_LIFT, xz[1]));
    }

    private void removeBall(RouletteBoardStore.BoardEntry board) {
        UUID id = ballByBoard.remove(board);
        if (id != null) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    /** Point on {@code plan}'s ring at {@code angleDeg}, per {@link RouletteGeometry#angleDegrees}'s convention. */
    private static double[] pointOnRing(SpinPlan plan, double angleDeg) {
        return RouletteSpinMath.rotateAboutY(
                plan.centreX() + plan.ringRadius(), plan.centreZ(), plan.centreX(), plan.centreZ(), angleDeg);
    }

    // ---- Holograms --------------------------------------------------------------------------

    private void spawnHolograms(RouletteBoardStore.BoardEntry board) {
        removeHolograms(board);
        World world = board.center().getWorld();
        TextDisplay houseHolo = spawnTextDisplay(world, board.center().clone().add(0, HOUSE_HOLO_HEIGHT, 0));
        if (houseHolo != null) {
            houseHoloByBoard.put(board, houseHolo.getUniqueId());
        }
        TextDisplay statusHolo = spawnTextDisplay(world, board.center().clone().add(0, STATUS_HOLO_HEIGHT, 0));
        if (statusHolo != null) {
            statusHoloByBoard.put(board, statusHolo.getUniqueId());
        }
    }

    private TextDisplay spawnTextDisplay(World world, Location loc) {
        try {
            TextDisplay text = (TextDisplay) world.spawnEntity(loc, EntityType.TEXT_DISPLAY);
            text.setBillboard(Display.Billboard.CENTER);
            text.setPersistent(false);
            text.setDefaultBackground(false);
            text.setShadowed(true);
            text.addScoreboardTag(HOLO_TAG);
            return text;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Roulette: failed to spawn a hologram at " + loc, e);
            return null;
        }
    }

    private void removeHolograms(RouletteBoardStore.BoardEntry board) {
        UUID houseId = houseHoloByBoard.remove(board);
        if (houseId != null) {
            Entity entity = Bukkit.getEntity(houseId);
            if (entity != null) {
                entity.remove();
            }
        }
        UUID statusId = statusHoloByBoard.remove(board);
        if (statusId != null) {
            Entity entity = Bukkit.getEntity(statusId);
            if (entity != null) {
                entity.remove();
            }
        }
        renderedHouseBalance.remove(board);
    }

    // ---- Segment labels ----------------------------------------------------------------------

    /**
     * One floating label per physical segment, showing its bet description and odds (e.g.
     * {@code "Straight: 18 (Black)"} / {@code "36:1"}) — visible for as long as the board is
     * active (idle and betting alike), matching the two existing per-board holograms rather than
     * the narrower betting-window lifecycle, since a clickable hitbox is always labeled regardless
     * of round state.
     */
    private void spawnSegmentLabels(RouletteBoardStore.BoardEntry board, List<RouletteSegmentScanner.SegmentScan> segments) {
        removeSegmentLabels(board);
        World world = board.center().getWorld();
        Set<UUID> ids = new HashSet<>();
        for (RouletteSegmentScanner.SegmentScan segment : segments) {
            RouletteGeometry.Vec3 c = segment.centroid();
            Location at = new Location(world, c.x(), c.y() + SEGMENT_LABEL_LIFT, c.z());
            TextDisplay label = spawnLabelDisplay(world, at, labelTextFor(segment.type(), segment.selector()));
            if (label != null) {
                ids.add(label.getUniqueId());
            }
        }
        segmentLabelsByBoard.put(board, ids);
    }

    private TextDisplay spawnLabelDisplay(World world, Location loc, Component text) {
        try {
            TextDisplay label = (TextDisplay) world.spawnEntity(loc, EntityType.TEXT_DISPLAY);
            label.setBillboard(Display.Billboard.CENTER);
            label.setPersistent(false);
            label.setDefaultBackground(false);
            label.setShadowed(true);
            label.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(LABEL_SCALE, LABEL_SCALE, LABEL_SCALE), new Quaternionf()));
            label.text(text);
            label.addScoreboardTag(LABEL_TAG);
            return label;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Roulette: failed to spawn a segment label at " + loc, e);
            return null;
        }
    }

    private void removeSegmentLabels(RouletteBoardStore.BoardEntry board) {
        Set<UUID> ids = segmentLabelsByBoard.remove(board);
        if (ids == null) {
            return;
        }
        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private static Component labelTextFor(BetType type, int selector) {
        String odds = RouletteBet.payoutMultiplierFor(type, selector) + ":1";
        return switch (type) {
            case STRAIGHT -> {
                RouletteWheel.PocketColor color = RouletteWheel.colorOf(selector);
                String desc = "Straight: " + selector + " (" + capitalize(color.name()) + ")";
                yield Messages.MINIGAMES.rouletteSegmentLabel(desc, odds, color.name());
            }
            case DOZEN -> Messages.MINIGAMES.rouletteSegmentLabel(
                    "Dozen: " + ordinal(selector) + " (" + dozenRange(selector) + ")", odds, null);
            case COLOR -> {
                boolean red = selector == RouletteBet.COLOR_RED;
                yield Messages.MINIGAMES.rouletteSegmentLabel(red ? "Red" : "Black", odds, red ? "RED" : "BLACK");
            }
        };
    }

    private static String ordinal(int n) {
        return switch (n) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> n + "th";
        };
    }

    private static String dozenRange(int dozen) {
        int start = (dozen - 1) * 12 + 1;
        return start + "-" + (dozen * 12);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    // ---- Segment markers ----------------------------------------------------------------------

    /**
     * One coloured {@code ItemDisplay} per physical segment, floating just above its invisible
     * {@code Interaction} hitbox (below the odds label) so the clickable area itself reads as
     * red/black/green wool (straight-up and colour bets) or grey/yellow/orange terracotta
     * (1st/2nd/3rd dozen) — an {@code Interaction} has no model of its own, so this is the only
     * way to make "the entity that gets clicked" visually present. Rendered the same way {@link
     * #spawnBall} renders the snowball — a normal-sized item icon, not a block stretched to the
     * segment's footprint — and, since it's a {@code Display} entity rather than a real dropped
     * {@code Item}, it never picks up vanilla's spin/bob animation; it just sits still.
     * {@code Billboard.VERTICAL} (not {@code CENTER}, unlike the label/holograms) keeps it upright
     * and rotating only around the Y axis to face a nearby player, rather than tilting to also
     * face them vertically — a flat item icon pitching up/down to stare at someone looking down at
     * the board reads as broken. Same lifecycle as segment labels: spawned/despawned with the
     * board's activation, and
     * hidden/restored around a spin exactly like {@link #spawnSegmentLabels}/{@link
     * #removeSegmentLabels} — see {@link #spinStart}'s Javadoc for why.
     */
    private void spawnSegmentMarkers(RouletteBoardStore.BoardEntry board, List<RouletteSegmentScanner.SegmentScan> segments) {
        removeSegmentMarkers(board);
        World world = board.center().getWorld();
        Set<UUID> ids = new HashSet<>();
        for (RouletteSegmentScanner.SegmentScan segment : segments) {
            RouletteGeometry.Vec3 c = segment.centroid();
            Location at = new Location(world, c.x(), c.y() + SEGMENT_MARKER_LIFT, c.z());
            Material material = markerMaterialFor(segment.type(), segment.selector());
            ItemDisplay marker = spawnMarkerDisplay(world, at, material);
            if (marker != null) {
                ids.add(marker.getUniqueId());
            }
        }
        segmentMarkersByBoard.put(board, ids);
    }

    private ItemDisplay spawnMarkerDisplay(World world, Location loc, Material material) {
        try {
            ItemDisplay marker = (ItemDisplay) world.spawnEntity(loc, EntityType.ITEM_DISPLAY);
            marker.setItemStack(new ItemStack(material));
            marker.setBillboard(Display.Billboard.VERTICAL);
            marker.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(MARKER_SCALE, MARKER_SCALE, MARKER_SCALE), new Quaternionf()));
            marker.setPersistent(false);
            marker.addScoreboardTag(MARKER_TAG);
            return marker;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Roulette: failed to spawn a segment marker at " + loc, e);
            return null;
        }
    }

    private void removeSegmentMarkers(RouletteBoardStore.BoardEntry board) {
        Set<UUID> ids = segmentMarkersByBoard.remove(board);
        if (ids == null) {
            return;
        }
        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private static Material markerMaterialFor(BetType type, int selector) {
        return switch (type) {
            case STRAIGHT -> switch (RouletteWheel.colorOf(selector)) {
                case RED -> Material.RED_WOOL;
                case BLACK -> Material.BLACK_WOOL;
                case GREEN -> Material.GREEN_WOOL;
            };
            case DOZEN -> switch (selector) {
                case 1 -> Material.GRAY_TERRACOTTA;
                case 2 -> Material.YELLOW_TERRACOTTA;
                case 3 -> Material.ORANGE_TERRACOTTA;
                default -> throw new IllegalStateException("DOZEN selector must be 1-3, was " + selector);
            };
            case COLOR -> selector == RouletteBet.COLOR_RED ? Material.RED_WOOL : Material.BLACK_WOOL;
        };
    }

    /**
     * Refreshes the house-balance hologram. If {@link HouseAccount} hasn't finished its async load
     * yet (true at board-activation time, since activation runs inside the bootstrap registrar),
     * shows a placeholder instead of a misleading {@code $0} until the self-healing periodic
     * refresh (see {@code RouletteSessionManager}) catches the load finishing. No-ops when the
     * balance hasn't changed since the last render, so it is cheap to call from a repeating task.
     */
    void refreshHouseBalance(RouletteBoardStore.BoardEntry board) {
        UUID id = houseHoloByBoard.get(board);
        if (id == null) {
            return;
        }
        if (!(Bukkit.getEntity(id) instanceof TextDisplay text)) {
            return;
        }
        if (!houseAccount.isLoaded()) {
            text.text(Messages.MINIGAMES.rouletteHouseBalanceUnavailable());
            return;
        }
        long balance = houseAccount.balance();
        Long last = renderedHouseBalance.get(board);
        if (last != null && last == balance) {
            return;
        }
        text.text(Messages.MINIGAMES.rouletteHouseBalanceHologram(balance));
        renderedHouseBalance.put(board, balance);
    }

    void refreshStatus(RouletteBoardStore.BoardEntry board, Component text) {
        UUID id = statusHoloByBoard.get(board);
        if (id == null) {
            return;
        }
        if (Bukkit.getEntity(id) instanceof TextDisplay display) {
            display.text(text);
        }
    }
}
