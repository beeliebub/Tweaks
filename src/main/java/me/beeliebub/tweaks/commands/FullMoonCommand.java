package me.beeliebub.tweaks.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

// Tells the player roughly how many real-world minutes remain until the next
// full-moon night begins, based on the overworld's current time.
public class FullMoonCommand implements CommandExecutor {

    private static final long DAY_LENGTH_TICKS = 24000L;
    private static final long NIGHT_START_TICK = 13000L;
    private static final long FULL_MOON_PHASE = 0L;
    private static final double TICKS_PER_MINUTE = 1200.0;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {

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
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) return world;
        }
        return null;
    }
}