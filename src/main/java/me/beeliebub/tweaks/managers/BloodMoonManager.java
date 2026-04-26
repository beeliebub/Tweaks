package me.beeliebub.tweaks.managers;

import me.beeliebub.tweaks.Tweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

// Tracks the Blood Moon event. At the start of each full-moon night, rolls a 50% chance
// to activate. While active, EnchantTableListener uses a boosted quality-roll chance.
// Ends at dawn (the world day rolls over) or when the night is skipped by sleeping.
public class BloodMoonManager implements Listener {

    private static final long FULL_MOON_PHASE = 0L;
    private static final long NIGHT_START_TICK = 13000L;
    private static final long DAY_LENGTH_TICKS = 24000L;
    private static final long POLL_INTERVAL_TICKS = 20L;
    private static final double ACTIVATION_CHANCE = 0.50;

    private final Tweaks plugin;

    private boolean active = false;
    private long activationDay = -1L;
    private long lastRolledDay = -1L;
    private BukkitTask pollTask;

    public BloodMoonManager(Tweaks plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active;
    }

    public enum ForceResult { ACTIVATED, ALREADY_ACTIVE, NO_WORLD }

    // Advance the reference world's time to the start of the next full-moon night and
    // guarantee activation, bypassing the random roll. If a Blood Moon is already active,
    // does nothing.
    @SuppressWarnings("deprecation")
    public ForceResult forceNextFullMoon() {
        if (active) return ForceResult.ALREADY_ACTIVE;

        World world = pickReferenceWorld();
        if (world == null) return ForceResult.NO_WORLD;

        long fullTime = world.getFullTime();
        long currentDay = fullTime / DAY_LENGTH_TICKS;
        long dayTime = world.getTime();
        long currentPhase = currentDay % 8L;

        long targetFullTime;
        if (currentPhase == FULL_MOON_PHASE && dayTime >= NIGHT_START_TICK) {
            targetFullTime = fullTime;
        } else {
            long daysAhead = (8L - currentPhase) % 8L;
            long targetDay = currentDay + daysAhead;
            targetFullTime = targetDay * DAY_LENGTH_TICKS + NIGHT_START_TICK;
        }
        world.setFullTime(targetFullTime);

        long activatedDay = targetFullTime / DAY_LENGTH_TICKS;
        lastRolledDay = activatedDay;
        activate(activatedDay);
        return ForceResult.ACTIVATED;
    }

    public void start() {
        if (pollTask != null) return;
        pollTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick,
                POLL_INTERVAL_TICKS, POLL_INTERVAL_TICKS);
    }

    public void stop() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
        active = false;
    }

    private void tick() {
        World world = pickReferenceWorld();
        if (world == null) return;

        long currentDay = world.getFullTime() / DAY_LENGTH_TICKS;
        long dayTime = world.getTime();
        long moonPhase = currentDay % 8L;

        if (active && currentDay > activationDay) {
            deactivate();
        }

        if (!active && moonPhase == FULL_MOON_PHASE && dayTime >= NIGHT_START_TICK
                && currentDay != lastRolledDay) {
            lastRolledDay = currentDay;
            if (ThreadLocalRandom.current().nextDouble() < ACTIVATION_CHANCE) {
                activate(currentDay);
            }
        }
    }

    private void activate(long day) {
        active = true;
        activationDay = day;
        plugin.getLogger().info("Blood Moon has risen.");
        announceStart();
    }

    private void deactivate() {
        active = false;
        plugin.getLogger().info("Blood Moon has ended.");
        announceEnd();
    }

    private void announceStart() {
        Component chat = Component.text("The Blood Moon has risen. Quality enchantments favor the bold tonight.")
                .color(NamedTextColor.DARK_RED)
                .decorate(TextDecoration.BOLD);
        Bukkit.getServer().broadcast(chat);

        Title title = Title.title(
                Component.text("Blood Moon").color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
                Component.text("The night turns crimson...").color(NamedTextColor.RED),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000)));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 0.6f, 0.6f);
        }
    }

    private void announceEnd() {
        Component chat = Component.text("The Blood Moon fades.").color(NamedTextColor.RED);
        Bukkit.getServer().broadcast(chat);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, SoundCategory.MASTER, 0.4f, 0.6f);
        }
    }

    @EventHandler
    public void onTimeSkip(TimeSkipEvent event) {
        if (!active) return;
        if (event.getSkipReason() != TimeSkipEvent.SkipReason.NIGHT_SKIP) return;
        deactivate();
    }

    private World pickReferenceWorld() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                return world;
            }
        }
        return null;
    }
}