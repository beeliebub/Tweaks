package me.beeliebub.tweaks.worldmanagement;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

// Consolidates the moon-event manager (BloodMoonManager) and its two commands
// (/bloodmoon and /fullmoon). Single dispatcher routes commands by label.
public class MoonSystem implements Listener, CommandExecutor {

    private static final long FULL_MOON_PHASE = 0L;
    private static final long ROLL_TICK = 12000L;
    private static final long NIGHT_START_TICK = 13000L;
    private static final long NIGHT_DURATION_TICKS = 11000L;
    private static final long DAY_LENGTH_TICKS = 24000L;
    private static final long POLL_INTERVAL_TICKS = 20L;
    private static final double ACTIVATION_CHANCE = 0.50;
    private static final double TICKS_PER_MINUTE = 1200.0;

    private final Tweaks plugin;

    private boolean active = false;
    private long activationDay = -1L;
    private long lastRolledDay = -1L;
    private long scheduledDay = -1L;
    private BukkitTask pollTask;
    private BossBar bossBar;

    public MoonSystem(Tweaks plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active;
    }

    public enum ForceResult { ACTIVATED, ALREADY_ACTIVE, NO_WORLD }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

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
        if (active) {
            deactivate();
        }
    }

    // ─── Commands (/bloodmoon, /fullmoon) ─────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "bloodmoon" -> handleBloodMoon(sender);
            case "fullmoon" -> handleFullMoon(sender);
            default -> false;
        };
    }

    private boolean handleBloodMoon(CommandSender sender) {
        if (!sender.hasPermission(Permissions.ADMIN_BLOODMOON)) {
            sender.sendMessage(Messages.noPermission());
            return true;
        }
        switch (forceNextFullMoon()) {
            case ALREADY_ACTIVE -> sender.sendMessage(Component.text("A Blood Moon is already active.")
                    .color(NamedTextColor.RED));
            case NO_WORLD -> sender.sendMessage(Component.text("Could not find an overworld to advance.")
                    .color(NamedTextColor.RED));
            case ACTIVATED -> sender.sendMessage(Component.text("Blood Moon forced.")
                    .color(NamedTextColor.GREEN));
        }
        return true;
    }

    private boolean handleFullMoon(CommandSender sender) {
        World world = pickReferenceWorld(sender);
        if (world == null) {
            sender.sendMessage(Component.text("Could not find an overworld to check.")
                    .color(NamedTextColor.RED));
            return true;
        }

        long fullTime = world.getFullTime();
        long currentDay = fullTime / DAY_LENGTH_TICKS;
        long dayTime = world.getTime();
        long currentPhase = currentDay % 8L;

        if (currentPhase == FULL_MOON_PHASE && dayTime >= NIGHT_START_TICK) {
            sender.sendMessage(Component.text("The full moon is up right now!")
                    .color(NamedTextColor.LIGHT_PURPLE));
            return true;
        }

        long daysAhead = (8L - currentPhase) % 8L;
        long targetDay = currentDay + (daysAhead == 0 ? 0 : daysAhead);
        long targetFullTime = targetDay * DAY_LENGTH_TICKS + NIGHT_START_TICK;
        long ticksRemaining = targetFullTime - fullTime;

        long minutes = Math.max(1L, Math.round(ticksRemaining / TICKS_PER_MINUTE));

        sender.sendMessage(Component.text("Next full moon in roughly ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(minutes + (minutes == 1 ? " minute" : " minutes"),
                        NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.LIGHT_PURPLE)));
        return true;
    }

    private World pickReferenceWorld(CommandSender sender) {
        if (sender instanceof Player player
                && player.getWorld().getEnvironment() == World.Environment.NORMAL) {
            return player.getWorld();
        }
        return pickReferenceWorld();
    }

    private World pickReferenceWorld() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                return world;
            }
        }
        return null;
    }

    // ─── Blood Moon engine ────────────────────────────────────────────────────

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

    private void tick() {
        World world = pickReferenceWorld();
        if (world == null) return;

        long currentDay = world.getFullTime() / DAY_LENGTH_TICKS;
        long dayTime = world.getTime();
        long moonPhase = currentDay % 8L;

        if (active) {
            if (currentDay > activationDay || dayTime < NIGHT_START_TICK) {
                deactivate();
            } else {
                updateBossBar(dayTime);
            }
            return;
        }

        if (scheduledDay >= 0 && currentDay != scheduledDay) {
            scheduledDay = -1L;
        }

        if (moonPhase == FULL_MOON_PHASE && dayTime >= ROLL_TICK && dayTime < DAY_LENGTH_TICKS
                && currentDay != lastRolledDay) {
            lastRolledDay = currentDay;
            if (ThreadLocalRandom.current().nextDouble() < ACTIVATION_CHANCE) {
                scheduledDay = currentDay;
            }
        }

        if (scheduledDay == currentDay && dayTime >= NIGHT_START_TICK) {
            activate(currentDay);
        }
    }

    private void activate(long day) {
        active = true;
        activationDay = day;
        plugin.getLogger().info("Blood Moon has risen.");

        bossBar = BossBar.bossBar(
                Component.text("Blood Moon", NamedTextColor.RED, TextDecoration.BOLD),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(bossBar);
        }

        announceStart();
    }

    private void deactivate() {
        active = false;
        scheduledDay = -1L;
        plugin.getLogger().info("Blood Moon has ended.");

        if (bossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bossBar);
            }
            bossBar = null;
        }

        announceEnd();
    }

    private void updateBossBar(long dayTime) {
        if (bossBar == null) return;
        long ticksLeft = DAY_LENGTH_TICKS - dayTime;
        float progress = Math.max(0.0f, Math.min(1.0f, (float) ticksLeft / NIGHT_DURATION_TICKS));
        bossBar.progress(progress);
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

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (!active && scheduledDay < 0) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("The Blood Moon's energy prevents you from sleeping.")
                .color(NamedTextColor.RED));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (active && bossBar != null) {
            event.getPlayer().showBossBar(bossBar);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (active && bossBar != null) {
            event.getPlayer().hideBossBar(bossBar);
        }
    }
}
