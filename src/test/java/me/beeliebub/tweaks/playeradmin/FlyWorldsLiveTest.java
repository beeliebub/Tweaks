package me.beeliebub.tweaks.playeradmin;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the fly-worlds live-read fix: before this fix, PlayerAdminManager cached
 * fly-worlds once at construction time into a Set, so a /tconfig edit at runtime silently had
 * no effect until restart - the same bug shape disabled-end-portal-worlds had. canFly is
 * package-private, hence this test lives beside production code rather than under tests/ - see the
 * root CLAUDE.md's "Test Layout" note on this project's documented exception.
 */
class FlyWorldsLiveTest {

    private ServerMock server;
    private Tweaks plugin;
    private PlayerAdminManager manager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        manager = new PlayerAdminManager(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void addingAndRemovingFlyWorldFlipsEligibilityImmediatelyWithoutReconstruction() {
        WorldMock world = server.addSimpleWorld("flyworld");
        String worldKey = world.getKey().asString();
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0, 100, 0));

        assertFalse(manager.canFly(player), "not yet a fly-world and no advancement");

        plugin.getConfig().set("fly-worlds", List.of(worldKey));
        assertTrue(manager.canFly(player), "adding the world to fly-worlds should take effect immediately");

        plugin.getConfig().set("fly-worlds", List.of());
        assertFalse(manager.canFly(player), "removing the world from fly-worlds should take effect immediately");
    }
}
