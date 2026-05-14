package me.beeliebub.tweaks.tests.enchantments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.DiceConverterListener;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiceConverterTest {

    private ServerMock server;
    private Tweaks plugin;
    private DiceConverterListener listener;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        listener = new DiceConverterListener(plugin);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void markBlockedRecordsTimestamp() {
        UUID id = player.getUniqueId();
        long until = System.currentTimeMillis() + 2000;
        invokeMarkBlocked(id, until);
        assertTrue(invokeIsBlocked(id, System.currentTimeMillis()));
    }

    @Test
    void blockExpiresAfterDuration() {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        invokeMarkBlocked(id, now - 1); // already expired
        assertFalse(invokeIsBlocked(id, now));
    }

    @Test
    void pickupCancelledWhileBlockedForSplashPotion() {
        UUID id = player.getUniqueId();
        invokeMarkBlocked(id, System.currentTimeMillis() + 2000);

        ItemStack splash = new ItemStack(Material.SPLASH_POTION);
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(splash);

        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getItem()).thenReturn(entity);

        listener.onPickup(event);

        verify(event).setCancelled(true);
    }

    @Test
    void pickupAllowedForNonSplashPotionEvenWhileBlocked() {
        UUID id = player.getUniqueId();
        invokeMarkBlocked(id, System.currentTimeMillis() + 2000);

        ItemStack stone = new ItemStack(Material.STONE);
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(stone);

        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getItem()).thenReturn(entity);

        listener.onPickup(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void pickupAllowedForSplashPotionAfterExpiry() {
        UUID id = player.getUniqueId();
        invokeMarkBlocked(id, System.currentTimeMillis() - 100); // already expired

        ItemStack splash = new ItemStack(Material.SPLASH_POTION);
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(splash);

        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getItem()).thenReturn(entity);

        listener.onPickup(event);

        verify(event, never()).setCancelled(anyBoolean());
        assertFalse(invokeIsBlocked(id, System.currentTimeMillis()), "Expired entries should be cleaned up on pickup attempt");
    }

    @Test
    void projectileLaunchOfNonSplashItemDoesNotBlock() {
        // Even with a thrown potion entity, a lingering potion ItemStack should not trigger the block.
        UUID id = player.getUniqueId();

        ThrownPotion potion = mock(ThrownPotion.class);
        when(potion.getItem()).thenReturn(new ItemStack(Material.LINGERING_POTION));
        when(potion.getShooter()).thenReturn(player);

        ProjectileLaunchEvent event = mock(ProjectileLaunchEvent.class);
        when(event.getEntity()).thenReturn(potion);

        listener.onProjectileLaunch(event);

        assertFalse(invokeIsBlocked(id, System.currentTimeMillis()));
    }

    @Test
    void quitClearsBlockedEntry() {
        UUID id = player.getUniqueId();
        invokeMarkBlocked(id, System.currentTimeMillis() + 2000);
        assertTrue(invokeIsBlocked(id, System.currentTimeMillis()));

        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);
        listener.onPlayerQuit(event);

        assertFalse(invokeIsBlocked(id, System.currentTimeMillis()));
    }

    private void invokeMarkBlocked(UUID id, long until) {
        try {
            Method m = DiceConverterListener.class.getDeclaredMethod("markBlocked", UUID.class, long.class);
            m.setAccessible(true);
            m.invoke(listener, id, until);
        } catch (Exception e) {
            fail(e);
        }
    }

    private boolean invokeIsBlocked(UUID id, long now) {
        try {
            Method m = DiceConverterListener.class.getDeclaredMethod("isPickupBlocked", UUID.class, long.class);
            m.setAccessible(true);
            return (boolean) m.invoke(listener, id, now);
        } catch (Exception e) {
            fail(e);
            return false;
        }
    }
}
