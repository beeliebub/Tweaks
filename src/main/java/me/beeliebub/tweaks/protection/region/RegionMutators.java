package me.beeliebub.tweaks.protection.region;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Package-private immutable-region mutation service behind the public facade. */
final class RegionMutators {

    private final ProtectionManager owner;

    RegionMutators(ProtectionManager owner) {
        this.owner = owner;
    }

    boolean setMaterials(World world, String id, RegionFlag flag, Set<Material> materials) {
        Region region = resolve(world, id);
        if (region == null) return false;
        require(flag.isMaterialFlag(), "Not a material-list flag: " + flag);
        Set<Material> normalized = materials == null || materials.isEmpty()
                ? Set.of() : EnumSet.copyOf(materials);
        if (region.materialsFor(flag).equals(normalized)) return false;
        return replace(region.withMaterials(flag, normalized));
    }

    boolean addMaterials(World world, String id, RegionFlag flag, Set<Material> materials) {
        Region region = resolve(world, id);
        if (region == null || materials == null || materials.isEmpty()) return false;
        require(flag.isMaterialFlag(), "Not a material-list flag: " + flag);
        EnumSet<Material> merged = EnumSet.noneOf(Material.class);
        merged.addAll(region.materialsFor(flag));
        if (!merged.addAll(materials)) return false;
        return replace(region.withMaterials(flag, merged));
    }

    boolean removeMaterials(World world, String id, RegionFlag flag, Set<Material> materials) {
        Region region = resolve(world, id);
        if (region == null || materials == null || materials.isEmpty()) return false;
        require(flag.isMaterialFlag(), "Not a material-list flag: " + flag);
        EnumSet<Material> reduced = EnumSet.noneOf(Material.class);
        reduced.addAll(region.materialsFor(flag));
        if (!reduced.removeAll(materials)) return false;
        return replace(region.withMaterials(flag, reduced));
    }

    boolean clearMaterials(World world, String id, RegionFlag flag) {
        Region region = resolve(world, id);
        if (region == null) return false;
        require(flag.isMaterialFlag(), "Not a material-list flag: " + flag);
        if (region.materialsFor(flag).isEmpty()) return false;
        return replace(region.withMaterials(flag, Set.of()));
    }

    boolean setEntities(World world, String id, RegionFlag flag,
                        Set<org.bukkit.entity.EntityType> entities) {
        Region region = resolve(world, id);
        if (region == null) return false;
        require(flag.isEntityFlag(), "Not an entity-list flag: " + flag);
        Set<org.bukkit.entity.EntityType> normalized = entities == null || entities.isEmpty()
                ? Set.of() : EnumSet.copyOf(entities);
        if (region.entitiesFor(flag).equals(normalized)) return false;
        return replace(region.withEntities(flag, normalized));
    }

    boolean clearEntities(World world, String id, RegionFlag flag) {
        Region region = resolve(world, id);
        if (region == null) return false;
        require(flag.isEntityFlag(), "Not an entity-list flag: " + flag);
        if (region.entitiesFor(flag).isEmpty()) return false;
        return replace(region.withEntities(flag, Set.of()));
    }

    boolean addMember(World world, String id, UUID member) {
        Region region = resolve(world, id);
        if (region == null || region.members().contains(member)) return false;
        var members = new java.util.ArrayList<>(region.members());
        members.add(member);
        return replace(region.withMembers(members));
    }

    boolean removeMember(World world, String id, UUID member) {
        Region region = resolve(world, id);
        if (region == null || !region.members().contains(member)) return false;
        var members = new java.util.ArrayList<>(region.members());
        members.remove(member);
        return replace(region.withMembers(members));
    }

    boolean addManager(World world, String id, UUID manager) {
        Region region = resolve(world, id);
        if (region == null || region.managers().contains(manager)) return false;
        var managers = new java.util.ArrayList<>(region.managers());
        managers.add(manager);
        return replace(region.withManagers(managers));
    }

    boolean removeManager(World world, String id, UUID manager) {
        Region region = resolve(world, id);
        if (region == null || !region.managers().contains(manager)) return false;
        var managers = new java.util.ArrayList<>(region.managers());
        managers.remove(manager);
        return replace(region.withManagers(managers));
    }

    boolean addMemberGroup(World world, String id, String group) {
        Region region = resolve(world, id);
        if (region == null || group == null) return false;
        Region updated = region.addMemberGroup(group);
        return updated == region ? false : replace(updated);
    }

    boolean removeMemberGroup(World world, String id, String group) {
        Region region = resolve(world, id);
        if (region == null || group == null) return false;
        Region updated = region.removeMemberGroup(group);
        return updated == region ? false : replace(updated);
    }

    boolean addManagerGroup(World world, String id, String group) {
        Region region = resolve(world, id);
        if (region == null || group == null) return false;
        Region updated = region.addManagerGroup(group);
        return updated == region ? false : replace(updated);
    }

    boolean removeManagerGroup(World world, String id, String group) {
        Region region = resolve(world, id);
        if (region == null || group == null) return false;
        Region updated = region.removeManagerGroup(group);
        return updated == region ? false : replace(updated);
    }

    private Region resolve(World world, String id) {
        if (ProtectionManager.GLOBAL_REGION_ID.equals(id)) return owner.globalRegion(world);
        return world == null ? owner.byNameAnyWorld(id) : owner.byName(world, id);
    }

    private boolean replace(Region updated) {
        owner.regions().put(ProtectionManager.keyOf(updated), updated);
        if (owner.writer() != null) owner.writer().queue(updated);
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
