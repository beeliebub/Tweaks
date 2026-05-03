package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

// Handles /tpa, /tpahere, /tpaccept, and /tpdeny teleport request commands.
// Requests expire after 30 seconds and only one pending request per target at a time.
public class TPACommand implements CommandExecutor, TabCompleter {

    private static final int TIMEOUT_SECONDS = 30;
    private final JavaPlugin plugin;
    private final ResourceHuntItems resourceHuntItems;
    // Maps target player UUID -> their pending TPA request
    private final Map<UUID, TpaRequest> requests = new HashMap<>();

    public TPACommand(JavaPlugin plugin, ResourceHuntItems resourceHuntItems) {
        this.plugin = plugin;
        this.resourceHuntItems = resourceHuntItems;
    }

    private record TpaRequest(UUID requester, UUID target, boolean here, BukkitTask expiryTask) {
        void cancel() {
            expiryTask.cancel();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use TPA commands.").color(NamedTextColor.RED));
            return true;
        }

        return switch (label.toLowerCase()) {
            case "tpa" -> handleRequest(player, args, false);
            case "tpahere" -> handleRequest(player, args, true);
            case "tpaccept" -> handleAccept(player);
            case "tpdeny" -> handleDeny(player);
            default -> false;
        };
    }

    private boolean handleRequest(Player sender, String[] args, boolean here) {
        String cmdName = here ? "/tpahere" : "/tpa";

        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: " + cmdName + " <player>").color(NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player '" + args[0] + "' is not online.").color(NamedTextColor.RED));
            return true;
        }

        if (target.equals(sender)) {
            sender.sendMessage(Component.text("You can't send a TPA request to yourself.").color(NamedTextColor.RED));
            return true;
        }

        TpaRequest existing = requests.remove(target.getUniqueId());
        if (existing != null) existing.cancel();

        BukkitTask expiryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TpaRequest expired = requests.remove(target.getUniqueId());
            if (expired != null) {
                Player reqPlayer = Bukkit.getPlayer(expired.requester());
                Player tgtPlayer = Bukkit.getPlayer(expired.target());
                if (reqPlayer != null) {
                    reqPlayer.sendMessage(Component.text("TPA request to " + target.getName() + " has expired.").color(NamedTextColor.GRAY));
                }
                if (tgtPlayer != null) {
                    tgtPlayer.sendMessage(Component.text("TPA request from " + sender.getName() + " has expired.").color(NamedTextColor.GRAY));
                }
            }
        }, TIMEOUT_SECONDS * 20L);

        requests.put(target.getUniqueId(), new TpaRequest(sender.getUniqueId(), target.getUniqueId(), here, expiryTask));
        sender.sendMessage(Component.text("TPA request sent to " + target.getName() + "! They have " + TIMEOUT_SECONDS + " seconds to respond.").color(NamedTextColor.GREEN));
        String description = here ? sender.getName() + " wants you to teleport to them." : sender.getName() + " wants to teleport to you.";
        Component acceptButton = Component.text("[Accept]").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/tpaccept"));
        Component denyButton = Component.text("[Deny]").color(NamedTextColor.RED).decorate(TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/tpdeny"));
        target.sendMessage(Component.text(description).color(NamedTextColor.AQUA));
        target.sendMessage(acceptButton.append(Component.text("  ")).append(denyButton));

        return true;
    }

    private boolean handleAccept(Player player) {
        TpaRequest request = requests.remove(player.getUniqueId());

        if (request == null) {
            player.sendMessage(Component.text("You have no pending TPA requests.").color(NamedTextColor.RED));
            return true;
        }

        request.cancel();

        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) {
            player.sendMessage(Component.text("The requesting player is no longer online.").color(NamedTextColor.RED));
            return true;
        }

        Player teleporting = request.here() ? player : requester;
        Player destination = request.here() ? requester : player;

        // Item safety check: block travel into resource worlds with disallowed items
        boolean enteringResourceFromOutside =
                ResourceHunt.isResourceWorld(destination.getWorld().getKey().asString())
                && !destination.getWorld().getKey().asString().equals(teleporting.getWorld().getKey().asString());

        if (enteringResourceFromOutside) {
            List<org.bukkit.Material> disallowed = resourceHuntItems.getDisallowedItems(teleporting);
            if (!disallowed.isEmpty()) {
                String itemNames = disallowed.stream()
                        .map(m -> m.name().toLowerCase().replace('_', ' '))
                        .distinct()
                        .collect(Collectors.joining(", "));
                teleporting.sendMessage(Component.text("You cannot teleport into the resource world with these items: ", NamedTextColor.RED)
                        .append(Component.text(itemNames, NamedTextColor.YELLOW)));
                destination.sendMessage(Component.text(teleporting.getName() + " has disallowed items and cannot teleport to you.", NamedTextColor.RED));
                return true;
            }
        }

        teleporting.teleportAsync(destination.getLocation()).thenAccept(success -> {
            if (success) {
                teleporting.sendMessage(Component.text("Teleported to " + destination.getName() + "!").color(NamedTextColor.GREEN));
                destination.sendMessage(Component.text(teleporting.getName() + " teleported to you.").color(NamedTextColor.GREEN));
            } else {
                teleporting.sendMessage(Component.text("Teleportation failed.").color(NamedTextColor.RED));
            }
        });

        return true;
    }

    private boolean handleDeny(Player player) {
        TpaRequest request = requests.remove(player.getUniqueId());

        if (request == null) {
            player.sendMessage(Component.text("You have no pending TPA requests.").color(NamedTextColor.RED));
            return true;
        }

        request.cancel();
        player.sendMessage(Component.text("TPA request denied.").color(NamedTextColor.YELLOW));

        Player requester = Bukkit.getPlayer(request.requester());
        if (requester != null) {
            requester.sendMessage(Component.text(player.getName() + " denied your TPA request.").color(NamedTextColor.RED));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        String cmd = label.toLowerCase();
        if ((cmd.equals("tpa") || cmd.equals("tpahere")) && args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .filter(n -> !(sender instanceof Player p) || !n.equals(p.getName()))
                    .toList();
        }
        return Collections.emptyList();
    }
}