package me.beeliebub.tweaks.skyblock.economy;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/** Legacy command adapter for selling only the player's main inventory. */
public final class SellCommand implements CommandExecutor, TabCompleter {
    private final ShopService shop;

    public SellCommand(ShopService shop) { this.shop = shop; }

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
        ShopService.SellMode mode = ShopService.SellMode.HELD;
        Material material = null;
        if (args.length > 1) {
            player.sendMessage(Messages.SKYBLOCK.invalidInput("/sell [item|all]"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("all")) mode = ShopService.SellMode.ALL;
        else if (args.length == 1) {
            material = Material.matchMaterial(args[0]);
            if (material == null) {
                player.sendMessage(Messages.SKYBLOCK.invalidInput(args[0]));
                return true;
            }
            mode = ShopService.SellMode.ITEM;
        }
        ShopService.SellResult result = shop.sell(player, mode, material);
        if (result.success()) player.sendMessage(Messages.SKYBLOCK.sold(result.amount(), args.length == 0 ? "held item" : args[0], result.price()));
        else player.sendMessage(Messages.SKYBLOCK.invalidInput(result.message()));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        return Arrays.stream(Material.values()).map(Material::name).filter(value -> value.toLowerCase().startsWith(args[0].toLowerCase())).limit(50).toList();
    }
}
