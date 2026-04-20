package me.beeliebub.tweaks.minigames.andrew;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

// Gives sheep-like wandering AI to mannequins with the "andrewkm" profile.
// Mannequins idle, look around randomly, then walk to nearby valid ground positions.
public class MannequinAI implements Listener {

    private static final String TARGET_NAME = "andrewkm";
    private static final double WANDER_RADIUS = 5.0;
    private static final double MOVE_SPEED = 0.03;
    private static final long TICK_INTERVAL = 1L;

    private final Plugin plugin;

    public MannequinAI(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntityType() != EntityType.MANNEQUIN) return;
        Mannequin mannequin = (Mannequin) event.getEntity();
        if (!isAndrewkm(mannequin)) return;

        startWandering(mannequin);
    }

    // Re-attach wandering AI to all existing andrewkm mannequins (called on plugin enable/reload)
    public void resumeAll() {
        for (var world : plugin.getServer().getWorlds()) {
            for (Mannequin mannequin : world.getEntitiesByClass(Mannequin.class)) {
                if (isAndrewkm(mannequin)) {
                    startWandering(mannequin);
                }
            }
        }
    }

    private boolean isAndrewkm(Mannequin mannequin) {
        return TARGET_NAME.equals(mannequin.getProfile().name());
    }

    private void startWandering(Mannequin mannequin) {
        new SheepWanderTask(mannequin).runTaskTimer(plugin, 0L, TICK_INTERVAL);
    }

    private static class SheepWanderTask extends BukkitRunnable {

        private final Mannequin mannequin;
        private final Location origin;
        private Location target;
        private int idleTicks;

        SheepWanderTask(Mannequin mannequin) {
            this.mannequin = mannequin;
            this.origin = mannequin.getLocation().clone();
            this.idleTicks = randomIdle();
        }

        @Override
        public void run() {
            if (!mannequin.isValid() || mannequin.isDead()) {
                cancel();
                return;
            }

            if (idleTicks > 0) {
                idleTicks--;
                if (ThreadLocalRandom.current().nextInt(60) == 0) {
                    float yaw = mannequin.getLocation().getYaw() + ThreadLocalRandom.current().nextFloat(-45, 45);
                    Location look = mannequin.getLocation();
                    look.setYaw(yaw);
                    mannequin.teleport(look);
                }
                return;
            }

            if (target == null) {
                target = pickTarget();
                if (target == null) {
                    idleTicks = randomIdle();
                    return;
                }
                faceLocation(target);
            }

            Location current = mannequin.getLocation();
            Vector direction = target.toVector().subtract(current.toVector());
            double distance = direction.length();

            if (distance < MOVE_SPEED * 2) {
                target = null;
                idleTicks = randomIdle();
                return;
            }

            direction.normalize().multiply(MOVE_SPEED);
            Location next = current.add(direction);
            next.setY(getGroundY(next));
            faceLocation(target, next);
            mannequin.teleport(next);
        }

        private Location pickTarget() {
            ThreadLocalRandom rand = ThreadLocalRandom.current();
            for (int attempts = 0; attempts < 10; attempts++) {
                double dx = rand.nextDouble(-WANDER_RADIUS, WANDER_RADIUS);
                double dz = rand.nextDouble(-WANDER_RADIUS, WANDER_RADIUS);
                Location candidate = origin.clone().add(dx, 0, dz);
                candidate.setY(getGroundY(candidate));

                if (Math.abs(candidate.getY() - origin.getY()) > 2) continue;

                Block ground = candidate.getBlock().getRelative(BlockFace.DOWN);
                if (ground.getType().isSolid() && candidate.getBlock().isPassable()) {
                    return candidate;
                }
            }
            return null;
        }

        private double getGroundY(Location loc) {
            Block block = loc.getWorld().getHighestBlockAt(loc);
            return block.getY() + 1;
        }

        private void faceLocation(Location target) {
            faceLocation(target, mannequin.getLocation());
        }

        private void faceLocation(Location target, Location from) {
            Vector dir = target.toVector().subtract(from.toVector());
            if (dir.lengthSquared() < 0.001) return;
            from.setDirection(dir);
            mannequin.teleport(from);
        }

        private int randomIdle() {
            return ThreadLocalRandom.current().nextInt(60, 200);
        }
    }
}