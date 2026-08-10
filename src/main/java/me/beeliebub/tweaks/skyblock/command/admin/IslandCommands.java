package me.beeliebub.tweaks.skyblock.command.admin;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.command.IslandAdminCommand;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Live-island administration boundary for {@code /isadmin}. */
public final class IslandCommands {
    private final AdminCommandContext context;

    public IslandCommands(AdminCommandContext context) {
        this.context = context;
    }

    public boolean handle(CommandSender sender, Player player, String[] args) {
        if (args.length < 2) return context.invalidUsage(sender, "island");
        String operation = args[1].toLowerCase(Locale.ROOT);
        if (operation.equals("list")) {
            sender.sendMessage(Messages.SKYBLOCK.memberChanged("Skyblock islands: " + context.admin.listIslands().size()));
            context.admin.listIslands().forEach(island -> sender.sendMessage(Messages.SKYBLOCK.islandInfo(
                    island.id(), Bukkit.getOfflinePlayer(island.owner()).getName(), island.memberCount())));
            return true;
        }
        if (args.length < 3) return context.invalid(sender, "island " + operation + " <owner|id> ...");
        context.admin.resolveIsland(args[2], sender, found -> {
            if (player != null && !context.actorUsable(player)) return;
            if (found.isEmpty()) {
                sender.sendMessage(Messages.SKYBLOCK.islandNotFound(args[2]));
                return;
            }
            Island island = found.get();
            switch (operation) {
                case "inspect" -> sender.sendMessage(Messages.SKYBLOCK.islandInfo(island.id(),
                        Bukkit.getOfflinePlayer(island.owner()).getName(), island.memberCount()));
                case "teleport" -> {
                    if (player == null) context.invalid(sender, "teleport requires an in-game administrator");
                    else player.teleport(context.runtime.islandManager().spawnLocation(island, context.runtime.world()));
                }
                case "force-delete" -> forceDelete(sender, player, island, args);
                case "size" -> resize(sender, island, args);
                case "force-complete" -> forceComplete(sender, player, island, args);
                default -> context.invalid(sender, operation);
            }
        });
        return true;
    }

    private void forceDelete(CommandSender sender, Player player, Island island, String[] args) {
        if (!context.requireConfirmation(sender, "island-force-delete", "island " + island.id(),
                island.memberCount(), "The island, wallet, and member inventories will be removed.",
                AdminArgumentParser.hasTrailingConfirm(args))) return;
        if (player != null && (!player.isOnline() || !player.hasPermission(Permissions.ADMIN_SKYBLOCK)
                || player.getWorld() != context.runtime.world())) return;
        Island current = context.runtime.islandManager().byId(island.id()).orElse(null);
        if (current == null) {
            sender.sendMessage(Messages.SKYBLOCK.islandNotFound(island.id()));
            return;
        }
        SkyblockAdminService.Result result = context.admin.forceDelete(current);
        sender.sendMessage(result.success() ? Messages.SKYBLOCK.deleteStarted()
                : Messages.SKYBLOCK.invalidInput(result.message()));
    }

    private void resize(CommandSender sender, Island island, String[] args) {
        if (args.length < 4) {
            context.invalid(sender, "size SMALL|MEDIUM|LARGE");
            return;
        }
        try {
            IslandSize size = IslandSize.valueOf(args[3].toUpperCase(Locale.ROOT));
            SkyblockAdminService.Result result = context.admin.setSize(island, size);
            sender.sendMessage(result.success() ? Messages.SKYBLOCK.saved("island size")
                    : Messages.SKYBLOCK.invalidInput(result.message()));
        } catch (IllegalArgumentException error) {
            context.invalid(sender, args[3]);
        }
    }

    private void forceComplete(CommandSender sender, Player player, Island island, String[] args) {
        if (args.length < 4) {
            context.invalid(sender, "force-complete <challenge>");
            return;
        }
        SkyblockAdminService.Result result = player == null
                ? context.admin.forceComplete(island, args[3])
                : context.admin.forceComplete(island, args[3], player);
        sender.sendMessage(result.success() ? Messages.SKYBLOCK.challengeClaimed(args[3])
                : Messages.SKYBLOCK.invalidInput(result.message()));
    }
}
