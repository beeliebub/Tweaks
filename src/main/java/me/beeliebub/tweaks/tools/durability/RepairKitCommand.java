package me.beeliebub.tweaks.tools.durability;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Administrative repair-kit give/debug command. */
public final class RepairKitCommand implements CommandExecutor, TabCompleter {

    private final RepairKitItem kitItem;
    private final DurabilityService durability;

    public RepairKitCommand(RepairKitItem kitItem, DurabilityService durability) {
        this.kitItem = kitItem;
        this.durability = durability;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!kitItem.enabled()) {
            sender.sendMessage(Messages.TOOLS.featureDisabled("Repair kits"));
            return true;
        }
        if (!sender.hasPermission(Permissions.REPAIRKIT_ADMIN)) {
            sender.sendMessage(me.beeliebub.tweaks.core.Messages.noPermission());
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Messages.TOOLS.repairKitUsage());
            return true;
        }
        if (args[0].equalsIgnoreCase("debug")) {
            if (sender instanceof Player player) {
                ItemStack item = player.getInventory().getItemInMainHand();
                durability.ensureStamped(item);
                boolean ledger = AugmentLedger.hasLedger(item);
                boolean stamp = durability.hasStamp(item);
                int maxDamage = durability.maxDamage(item);
                player.sendMessage(Messages.TOOLS.repairKitDebug(durability.isDamageable(item), ledger, stamp,
                        durability.tier(item), durability.maxTier(), durability.damage(item), maxDamage,
                        durability.depletedThreshold(item), durability.isSpent(item),
                        durability.effectiveTierStep()));
            } else {
                sender.sendMessage(Messages.TOOLS.repairKitRequiresPlayer());
            }
            return true;
        }
        if (!args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Messages.TOOLS.repairKitUsage());
            return true;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
        if (target == null) {
            sender.sendMessage(Messages.TOOLS.repairKitRequiresPlayer());
            return true;
        }
        int amount = 1;
        if (args.length >= 3) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[2]))); }
            catch (NumberFormatException e) { sender.sendMessage(Messages.invalidNumber()); return true; }
        }
        if (target.getInventory().addItem(kitItem.create(amount)).isEmpty()) {
            sender.sendMessage(Messages.TOOLS.repairKitGiven());
        } else {
            sender.sendMessage(Messages.TOOLS.inventoryFull());
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? List.of("give", "debug") : List.of();
    }
}
