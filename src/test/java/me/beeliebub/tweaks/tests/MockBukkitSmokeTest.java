package me.beeliebub.tweaks.tests;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that MockBukkit boots a server and exposes the bits we'll lean on
 * across the rest of the suite (PlayerMock, WorldMock, ItemStack with real
 * Material registry). If this fails on a Paper bump, every other test that
 * relies on MockBukkit will too — fix here first.
 */
class MockBukkitSmokeTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void canCreateWorldAndPlayer() {
        var world = server.addSimpleWorld("test");
        assertNotNull(world);
        Player player = server.addPlayer("Bee");
        assertNotNull(player);
        assertEquals("Bee", player.getName());
    }

    @Test
    void materialIsAirNoLongerExplodes() {
        // The whole reason we wired in MockBukkit: Material#isAir() needs the
        // RegistryAccess that MockBukkit's ServerMock installs on construction.
        assertTrue(Material.AIR.isAir());
        assertFalse(Material.STONE.isAir());
    }

    @Test
    void itemStackEmptyWorksWithRealMaterialRegistry() {
        ItemStack stone = new ItemStack(Material.STONE);
        assertFalse(stone.isEmpty());
        assertEquals(Material.STONE, stone.getType());
    }
}
