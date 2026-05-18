package me.beeliebub.tweaks.teleport;

import me.beeliebub.tweaks.Point;
import me.beeliebub.tweaks.managers.StorageManager;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Single executor + listener for every teleportation command in the plugin:
// /home, /sethome, /delhome, /homes, /warp, /setwarp, /delwarp, /warps,
// /spawn, /back, /tpa, /tpahere, /tpaccept, /tpdeny.
//
// /sethome refuses to record homes inside the resource world (hardcoded jass:resource),
// and /back / /tpa enforce the resource-world item safety check on entry.
public class TeleportCommandManager implements CommandExecutor, TabCompleter, Listener {

    private static final String DISABLED_HOME_WORLD_KEY = "jass:resource";
    private static final int TPA_TIMEOUT_SECONDS = 30;

    private final JavaPlugin plugin;
    private final StorageManager storage;
    private final ResourceHuntItems resourceHuntItems;
    private final int maxHomes;

    private NamespacedKey backKey;
    private final Map<UUID, TpaRequest> tpaRequests = new HashMap<>();

    public TeleportCommandManager(JavaPlugin plugin, StorageManager storage,
                                  ResourceHuntItems resourceHuntItems, int maxHomes) {
        this.plugin = plugin;
        this.storage = storage;
        this.resourceHuntItems = resourceHuntItems;
        this.maxHomes = maxHomes;
    }

    // Lazy so unit tests that only exercise unrelated commands can mock the plugin without
    // hitting NamespacedKey's Bukkit-internal validation.
    private NamespacedKey backKey() {
        if (backKey == null) {
            backKey = new NamespacedKey(plugin, "back_location");
        }
        return backKey;
    }

    private record TpaRequest(UUID requester, UUID target, boolean here, BukkitTask expiryTask) {
        void cancel() { expiryTask.cancel(); }
    }

    // ============================================================
    // Dispatch
    // ============================================================

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        return switch (label.toLowerCase()) {
            case "home"     -> handleHome(sender, args);
            case "sethome"  -> handleSetHome(sender, args);
            case "delhome"  -> handleDelHome(sender, args);
            case "homes"    -> handleHomes(sender, args);
            case "warp"     -> handleWarp(sender, args);
            case "setwarp"  -> handleSetWarp(sender, args);
            case "delwarp"  -> handleDelWarp(sender, args);
            case "warps"    -> handleWarps(sender);
            case "spawn"    -> handleSpawn(sender);
            case "back"     -> handleBack(sender);
            case "tpa"      -> handleTpaRequest(sender, args, false);
            case "tpahere"  -> handleTpaRequest(sender, args, true);
            case "tpaccept" -> handleTpaAccept(sender);
            case "tpdeny"   -> handleTpaDeny(sender);
            default -> false;
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        return switch (label.toLowerCase()) {
            case "home"    -> completeHomeAccess(sender, args, Permissions.ADMIN_HOME);
            case "sethome" -> completeHomeAccess(sender, args, Permissions.ADMIN_SETHOME);
            case "delhome" -> completeHomeAccess(sender, args, Permissions.ADMIN_DELHOME);
            case "homes"   -> completeHomesList(sender, args);
            case "warp", "setwarp", "delwarp" -> completeWarpNames(args);
            case "tpa", "tpahere"             -> completeOnlinePlayersExceptSelf(sender, args);
            default -> Collections.emptyList();
        };
    }

    // ============================================================
    // Homes
    // ============================================================

