package me.beeliebub.tweaks.minigames;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// Handles /reward create, /reward edit, and /reward claim commands.
// Admins create rewards and edit their contents via a chest GUI; players claim pending rewards.
public class RewardCommand implements CommandExecutor, TabCompleter {

    private final RewardManager rewardManager;

    // Track which players have a reward-editing inventory open
    private final Map<UUID, String> editingSessions = new HashMap<>();

    public RewardCommand(RewardManager rewardManager) {
        this.rewardManager = rewardManager;
    }

    public Map<UUID, String> getEditingSessions() {
        return editingSessions;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(sender, args);
            case "edit" -> handleEdit(sender, args);
            case "give" -> handleGive(sender, args);
            case "claim" -> handleClaim(sender);
            default -> sendUsage(sender, label);
        }

        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_REWARD)) {
            sender.sendMessage(Messages.noPermission());
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Messages.MINIGAMES.rewardCreateUsage());
            return;
        }

        String name = args[1].toLowerCase();

        if (rewardManager.rewardExists(name)) {
            sender.sendMessage(Messages.MINIGAMES.rewardAlreadyExists(name));
            return;
        }

        rewardManager.createReward(name);
        sender.sendMessage(Messages.MINIGAMES.rewardCreated(name));
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_REWARD)) {
            sender.sendMessage(Messages.noPermission());
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.MINIGAMES.rewardEditRequiresPlayer());
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Messages.MINIGAMES.rewardEditUsage());
            return;
        }

        String name = args[1].toLowerCase();

        if (!rewardManager.rewardExists(name)) {
            player.sendMessage(Messages.MINIGAMES.rewardDoesNotExist(name));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, Messages.MINIGAMES.rewardEditorTitle(name));

        // Pre-fill with existing items
        ItemStack[] existing = rewardManager.getRewardItems(name);
        for (int i = 0; i < existing.length && i < 27; i++) {
            inv.setItem(i, existing[i]);
        }

        editingSessions.put(player.getUniqueId(), name);
        player.openInventory(inv);
    }

    @SuppressWarnings("deprecation")
    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_REWARD)) {
            sender.sendMessage(Messages.noPermission());
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Messages.MINIGAMES.rewardGiveUsage());
            return;
        }

        String playerName = args[1];
        String rewardName = args[2].toLowerCase();

        int count = 1;
        if (args.length >= 4) {
            try {
                count = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Messages.MINIGAMES.rewardCountMustBeInteger());
                return;
            }
            if (count < 1) {
                sender.sendMessage(Messages.MINIGAMES.rewardCountMustBePositive());
                return;
            }
        }

        if (!rewardManager.rewardExists(rewardName)) {
            sender.sendMessage(Messages.MINIGAMES.rewardDoesNotExist(rewardName));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(Messages.MINIGAMES.rewardRecipientHasNeverPlayed(playerName));
            return;
        }

        for (int i = 0; i < count; i++) {
            rewardManager.grantReward(target.getUniqueId(), rewardName);
        }

        String resolvedName = target.getName() == null ? playerName : target.getName();
        sender.sendMessage(Messages.MINIGAMES.rewardGranted(count, rewardName, resolvedName));

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            onlineTarget.sendMessage(Messages.MINIGAMES.rewardReceived(count, rewardName));
        }
    }

    private void handleClaim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.MINIGAMES.rewardClaimRequiresPlayer());
            return;
        }
        List<String> pending = rewardManager.getPendingRewards(player.getUniqueId());

        if (pending.isEmpty()) {
            player.sendMessage(Messages.MINIGAMES.rewardNoneToClaim());
            return;
        }

        int totalGiven = 0;
        for (String rewardName : pending) {
            ItemStack[] items = rewardManager.getRewardItems(rewardName);
            for (ItemStack item : items) {
                if (item == null) continue;
                ItemStack clone = item.clone();
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(clone);
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
                totalGiven++;
            }
        }

        rewardManager.clearPendingRewards(player.getUniqueId());

        if (totalGiven > 0) {
            player.sendMessage(Messages.MINIGAMES.rewardClaimedWithOverflowWarning());
        } else {
            player.sendMessage(Messages.MINIGAMES.rewardClaimedNoItems());
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        Messages.MINIGAMES.rewardUsage(label, sender.hasPermission(Permissions.ADMIN_REWARD)).forEach(sender::sendMessage);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("claim");
            if (sender.hasPermission(Permissions.ADMIN_REWARD)) {
                subs.add("create");
                subs.add("edit");
                subs.add("give");
            }
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (sender.hasPermission(Permissions.ADMIN_REWARD)) {
            String sub = args[0].toLowerCase();
            if (args.length == 2 && sub.equals("edit")) {
                String partial = args[1].toLowerCase();
                return rewardManager.getRewardNames().stream()
                        .filter(n -> n.startsWith(partial))
                        .toList();
            }
            if (sub.equals("give")) {
                if (args.length == 2) return null; // Let Bukkit suggest online players.
                if (args.length == 3) {
                    String partial = args[2].toLowerCase();
                    return rewardManager.getRewardNames().stream()
                            .filter(n -> n.startsWith(partial))
                            .toList();
                }
                if (args.length == 4) {
                    return List.of("1", "3", "5");
                }
            }
        }

        return Collections.emptyList();
    }
}
