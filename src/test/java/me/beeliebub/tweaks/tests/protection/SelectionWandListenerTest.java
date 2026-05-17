package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.GeometryUtil;
import me.beeliebub.tweaks.protection.RegionSelection;
import me.beeliebub.tweaks.protection.RegionSelectionManager;
import me.beeliebub.tweaks.protection.SelectionWandListener;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SelectionWandListenerTest {

    private static final UUID PLAYER = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    private Tweaks plugin;
    private RegionSelectionManager selections;
    private SelectionWandListener listener;
    private World world;

    @BeforeEach
    void setUp() {
        plugin = mock(Tweaks.class);
        when(plugin.getProtectionSelectionTool()).thenReturn(Material.STONE_AXE);
        selections = new RegionSelectionManager(plugin);
        listener = new SelectionWandListener(plugin, selections);
        world = mock(World.class);
    }

    private Block cornerBlock(int x, int z) {
        Block b = mock(Block.class);
        when(b.getX()).thenReturn(x);
        when(b.getZ()).thenReturn(z);
        when(b.getWorld()).thenReturn(world);
        return b;
    }

    private Player playerWith(ItemStack item) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(PLAYER);
        PlayerInventory inv = mock(PlayerInventory.class);
        when(inv.getItemInMainHand()).thenReturn(item);
        when(p.getInventory()).thenReturn(inv);
        return p;
    }

    private PlayerInteractEvent interactWith(Player player, Action action, Block block, ItemStack item) {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(action);
        when(event.getClickedBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItem()).thenReturn(item);
        return event;
    }

    @Test
    void leftClickWithWandOnCornerSetsPos1AndCancels() {
        ItemStack wand = new ItemStack(Material.STONE_AXE);
        Player p = playerWith(wand);
        Block corner = cornerBlock(0, 0);
        PlayerInteractEvent event = interactWith(p, Action.LEFT_CLICK_BLOCK, corner, wand);

        listener.onInteract(event);

        verify(event).setCancelled(true);
        RegionSelection sel = selections.get(PLAYER);
        assertNotNull(sel);
        assertEquals(GeometryUtil.chunkKey(0, 0), sel.pos1());
        assertFalse(sel.hasPos2());
    }

    @Test
    void rightClickWithWandOnCornerSetsPos2() {
        ItemStack wand = new ItemStack(Material.STONE_AXE);
        Player p = playerWith(wand);
        Block corner = cornerBlock(31, 31); // chunk (1, 1)
        PlayerInteractEvent event = interactWith(p, Action.RIGHT_CLICK_BLOCK, corner, wand);

        listener.onInteract(event);

        verify(event).setCancelled(true);
        RegionSelection sel = selections.get(PLAYER);
        assertNotNull(sel);
        assertEquals(GeometryUtil.chunkKey(1, 1), sel.pos2());
    }

    @Test
    void midChunkClickAnchorsTheContainingChunk() {
        ItemStack wand = new ItemStack(Material.STONE_AXE);
        Player p = playerWith(wand);
        Block middle = cornerBlock(7, 8); // mid-chunk (0, 0)
        PlayerInteractEvent event = interactWith(p, Action.LEFT_CLICK_BLOCK, middle, wand);

        listener.onInteract(event);

        // Clicking anywhere inside a chunk anchors that whole chunk — no corner snap required.
        verify(event).setCancelled(true);
        RegionSelection sel = selections.get(PLAYER);
        assertNotNull(sel);
        assertEquals(GeometryUtil.chunkKey(0, 0), sel.pos1());
    }

    @Test
    void wrongItemIsIgnoredEntirely() {
        ItemStack notWand = new ItemStack(Material.STONE);
        Player p = playerWith(notWand);
        Block corner = cornerBlock(0, 0);
        PlayerInteractEvent event = interactWith(p, Action.LEFT_CLICK_BLOCK, corner, notWand);

        listener.onInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
        assertNull(selections.get(PLAYER));
    }

    @Test
    void nullItemIgnored() {
        Player p = playerWith(null);
        PlayerInteractEvent event = interactWith(p, Action.LEFT_CLICK_BLOCK, cornerBlock(0, 0), null);

        listener.onInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void nullClickedBlockIgnored() {
        ItemStack wand = new ItemStack(Material.STONE_AXE);
        Player p = playerWith(wand);
        PlayerInteractEvent event = interactWith(p, Action.LEFT_CLICK_BLOCK, null, wand);

        listener.onInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void nonClickActionIgnored() {
        ItemStack wand = new ItemStack(Material.STONE_AXE);
        Player p = playerWith(wand);
        PlayerInteractEvent event = interactWith(p, Action.PHYSICAL, cornerBlock(0, 0), wand);

        listener.onInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void blockBreakWithWandIsCancelled() {
        Player p = playerWith(new ItemStack(Material.STONE_AXE));
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getPlayer()).thenReturn(p);

        listener.onBlockBreak(event);

        verify(event).setCancelled(true);
    }

    @Test
    void blockBreakWithoutWandLeftAlone() {
        Player p = playerWith(new ItemStack(Material.DIAMOND_PICKAXE));
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getPlayer()).thenReturn(p);

        listener.onBlockBreak(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void sequentialLeftThenRightCompletesSelection() {
        ItemStack wand = new ItemStack(Material.STONE_AXE);
        Player p = playerWith(wand);

        listener.onInteract(interactWith(p, Action.LEFT_CLICK_BLOCK, cornerBlock(0, 0), wand));
        listener.onInteract(interactWith(p, Action.RIGHT_CLICK_BLOCK, cornerBlock(47, 47), wand));

        RegionSelection sel = selections.get(PLAYER);
        assertTrue(sel.isComplete());
        assertEquals(GeometryUtil.chunkKey(0, 0), sel.pos1());
        assertEquals(GeometryUtil.chunkKey(2, 2), sel.pos2());
    }
}
