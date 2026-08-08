package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldScopedMutatorTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID MANAGER = UUID.randomUUID();

    @Test
    void everyMemberManagerAndListMutatorUsesOnlyTheScopeWorld() {
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        World alpha = world("alpha");
        World beta = world("beta");
        protection.regions().put("alpha:base", region("base", "alpha"));
        protection.regions().put("beta:base", region("base", "beta"));

        assertTrue(protection.addMember(alpha, "base", MEMBER));
        assertTrue(protection.removeMember(alpha, "base", MEMBER));
        assertTrue(protection.addManager(alpha, "base", MANAGER));
        assertTrue(protection.removeManager(alpha, "base", MANAGER));
        assertTrue(protection.addMemberGroup(alpha, "base", "staff"));
        assertTrue(protection.removeMemberGroup(alpha, "base", "staff"));
        assertTrue(protection.addManagerGroup(alpha, "base", "mods"));
        assertTrue(protection.removeManagerGroup(alpha, "base", "mods"));
        assertTrue(protection.setMaterials(alpha, "base", RegionFlag.ALLOW_BLOCK_BREAK,
                Set.of(Material.STONE)));
        assertTrue(protection.clearMaterials(alpha, "base", RegionFlag.ALLOW_BLOCK_BREAK));
        assertTrue(protection.setEntities(alpha, "base", RegionFlag.ALLOW_MOB_SPAWN,
                Set.of(EntityType.ZOMBIE)));
        assertTrue(protection.clearEntities(alpha, "base", RegionFlag.ALLOW_MOB_SPAWN));

        Region untouched = protection.regions().get("beta:base");
        assertTrue(untouched.members().isEmpty());
        assertTrue(untouched.managers().isEmpty());
        assertTrue(untouched.memberGroups().isEmpty());
        assertTrue(untouched.managerGroups().isEmpty());
        assertTrue(untouched.materialFlags().isEmpty());
        assertTrue(untouched.entityFlags().isEmpty());
    }

    private static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }

    private static Region region(String id, String world) {
        return new Region(id, OWNER, List.of(), Map.of(), Map.of(), null,
                null, world, List.of(), Map.of());
    }
}
