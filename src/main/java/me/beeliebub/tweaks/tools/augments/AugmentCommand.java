package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Ungated player augment menu with permission-gated admin give/debug subcommands. */
public final class AugmentCommand implements CommandExecutor, TabCompleter {

    private final AugmentService augments;
    private final AugmentDialog dialog;

    public AugmentCommand(AugmentService augments, AugmentDialog dialog) {
        this.augments = augments;
        this.dialog = dialog;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!augments.enabled()) {
            sender.sendMessage(Messages.TOOLS.featureDisabled("Augments"));
            return true;
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("debug"))) {
            if (!sender.hasPermission(Permissions.AUGMENT_ADMIN)) {
                sender.sendMessage(Messages.noPermission());
                return true;
            }
            if (args[0].equalsIgnoreCase("debug")) {
                if (sender instanceof Player player) {
                    ItemStack item = player.getInventory().getItemInMainHand();
                    player.sendMessage(Messages.TOOLS.augmentSlotsBody(
                            augments.slotCalculator().slotDots(augments.ledger().slots(item),
                                    augments.slotCalculator().used(augments.entries(item)),
                                    augments.slotCalculator().capacity(item.getType())),
                            augments.slotCalculator().used(augments.entries(item)),
                            augments.slotCalculator().capacity(item.getType())));
                }
                return true;
            }
            return give(sender, args);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.TOOLS.augmentRequiresPlayerSender());
            return true;
        }
        dialog.openHeld(player);
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.TOOLS.augmentUsage());
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Messages.playerNotOnline(args[1]));
            return true;
        }
        var key = NamespacedKey.fromString(args[2].toLowerCase(Locale.ROOT));
        var enchantment = key == null ? null : io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(key);
        if (enchantment == null) {
            sender.sendMessage(Messages.TOOLS.augmentIncompatible());
            return true;
        }
        int level = 1;
        if (args.length >= 4) {
            try { level = Math.max(1, Integer.parseInt(args[3])); }
            catch (NumberFormatException e) { sender.sendMessage(Messages.invalidNumber()); return true; }
        }
        if (target.getInventory().addItem(augments.gemItem().create(enchantment, level)).isEmpty()) {
            sender.sendMessage(Messages.TOOLS.augmentAttached(enchantment.getKey().toString(), level));
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
