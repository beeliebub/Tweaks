package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.utils.InventoryUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

// /reroll — Assigns the player a new random Resource Hunt target.
// The first reroll per server session is free; subsequent rerolls each cost 1 Resource Rupee.
public class RerollCommand implements CommandExecutor {

    private final ResourceHunt resourceHunt;

    public RerollCommand(ResourceHunt resourceHunt) {
        this.resourceHunt = resourceHunt;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (!resourceHunt.isActive()) {
            player.sendMessage(Component.text("Resource Hunt is not active this session.", NamedTextColor.RED));
            return true;
        }

        String worldKey = player.getWorld().getKey().asString();
        if (!ResourceHunt.isResourceWorld(worldKey)) {
            player.sendMessage(Component.text("You must be in the resource world to reroll your target.", NamedTextColor.RED));
            return true;
        }

        if (resourceHunt.hasCompletedFirstTier(player.getUniqueId())) {
            player.sendMessage(Component.text(
                    "You have already completed a tier in your current hunt. You cannot reroll until the next session.",
                    NamedTextColor.RED));
            return true;
        }

        boolean freeRerollAvailable = !resourceHunt.hasUsedFreeReroll(player.getUniqueId());

        if (freeRerollAvailable) {
            resourceHunt.markFreeRerollUsed(player.getUniqueId());
            boolean success = resourceHunt.rerollTarget(player);
            if (!success) {
                player.sendMessage(Component.text("Could not reroll — no targets available.", NamedTextColor.RED));
                return true;
            }
            player.sendMessage(Component.text()
                    .append(Component.text("Your target has been rerolled!", NamedTextColor.GREEN))
                    .append(Component.text(" (Free reroll used.)", NamedTextColor.GRAY))
                    .build());
            return true;
        }

        // Paid reroll — attempt to deduct 1 Resource Rupee.
        boolean deducted = InventoryUtil.deductResourceRupees(player, 1);
        if (!deducted) {
            player.sendMessage(Component.text()
                    .append(Component.text("You don't have enough Resource Rupees to reroll.", NamedTextColor.RED))
                    .append(Component.text(" (Cost: 1 Resource Rupee.)", NamedTextColor.GRAY))
                    .build());
            return true;
        }

        boolean success = resourceHunt.rerollTarget(player);
        if (!success) {
            // Refund on the unlikely path where rerollTarget fails after deduction.
            InventoryUtil.addResourceRupees(player, 1);
            player.sendMessage(Component.text("Could not reroll — no targets available.", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text()
                .append(Component.text("Your target has been rerolled!", NamedTextColor.GREEN))
                .append(Component.text(" (1 Resource Rupee deducted.)", NamedTextColor.GRAY))
                .build());
        return true;
    }
}
