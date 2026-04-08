package me.beeliebub.tweaks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;

public record Point(String worldName, double x, double y, double z, float yaw, float pitch) {

    public static Point fromLocation(Location location) {
        return new Point(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public Optional<Location> toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return Optional.empty();
        return Optional.of(new Location(world, x, y, z, yaw, pitch));
    }

}

