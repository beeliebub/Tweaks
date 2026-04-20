package me.beeliebub.tweaks.minigames.andrew;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import me.beeliebub.tweaks.minigames.RewardManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

// Core game logic for Whack-an-Andrew. Spawns mannequins on designated blocks,
// tracks scores per player, adjusts difficulty over time, and awards rewards to top 3 finishers.
public class WhackGame {

    public enum State { IDLE, RUNNING, PAUSED }
    // PRIMARY = normal (+1 point), SECONDARY = bonus (+N points), TERNARY = penalty (-N points)
    public enum MannequinType { PRIMARY, SECONDARY, TERNARY }

    private final JavaPlugin plugin;
    private final WhackConfig config;
    private final WhackArena arena;
    private final RewardManager rewardManager;

    private State state = State.IDLE;
    private final Map<UUID, Integer> scores = new ConcurrentHashMap<>();
    private final Set<UUID> aliveMannequins = ConcurrentHashMap.newKeySet(); // Mannequins that can still be hit
    private final Map<UUID, MannequinType> mannequinTypes = new ConcurrentHashMap<>(); // Type of each live mannequin
    private final Set<Location> occupiedSpawns = ConcurrentHashMap.newKeySet(); // Spawn spots currently in use
    private final List<Location> recentSpawns = new ArrayList<>(); // Last 5 spawns, used to spread out placement

    private BukkitRunnable spawnTask;
    private BukkitRunnable timerTask;
    private int ticksElapsed;
    private int roundDurationTicks;

    public WhackGame(JavaPlugin plugin, WhackConfig config, WhackArena arena, RewardManager rewardManager) {
        this.plugin = plugin;
        this.config = config;
        this.arena = arena;
        this.rewardManager = rewardManager;
    }

