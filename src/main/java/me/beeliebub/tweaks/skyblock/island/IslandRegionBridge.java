package me.beeliebub.tweaks.skyblock.island;

import me.beeliebub.tweaks.protection.region.FlagTarget;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import org.bukkit.World;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Adapts the Skyblock island aggregate to the public protection.region API. */
public final class IslandRegionBridge {

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
