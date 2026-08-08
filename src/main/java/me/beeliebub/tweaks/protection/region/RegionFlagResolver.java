package me.beeliebub.tweaks.protection.region;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Package-private protection verdict engine behind {@link ProtectionManager}. */
final class RegionFlagResolver {

    private final ProtectionManager owner;

    RegionFlagResolver(ProtectionManager owner) {
        this.owner = owner;
    }

    boolean isAllowed(Location loc, UUID actor, RegionFlag flag) {
        List<Region> applicable = owner.regionsAt(loc);
        if (applicable.isEmpty()) {
            applicable = fallback(loc.getWorld());
            if (applicable.isEmpty()) return true;
        }
        return isAllowed(applicable, loc, actor, flag);
    }

    boolean isAllowed(List<Region> applicable, Location loc, UUID actor, RegionFlag flag) {
        if (applicable.isEmpty()) {
            applicable = fallback(loc.getWorld());
            if (applicable.isEmpty()) return true;
        }
        Set<String> groups = actor == null ? Set.of() : owner.groupsOf(actor);
        for (Region leaf : leavesOf(applicable)) {
            Optional<Boolean> resolved = resolveAcrossChain(leaf, flag, actor, groups);
            if (resolved.isPresent()) {
                if (!resolved.get()) return false;
                continue;
            }
            // ENTRY is opt-in: a claimed region with no effective ENTRY rule
            // must not become an accidental no-entry zone merely because it
            // is protected. Explicit matching ENTRY rules still use normal
            // targeted resolution above.
            if (flag == RegionFlag.ENTRY) continue;
            if (ProtectionManager.isGlobal(leaf)) continue;
            if (actor != null && leaf.isMember(actor, groups)) continue;
            return false;
        }
        return true;
    }

    boolean isBlockActionAllowed(Location loc, UUID actor, Material material, RegionFlag baseFlag) {
        if (baseFlag != RegionFlag.BLOCK_BREAK && baseFlag != RegionFlag.BLOCK_PLACE) {
            throw new IllegalArgumentException(
                    "isBlockActionAllowed requires BLOCK_BREAK or BLOCK_PLACE, got " + baseFlag);
        }
        List<Region> applicable = owner.regionsAt(loc);
        if (applicable.isEmpty()) {
            applicable = fallback(loc.getWorld());
            if (applicable.isEmpty()) return true;
        }
        RegionFlag denyFlag = baseFlag == RegionFlag.BLOCK_BREAK
                ? RegionFlag.DENY_BLOCK_BREAK : RegionFlag.DENY_BLOCK_PLACE;
        RegionFlag allowFlag = baseFlag == RegionFlag.BLOCK_BREAK
                ? RegionFlag.ALLOW_BLOCK_BREAK : RegionFlag.ALLOW_BLOCK_PLACE;
        Set<String> groups = actor == null ? Set.of() : owner.groupsOf(actor);
        for (Region leaf : leavesOf(applicable)) {
            Optional<Boolean> resolved = resolveBlockActionChain(
                    leaf, baseFlag, denyFlag, allowFlag, material, actor, groups);
            if (resolved.isPresent()) {
                if (!resolved.get()) return false;
                continue;
            }
            if (ProtectionManager.isGlobal(leaf)) continue;
            if (actor != null && leaf.isMember(actor, groups)) continue;
            return false;
        }
        return true;
    }

    boolean isExplicitlyAllowed(Location loc, UUID actor, RegionFlag flag) {
        List<Region> applicable = owner.regionsAt(loc);
        if (applicable.isEmpty()) {
            applicable = fallback(loc.getWorld());
            if (applicable.isEmpty()) return false;
        }
        Set<String> groups = actor == null ? Set.of() : owner.groupsOf(actor);
        for (Region leaf : leavesOf(applicable)) {
            Optional<Boolean> resolved = resolveAcrossChain(leaf, flag, actor, groups);
            if (resolved.isEmpty() || !resolved.get()) return false;
        }
        return true;
    }

    boolean isEntityListed(Location loc, RegionFlag flag, org.bukkit.entity.EntityType type) {
        if (!flag.isEntityFlag()) return false;
        return isEntityListed(owner.regionsAt(loc), flag, type);
    }

    boolean isEntityListed(List<Region> applicable, RegionFlag flag,
                           org.bukkit.entity.EntityType type) {
        if (!flag.isEntityFlag()) return false;
        for (Region region : applicable) {
            if (region.entitiesFor(flag).contains(type)) return true;
        }
        return false;
    }

    private List<Region> fallback(World world) {
        Region global = owner.globalRegion(world);
        return global == null ? List.of() : List.of(global);
    }

    private List<Region> leavesOf(List<Region> applicable) {
        if (applicable.size() == 1) return applicable;
        Set<String> referenced = new HashSet<>();
        for (Region region : applicable) {
            if (region.hasParent()) referenced.add(region.parentId());
        }
        List<Region> leaves = new ArrayList<>(applicable.size());
        for (Region region : applicable) {
            if (!referenced.contains(region.id())) leaves.add(region);
        }
        return leaves.isEmpty() ? applicable : leaves;
    }

    private Optional<Boolean> resolveAcrossChain(
            Region start, RegionFlag flag, UUID actor, Set<String> groups) {
        Set<String> visited = new LinkedHashSet<>();
        Region cursor = start;
        while (cursor != null && visited.add(ProtectionManager.keyOf(cursor))) {
            boolean isOwner = actor != null && cursor.isOwner(actor);
            boolean isManager = actor != null && cursor.isManager(actor, groups);
            boolean isMember = actor != null && cursor.isMember(actor, groups);
            Optional<Boolean> resolved = cursor.resolveFlag(flag, isOwner, isManager, isMember, groups);
            if (resolved.isPresent()) return resolved;
            cursor = cursor.hasParent() ? parentOf(cursor) : null;
        }
        return Optional.empty();
    }

    private Optional<Boolean> resolveBlockActionChain(
            Region start, RegionFlag baseFlag, RegionFlag denyFlag, RegionFlag allowFlag,
            Material material, UUID actor, Set<String> groups) {
        Set<String> visited = new LinkedHashSet<>();
        Region cursor = start;
        while (cursor != null && visited.add(ProtectionManager.keyOf(cursor))) {
            Optional<Boolean> materialVerdict = cursor.resolveMaterial(denyFlag, allowFlag, material);
            if (materialVerdict.isPresent()) return materialVerdict;
            boolean isOwner = actor != null && cursor.isOwner(actor);
            boolean isManager = actor != null && cursor.isManager(actor, groups);
            boolean isMember = actor != null && cursor.isMember(actor, groups);
            Optional<Boolean> boolVerdict = cursor.resolveFlag(baseFlag, isOwner, isManager, isMember, groups);
            if (boolVerdict.isPresent()) return boolVerdict;
            cursor = cursor.hasParent() ? parentOf(cursor) : null;
        }
        return Optional.empty();
    }

    private Region parentOf(Region cursor) {
        if (cursor == null || cursor.parentId() == null) return null;
        Region parent = owner.regions().get(ProtectionManager.keyOf(cursor.worldName(), cursor.parentId()));
        return parent != null ? parent : owner.regions().get(cursor.parentId());
    }
}
