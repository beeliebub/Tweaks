package me.beeliebub.tweaks.tests.listeners;

import me.beeliebub.tweaks.listeners.MobGriefListener;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class MobGriefListenerTest {

    private ProtectionManager protection;
    private MobGriefListener listener;

    @BeforeEach
    void setUp() {
        protection = mock(ProtectionManager.class);
        listener = new MobGriefListener(protection);
    }

    private static Block blockAt(Location loc) {
        Block b = mock(Block.class);
        when(b.getLocation()).thenReturn(loc);
        return b;
    }

    @Test
    void creeperExplosionStripsBlocksOutsideExplicitlyAllowedRegion() {
        Creeper creeper = mock(Creeper.class);
        Location protectedLoc = mock(Location.class);
        Location permittedLoc = mock(Location.class);
        when(protection.isExplicitlyAllowed(eq(protectedLoc), isNull(), eq(RegionFlag.MOB_GRIEFING)))
                .thenReturn(false);
        when(protection.isExplicitlyAllowed(eq(permittedLoc), isNull(), eq(RegionFlag.MOB_GRIEFING)))
                .thenReturn(true);

        Block protectedBlock = blockAt(protectedLoc);
        Block permittedBlock = blockAt(permittedLoc);
        List<Block> affected = new ArrayList<>(List.of(protectedBlock, permittedBlock));

        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.getEntity()).thenReturn(creeper);
        when(event.blockList()).thenReturn(affected);

        listener.onCreeperExplode(event);
        assertEquals(List.of(permittedBlock), affected,
                "only blocks in regions with MOB_GRIEFING=true should survive");
    }

    @Test
    void creeperGriefingDefaultDeniedInWilderness() {
        Creeper creeper = mock(Creeper.class);
        Location wildLoc = mock(Location.class);
        when(protection.isExplicitlyAllowed(eq(wildLoc), isNull(), eq(RegionFlag.MOB_GRIEFING)))
                .thenReturn(false);

        Block b = blockAt(wildLoc);
        List<Block> affected = new ArrayList<>(List.of(b));
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.getEntity()).thenReturn(creeper);
        when(event.blockList()).thenReturn(affected);

        listener.onCreeperExplode(event);
        assertTrue(affected.isEmpty(), "wilderness preserves pre-flag default of no creeper griefing");
    }

    @Test
    void tntExplosionLeavesAffectedBlocksUntouched() {
        TNTPrimed tnt = mock(TNTPrimed.class);
        List<Block> affected = new ArrayList<>(List.of(mock(Block.class)));
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.getEntity()).thenReturn(tnt);
        when(event.blockList()).thenReturn(affected);

        listener.onCreeperExplode(event);
        assertEquals(1, affected.size(), "TNT does not trigger MOB_GRIEFING-controlled mob path");
        verifyNoInteractions(protection);
    }

    @Test
    void endermanBlockChangeCancelledByDefault() {
        Enderman enderman = mock(Enderman.class);
        Location loc = mock(Location.class);
        Block b = blockAt(loc);
        when(protection.isExplicitlyAllowed(eq(loc), isNull(), eq(RegionFlag.MOB_GRIEFING)))
                .thenReturn(false);

        EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);
        when(event.getEntity()).thenReturn(enderman);
        when(event.getBlock()).thenReturn(b);

        listener.onEndermanGrief(event);
        verify(event).setCancelled(true);
    }

    @Test
    void endermanBlockChangeAllowedInsideMobGriefingRegion() {
        Enderman enderman = mock(Enderman.class);
        Location loc = mock(Location.class);
        Block b = blockAt(loc);
        when(protection.isExplicitlyAllowed(eq(loc), isNull(), eq(RegionFlag.MOB_GRIEFING)))
                .thenReturn(true);

        EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);
        when(event.getEntity()).thenReturn(enderman);
        when(event.getBlock()).thenReturn(b);

        listener.onEndermanGrief(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void otherMobBlockChangesAreNotCancelled() {
        Skeleton skeleton = mock(Skeleton.class);
        EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);
        when(event.getEntity()).thenReturn(skeleton);

        listener.onEndermanGrief(event);
        verify(event, never()).setCancelled(true);
        verifyNoInteractions(protection);
    }
}
