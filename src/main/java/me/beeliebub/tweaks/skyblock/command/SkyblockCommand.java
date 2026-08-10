package me.beeliebub.tweaks.skyblock.command;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import me.beeliebub.tweaks.skyblock.island.SkyblockSpawn;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Travel and status command for the Skyblock world. */
public final class SkyblockCommand implements CommandExecutor, TabCompleter {

    private final SkyblockBootstrap.Runtime runtime;

    public SkyblockCommand(SkyblockBootstrap.Runtime runtime) {
        this.runtime = runtime;
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
        if (args.length > 0 && !args[0].equalsIgnoreCase("help")) {
            player.sendMessage(Messages.SKYBLOCK.invalidInput(args[0]));
            return true;
        }
        if (player.getWorld() != runtime.world()) {
            player.teleport(runtime.spawn().location(runtime.world()));
            return true;
        }
        IslandManager islands = runtime.islandManager();
        islands.forPlayer(player.getUniqueId())
                .map(island -> islands.spawnLocation(island, runtime.world()))
                .ifPresentOrElse(player::teleport,
                        () -> player.teleport(runtime.spawn().location(runtime.world())));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? List.of("help") : List.of();
    }
}
