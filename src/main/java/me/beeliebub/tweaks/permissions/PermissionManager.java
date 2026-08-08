package me.beeliebub.tweaks.permissions;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

// Manages permission logic, inheritance, attachments, and YAML persistence.
// Storage was previously a separate PermissionStorage class.
public class PermissionManager implements Listener {
    private final JavaPlugin plugin;
    private final File groupsFile;
    private final File usersFile;

    private final Map<String, PermissionGroup> groups;
    private final Map<UUID, UserPermissions> users;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public PermissionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.groupsFile = new File(plugin.getDataFolder(), "groups.yml");
        this.usersFile = new File(plugin.getDataFolder(), "users.yml");
        this.groups = loadGroups();
        this.users = loadUsers();

        // Ensure at least a 'default' group exists
        if (!groups.containsKey("default")) {
            groups.put("default", new PermissionGroup("default"));
            saveGroups();
        }
    }

    public void saveGroups() {
        saveGroupsToDisk(groups.values());
    }

    public void saveUsers() {
        saveUsersToDisk(users.values());
    }

    private Map<String, PermissionGroup> loadGroups() {
        Map<String, PermissionGroup> loaded = new ConcurrentHashMap<>();
        if (!groupsFile.exists()) return loaded;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(groupsFile);
        for (String key : config.getKeys(false)) {
            PermissionGroup group = new PermissionGroup(key);
            group.setParentName(config.getString(key + ".parent"));
            List<String> perms = config.getStringList(key + ".permissions");
            perms.forEach(group::addPermission);
            loaded.put(key.toLowerCase(), group);
        }
        return loaded;
    }

    private void saveGroupsToDisk(Collection<PermissionGroup> groups) {
        YamlConfiguration config = new YamlConfiguration();
        for (PermissionGroup group : groups) {
            config.set(group.getName() + ".parent", group.getParentName());
            config.set(group.getName() + ".permissions", new ArrayList<>(group.getPermissions()));
        }
        try {
            config.save(groupsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save groups.yml", e);
        }
    }

    private Map<UUID, UserPermissions> loadUsers() {
        Map<UUID, UserPermissions> loaded = new ConcurrentHashMap<>();
        if (!usersFile.exists()) return loaded;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(usersFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                UserPermissions user = new UserPermissions(uuid);

                // Multi-group: 'group' is now a list. Fall back to a single-string
                // value to migrate users.yml files written before the multi-group
                // refactor.
                Object rawGroup = config.get(key + ".group");
                if (rawGroup instanceof List<?> list) {
                    for (Object entry : list) {
                        if (entry != null) user.addGroup(entry.toString());
                    }
                } else if (rawGroup instanceof String single && !single.isEmpty()) {
                    user.addGroup(single);
                }

                List<String> perms = config.getStringList(key + ".permissions");
                perms.forEach(user::addPermission);
                loaded.put(uuid, user);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Invalid UUID in users.yml: " + key, e);
            }
        }
        return loaded;
    }

    private void saveUsersToDisk(Collection<UserPermissions> users) {
        YamlConfiguration config = new YamlConfiguration();
        for (UserPermissions user : users) {
            String key = user.getUuid().toString();
            config.set(key + ".group", new ArrayList<>(user.getGroups()));
            config.set(key + ".permissions", new ArrayList<>(user.getPermissions()));
        }
        try {
            config.save(usersFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save users.yml", e);
        }
    }

    public Map<String, PermissionGroup> getGroups() {
        return groups;
    }

    public Map<UUID, UserPermissions> getUsers() {
        return users;
    }

    public UserPermissions getUserPermissions(UUID uuid) {
        return users.computeIfAbsent(uuid, UserPermissions::new);
    }

    /** Removes a non-default group and every reference that could otherwise revive it. */
    public boolean deleteGroup(String groupName) {
        if (groupName == null || groupName.equalsIgnoreCase("default")) return false;
        String normalized = groupName.toLowerCase();
        if (groups.remove(normalized) == null) return false;

        for (UserPermissions user : users.values()) {
            user.removeGroup(normalized);
        }
        for (PermissionGroup group : groups.values()) {
            if (normalized.equalsIgnoreCase(group.getParentName())) {
                group.setParentName(null);
            }
        }
        return true;
    }

    public Set<String> calculateEffectivePermissions(UUID uuid) {
        Set<String> effective = new HashSet<>();
        UserPermissions user = users.get(uuid);
        if (user != null) {
            effective.addAll(user.getPermissions());
        }

        // Every player receives the default group's permissions. Additional group
        // memberships supplement that baseline and may contribute inherited permissions.
        // A single `visited` set is shared across all entry-point groups so that
        // shared ancestors in the inheritance DAG (e.g. two groups inheriting
        // from a common parent) contribute their permissions exactly once.
        Set<String> visited = new HashSet<>();
        addInheritedPermissions("default", effective, visited);
        if (user != null) {
            for (String groupName : user.getGroups()) {
                addInheritedPermissions(groupName, effective, visited);
            }
        }

        return effective;
    }

    private void addInheritedPermissions(String groupName, Set<String> effective, Set<String> visited) {
        if (groupName == null || visited.contains(groupName.toLowerCase())) return;

        PermissionGroup group = groups.get(groupName.toLowerCase());
        if (group == null) return;

        visited.add(groupName.toLowerCase());
        effective.addAll(group.getPermissions());

        if (group.getParentName() != null) {
            addInheritedPermissions(group.getParentName(), effective, visited);
        }
    }

    public void refreshPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Remove existing attachment
        PermissionAttachment old = attachments.remove(uuid);
        if (old != null) {
            player.removeAttachment(old);
        }

        // Create new attachment
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(uuid, attachment);

        // Apply calculated permissions
        Set<String> perms = calculateEffectivePermissions(uuid);
        for (String perm : perms) {
            attachment.setPermission(perm, true);
        }
    }

    /** Rebuilds permission attachments after an administrative group change. */
    public void refreshAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        attachments.remove(event.getPlayer().getUniqueId());
    }
    
    public void shutdown() {
        for (UUID uuid : attachments.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.removeAttachment(attachments.get(uuid));
            }
        }
        attachments.clear();
        saveGroups();
        saveUsers();
    }
}
