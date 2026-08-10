package me.beeliebub.tweaks.skyblock.economy;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Island-wallet transfer command. */
public final class PayCommand implements CommandExecutor, TabCompleter {
    private final SkyblockEconomy economy;
    private final IslandManager islands;
    private final String worldKey;

    public PayCommand(SkyblockEconomy economy, IslandManager islands, String worldKey) {
        this.economy = economy;
        this.islands = islands;
        this.worldKey = worldKey;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.SKYBLOCK.invalidInput("player-only command"));
            return true;
        }
        if (!player.hasPermission(Permissions.SKYBLOCK_USE)) {
            player.sendMessage(Messages.SKYBLOCK.noPermission());
            return true;
        }
        if (!worldKey.equals(player.getWorld().getKey().asString()) || args.length != 2) {
            player.sendMessage(Messages.SKYBLOCK.invalidInput("/pay <player> <amount>"));
            return true;
        }
        Player target = player.getServer().getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Messages.SKYBLOCK.invalidInput("target must be online"));
            return true;
        }
        final double amount;
        try { amount = Double.parseDouble(args[1]); } catch (NumberFormatException error) {
            player.sendMessage(Messages.SKYBLOCK.invalidInput(args[1]));
            return true;
        }
        Island source = islands.forPlayer(player.getUniqueId()).orElse(null);
        Island destination = islands.forPlayer(target.getUniqueId()).orElse(null);
        SkyblockEconomy.Transfer result = economy.transfer(source, destination, amount);
        switch (result) {
            case APPLIED -> player.sendMessage(Messages.SKYBLOCK.transferComplete(target.getName(), amount));
            case SAME_ISLAND -> player.sendMessage(Messages.SKYBLOCK.sameIsland());
            case INSUFFICIENT -> player.sendMessage(Messages.SKYBLOCK.insufficientFunds());
            case NO_ISLAND -> player.sendMessage(Messages.SKYBLOCK.noIsland());
            default -> player.sendMessage(Messages.SKYBLOCK.invalidInput("amount"));
        }
        return true;
    }

    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        return sender.getServer().getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase())).toList();
    }
}
