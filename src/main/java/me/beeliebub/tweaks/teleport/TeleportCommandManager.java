package me.beeliebub.tweaks.teleport;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingPaths;
import me.beeliebub.tweaks.utils.Point;
import me.beeliebub.tweaks.profiles.StorageManager;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.utils.OfflinePlayerResolver;
import net.kyori.adventure.text.event.ClickCallback;
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
// /sethome refuses to record homes in any world listed under teleport.sethome-disabled-worlds,
// and /back / /tpa enforce the resource-world item safety check on entry.
public class TeleportCommandManager implements CommandExecutor, TabCompleter, Listener {

    private final JavaPlugin plugin;
    private final StorageManager storage;
    private final ResourceHuntItems resourceHuntItems;
    private SkyblockTeleportGate skyblockGate;

    private NamespacedKey backKey;
    private final Map<UUID, TpaRequest> tpaRequests = new HashMap<>();

    public TeleportCommandManager(JavaPlugin plugin, StorageManager storage,
                                  ResourceHuntItems resourceHuntItems) {
        this.plugin = plugin;
        this.storage = storage;
        this.resourceHuntItems = resourceHuntItems;
    }

    public void setSkyblockGate(SkyblockTeleportGate skyblockGate) {
        this.skyblockGate = skyblockGate;
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
        if (sender instanceof Player player && skyblockGate != null
                && !skyblockGate.allowedCommand(player, label, args)) return true;
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
            sender.sendMessage(Messages.TELEPORT.homeTeleportRequiresPlayer());
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
            String targetInput = args[0];
            String requestedHome = args[1];
            resolveTarget(player, targetInput, target -> showHome(player, target.getUniqueId(), requestedHome));
            return true;
        } else {
            player.sendMessage(Messages.TELEPORT.homeUsage());
            return true;
        }

        showHome(player, targetUUID, homeName);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void enforceSkyblockDestination(PlayerTeleportEvent event) {
        if (skyblockGate != null && !skyblockGate.allowed(event.getPlayer(), event.getTo())) event.setCancelled(true);
    }

    private void showHome(Player player, UUID targetUUID, String homeName) {
        Optional<Point> pointOpt = storage.getHome(targetUUID, homeName);
        if (pointOpt.isEmpty()) {
            player.sendMessage(Messages.TELEPORT.homeNotFound());
            return;
        }

        String finalHomeName = homeName;
        pointOpt.get().toLocation().ifPresentOrElse(loc -> {
            player.sendMessage(Messages.TELEPORT.homeTeleporting(finalHomeName));
            player.teleportAsync(loc).thenAccept(success -> {
                if (!success) {
                    player.sendMessage(Messages.TELEPORT.homeTeleportFailed());
                }
            });
        }, () -> player.sendMessage(Messages.TELEPORT.homeWorldNotLoaded()));
    }

