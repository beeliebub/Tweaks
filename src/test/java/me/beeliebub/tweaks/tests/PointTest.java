package me.beeliebub.tweaks.tests;

import me.beeliebub.tweaks.Point;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PointTest {

    @Test
    void recordAccessorsReturnConstructorValues() {
        Point p = new Point("world", 1.5, 64.0, -10.25, 90f, 45f);
        assertEquals("world", p.worldName());
        assertEquals(1.5, p.x());
        assertEquals(64.0, p.y());
        assertEquals(-10.25, p.z());
        assertEquals(90f, p.yaw());
        assertEquals(45f, p.pitch());
    }

    @Test
    void fromLocationCopiesAllFields() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("nether");
        Location loc = new Location(world, 10.0, 70.0, -5.0, 180f, -30f);

        Point p = Point.fromLocation(loc);

        assertEquals("nether", p.worldName());
        assertEquals(10.0, p.x());
        assertEquals(70.0, p.y());
        assertEquals(-5.0, p.z());
        assertEquals(180f, p.yaw());
        assertEquals(-30f, p.pitch());
    }

    @Test
    void toLocationReturnsEmptyWhenWorldUnloaded() {
        Point p = new Point("missing", 0, 0, 0, 0f, 0f);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("missing")).thenReturn(null);
            Optional<Location> result = p.toLocation();
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void toLocationReturnsLocationWhenWorldLoaded() {
        World world = mock(World.class);
        Point p = new Point("world", 100.5, 65.0, -50.25, 12.5f, -7.5f);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            Location loc = p.toLocation().orElseThrow();
            assertSame(world, loc.getWorld());
            assertEquals(100.5, loc.getX());
            assertEquals(65.0, loc.getY());
            assertEquals(-50.25, loc.getZ());
            assertEquals(12.5f, loc.getYaw());
            assertEquals(-7.5f, loc.getPitch());
        }
    }

    @Test
    void roundTripPreservesValuesWhenWorldMatches() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("end");
        Location original = new Location(world, 7.0, 8.0, 9.0, 10f, 11f);
        Point p = Point.fromLocation(original);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("end")).thenReturn(world);
            Location restored = p.toLocation().orElseThrow();
            assertEquals(original.getX(), restored.getX());
            assertEquals(original.getY(), restored.getY());
            assertEquals(original.getZ(), restored.getZ());
            assertEquals(original.getYaw(), restored.getYaw());
            assertEquals(original.getPitch(), restored.getPitch());
        }
    }
}