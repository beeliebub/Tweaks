package me.beeliebub.tweaks.tests.managers;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.managers.BloodMoonManager;
import org.bukkit.World;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BloodMoonManagerTest {

    private ServerMock server;
    private Tweaks plugin;
    private BloodMoonManager manager;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        manager = new BloodMoonManager(plugin);
        world = server.addSimpleWorld("world");
        // Ensure it's NORMAL environment as required by pickReferenceWorld
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void forceNextFullMoonActivates() {
        BloodMoonManager.ForceResult result = manager.forceNextFullMoon();
        assertEquals(BloodMoonManager.ForceResult.ACTIVATED, result);
        assertTrue(manager.isActive());
        
        // Full moon is phase 0. Day 0 % 8 = 0.
        // NIGHT_START_TICK is 13000.
        assertEquals(13000L, world.getFullTime());
    }

    @Test
    void forceNextFullMoonHandlesExistingActivation() {
        manager.forceNextFullMoon();
        BloodMoonManager.ForceResult result = manager.forceNextFullMoon();
        assertEquals(BloodMoonManager.ForceResult.ALREADY_ACTIVE, result);
    }

    @Test
    void onTimeSkipDeactivates() {
        manager.forceNextFullMoon();
        assertTrue(manager.isActive());

        TimeSkipEvent event = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.NIGHT_SKIP, 10000L);
        manager.onTimeSkip(event);

        assertFalse(manager.isActive());
    }

    @Test
    void onBedEnterCancelledWhenActive() {
        manager.forceNextFullMoon();
        PlayerMock player = server.addPlayer();
        
        PlayerBedEnterEvent event = mock(PlayerBedEnterEvent.class);
        when(event.getPlayer()).thenReturn(player);
        
        manager.onBedEnter(event);

        verify(event).setCancelled(true);
        assertNotNull(player.nextMessage());
    }

    @Test
    void deactivateOnDawn() {
        manager.forceNextFullMoon();
        assertTrue(manager.isActive());

        // Morning is time 0 (or anything < 13000)
        world.setTime(0);
        world.setFullTime(24000); // Next day

        // Tick is private, but we can trigger it via start() if we wait, 
        // or just use reflection to call it for testing if we really want to.
        // Actually, let's just use reflection to call tick() to avoid dealing with scheduler in unit tests.
        try {
            java.lang.reflect.Method tick = BloodMoonManager.class.getDeclaredMethod("tick");
            tick.setAccessible(true);
            tick.invoke(manager);
        } catch (Exception e) {
            fail(e);
        }

        assertFalse(manager.isActive());
    }
}
