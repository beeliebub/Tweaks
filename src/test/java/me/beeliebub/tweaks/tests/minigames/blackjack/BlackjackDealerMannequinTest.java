package me.beeliebub.tweaks.tests.minigames.blackjack;

import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Unit tests for {@link BlackjackListener#dealerLocation(Location, BlockFace)} — the pure
 * geometry helper that places the dealer mannequin opposite the player's middle button for
 * any table orientation (Tweaks-0ku3).
 *
 * <p>Only the location math is unit-tested. The mannequin spawn/animation in
 * {@code finish()} requires a live server (MockBukkit cannot spawn {@code EntityType.MANNEQUIN}),
 * so that behaviour is verified by manual testing.
 */
class BlackjackDealerMannequinTest {

    /**
     * Expected offset distance from table centre to dealer mannequin along the dealer axis.
     * = PLAYER_Z_OFFSET (0.55) + 1.0 = 1.55.
     */
    private static final double DIST = 1.55;
    private static final double DELTA = 1.0e-9;

    private ServerMock server;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ---- SOUTH (original behaviour, must be unchanged) --------------------------

    @Test
    void south_dealerSitsOnNorthSideFacingPlayer() {
        Location center = new Location(world, 10.0, 64.0, 20.0);
        Location dealer = BlackjackListener.dealerLocation(center, BlockFace.SOUTH);

        // Dealer direction is NORTH (modZ = -1), offset = -1.55 on Z.
        assertEquals(10.0,          dealer.getX(), DELTA, "X unchanged");
        assertEquals(64.0,          dealer.getY(), DELTA, "Y unchanged");
        assertEquals(20.0 - DIST,   dealer.getZ(), DELTA, "Z = center - 1.55");

        // Yaw 0 = south = toward +Z = toward the player seat.
        assertEquals(0.0f,  dealer.getYaw(),   (float) DELTA, "yaw");
        assertEquals(0.0f,  dealer.getPitch(), (float) DELTA, "pitch");
    }

    @Test
    void south_doesNotMutateInput() {
        Location center = new Location(world, -5.0, 70.0, -8.0);
        Location dealer = BlackjackListener.dealerLocation(center, BlockFace.SOUTH);

        assertNotSame(center, dealer);
        assertEquals(-5.0,         center.getX(), DELTA);
        assertEquals(70.0,         center.getY(), DELTA);
        assertEquals(-8.0,         center.getZ(), DELTA);
        assertEquals(-8.0 - DIST,  dealer.getZ(), DELTA);
    }

    // ---- NORTH ------------------------------------------------------------------

    @Test
    void north_dealerSitsOnSouthSideFacingPlayer() {
        Location center = new Location(world, 10.0, 64.0, 20.0);
        Location dealer = BlackjackListener.dealerLocation(center, BlockFace.NORTH);

        // Dealer direction is SOUTH (modZ = +1), offset = +1.55 on Z.
        assertEquals(10.0,         dealer.getX(), DELTA, "X unchanged");
        assertEquals(64.0,         dealer.getY(), DELTA, "Y unchanged");
        assertEquals(20.0 + DIST,  dealer.getZ(), DELTA, "Z = center + 1.55");

        // Yaw 180 = north = toward -Z = toward the player seat (player is on NORTH side).
        assertEquals(180.0f, dealer.getYaw(),   (float) DELTA, "yaw");
        assertEquals(0.0f,   dealer.getPitch(), (float) DELTA, "pitch");
    }

    // ---- EAST -------------------------------------------------------------------

    @Test
    void east_dealerSitsOnWestSideFacingPlayer() {
        Location center = new Location(world, 10.0, 64.0, 20.0);
        Location dealer = BlackjackListener.dealerLocation(center, BlockFace.EAST);

        // Dealer direction is WEST (modX = -1), offset = -1.55 on X; Z unchanged.
        assertEquals(10.0 - DIST,  dealer.getX(), DELTA, "X = center - 1.55");
        assertEquals(64.0,         dealer.getY(), DELTA, "Y unchanged");
        assertEquals(20.0,         dealer.getZ(), DELTA, "Z unchanged");

        // Yaw -90 = east = toward +X = toward the player seat.
        assertEquals(-90.0f, dealer.getYaw(),   (float) DELTA, "yaw");
        assertEquals(0.0f,   dealer.getPitch(), (float) DELTA, "pitch");
    }

    // ---- WEST -------------------------------------------------------------------

    @Test
    void west_dealerSitsOnEastSideFacingPlayer() {
        Location center = new Location(world, 10.0, 64.0, 20.0);
        Location dealer = BlackjackListener.dealerLocation(center, BlockFace.WEST);

        // Dealer direction is EAST (modX = +1), offset = +1.55 on X; Z unchanged.
        assertEquals(10.0 + DIST,  dealer.getX(), DELTA, "X = center + 1.55");
        assertEquals(64.0,         dealer.getY(), DELTA, "Y unchanged");
        assertEquals(20.0,         dealer.getZ(), DELTA, "Z unchanged");

        // Yaw 90 = west = toward -X = toward the player seat.
        assertEquals(90.0f, dealer.getYaw(),   (float) DELTA, "yaw");
        assertEquals(0.0f,  dealer.getPitch(), (float) DELTA, "pitch");
    }
}
