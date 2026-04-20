package me.beeliebub.tweaks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;

// World-independent location storage that can be saved to YAML without referencing live World objects
public record Point(String worldName, double x, double y, double z, float yaw, float pitch) {

    // Convert a live Bukkit Location into a storable Point
    public static Point fromLocation(Location location) {
        return new Point(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    // Convert back to a live Location, returns empty if the world is not loaded
    public Optional<Location> toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return Optional.empty();
        return Optional.of(new Location(world, x, y, z, yaw, pitch));
    }

}