    private boolean handleSetHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.TELEPORT.setHomeRequiresPlayer());
            return true;
        }

        if (isSethomeDisabledWorld(player.getWorld().getKey().asString().toLowerCase())) {
            player.sendMessage(Messages.TELEPORT.setHomeWorldDenied());
            return true;
        }

        UUID targetUUID = player.getUniqueId();
        String homeName = "default";

        if (args.length == 1) {
            homeName = args[0];
        } else if (args.length == 2 && player.hasPermission(Permissions.ADMIN_SETHOME)) {
            String targetInput = args[0];
            String requestedHome = args[1];
            resolveTarget(player, targetInput,
                    target -> setHomeFor(player, target.getUniqueId(), requestedHome));
            return true;
        } else if (args.length > 0) {
            player.sendMessage(Messages.TELEPORT.setHomeUsage());
            return true;
        }

        setHomeFor(player, targetUUID, homeName);
        return true;
    }

    private void setHomeFor(Player player, UUID targetUUID, String homeName) {
        if (targetUUID.equals(player.getUniqueId()) && !player.hasPermission(Permissions.BYPASS_HOMES)) {
            // Live read (no constructor-time caching) so a /tconfig edit to max-homes takes
            // effect immediately.
            int maxHomes = plugin.getConfig().getInt("max-homes", 15);
            if (storage.getHomeCount(targetUUID) >= maxHomes && storage.getHome(targetUUID, homeName).isEmpty()) {
                player.sendMessage(Messages.TELEPORT.setHomeMaximumReached(maxHomes));
                return;
            }
        }

        storage.setHome(targetUUID, homeName, Point.fromLocation(player.getLocation()));
        player.sendMessage(Messages.TELEPORT.setHomeSuccess(homeName));
        String savedHomeName = homeName;
        UUID savedTargetUuid = targetUUID;
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) eventLog.log(LoggingPaths.TELEPORT_HOME_SET, () ->
                "[Teleport] " + ConsoleEventLog.actorLabel(player.getName(), player.getUniqueId())
                        + " set home " + savedHomeName + " for " + savedTargetUuid);
    }

    // Live read (no constructor-time caching) so a /tconfig edit to teleport.sethome-disabled-worlds
    // takes effect immediately - matches WorldRuleListener's disabled-portal-worlds pattern.
    // Replaces the previously hardcoded DISABLED_HOME_WORLD_KEY, which only ever covered
    // jass:resource despite teleport/CLAUDE.md claiming both resource world keys were blocked.
    private boolean isSethomeDisabledWorld(String worldKey) {
        for (String entry : plugin.getConfig().getStringList("teleport.sethome-disabled-worlds")) {
            if (entry.equalsIgnoreCase(worldKey)) return true;
        }
        return false;
    }

    private boolean handleDelHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.TELEPORT.delHomeRequiresPlayer());
            return true;
        }

        UUID targetUUID = player.getUniqueId();
        String homeName = "default";

        if (args.length == 1) {
            homeName = args[0];
        } else if (args.length == 2 && player.hasPermission(Permissions.ADMIN_DELHOME)) {
            String targetInput = args[0];
            String requestedHome = args[1];
            resolveTarget(player, targetInput,
                    target -> deleteHomeFor(player, target.getUniqueId(), requestedHome));
            return true;
        } else if (args.length > 0) {
            player.sendMessage(Messages.TELEPORT.delHomeUsage());
            return true;
        }

        deleteHomeFor(player, targetUUID, homeName);
        return true;
    }

    private void deleteHomeFor(Player player, UUID targetUUID, String homeName) {
        if (storage.getHome(targetUUID, homeName).isEmpty()) {
            player.sendMessage(Messages.TELEPORT.delHomeNotFound(homeName));
            return;
        }

        storage.delHome(targetUUID, homeName);
        player.sendMessage(Messages.TELEPORT.delHomeSuccess(homeName));
        if (!targetUUID.equals(player.getUniqueId())) {
            String deletedHomeName = homeName;
            UUID deletedTargetUuid = targetUUID;
            ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
            if (eventLog != null) eventLog.log(LoggingPaths.TELEPORT_ADMIN_HOME_DELETE, () ->
                    "[Teleport] " + ConsoleEventLog.actorLabel(player.getName(), player.getUniqueId())
                            + " deleted home " + deletedHomeName + " for " + deletedTargetUuid);
        }
    }

    private boolean handleHomes(CommandSender sender, String[] args) {
        if (!(sender instanceof Player) && args.length == 0) {
            sender.sendMessage(Messages.TELEPORT.homesConsoleRequiresPlayer());
            return true;
        }

        UUID targetUUID;
        String targetName;
        if (args.length == 1 && sender.hasPermission(Permissions.ADMIN_HOMES)) {
            String targetInput = args[0];
            resolveTarget(sender, targetInput, target -> showHomes(sender, target));
            return true;
        } else if (sender instanceof Player player) {
            targetUUID = player.getUniqueId();
            targetName = player.getName();
        } else {
            sender.sendMessage(Messages.TELEPORT.homesUsage());
            return true;
        }

        showHomes(sender, targetUUID, targetName);
        return true;
    }

    private void showHomes(CommandSender sender, OfflinePlayer target) {
        showHomes(sender, target.getUniqueId(), target.getName() != null ? target.getName() : "player");
    }

    private void showHomes(CommandSender sender, UUID targetUUID, String targetName) {
        Set<String> homes = storage.getHomes(targetUUID);
        if (homes.isEmpty()) {
            sender.sendMessage(Messages.TELEPORT.homesNone(targetName));
        } else {
            sender.sendMessage(Messages.TELEPORT.homesList(targetName, String.join(", ", homes)));
        }
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
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) return Collections.emptyList();
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
            sender.sendMessage(Messages.TELEPORT.warpRequiresPlayer());
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Messages.TELEPORT.warpUsage());
            return true;
        }
        String warpName = args[0];
        Optional<Point> pointOpt = storage.getWarp(warpName);
        if (pointOpt.isEmpty()) {
            player.sendMessage(Messages.TELEPORT.warpNotFound(warpName));
            return true;
        }
        pointOpt.get().toLocation().ifPresentOrElse(loc -> {
            player.sendMessage(Messages.TELEPORT.warpingTo(warpName));
            player.teleportAsync(loc);
        }, () -> player.sendMessage(Messages.TELEPORT.warpWorldNotLoaded()));
        return true;
    }

    private boolean handleSetWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.TELEPORT.setWarpRequiresPlayer());
            return true;
        }
        if (!player.hasPermission(Permissions.ADMIN_SETWARP)) {
            sender.sendMessage(Messages.noPermission());
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Messages.TELEPORT.setWarpUsage());
            return true;
        }
        String warpName = args[0];
        storage.setWarp(warpName, Point.fromLocation(player.getLocation()));
        player.sendMessage(Messages.TELEPORT.setWarpSuccess(warpName));
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) eventLog.log(LoggingPaths.TELEPORT_WARP_SET, () ->
                "[Teleport] " + ConsoleEventLog.actorLabel(player.getName(), player.getUniqueId())
                        + " set warp " + warpName);
        return true;
    }

    private boolean handleDelWarp(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_DELWARP)) {
            sender.sendMessage(Messages.noPermission());
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Messages.TELEPORT.delWarpUsage());
            return true;
        }
        String warpName = args[0];
        if (storage.getWarp(warpName).isEmpty()) {
            sender.sendMessage(Messages.TELEPORT.warpNotFound(warpName));
            return true;
        }
        storage.delWarp(warpName);
        sender.sendMessage(Messages.TELEPORT.delWarpSuccess(warpName));
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) {
            String actorName = sender instanceof Player player ? player.getName() : null;
            UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
            eventLog.log(LoggingPaths.TELEPORT_WARP_DELETE, () ->
                    "[Teleport] " + ConsoleEventLog.actorLabel(actorName, actorId)
                            + " deleted warp " + warpName);
        }
        return true;
    }

    private boolean handleWarps(CommandSender sender) {
        Set<String> warps = storage.getWarps();
        if (warps.isEmpty()) {
            sender.sendMessage(Messages.TELEPORT.warpsNone());
            return true;
        }
        // Players get the click-to-teleport Dialog; console and other non-player
        // senders fall back to the legacy chat list because Paper's Dialog API
        // only renders for in-game clients.
        if (sender instanceof Player player) {
            player.showDialog(buildWarpsDialog(warps));
            return true;
        }
        sender.sendMessage(Messages.TELEPORT.warpsList(String.join(", ", warps)));
        return true;
    }

    // Constructs the warps Dialog. Button construction lives inside the Dialog.create lambda;
    // static Dialog mocking in the command tests short-circuits before the lambda runs and keeps
    // those tests focused on sender routing rather than rendering details.
    private Dialog buildWarpsDialog(Set<String> warps) {
        var title = Messages.TELEPORT.warpsDialogTitle();
        return Dialog.create(b -> {
            List<ActionButton> buttons = new ArrayList<>();
            for (String warp : warps) {
                var label = Messages.TELEPORT.warpsDialogButton(warp);
                var tooltip = Messages.TELEPORT.warpsDialogTooltip(warp);
                buttons.add(ActionButton.builder(label)
                        .tooltip(tooltip)
                        .width(200)
                        .action(DialogAction.customClick(
                                (view, audience) -> {
                                    if (audience instanceof Player p) {
                                        handleWarp(p, new String[]{warp});
                                    }
                                },
                                ClickCallback.Options.builder().build()))
                        .build());
            }
            b.empty()
                    .base(DialogBase.builder(title).canCloseWithEscape(true).pause(false).build())
                    .type(DialogType.multiAction(buttons, null, 2));
        });
    }

    private boolean handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.TELEPORT.spawnRequiresPlayer());
            return true;
        }
        Optional<Point> pointOpt = storage.getWarp("spawn");
        if (pointOpt.isEmpty()) {
            player.sendMessage(Messages.TELEPORT.spawnNotSet());
            return true;
        }
        pointOpt.get().toLocation().ifPresentOrElse(loc -> {
            player.sendMessage(Messages.TELEPORT.spawnTeleporting());
            player.teleportAsync(loc);
        }, () -> player.sendMessage(Messages.TELEPORT.spawnWorldNotLoaded()));
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
            sender.sendMessage(Messages.TELEPORT.backRequiresPlayer());
            return true;
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String raw = pdc.get(backKey(), PersistentDataType.STRING);

        if (raw == null || raw.isEmpty()) {
            player.sendMessage(Messages.TELEPORT.backNoPreviousLocation());
            return true;
        }

        String[] parts = raw.split(",");
        if (parts.length != 6) {
            player.sendMessage(Messages.TELEPORT.backLocationCorrupt());
            pdc.remove(backKey());
            return true;
        }

        try {
            var world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                player.sendMessage(Messages.TELEPORT.backWorldNotLoaded());
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
                    player.sendMessage(Messages.TELEPORT.backDisallowedItems(itemNames));
                    return true;
                }
            }

            player.teleportAsync(loc).thenAccept(success -> {
                if (success) {
                    player.sendMessage(Messages.TELEPORT.backSuccess());
                } else {
                    player.sendMessage(Messages.TELEPORT.teleportFailed());
                }
            });

        } catch (NumberFormatException e) {
            player.sendMessage(Messages.TELEPORT.backLocationCorrupt());
            pdc.remove(backKey());
        }

        return true;
    }

    // ============================================================
    // TPA
    // ============================================================

    private boolean handleTpaRequest(CommandSender sender, String[] args, boolean here) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.TELEPORT.tpaRequiresPlayer());
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Messages.TELEPORT.tpaUsage(here));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Messages.playerNotOnline(args[0]));
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(Messages.TELEPORT.tpaCannotTargetSelf());
            return true;
        }

        TpaRequest existing = tpaRequests.remove(target.getUniqueId());
        if (existing != null) existing.cancel();

        // Snapshotted once per request (not re-read inside the scheduled task) so the timeout the
        // player was told and the timeout actually scheduled always agree, even if a /tconfig edit
        // lands while this request is pending.
        int timeoutSeconds = tpaTimeoutSeconds();
        BukkitTask expiryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TpaRequest expired = tpaRequests.remove(target.getUniqueId());
            if (expired != null) {
                Player reqPlayer = Bukkit.getPlayer(expired.requester());
                Player tgtPlayer = Bukkit.getPlayer(expired.target());
                if (reqPlayer != null) {
                    reqPlayer.sendMessage(Messages.TELEPORT.tpaRequestExpiredTo(target.getName()));
                }
                if (tgtPlayer != null) {
                    tgtPlayer.sendMessage(Messages.TELEPORT.tpaRequestExpiredFrom(player.getName()));
                }
            }
        }, timeoutSeconds * 20L);

        tpaRequests.put(target.getUniqueId(),
                new TpaRequest(player.getUniqueId(), target.getUniqueId(), here, expiryTask));
        player.sendMessage(Messages.TELEPORT.tpaRequestSent(target.getName(), timeoutSeconds));

        target.sendMessage(Messages.TELEPORT.tpaRequestDescription(player.getName(), here));
        target.sendMessage(Messages.TELEPORT.tpaRequestControls());

        return true;
    }

    private boolean handleTpaAccept(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.TELEPORT.tpaRequiresPlayer());
            return true;
        }
        TpaRequest request = tpaRequests.remove(player.getUniqueId());
        if (request == null) {
            player.sendMessage(Messages.TELEPORT.tpaNoPendingRequest());
            return true;
        }
        request.cancel();

        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) {
            player.sendMessage(Messages.TELEPORT.tpaRequesterOffline());
            return true;
        }

        Player teleporting = request.here() ? player : requester;
        Player destination = request.here() ? requester : player;
        String teleportingName = teleporting.getName();
        UUID teleportingId = teleporting.getUniqueId();
        String destinationName = destination.getName();
        UUID destinationId = destination.getUniqueId();

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
                teleporting.sendMessage(Messages.TELEPORT.tpaDisallowedItems(itemNames));
                destination.sendMessage(Messages.TELEPORT.tpaTeleportingPlayerDisallowed(teleporting.getName()));
                return true;
            }
        }

        teleporting.teleportAsync(destination.getLocation()).thenAccept(success -> {
            if (success) {
                teleporting.sendMessage(Messages.TELEPORT.tpaTeleportSuccess(destinationName));
                destination.sendMessage(Messages.TELEPORT.tpaDestinationNotice(teleportingName));
                ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
                if (eventLog != null) eventLog.log(LoggingPaths.TELEPORT_TPA_ACCEPTED, () ->
                        "[Teleport] " + ConsoleEventLog.actorLabel(destinationName, destinationId)
                                + " accepted TPA for " + ConsoleEventLog.actorLabel(teleportingName, teleportingId));
            } else {
                teleporting.sendMessage(Messages.TELEPORT.teleportFailed());
            }
        });

        return true;
    }

    private boolean handleTpaDeny(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.TELEPORT.tpaRequiresPlayer());
            return true;
        }
        TpaRequest request = tpaRequests.remove(player.getUniqueId());
        if (request == null) {
            player.sendMessage(Messages.TELEPORT.tpaNoPendingRequest());
            return true;
        }
        request.cancel();
        player.sendMessage(Messages.TELEPORT.tpaDenied());

        Player requester = Bukkit.getPlayer(request.requester());
        if (requester != null) {
            requester.sendMessage(Messages.TELEPORT.tpaDeniedBy(player.getName()));
        }
        return true;
    }

    // Live read (no constructor-time caching) so a /tconfig edit to teleport.tpa-timeout-seconds
    // takes effect on the next /tpa request; already-pending requests keep their snapshotted value.
    private int tpaTimeoutSeconds() {
        return plugin.getConfig().getInt("teleport.tpa-timeout-seconds", 30);
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

    private void resolveTarget(CommandSender sender, String input,
                               java.util.function.Consumer<OfflinePlayer> action) {
        var future = OfflinePlayerResolver.resolve(plugin, sender, input);
        boolean asynchronous = !future.isDone();
        future.whenComplete((target, error) -> {
            Runnable continuation = () -> {
                if (asynchronous && !OfflinePlayerResolver.isSenderOnline(sender)) return;
                if (error != null) {
                    if (OfflinePlayerResolver.isLookupInProgress(error)) {
                        sender.sendMessage(Messages.COMMANDS.offlinePlayerLookupBusy());
                    } else {
                        plugin.getLogger().log(java.util.logging.Level.WARNING,
                                "Teleport player lookup failed for " + input, error);
                    }
                    return;
                }
                if (target == null) {
                    sender.sendMessage(Messages.playerNotOnline(input));
                    return;
                }
                action.accept(target);
            };
            if (future.isDone()) continuation.run();
            else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, continuation);
        });
    }
}