    public void start() {
        if (state == State.RUNNING) return;

        if (state == State.IDLE) {
            scores.clear();
            aliveMannequins.clear();
            mannequinTypes.clear();
            occupiedSpawns.clear();
            recentSpawns.clear();
            ticksElapsed = 0;
            roundDurationTicks = config.getRoundDuration() * 20;
        }

        state = State.RUNNING;
        broadcastToArena(Component.text("Whack an Andrew has started! Hit the mannequins to score!")
                .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        startSpawnTask();
        startTimerTask();
    }

    public void pause() {
        if (state != State.RUNNING) return;
        state = State.PAUSED;
        cancelTasks();
        despawnAll();
        broadcastToArena(Component.text("Game paused.").color(NamedTextColor.YELLOW));
    }

    public void stop() {
        if (state == State.IDLE) return;
        cancelTasks();
        despawnAll();

        if (config.isAnnounceResults()) {
            announceResults();
        }

        state = State.IDLE;
    }

    public State getState() { return state; }

    public Map<UUID, Integer> getScores() { return Collections.unmodifiableMap(scores); }

    /**
     * Called when a player hits a game mannequin. Returns true if the hit was scored.
     */
    public boolean onMannequinHit(Player player, Mannequin mannequin) {
        if (state != State.RUNNING) return false;
        if (!aliveMannequins.remove(mannequin.getUniqueId())) return false;

        MannequinType type = mannequinTypes.remove(mannequin.getUniqueId());
        int points = switch (type) {
            case SECONDARY -> config.getSecondaryPoints();
            case TERNARY -> -config.getTernaryPoints();
            default -> 1;
        };

        scores.merge(player.getUniqueId(), points, Integer::sum);
        occupiedSpawns.remove(mannequin.getLocation().toBlockLocation());
        mannequin.setHealth(0);

        int score = scores.get(player.getUniqueId());
        String pointsText = points > 0 ? "+" + points + "!" : points + "!";
        NamedTextColor pointsColor = points > 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
        player.sendActionBar(Component.text(pointsText + " ")
                .color(pointsColor)
                .append(Component.text("Score: " + score).color(NamedTextColor.GOLD)));

        return true;
    }

    /**
     * Check if a mannequin belongs to this game.
     */
    public boolean isGameMannequin(UUID entityId) {
        return aliveMannequins.contains(entityId);
    }

    private void startSpawnTask() {
        spawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != State.RUNNING) {
                    cancel();
                    return;
                }

                int spawnCount = calculateSpawnCount();
                for (int i = 0; i < spawnCount; i++) {
                    if (aliveMannequins.size() >= config.getMaxAlive()) break;
                    spawnMannequin();
                }
            }
        };
        spawnTask.runTaskTimer(plugin, 20L, calculateCurrentInterval());

        // Dynamically adjust spawn interval over time
        new BukkitRunnable() {
            @Override
            public void run() {
                if (state != State.RUNNING) {
                    cancel();
                    return;
                }
                // Restart spawn task with updated interval
                if (spawnTask != null && !spawnTask.isCancelled()) {
                    spawnTask.cancel();
                }
                spawnTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (state != State.RUNNING) {
                            cancel();
                            return;
                        }
                        int spawnCount = calculateSpawnCount();
                        for (int i = 0; i < spawnCount; i++) {
                            if (aliveMannequins.size() >= config.getMaxAlive()) break;
                            spawnMannequin();
                        }
                    }
                };
                long interval = calculateCurrentInterval();
                spawnTask.runTaskTimer(plugin, interval, interval);
            }
        }.runTaskTimer(plugin, 200L, 200L); // Re-evaluate every 10 seconds
    }

    private void startTimerTask() {
        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != State.RUNNING) {
                    cancel();
                    return;
                }

                ticksElapsed += 20;

                if (ticksElapsed >= roundDurationTicks) {
                    cancel();
                    plugin.getServer().getScheduler().runTask(plugin, () -> stop());
                    return;
                }

                if (config.isShowActionbar()) {
                    int secondsLeft = (roundDurationTicks - ticksElapsed) / 20;
                    int minutes = secondsLeft / 60;
                    int seconds = secondsLeft % 60;
                    String timeStr = String.format("%d:%02d", minutes, seconds);

                    for (Player player : arena.getWorld().getPlayers()) {
                        if (!arena.contains(player.getLocation())) continue;
                        int score = scores.getOrDefault(player.getUniqueId(), 0);
                        player.sendActionBar(
                                Component.text("Score: " + score + " ")
                                        .color(NamedTextColor.GOLD)
                                        .append(Component.text("| Time: " + timeStr)
                                                .color(NamedTextColor.AQUA))
                        );
                    }
                }
            }
        };
        timerTask.runTaskTimer(plugin, 20L, 20L);
    }

    // Spawn a mannequin on a random available block, rolling its type (primary/secondary/ternary)
    private void spawnMannequin() {
        List<Location> blocks = arena.getSpawnBlocks();
        if (blocks.isEmpty()) return;

        // Filter out locations that already have a mannequin
        List<Location> available = blocks.stream()
                .filter(loc -> !occupiedSpawns.contains(loc))
                .toList();
        if (available.isEmpty()) return;

        Location spawnLoc = pickSpawnLocation(available);
        if (spawnLoc == null) return;

        occupiedSpawns.add(spawnLoc);

        // Roll mannequin type
        double roll = ThreadLocalRandom.current().nextDouble(100.0);
        MannequinType type;
        String profileName;
        if (roll < config.getTernaryChance()) {
            type = MannequinType.TERNARY;
            profileName = config.getTernaryProfileName();
        } else if (roll < config.getTernaryChance() + config.getSecondaryChance()) {
            type = MannequinType.SECONDARY;
            profileName = config.getSecondaryProfileName();
        } else {
            type = MannequinType.PRIMARY;
            profileName = config.getProfileName();
        }

        Mannequin mannequin = arena.getWorld().spawn(spawnLoc, Mannequin.class, m -> {
            m.setProfile(ResolvableProfile.resolvableProfile().name(profileName).build());
            m.setAI(false);
            m.setGravity(true);
        });

        aliveMannequins.add(mannequin.getUniqueId());
        mannequinTypes.put(mannequin.getUniqueId(), type);

        // Track recent spawns (keep last 5)
        recentSpawns.add(spawnLoc);
        if (recentSpawns.size() > 5) {
            recentSpawns.removeFirst();
        }

        // Auto-despawn after lifespan
        new BukkitRunnable() {
            @Override
            public void run() {
                if (mannequin.isValid() && !mannequin.isDead()) {
                    aliveMannequins.remove(mannequin.getUniqueId());
                    mannequinTypes.remove(mannequin.getUniqueId());
                    mannequin.remove();
                }
                occupiedSpawns.remove(spawnLoc);
            }
        }.runTaskLater(plugin, config.getMannequinLifespan());
    }

    /**
     * Picks a spawn location that is biased toward being far from recent spawns,
     * while maintaining randomness.
     */
    private Location pickSpawnLocation(List<Location> candidates) {
        if (recentSpawns.isEmpty()) {
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }

        // Score each candidate by minimum distance to recent spawns, then weight selection
        double[] weights = new double[candidates.size()];
        double totalWeight = 0;

        for (int i = 0; i < candidates.size(); i++) {
            Location candidate = candidates.get(i);
            double minDist = Double.MAX_VALUE;
            for (Location recent : recentSpawns) {
                double dist = candidate.distanceSquared(recent);
                if (dist < minDist) minDist = dist;
            }
            // Weight = distance squared (so farther locations are much more likely)
            // Add 1 to avoid zero weights for same-block
            weights[i] = minDist + 1;
            totalWeight += weights[i];
        }

        // Weighted random selection
        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return candidates.get(i);
            }
        }

        return candidates.getLast();
    }

    /**
     * Calculates the current spawn interval based on time elapsed and difficulty.
     */
    private long calculateCurrentInterval() {
        double progress = (double) ticksElapsed / roundDurationTicks;
        double difficultyFactor = config.getDifficulty() / 10.0;

        // Difficulty shifts the starting point: higher difficulty = start closer to min interval
        double effectiveProgress = difficultyFactor + (1.0 - difficultyFactor) * progress;
        effectiveProgress = Math.min(effectiveProgress, 1.0);

        int range = config.getBaseSpawnInterval() - config.getMinSpawnInterval();
        int interval = config.getBaseSpawnInterval() - (int) (range * effectiveProgress);
        return Math.max(interval, config.getMinSpawnInterval());
    }

    /**
     * Calculates how many mannequins to spawn per wave based on elapsed time and difficulty.
     */
    private int calculateSpawnCount() {
        double progress = (double) ticksElapsed / roundDurationTicks;
        double difficultyFactor = config.getDifficulty() / 10.0;
        double effectiveProgress = difficultyFactor * 0.5 + progress;

        // Start at 1, scale up to 4 as the round progresses
        return Math.min(1 + (int) (effectiveProgress * 3), 4);
    }

    private void despawnAll() {
        for (Mannequin mannequin : arena.getWorld().getEntitiesByClass(Mannequin.class)) {
            if (aliveMannequins.remove(mannequin.getUniqueId())) {
                mannequinTypes.remove(mannequin.getUniqueId());
                mannequin.remove();
            }
        }
        occupiedSpawns.clear();
    }

    private void cancelTasks() {
        if (spawnTask != null && !spawnTask.isCancelled()) spawnTask.cancel();
        if (timerTask != null && !timerTask.isCancelled()) timerTask.cancel();
    }

    // Display final scores sorted by points, and grant configured rewards to top 3 players
    private void announceResults() {
        broadcastToArena(Component.text("=== Whack an Andrew - Results ===")
                .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        if (scores.isEmpty()) {
            broadcastToArena(Component.text("No scores recorded.").color(NamedTextColor.GRAY));
            return;
        }

        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        String[] placeRewards = {
                config.getFirstPlaceReward(),
                config.getSecondPlaceReward(),
                config.getThirdPlaceReward()
        };

        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            String name = player != null ? player.getName() : entry.getKey().toString();
            NamedTextColor color = switch (rank) {
                case 1 -> NamedTextColor.GOLD;
                case 2 -> NamedTextColor.GRAY;
                case 3 -> NamedTextColor.RED;
                default -> NamedTextColor.WHITE;
            };
            broadcastToArena(Component.text("#" + rank + " " + name + " - " + entry.getValue() + " points")
                    .color(color));

            // Grant reward for top 3
            if (rank <= 3) {
                String rewardName = placeRewards[rank - 1];
                if (rewardName != null && !rewardName.isEmpty() && rewardManager.rewardExists(rewardName)) {
                    rewardManager.grantReward(entry.getKey(), rewardName);
                    if (player != null) {
                        player.sendMessage(Component.text("You earned a reward! Use /reward claim to collect it.")
                                .color(NamedTextColor.GREEN));
                    }
                }
            }

            rank++;
        }
    }

    private void broadcastToArena(Component message) {
        for (Player player : arena.getWorld().getPlayers()) {
            player.sendMessage(message);
        }
    }
}