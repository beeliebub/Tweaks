package me.beeliebub.tweaks.minigames.resource;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.utils.InventoryUtil;
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
            sender.sendMessage(Messages.COMMANDS.rerollRequiresPlayer());
            return true;
        }

        if (!resourceHunt.isActive()) {
            player.sendMessage(Messages.COMMANDS.rerollResourceHuntInactive());
            return true;
        }

        String worldKey = player.getWorld().getKey().asString();
        if (!ResourceHunt.isResourceWorld(worldKey)) {
            player.sendMessage(Messages.COMMANDS.rerollRequiresResourceWorld());
            return true;
        }

        if (resourceHunt.hasCompletedFirstTier(player.getUniqueId())) {
            player.sendMessage(Messages.COMMANDS.rerollTierAlreadyCompleted());
            return true;
        }

        boolean freeRerollAvailable = !resourceHunt.hasUsedFreeReroll(player.getUniqueId());

        if (freeRerollAvailable) {
            resourceHunt.markFreeRerollUsed(player.getUniqueId());
            boolean success = resourceHunt.rerollTarget(player);
            if (!success) {
                player.sendMessage(Messages.COMMANDS.rerollNoTargetAvailable());
                return true;
            }
            player.sendMessage(Messages.COMMANDS.rerollFreeSuccess());
            return true;
        }

        // Paid reroll — attempt to deduct 1 Resource Rupee.
        boolean deducted = InventoryUtil.deductResourceRupees(player, 1);
        if (!deducted) {
            player.sendMessage(Messages.COMMANDS.rerollInsufficientRupees());
            return true;
        }

        boolean success = resourceHunt.rerollTarget(player);
        if (!success) {
            // Refund on the unlikely path where rerollTarget fails after deduction.
            InventoryUtil.addResourceRupees(player, 1);
            player.sendMessage(Messages.COMMANDS.rerollNoTargetAvailable());
            return true;
        }

        player.sendMessage(Messages.COMMANDS.rerollPaidSuccess());
        return true;
    }
}
