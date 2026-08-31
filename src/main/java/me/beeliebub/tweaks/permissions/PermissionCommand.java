package me.beeliebub.tweaks.permissions;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingPaths;
import me.beeliebub.tweaks.utils.OfflinePlayerResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Command system for managing permissions.
 */
public class PermissionCommand implements CommandExecutor, TabCompleter {
    private final PermissionManager manager;
    private final JavaPlugin plugin;
    private final List<String> externalPermissionSuggestions;

    public PermissionCommand(PermissionManager manager) {
        this(null, manager);
    }

    public PermissionCommand(JavaPlugin plugin, PermissionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.externalPermissionSuggestions = loadExternalPermissionSuggestions(plugin, manager);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_PERMISSIONS)) {
            sender.sendMessage(Messages.noPermission());
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                PermissionGUI.openMainMenu(player, manager);
            } else {
                sendUsage(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("gui")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.PERMISSIONS.guiRequiresPlayer());
                return true;
            }
            PermissionGUI.openMainMenu(player, manager);
            return true;
        }

        if (sub.equals("group")) {
            return handleGroup(sender, args);
        } else if (sub.equals("user")) {
            return handleUser(sender, args);
        }

        sendUsage(sender);
        return true;
    }

    private boolean handleGroup(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.PERMISSIONS.groupUsage());
            return true;
        }

        String name = args[1].toLowerCase();
        String action = args[2].toLowerCase();

        switch (action) {
            case "create" -> {
                if (manager.getGroups().containsKey(name)) {
                    sender.sendMessage(Messages.PERMISSIONS.groupAlreadyExists());
                    return true;
                }
                manager.getGroups().put(name, new PermissionGroup(name));
                if (!persistGroups(sender)) {
                    manager.getGroups().remove(name);
                    return true;
                }
                sender.sendMessage(Messages.PERMISSIONS.groupCreated(name));
                log(sender, LoggingPaths.PERMISSIONS_GROUP, "created group " + name);
            }
            case "delete" -> {
                if (name.equals("default")) {
                    sender.sendMessage(Messages.PERMISSIONS.defaultGroupCannotBeDeleted());
                    return true;
                }
                PermissionManager.StateSnapshot previous = manager.snapshotState();
                if (manager.deleteGroup(name)) {
                    if (!persistGroups(sender) || !persistUsers(sender)) {
                        manager.restoreState(previous);
                        return true;
                    }
                    manager.refreshAllOnlinePlayers();
                    sender.sendMessage(Messages.PERMISSIONS.groupDeleted(name));
                    log(sender, LoggingPaths.PERMISSIONS_GROUP, "deleted group " + name);
                } else {
                    sender.sendMessage(Messages.PERMISSIONS.groupNotFound());
                }
            }
            case "addperm" -> {
                if (args.length < 4) {
                    sender.sendMessage(Messages.PERMISSIONS.groupAddPermissionUsage());
                    return true;
                }
                PermissionGroup group = manager.getGroups().get(name);
                if (group == null) {
                    sender.sendMessage(Messages.PERMISSIONS.groupNotFound());
                    return true;
                }
                group.addPermission(args[3]);
                if (!persistGroups(sender)) {
                    group.removePermission(args[3]);
                    return true;
                }
                manager.refreshAllOnlinePlayers();
                sender.sendMessage(Messages.PERMISSIONS.groupPermissionAdded(name));
                log(sender, LoggingPaths.PERMISSIONS_PERMISSION, "granted " + args[3] + " to group " + name);
            }
            case "delperm" -> {
                if (args.length < 4) {
                    sender.sendMessage(Messages.PERMISSIONS.groupRemovePermissionUsage());
                    return true;
                }
                PermissionGroup group = manager.getGroups().get(name);
                if (group == null) {
                    sender.sendMessage(Messages.PERMISSIONS.groupNotFound());
                    return true;
                }
                group.removePermission(args[3]);
                if (!persistGroups(sender)) {
                    group.addPermission(args[3]);
                    return true;
                }
                manager.refreshAllOnlinePlayers();
                sender.sendMessage(Messages.PERMISSIONS.groupPermissionRemoved(name));
                log(sender, LoggingPaths.PERMISSIONS_PERMISSION, "revoked " + args[3] + " from group " + name);
            }
            case "inherited-from" -> {
                if (args.length < 4) {
                    sender.sendMessage(Messages.PERMISSIONS.groupInheritanceUsage());
                    return true;
                }
                PermissionGroup group = manager.getGroups().get(name);
                if (group == null) {
                    sender.sendMessage(Messages.PERMISSIONS.groupNotFound());
                    return true;
                }
                String parent = args[3].equalsIgnoreCase("none") ? null : args[3].toLowerCase();
                if (parent != null && !manager.getGroups().containsKey(parent)) {
                    sender.sendMessage(Messages.PERMISSIONS.parentGroupNotFound());
                    return true;
                }
                String previousParent = group.getParentName();
                group.setParentName(parent);
                if (!persistGroups(sender)) {
                    group.setParentName(previousParent);
                    return true;
                }
                manager.refreshAllOnlinePlayers();
                sender.sendMessage(Messages.PERMISSIONS.groupInheritanceSet(name, parent));
                log(sender, LoggingPaths.PERMISSIONS_GROUP, "set parent of " + name + " to " + parent);
            }
            default -> sender.sendMessage(Messages.PERMISSIONS.unknownAction());
        }
        return true;
    }

    private boolean handleUser(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.PERMISSIONS.userUsage());
            return true;
        }

        resolveTarget(sender, args[1], target -> applyUserAction(sender, args, target));
        return true;
    }

    private void applyUserAction(CommandSender sender, String[] args, OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        String action = args[2].toLowerCase();

        switch (action) {
            case "addperm" -> {
                if (args.length < 4) {
                    sender.sendMessage(Messages.PERMISSIONS.userAddPermissionUsage());
                    return;
                }
                UserPermissions user = manager.getUserPermissions(uuid);
                user.addPermission(args[3]);
                if (!persistUsers(sender)) {
                    user.removePermission(args[3]);
                    return;
                }
                refreshPlayer(uuid);
                sender.sendMessage(Messages.PERMISSIONS.userPermissionAdded(target.getName()));
                log(sender, LoggingPaths.PERMISSIONS_PERMISSION, "granted " + args[3] + " to user " + uuid);
            }
            case "delperm" -> {
                if (args.length < 4) {
                    sender.sendMessage(Messages.PERMISSIONS.userRemovePermissionUsage());
                    return;
                }
                UserPermissions user = manager.getUserPermissions(uuid);
                user.removePermission(args[3]);
                if (!persistUsers(sender)) {
                    user.addPermission(args[3]);
                    return;
                }
                refreshPlayer(uuid);
                sender.sendMessage(Messages.PERMISSIONS.userPermissionRemoved(target.getName()));
                log(sender, LoggingPaths.PERMISSIONS_PERMISSION, "revoked " + args[3] + " from user " + uuid);
            }
            case "setgroup" -> {
                if (args.length < 4) {
                    sender.sendMessage(Messages.PERMISSIONS.userSetGroupUsage());
                    return;
                }
                String group = args[3].equalsIgnoreCase("none") ? null : args[3].toLowerCase();
                if (group != null && !manager.getGroups().containsKey(group)) {
                    sender.sendMessage(Messages.PERMISSIONS.groupNotFound());
                    return;
                }
                UserPermissions u = manager.getUserPermissions(uuid);
                List<String> previousGroups = new ArrayList<>(u.getGroups());
                u.getGroups().clear();
                if (group != null) u.addGroup(group);
                if (!persistUsers(sender)) {
                    u.getGroups().clear();
                    previousGroups.forEach(u::addGroup);
                    return;
                }
                refreshPlayer(uuid);
                sender.sendMessage(Messages.PERMISSIONS.userGroupSet(target.getName(), group));
                log(sender, LoggingPaths.PERMISSIONS_USER_GROUPS, "set user " + uuid + " groups to " + group);
            }
            default -> sender.sendMessage(Messages.PERMISSIONS.unknownAction());
        }
    }

    private void resolveTarget(CommandSender sender, String input,
                               java.util.function.Consumer<OfflinePlayer> action) {
        if (plugin == null) {
            Player target = Bukkit.getPlayerExact(input);
            if (target != null) action.accept(target);
            else sender.sendMessage(Messages.PERMISSIONS.playerNeverPlayed(input));
            return;
        }
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
                                "Permission player lookup failed for " + input, error);
                        sender.sendMessage(Messages.PERMISSIONS.playerLookupFailed(input));
                    }
                    return;
                }
                if (target == null) {
                    sender.sendMessage(Messages.PERMISSIONS.playerNeverPlayed(input));
                    return;
                }
                action.accept(target);
            };
            if (future.isDone()) continuation.run();
            else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, continuation);
        });
    }

    private void refreshPlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            manager.refreshPlayer(player);
        }
    }

    private void log(CommandSender sender, String path, String detail) {
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog == null) return;
        String actorName = sender instanceof Player player ? player.getName() : null;
        UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
        eventLog.log(path, () -> "[Permissions] " + ConsoleEventLog.actorLabel(actorName, actorId)
                + " " + detail);
    }

    private void sendUsage(CommandSender sender) {
        Messages.PERMISSIONS.commandUsage().forEach(sender::sendMessage);
    }

    private boolean persistGroups(CommandSender sender) {
        try {
            manager.saveGroups();
            return true;
        } catch (IllegalStateException error) {
            sender.sendMessage(Messages.PERMISSIONS.storageFailed());
            return false;
        }
    }

    private boolean persistUsers(CommandSender sender) {
        try {
            manager.saveUsers();
            return true;
        } catch (IllegalStateException error) {
            sender.sendMessage(Messages.PERMISSIONS.storageFailed());
            return false;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_PERMISSIONS)) return Collections.emptyList();

        if (args.length == 1) {
            return filter(List.of("gui", "group", "user"), args[0]);
        }

        if (args[0].equalsIgnoreCase("group")) {
            if (args.length == 2) {
                return filter(new ArrayList<>(manager.getGroups().keySet()), args[1]);
            }
            if (args.length == 3) {
                return filter(List.of("create", "delete", "addperm", "delperm", "inherited-from"), args[2]);
            }
            if (args.length == 4) {
                String action = args[2].toLowerCase();
                if (action.equals("addperm") || action.equals("delperm")) {
                    return filter(permissionSuggestions(), args[3]);
                }
                if (action.equals("inherited-from")) {
                    List<String> groups = new ArrayList<>(manager.getGroups().keySet());
                    groups.add("none");
                    return filter(groups, args[3]);
                }
            }
        }

        if (args[0].equalsIgnoreCase("user")) {
            if (args.length == 2) {
                return null; // Player names
            }
            if (args.length == 3) {
                return filter(List.of("addperm", "delperm", "setgroup"), args[2]);
            }
            if (args.length == 4) {
                String action = args[2].toLowerCase();
                if (action.equals("addperm") || action.equals("delperm")) {
                    return filter(permissionSuggestions(), args[3]);
                }
                if (action.equals("setgroup")) {
                    List<String> groups = new ArrayList<>(manager.getGroups().keySet());
                    groups.add("none");
                    return filter(groups, args[3]);
                }
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        String p = prefix.toLowerCase();
        return list.stream().filter(s -> s.toLowerCase().startsWith(p)).collect(Collectors.toList());
    }

    private List<String> permissionSuggestions() {
        Set<String> permissions = new LinkedHashSet<>(Permissions.getAllPermissions());
        permissions.addAll(externalPermissionSuggestions);
        return new ArrayList<>(permissions);
    }

    /**
     * Tab completion is called once per keystroke, so the administrator-owned catalog is read
     * once when this command is wired instead of doing filesystem I/O on every completion.
     * Permission dialogs deliberately keep their fresh-on-open scan contract.
     */
    private static List<String> loadExternalPermissionSuggestions(JavaPlugin plugin,
                                                                    PermissionManager manager) {
        JavaPlugin catalogPlugin = plugin;
        if (catalogPlugin == null && manager != null) {
            catalogPlugin = manager.getPlugin();
        }
        if (catalogPlugin == null) return List.of();

        Set<String> permissions = new LinkedHashSet<>();
        try {
            ExternalPermissionCatalog catalog = new ExternalPermissionCatalog(catalogPlugin);
            for (ExternalPermissionCatalog.ExternalCategory category : catalog.scan()) {
                category.nodes().forEach(node -> permissions.add(node.node()));
            }
        } catch (RuntimeException error) {
            catalogPlugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Could not load external permissions for tab completion", error);
        }
        return List.copyOf(permissions);
    }
}
