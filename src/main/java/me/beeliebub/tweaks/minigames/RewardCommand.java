package me.beeliebub.tweaks.minigames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "edit" -> handleEdit(player, args);
            case "claim" -> handleClaim(player);
            default -> sendUsage(player, label);
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (!player.hasPermission("tweaks.admin.reward")) {
            player.sendMessage(Component.text("You do not have permission.").color(NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /reward create <name>").color(NamedTextColor.RED));
            return;
        }

        String name = args[1].toLowerCase();

        if (rewardManager.rewardExists(name)) {
            player.sendMessage(Component.text("A reward named '" + name + "' already exists.").color(NamedTextColor.RED));
            return;
        }

        rewardManager.createReward(name);
        player.sendMessage(Component.text("Reward '" + name + "' created! Use /reward edit " + name + " to set its items.")
                .color(NamedTextColor.GREEN));
    }

    private void handleEdit(Player player, String[] args) {
        if (!player.hasPermission("tweaks.admin.reward")) {
            player.sendMessage(Component.text("You do not have permission.").color(NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /reward edit <name>").color(NamedTextColor.RED));
            return;
        }

        String name = args[1].toLowerCase();

        if (!rewardManager.rewardExists(name)) {
            player.sendMessage(Component.text("No reward named '" + name + "' exists.").color(NamedTextColor.RED));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Reward: " + name).color(NamedTextColor.GOLD));

        // Pre-fill with existing items
        ItemStack[] existing = rewardManager.getRewardItems(name);
        for (int i = 0; i < existing.length && i < 27; i++) {
            inv.setItem(i, existing[i]);
        }

        editingSessions.put(player.getUniqueId(), name);
        player.openInventory(inv);
    }

    private void handleClaim(Player player) {
        List<String> pending = rewardManager.getPendingRewards(player.getUniqueId());

        if (pending.isEmpty()) {
            player.sendMessage(Component.text("You have no rewards to claim.").color(NamedTextColor.YELLOW));
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
            player.sendMessage(Component.text("Rewards claimed! Items that didn't fit were dropped at your feet.")
                    .color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Rewards claimed, but they contained no items.").color(NamedTextColor.YELLOW));
        }
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage(Component.text("=== Rewards ===").color(NamedTextColor.GOLD));
        if (player.hasPermission("tweaks.admin.reward")) {
            player.sendMessage(Component.text("/" + label + " create <name>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Create a new reward").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/" + label + " edit <name>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Edit reward items").color(NamedTextColor.GRAY)));
        }
        player.sendMessage(Component.text("/" + label + " claim").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Claim pending rewards").color(NamedTextColor.GRAY)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("claim");
            if (sender.hasPermission("tweaks.admin.reward")) {
                subs.add("create");
                subs.add("edit");
            }
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && sender.hasPermission("tweaks.admin.reward")) {
            String sub = args[0].toLowerCase();
            if (sub.equals("edit")) {
                String partial = args[1].toLowerCase();
                return rewardManager.getRewardNames().stream()
                        .filter(n -> n.startsWith(partial))
                        .toList();
            }
        }

        return Collections.emptyList();
    }
}