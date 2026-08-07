package me.beeliebub.tweaks.minigames.resource;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Detects successful pairings for egg-laying Resource Hunt breed targets.
 */
public final class ResourceHuntBreedListener implements Listener {

    private static final long LOVE_MODE_TICKS = 600L;
    private static final long EXPIRY_MARGIN_TICKS = 100L;
    private static final long SWEEP_PERIOD_TICKS = 10L;

    private final Tweaks plugin;
    private final ResourceHunt resourceHunt;
    private final Map<UUID, TrackedAnimal> trackedAnimals = new HashMap<>();
    private BukkitTask sweepTask;

    public ResourceHuntBreedListener(Tweaks plugin, ResourceHunt resourceHunt) {
        this.plugin = plugin;
        this.resourceHunt = resourceHunt;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityEnterLoveMode(EntityEnterLoveModeEvent event) {
        Animals animal = event.getEntity();
        EntityType type = animal.getType();
        if (!ResourceHunt.isEggLayingBreedType(type)) return;
        if (!(event.getHumanEntity() instanceof Player player)) return;
        if (!resourceHunt.getActiveWorldKey().equals(animal.getWorld().getKey().asString())) return;

        UUID playerUuid = player.getUniqueId();
        ResourceHuntTarget.Player target = resourceHunt.targetFor(playerUuid);
        if (target == null || target.category != ResourceHunt.Category.BREED
                || target.entityType != type || resourceHunt.isFullyComplete(playerUuid)) {
            return;
        }

        long loveModeTicks = event.getTicksInLove() > 0 ? event.getTicksInLove() : LOVE_MODE_TICKS;
        long expiryTick = (long) Bukkit.getCurrentTick() + loveModeTicks + EXPIRY_MARGIN_TICKS;
        trackedAnimals.put(animal.getUniqueId(), new TrackedAnimal(playerUuid, type, expiryTick));
        startSweepIfNeeded();
    }

    private void startSweepIfNeeded() {
        if (sweepTask == null || sweepTask.isCancelled()) {
            sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep,
                    SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);
        }
    }

    private void sweep() {
        try {
            sweepOnce();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Resource Hunt breed sweep failed; resetting the tracked-entity sweep.", exception);
            stopSweep();
        }
    }

    private void sweepOnce() {
        if (trackedAnimals.isEmpty()) {
            stopSweep();
            return;
        }

        long currentTick = Bukkit.getCurrentTick();
        Map<PairingGroup, List<BreedPairing.Candidate>> candidatesByGroup = new HashMap<>();
        Iterator<Map.Entry<UUID, TrackedAnimal>> iterator = trackedAnimals.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedAnimal> entry = iterator.next();
            UUID entityUuid = entry.getKey();
            TrackedAnimal tracked = entry.getValue();
            Entity entity = Bukkit.getEntity(entityUuid);
            if (!(entity instanceof Animals animal)
                    || animal.isDead()
                    || !animal.isValid()
                    || currentTick > tracked.expiryTick()
                    || Bukkit.getPlayer(tracked.playerUuid()) == null
                    || animal.getType() != tracked.type()
                    || !resourceHunt.getActiveWorldKey().equals(animal.getWorld().getKey().asString())) {
                iterator.remove();
                continue;
            }

            UUID playerUuid = tracked.playerUuid();
            ResourceHuntTarget.Player target = resourceHunt.targetFor(playerUuid);
            if (target == null || target.category != ResourceHunt.Category.BREED
                    || target.entityType != tracked.type()
                    || resourceHunt.isFullyComplete(playerUuid)) {
                iterator.remove();
                continue;
            }

            if (!animal.isLoveMode() && animal.getAge() > 0) {
                Location location = animal.getLocation();
                String worldKey = animal.getWorld().getKey().asString();
                PairingGroup group = new PairingGroup(playerUuid, tracked.type(), worldKey);
                candidatesByGroup.computeIfAbsent(group, ignored -> new ArrayList<>())
                        .add(new BreedPairing.Candidate(
                                entityUuid,
                                playerUuid,
                                worldKey,
                                location.getX(),
                                location.getY(),
                                location.getZ()));
                iterator.remove();
            }
        }

        for (Map.Entry<PairingGroup, List<BreedPairing.Candidate>> entry : candidatesByGroup.entrySet()) {
            PairingGroup group = entry.getKey();
            Map<UUID, Integer> pairings = BreedPairing.pair(entry.getValue());
            for (Map.Entry<UUID, Integer> pairing : pairings.entrySet()) {
                Player player = Bukkit.getPlayer(pairing.getKey());
                if (player != null) {
                    resourceHunt.recordBreedProgress(player, group.type(), pairing.getValue());
                }
            }
        }

        if (trackedAnimals.isEmpty()) {
            stopSweep();
        }
    }

    private void stopSweep() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }

    private record TrackedAnimal(UUID playerUuid, EntityType type, long expiryTick) {
    }

    private record PairingGroup(UUID playerUuid, EntityType type, String worldKey) {
    }
}
