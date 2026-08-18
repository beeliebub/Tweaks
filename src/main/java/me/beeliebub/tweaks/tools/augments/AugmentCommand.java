package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        if (args.length > 0 && (args[0].equalsIgnoreCase("confirm")
                || args[0].equalsIgnoreCase("cancel"))) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.TOOLS.augmentRequiresPlayerSender());
                return true;
            }
            if (args[0].equalsIgnoreCase("cancel")) {
                if (!augments.cancelPending(player)) {
                    player.sendMessage(Messages.TOOLS.augmentConfirmationExpired());
                }
                return true;
            }
            ItemStack item = augments.confirmSlotOneUnlock(player);
            if (item != null) dialog.openHub(player, item);
            return true;
        }
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
                    SlotCalculator.CapacityResolution capacity = augments.slotCalculator()
                            .capacityResolution(item.getType());
                    int purchased = augments.ledger().slots(item);
                    SlotCalculator.PriceResolution price = augments.slotCalculator()
                            .priceResolution(purchased + 1);
                    player.sendMessage(Messages.TOOLS.augmentSlotsBody(
                            augments.slotCalculator().slotDots(augments.ledger().slots(item),
                                    augments.slotCalculator().used(augments.entries(item)),
                                    capacity.value()),
                            augments.slotCalculator().used(augments.entries(item)),
                            capacity.value()));
                    player.sendMessage(Messages.TOOLS.augmentDebugResolution(item.getType().name(),
                            capacity.key(), capacity.value(), price.key(), price.value()));
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
        int nextArgument = 3;
        if (args.length > nextArgument) {
            try {
                level = Math.max(1, Integer.parseInt(args[nextArgument]));
                nextArgument++;
            } catch (NumberFormatException ignored) {
                // The optional level was omitted; the token is the first curse rider.
            }
        }
        List<AugmentGemItem.CurseRider> riders = new ArrayList<>();
        Set<NamespacedKey> riderKeys = new HashSet<>();
        Set<Enchantment> knownCurses = augments.curseEnchantments();
        for (; nextArgument < args.length; nextArgument++) {
            CurseArgument rider = parseCurse(args[nextArgument]);
            if (rider == null) {
                sender.sendMessage(Messages.TOOLS.augmentCurseInvalid(args[nextArgument]));
                return true;
            }
            if (!knownCurses.contains(rider.enchantment())) {
                sender.sendMessage(Messages.TOOLS.augmentCurseNotCurse(rider.key().toString()));
                return true;
            }
            if (rider.key().equals(enchantment.getKey())) {
                sender.sendMessage(Messages.TOOLS.augmentCursePrimary(rider.key().toString()));
                return true;
            }
            if (!riderKeys.add(rider.key())) {
                sender.sendMessage(Messages.TOOLS.augmentCurseDuplicate(rider.key().toString()));
                return true;
            }
            if (riders.size() >= AugmentGemItem.MAX_CURSE_RIDERS) {
                sender.sendMessage(Messages.TOOLS.augmentTooManyCurses(AugmentGemItem.MAX_CURSE_RIDERS));
                return true;
            }
            riders.add(new AugmentGemItem.CurseRider(rider.enchantment(), rider.level()));
        }
        ItemStack gem = augments.gemItem().create(enchantment, level, riders);
        if (gem == null) {
            sender.sendMessage(Messages.TOOLS.augmentTooManyCurses(AugmentGemItem.MAX_CURSE_RIDERS));
            return true;
        }
        if (augments.canFit(target, List.of(gem))) {
            augments.addGems(target, List.of(gem));
            sender.sendMessage(Messages.TOOLS.augmentAttached(
                    Messages.TOOLS.enchantmentName(enchantment, level), riders.size()));
        } else {
            sender.sendMessage(Messages.TOOLS.inventoryFull());
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("give", "debug", "confirm", "cancel");
        if (!args[0].equalsIgnoreCase("give")) return List.of();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
        }
        if (args.length == 3) return enchantmentKeys(false, prefix);
        if (args.length >= 5) return enchantmentKeys(true, prefix);
        return List.of();
    }

    private CurseArgument parseCurse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String token = raw.toLowerCase(Locale.ROOT);
        int level = 1;
        int separator = token.lastIndexOf(':');
        if (separator > 0 && separator < token.length() - 1) {
            String possibleLevel = token.substring(separator + 1);
            try {
                int parsed = Integer.parseInt(possibleLevel);
                if (parsed > 0) {
                    level = parsed;
                    token = token.substring(0, separator);
                }
            } catch (NumberFormatException ignored) {
                // The complete token is the namespaced key.
            }
        }
        NamespacedKey key = NamespacedKey.fromString(token);
        if (key == null) return null;
        Enchantment enchantment = registry().get(key);
        return enchantment == null ? null : new CurseArgument(key, enchantment, level);
    }

    private List<String> enchantmentKeys(boolean cursesOnly, String prefix) {
        Set<Enchantment> knownCurses = cursesOnly ? augments.curseEnchantments() : Set.of();
        return registry().stream()
                .filter(enchantment -> !cursesOnly || knownCurses.contains(enchantment))
                .map(enchantment -> enchantment.getKey().toString())
                .filter(key -> key.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted().toList();
    }

    private org.bukkit.Registry<Enchantment> registry() {
        return io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT);
    }

    private record CurseArgument(NamespacedKey key, Enchantment enchantment, int level) {}
}
