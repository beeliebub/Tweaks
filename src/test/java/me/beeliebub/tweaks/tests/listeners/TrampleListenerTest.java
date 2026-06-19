package me.beeliebub.tweaks.tests.listeners;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.worldmanagement.WorldRuleListener;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class TrampleListenerTest {

    private final WorldRuleListener listener = newListener();

    private static WorldRuleListener newListener() {
        Tweaks plugin = mock(Tweaks.class);
        FileConfiguration cfg = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(cfg);
        when(cfg.getStringList("disabled-end-portal-worlds")).thenReturn(List.of());
        return new WorldRuleListener(plugin, mock(ProtectionManager.class));
    }

    @Test
    void cancelsPlayerPhysicalInteractionOnFarmland() {
        Block farmland = mock(Block.class);
        when(farmland.getType()).thenReturn(Material.FARMLAND);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.PHYSICAL);
        when(event.getClickedBlock()).thenReturn(farmland);

        listener.onPlayerTrample(event);
        verify(event).setCancelled(true);
    }

    @Test
    void doesNotCancelNonPhysicalActionsEvenOnFarmland() {
        Block farmland = mock(Block.class);
        when(farmland.getType()).thenReturn(Material.FARMLAND);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(farmland);

        listener.onPlayerTrample(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void doesNotCancelPhysicalInteractionOnOtherBlocks() {
        Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.PHYSICAL);
        when(event.getClickedBlock()).thenReturn(stone);

        listener.onPlayerTrample(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void handlesNullClickedBlockGracefully() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.PHYSICAL);
        when(event.getClickedBlock()).thenReturn(null);

        listener.onPlayerTrample(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void cancelsAnyEntityInteractionOnFarmland() {
        Block farmland = mock(Block.class);
        when(farmland.getType()).thenReturn(Material.FARMLAND);
        EntityInteractEvent event = mock(EntityInteractEvent.class);
        when(event.getBlock()).thenReturn(farmland);

        listener.onEntityTrample(event);
        verify(event).setCancelled(true);
    }

    @Test
    void doesNotCancelEntityInteractionsOnOtherBlocks() {
        Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);
        EntityInteractEvent event = mock(EntityInteractEvent.class);
        when(event.getBlock()).thenReturn(stone);

        listener.onEntityTrample(event);
        verify(event, never()).setCancelled(true);
    }
}