    private boolean handleHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can teleport.").color(NamedTextColor.RED));
            return true;
        }

        UUID targetUUID;
        String homeName;
        if (args.length == 0) {
            targetUUID = player.getUniqueId();
            homeName = "default";
        } else if (args.length == 1) {
            targetUUID = player.getUniqueId();
            homeName = args[0];
        } else if (args.length == 2 && player.hasPermission(Permissions.ADMIN_HOME)) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            targetUUID = target.getUniqueId();
            homeName = args[1];
        } else {
            player.sendMessage(Component.text("Usage: /home [name] | /home <player> <name>").color(NamedTextColor.YELLOW));
            return true;
        }

        Optional<Point> pointOpt = storage.getHome(targetUUID, homeName);
        if (pointOpt.isEmpty()) {
            player.sendMessage(Component.text("Home not found!").color(NamedTextColor.RED));
            return true;
        }

        String finalHomeName = homeName;
        pointOpt.get().toLocation().ifPresentOrElse(loc -> {
            player.sendMessage(Component.text("Teleporting to " + finalHomeName + "...").color(NamedTextColor.GREEN));
            player.teleportAsync(loc).thenAccept(success -> {
                if (!success) {
                    player.sendMessage(Component.text("Teleportation failed. Is the destination safe?").color(NamedTextColor.RED));
                }
            });
        }, () -> player.sendMessage(Component.text("The world this home is in is not loaded!").color(NamedTextColor.DARK_RED)));

        return true;
    }

    private boolean handleSetHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can set homes.").color(NamedTextColor.RED));
            return true;
        }

        if (DISABLED_HOME_WORLD_KEY.equals(player.getWorld().getKey().asString().toLowerCase())) {
            player.sendMessage(Component.text("You cannot set a home in this world.").color(NamedTextColor.RED));
            return true;
        }

        UUID targetUUID = player.getUniqueId();
        String homeName = "default";

        if (args.length == 1) {
            homeName = args[0];
        } else if (args.length == 2 && player.hasPermission(Permissions.ADMIN_SETHOME)) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            targetUUID = target.getUniqueId();
            homeName = args[1];
        } else if (args.length > 0) {
            player.sendMessage(Component.text("Usage: /sethome <name> OR /sethome <player> <name>").color(NamedTextColor.YELLOW));
            return true;
        }

        if (targetUUID.equals(player.getUniqueId()) && !player.hasPermission(Permissions.BYPASS_HOMES)) {
            if (storage.getHomeCount(targetUUID) >= maxHomes && storage.getHome(targetUUID, homeName).isEmpty()) {
                player.sendMessage(Component.text("You have reached the maximum of " + maxHomes + " homes!").color(NamedTextColor.RED));
                return true;
            }
        }

        storage.setHome(targetUUID, homeName, Point.fromLocation(player.getLocation()));
        player.sendMessage(Component.text("Home '" + homeName + "' set successfully!").color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleDelHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can delete homes.").color(NamedTextColor.RED));
            return true;
        }

        UUID targetUUID = player.getUniqueId();
        String homeName = "default";

        if (args.length == 1) {
            homeName = args[0];
        } else if (args.length == 2 && player.hasPermission(Permissions.ADMIN_DELHOME)) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            targetUUID = target.getUniqueId();
            homeName = args[1];
        } else if (args.length > 0) {
            player.sendMessage(Component.text("Usage: /delhome <name> OR /delhome <player> <name>").color(NamedTextColor.YELLOW));
            return true;
        }

        if (storage.getHome(targetUUID, homeName).isEmpty()) {
            player.sendMessage(Component.text("Home '" + homeName + "' does not exist!").color(NamedTextColor.RED));
            return true;
        }

        storage.delHome(targetUUID, homeName);
        player.sendMessage(Component.text("Home '" + homeName + "' deleted successfully!").color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleHomes(CommandSender sender, String[] args) {
        if (!(sender instanceof Player) && args.length == 0) {
            sender.sendMessage(Component.text("Console must specify a player: /homes <player>").color(NamedTextColor.RED));
            return true;
        }

        UUID targetUUID;
        String targetName;
        if (args.length == 1 && sender.hasPermission(Permissions.ADMIN_HOMES)) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            targetUUID = target.getUniqueId();
            targetName = target.getName() != null ? target.getName() : args[0];
        } else if (sender instanceof Player player) {
            targetUUID = player.getUniqueId();
            targetName = player.getName();
        } else {
            sender.sendMessage(Component.text("Usage: /homes OR /homes <player>").color(NamedTextColor.YELLOW));
            return true;
        }

        Set<String> homes = storage.getHomes(targetUUID);
        if (homes.isEmpty()) {
            sender.sendMessage(Component.text(targetName + " has no homes set.").color(NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Homes for " + targetName + ": " + String.join(", ", homes)).color(NamedTextColor.AQUA));
        }
        return true;
    }

    private List<String> completeHomeAccess(CommandSender sender, String[] args, String adminPerm) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(storage.getHomes(player.getUniqueId()));
            if (player.hasPermission(adminPerm)) {
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && player.hasPermission(adminPerm)) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            return storage.getHomes(target.getUniqueId()).stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }

    private List<String> completeHomesList(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission(Permissions.ADMIN_HOMES)) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .toList();
        }
        return Collections.emptyList();
    }

    // ============================================================
    // Warps + Spawn
    // ============================================================

    private boolean handleWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use warps.").color(NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /warp <name>").color(NamedTextColor.YELLOW));
            return true;
        }
        String warpName = args[0];
        Optional<Point> pointOpt = storage.getWarp(warpName);
        if (pointOpt.isEmpty()) {
            player.sendMessage(Component.text("Warp '" + warpName + "' does not exist!").color(NamedTextColor.RED));
            return true;
        }
        pointOpt.get().toLocation().ifPresentOrElse(loc -> {
            player.sendMessage(Component.text("Warping to '" + warpName + "'...").color(NamedTextColor.GREEN));
            player.teleportAsync(loc);
        }, () -> player.sendMessage(Component.text("The world for this warp is not loaded.").color(NamedTextColor.RED)));
        return true;
    }

    private boolean handleSetWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can set warps.").color(NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission(Permissions.ADMIN_SETWARP)) {
            sender.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /setwarp <name>").color(NamedTextColor.YELLOW));
            return true;
        }
        String warpName = args[0];
        storage.setWarp(warpName, Point.fromLocation(player.getLocation()));
        player.sendMessage(Component.text("Warp '" + warpName + "' set successfully!").color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleDelWarp(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_DELWARP)) {
            sender.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /delwarp <name>").color(NamedTextColor.YELLOW));
            return true;
        }
        String warpName = args[0];
        if (storage.getWarp(warpName).isEmpty()) {
            sender.sendMessage(Component.text("Warp '" + warpName + "' does not exist!").color(NamedTextColor.RED));
            return true;
        }
        storage.delWarp(warpName);
        sender.sendMessage(Component.text("Warp '" + warpName + "' deleted successfully!").color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleWarps(CommandSender sender) {
        Set<String> warps = storage.getWarps();
        if (warps.isEmpty()) {
            sender.sendMessage(Component.text("There are no warps available.").color(NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Available Warps: " + String.join(", ", warps)).color(NamedTextColor.AQUA));
        }
        return true;
    }

    private boolean handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can teleport to spawn.").color(NamedTextColor.RED));
            return true;
        }
        Optional<Point> pointOpt = storage.getWarp("spawn");
        if (pointOpt.isEmpty()) {
            player.sendMessage(Component.text("Spawn has not been set!").color(NamedTextColor.RED));
            return true;
        }
        pointOpt.get().toLocation().ifPresentOrElse(loc -> {
            player.sendMessage(Component.text("Teleporting to spawn...").color(NamedTextColor.GREEN));
            player.teleportAsync(loc);
        }, () -> player.sendMessage(Component.text("The world for spawn is not loaded.").color(NamedTextColor.RED)));
        return true;
    }

    private List<String> completeWarpNames(String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return storage.getWarps().stream()
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .toList();
        }
        return Collections.emptyList();
    }

    // ============================================================
    // /back  (command + teleport / death listeners)
    // ============================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) return;
        if (cause == PlayerTeleportEvent.TeleportCause.DISMOUNT) return;
        if (cause == PlayerTeleportEvent.TeleportCause.EXIT_BED) return;
        saveBackLocation(event.getPlayer(), event.getFrom());
    }

    // Vanilla respawn does not fire PlayerTeleportEvent, so the death location is captured
    // here. If the player teleports somewhere after respawning, the teleport listener
    // overwrites this with the post-respawn location.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        saveBackLocation(player, player.getLocation());
    }

    private void saveBackLocation(Player player, Location loc) {
        if (loc.getWorld() == null) return;
        String serialized = loc.getWorld().getName()
                + "," + loc.getX()
                + "," + loc.getY()
                + "," + loc.getZ()
                + "," + loc.getYaw()
                + "," + loc.getPitch();
        player.getPersistentDataContainer().set(backKey(), PersistentDataType.STRING, serialized);
    }

    private boolean handleBack(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /back.").color(NamedTextColor.RED));
            return true;
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String raw = pdc.get(backKey(), PersistentDataType.STRING);

        if (raw == null || raw.isEmpty()) {
            player.sendMessage(Component.text("No previous location found.").color(NamedTextColor.RED));
            return true;
        }

        String[] parts = raw.split(",");
        if (parts.length != 6) {
            player.sendMessage(Component.text("Stored location data is corrupt.").color(NamedTextColor.RED));
            pdc.remove(backKey());
            return true;
        }

        try {
            var world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                player.sendMessage(Component.text("The world for your previous location is not loaded.").color(NamedTextColor.RED));
                return true;
            }

            Location loc = new Location(world,
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]),
                    Float.parseFloat(parts[5]));

            // Only scan inventory when entering a resource world from outside THAT world.
            boolean enteringResourceFromOutside =
                    ResourceHunt.isResourceWorld(world.getKey().asString())
                    && !world.getKey().asString().equals(player.getWorld().getKey().asString());
            if (enteringResourceFromOutside) {
                List<Material> disallowed = resourceHuntItems.getDisallowedItems(player);
                if (!disallowed.isEmpty()) {
                    String itemNames = disallowed.stream()
                            .map(m -> m.name().toLowerCase().replace('_', ' '))
                            .distinct()
                            .collect(Collectors.joining(", "));
                    player.sendMessage(Component.text("You cannot return to the resource world with these items: ", NamedTextColor.RED)
                            .append(Component.text(itemNames, NamedTextColor.YELLOW)));
                    return true;
                }
            }

            player.teleportAsync(loc).thenAccept(success -> {
                if (success) {
                    player.sendMessage(Component.text("Teleported to your previous location!").color(NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Teleportation failed.").color(NamedTextColor.RED));
                }
            });

        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Stored location data is corrupt.").color(NamedTextColor.RED));
            pdc.remove(backKey());
        }

        return true;
    }

    // ============================================================
    // TPA
    // ============================================================

    private boolean handleTpaRequest(CommandSender sender, String[] args, boolean here) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use TPA commands.").color(NamedTextColor.RED));
            return true;
        }
        String cmdName = here ? "/tpahere" : "/tpa";

        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: " + cmdName + " <player>").color(NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Component.text("Player '" + args[0] + "' is not online.").color(NamedTextColor.RED));
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(Component.text("You can't send a TPA request to yourself.").color(NamedTextColor.RED));
            return true;
        }

        TpaRequest existing = tpaRequests.remove(target.getUniqueId());
        if (existing != null) existing.cancel();

        BukkitTask expiryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TpaRequest expired = tpaRequests.remove(target.getUniqueId());
            if (expired != null) {
                Player reqPlayer = Bukkit.getPlayer(expired.requester());
                Player tgtPlayer = Bukkit.getPlayer(expired.target());
                if (reqPlayer != null) {
                    reqPlayer.sendMessage(Component.text("TPA request to " + target.getName() + " has expired.").color(NamedTextColor.GRAY));
                }
                if (tgtPlayer != null) {
                    tgtPlayer.sendMessage(Component.text("TPA request from " + player.getName() + " has expired.").color(NamedTextColor.GRAY));
                }
            }
        }, TPA_TIMEOUT_SECONDS * 20L);

        tpaRequests.put(target.getUniqueId(),
                new TpaRequest(player.getUniqueId(), target.getUniqueId(), here, expiryTask));
        player.sendMessage(Component.text("TPA request sent to " + target.getName() + "! They have "
                + TPA_TIMEOUT_SECONDS + " seconds to respond.").color(NamedTextColor.GREEN));

        String description = here
                ? player.getName() + " wants you to teleport to them."
                : player.getName() + " wants to teleport to you.";
        Component acceptButton = Component.text("[Accept]").color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/tpaccept"));
        Component denyButton = Component.text("[Deny]").color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/tpdeny"));
        target.sendMessage(Component.text(description).color(NamedTextColor.AQUA));
        target.sendMessage(acceptButton.append(Component.text("  ")).append(denyButton));

        return true;
    }

    private boolean handleTpaAccept(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use TPA commands.").color(NamedTextColor.RED));
            return true;
        }
        TpaRequest request = tpaRequests.remove(player.getUniqueId());
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

        boolean enteringResourceFromOutside =
                ResourceHunt.isResourceWorld(destination.getWorld().getKey().asString())
                && !destination.getWorld().getKey().asString().equals(teleporting.getWorld().getKey().asString());

        if (enteringResourceFromOutside) {
            List<Material> disallowed = resourceHuntItems.getDisallowedItems(teleporting);
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

    private boolean handleTpaDeny(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use TPA commands.").color(NamedTextColor.RED));
            return true;
        }
        TpaRequest request = tpaRequests.remove(player.getUniqueId());
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

    private List<String> completeOnlinePlayersExceptSelf(CommandSender sender, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        String partial = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(partial))
                .filter(n -> !(sender instanceof Player p) || !n.equals(p.getName()))
                .toList();
    }
}
