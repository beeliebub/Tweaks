package me.beeliebub.tweaks.tests.minigames.blackjack;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Geometry-layer tests for the PvD Blackjack table detection helpers.
 */
class BlackjackTableGeometryTest {

    private ServerMock server;
    private Tweaks plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ---- Helpers ------------------------------------------------------------

    /**
     * Build a mocked world where every block at Y=64 in the rectangle
     * [minX..minX+w) x [minZ..minZ+d) is a solid STONE block, and every other
     * block is AIR.
     */
    private static World buildTableWorld(int minX, int minZ, int w, int d, int y) {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getUID()).thenReturn(java.util.UUID.randomUUID());

        Block airBlock = mock(Block.class);
        when(airBlock.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(airBlock);

        for (int x = minX; x < minX + w; x++) {
            for (int z = minZ; z < minZ + d; z++) {
                Block solid = mock(Block.class);
                when(solid.getType()).thenReturn(Material.STONE);
                when(solid.getWorld()).thenReturn(world);
                when(solid.getX()).thenReturn(x);
                when(solid.getY()).thenReturn(y);
                when(solid.getZ()).thenReturn(z);
                Location loc = new Location(world, x, y, z);
                when(solid.getLocation()).thenReturn(loc);
                when(world.getBlockAt(x, y, z)).thenReturn(solid);
            }
        }
        return world;
    }

    // ---- isValidTableRect ---------------------------------------------------

    @Test
    void isValidTableRectTrueForAllSolid() {
        World world = buildTableWorld(10, 20, 2, 3, 64);
        assertTrue(BlackjackListener.isValidTableRect(world, 64, 10, 20, 2, 3),
                "2x3 solid rectangle must be valid");
    }

    @Test
    void isValidTableRectFalseIfOneBlockIsAir() {
        World world = buildTableWorld(10, 20, 2, 3, 64);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(11, 64, 21)).thenReturn(air);

        assertFalse(BlackjackListener.isValidTableRect(world, 64, 10, 20, 2, 3),
                "Rectangle with one air block must be invalid");
    }

    // ---- findTableCenterFromButton ------------------------------------------

    /**
     * A NORTH-facing button sits in front of a 2×3 footprint. The center must be
     * at the correct geometric midpoint.
     *
     * <p>Layout: solid blocks at X=1..2, Z=0..2, Y=64.
     * Button at (1,64,-1) facing NORTH.
     */
    @Test
    void findTableCenterFromButtonNorth() {
        World world = buildTableWorld(1, 0, 2, 3, 64);

        Block button = mock(Block.class);
        when(button.getWorld()).thenReturn(world);
        when(button.getX()).thenReturn(1);
        when(button.getY()).thenReturn(64);
        when(button.getZ()).thenReturn(-1);

        Location center = BlackjackListener.findTableCenterFromButton(button, BlockFace.NORTH);

        assertNotNull(center, "findTableCenterFromButton must find the 2x3 footprint");
        assertEquals(2.0,  center.x(), 1e-6, "Center X must be 2.0 for minX=1, width=2");
        assertEquals(65.0, center.y(), 1e-6, "Center Y must be supportY + 1.0 = 65.0");
        assertEquals(1.5,  center.z(), 1e-6, "Center Z must be 1.5 for minZ=0, depth=3");
    }

    @Test
    void findTableCenterFromButtonNullWhenNoSolidFootprint() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);

        Block button = mock(Block.class);
        when(button.getWorld()).thenReturn(world);
        when(button.getX()).thenReturn(0);
        when(button.getY()).thenReturn(64);
        when(button.getZ()).thenReturn(-1);

        Location center = BlackjackListener.findTableCenterFromButton(button, BlockFace.NORTH);
        assertNull(center, "findTableCenterFromButton must return null when no solid footprint exists");
    }
}
