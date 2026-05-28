package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ResourceCommand implements CommandExecutor, TabCompleter {

    private final ResourceHunt resourceHunt;
    private final ResourceHuntItems resourceHuntItems;

    public ResourceCommand(ResourceHunt resourceHunt, ResourceHuntItems resourceHuntItems) {
        this.resourceHunt = resourceHunt;
        this.resourceHuntItems = resourceHuntItems;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        // Only offer settarget completions — the base /resource has no further args.
        if (args.length == 1) {
            if (player.hasPermission(Permissions.ADMIN_RESOURCE_SETTARGET_SELF)
                    || player.hasPermission(Permissions.ADMIN_RESOURCE_SETTARGET_OTHER)) {
                return filterPrefix(List.of("settarget"), args[0]);
            }
            return Collections.emptyList();
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("settarget")) {
            return Collections.emptyList();
        }

        boolean hasSelf  = player.hasPermission(Permissions.ADMIN_RESOURCE_SETTARGET_SELF);
        boolean hasOther = player.hasPermission(Permissions.ADMIN_RESOURCE_SETTARGET_OTHER);

        if (!hasSelf && !hasOther) return Collections.emptyList();

        Set<String> targetKeys = resourceHunt.getAvailableTargetKeys();

        if (args.length == 2) {
            // Arg 2 is either a player name (if OTHER) or a target key (if SELF-only).
            if (hasOther) {
                // Suggest online player names; the sender can always type a target key too
                // once they've selected a player, but at this position it must be a player name.
                List<String> players = Bukkit.getOnlinePlayers().stream()
                        .map(p -> p.getName())
                        .collect(Collectors.toList());
                return filterPrefix(players, args[1]);
            }
            // SELF-only: arg 2 is the target key
            return filterPrefix(new ArrayList<>(targetKeys), args[1]);
        }

        if (args.length == 3 && hasOther) {
            // Arg 3 is the target key when OTHER is used (player name was arg 2).
            return filterPrefix(new ArrayList<>(targetKeys), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> candidates, String prefix) {
        if (prefix.isEmpty()) return new ArrayList<>(candidates);
        String lower = prefix.toLowerCase();
        return candidates.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 1. Check if the command sender is actually a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        // Route settarget subcommand before the teleport logic.
        if (args.length >= 1 && args[0].equalsIgnoreCase("settarget")) {
            return handleSetTarget(player, args);
        }

        String activeWorldKey = resourceHunt.getActiveWorldKey();

        // Only scan inventory when entering from outside the target resource world.
        // A player already in the specific resource world legally has whatever they're
        // carrying, and re-checking would block a /resource that is just a
        // "return to spawn within the world" convenience.
        boolean alreadyInTargetResource =
                activeWorldKey.equals(player.getWorld().getKey().asString());
        if (!alreadyInTargetResource) {
            List<Material> disallowed = resourceHuntItems.getDisallowedItems(player);
            if (!disallowed.isEmpty()) {
                String itemNames = disallowed.stream()
                        .map(m -> m.name().toLowerCase().replace('_', ' '))
                        .distinct()
                        .collect(Collectors.joining(", "));
                player.sendMessage(Component.text("You cannot enter the resource world with these items: ", NamedTextColor.RED)
                        .append(Component.text(itemNames, NamedTextColor.YELLOW)));
                return true;
            }
        }

        // 2. Locate the specific world
        String[] parts = activeWorldKey.split(":");
        World resourceWorld = Bukkit.getWorld(new NamespacedKey(parts[0], parts[1]));

        if (resourceWorld == null) {
            player.sendMessage(Component.text("The resource world (" + activeWorldKey + ") is currently unavailable.", NamedTextColor.RED));
            return true;
        }

        // 3. Calculate a safe spawn location
        Location defaultSpawn = resourceWorld.getSpawnLocation();
        Location safeLocation;

        if (resourceWorld.getEnvironment() == World.Environment.NETHER) {
            // In the Nether, we can't use getHighestBlockYAt (it gives the ceiling).
            // Attempt to find a safe 2-block air gap nearby, or fallback to a platform.
            Location found = findSafeNetherLocation(defaultSpawn);
            if (found != null) {
                safeLocation = found;
            } else {
                safeLocation = ResourceHunt.createBedrockPlatform(resourceWorld, defaultSpawn.getBlockX(), defaultSpawn.getBlockZ());
                safeLocation.setYaw(defaultSpawn.getYaw());
                safeLocation.setPitch(defaultSpawn.getPitch());
                player.sendMessage(Component.text("No safe spot found; generated a temporary platform.", NamedTextColor.GOLD));
            }
        } else {
            // Get the highest solid block Y coordinate to prevent suffocation or falling
            int safeY = resourceWorld.getHighestBlockYAt(defaultSpawn.getBlockX(), defaultSpawn.getBlockZ());
            safeLocation = new Location(
                    resourceWorld,
                    defaultSpawn.getBlockX() + 0.5,
                    safeY + 1.0,
                    defaultSpawn.getBlockZ() + 0.5,
                    defaultSpawn.getYaw(),
                    defaultSpawn.getPitch()
            );
        }

        // 4. Teleport the player asynchronously (Recommended in PaperMC)
        player.sendMessage(Component.text("Teleporting to the resource world...", NamedTextColor.YELLOW));
        player.teleportAsync(safeLocation).thenAccept(success -> {
            if (success) {
                player.sendMessage(Component.text("Successfully teleported!", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Teleportation was cancelled or failed.", NamedTextColor.RED));
            }
        });

        return true;
    }

    // /resource settarget [player] <targetKey>
    //
    // Permission rules:
    //   SELF-only:  sender can omit the player arg; the target is applied to themselves.
    //   OTHER:      sender must supply a player name as the first positional arg after
    //               "settarget". If the named player happens to be the sender themselves,
    //               the SELF permission alone would also have been sufficient.
    private boolean handleSetTarget(Player sender, String[] args) {
        boolean hasSelf  = sender.hasPermission(Permissions.ADMIN_RESOURCE_SETTARGET_SELF);
        boolean hasOther = sender.hasPermission(Permissions.ADMIN_RESOURCE_SETTARGET_OTHER);

        if (!hasSelf && !hasOther) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (!resourceHunt.isActive()) {
            sender.sendMessage(Component.text("Resource Hunt is not active this session.", NamedTextColor.RED));
            return true;
        }

        // Determine whether a player argument was supplied.
        // With OTHER the syntax is: settarget <player> <targetKey>  (args.length == 3)
        // With SELF the syntax is:  settarget <targetKey>           (args.length == 2)
        // A sender with OTHER can also use the self-only form when targeting themselves.

        Player target;
        String targetKey;

        if (args.length == 3 && hasOther) {
            // Explicit player target
            String playerName = args[1];
            target = Bukkit.getPlayerExact(playerName);
            if (target == null) {
                sender.sendMessage(Component.text("Player '" + playerName + "' is not online.", NamedTextColor.RED));
                return true;
            }
            targetKey = args[2];
        } else if (args.length == 2) {
            // No explicit player: applies to sender only; requires SELF permission.
            if (!hasSelf) {
                sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            target = sender;
            targetKey = args[1];
        } else {
            // Wrong number of arguments
            sender.sendMessage(Component.text("Usage: /resource settarget [player] <targetKey>", NamedTextColor.RED));
            return true;
        }

        boolean applied = resourceHunt.forceTarget(target.getUniqueId(), targetKey);
        if (!applied) {
            sender.sendMessage(Component.text("Unknown target key: ", NamedTextColor.RED)
                    .append(Component.text(targetKey, NamedTextColor.YELLOW))
                    .append(Component.text(". Use tab-completion to see valid keys.", NamedTextColor.RED)));
            return true;
        }

        if (target.equals(sender)) {
            sender.sendMessage(Component.text("Your Resource Hunt target has been set to ", NamedTextColor.GREEN)
                    .append(Component.text(targetKey, NamedTextColor.YELLOW))
                    .append(Component.text(".", NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(Component.text("Set ", NamedTextColor.GREEN)
                    .append(Component.text(target.getName(), NamedTextColor.YELLOW))
                    .append(Component.text("'s Resource Hunt target to ", NamedTextColor.GREEN))
                    .append(Component.text(targetKey, NamedTextColor.YELLOW))
                    .append(Component.text(".", NamedTextColor.GREEN)));
            target.sendMessage(Component.text("Your Resource Hunt target has been changed to ", NamedTextColor.GOLD)
                    .append(Component.text(targetKey, NamedTextColor.YELLOW))
                    .append(Component.text(" by an admin.", NamedTextColor.GOLD)));
        }
        return true;
    }

    private Location findSafeNetherLocation(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        // Search in a small radius for a 2-block air gap with a solid floor
        for (int x = cx - 5; x <= cx + 5; x++) {
            for (int z = cz - 5; z <= cz + 5; z++) {
                for (int y = 31; y < 120; y++) {
                    if (isSafe(world, x, y, z)) {
                        return new Location(world, x + 0.5, y, z + 0.5, center.getYaw(), center.getPitch());
                    }
                }
            }
        }
        return null;
    }

    private boolean isSafe(World world, int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType().isAir() &&
               world.getBlockAt(x, y + 1, z).getType().isAir() &&
               world.getBlockAt(x, y - 1, z).getType().isSolid();
    }
}
