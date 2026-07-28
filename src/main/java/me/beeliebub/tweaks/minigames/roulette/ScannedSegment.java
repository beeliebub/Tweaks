package me.beeliebub.tweaks.minigames.roulette;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.UUID;

/**
 * One {@code BlockDisplay} segment as read off the live board, verbatim and at full precision.
 * Carries only Bukkit <em>value</em> types (enums/records), never a live {@code Entity},
 * {@code Location}, or {@code Transformation} — those are read exactly once, at capture time in
 * {@link RouletteScanCommand}, and their mutable fields are copied into
 * {@link RouletteGeometry.Vec3}/{@link RouletteGeometry.Quat} immediately (a live
 * {@code Transformation}'s translation/scale/rotations are mutable JOML objects; holding onto the
 * original and re-reading it later would risk observing a value this diagnostic never itself wrote).
 */
record ScannedSegment(
        UUID entityId,
        EntityType entityType,
        String worldName,

        double x, double y, double z,
        float yaw, float pitch,

        RouletteGeometry.Vec3 translation,
        RouletteGeometry.Quat leftRotation,
        RouletteGeometry.Vec3 scale,
        RouletteGeometry.Quat rightRotation,

        Display.Billboard billboard,
        int interpolationDuration,
        int interpolationDelay,
        int teleportDuration,
        float displayWidth,
        float displayHeight,
        float viewRange,
        Display.Brightness brightness,
        boolean glowing,
        Color glowColorOverride,
        String blockDataString,

        Ring ring,
        int selector,
        String rawTag,
        RouletteTagParser.NumericForm numericForm,
        List<String> allRouletteTags,

        RouletteGeometry.Vec3 planCentroid,
        RouletteGeometry.Vec3 correctCentroid,

        int chunkX,
        int chunkZ
) {}
