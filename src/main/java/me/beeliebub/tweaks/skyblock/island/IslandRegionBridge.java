package me.beeliebub.tweaks.skyblock.island;

import me.beeliebub.tweaks.protection.region.FlagTarget;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import org.bukkit.World;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Adapts Skyblock island and spawn territory to the public protection.region API. */
public final class IslandRegionBridge {

    public static final String SPAWN_REGION_ID = "skyblock-spawn";

    private final ProtectionManager protection;
    private final IslandGrid grid;

    public IslandRegionBridge(ProtectionManager protection, IslandGrid grid) {
        this.protection = protection;
        this.grid = grid;
    }

    public ProtectionManager.ClaimResult create(Island island, World world,
                                                AtomicReference<CompletableFuture<Void>> pending) {
        Region.RegionBounds bounds = grid.boundsFor(island.slotIndex(), island.size());
        EnumMap<RegionFlag, Map<FlagTarget, Boolean>> rules = new EnumMap<>(RegionFlag.class);
        rules.put(RegionFlag.PVP, Map.of(FlagTarget.DEFAULT, false));
        rules.put(RegionFlag.MOB_SPAWNING, Map.of(FlagTarget.DEFAULT, true));
        Region region = new Region(island.id(), island.owner(), List.copyOf(island.members()), rules,
                Map.of(), null, bounds, world.getName(), List.of(), Map.of(), 0);
        return protection.tryClaim(region, world, minBlock(bounds.minChunkX()), minBlock(bounds.minChunkZ()),
                maxBlock(bounds.maxChunkX()), maxBlock(bounds.maxChunkZ()), pending);
    }

    /** Returns the live spawn claim; the protection cache is the enforcement source of truth. */
    public Region spawnRegion(World world) {
        if (world == null) return null;
        Region region = protection.byName(world, SPAWN_REGION_ID);
        if (region == null || region.worldName() == null
                || !region.worldName().equalsIgnoreCase(world.getName())) {
            return null;
        }
        return region;
    }

    public ProtectionManager.UnclaimOutcome unclaimSpawn(World world) {
        if (world == null) {
            return new ProtectionManager.UnclaimOutcome(
                    ProtectionManager.UnclaimResult.UNKNOWN_REGION, List.of());
        }
        return protection.unclaim(world, SPAWN_REGION_ID);
    }

    /** Claims the reserved spawn id while carrying forward the prior region's mutable settings. */
    public ProtectionManager.ClaimResult claimSpawn(
            World world, SkyblockSpawn.SpawnData data, IslandGrid.ChunkBounds bounds,
            Region carryForward, AtomicReference<CompletableFuture<Void>> pending) {
        if (world == null || data == null || bounds == null) {
            return ProtectionManager.ClaimResult.ID_TAKEN;
        }

        Region region = new Region(SPAWN_REGION_ID, data.owner(),
                carryForward == null ? List.of() : carryForward.members(),
                spawnRules(carryForward),
                carryForward == null ? Map.of() : carryForward.materialFlags(),
                carryForward == null ? null : carryForward.parentId(),
                bounds.toRegionBounds(), world.getName(),
                carryForward == null ? List.of() : carryForward.managers(),
                carryForward == null ? Map.of() : carryForward.entityFlags(),
                carryForward == null ? 0 : carryForward.cost(),
                carryForward == null ? List.of() : carryForward.memberGroups(),
                carryForward == null ? List.of() : carryForward.managerGroups());
        return protection.tryClaim(region, world, minBlock(bounds.minChunkX()), minBlock(bounds.minChunkZ()),
                maxBlock(bounds.maxChunkX()), maxBlock(bounds.maxChunkZ()), pending);
    }

    public boolean resize(Island island, World world, IslandSize newSize) {
        if (island == null || world == null || newSize == null || newSize.chunks() <= island.size().chunks()) {
            return false;
        }
        Region.RegionBounds oldBounds = grid.boundsFor(island.slotIndex(), island.size());
        Region.RegionBounds newBounds = grid.boundsFor(island.slotIndex(), newSize);
        if (!contains(newBounds, oldBounds)) return false;
        return protection.resize(world, island.id(), newBounds);
    }

    public boolean addMember(Island island, World world, java.util.UUID member) {
        return island != null && world != null && member != null
                && protection.addMember(world, island.id(), member);
    }

    public boolean removeMember(Island island, World world, java.util.UUID member) {
        return island != null && world != null && member != null
                && protection.removeMember(world, island.id(), member);
    }

    public ProtectionManager.UnclaimOutcome delete(Island island, World world) {
        if (island == null || world == null) {
            return new ProtectionManager.UnclaimOutcome(ProtectionManager.UnclaimResult.UNKNOWN_REGION, List.of());
        }
        return protection.unclaim(world, island.id());
    }

    public Region region(Island island, World world) {
        return protection.byName(world, island.id());
    }

    private static Map<RegionFlag, Map<FlagTarget, Boolean>> spawnRules(Region carryForward) {
        EnumMap<RegionFlag, Map<FlagTarget, Boolean>> rules = new EnumMap<>(RegionFlag.class);
        if (carryForward != null) {
            carryForward.flagRules().forEach((flag, targets) ->
                    rules.put(flag, new HashMap<>(targets)));
        }
        setSpawnDefault(rules, RegionFlag.PVP, false);
        setSpawnDefault(rules, RegionFlag.MOB_SPAWNING, false);
        setSpawnDefault(rules, RegionFlag.INVINCIBILITY, true);
        return rules;
    }

    private static void setSpawnDefault(Map<RegionFlag, Map<FlagTarget, Boolean>> rules,
                                        RegionFlag flag, boolean value) {
        Map<FlagTarget, Boolean> targets = new HashMap<>(rules.getOrDefault(flag, Map.of()));
        targets.put(FlagTarget.DEFAULT, value);
        rules.put(flag, targets);
    }

    private static boolean contains(Region.RegionBounds outer, Region.RegionBounds inner) {
        return inner.minChunkX() >= outer.minChunkX() && inner.maxChunkX() <= outer.maxChunkX()
                && inner.minChunkZ() >= outer.minChunkZ() && inner.maxChunkZ() <= outer.maxChunkZ();
    }

    private static int minBlock(int chunk) {
        return Math.multiplyExact(chunk, 16);
    }

    private static int maxBlock(int chunk) {
        return Math.addExact(Math.multiplyExact(chunk, 16), 15);
    }
}
