package me.beeliebub.tweaks.permissions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Command system for managing permissions.
 */
public class PermissionCommand implements CommandExecutor, TabCompleter {
    private final PermissionManager manager;

    public PermissionCommand(PermissionManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_PERMISSIONS)) {
            sender.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
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
                sender.sendMessage(Component.text("Only players can use the GUI.").color(NamedTextColor.RED));
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
            sender.sendMessage(Component.text("Usage: /tprm group <name> <create|delete|addperm|delperm|inherited-from>").color(NamedTextColor.RED));
            return true;
        }

        String name = args[1].toLowerCase();
        String action = args[2].toLowerCase();

        switch (action) {
            case "create" -> {
                if (manager.getGroups().containsKey(name)) {
                    sender.sendMessage(Component.text("Group already exists.").color(NamedTextColor.RED));
                    return true;
                }
                manager.getGroups().put(name, new PermissionGroup(name));
                manager.saveGroups();
                sender.sendMessage(Component.text("Group '" + name + "' created.").color(NamedTextColor.GREEN));
            }
            case "delete" -> {
                if (name.equals("default")) {
                    sender.sendMessage(Component.text("Cannot delete default group.").color(NamedTextColor.RED));
                    return true;
                }
                if (manager.getGroups().remove(name) != null) {
                    manager.saveGroups();
                    sender.sendMessage(Component.text("Group '" + name + "' deleted.").color(NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Group not found.").color(NamedTextColor.RED));
                }
            }
            case "addperm" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /tprm group <name> addperm <permission>").color(NamedTextColor.RED));
                    return true;
                }
                PermissionGroup group = manager.getGroups().get(name);
                if (group == null) {
                    sender.sendMessage(Component.text("Group not found.").color(NamedTextColor.RED));
                    return true;
                }
                group.addPermission(args[3]);
                manager.saveGroups();
                refreshAllInGroup(name);
                sender.sendMessage(Component.text("Added permission to group '" + name + "'.").color(NamedTextColor.GREEN));
            }
            case "delperm" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /tprm group <name> delperm <permission>").color(NamedTextColor.RED));
                    return true;
                }
                PermissionGroup group = manager.getGroups().get(name);
                if (group == null) {
                    sender.sendMessage(Component.text("Group not found.").color(NamedTextColor.RED));
                    return true;
                }
                group.removePermission(args[3]);
                manager.saveGroups();
                refreshAllInGroup(name);
                sender.sendMessage(Component.text("Removed permission from group '" + name + "'.").color(NamedTextColor.GREEN));
            }
            case "inherited-from" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /tprm group <name> inherited-from <parent|none>").color(NamedTextColor.RED));
                    return true;
                }
                PermissionGroup group = manager.getGroups().get(name);
                if (group == null) {
                    sender.sendMessage(Component.text("Group not found.").color(NamedTextColor.RED));
                    return true;
                }
                String parent = args[3].equalsIgnoreCase("none") ? null : args[3].toLowerCase();
                if (parent != null && !manager.getGroups().containsKey(parent)) {
                    sender.sendMessage(Component.text("Parent group not found.").color(NamedTextColor.RED));
                    return true;
                }
                group.setParentName(parent);
                manager.saveGroups();
                refreshAllInGroup(name);
                sender.sendMessage(Component.text("Set inheritance for group '" + name + "' to " + (parent == null ? "none" : parent) + ".").color(NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Unknown action.").color(NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleUser(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /tprm user <player> <addperm|delperm|setgroup>").color(NamedTextColor.RED));
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(Component.text("Player '" + args[1] + "' has never played before.").color(NamedTextColor.RED));
            return true;
        }
        UUID uuid = target.getUniqueId();
        String action = args[2].toLowerCase();

        switch (action) {
            case "addperm" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /tprm user <player> addperm <permission>").color(NamedTextColor.RED));
                    return true;
                }
                manager.getUserPermissions(uuid).addPermission(args[3]);
                manager.saveUsers();
                refreshPlayer(uuid);
                sender.sendMessage(Component.text("Added permission to user " + target.getName() + ".").color(NamedTextColor.GREEN));
            }
            case "delperm" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /tprm user <player> delperm <permission>").color(NamedTextColor.RED));
                    return true;
                }
                manager.getUserPermissions(uuid).removePermission(args[3]);
                manager.saveUsers();
                refreshPlayer(uuid);
                sender.sendMessage(Component.text("Removed permission from user " + target.getName() + ".").color(NamedTextColor.GREEN));
            }
            case "setgroup" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /tprm user <player> setgroup <group|none>").color(NamedTextColor.RED));
                    return true;
                }
                String group = args[3].equalsIgnoreCase("none") ? null : args[3].toLowerCase();
                if (group != null && !manager.getGroups().containsKey(group)) {
                    sender.sendMessage(Component.text("Group not found.").color(NamedTextColor.RED));
                    return true;
                }
                UserPermissions u = manager.getUserPermissions(uuid);
                u.getGroups().clear();
                if (group != null) u.addGroup(group);
                manager.saveUsers();
                refreshPlayer(uuid);
                sender.sendMessage(Component.text("Set group for user " + target.getName() + " to " + (group == null ? "none" : group) + ".").color(NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Unknown action.").color(NamedTextColor.RED));
        }
        return true;
    }

    private void refreshPlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            manager.refreshPlayer(player);
        }
    }

    private void refreshAllInGroup(String groupName) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UserPermissions user = manager.getUsers().get(player.getUniqueId());
            boolean inGroup;
            if (user == null || user.getGroups().isEmpty()) {
                inGroup = groupName.equalsIgnoreCase("default");
            } else {
                inGroup = user.hasGroup(groupName);
            }
            if (inGroup) {
                manager.refreshPlayer(player);
            }
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("=== Permission Commands ===").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/tprm gui").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/tprm group <name> create|delete|addperm|delperm|inherited-from").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/tprm user <player> addperm|delperm|setgroup").color(NamedTextColor.YELLOW));
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
                    return filter(Permissions.getAllPermissions(), args[3]);
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
                    return filter(Permissions.getAllPermissions(), args[3]);
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
}
