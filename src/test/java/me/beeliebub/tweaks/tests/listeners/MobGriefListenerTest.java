package me.beeliebub.tweaks.tests.listeners;

import me.beeliebub.tweaks.listeners.MobGriefListener;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobGriefListenerTest {

    private final MobGriefListener listener = new MobGriefListener();

    @Test
    void creeperExplosionStripsAffectedBlocks() {
        Creeper creeper = mock(Creeper.class);
        List<Block> affected = new ArrayList<>(List.of(mock(Block.class), mock(Block.class)));
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.getEntity()).thenReturn(creeper);
        when(event.blockList()).thenReturn(affected);

        listener.onCreeperExplode(event);
        assertTrue(affected.isEmpty(), "creeper explosion should leave no broken blocks");
    }

    @Test
    void tntExplosionLeavesAffectedBlocksUntouched() {
        TNTPrimed tnt = mock(TNTPrimed.class);
        List<Block> affected = new ArrayList<>(List.of(mock(Block.class)));
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.getEntity()).thenReturn(tnt);
        when(event.blockList()).thenReturn(affected);

        listener.onCreeperExplode(event);
        assertEquals(1, affected.size(), "TNT must still break blocks");
    }

    @Test
    void endermanBlockChangeIsCancelled() {
        Enderman enderman = mock(Enderman.class);
        EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);
        when(event.getEntity()).thenReturn(enderman);

        listener.onEndermanGrief(event);
        verify(event).setCancelled(true);
    }

    @Test
    void otherMobBlockChangesAreNotCancelled() {
        Skeleton skeleton = mock(Skeleton.class);
        EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);
        when(event.getEntity()).thenReturn(skeleton);

        listener.onEndermanGrief(event);
        verify(event, never()).setCancelled(true);
    }
}
