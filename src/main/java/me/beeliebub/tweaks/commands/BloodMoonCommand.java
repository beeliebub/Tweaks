package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.managers.BloodMoonManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

// Admin command: advance the reference world to the start of the next full moon
// and force-activate the Blood Moon event.
public class BloodMoonCommand implements CommandExecutor {

    private static final String PERMISSION = "tweaks.admin.bloodmoon";

    private final BloodMoonManager manager;

    public BloodMoonCommand(BloodMoonManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("You do not have permission to use this command.")
                    .color(NamedTextColor.RED));
            return true;
        }

        switch (manager.forceNextFullMoon()) {
            case ALREADY_ACTIVE -> sender.sendMessage(Component.text("A Blood Moon is already active.")
                    .color(NamedTextColor.RED));
            case NO_WORLD -> sender.sendMessage(Component.text("Could not find an overworld to advance.")
                    .color(NamedTextColor.RED));
            case ACTIVATED -> sender.sendMessage(Component.text("Blood Moon forced.")
                    .color(NamedTextColor.GREEN));
        }
        return true;
    }
}