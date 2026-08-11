package me.beeliebub.tweaks.minigames.roulette;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingPaths;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * {@code /roulettescan [radius]} — admin diagnostic originally scoped as throwaway and kept
 * instead as a standing tool (see the package {@code CLAUDE.md}). Walks entities near the invoking
 * admin, reads (never writes) the {@code Transformation} of every tagged {@code roulette_*}
 * {@code BlockDisplay}, and dumps the raw geometry plus derived wheel-centre/angle/gap/rigid-body-
 * rotation findings to a TSV file and a chat summary.
 *
 * <p><b>This class is strictly read-only.</b> It must never call {@code setGlowing},
 * {@code setTransformation}, or write any tag — {@code RouletteRenderer}'s spin animation
 * legitimately mutates these same entities under a snapshot-and-restore contract, and a future
 * reader must not cargo-cult a "just call setters" habit from this diagnostic.
 */
public final class RouletteScanCommand implements CommandExecutor {

    private static final int DEFAULT_RADIUS = 16;
    private static final int MIN_RADIUS = 1;
    private static final int MAX_RADIUS = 64;

    private final JavaPlugin plugin;

    public RouletteScanCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.MINIGAMES.rouletteScanFailed("This command can only be run by a player."));
            return true;
        }
        if (!player.hasPermission(Permissions.ADMIN_ROULETTE_SCAN)) {
            player.sendMessage(Messages.noPermission());
            return true;
        }

        int radius = DEFAULT_RADIUS;
        if (args.length > 0) {
            try {
                radius = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(Messages.MINIGAMES.rouletteScanFailed("Radius must be a whole number."));
                return true;
            }
        }
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));

        String senderName = player.getName();
        List<ScannedSegment> segments;
        String tsv;
        File target;
        String summary;
        try {
            segments = scan(player, radius);
            RouletteScanReport report = RouletteScanReport.compute(segments);
            String worldName = player.getWorld().getName();
            tsv = RouletteScanWriter.buildTsv(segments, report, worldName);
            target = RouletteScanWriter.scanFile(plugin.getDataFolder(), worldName);
            summary = buildSummary(segments.size(), report, target);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "roulettescan failed while scanning/building the report", e);
            player.sendMessage(Messages.MINIGAMES.rouletteScanFailed("An error occurred while scanning — see the server log."));
            return true;
        }

        // segments/target/tsv/summary are assigned exactly once above (effectively final), so the
        // async lambda below can capture them directly.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                RouletteScanWriter.writeNow(target, tsv);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Log before messaging: a disconnected/edge-case player's sendMessage must not
                    // be able to suppress this audit line for a write that genuinely succeeded.
                    ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
                    if (eventLog != null) eventLog.log(LoggingPaths.ROULETTE_SCAN,
                            () -> "[Roulette] " + ConsoleEventLog.actorLabel(senderName, player.getUniqueId())
                                    + " ran /roulettescan (" + segments.size()
                                    + " segments found) -> " + target.getAbsolutePath());
                    player.sendMessage(Messages.MINIGAMES.rouletteScanResult(summary));
                });
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "roulettescan write failed", e);
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(Messages.MINIGAMES.rouletteScanFailed("Could not write scan file: " + e.getMessage())));
            }
        });

        return true;
    }

    private List<ScannedSegment> scan(Player player, int radius) {
        List<ScannedSegment> segments = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof BlockDisplay display)) {
                continue;
            }
            Set<String> tags = display.getScoreboardTags();
            List<String> rouletteTags = tags.stream()
                    .filter(t -> t.startsWith("roulette_"))
                    .sorted()
                    .toList();
            if (rouletteTags.isEmpty()) {
                continue;
            }
            try {
                segments.add(capture(display, rouletteTags));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING,
                        "roulettescan: skipping entity " + display.getUniqueId() + " (tags " + rouletteTags + ") after a capture error", e);
            }
        }
        return segments;
    }

    private static ScannedSegment capture(BlockDisplay display, List<String> rouletteTags) {
        RouletteTagParser.ParsedTag parsed = RouletteTagParser.parse(rouletteTags.get(0));

        Location loc = display.getLocation();
        RouletteGeometry.Vec3 entityPos = new RouletteGeometry.Vec3(loc.getX(), loc.getY(), loc.getZ());

        Transformation t = display.getTransformation();
        // Copy every mutable JOML component on read — never hold onto or mutate the live objects
        // Transformation hands back (see class Javadoc and RouletteGeometry's own Javadoc).
        Vector3f translation = t.getTranslation();
        Quaternionf leftRotation = t.getLeftRotation();
        Vector3f scale = t.getScale();
        Quaternionf rightRotation = t.getRightRotation();

        RouletteGeometry.Vec3 translationVec = new RouletteGeometry.Vec3(translation.x(), translation.y(), translation.z());
        RouletteGeometry.Quat leftRotationQuat = new RouletteGeometry.Quat(leftRotation.x(), leftRotation.y(), leftRotation.z(), leftRotation.w());
        RouletteGeometry.Vec3 scaleVec = new RouletteGeometry.Vec3(scale.x(), scale.y(), scale.z());
        RouletteGeometry.Quat rightRotationQuat = new RouletteGeometry.Quat(rightRotation.x(), rightRotation.y(), rightRotation.z(), rightRotation.w());

        RouletteGeometry.Vec3 planCentroid = RouletteGeometry.planCentroid(entityPos, translationVec, scaleVec, leftRotationQuat);
        RouletteGeometry.Vec3 correctCentroid = RouletteGeometry.correctCentroid(entityPos, translationVec, scaleVec, leftRotationQuat, rightRotationQuat);

        return new ScannedSegment(
                display.getUniqueId(), display.getType(), loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(),
                translationVec, leftRotationQuat, scaleVec, rightRotationQuat,
                display.getBillboard(),
                display.getInterpolationDuration(), display.getInterpolationDelay(), display.getTeleportDuration(),
                display.getDisplayWidth(), display.getDisplayHeight(), display.getViewRange(),
                display.getBrightness(), display.isGlowing(), display.getGlowColorOverride(),
                display.getBlock().getAsString(),
                parsed.ring(), parsed.selector(), parsed.rawTag(), parsed.form(), rouletteTags,
                planCentroid, correctCentroid,
                loc.getChunk().getX(), loc.getChunk().getZ());
    }

    private static String buildSummary(int totalFound, RouletteScanReport report, File target) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Roulette scan complete ===\n");
        sb.append("Found ").append(totalFound).append(" segments (expected ")
                .append(RouletteScanReport.EXPECTED_TOTAL_SEGMENTS).append("). Complete: ")
                .append(report.completeBoard()).append('\n');
        for (Ring r : Ring.values()) {
            sb.append("  ").append(r).append(": ").append(report.countsByRing().getOrDefault(r, 0)).append('\n');
        }
        if (!report.missingOuterPockets().isEmpty()) sb.append("Missing pockets: ").append(report.missingOuterPockets()).append('\n');
        if (!report.missingMiddleThirds().isEmpty()) sb.append("Missing thirds: ").append(report.missingMiddleThirds()).append('\n');
        if (report.missingColorRed()) sb.append("Missing color: red\n");
        if (report.missingColorBlack()) sb.append("Missing color: black\n");
        if (!report.duplicateTags().isEmpty()) sb.append("Duplicates: ").append(report.duplicateTags().size()).append(" (see file)\n");
        sb.append(String.format(Locale.ROOT, "Wheel centre (Kasa): (%.3f, %.3f) radius=%.3f rms=%.3f%n",
                report.kasaEstimator().centerX(), report.kasaEstimator().centerZ(),
                report.kasaEstimator().radius(), report.kasaEstimator().rmsResidual()));
        sb.append(String.format(Locale.ROOT, "Median gap: %.3f deg%n", report.gapStats().median()));
        sb.append(String.format(Locale.ROOT, "Recommended Interaction size: width=%.3f (conservative=%.3f) height=%.3f%n",
                report.recommendedWidth(), report.recommendedWidthConservative(), report.recommendedHeight()));
        var v = report.rigidBodyVerdict();
        sb.append("Rigid-body verdict: plane=").append(v.planeFlatPass() ? "PASS" : "CHECK")
                .append(" circle=").append(v.circleResidualPass() ? "PASS" : "CHECK")
                .append(" axisAngle=").append(v.axisAnglePass() ? "PASS" : "CHECK")
                .append(" rightRot=").append(v.rightRotationUniformPass() ? "PASS" : "CHECK")
                .append(" yawPitch=").append(v.yawPitchZeroPass() ? "PASS" : "CHECK")
                .append(" billboard=").append(v.billboardFixedPass() ? "PASS" : "CHECK").append('\n');
        for (String caveat : report.caveats()) {
            sb.append("CAVEAT: ").append(caveat).append('\n');
        }
        sb.append("File: ").append(target.getAbsolutePath());
        return sb.toString();
    }
}
